#!/usr/bin/env python3
"""Review/sync the explicit shared tree boundary, never platform adapters.

Run from the target checkout. --check compares source against target HEAD.
Default checks a baseline->source patch; --apply applies it without staging.
"""
import argparse
from pathlib import Path
import subprocess

SHARED_PATHS = ('src/shared/java', 'src/sharedTest/java', 'src/shared/resources')


def git(root, *args, **kwargs):
    return subprocess.run(['git', *args], cwd=root, check=True,
                          stdout=subprocess.PIPE, **kwargs).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', help='Shared baseline already present in target')
    parser.add_argument('source', help='Ref with the desired shared changes')
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--check', action='store_true', help='Fail on shared drift against source')
    args = parser.parse_args()
    root = Path(git(None, 'rev-parse', '--show-toplevel').decode().strip())
    # Resolve once: branch movement cannot change what is reviewed/applied.
    baseline = git(root, 'rev-parse', '--verify', args.baseline + '^{commit}').decode().strip()
    source = git(root, 'rev-parse', '--verify', args.source + '^{commit}').decode().strip()
    target = git(root, 'rev-parse', 'HEAD').decode().strip()
    print(f'Shared direction: {source} -> target {target}; baseline {baseline}', flush=True)
    print('Allowed trees: ' + ', '.join(SHARED_PATHS), flush=True)
    if git(root, 'status', '--porcelain'):
        raise SystemExit('Refusing non-clean target worktree (including untracked files)')
    if args.check:
        drift = git(root, 'diff', '--name-status', target, source, '--', *SHARED_PATHS)
        if drift:
            raise SystemExit('Shared drift:\n' + drift.decode())
        print('SHARED_DRIFT_PASS')
        return
    patch = git(root, 'diff', '--binary', baseline, source, '--', *SHARED_PATHS)
    if not patch:
        print('No shared changes')
        return
    print(git(root, 'diff', '--stat', baseline, source, '--', *SHARED_PATHS).decode(), end='')
    try:
        git(root, 'apply', '--check', '-', input=patch)
    except subprocess.CalledProcessError:
        raise SystemExit('Shared patch conflicts with target; no files changed. Reconcile the listed shared files manually.')
    if args.apply:
        git(root, 'apply', '-', input=patch)
        print('Shared patch applied; review and run both platform checks before committing.')
    else:
        print('Shared patch applicable; use --apply to modify the working tree.')


if __name__ == '__main__':
    main()
