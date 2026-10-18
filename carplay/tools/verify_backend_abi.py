"""Resolve every internal DEX method reference against assembled definitions."""
from pathlib import Path
import re


def verify(directory):
    definitions = {}
    for path in Path(directory).rglob('*.smali'):
        text = path.read_text()
        owner = re.search(r'^\.class (?:.* )?(L[^;]+;)$', text, re.M).group(1)
        parent = re.search(r'^\.super (L[^;]+;)$', text, re.M)
        methods = {}
        for declaration in re.findall(r'^\.method (.+)$', text, re.M):
            tokens = declaration.split()
            methods[tokens[-1]] = 'static' in tokens[:-1]
        definitions[owner] = (methods, parent.group(1) if parent else None, text)
    count = 0
    for caller, (_, _, text) in definitions.items():
        for kind, owner, signature in re.findall(
                r'^\s*invoke-(\S+)\s+\{[^}]*\},\s*(L[^;]+;)->([^\s]+)', text, re.M):
            if owner not in definitions:
                continue
            original = owner
            seen = set()
            while owner in definitions and owner not in seen:
                seen.add(owner)
                methods, parent, _ = definitions[owner]
                if signature in methods:
                    if kind.startswith('static') != methods[signature]:
                        raise RuntimeError('Internal static/instance ABI differs: ' + caller + ' -> ' + original + signature)
                    count += 1
                    break
                # Constructors cannot be inherited.
                if signature.startswith('<init>'):
                    raise RuntimeError('Missing internal constructor: ' + caller + ' -> ' + original + signature)
                owner = parent
            else:
                # An inherited Android/JDK method cannot be resolved here.
                if owner is None or (owner in definitions):
                    raise RuntimeError('Unresolved internal method: ' + caller + ' -> ' + original + signature)
                continue
    return {'resolved_internal_method_references': count, 'classes': len(definitions)}


if __name__ == '__main__':
    import argparse
    import json
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    print(json.dumps(verify(parser.parse_args().directory)))
