"""Read a Java structure NBT without installing third-party Python packages."""
import gzip
import json
import struct
import sys
from collections import Counter
from pathlib import Path


def read_structure(path):
    data = gzip.decompress(Path(path).read_bytes())
    offset = 0

    def unpack(fmt):
        nonlocal offset
        size = struct.calcsize(fmt)
        value = struct.unpack_from(fmt, data, offset)
        offset += size
        return value[0] if len(value) == 1 else value

    def string():
        nonlocal offset
        size = unpack('>H')
        value = data[offset:offset + size].decode('utf-8')
        offset += size
        return value

    def payload(kind):
        nonlocal offset
        if kind in (1, 2, 3, 4, 5, 6):
            return unpack({1: '>b', 2: '>h', 3: '>i', 4: '>q', 5: '>f', 6: '>d'}[kind])
        if kind == 7:
            size = unpack('>i')
            value = list(data[offset:offset + size])
            offset += size
            return value
        if kind == 8:
            return string()
        if kind == 9:
            element, size = unpack('>b'), unpack('>i')
            return [payload(element) for _ in range(size)]
        if kind == 10:
            result = {}
            while (child := unpack('>b')) != 0:
                name = string()
                result[name] = payload(child)
            return result
        if kind in (11, 12):
            return [unpack('>i' if kind == 11 else '>q') for _ in range(unpack('>i'))]
        raise ValueError(f'Unknown NBT type {kind}')

    kind = unpack('>b')
    string()
    return payload(kind)


if __name__ == '__main__':
    root = read_structure(sys.argv[1])
    palette = root.get('palette', [])
    counts = Counter(palette[b['state']]['Name'] for b in root['blocks'])
    print(json.dumps({
        'size': root['size'], 'data_version': root.get('DataVersion'),
        'blocks': counts, 'entities': root.get('entities', []),
        'block_entities': [b for b in root['blocks'] if 'nbt' in b],
    }, ensure_ascii=False, indent=2))
