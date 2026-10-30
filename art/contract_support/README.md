# 契约辅助饰品

两张 32×32 透明像素贴图：同契之证与护主结。

- 同契之证：已确认的斜向幻灵剑。先使用内置生图工具生成像素概念，再参考概念重绘整数网格 SVG；青白剑刃、紫色灵影与缠绕灵光表现力量注入。概念与草案位于 `spectral-sword/`，概念大图不打包进游戏。
- 护主结：采用已确认的组纽第二版。先使用内置生图工具生成红橙编织绳结概念，再参考概念重绘整数网格 SVG，保留交替绳股、中央穿插结、浅金束扣与长短流苏。概念、源稿及对照图位于 `kumihimo-v2/`；借用《你的名字。》组纽表达的联系意象，没有复制影片画面或原物件图案。

早期候选参考过 Botania 官方魔力戒指的轮廓可读性与明暗组织；没有复制或打包第三方像素。未采用的候选不随正式源稿提交，也不导出到游戏。

- 风格参考：https://botaniamod.net/lexicon.html
- 官方贴图：https://raw.githubusercontent.com/VazkiiMods/Botania/1.20.x/Xplat/src/main/resources/assets/botania/textures/item/mana_ring.png

源文件为同目录 SVG，项目许可适用。安装 `resvg-py` 与 `Pillow` 后，在仓库根目录执行 `python art/contract_support/export.py`；预览文字使用 Windows 微软雅黑。导出会更新正式贴图与 `preview.png`，预览采用最近邻放大。
