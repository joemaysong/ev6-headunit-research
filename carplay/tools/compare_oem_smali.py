"""Compare resolved smali after assembly, without equating different operands.

Debug positions and generated branch-label names are ignored. Instructions,
registers, constants, descriptors, annotations and exception tables are kept.
This establishes a textual instruction contract, not runtime equivalence.
"""
from pathlib import Path
import re

QUOTED = re.compile(r'("(?:[^"\\]|\\.)*")')
LABEL = re.compile(r'(?<![\w;])(:[A-Za-z_]\w*)')
DEBUG = ('.line ', '.local ', '.end local ', '.restart local ',
         '.prologue', '.epilogue')


def normalize(text):
    lines = [line.strip() for line in text.splitlines()
             if line.strip() and not line.lstrip().startswith(('#',) + DEBUG)]
    parameters = []
    for index, line in enumerate(lines):
        if line.startswith('.param '):
            # Parameter annotations are semantic. Retain their register binding;
            # remove only a debug-only parameter name/declaration.
            if index + 1 < len(lines) and lines[index + 1].startswith('.annotation '):
                parameters.append(line.split(',', 1)[0].split('#', 1)[0].strip())
        else:
            parameters.append(line)
    lines = parameters
    # Labels are method-local. Their definition order is independent of their
    # byte offsets. Keep separate identities for aliases, and reject dangling
    # references rather than silently normalizing an unresolved target.
    result = []
    labels = {}
    method = []

    def flush():
        payload = False
        for line in method:
            if line.startswith(('.packed-switch ', '.sparse-switch', '.array-data ')):
                payload = True
            if line.startswith(':') and not payload:
                if line in labels:
                    raise ValueError('Duplicate method-local label')
                labels[line] = ':label_' + str(len(labels))
            if line.startswith(('.end packed-switch', '.end sparse-switch', '.end array-data')):
                payload = False
        for line in method:
            parts = QUOTED.split(line)
            for index in range(0, len(parts), 2):
                def replace(match):
                    label = match.group(1)
                    if label not in labels:
                        raise ValueError('Unresolved method-local label: ' + label)
                    return labels[label]
                parts[index] = LABEL.sub(replace, parts[index])
            result.append(''.join(parts))
        method.clear()
        labels.clear()

    for line in lines:
        if line.startswith('.method '):
            flush()
        method.append(line)
        if line == '.end method':
            flush()
    flush()
    # DEX stores direct and virtual methods separately and sorts their IDs.
    # Compare method bodies by declaration, not source placement. Actual VM
    # virtual indices must still be checked independently before a rebuild.
    header=[];methods=[];block=None;signatures=set()
    for line in result:
        if line.startswith('.method '):
            if block is not None:raise ValueError('Nested method')
            block=[line]
            signature=line.split()[-1]
            if signature in signatures:raise ValueError('Duplicate method signature')
            signatures.add(signature)
        elif block is not None:
            if line.startswith('.locals '):
                parameters=block[0].split('(',1)[1].split(')',1)[0]
                types=re.findall(r'\[*[ZBCSIJFD]|\[*L[^;]+;',parameters)
                if ''.join(types)!=parameters:raise ValueError('Unsupported parameter descriptor')
                inputs=(0 if 'static' in block[0].split()[:-1] else 1)
                inputs+=sum(2 if t in ('J','D') else 1 for t in types)
                line='.registers '+str(int(line.split()[1],0)+inputs)
            block.append(line)
            if line=='.end method':methods.append(block);block=None
        else:header.append(line)
    if block is not None:raise ValueError('Unterminated method')
    return header+[line for method in sorted(methods,key=lambda m:m[0]) for line in method]


def compare(before, after):
    a = {p.relative_to(before): p for p in Path(before).rglob('*.smali')}
    b = {p.relative_to(after): p for p in Path(after).rglob('*.smali')}
    if a.keys() != b.keys():
        raise ValueError('Class inventory differs')
    changed = [str(name) for name in sorted(a)
               if normalize(a[name].read_text()) != normalize(b[name].read_text())]
    return {'classes': len(a), 'changed_classes': changed,
            'all_normalized_instruction_contracts_match': not changed,
            'runtime_equivalence_proven': False}


if __name__ == '__main__':
    import argparse
    import json
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    args = parser.parse_args()
    result = compare(args.before, args.after)
    print(json.dumps(result))
    if result['changed_classes']:
        raise SystemExit(1)
