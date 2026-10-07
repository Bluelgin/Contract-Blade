"""Stage a public, encounter-free build without changing the development sources."""
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]).resolve()
assert OUT == (ROOT / 'build/stable-release/release-inputs').resolve()


def replace(text, old, new):
    assert old in text, f'Stable release adapter drift: {old[:90]}'
    return text.replace(old, new)


def source(text, name):
    if name == 'MaidWeaponMod.java':
        text = re.sub(r'        net\.minecraftforge\.fml\.ModLoadingContext\.get\(\)\.registerConfig\(net\.minecraftforge\.fml\.config\.ModConfig\.Type\.CLIENT,\s*com\.maidweapon\.common\.BlackFoxClientConfig\.SPEC, "maid_weapon-client.toml"\);\n', '', text)
        for line in ['com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat.bootstrap(modBus);',
                     'com.maidweapon.forge.compat.fox.BlackFoxBossCompat.bootstrap(modBus);',
                     'com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters.setup();',
                     'com.maidweapon.forge.compat.BlackFoxSlashCompat.bootstrap();']:
            text = replace(text, line, '')
    elif name == 'BladeTetraStoryCompat.java':
        start = text.index('    /** Read-only session check:')
        end = text.index('    /** Recognize an awakened blade', start)
        text = text[:start] + text[end:]
    elif name == 'ShrineFoxStory.java':
        text = replace(text, 'import com.maidweapon.forge.system.fox.challenge.ShrineRitualSites;\n', '')
        text = replace(text, 'BladeTetraStoryCompat.clearedRoute(player) != null', 'BladeTetraStoryCompat.hasCompletedDivinePrologue(player)')
        start = text.index('        boolean dreamHint =')
        end = text.index('        switch (beat)', start)
        text = text[:start] + '        if (beat == ShrineStoryPolicy.Beat.NONE && !firstObservation) return;\n' + text[end:]
        start = text.index('            case MEMORY -> {')
        end = text.index('            case ARRIVAL', start)
        text = text[:start] + '            case MEMORY -> { }\n' + text[end:]
        start = text.index('        if (dreamHint) {')
        end = text.index('        persisted.put(PROGRESS', start)
        text = text[:start] + text[end:]
    elif name == 'ShrineStoryPolicy.java':
        text = replace(text, 'return context.followingDay() && !progress.memoryHeard() ? Beat.MEMORY : Beat.NONE;', 'return Beat.NONE;')
    elif name == 'ShrineBladeStandMixin.java':
        text = replace(text, '\n                || com.maidweapon.forge.system.fox.challenge.BlackFoxShrineReturn.protectedStand((ItemFrame) (Object) this)', '')
        text = replace(text, '&& (!com.maidweapon.forge.system.fox.challenge.BlackFoxShrineReturn.allowTake((ItemFrame) (Object) this, serverPlayer, hand)\n                || !ShrineOfferingService.take((ItemFrame) (Object) this, serverPlayer, hand)))', '&& !ShrineOfferingService.take((ItemFrame) (Object) this, serverPlayer, hand))')
    elif name == 'FoxSpiritTransferService.java':
        text = replace(text, '        com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat.ensureEffect(blade);\n', '')
    elif name == 'FoxSpiritTransferValidation.java':
        start = text.index('        check(com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat.hasEffect(first[0])')
        end = text.index('        ItemStack original', start)
        text = text[:start] + text[end:]
    elif name == 'ShrineEntityProcessor.java':
        text = replace(text, 'import com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat;\n', '')
        start = text.index('        var blade = ItemStack.of(')
        end = text.index('        return new StructureTemplate.StructureEntityInfo', start)
        text = text[:start] + text[end:]
    assert 'fox.challenge.' not in text
    return text


for directory in ['java', 'resources']:
    dest = OUT / directory
    if dest.exists():
        shutil.rmtree(dest)  # Exact, validated generated build subtree only.
    dest.mkdir(parents=True)
    for path in (ROOT / 'src/main' / directory).rglob('*'):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT / 'src/main' / directory)
        name = path.name
        if directory == 'java':
            if '/challenge/' in '/' + rel.as_posix() or name.startswith(('BlackFox', 'FoxChallenge')) or name in ['SlashEffectProvenanceMixin.java', 'WhiteFoxPurifyingEdge.java', 'WhiteFoxSeValidation.java', 'WhiteFoxSpecialEffectCompat.java']:
                continue
            data = source(path.read_text(encoding='utf-8'), name).encode('utf-8')
        else:
            if any(x in rel.as_posix() for x in ['black_fox', 'fox_challenge', 'fox_passage.json', 'parry_success.png', 'bladesmith']):
                continue
            data = path.read_bytes()
            if name == 'maid_weapon.slashblade.mixins.json':
                obj = json.loads(data)
                obj['mixins'] = ['SlashBladeIngredientProtectionMixin', 'ShrineBladeStandMixin', 'SlashBladeDurabilityMixin']
                obj['client'] = []
                data = json.dumps(obj, indent=2).encode()
            elif rel.as_posix() == 'assets/maid_weapon/sounds.json':
                obj = json.loads(data)
                obj = {k: v for k, v in obj.items() if not k.startswith('black_fox')}
                data = json.dumps(obj, indent=2).encode()
            elif 'assets/maid_weapon/lang/' in rel.as_posix() and name.endswith('.json'):
                obj = json.loads(data)
                obj = {k: v for k, v in obj.items() if not any(s in k for s in ['.fox.challenge.', 'entity.maid_weapon.black_fox', 'fox_challenge', '.fox_passage.', 'purifying_edge'])}
                data = json.dumps(obj, ensure_ascii=False, indent=2).encode('utf-8')
        target = dest / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
print('Stable release staged: repairs and fox models; no Boss, arena, ritual, or combat SE.')
