"""Pure visual timeline checks; actual render checks live in the opt-in client fixture."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = root / 'src/main/java/com/maidweapon/forge'
with tempfile.TemporaryDirectory(prefix='black-fox-slash-') as out:
    subprocess.run(['javac', '-d', out,
                    str(java / 'system/fox/challenge/BlackFoxDimensionTimeline.java'),
                    str(java / 'system/fox/challenge/BlackFoxSlashTimeline.java'),
                    str(root / 'tools/tests/BlackFoxSlashTimelineTest.java')], check=True)
    subprocess.run(['java', '-cp', out, 'BlackFoxSlashTimelineTest'], check=True)
