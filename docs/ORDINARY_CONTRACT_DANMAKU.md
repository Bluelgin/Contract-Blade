# 万华御札 / Manka Talisman

Scope: ordinary, current active, owner-authorized contract maid channels, including
maids captured with the contract altar and regular Contract Blade. Innate sword-spirit
weapons are excluded. Dormant archive channels cannot trigger skills. The talisman
must exist in the **maid's native TLM bauble slots**: the player's own inventory is
not an alternative; stored maid snapshots are validated and read through the same
native codec/cached effects resolver used by Endless Contract.

## Combat progression (player-wide ContractMastery)

- Lv.1: two aimed companion pellets after an eligible melee damage event.
- Lv.2: three pellets, splitting the same bonus-damage budget.
- Lv.3: visual spread fundamentals.
- Lv.4: more accurate aimed follow-up.
- Lv.5: unlock all three active forms (NEEDLE, FLOWER, GUARD) and wheel selection.
- Lv.6: empower GUARD with a denser circular screen.
- Lv.7: spread gains two-stage density.
- Lv.8: a recent second hit on the same entity increases a salvo to four pellets.
- Lv.9: denser FLOWER spell.
- Lv.10: FANTASY three-colored circular spell card, resolved in three 8-tick-spaced waves.

Player input: the rebindable middle-mouse key is a short tap to cast the last selected
spell. Holding for six client ticks (~0.3s at 20 TPS) opens the four-direction
selection wheel; moving the pointer and releasing confirms the form **without
casting**. The original Pick Block mapping is intercepted only while the client has
a valid server-synchronized ordinary-contract eligibility snapshot and the key
conflicts. Pick Block is otherwise left intact. The network packet contains only
the selected form ID, never a maid UUID, damage, cost, or entitlement.

Server authorization is re-evaluated at every spell and every automatic follow-up.
Player global mastery determines the unlocked tier; nothing is stored on the
talisman or weapon. Damage follows TLM's native projectile damage source and ordinary
armor/invulnerability rules, with additional friendly-fire checks for player-owned
maids and animals. No bypass of damage caps, boss phases or invulnerability.
Total damage is budgeted across the entire array, not multiplied by bullet count.
Auto salvos are cooled down 120 ticks; active spells have separate per-maid/form
cooldowns. Rates and budget fractions live in DanmakuGrowth rather than the event
handler; additional balanced configurations can follow in a separate PR.

**Resonance consumption intentionally unimplemented:** DanmakuGrowth.resonanceCost
currently returns zero, and no ResonanceService.pay invocation is made here. This
is a design extension point for the later unified sword-spirit cost discussion.
The existing resonance resource balance and recovery remain unchanged.

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
