#!/usr/bin/env python3
"""Scoped prototype checks. Vanilla references need the real Minecraft resource-loader tests."""
import json, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
DEVICES=('voltage_source','resistive_wire','resistive_load','capacitor')
def validate(resources):
    resources=pathlib.Path(resources)
    def load(path):return json.loads((resources/path).read_text(encoding='utf-8'))
    for name in DEVICES:
        variants=load(f'assets/eln/blockstates/{name}.json')['variants']
        expected={f'facing={d},lit={lit}' for d in ('north','south','west','east','up','down') for lit in ('true','false')}
        if set(variants)!=expected: raise ValueError(f'Missing or unexpected device states: {name}')
        for variant in variants.values():
            model=variant['model']
            if not model.startswith('eln:block/'):raise ValueError('Unexpected model namespace')
            load('assets/eln/models/'+model.split(':',1)[1]+'.json')
        item=load(f'assets/eln/models/item/{name}.json')
        if item.get('parent')!=f'eln:block/{name}':raise ValueError('Wrong item model')
        recipe=load(f'data/eln/recipe/{name}.json')
        if recipe.get('result',{}).get('id')!='eln:'+name:raise ValueError('Wrong recipe result')
        loot=load(f'data/eln/loot_table/blocks/{name}.json')
        if loot['pools'][0]['entries'][0]['name']!='eln:'+name:raise ValueError('Wrong block drop')
    return {'network_blockstates':48,'network_items':4,'network_recipes':4,'network_loot_tables':4}
if __name__=='__main__': print(json.dumps(validate(ROOT/'src/main/resources'),indent=2))
