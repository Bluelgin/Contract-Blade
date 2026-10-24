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

# Encounter messages announce the fight, not the provider mod's basic controls.
for locale, strings in langs.items():
    warning = strings['maid_weapon.fox.boss.warning'].lower()
    assert warning.strip(), locale
    for control in ('shift', 'sneak', 'thorns', '潜行', '荆棘', '格挡'):
        assert control not in warning, (locale, control)
print('BOSS_WARNING_PASS: challenge announcement without basic-control tutorial')

simple = (ROOT / 'src/main/java/com/maidweapon/forge/client/tooltip/SimpleItemTooltipHandler.java').read_text(encoding='utf-8')
presentation = (ROOT / 'src/main/java/com/maidweapon/forge/client/tooltip/SimpleTooltipPresentation.java').read_text(encoding='utf-8')
assert 'value = Dist.CLIENT' in simple and 'Screen.hasShiftDown()' in simple
assert 'projection.manual_work' not in simple, 'Projection tooltips should only describe item effects'
assert 'if (expanded) details.accept(tooltip);' in presentation
assert 'else {' in presentation and 'simple.shift' in presentation
assert 'if (spirit.isEmpty() && !red)' in composer, 'Ordinary and fox carriers must remain distinct'
for name in ('ContractProjectionBaubleItem', 'DanmakuTalismanItem', 'ResonanceUpgradeItem', 'ContractInteriorKeyItem'):
    item = (ROOT / f'src/main/java/com/maidweapon/forge/item/{name}.java').read_text(encoding='utf-8')
    assert 'appendHoverText' not in item and 'net.minecraft.client' not in item
    assert name in simple
for name in ('SPIRIT_CRYSTAL', 'MAID_INJECTOR', 'CAT_BLADE', 'MAID_HEAVY', 'MAID_FAST', 'TEST_SLASHBLADE'):
    assert name in simple
required = ('shift', 'endless_contract.lore', 'vein.1.lore', 'vein.2.lore', 'vein.3.lore', 'vein.use',
            'manka.equip', 'crystal.lore', 'crystal.use', 'crystal.effect', 'key.lore', 'table.lore',
            'table.use', 'cat.lore', 'dog.lore', 'blade.lore', 'heavy.lore', 'fast.lore', 'blade.bind',
            'bound.lore', 'soul.lore', 'emergency.lore', 'legacy_film.lore')
for locale, strings in langs.items():
    for suffix in required:
        assert strings['maid_weapon.tooltip.simple.' + suffix], (locale, suffix)
print('SIMPLE_TOOLTIP_PASS: lore/details replacement, client-only input and ordinary item coverage')

for name in ('Akatsuki', 'BlackFox', 'WhiteFox'):
    section = (ROOT / f'src/main/java/com/maidweapon/forge/client/tooltip/{name}TooltipSection.java').read_text(encoding='utf-8')
    assert 'if (!expanded) return;' in section
    assert 'tooltip.section.power' not in section
assert 'if (!expanded)' in composer and 'FoxTooltipProgress.append(tooltip, route)' in composer
assert 'tooltip.shift.expanded' not in composer
assert 'static void tooltip(' not in (ROOT / 'src/main/java/com/maidweapon/forge/event/BlackFoxSwordArtEvents.java').read_text(encoding='utf-8')
for locale, strings in langs.items():
    assert len(strings['maid_weapon.tooltip.lore.white']) < 100
    assert len(strings['maid_weapon.tooltip.lore.black']) < 100
    assert len(strings['maid_weapon.tooltip.lore.red']) < 100
print('FOX_TOOLTIP_PASS: short lore, Shift-only skills, server milestone preview and no duplicate SA line')
