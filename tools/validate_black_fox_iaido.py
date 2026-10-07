"""Pure time/flow checks plus narrow asset and renderer ownership invariants."""
from pathlib import Path
import json
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/maidweapon/forge'
with tempfile.TemporaryDirectory(prefix='black-fox-iaido-') as out:
    subprocess.run(['javac', '-d', out,
                    str(JAVA / 'system/fox/challenge/BlackFoxDimensionTimeline.java'),
                    str(JAVA / 'system/fox/challenge/BlackFoxIaidoTimeline.java'),
                    str(ROOT / 'tools/tests/BlackFoxIaidoTimelineTest.java')], check=True)
    subprocess.run(['java', '-cp', out, 'BlackFoxIaidoTimelineTest'], check=True)
resource = ROOT / 'src/main/resources/assets/maid_weapon'
clip = json.loads((resource / 'animations/black_fox_iaido.animation.json').read_text())['animations']['contract_fox_boss.iaido']
bones = {b['name'] for b in json.loads((resource / 'geo/black_fox_boss.json').read_text())['minecraft:geometry'][0]['bones']}
assert set(clip['bones']) <= bones
assert not any('Tail' in name for name in clip['bones'])
assert clip['animation_length'] == 2.8
effect = (JAVA / 'compat/fox/BlackFoxIaidoCharge.java').read_text()
assert 'BlackFoxEffectQuad.energy' in effect and 'BlackFoxRiftVisual.TEXTURE' in effect
assert 'prepMatrixForLocator' in effect and 'LeftHandLocator' in effect
assert 'addFreshEntity' not in effect and 'sendParticles' not in effect
renderer = (JAVA / 'compat/fox/BlackFoxBossRenderer.java').read_text()
assert renderer.index('BlackFoxIaidoCharge.draw') < renderer.index('if (bodyAlpha > .5f) renderEquipment')
print('BLACK_FOX_IAIDO_ARCHITECTURE_PASS: existing textures/shader, hand-bound anchor, independent tails, no effect entities')
