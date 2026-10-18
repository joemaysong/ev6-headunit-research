"""Apply project hooks to matching owner inputs in a new local output only."""
import argparse
import hashlib
import json
import re
import struct
import sys
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'tools'))
from compare_oem_smali import normalize

METHOD = re.compile(r'^\.method [\s\S]*?^\.end method', re.M)


def canonical(text):
    return '\n'.join(normalize(text)) + '\n'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def contract(directory):
    return {p.relative_to(directory).as_posix(): sha(canonical(p.read_text()).encode())
            for p in sorted(directory.rglob('*.smali'))}


def patch_tree(kind, source, output):
    recipe = json.loads((HERE / 'patches' / (kind + '.json')).read_text())
    actual = contract(source)
    if actual != recipe['baseline_classes']:
        changed = sorted(k for k in actual.keys() | recipe['baseline_classes'].keys()
                         if actual.get(k) != recipe['baseline_classes'].get(k))
        raise ValueError('Preserved OEM baseline differs; no fuzzy patch: ' + ', '.join(changed[:12]))
    transformed = {}
    for relative in actual:
        text = canonical((source / relative).read_text())
        operations = recipe['changes'].get(relative, {})
        lines = text.splitlines()
        for entry in reversed(operations.get('pre_edits', [])):
            start, count = entry['line'], entry['delete']
            if sha('\n'.join(lines[start:start + count]).encode()) != entry['input_sha256']:
                raise ValueError('Legacy initialization/hook scope differs: ' + relative)
            lines[start:start + count] = entry['replacement']
        text = '\n'.join(lines) + '\n'
        for entry in operations.get('wrap', []):
            old = entry['original_declaration']
            if text.splitlines().count(old) != 1:
                raise ValueError('Original method declaration not unique: ' + relative)
            text = text.replace(old + '\n', entry['preserved_declaration'] + '\n', 1)
            text += '\n' + entry['wrapper'] + '\n'
        for entry in operations.get('edits', []):
            blocks = [m for m in METHOD.finditer(text)
                      if m.group().splitlines()[0].split()[-1] == entry['method']]
            if len(blocks) != 1:
                raise ValueError('Expected method not unique: ' + relative)
            block = blocks[0]
            # Match whole canonical lines; never alter a substring of another instruction.
            body = '\n' + block.group() + '\n'
            old, new = '\n' + entry['old'] + '\n', '\n' + entry['new'] + '\n'
            if body.count(old) != entry['count']:
                raise ValueError('Hook instruction count differs: ' + relative + ':' + entry['method'])
            body = body.replace(old, new)[1:-1]
            text = text[:block.start()] + body + text[block.end():]
        for addition in operations.get('add_methods', []):
            signature = addition.splitlines()[0].split()[-1]
            if any(m.group().splitlines()[0].split()[-1] == signature for m in METHOD.finditer(text)):
                raise ValueError('New method already present: ' + signature)
            text += '\n' + addition + '\n'
        transformed[relative] = canonical(text)
    if {k: sha(v.encode()) for k, v in transformed.items()} != recipe['expected_oem_classes']:
        raise ValueError('Patched OEM instruction contract differs from frozen V16')
    output.mkdir(parents=True, exist_ok=False)
    for relative, text in transformed.items():
        dest = output / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(text)
    return {'oem_classes': len(actual), 'changed_classes': sorted(recipe['changes']),
            'frozen_oem_contract_matched': True, 'helpers_still_require_compilation': True}


def patch_bytes(source, output):
    recipe = json.loads((HERE / 'patches/btsettings-v16.json').read_text())
    data = bytearray(source.read_bytes())
    if len(data) != recipe['bytes'] or sha(data) != recipe['input_sha256']:
        raise ValueError('BtSettings original differs from the verified firmware')
    for entry in recipe['edits']:
        offset, replacement = entry['offset'], bytes(entry['replacement'])
        if sha(data[offset:offset + len(replacement)]) != entry['input_sha256']:
            raise ValueError('Replacement scope differs')
        data[offset:offset + len(replacement)] = replacement
    offset, size = struct.unpack_from('<II', data, 8)
    dex = bytearray(data[offset:offset + size])
    dex[12:32] = hashlib.sha1(dex[32:]).digest()
    struct.pack_into('<I', dex, 8, zlib.adler32(dex[12:]) & 0xffffffff)
    data[offset:offset + size] = dex
    if sha(data) != recipe['output_sha256']:
        raise ValueError('Result does not reproduce the accepted settings artifact')
    with output.open('xb') as stream:
        stream.write(data)
    return {'sha256': sha(data), 'exact_frozen_output': True}


def patch_feature(source, output):
    data = source.read_bytes()
    profile = json.loads((HERE.parent / 'profiles/v16-release.json').read_text())
    if sha(data) != profile['originals']['featurexml.cfg']['sha256']:
        raise ValueError('Feature configuration is not the reference original')
    line = b'-/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml'
    if data.count(line) != 1:
        raise ValueError('Feature exclusion must occur once')
    data = data.replace(line + b'\n', b'', 1)
    if sha(data) != profile['assets']['featurexml.cfg']['sha256']:
        raise ValueError('Feature result differs from the reference final')
    with output.open('xb') as stream:
        stream.write(data)
    return {'sha256': sha(data), 'exact_frozen_output': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('component', choices=['dipo-v16', 'bluetooth-v14', 'btsettings', 'feature'])
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    source, output = args.input.resolve(), args.output.resolve()
    if output.exists() or output == source or output == Path('/dev') or Path('/dev') in output.parents:
        parser.error('Output must be a new ordinary local path')
    if source.is_dir() and source in output.parents:
        parser.error('Output must be outside the original input tree')
    if args.component in ('dipo-v16', 'bluetooth-v14'):
        result = patch_tree(args.component, source, output)
    elif args.component == 'btsettings':
        result = patch_bytes(source, output)
    else:
        result = patch_feature(source, output)
    print(json.dumps(dict(result, installed=False, vehicle_used=False)))


if __name__ == '__main__':
    main()
