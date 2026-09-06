#!/usr/bin/env python3
"""Reviewed API migration pass over original code. No resource or gameplay replacement."""
import argparse
import hashlib
import json
from pathlib import Path
import re
from rewrite_types import TOKENS

ROOT=Path(__file__).resolve().parents[1]
NBT_METHODS={
 'setByte':'putByte','setShort':'putShort','setInteger':'putInt','getInteger':'getInt',
 'setLong':'putLong','setFloat':'putFloat','setDouble':'putDouble','setBoolean':'putBoolean',
 'setString':'putString','setByteArray':'putByteArray','setIntArray':'putIntArray',
 'setTag':'put','getCompoundTag':'getCompound','getTagList':'getList','hasKey':'contains',
 'getKeySet':'getAllKeys','removeTag':'remove',
}
LIST_METHODS={'appendTag':'add','tagCount':'size','getCompoundTagAt':'getCompound','getStringTagAt':'getString'}
# These identifiers refer to the old inventory contract in this repository.
INVENTORY={
 'getSizeInventory':'getContainerSize','getStackInSlot':'getItem',
 'decrStackSize':'removeItem','removeStackFromSlot':'removeItemNoUpdate',
 'setInventorySlotContents':'setItem','getInventoryStackLimit':'getMaxStackSize',
 'isUsableByPlayer':'stillValid','openInventory':'startOpen','closeInventory':'stopOpen',
 'isItemValidForSlot':'canPlaceItem','canInsertItem':'canPlaceItemThroughFace',
 'canExtractItem':'canTakeItemThroughFace',
 'sizeInventory':'containerSize','inventoryStackLimit':'maxStackSize',
}

def typed_names(text, kind):
    code=TOKENS.sub(lambda m: ' ' * len(m.group(0)) if m.group(1) else m.group(0),text)
    names=set(re.findall(r'\b'+kind+r'\s+([A-Za-z_]\w*)',code))
    names.update(re.findall(r'\b([A-Za-z_]\w*)\s*:\s*'+kind+r'\b',code))
    names.update(re.findall(r'\b(?:val|var)\s+([A-Za-z_]\w*)\s*=\s*'+kind+r'\s*\(',code))
    return names

def rewrite(text):
    compounds=typed_names(text,'CompoundTag')
    lists=typed_names(text,'ListTag')
    def token(m):
        if m.group(1):return m.group(0)
        pieces=m.group(2).split('.')
        if len(pieces)>=2 and pieces[-2] in compounds:
            pieces[-1]=NBT_METHODS.get(pieces[-1],pieces[-1])
        if len(pieces)>=2 and pieces[-2] in lists:
            pieces[-1]=LIST_METHODS.get(pieces[-1],pieces[-1])
        pieces=[INVENTORY.get(p,p) for p in pieces]
        if len(pieces)>1 and pieces[-1]=='isRemote':pieces[-1]='isClientSide'
        if len(pieces)>1 and pieces[-1]=='splitStack':pieces[-1]='split'
        if pieces[:2]==['Minecraft','getMinecraft']:pieces[1]='getInstance'
        return '.'.join(pieces)
    result=TOKENS.sub(token,text)
    # Annotation replacements do not conflate the old logical network Side
    # with the physical distribution. Unmigrated networking remains an error.
    result=re.sub(r'@SideOnly\(Side\.CLIENT\)', '@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)',result)
    result=re.sub(r'@SideOnly\(Side\.SERVER\)', '@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.DEDICATED_SERVER)',result)
    if not re.search(r'@SideOnly\b',result):
        result=re.sub(r'(?m)^import net\.minecraftforge\.fml\.relauncher\.SideOnly;?\n','',result)
    without_import=re.sub(r'(?m)^import[^\n]*\n','',result)
    if not re.search(r'\bSide\b',without_import):
        result=re.sub(r'(?m)^import net\.minecraftforge\.fml\.relauncher\.Side;?\n','',result)
    return result

def resource_factories(text):
    # Count constructor arguments outside nested expressions and strings.
    protected=[(m.start(),m.end()) for m in TOKENS.finditer(text) if m.group(1)]
    def is_protected(pos):return any(a<=pos<b for a,b in protected)
    replacements=[]
    for m in re.finditer(r'\b(?:new\s+)?ResourceLocation\s*\(',text):
        if is_protected(m.start()):continue
        depth=1;commas=0;i=m.end()
        while i<len(text) and depth:
            region=next(((a,b) for a,b in protected if a<=i<b),None)
            if region:i=region[1];continue
            if text[i] in '([{':depth+=1
            elif text[i] in ')]}':depth-=1
            elif text[i]==',' and depth==1:commas+=1
            i+=1
        if depth:raise ValueError('Unbalanced ResourceLocation call')
        if commas not in (0,1):raise ValueError('Unexpected ResourceLocation arity')
        replacements.append((m.start(),m.end(),'ResourceLocation.'+('parse' if commas==0 else 'fromNamespaceAndPath')+'('))
    for a,b,new in reversed(replacements):text=text[:a]+new+text[b:]
    return text

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    rows=[]
    for p in sorted((ROOT/'src/main/java').rglob('*')):
        if p.suffix not in ('.java','.kt') or p.name=='Eln_old.java':continue
        old=p.read_text();new=rewrite(old)
        if 'import net.minecraft.resources.ResourceLocation' in new:
            new=resource_factories(new)
        if p.name in ('SixNodeElementInventory.java','TransparentNodeElementInventory.java','FakeSideInventory.java'):
            # Keep obsolete helper methods for callers; do not erase behavior.
            for name in ('getName','hasCustomName','getDisplayName','getField','setField','getFieldCount'):
                new=re.sub(r'@Override(\s+(?:@NotNull\s+)?public\s+\S+\s+'+name+r'\s*\()',r'\1',new)
            new=new.replace('public void markDirty()', 'public void setChanged()')
            new=re.sub(r'\bmarkDirty\(\)', 'setChanged()',new)
            new=new.replace('public void clear()', 'public void clearContent()')
            new=new.replace('new TextComponentString(', 'Component.literal(')
            new=new.replace('import net.minecraft.util.text.TextComponentString;\n','')
            new=new.replace('Direction.VALUES', 'Direction.values()')
        if p.name=='FakeSideInventory.java':
            # Existing empty-inventory sentinel, NOT a missing-device substitute.
            new=re.sub(r'(public ItemStack (?:getItem|removeItem|removeItemNoUpdate)\([^)]*\)\s*\{)\s*return null;', r'\1\n        return ItemStack.EMPTY;', new)
            new=new.replace('public Component getDisplayName() {\n        return null;', 'public Component getDisplayName() {\n        return Component.literal(getName());')
        if old!=new:
            rows.append({'file':p.relative_to(ROOT).as_posix(),'before':hashlib.sha256(old.encode()).hexdigest(),'after':hashlib.sha256(new.encode()).hexdigest()})
            if args.apply:p.write_text(new)
    if args.apply:
        (ROOT/'migrations/002-members.json').write_text(json.dumps(rows,indent=2)+'\n')
    print(f'{len(rows)} source files {"updated" if args.apply else "would change"}; names, recipes and models retained')

if __name__=='__main__':main()
