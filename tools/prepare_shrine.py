"""Prepare the runtime copy; never modify the artist's source structure.

NBT tag types are retained, including SlashBlade's full capability state.
Only gallery entities, one broken sign and the shrine provenance tag change.
"""
import gzip
import io
import json
import struct
import sys
from pathlib import Path

FORMATS = {1: '>b', 2: '>h', 3: '>i', 4: '>q', 5: '>f', 6: '>d'}


def read_tag(stream, kind):
    def number(fmt):
        return struct.unpack(fmt, stream.read(struct.calcsize(fmt)))[0]

    def string():
        return stream.read(number('>H')).decode('utf-8')

    if kind in FORMATS:
        value = number(FORMATS[kind])
    elif kind == 7:
        value = stream.read(number('>i'))
    elif kind == 8:
        value = string()
    elif kind == 9:
        child, count = number('>b'), number('>i')
        value = (child, [read_tag(stream, child) for _ in range(count)])
    elif kind == 10:
        value = {}
        while (child := number('>b')) != 0:
            name = string()
            value[name] = read_tag(stream, child)
    elif kind in (11, 12):
        value = [number('>i' if kind == 11 else '>q') for _ in range(number('>i'))]
    else:
        raise ValueError(kind)
    return kind, value


def encode(tag):
    kind, value = tag
    def string(text):
        raw = text.encode('utf-8')
        return struct.pack('>H', len(raw)) + raw
    if kind in FORMATS:
        return struct.pack(FORMATS[kind], value)
    if kind == 7:
        return struct.pack('>i', len(value)) + value
    if kind == 8:
        return string(value)
    if kind == 9:
        child, elements = value
        return struct.pack('>bi', child, len(elements)) + b''.join(map(encode, elements))
    if kind == 10:
        return b''.join(bytes([child[0]]) + string(name) + encode(child)
                        for name, child in value.items()) + b'\0'
    if kind in (11, 12):
        fmt = '>i' if kind == 11 else '>q'
        return struct.pack('>i', len(value)) + b''.join(struct.pack(fmt, x) for x in value)
    raise ValueError(kind)


def prepare(source, target):
    stream = io.BytesIO(gzip.decompress(source.read_bytes()))
    assert stream.read(1) == b'\x0a'
    name_length = struct.unpack('>H', stream.read(2))[0]
    root_name = stream.read(name_length)
    root = read_tag(stream, 10)
    entities = root[1]['entities'][1][1]
    offerings = []
    for entry in entities:
        entity = entry[1]['nbt'][1]
        if entity['id'][1] != 'slashblade:blade_stand_entity' or 'Item' not in entity:
            continue
        item = entity['Item'][1]
        state = item['tag'][1].get('bladeState', (10, {}))[1]
        if state.get('translationKey', (8, ''))[1] != 'item.slashblade.fox_white':
            continue
        item['tag'][1]['MaidWeaponShrineWhiteFox'] = (1, 1)
        # Import identity/location, not UUIDs from the author's test world.
        entity.pop('UUID', None)
        entity['Tags'] = (9, (8, [(8, 'maid_weapon_shrine_offering')]))
        offerings.append(entry)
    assert len(offerings) == 1, 'Expected exactly one White Fox offering'
    root[1]['entities'] = (9, (10, offerings))
    for entry in root[1]['blocks'][1][1]:
        block = entry[1]
        position = [child[1] for child in block['pos'][1][1]]
        if position == [6, 1, 23]:
            messages = ['神狐神社', '愿归途常明', '愿故人平安', '']
            block['nbt'][1]['front_text'][1]['messages'] = (9, (8, [
                (8, json.dumps({'text': line}, ensure_ascii=False)) for line in messages]))
    target.parent.mkdir(parents=True, exist_ok=True)
    output = b'\x0a' + struct.pack('>H', len(root_name)) + root_name + encode(root)
    target.write_bytes(gzip.compress(output, mtime=0))
    print(f'Prepared shrine: {target}; source unchanged; one White Fox offering')


if __name__ == '__main__':
    prepare(Path(sys.argv[1]), Path(__file__).resolve().parents[1] /
            'src/main/resources/data/maid_weapon/structures/shinkitsu_shrine.nbt')
