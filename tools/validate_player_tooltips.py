"""Check localized player-facing contract strings and keep technical archives out of the tooltip composer."""
from pathlib import Path
import json
import re
ROOT = Path(__file__).resolve().parents[1]
langs = {lang: json.loads((ROOT / f'src/main/resources/assets/maid_weapon/lang/{lang}.json').read_text()) for lang in ('zh_cn', 'en_us')}
composer = (ROOT / 'src/main/java/com/maidweapon/forge/client/tooltip/ContractTooltipComposer.java').read_text()
for forbidden in ('LegacySinArchive', 'legacy_sin_data', 'MaidBindingId', 'OwnerUUID', 'getMaidSlashBladeProviderName', 'REVIEW_REQUIRED', 'RECOVERY_PENDING'):
    assert forbidden not in composer, forbidden
for key in langs['zh_cn']:
    if not key.startswith(('maid_weapon.tooltip.', 'maid_weapon.transfer.', 'maid_weapon.recovery.', 'maid_weapon.fox.', 'maid_weapon.white_blessing.', 'gui.maid_weapon.')):
        continue
    assert key in langs['en_us'], key
    zh = re.findall(r'%(?:\d+\$)?[dsf]', langs['zh_cn'][key])
    en = re.findall(r'%(?:\d+\$)?[dsf]', langs['en_us'][key])
    assert zh == en, (key, zh, en)
for lang, strings in langs.items():
    for key in ('maid_weapon.transfer.soul', 'maid_weapon.transfer.emergency', 'maid_weapon.recovery.native_backup', 'maid_weapon.tooltip.status.away'):
        assert strings[key], (lang, key)
print('PLAYER_TOOLTIP_PASS: compact gameplay text, archive removal, bilingual keys and format arguments')
