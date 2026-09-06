#!/usr/bin/env python3
"""First mechanical type pass. No fake legacy classes, method stubs or machine removal."""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
IMPORT = re.compile(r'(?m)^([ \t]*import[ \t]+)(static[ \t]+)?([\w.$]+)([ \t]*;?[^\n]*)(\n|$)')
TOKENS = re.compile(r'("""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/)|([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)')

def rewrite(text, mapping, kotlin=False):
    renames = {}
    imports = list(IMPORT.finditer(text))
    final_simple = collections.defaultdict(set)
    for match in imports:
        name = match.group(3)
        target = mapping.get(name, name)
        final_simple[target.rsplit('.',1)[-1]].add(target)
    own = re.search(r'\b(?:class|interface|enum|object)\s+([\w$]+)', text)
    if own:
        final_simple[own.group(1)].add('<declared-here>')
    replacements = {}
    for match in imports:
        name = match.group(3)
        target = mapping.get(name)
        if target and name != target:
            old_simple, new_simple = name.rsplit('.',1)[-1], target.rsplit('.',1)[-1]
            if ' as ' in match.group(4):
                replacements[match.span()] = match.group(1) + (match.group(2) or '') + target + match.group(4) + match.group(5)
            elif len(final_simple[new_simple]) > 1:
                if kotlin:
                    replacements[match.span()] = 'import ' + target + ' as ' + old_simple + '\n'
                else:
                    replacements[match.span()] = ''
                    renames[old_simple] = target
            else:
                replacements[match.span()] = match.group(1) + (match.group(2) or '') + target + match.group(4) + match.group(5)
                renames[old_simple] = new_simple
    def token_replace(match):
        if match.group(1) is not None:
            return match.group(0)
        value = match.group(2)
        for source in sorted(mapping, key=len, reverse=True):
            if value == source or value.startswith(source + '.'):
                return mapping[source] + value[len(source):]
        first, dot, suffix = value.partition('.')
        return renames.get(first, first) + (dot + suffix if dot else '')
    out=[]
    cursor=0
    for match in imports:
        out.append(TOKENS.sub(token_replace,text[cursor:match.start()]))
        if match.span() in replacements:
            out.append(replacements[match.span()])
        else:
            out.append(TOKENS.sub(token_replace,match.group(0)))
        cursor=match.end()
    out.append(TOKENS.sub(token_replace,text[cursor:]))
    return ''.join(out)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args=parser.parse_args()
    mapping=json.loads((ROOT/'tools/type-map.json').read_text())
    rows=[]
    for p in sorted((ROOT/'src/main/java').rglob('*')):
        if p.suffix not in ('.java','.kt') or p.name=='Eln_old.java':
            continue
        original=p.read_text()
        result=rewrite(original,mapping,p.suffix=='.kt')
        if original!=result:
            rows.append({'file':p.relative_to(ROOT).as_posix(),'before':hashlib.sha256(original.encode()).hexdigest(),'after':hashlib.sha256(result.encode()).hexdigest()})
            if args.apply:
                p.write_text(result)
    if args.apply:
        (ROOT/'migrations').mkdir(exist_ok=True)
        (ROOT/'migrations/001-type-remap.json').write_text(json.dumps(rows,indent=2)+'\n')
    print(f'{len(rows)} source files {"updated" if args.apply else "would change"}; no gameplay files removed')

if __name__=='__main__':
    main()
