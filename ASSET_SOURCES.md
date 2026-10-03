# Asset sources

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
