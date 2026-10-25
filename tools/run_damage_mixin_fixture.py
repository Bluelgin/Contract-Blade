"""Transform the production DamageObservationMixin using Mixin 0.8.5 and Extras 0.5.3.

Isolated simulation, not a Forge launch: only Minecraft/observation dependencies are
stubbed. The production wrapper bodies and annotations are compiled unchanged.
Requires JDK 17. Dependencies are cached outside the repository; --classpath can
supply offline jars instead. Logs/classes stay in --output (default a temp folder).
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--classpath', help='OS-separated Mixin, Extras, ASM, Guava, Gson jars')
parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '/usr')))
parser.add_argument('--output', type=Path)
parser.add_argument('--jar', type=Path, help='Also exercise the actual reobfuscated release mixin class')
args = parser.parse_args()
out = args.output or Path(tempfile.mkdtemp(prefix='cb-damage-mixin-'))
out.mkdir(parents=True, exist_ok=True)
if args.classpath:
    cp = args.classpath
else:
    deps = out / 'deps'
    deps.mkdir(exist_ok=True)
    artifacts = {
        'mixin.jar': 'https://maven.minecraftforge.net/org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar',
        'extras.jar': 'https://repo.maven.apache.org/maven2/io/github/llamalad7/mixinextras-common/0.5.3/mixinextras-common-0.5.3.jar',
        'guava.jar': 'https://repo.maven.apache.org/maven2/com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar',
        'gson.jar': 'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.10/gson-2.10.jar',
    }
    for module in ['asm', 'asm-tree', 'asm-commons', 'asm-util', 'asm-analysis']:
        artifacts[module + '.jar'] = f'https://repo.maven.apache.org/maven2/org/ow2/asm/{module}/9.6/{module}-9.6.jar'
    for name, url in artifacts.items():
        if not (deps / name).exists():
            with urllib.request.urlopen(url, timeout=90) as response:
                (deps / name).write_bytes(response.read())
    cp = os.pathsep.join(str(deps / name) for name in artifacts)

production = (ROOT / 'src/main/java/com/maidweapon/forge/mixin/DamageObservationMixin.java').read_text()
fixture_sources = list((ROOT / 'tools/mixin-fixture/src').rglob('*.java'))
for mapped, overloads, broken in [(False, False, False), (False, True, False),
                                   (True, False, False), (True, True, False),
                                   (False, True, True)]:
    label = ('srg' if mapped else 'mojmap') + ('-overloads' if overloads else '-plain') + ('-old-require0' if broken else '')
    case = out / label
    case.mkdir(exist_ok=True)
    classes = case / 'classes'
    classes.mkdir(exist_ok=True)
    names = ['m_6469_', 'm_21161_', 'm_6515_'] if mapped else ['hurt', 'getDamageAfterArmorAbsorb', 'getDamageAfterMagicAbsorb']
    hurt, armor, magic = names
    helper = ''
    if overloads:
        for name, ret, value in [(hurt, 'boolean', 'false'), (armor, 'float', '300f'), (magic, 'float', '477f')]:
            # Include both namespaces: production LivingEntity may contain Mojmap helper methods.
            for alias in {name, {'m_6469_': 'hurt', 'm_21161_': 'getDamageAfterArmorAbsorb', 'm_6515_': 'getDamageAfterMagicAbsorb'}.get(name, name)}:
                helper += f'private {ret} {alias}(DamageSource s, float n, LivingEntity other) {{ return {value}; }}\n'
        helpers = f'{hurt}(s, 1f, this); return {armor}(s, 1f, this) + {magic}(s, 1f, this);'
    else:
        helpers = 'return 777f;'
    target = case / 'LivingEntity.java'
    target.write_text(f'''package net.minecraft.world.entity;
import net.minecraft.world.damagesource.DamageSource;
public class LivingEntity {{
 {helper}
 public float health = 100f;
 public boolean {hurt}(DamageSource s, float n) {{
   if(n < 0) throw new IllegalArgumentException("original");
   if(n == 40f) {hurt}(s, 10f);
   health -= {magic}(s, {armor}(s,n)); return true;
 }}
 protected float {armor}(DamageSource s,float n) {{return n * .8f;}}
 protected float {magic}(DamageSource s,float n) {{return n * .5f;}}
 public float helpers(DamageSource s) {{ {helpers} }}
}}''')
    mixin = case / 'DamageObservationMixin.java'
    text = production
    if broken:
        text = re.sub(r'((?:getDamageAfter(?:Armor|Magic)Absorb|m_21161_|m_6515_))\(Lnet/minecraft/world/damagesource/DamageSource;F\)F', r'\1', text)
        text = text.replace('remap = false)', 'remap = false, require = 0)')
        # The class-level @Mixin has no require attribute.
        text = text.replace('@Mixin(value = LivingEntity.class, remap = false, require = 0)', '@Mixin(value = LivingEntity.class, remap = false)')
    mixin.write_text(text)
    subprocess.run([str(args.java_home / 'bin/javac'), '-proc:none', '-cp', cp, '-d', str(classes),
                    *map(str, fixture_sources), str(target), str(mixin)], check=True)
    if args.jar and not broken:
        entry = 'com/maidweapon/forge/mixin/DamageObservationMixin.class'
        with zipfile.ZipFile(args.jar) as jar:
            (classes / entry).write_bytes(jar.read(entry))
    services = classes / 'META-INF/services'
    services.mkdir(parents=True, exist_ok=True)
    (services / 'org.spongepowered.asm.service.IMixinService').write_text('fixture.FixtureService\n')
    (services / 'org.spongepowered.asm.service.IGlobalPropertyService').write_text('fixture.FixtureProperties\n')
    (classes / 'fixture.mixins.json').write_text('''{"required":true,"minVersion":"0.8","package":"com.maidweapon.forge.mixin", "compatibilityLevel":"JAVA_17", "mixins":["DamageObservationMixin"],"injectors":{"defaultRequire":1}}''')
    with (case / 'transform.log').open('w') as log:
        result = subprocess.run([str(args.java_home / 'bin/java'), '-cp', str(classes) + os.pathsep + cp,
                                 'fixture.FixtureRunner', hurt], stdout=log, stderr=subprocess.STDOUT)
    log = (case / 'transform.log').read_text()
    if broken:
        if result.returncode == 0 or 'InvalidInjectionException' not in log:
            raise SystemExit(f'{label}: expected old signature conflict; see {case}')
        print(f'PASS {label}: reproduced signature conflict despite require=0', flush=True)
    else:
        if result.returncode or 'MIXIN_OVERLOAD_PASS' not in log:
            raise SystemExit(f'{label}: failed; see {case / "transform.log"}')
        print(f'PASS {label}: exact transform, unchanged return/damage, defense observations, overload isolation, exception/nested cleanup', flush=True)
print(f'Evidence: {out}')
