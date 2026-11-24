#!/usr/bin/env python3
"""Export only shared code/tests/resources from a known baseline to a source ref.
Run from the target worktree. Default: check applicability; --apply stages nothing.
Never changes branches, platform sources, or pushes anything.
"""
import argparse
import subprocess
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("baseline",help="Git ref representing the shared baseline already present in target")
p.add_argument("source",help="Git ref containing the desired shared changes")
p.add_argument("--apply",action="store_true")
args=p.parse_args()
root=Path(subprocess.check_output(["git","rev-parse","--show-toplevel"],text=True).strip())
if subprocess.check_output(["git","status","--porcelain"],cwd=root).strip():
    raise SystemExit("Refusing non-clean target worktree")
patch=subprocess.check_output(["git","diff","--binary",args.baseline,args.source,"--","src/shared/java","src/sharedTest/java","src/shared/resources"],cwd=root)
if not patch:
    print("No shared changes");raise SystemExit(0)
subprocess.run(["git","apply","--check","-"],input=patch,cwd=root,check=True)
if args.apply:
    subprocess.run(["git","apply","-"],input=patch,cwd=root,check=True)
    print("Shared patch applied to working tree; review, run native tests, commit separately.")
else:
    print("Shared patch applicable; use --apply to modify the working tree.")
