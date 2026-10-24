# Resonance item art — crystal growth revision

Flowing Vein and Endless Vein now use the approved `redesign-v4` crystal-growth
silhouettes: branching lavender crystal followed by a fuller crystal crown.
They retain Awakening Vein's lavender/cream, warm gold and restrained cyan palette.
Awakening Vein and Endless Contract runtime textures were deliberately left unchanged.

Approved designs follow the mod's existing pink/cyan cordwork, warm gold fittings,
plum/navy pixel outlines and lavender crystals. The four base SVGs were manually
redrawn after a built-in imagegen concept using the current tassel, ribbon, knot
and interior-key textures as style references. No generated bitmap is used as a
game texture.

## Motion

- Awakening Vein: gentle crystal-facet light and a small vein shimmer.
- Flowing Vein: a rising crystal highlight and a short cyan current.
- Endless Vein: three staggered crystal highlights and a lower cyan current.
- Endless Contract: a breathing crystal heart, upper gold-arc glint and slightly
  offset silk-tip bends. The knot and attachment points remain fixed.

All four native textures are transparent **32×1280 PNG strips**, 40 frames with
`frametime: 2` and `interpolate: false`: one loop is 80 game ticks / 4 seconds.
Silk movement uses integer-pixel scanline offsets, not whole-item bobbing.

`item-preview.gif` is the approved animated comparison; `item-preview.png` includes
16×16 readability checks, and `motion-phases.png` compares the tassels' phases.

Rebuild from the editable SVG source with:

```sh
python tools/render_resonance_icons.py
```

Requires cairosvg and Pillow. Set `CONTRACT_ART_FONT` to a local Noto CJK font to
render Chinese preview captions; without one the script uses English captions.
Fonts affect only review graphics, not game textures. Optional `--export-svg`
and `--individual` export standalone animated SVGs and individual GIFs; these
large derived previews are not required in the repository or game resources.

`resvg-py` may substitute for CairoSVG on platforms without Cairo. To update
only the two revised runtime strips while refreshing the comparison preview:

```sh
python tools/render_resonance_icons.py --items flowing_vein endless_vein
```
