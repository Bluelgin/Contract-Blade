# 万华御札 / Manka Talisman

> 历史方案与实现记录，可能包含已撤回功能、旧数值和当时的测试结果。
> 当前说明从 [文档导航](../../README.md) 进入；本页不作为现行玩法指南。

Scope: ordinary, current active, owner-authorized contract maid channels, including
maids captured with the contract altar and regular Contract Blade, **only while the maid is stored in the held weapon**. Innate sword-spirit
weapons are excluded. Dormant archive channels cannot trigger skills. The talisman
must exist in the **maid's native TLM bauble slots**: the player's own inventory is
not an alternative; stored maid snapshots are validated and read through the same
native codec/cached effects resolver used by Endless Contract.

## Fixed weapon enhancement (no ContractMastery dependency)

Combat choreography and the precise new level table are maintained in
[ORDINARY_CONTRACT_DANMAKU_REWORK.md](../../development/MANKA_TALISMAN.md).

All four spells are available immediately: Needles (12 shots), Blossom (24),
Guard (six real interceptions), and Fantasy Manka (four waves, 32 shots).
Each eligible ordinary attack fires four tracking bullets; the third
consecutive hit on one target fires six. No mastery or talisman XP is used,
while held weapon attack damage and resonance remain authoritative.
Middle-mouse tap-to-cast / six-tick hold-to-select controls remain unchanged.

A server-only `DanmakuHitBudget` authorizes one ordinary TLM hit per
target per wave, never damage from a miss. Multi-target FLOWER has a strict
three-target ceiling per wave. The same boss caps, phases, hurt immunity, armor
and damage protections remain enforced by native damage mechanics.
Client key packets contain no damage or permission authority.
Lost contract/weapon identity cancels all pending shots.

**Historical initial design (superseded by [SPIRIT_RESONANCE_COSTS.md](../../development/SPIRIT_RESONANCE_COSTS.md)):** all skills cost zero in
this iteration; no further Sword Spirit cost policy has been adopted.

## Art

The final A design, **朱樱纸御札**, is authored entirely with SVG vector paths:
cream paper, a crimson knot, a gold bell, a sakura crest and a restrained tassel.
It replaces the original item icon. No generated-image files or bitmap layers
are used to produce the art. The editor source is `art/danmaku/manka_talisman.svg`.

The game sprite is a native 32x1280 PNG strip (40 frames of 32x32), played at
2 ticks/frame without interpolation via `manka_talisman.png.mcmeta`.
Motion consists of seal breathing, bell/knot glints, slow tassel motion and petal drift.
To regenerate the PNG strip from SVG, install `Pillow` and `CairoSVG`,
then run `python art/danmaku/export_manka_animation.py`.
The exporter quantizes the final 32px sprite to a compact 256-color indexed PNG.

## Validation

Run `./gradlew test`, the existing static resource checks and a dedicated server
with TLM 1.5.3. Client manual tests are still required for short/long presses,
alternative key bindings, rapid item switching, native maid bauble changes, GUI
mouse release, server latency, 16×16 icon legibility, and optional-mod stacks.
