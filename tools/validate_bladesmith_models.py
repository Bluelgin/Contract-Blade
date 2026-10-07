"""Structural and actual native TLM parser verification, without claiming visual QA."""
import base64
import hashlib
import json
import struct
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HERE = ROOT / 'tools/tlm-validator'
source_path = ROOT / 'art/bladesmith/source/travelling-bladesmith.bbmodel'
assert hashlib.sha256(source_path.read_bytes()).hexdigest() == '57c91ab7f6fba18f0fc70ffcc395712eaa89f8a389d3b25176181603f789a3d7'
source = json.loads(source_path.read_text(encoding='utf-8'))
pack = ROOT / 'art/bladesmith/archive/contract-bladesmith-1.0.0.zip'
extracted = ROOT / 'build/bladesmith-parser-pack'
with zipfile.ZipFile(pack) as archive:
    for line in archive.read('SHA256SUMS.txt').decode().splitlines():
        digest, name = line.split('  ', 1)
        assert hashlib.sha256(archive.read(name)).hexdigest() == digest
    prefix = 'assets/contract_bladesmith/'
    texture = archive.read(prefix + 'textures/entity/travelling_bladesmith.png')
    assert texture == base64.b64decode(source['textures'][0]['source'].split(',', 1)[1])
    assert struct.unpack('>II', texture[16:24]) == (1024, 512)
    geometry = json.loads(archive.read(prefix + 'models/entity/travelling_bladesmith.json'))['minecraft:geometry'][0]
    names = {bone['name'] for bone in geometry['bones']}
    assert len(names) == len(geometry['bones']) == 136
    assert sum(len(b.get('cubes', [])) for b in geometry['bones']) == sum(
        e.get('export', True) for e in source['elements']) == 431
    for bone in geometry['bones']:
        assert 'parent' not in bone or bone['parent'] in names
    for name in archive.namelist():
        if name.endswith('.animation.json'):
            for clip in json.loads(archive.read(name))['animations'].values():
                assert set(clip.get('bones', {})) <= names
                assert 'scale' not in clip.get('bones', {}).get('AllBody', {})
    main = json.loads(archive.read(prefix + 'animation/travelling_bladesmith.main.animation.json'))['animations']
    for activity in ('idle', 'walk', 'run', 'sleep', 'chair', 'computer', 'attacked'):
        assert activity in main, activity
    assert main['parallel3']['bones']['DrinkFace']['scale'] == [0, 0, 0]
    extracted.mkdir(parents=True, exist_ok=True)
    archive.extractall(extracted)

jdk = Path('C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot/bin')
mod = Path('C:/Users/Administrator/.gradle/caches/forge_gradle/deobf_dependencies/local/touhoulittlemaid/1.5.3-forge+mc1.20.1_mapped_official_1.20.1/touhoulittlemaid-1.5.3-forge+mc1.20.1_mapped_official_1.20.1.jar')
cp = (ROOT / 'build/classpath/runServer_minecraftClasspath.txt').read_text().splitlines()
classpath = ';'.join([str(mod)] + [p for p in cp if p.endswith('.jar') and Path(p).is_file()])
classes = HERE / 'classes'
classes.mkdir(exist_ok=True)
def quote(value):
    return '"' + str(value).replace('\\', '\\\\').replace('"', '\\"') + '"'
args = HERE / 'bladesmith-compile.args'
args.write_text('-encoding\nUTF-8\n-classpath\n'+quote(classpath)+'\n-d\nclasses\nBladesmithPackValidator.java\n')
subprocess.run([str(jdk/'javac.exe'), '@'+args.name], cwd=HERE, check=True)
args = HERE / 'bladesmith-runtime.args'
args.write_text('-Djava.awt.headless=true\n-classpath\n'+quote('classes;'+classpath)+'\nBladesmithPackValidator\n'+quote(extracted)+'\n'+quote(ROOT/'art/bladesmith/runtime/native-parser-validation.json')+'\n')
subprocess.run([str(jdk/'java.exe'), '@'+args.name], cwd=HERE, check=True)
print('BLADESMITH_STRUCTURE_AND_NATIVE_PARSER_PASS')
