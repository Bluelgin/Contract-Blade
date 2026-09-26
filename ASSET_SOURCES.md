# Asset sources

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
