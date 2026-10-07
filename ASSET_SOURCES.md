# Asset sources

## Akatsuki signature katana and swordplay (2026-10-07)

`assets/maid_weapon/akatsuki/katana.json` is original small cuboid cosmetic geometry
following this project's wine-red, black and gold concept. The one-pixel neutral
material is generated procedurally; no concept bitmap or character texture is
resampled into it. Short attack curves adapt the existing approved SlashBlade
Combo B sample (amplitude/direction/recovery changes); its original MIT copyright
and license are included separately in `licenses/akatsuki/SlashBlade-animation-MIT.txt`.
Draw/sheathing curves, grip solver and client-only state machine are project additions.
The external Akatsuki/TLM character pack is not copied, redistributed, overwritten
or relicensed by this change. Its original notices continue to apply.

`contract_interior_key.png`, `resonance_sword_tassel.png`, `guardian_ribbon.png`
and `heartbound_knot.png` are original 32 × 32 pixel-art item textures. The approved
editable SVG sources are in `art/contract_refresh/v2/`; they contain no third-party
artwork. The visual reference was the user-provided Touhou Little Maid item screenshots;
no textures from that mod are redistributed as part of those item textures. Earlier concepts remain in
`art/contract_baubles/` and `art/contract_interior/` for reference, not as shipping sources.

`art/contract_refresh/v2/preview_motion.cjs --install` renders the same approved
frames used by the GIF previews into native Minecraft vertical PNG strips.
Each texture has 80 frames, two game ticks per frame (eight seconds), without
interpolation. The key has an occasional metal-edge highlight; the tassel's
two strands bend with a slight delay, while the ribbon and knot have occasional
tip movement and long rests. Attachment points stay fixed. Running the script
without `--install` writes only previews under `art/contract_refresh/v2/motion/`.

Contract Blade's item textures are original project assets. The credited
White/Black Fox model pack below is a separately licensed derivative, not an
original asset covered by the project's code license.

`dog_blade.png` and `cat_blade.png` are original 32 × 32 pixel-art textures.
Their editable SVG sources are in `art/pet_blades/` and use the diagonal sword
silhouette and compact palette of this project's existing item art.

`contract_fragments.png` is an original 32 × 32 Patchouli handbook icon. It was
generated specifically for this project from the user's magic-book reference,
then cleaned and resized for Minecraft's item atlas.

`textures/gui/spirit/akatsuki.png` is an original 16 × 16 HUD emblem derived
from the project-owned Akatsuki pixel-art concept sheet. The concept sheet is
stored at `art/akatsuki/akatsuki_concept.png`; the shipped emblem was
redrawn at native Minecraft resolution by Blade Tetra's asset build script.

Goety, Iron's Spells 'n Spellbooks, Touhou Little Maid: Spell, SlashBlade, and
the True POWER addons are detected at runtime. Their mod JARs are not bundled in
this repository or in the release JAR.

Akatsuki intentionally has no bundled entity model. Its identity and contract
are provided by the addon, while players choose any installed Touhou Little
Maid model. The selected `ModelId` is retained in the spirit's complete entity
data across recall and redeployment.

`data/maid_weapon/structures/shinkitsu_shrine.nbt` is the user's original
Shinkitsu Shrine build supplied from the paused BladeChronicle project.
The runtime copy keeps the authored blocks and SlashBlade White Fox offering,
removes test/gallery entities and fixes a corrupted sign. It contains only
vanilla block references and SlashBlade entity/item state; no third-party
textures, models or code are bundled. The source structure is left untouched.

`modelpacks/contract-fox-1.0.0.zip` is the user-supplied White/Black Fox Touhou
Little Maid model pack, copied unchanged from the BladeChronicle project's
release directory. SHA-256:
`7fc3517d572a09964e97fe42ae68f45c3ad98b108a44b170039341dc0afbe308`.
Original Wine Fox: TartaricAcid / 酒石酸菌 and Touhou Little Maid model-pack
contributors; source adaptation: Blade Tetra contributors; derivative fox
characters and pack adaptation: the supplied pack's authors. It includes its
own `CREDITS.md` and full CC BY-NC-SA 4.0 license. These assets retain that
license; the project's code license does not replace it.
The original archive remains untouched as the source reference. The native
loader receives `modelpacks/contract-fox-1.0.1.zip`, a CC BY-NC-SA derivative
built by `tools/build_fox_model_pack.py`: only the crown geometry removal,
player-facing metadata, pack icon and checksum/credit records change. Character
textures and all animation resources remain byte-identical. The icon is a
hand-authored SVG pixel portrait saved at `art/fox_model_pack/fox_pixel_icon.svg`.
The earlier generated emblem remains archived but is no longer used in the pack.
An exact known original installed under our managed filename can be upgraded
with a recoverable `.bak` copy. Edited managed files and external packs with
the same IDs are preserved. Black Fox's model registration is not an
implementation of its Boss or rescue story.

The independent encounter renderer uses `assets/maid_weapon/geo/black_fox_boss.json`
and `textures/entity/black_fox_boss.png`, extracted from that same credited fox
pack. The texture is unchanged; geometry receives its own identifier and a
battle-only hip attachment bone, `BossSheathLocator`. The companion source pack is unchanged.
`tools/build_black_fox_boss_assets.py` derives ordinary walking, a neutral idle,
stagger/defeat reactions and tail motion, without companion work/health-transform
controllers. Mental attacks retain neutral poses; native Combo B now uses the
user-approved fox retarget of SlashBlade's player animation.
Credits and the full asset license are retained under `licenses/black_fox/`.
The source is SlashBlade_2 `assets/slashblade/model/pa/player_motion.vmd`,
retargeted offline in `art/slashblade_animations/combo_b/`. The seven static
native-state clips are exported by `tools/build_black_fox_combo.cjs`; the
upstream MIT notice is included at `licenses/black_fox/SlashBlade-animation-MIT.txt`.
There is no native VMD parser or player-animation dependency in the Boss renderer;
SlashBlade weapon models/textures are loaded from the installed provider, not redistributed.

The deferred archive `art/bladesmith/archive/contract-bladesmith-1.0.0.zip` is
not shipped or automatically installed. It contains the user-supplied Travelling
Bladesmith Wine Fox model. Its unchanged source copy is
`art/bladesmith/source/travelling-bladesmith.bbmodel`, SHA-256
`57c91ab7f6fba18f0fc70ffcc395712eaa89f8a389d3b25176181603f789a3d7`.
Native Blockbench export respects disabled export flags. The embedded character
texture is preserved byte-for-byte. Native maid activity animations derive from
the supplied single-tail clips and the credited Wine Fox / White Fox activity
library; this human-only model does not use the fox transformation controller.
Original Wine Fox: TartaricAcid and Touhou Little Maid contributors; source
adaptation: Blade Tetra contributors; Bladesmith character: user-supplied model.
The pack retains CC BY-NC-SA 4.0 and full license/credit files. Its icon is a
hand-authored SVG pixel portrait in `art/fox_model_pack/bladesmith_pixel_icon.svg`. Model registration
does not implement the Bladesmith rescue story or a new NPC behavior system.

## Black Fox contact flash

The user-approved, hand-authored pixel SVG `art/black_fox/parry_contact.svg`
is converted by `tools/build_black_fox_contact.cjs` to the transparent 64×64
`assets/maid_weapon/textures/particle/parry_contact.png`. This original contact
effect is displayed in world space; it is not part of a borrowed character model.

## Black Fox dimension breach and grand slash

The original SVG concepts in `art/black_fox/dimension_strike/` depict an angular
purple spatial rift and a layered purple-white slash. `build-preview.cjs` generates
the editable keyframes and an illustrative GIF; `build-game-assets.cjs` rasterizes
the two transparent SVG sources to `textures/effect/black_fox_rift.png` and
`textures/effect/black_fox_great_slash.png`. No Devourer of Gods sprite, texture or
animation is copied. The simplified fox silhouette is preview-only, not a model.
Game animation is sampled from the shared skill timeline, not GIF playback.

The local `black_fox_energy` GLSL program adds moving edge/core energy and a soft
additive halo in the no-shader-pack renderer. `build-energy-masks.cjs` derives
packed edge/halo/core masks from these original SVG textures, with transparent
padding. No third-party shader code or shader-pack assets are included. The
opt-in `shader-preview` frames are actual offscreen GPU renders of this program.

## Fox garment and texture refinement (2026-10-06)

The approved editable derivatives live in `art/fox_polish_preview/sleeve/`.
The `fox_*_optimized.bbmodel` candidates refine hair, sleeve/hand textures,
shoulder fur, robe fabric and fitted waist ornaments. Their 512px atlases share
identical RGBA regions without downsampling nonuniform detail. The White Fox
omits fully enclosed opaque faces; all Black Fox faces remain for translucent
Boss rendering. Native Blockbench Bedrock exports provide runtime geometry.
Existing model IDs, native activity animation files and the full credited
license are preserved. `tools/build_fox_model_pack.py` builds this current look;
`--original-look` is reserved for rebuilding the earlier visual derivative.
The standalone Boss extracts the same approved Black Fox geometry/texture and
retains its independent battle animation library and hip equipment attachment.

## Fox model license notice and encounter music (2026-10-07)

Both fox companion models retain CC BY-NC-SA 4.0. The runtime model archive
now includes a bilingual `ASSET-LICENSE-NOTICE.md`, original credits/full license
and model-only SHA-256 records; copies are also shipped under `licenses/fox_models/`.
These are provenance notices, not signed authorization certificates or a new
commercial license. The unchanged supplied archive remains the reference.

The development Black Fox soundtrack is `EpicBattle_J` by PeriTune, originally
published 2021-09-16. Official sources: https://peritune.com/blog/2021/09/16/epicbattle_j/
and https://peritune.com/about/. The author states pre-March-2026 tracks remain
under CC BY 4.0. `sounds/black_fox/epicbattle_j_intro.ogg` retains the first
0.24075-second unique lead-in from the official MP3 and transcodes it to Vorbis;
`epicbattle_j_loop.ogg` is derived from the author's OGG loop. Both runtime
files use 48 kHz stereo Vorbis quality 3; original downloads remain unchanged.
Playback applies a three-second smooth gain fade-in. The loop already includes
the opening musical passage; the short lead-in is followed by that loop in one
streaming channel, avoiding a redundant complete cycle. Recurring motifs
are retained. No YouTube cover art or photograph is bundled. Attribution,
full CC BY 4.0 terms, modification notes and file hashes are shipped under
`licenses/black_fox_music/`. This music license does not relicense the fox models.
Encounter-free stable builds exclude both the soundtrack and its sound event.

## Mod-list White Fox screenshot

`src/main/resources/white-fox-mod-list.png` is a deterministic 485×340 crop
of the player's supplied screenshot `da5b291d29c05cf4d5b76996012d4cce.png`.
The crop retains White Fox, the complete sword, stone support and nearby
ice/forest terrain. No AI repainting, background substitution or character
changes were applied. Reproduce using `tools/build_mod_list_image.ps1`.
It is a gameplay screenshot supplied for this project's Mod-list display,
not a new MIT grant for depicted third-party game/model assets. Underlying
model notices remain in `licenses/fox_models/`; this does not relicense
Minecraft or third-party resource/shader assets visible in the screenshot.

## Black Fox pixel boss bar (2026-10-07)

The approved second concept is hand-redrawn on an integer SVG pixel grid in
`art/black_fox/boss_presentation/black-fox-bar.svg`. The build script
`tools/build_black_fox_boss_bar.cjs` produces the transparent 214x96 runtime
atlas. The concept bitmap is not resampled into the game. Fox-tail and knot
details, three styles, and five procedural parry marks follow the approved
minimal direction. `runtime-bar-preview.png` is an actual offscreen Minecraft
GUI render. No artwork from a third-party game's boss HUD was copied.
