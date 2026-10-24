"""Compatibility entry point: 1.2 stable builds are complete, not encounter-free."""
from validate_release_readiness import ROOT, VERSION, check_sources, check_jar

check_sources()
check_jar(ROOT / f'build/stable-release/libs/contract-blade-forge-1.20.1-{VERSION}.jar')
