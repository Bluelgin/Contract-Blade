# Asset sources

`contract_interior_key.png`, `resonance_sword_tassel.png`, `guardian_ribbon.png`
and `heartbound_knot.png` are original 32 × 32 pixel-art item textures. The approved
editable SVG sources are in `art/contract_refresh/v2/`; they contain no third-party
artwork. The visual reference was the user-provided Touhou Little Maid item screenshots;
no textures from that mod are redistributed. Earlier concepts remain in
`art/contract_baubles/` and `art/contract_interior/` for reference, not as shipping sources.

`art/contract_refresh/v2/preview_motion.cjs --install` renders the same approved
frames used by the GIF previews into native Minecraft vertical PNG strips.
Each texture has 80 frames, two game ticks per frame (eight seconds), without
interpolation. The key has an occasional metal-edge highlight; the tassel's
two strands bend with a slight delay, while the ribbon and knot have occasional
tip movement and long rests. Attachment points stay fixed. Running the script
without `--install` writes only previews under `art/contract_refresh/v2/motion/`.

The textures and models distributed by Contract Blade are original project
assets and do not redistribute files from optional integrations.

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
the True POWER addons are detected at runtime. Their files are not bundled in
this repository or in the release JAR.

Akatsuki intentionally has no bundled entity model. Its identity and contract
are provided by the addon, while players choose any installed Touhou Little
Maid model. The selected `ModelId` is retained in the spirit's complete entity
data across recall and redeployment.
