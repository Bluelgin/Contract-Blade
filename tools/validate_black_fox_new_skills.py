"""Pure selection/timing regression plus narrow architecture checks for the new phase-one skills."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/maidweapon/forge'
FIGHT = JAVA / 'system/fox/challenge'
with tempfile.TemporaryDirectory(prefix='black-fox-skills-') as out:
    sources = ['BlackFoxFight', 'BlackFoxTactics', 'BlackFoxSpacing', 'BlackFoxSwordplay',
               'BlackFoxDimensionTimeline', 'BlackFoxOpenings', 'BlackFoxSkillPool', 'BlackFoxIaidoTimeline', 'BlackFoxStandingIaidoTimeline', 'BlackFoxSlashPresentation', 'BlackFoxBalance', 'BlackFoxDomainPool', 'BlackFoxDomainCadence']
    subprocess.run(['javac', '-d', out, *[str(FIGHT / (name + '.java')) for name in sources],
                    str(ROOT / 'tools/tests/BlackFoxFightTest.java'),
                    str(ROOT / 'tools/tests/BlackFoxSkillPoolTest.java'),
                    str(ROOT / 'tools/tests/BlackFoxSlashPresentationTest.java'),
                    str(ROOT / 'tools/tests/BlackFoxDomainPoolTest.java')], check=True)
    subprocess.run(['java', '-cp', out, 'BlackFoxFightTest'], check=True)
    subprocess.run(['java', '-cp', out, 'BlackFoxSkillPoolTest'], check=True)
    subprocess.run(['java', '-cp', out, 'BlackFoxSlashPresentationTest'], check=True)
    subprocess.run(['java', '-cp', out, 'BlackFoxDomainPoolTest'], check=True)
phase = (FIGHT / 'BlackFoxPhaseOne.java').read_text(encoding='utf-8')
assert 'BlackFoxRiftCross' in phase and 'BlackFoxArcBarrage.tick' in phase
assert 'nextInt(Integer.MAX_VALUE)' in phase and 'phaseOne.opportunisticHit()' in (FIGHT / 'BlackFoxController.java').read_text(encoding='utf-8')
assert 'BlackFoxEffects.contact' in phase and 'protectParry()' in phase
assert 'BlackFoxRiftVisual.draw' in (JAVA / 'compat/fox/BlackFoxCrossRifts.java').read_text(encoding='utf-8')
print('BLACK_FOX_NEW_SKILLS_ARCHITECTURE_PASS: separate executors, shared openings, native cuts and reused portal rendering')
