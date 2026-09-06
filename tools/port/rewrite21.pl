#!/usr/bin/env perl
# Mechanical 1.12.2 (MCP stable_39 names) -> 1.21.1 (Mojmap / NeoForge) renames. Idempotent.
#
#   perl tools/port/rewrite21.pl $(git ls-files 'src/main/**.java' 'src/main/**.kt' 'src/test/**.kt' 'src/test/**.java')
#
# Only rules that are true unconditionally live here. Anything that needs a look at the
# surrounding code (block shapes, the Flattening, GL state, GUI drawing, packets, capabilities)
# is left alone so the compiler still reports it. Identifier renames are applied only to files
# that import the old class, so a word like "World" or "Container" in unrelated code is safe.
use strict;
use warnings;

# --- fully qualified class moves: [old FQN, new FQN, old simple name, new simple name] ------
# The simple-name rename (columns 3/4) is applied to a file only if it imported the old FQN.
my @classes = (
    # entities
    ['net.minecraft.entity.player.EntityPlayer',          'net.minecraft.world.entity.player.Player',            'EntityPlayer',       'Player'],
    ['net.minecraft.entity.player.EntityPlayerMP',        'net.minecraft.server.level.ServerPlayer',             'EntityPlayerMP',     'ServerPlayer'],
    ['net.minecraft.client.entity.EntityPlayerSP',        'net.minecraft.client.player.LocalPlayer',             'EntityPlayerSP',     'LocalPlayer'],
    ['net.minecraft.client.entity.EntityOtherPlayerMP',   'net.minecraft.client.player.RemotePlayer',            'EntityOtherPlayerMP','RemotePlayer'],
    ['net.minecraft.entity.player.InventoryPlayer',       'net.minecraft.world.entity.player.Inventory',         'InventoryPlayer',    'Inventory'],
    ['net.minecraft.entity.EntityLivingBase',             'net.minecraft.world.entity.LivingEntity',             'EntityLivingBase',   'LivingEntity'],
    ['net.minecraft.entity.EntityLiving',                 'net.minecraft.world.entity.Mob',                      'EntityLiving',       'Mob'],
    ['net.minecraft.entity.EntityCreature',               'net.minecraft.world.entity.PathfinderMob',            'EntityCreature',     'PathfinderMob'],
    ['net.minecraft.entity.Entity',                       'net.minecraft.world.entity.Entity',                   undef, undef],
    ['net.minecraft.entity.EntityList',                   'net.minecraft.world.entity.EntityType',               'EntityList',         'EntityType'],
    ['net.minecraft.entity.SharedMonsterAttributes',      'net.minecraft.world.entity.ai.attributes.Attributes', 'SharedMonsterAttributes', 'Attributes'],
    ['net.minecraft.entity.EnumCreatureAttribute',        'net.minecraft.world.entity.MobType',                  'EnumCreatureAttribute', 'MobType'],
    ['net.minecraft.entity.item.EntityItem',              'net.minecraft.world.entity.item.ItemEntity',          'EntityItem',         'ItemEntity'],
    ['net.minecraft.entity.item.EntityMinecart',          'net.minecraft.world.entity.vehicle.AbstractMinecart', 'EntityMinecart',     'AbstractMinecart'],
    ['net.minecraft.entity.monster.EntityMob',            'net.minecraft.world.entity.monster.Monster',          'EntityMob',          'Monster'],
    ['net.minecraft.entity.monster.IMob',                 'net.minecraft.world.entity.monster.Enemy',            'IMob',               'Enemy'],
    ['net.minecraft.entity.monster.EntityEnderman',       'net.minecraft.world.entity.monster.EnderMan',         'EntityEnderman',     'EnderMan'],
    ['net.minecraft.entity.passive.EntityAnimal',         'net.minecraft.world.entity.animal.Animal',            'EntityAnimal',       'Animal'],
    ['net.minecraft.entity.passive.EntityVillager',       'net.minecraft.world.entity.npc.Villager',             'EntityVillager',     'Villager'],
    ['net.minecraft.entity.passive.EntityChicken',        'net.minecraft.world.entity.animal.Chicken',           'EntityChicken',      'Chicken'],
    ['net.minecraft.entity.boss.EntityWither',            'net.minecraft.world.entity.boss.wither.WitherBoss',   'EntityWither',       'WitherBoss'],
    ['net.minecraft.entity.effect.EntityLightningBolt',   'net.minecraft.world.entity.LightningBolt',            'EntityLightningBolt','LightningBolt'],
    ['net.minecraft.entity.projectile.EntityArrow',       'net.minecraft.world.entity.projectile.AbstractArrow', 'EntityArrow',        'AbstractArrow'],
    ['net.minecraft.entity.ai.EntityAIBase',              'net.minecraft.world.entity.ai.goal.Goal',             'EntityAIBase',       'Goal'],
    ['net.minecraft.entity.ai.EntityAIAttackMelee',       'net.minecraft.world.entity.ai.goal.MeleeAttackGoal',  'EntityAIAttackMelee','MeleeAttackGoal'],
    ['net.minecraft.entity.ai.EntityAIHurtByTarget',      'net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal', 'EntityAIHurtByTarget', 'HurtByTargetGoal'],
    ['net.minecraft.entity.ai.EntityAILookIdle',          'net.minecraft.world.entity.ai.goal.RandomLookAroundGoal', 'EntityAILookIdle', 'RandomLookAroundGoal'],
    ['net.minecraft.entity.ai.EntityAIMoveThroughVillage','net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal', 'EntityAIMoveThroughVillage', 'MoveThroughVillageGoal'],
    ['net.minecraft.entity.ai.EntityAIMoveTowardsRestriction', 'net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal', 'EntityAIMoveTowardsRestriction', 'MoveTowardsRestrictionGoal'],
    ['net.minecraft.entity.ai.EntityAINearestAttackableTarget', 'net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal', 'EntityAINearestAttackableTarget', 'NearestAttackableTargetGoal'],
    ['net.minecraft.entity.ai.EntityAISwimming',          'net.minecraft.world.entity.ai.goal.FloatGoal',        'EntityAISwimming',   'FloatGoal'],
    ['net.minecraft.entity.ai.EntityAIWatchClosest',      'net.minecraft.world.entity.ai.goal.LookAtPlayerGoal', 'EntityAIWatchClosest','LookAtPlayerGoal'],
    ['net.minecraft.entity.ai.RandomPositionGenerator',   'net.minecraft.world.entity.ai.util.DefaultRandomPos', 'RandomPositionGenerator', 'DefaultRandomPos'],
    # items
    ['net.minecraft.item.ItemStack',                      'net.minecraft.world.item.ItemStack',                  undef, undef],
    ['net.minecraft.item.Item.ToolMaterial',              'net.minecraft.world.item.Tiers',                      'ToolMaterial',       'Tiers'],
    ['net.minecraft.item.ItemArmor.ArmorMaterial',        'net.minecraft.world.item.ArmorMaterials',             'ArmorMaterial',      'ArmorMaterials'],
    ['net.minecraft.item.ItemArmor',                      'net.minecraft.world.item.ArmorItem',                  'ItemArmor',          'ArmorItem'],
    ['net.minecraft.item.ItemBlock',                      'net.minecraft.world.item.BlockItem',                  'ItemBlock',          'BlockItem'],
    ['net.minecraft.item.ItemPickaxe',                    'net.minecraft.world.item.PickaxeItem',                'ItemPickaxe',        'PickaxeItem'],
    ['net.minecraft.item.ItemAxe',                        'net.minecraft.world.item.AxeItem',                    'ItemAxe',            'AxeItem'],
    ['net.minecraft.item.ItemMonsterPlacer',              'net.minecraft.world.item.SpawnEggItem',               'ItemMonsterPlacer',  'SpawnEggItem'],
    ['net.minecraft.item.crafting.IRecipe',               'net.minecraft.world.item.crafting.Recipe',            'IRecipe',            'Recipe'],
    ['net.minecraft.item.crafting.Ingredient',            'net.minecraft.world.item.crafting.Ingredient',        undef, undef],
    ['net.minecraft.item.crafting.ShapedRecipes',         'net.minecraft.world.item.crafting.ShapedRecipe',      'ShapedRecipes',      'ShapedRecipe'],
    ['net.minecraft.item.crafting.ShapelessRecipes',      'net.minecraft.world.item.crafting.ShapelessRecipe',   'ShapelessRecipes',   'ShapelessRecipe'],
    ['net.minecraft.item.Item',                           'net.minecraft.world.item.Item',                       undef, undef],
    ['net.minecraft.init.Items',                          'net.minecraft.world.item.Items',                      undef, undef],
    ['net.minecraft.init.Blocks',                         'net.minecraft.world.level.block.Blocks',              undef, undef],
    ['net.minecraft.init.SoundEvents',                    'net.minecraft.sounds.SoundEvents',                    undef, undef],
    ['net.minecraft.init.Bootstrap',                      'net.minecraft.server.Bootstrap',                      undef, undef],
    ['net.minecraft.creativetab.CreativeTabs',            'net.minecraft.world.item.CreativeModeTab',            'CreativeTabs',       'CreativeModeTab'],
    ['net.minecraft.client.util.ITooltipFlag',            'net.minecraft.world.item.TooltipFlag',                'ITooltipFlag',       'TooltipFlag'],
    # inventory: Container -> AbstractContainerMenu must run before IInventory -> Container
    ['net.minecraft.inventory.Container',                 'net.minecraft.world.inventory.AbstractContainerMenu', 'Container',          'AbstractContainerMenu'],
    ['net.minecraft.inventory.IInventory',                'net.minecraft.world.Container',                       'IInventory',         'Container'],
    ['net.minecraft.inventory.ISidedInventory',           'net.minecraft.world.WorldlyContainer',                'ISidedInventory',    'WorldlyContainer'],
    ['net.minecraft.inventory.InventoryBasic',            'net.minecraft.world.SimpleContainer',                 'InventoryBasic',     'SimpleContainer'],
    ['net.minecraft.inventory.Slot',                      'net.minecraft.world.inventory.Slot',                  undef, undef],
    ['net.minecraft.inventory.EntityEquipmentSlot',       'net.minecraft.world.entity.EquipmentSlot',            'EntityEquipmentSlot','EquipmentSlot'],
    # world / blocks
    ['net.minecraft.world.WorldServer',                   'net.minecraft.server.level.ServerLevel',              'WorldServer',        'ServerLevel'],
    ['net.minecraft.client.multiplayer.WorldClient',      'net.minecraft.client.multiplayer.ClientLevel',        'WorldClient',        'ClientLevel'],
    ['net.minecraft.world.World',                         'net.minecraft.world.level.Level',                     'World',              'Level'],
    ['net.minecraft.world.IBlockAccess',                  'net.minecraft.world.level.BlockGetter',               'IBlockAccess',       'BlockGetter'],
    ['net.minecraft.world.EnumSkyBlock',                  'net.minecraft.world.level.LightLayer',                'EnumSkyBlock',       'LightLayer'],
    ['net.minecraft.world.EnumDifficulty',                'net.minecraft.world.Difficulty',                      'EnumDifficulty',     'Difficulty'],
    ['net.minecraft.world.chunk.Chunk',                   'net.minecraft.world.level.chunk.LevelChunk',          'Chunk',              'LevelChunk'],
    ['net.minecraft.world.biome.Biome',                   'net.minecraft.world.level.biome.Biome',               undef, undef],
    ['net.minecraft.world.storage.WorldSavedData',        'net.minecraft.world.level.saveddata.SavedData',       'WorldSavedData',     'SavedData'],
    ['net.minecraft.block.state.IBlockState',             'net.minecraft.world.level.block.state.BlockState',    'IBlockState',        'BlockState'],
    ['net.minecraft.block.state.BlockStateContainer',     'net.minecraft.world.level.block.state.StateDefinition', 'BlockStateContainer', 'StateDefinition'],
    ['net.minecraft.block.properties.PropertyInteger',    'net.minecraft.world.level.block.state.properties.IntegerProperty', 'PropertyInteger', 'IntegerProperty'],
    ['net.minecraft.block.Block',                         'net.minecraft.world.level.block.Block',               undef, undef],
    ['net.minecraft.block.BlockChest',                    'net.minecraft.world.level.block.ChestBlock',          'BlockChest',         'ChestBlock'],
    ['net.minecraft.block.BlockDoor',                     'net.minecraft.world.level.block.DoorBlock',           'BlockDoor',          'DoorBlock'],
    ['net.minecraft.block.BlockFire',                     'net.minecraft.world.level.block.FireBlock',           'BlockFire',          'FireBlock'],
    ['net.minecraft.block.BlockHopper',                   'net.minecraft.world.level.block.HopperBlock',         'BlockHopper',        'HopperBlock'],
    ['net.minecraft.block.BlockRailBase',                 'net.minecraft.world.level.block.BaseRailBlock',       'BlockRailBase',      'BaseRailBlock'],
    ['net.minecraft.block.BlockRedstoneOre',              'net.minecraft.world.level.block.RedStoneOreBlock',    'BlockRedstoneOre',   'RedStoneOreBlock'],
    ['net.minecraft.tileentity.TileEntityFurnace',        'net.minecraft.world.level.block.entity.FurnaceBlockEntity', 'TileEntityFurnace', 'FurnaceBlockEntity'],
    ['net.minecraft.tileentity.TileEntity',               'net.minecraft.world.level.block.entity.BlockEntity',  'TileEntity',         'BlockEntity'],
    ['net.minecraft.util.math.BlockPos',                  'net.minecraft.core.BlockPos',                         undef, undef],
    ['net.minecraft.util.math.Vec3d',                     'net.minecraft.world.phys.Vec3',                       'Vec3d',              'Vec3'],
    ['net.minecraft.util.math.AxisAlignedBB',             'net.minecraft.world.phys.AABB',                       'AxisAlignedBB',      'AABB'],
    ['net.minecraft.util.math.RayTraceResult',            'net.minecraft.world.phys.HitResult',                  'RayTraceResult',     'HitResult'],
    ['net.minecraft.util.math.MathHelper',                'net.minecraft.util.Mth',                              'MathHelper',         'Mth'],
    ['net.minecraft.util.EnumFacing',                     'net.minecraft.core.Direction',                        'EnumFacing',         'Direction'],
    ['net.minecraft.util.ResourceLocation',               'net.minecraft.resources.ResourceLocation',            undef, undef],
    ['net.minecraft.util.NonNullList',                    'net.minecraft.core.NonNullList',                      undef, undef],
    ['net.minecraft.util.EnumHand',                       'net.minecraft.world.InteractionHand',                 'EnumHand',           'InteractionHand'],
    ['net.minecraft.util.EnumActionResult',               'net.minecraft.world.InteractionResult',               'EnumActionResult',   'InteractionResult'],
    ['net.minecraft.util.ActionResult',                   'net.minecraft.world.InteractionResultHolder',         'ActionResult',       'InteractionResultHolder'],
    ['net.minecraft.util.EnumBlockRenderType',            'net.minecraft.world.level.block.RenderShape',         'EnumBlockRenderType','RenderShape'],
    ['net.minecraft.util.DamageSource',                   'net.minecraft.world.damagesource.DamageSource',       undef, undef],
    ['net.minecraft.util.SoundCategory',                  'net.minecraft.sounds.SoundSource',                    'SoundCategory',      'SoundSource'],
    ['net.minecraft.util.SoundEvent',                     'net.minecraft.sounds.SoundEvent',                     undef, undef],
    ['net.minecraft.util.EnumParticleTypes',              'net.minecraft.core.particles.ParticleTypes',          'EnumParticleTypes',  'ParticleTypes'],
    ['net.minecraft.util.text.TextComponentString',       'net.minecraft.network.chat.Component',                undef, undef],
    ['net.minecraft.util.text.TextComponentTranslation',  'net.minecraft.network.chat.Component',                undef, undef],
    ['net.minecraft.util.text.ITextComponent',            'net.minecraft.network.chat.Component',                'ITextComponent',     'Component'],
    ['net.minecraft.util.text.TextFormatting',            'net.minecraft.ChatFormatting',                        'TextFormatting',     'ChatFormatting'],
    ['net.minecraft.util.text.event.ClickEvent',          'net.minecraft.network.chat.ClickEvent',               undef, undef],
    # nbt
    ['net.minecraft.nbt.NBTTagCompound',                  'net.minecraft.nbt.CompoundTag',                       'NBTTagCompound',     'CompoundTag'],
    ['net.minecraft.nbt.NBTTagList',                      'net.minecraft.nbt.ListTag',                           'NBTTagList',         'ListTag'],
    ['net.minecraft.nbt.NBTTagString',                    'net.minecraft.nbt.StringTag',                         'NBTTagString',       'StringTag'],
    ['net.minecraft.nbt.NBTTagDouble',                    'net.minecraft.nbt.DoubleTag',                         'NBTTagDouble',       'DoubleTag'],
    ['net.minecraft.nbt.NBTBase',                         'net.minecraft.nbt.Tag',                               'NBTBase',            'Tag'],
    ['net.minecraft.nbt.CompressedStreamTools',           'net.minecraft.nbt.NbtIo',                             'CompressedStreamTools', 'NbtIo'],
    # network
    ['net.minecraft.network.PacketBuffer',                'net.minecraft.network.FriendlyByteBuf',               'PacketBuffer',       'FriendlyByteBuf'],
    ['net.minecraft.network.NetworkManager',              'net.minecraft.network.Connection',                    'NetworkManager',     'Connection'],
    ['net.minecraft.network.Packet',                      'net.minecraft.network.protocol.Packet',               undef, undef],
    ['net.minecraft.network.NetHandlerPlayServer',        'net.minecraft.server.network.ServerGamePacketListenerImpl', 'NetHandlerPlayServer', 'ServerGamePacketListenerImpl'],
    ['net.minecraft.network.play.server.SPacketUpdateTileEntity', 'net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket', 'SPacketUpdateTileEntity', 'ClientboundBlockEntityDataPacket'],
    ['net.minecraft.network.play.server.SPacketSetSlot',  'net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket', 'SPacketSetSlot', 'ClientboundContainerSetSlotPacket'],
    # client
    ['net.minecraft.client.gui.GuiScreen',                'net.minecraft.client.gui.screens.Screen',             'GuiScreen',          'Screen'],
    ['net.minecraft.client.gui.inventory.GuiContainer',   'net.minecraft.client.gui.screens.inventory.AbstractContainerScreen', 'GuiContainer', 'AbstractContainerScreen'],
    ['net.minecraft.client.gui.GuiButton',                'net.minecraft.client.gui.components.Button',          'GuiButton',          'Button'],
    ['net.minecraft.client.gui.GuiTextField',             'net.minecraft.client.gui.components.EditBox',         'GuiTextField',       'EditBox'],
    ['net.minecraft.client.gui.FontRenderer',             'net.minecraft.client.gui.Font',                       'FontRenderer',       'Font'],
    ['net.minecraft.client.gui.Gui',                      'net.minecraft.client.gui.GuiGraphics',                undef, undef],
    ['net.minecraft.client.settings.KeyBinding',          'net.minecraft.client.KeyMapping',                     'KeyBinding',         'KeyMapping'],
    ['net.minecraft.client.renderer.Tessellator',         'com.mojang.blaze3d.vertex.Tesselator',                'Tessellator',        'Tesselator'],
    ['net.minecraft.client.renderer.BufferBuilder',       'com.mojang.blaze3d.vertex.BufferBuilder',             undef, undef],
    ['net.minecraft.client.renderer.vertex.DefaultVertexFormats', 'com.mojang.blaze3d.vertex.DefaultVertexFormat', 'DefaultVertexFormats', 'DefaultVertexFormat'],
    ['net.minecraft.client.renderer.RenderItem',          'net.minecraft.client.renderer.entity.ItemRenderer',   'RenderItem',         'ItemRenderer'],
    ['net.minecraft.client.renderer.entity.RenderManager','net.minecraft.client.renderer.entity.EntityRenderDispatcher', 'RenderManager', 'EntityRenderDispatcher'],
    ['net.minecraft.client.renderer.entity.RenderLiving', 'net.minecraft.client.renderer.entity.MobRenderer',    'RenderLiving',       'MobRenderer'],
    ['net.minecraft.client.renderer.entity.Render',       'net.minecraft.client.renderer.entity.EntityRenderer', 'Render',             'EntityRenderer'],
    ['net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer', 'net.minecraft.client.renderer.blockentity.BlockEntityRenderer', 'TileEntitySpecialRenderer', 'BlockEntityRenderer'],
    ['net.minecraft.client.renderer.block.model.ModelResourceLocation', 'net.minecraft.client.resources.model.ModelResourceLocation', undef, undef],
    ['net.minecraft.client.model.ModelSilverfish',        'net.minecraft.client.model.SilverfishModel',          'ModelSilverfish',    'SilverfishModel'],
    ['net.minecraft.client.model.ModelRenderer',          'net.minecraft.client.model.geom.ModelPart',           'ModelRenderer',      'ModelPart'],
    ['net.minecraft.client.model.ModelBase',              'net.minecraft.client.model.Model',                    'ModelBase',          'Model'],
    ['net.minecraft.client.audio.ISound',                 'net.minecraft.client.resources.sounds.SoundInstance', 'ISound',             'SoundInstance'],
    ['net.minecraft.client.audio.ITickableSound',         'net.minecraft.client.resources.sounds.TickableSoundInstance', 'ITickableSound', 'TickableSoundInstance'],
    ['net.minecraft.client.audio.PositionedSoundRecord',  'net.minecraft.client.resources.sounds.SimpleSoundInstance', 'PositionedSoundRecord', 'SimpleSoundInstance'],
    ['net.minecraft.client.audio.PositionedSound',        'net.minecraft.client.resources.sounds.AbstractSoundInstance', 'PositionedSound', 'AbstractSoundInstance'],
    ['net.minecraft.client.audio.SoundManager',           'net.minecraft.client.sounds.SoundManager',            undef, undef],
    ['net.minecraft.client.audio.SoundHandler',           'net.minecraft.client.sounds.SoundManager',            'SoundHandler',       'SoundManager'],
    # forge -> neoforge
    ['net.minecraftforge.fml.common.eventhandler.SubscribeEvent', 'net.neoforged.bus.api.SubscribeEvent',         undef, undef],
    ['net.minecraftforge.fml.common.eventhandler.Event',  'net.neoforged.bus.api.Event',                          undef, undef],
    ['net.minecraftforge.fml.relauncher.SideOnly',        'net.neoforged.api.distmarker.OnlyIn',                  'SideOnly',           'OnlyIn'],
    ['net.minecraftforge.fml.relauncher.Side',            'net.neoforged.api.distmarker.Dist',                    'Side',               'Dist'],
    ['net.minecraftforge.common.MinecraftForge',          'net.neoforged.neoforge.common.NeoForge',               'MinecraftForge',     'NeoForge'],
    ['net.minecraftforge.fluids.FluidStack',              'net.neoforged.neoforge.fluids.FluidStack',             undef, undef],
    ['net.minecraftforge.fluids.FluidTank',               'net.neoforged.neoforge.fluids.capability.templates.FluidTank', undef, undef],
    ['net.minecraftforge.fluids.FluidUtil',               'net.neoforged.neoforge.fluids.FluidUtil',              undef, undef],
    ['net.minecraftforge.fluids.capability.IFluidHandler','net.neoforged.neoforge.fluids.capability.IFluidHandler', undef, undef],
    ['net.minecraftforge.fluids.capability.IFluidHandlerItem', 'net.neoforged.neoforge.fluids.capability.IFluidHandlerItem', undef, undef],
    ['net.minecraftforge.energy.IEnergyStorage',          'net.neoforged.neoforge.energy.IEnergyStorage',         undef, undef],
    ['net.minecraftforge.items.IItemHandler',             'net.neoforged.neoforge.items.IItemHandler',            undef, undef],
    ['net.minecraftforge.common.util.FakePlayer',         'net.neoforged.neoforge.common.util.FakePlayer',        undef, undef],
    ['net.minecraftforge.common.util.FakePlayerFactory',  'net.neoforged.neoforge.common.util.FakePlayerFactory', undef, undef],
    ['net.minecraftforge.event.world.BlockEvent',         'net.neoforged.neoforge.event.level.BlockEvent',        undef, undef],
    ['net.minecraftforge.event.world.ExplosionEvent',     'net.neoforged.neoforge.event.level.ExplosionEvent',    undef, undef],
    ['net.minecraftforge.event.world.WorldEvent',         'net.neoforged.neoforge.event.level.LevelEvent',        'WorldEvent',         'LevelEvent'],
    ['net.minecraftforge.event.entity.player.PlayerInteractEvent', 'net.neoforged.neoforge.event.entity.player.PlayerInteractEvent', undef, undef],
    ['net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemCraftedEvent', 'net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent', undef, undef],
    ['net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent', 'net.neoforged.neoforge.client.event.InputEvent.Key', 'KeyInputEvent', 'Key'],
    ['net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent', 'net.neoforged.neoforge.event.tick.ServerTickEvent', undef, undef],
    ['net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent', 'net.neoforged.neoforge.event.tick.ClientTickEvent', undef, undef],
    ['net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent', 'net.neoforged.neoforge.client.event.RenderFrameEvent', 'RenderTickEvent', 'RenderFrameEvent'],
    ['net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent', 'net.neoforged.neoforge.event.tick.LevelTickEvent', 'WorldTickEvent', 'LevelTickEvent'],
    ['net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent', 'net.neoforged.neoforge.event.tick.PlayerTickEvent', undef, undef],
    ['net.minecraftforge.client.event.sound.PlaySoundSourceEvent', 'net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent', undef, undef],
    ['net.minecraftforge.client.event.GuiOpenEvent',      'net.neoforged.neoforge.client.event.ScreenEvent.Opening', 'GuiOpenEvent',    'Opening'],
    ['net.minecraftforge.fml.relauncher.ReflectionHelper','net.neoforged.fml.util.ObfuscationReflectionHelper', 'ReflectionHelper',   'ObfuscationReflectionHelper'],
    ['net.minecraftforge.fml.common.Loader',              'net.neoforged.fml.ModList',                            'Loader',             'ModList'],
    # fixed-function GL -> the emulator in mods.eln.client.gl (same names, same constants)
    ['org.lwjgl.opengl.GL11',                             'mods.eln.client.gl.GL11',                              undef, undef],
    ['org.lwjgl.opengl.GL12',                             'mods.eln.client.gl.GL12',                              undef, undef],
    ['net.minecraft.client.renderer.RenderHelper',        'mods.eln.client.gl.RenderHelper',                      undef, undef],
    ['net.minecraft.client.renderer.OpenGlHelper',        'mods.eln.client.gl.OpenGlHelper',                      undef, undef],
);

# --- member renames, only when dot-qualified (receiver.name) -------------------------------
my @members = (
    # Level (World)
    ['isRemote'                 => 'isClientSide'],
    ['getTileEntity'            => 'getBlockEntity'],
    ['isAirBlock'               => 'isEmptyBlock'],
    ['getTotalWorldTime'        => 'getGameTime'],
    ['totalWorldTime'           => 'gameTime'],
    ['getWorldTime'             => 'getDayTime'],
    ['worldTime'                => 'dayTime'],
    ['isDaytime'                => 'isDay'],
    ['notifyNeighborsOfStateChange' => 'updateNeighborsAt'],
    ['getEntitiesWithinAABB'    => 'getEntitiesOfClass'],
    ['spawnEntity'              => 'addFreshEntity'],
    ['getMinecraftServer'       => 'getServer'],
    ['getWorldInfo'             => 'getLevelData'],
    ['getMapStorage'            => 'getDataStorage'],
    ['isBlockPowered'           => 'hasNeighborSignal'],
    ['getRedstonePower'         => 'getSignal'],
    ['getStrongPower'           => 'getDirectSignal'],
    # BlockEntity
    ['markDirty'                => 'setChanged'],
    ['hasWorld'                 => 'hasLevel'],
    ['isInvalid'                => 'isRemoved'],
    # Entity / Player
    ['getEntityWorld'           => 'level'],
    ['getUniqueID'              => 'getUUID'],
    ['getEntityId'              => 'getId'],
    ['isSneaking'               => 'isShiftKeyDown'],
    ['getHeldItemMainhand'      => 'getMainHandItem'],
    ['heldItemMainhand'         => 'mainHandItem'],
    ['getHeldItem'              => 'getItemInHand'],
    ['setHeldItem'              => 'setItemInHand'],
    ['inventoryContainer'       => 'inventoryMenu'],
    ['openContainer'            => 'containerMenu'],
    ['getPositionVector'        => 'position'],
    ['getLookVec'               => 'getLookAngle'],
    ['isEntityAlive'            => 'isAlive'],
    ['attackEntityFrom'         => 'hurt'],
    ['setDead'                  => 'discard'],
    ['entityDropItem'           => 'spawnAtLocation'],
    ['setLocationAndAngles'     => 'moveTo'],
    # Minecraft
    ['getMinecraft'             => 'getInstance'],
    ['displayGuiScreen'         => 'setScreen'],
    ['currentScreen'            => 'screen'],
    ['getRenderManager'         => 'getEntityRenderDispatcher'],
    ['getSoundHandler'          => 'getSoundManager'],
    ['getRenderItem'            => 'getItemRenderer'],
    ['objectMouseOver'          => 'hitResult'],
    ['gameSettings'             => 'options'],
    ['isGamePaused'             => 'isPaused'],
    ['getRenderViewEntity'      => 'getCameraEntity'],
    ['isSingleplayer'           => 'hasSingleplayerServer'],
    ['getIntegratedServer'      => 'getSingleplayerServer'],
    # Direction (EnumFacing)
    ['getFrontOffsetX'          => 'getStepX'],
    ['getFrontOffsetY'          => 'getStepY'],
    ['getFrontOffsetZ'          => 'getStepZ'],
    ['frontOffsetX'             => 'stepX'],
    ['frontOffsetY'             => 'stepY'],
    ['frontOffsetZ'             => 'stepZ'],
    ['getDirectionVec'          => 'getNormal'],
    ['directionVec'             => 'normal'],
    ['getHorizontalIndex'       => 'get2DDataValue'],
    ['horizontalIndex'          => '2DDataValue'],
    # Container / Slot / inventories
    ['addSlotToContainer'       => 'addSlot'],
    ['inventorySlots'           => 'slots'],
    ['canInteractWith'          => 'stillValid'],
    ['transferStackInSlot'      => 'quickMoveStack'],
    ['detectAndSendChanges'     => 'broadcastChanges'],
    ['mergeItemStack'           => 'moveItemStackTo'],
    ['onContainerClosed'        => 'removed'],
    ['getSizeInventory'         => 'getContainerSize'],
    ['sizeInventory'            => 'containerSize'],
    ['getStackInSlot'           => 'getItem'],
    ['decrStackSize'            => 'removeItem'],
    ['removeStackFromSlot'      => 'removeItemNoUpdate'],
    ['setInventorySlotContents' => 'setItem'],
    ['getInventoryStackLimit'   => 'getMaxStackSize'],
    ['inventoryStackLimit'      => 'maxStackSize'],
    ['isUsableByPlayer'         => 'stillValid'],
    ['openInventory'            => 'startOpen'],
    ['closeInventory'           => 'stopOpen'],
    ['isItemValidForSlot'       => 'canPlaceItem'],
    ['canInsertItem'            => 'canPlaceItemThroughFace'],
    ['canExtractItem'           => 'canTakeItemThroughFace'],
    ['slotNumber'               => 'index'],
    ['getHasStack'              => 'hasItem'],
    ['hasStack'                 => 'hasItem'],
    ['putStack'                 => 'set'],
    ['onSlotChanged'            => 'setChanged'],
    ['getSlotStackLimit'        => 'getMaxStackSize'],
    ['slotStackLimit'           => 'maxStackSize'],
    # ItemStack
    ['splitStack'               => 'split'],
    ['areItemStacksEqual'       => 'matches'],
    ['isItemEqualIgnoreDurability' => 'isSameItem'],
    # Vec3 / AABB / BlockPos
    ['lengthVector'             => 'length'],
    ['dotProduct'               => 'dot'],
    ['crossProduct'             => 'cross'],
    ['distanceSq'               => 'distSqr'],
    # Font / misc
    ['getStringWidth'           => 'width'],
    ['fontRenderer'             => 'font'],
);

# --- NBT accessors: only on receivers that look like tags (JsonConfig has setDouble/... too) ---
my @nbt = (
    ['setInteger' => 'putInt'], ['getInteger' => 'getInt'], ['setDouble' => 'putDouble'], ['setFloat' => 'putFloat'],
    ['setString' => 'putString'], ['setBoolean' => 'putBoolean'], ['setLong' => 'putLong'], ['setByte' => 'putByte'],
    ['setShort' => 'putShort'], ['setByteArray' => 'putByteArray'], ['setIntArray' => 'putIntArray'], ['setTag' => 'put'],
    ['setUniqueId' => 'putUUID'], ['getUniqueId' => 'getUUID'], ['hasUniqueId' => 'hasUUID'],
    ['getTagList' => 'getList'], ['getCompoundTag' => 'getCompound'], ['hasKey' => 'contains'], ['getKeySet' => 'getAllKeys'],
    ['keySet' => 'getAllKeys'], ['removeTag' => 'remove'], ['tagCount' => 'size'], ['getCompoundTagAt' => 'getCompound'],
    ['appendTag' => 'add'], ['getStringTagAt' => 'getString'], ['getDoubleAt' => 'getDouble'], ['getIntAt' => 'getInt'],
    ['hasNoTags' => 'isEmpty'],
);
my $nbtrecv = qr/(?:\w*(?:nbt|Nbt|NBT|[tT]ag|compound|Compound)\w*|root)/;
# Precompiled once: these run per line over ~1000 files.
my @member_re = map { my ($f, $t) = @$_; [qr/(?<=[\w)\]])(\??\.|!!\.)\Q$f\E\b(?!\s*=[^=])/, $t] } @members;
my @nbt_re = map { my ($f, $t) = @$_; [qr/\b($nbtrecv)((?:\??\.|!!\.))\Q$f\E\b/, $t] } @nbt;
my $member_any = do { my $alt = join '|', map { quotemeta $_->[0] } @members; qr/\.(?:$alt)\b/ };
my $nbt_any = do { my $alt = join '|', map { quotemeta $_->[0] } @nbt; qr/\.(?:$alt)\b/ };

# --- expression rewrites ---------------------------------------------------------------------
my @exprs = (
    [qr/\bTextComponentString\s*\(/                     => 'Component.literal('],
    [qr/\bTextComponentTranslation\s*\(/                => 'Component.translatable('],
    [qr/\bnew Component\.(literal|translatable)\(/      => 'Component.$1('],
    [qr/\bMinecraft\.getMinecraft\(\)/                  => 'Minecraft.getInstance()'],
    [qr/\bnew ResourceLocation\(\s*("[^"]*")\s*\)/      => 'ResourceLocation.parse($1)'],
    [qr/\bnew ResourceLocation\(/                       => 'ResourceLocation.fromNamespaceAndPath('],
    [qr/\bMathHelper\./                                 => 'Mth.'],
    [qr/\bEnumFacing\.VALUES\b/                         => 'Direction.values()'],
    [qr/\bEnumFacing\.byIndex\(/                        => 'Direction.from3DDataValue('],
    [qr/\bEnumFacing\.getFront\(/                       => 'Direction.from3DDataValue('],
    [qr/\.getIndex\(\)/                                 => '.get3DDataValue()'],
    [qr/\bSide\.SERVER\b/                               => 'Dist.DEDICATED_SERVER'],
    [qr/\bMinecraftForge\.EVENT_BUS\b/                  => 'NeoForge.EVENT_BUS'],
    [qr/\bTickEvent\.Phase\.END\b/                      => 'true /* NeoForge: Post event */'],
    [qr/\bTickEvent\.ServerTickEvent\b/                 => 'ServerTickEvent.Post'],
    [qr/\bTickEvent\.ClientTickEvent\b/                 => 'ClientTickEvent.Post'],
    [qr/\bTickEvent\.WorldTickEvent\b/                  => 'LevelTickEvent.Post'],
    [qr/\bTickEvent\.PlayerTickEvent\b/                 => 'PlayerTickEvent.Post'],
    [qr/\bTickEvent\.RenderTickEvent\b/                 => 'RenderFrameEvent.Post'],
    [qr/\bServerTickEvent\b(?!\.Post|\.Pre)/            => 'ServerTickEvent.Post'],
    [qr/\bClientTickEvent\b(?!\.Post|\.Pre)/            => 'ClientTickEvent.Post'],
    [qr/\bRenderFrameEvent\b(?!\.Post|\.Pre)/           => 'RenderFrameEvent.Post'],
    [qr/\bWorldSavedData\b/                             => 'SavedData'],
    [qr/\.getPos\(\)/                                   => '.getBlockPos()'],
    [qr/\bgetWorld\(\)/                                 => 'getLevel()'],
    [qr/\bworld\.provider\.getDimension\(\)/            => 'world.dimension()'],
    [qr/\bprovider\.dimension\b/                        => 'dimension()'],
    [qr/\bItemStack\.areItemsEqual\(/                   => 'ItemStack.isSameItem('],
    [qr/\bItemStack\.areItemStacksEqual\(/              => 'ItemStack.matches('],
    [qr/\bEnumSkyBlock\.SKY\b/                          => 'LightLayer.SKY'],
    [qr/\bEnumSkyBlock\.BLOCK\b/                        => 'LightLayer.BLOCK'],
    [qr/\bgetLightFor\(/                                => 'getBrightness('],
    [qr/\bcapabilities\.isCreativeMode\b/               => 'isCreative()'],
    [qr/\bgetItemDamage\(\)(?! \/\* TODO)/              => 'getItemDamage() /* TODO(flattening) */'],
    # ItemStack.getDisplayName -> getHoverName; Entity.getDisplayName keeps its name, so only stack receivers.
    [qr/\b(\w*[sS]tack\w*|getItem\(\)|item)\.getDisplayName\(\)/ => '$1.getHoverName()'],
    [qr/\b(\w*[sS]tack\w*|item)\.displayName\b/           => '$1.hoverName'],
);

# --- interface methods of Minecraft types that the mod implements: rename the definitions too ---
my @defs = qw(markDirty getSizeInventory getStackInSlot decrStackSize removeStackFromSlot setInventorySlotContents
    getInventoryStackLimit isUsableByPlayer openInventory closeInventory isItemValidForSlot canInsertItem canExtractItem
    canInteractWith transferStackInSlot detectAndSendChanges onContainerClosed);
my %defs = map { my ($f, $t) = @$_; ($f => $t) } grep { my $n = $_->[0]; grep { $_ eq $n } @defs } @members;

my $changed_files = 0;
for my $file (@ARGV) {
    open my $fh, '<', $file or die "$file: $!";
    my $src = do { local $/; <$fh> };
    close $fh;
    my $orig = $src;
    my $kotlin = $file =~ /\.kt$/;

    # The mod's own Direction enum clashes with net.minecraft.core.Direction. Kotlin can alias
    # the import; Java gets the fully qualified name at every use.
    my $eln_direction = ($src =~ /^import mods\.eln\.misc\.Direction\b/m) || ($file =~ m{mods/eln/misc/} && $src !~ /^import mods\.eln\.misc\.Direction/m && $src =~ /\bDirection\b/);
    my $uses_facing = $src =~ /^import net\.minecraft\.util\.EnumFacing\b/m;

    for my $c (@classes) {
        my ($oldfq, $newfq, $old, $new) = @$c;
        my $imported = $src =~ /^import (?:static )?\Q$oldfq\E\b/m;
        (my $q = $oldfq) =~ s/\./\\./g;
        $src =~ s/\b$q\b/$newfq/g;
        next unless $imported && defined $old;
        if ($old eq 'EnumFacing' && $eln_direction) {
            if ($kotlin) {
                $src =~ s/^import net\.minecraft\.core\.Direction\n/import net.minecraft.core.Direction as EnumFacing\n/m;
            } else {
                $src =~ s/^import net\.minecraft\.core\.Direction;\n//m;
                $src =~ s/(?<![\w.\$])EnumFacing\b/net.minecraft.core.Direction/g;
            }
            next;
        }
        $src =~ s/(?<![\w.\$])\Q$old\E\b/$new/g;
    }
    # duplicate imports left by two old classes mapping to one new one (Component, SoundManager)
    my %seen;
    $src =~ s/^(import (?:static )?[\w.]+;?\n)/$seen{$1}++ ? '' : $1/gme;

    # Everything below is code, not imports: apply it line by line, skipping import/package lines,
    # so a member rule like ".world -> .level" cannot rewrite "net.minecraft.world.level.Level".
    my @lines = split /(?<=\n)/, $src;
    for my $line (@lines) {
        next if $line =~ /^\s*(?:import|package)\b/;
        my $l = $line;
        if ($l =~ $member_any) {
            for my $r (@member_re) { my ($re, $to) = @$r; $l =~ s/$re/$1$to/g; }
        }
        if ($l =~ $nbt_any) {
            for my $r (@nbt_re) { my ($re, $to) = @$r; $l =~ s/$re/$1$2$to/g; }
        }
        # /ee so that "$1" in a replacement string is the capture, not the two characters
        for my $r (@exprs) { my ($from, $to) = @$r; $l =~ s/$from/"\"$to\""/gee; }
        # definitions: "override fun markDirty(" / "public void markDirty(" -> the 1.21 interface name
        if ($l =~ /\b(?:fun|void|int|boolean|ItemStack|String|IntArray)\s+\w+\s*\(/ ) {
            for my $from (keys %defs) {
                my $to = $defs{$from};
                $l =~ s/^(\s*(?:\@Override\s+)?(?:public|protected|private|override|open|abstract|final|synchronized|\s)*(?:fun|void|int|boolean|ItemStack|String|IntArray|int\[\])\s+)\Q$from\E(?=\s*\()/$1$to/;
            }
        }

        # Kotlin property forms of the renamed getters.
        if ($kotlin) {
            $l =~ s/(?<=[\w)\]])(\??\.|!!\.)world\b(?!\s*=[^=])(?![\w(])/$1level/g;
            $l =~ s/(?<=[\w)\]])(\??\.|!!\.)posX\b/$1x/g;
            $l =~ s/(?<=[\w)\]])(\??\.|!!\.)posY\b/$1y/g;
            $l =~ s/(?<=[\w)\]])(\??\.|!!\.)posZ\b/$1z/g;
            $l =~ s/(?<=[\w)\]])(\??\.|!!\.)rotationYaw\b/$1yRot/g;
            $l =~ s/(?<=[\w)\]])(\??\.|!!\.)rotationPitch\b/$1xRot/g;
            $l =~ s/(?<=[\w)\]])(\??\.|!!\.)tagCompound\b(?! \/\* TODO)/$1tagCompound \/* TODO(components) *\//g;
        } else {
            $l =~ s/(?<=[\w)\]])\.posX\b/.getX()/g;
            $l =~ s/(?<=[\w)\]])\.posY\b/.getY()/g;
            $l =~ s/(?<=[\w)\]])\.posZ\b/.getZ()/g;
            $l =~ s/(?<=[\w)\]])\.rotationYaw\b/.getYRot()/g;
            $l =~ s/(?<=[\w)\]])\.rotationPitch\b/.getXRot()/g;
            $l =~ s/(?<=[\w)\]])\.world\b(?!\s*=[^=])(?![\w(])/.level()/g;
        }

        $line = $l;
    }
    $src = join '', @lines;

    if ($src ne $orig) {
        open my $out, '>', $file or die "$file: $!";
        print $out $src;
        close $out;
        $changed_files++;
    }
}
print "rewrote $changed_files of " . scalar(@ARGV) . " files\n";
