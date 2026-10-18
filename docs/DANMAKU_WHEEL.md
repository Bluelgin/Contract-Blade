# 朱樱四式轮盘 (Zhu-ying Four Spell Wheel)

Art direction: original hand-drawn SVG reference for the Manka Talisman A
variant, reproduced at runtime with vanilla GuiGraphics colored scanline quads.
No Shader or optional rendering dependency. The wheel's warm-gold rim, crimson
petal-shaped spell icon, ivory center talisman and four dark plum sectors follow
the talisman item palette.

Controls: short press the configurable key (middle mouse by default) to cast
the last selected form; hold six client ticks to open the wheel; move to a valid
sector, release to select without casting. The cursor's central dead zone and
outer boundary do not change the current choice. Locked Lv.10 FANTASY is dimmed,
shows its unlock level, and never commits before the required mastery. Original
pick-block interception remains conditional on authenticated eligibility.
Server spell authorization remains unchanged.

Wheel design:
- top: NEEDLE / 灵针
- right: FLOWER / 散华
- left: GUARD / 护身
- bottom: FANTASY / 幻想万华

Rendering duties live in DanmakuWheelPainter; pointer hit testing lives in
DanmakuWheelGeometry. DanmakuWheelScreen only owns GUI lifecycle and input.
The centered real item icon uses the Minecraft item renderer, so the same
talisman animation appears without maintaining a second thumbnail.
