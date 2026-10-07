"""Standalone Boss resource and lifecycle boundaries; real combat/client fixtures are separate."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
JAVA = ROOT / 'src/main/java/com/maidweapon/forge'
geometry = json.loads((RES / 'assets/maid_weapon/geo/black_fox_boss.json').read_text(encoding='utf-8'))
bones = {bone['name'] for bone in geometry['minecraft:geometry'][0]['bones']}
assert {'Root', 'Head', 'UpperBody', 'AllBody', 'FOX', 'LeftArm', 'RightArm', 'LeftLeg', 'RightLeg',
        'BladeLocator', 'SheathLocator', 'BossSheathLocator', 'Tail', 'LeftSpirit_Tail', 'RightSpirit_Tail'} <= bones
assert 'FoxBladeSheath' not in bones
animation_path = RES / 'assets/maid_weapon/animations/black_fox_boss.animation.json'
animation_text = animation_path.read_text(encoding='utf-8')
animation = json.loads(animation_text)['animations']
assert set(animation) == {f'contract_fox_boss.{name}' for name in ('walk', 'ready', 'stagger', 'defeated', 'tails')}
combo_text = (RES / 'assets/maid_weapon/animations/black_fox_combo_b.animation.json').read_text(encoding='utf-8')
combo = json.loads(combo_text)['animations']
assert set(combo) == {f'contract_fox_boss.combo_b{i}' for i in range(1, 8)}
assert 'ysm.' not in combo_text and 'tlm.' not in combo_text
for clip in combo.values():
    assert set(clip['bones']) <= bones
    assert not {'FOX', 'Tail', 'LeftSpirit_Tail', 'RightSpirit_Tail'} & set(clip['bones'])
    assert 0 < clip['animation_length'] <= 1.1
assert (RES / 'licenses/black_fox/SlashBlade-animation-MIT.txt').is_file()
assert 'ysm.' not in animation_text and 'tlm.' not in animation_text
for clip in animation.values():
    assert set(clip['bones']) <= bones
with zipfile.ZipFile(RES / 'modelpacks/contract-fox-1.0.1.zip') as source:
    assert hashlib.sha256(source.read('assets/contract_fox/textures/entity/fox_black.png')).digest() == hashlib.sha256(
        (RES / 'assets/maid_weapon/textures/entity/black_fox_boss.png').read_bytes()).digest()
assert (RES / 'licenses/black_fox/CREDITS.md').is_file()
assert (RES / 'licenses/black_fox/LICENSE-CC-BY-NC-SA-4.0.txt').is_file()
assert not (RES / 'modelpacks/black-fox-combat-1.0.0.zip').exists()
assert not (RES / 'maid_weapon.fox_client.mixins.json').exists()
entity = (JAVA / 'entity/BlackFoxBossEntity.java').read_text(encoding='utf-8')
assert 'extends Monster implements BlackFoxCombatant' in entity
assert 'setNoAi(' not in entity and 'getHealth() > before' not in entity
assert 'shouldDespawnInPeaceful() { return false; }' in entity
assert 'BlackFoxSlashCompat.disableStun(this)' in entity
renderer = (JAVA / 'compat/fox/BlackFoxBossRenderer.java').read_text(encoding='utf-8')
assert 'void renderLate(' not in renderer
assert renderer.index('super.render(model,') < renderer.index('renderEquipment(model,')
controller = (JAVA / 'system/fox/challenge/BlackFoxController.java').read_text(encoding='utf-8')
assert 'freshComboB' in controller and 'impactPause' not in controller
skills = (JAVA / 'system/fox/challenge/BlackFoxSkills.java').read_text(encoding='utf-8')
pursuit = skills.split('private static void pursuit(', 1)[1].split('private static void sky(', 1)[0]
assert 'nativeCombo().tick()' in pursuit and 'actions.slash(' not in pursuit and '.hurt(' not in pursuit
actions = (JAVA / 'system/fox/challenge/BlackFoxActions.java').read_text(encoding='utf-8')
corruption = (JAVA / 'system/fox/challenge/BlackFoxCorruption.java').read_text(encoding='utf-8')
assert 'new BlackFoxDomainAttacks(actor)' in controller
assert 'new BlackFoxDomainAttacks(' not in corruption
assert 'actor.combat().nativeCombo()' not in actions
assert 'actor.combat().phaseOne()' not in actions
assert 'actor.combat().corruption()' not in actions
assert 'case APPROACH -> approach(player)' in (JAVA / 'system/fox/challenge/BlackFoxPhaseOne.java').read_text(encoding='utf-8')
rifts = (JAVA / 'system/fox/challenge/BlackFoxRifts.java').read_text(encoding='utf-8')
assert 'visuals.forEach(Entity::discard)' in rifts and 'rifts.clear()' in corruption
assert 'rifts.spawn(player, center())' in corruption and 'rifts.tick(player)' in corruption
native_combo = (JAVA / 'compat/BlackFoxNativeCombo.java').read_text(encoding='utf-8')
capture = native_combo.split('public void capture(', 1)[1].split('public void stop(', 1)[0]
assert 'BlackFoxSlashColors.blade(' in capture and 'setRank' not in capture
slash_colors = (JAVA / 'compat/fox/mixin/BlackFoxSlashColorMixin.java').read_text(encoding='utf-8')
assert 'projectile.getOwner() instanceof BlackFoxBossEntity' in slash_colors
assert 'setRank' not in slash_colors and 'getRankCode' not in slash_colors
mixins = json.loads((RES / 'maid_weapon.slashblade.mixins.json').read_text(encoding='utf-8'))
assert 'BlackFoxSlashColorMixin' in mixins['client'] and 'BlackFoxSlashColorMixin' not in mixins['mixins']
for name in ('black_fox_rift','black_fox_great_slash'):
    assert (RES / f'assets/maid_weapon/textures/effect/{name}.png').is_file()
dimension = (JAVA / 'system/fox/challenge/BlackFoxDimensionStrike.java').read_text(encoding='utf-8')
assert 'if (age == HIT)' in dimension and 'actions.parry(player)' in dimension
assert 'actions.fight.clash()' in dimension and 'BlackFoxDimensionTimeline.*' in dimension
dimension_effects = (JAVA / 'compat/fox/BlackFoxDimensionEffects.java').read_text(encoding='utf-8')
assert 'BlackFoxDimensionTimeline.*' in dimension_effects and 'skillAge(partial)' in dimension_effects
assert 'addFreshEntity' not in dimension_effects and '.hurt(' not in dimension_effects
energy_shader = json.loads((RES / 'assets/maid_weapon/shaders/core/black_fox_energy.json').read_text(encoding='utf-8'))
assert energy_shader['blend']['dstrgb'] == 'one'
assert 'UV2' in energy_shader['attributes']
vertex_shader = (RES / 'assets/maid_weapon/shaders/core/black_fox_energy.vsh').read_text(encoding='utf-8')
assert 'effectData = vec2' in vertex_shader and 'uniform float' not in vertex_shader
energy_backend = (JAVA / 'compat/fox/BlackFoxEnergyShader.java').read_text(encoding='utf-8')
assert 'RegisterShadersEvent' in energy_backend and 'LEQUAL_DEPTH_TEST' in energy_backend and 'COLOR_WRITE' in energy_backend
for name in ('black_fox_rift_energy','black_fox_great_slash_energy'):
    assert (RES / f'assets/maid_weapon/textures/effect/{name}.png').is_file()
assert not (JAVA / 'compat/fox/BlackFoxVmdMotion.java').exists()
assert 'nativeMotion' not in (JAVA / 'compat/fox/BlackFoxBossResources.java').read_text(encoding='utf-8')
for source_name in ('EntityMaid', 'IMaid', 'setOwnerUUID', 'setModelId', 'com.github.tartaricacid'):
    assert source_name not in entity
for filename in ('BlackFoxBossClient.java', 'BlackFoxBossRenderer.java', 'BlackFoxBossPose.java', 'BlackFoxBossResources.java'):
    text = (JAVA / f'compat/fox/{filename}').read_text(encoding='utf-8')
    for forbidden in ('import com.github.tartaricacid.touhoulittlemaid.entity.', 'EntityMaidRenderer',
                      'import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid', 'CustomPackLoader',
                      'GeckoMaidEntityCapability', 'AnimationManager.getInstance()'):
        assert forbidden not in text, (filename, forbidden)
assert not (JAVA / 'compat/fox/BlackFoxMaidEntity.java').exists()
assert not (JAVA / 'compat/fox/BlackFoxBossModel.java').exists()
for locale in ('zh_cn', 'en_us'):
    lang = json.loads((RES / f'assets/maid_weapon/lang/{locale}.json').read_text(encoding='utf-8'))
    assert lang['entity.maid_weapon.black_fox_boss']
    for suffix in ('warning', 'phase', 'clash', 'defeated'):
        assert lang[f'maid_weapon.fox.boss.{suffix}']
    assert lang['patchouli.maid_weapon.entry.fox_passage.page.3']
formation = (JAVA / 'system/fox/challenge/BlackFoxSwordFormation.java').read_text(encoding='utf-8')
assert 'FIRST_HOLD = 14, SECOND_HOLD = 10' in formation
assert 'LOCK_LEAD = 4' in formation and 'target = player.getEyePosition()' in formation
assert 'BlackFoxPhantomCompat.spawn' in formation and 'BlackFoxPhantomCompat.fire' in formation
assert 'second ? 1.5f : 1.2f' in formation and 'slot * (second ? 2 : 3)' in formation
assert 'sword.entity().discard()' in formation
rift_visual = (JAVA / 'compat/fox/BlackFoxSwordRifts.java').read_text(encoding='utf-8')
assert 'BlackFoxRiftVisual.draw' in rift_visual and 'BlackFoxSwordFormation.fireAge' in rift_visual
assert 'BlackFoxSwordRifts.draw(entity' in renderer
assert 'BlackFoxHeldSwordMixin' in mixins['mixins']
print('BLACK_FOX_RESOURCES_PASS: standalone renderer, native sword formation, reused rift visual, private animations and credits')
