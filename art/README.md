# 素材导航 / Asset index

这里是开发源素材与历史记录，不是可直接安装的资源包。运行资源位于
`src/main/resources`，分发边界由构建及对应打包脚本决定。

## 当前维护的素材

- `resonance/`：灵脉、无尽契约的 SVG 源文件、动画预览和导出说明。
- `fox_model_pack/`：双狐来源及像素图标；`fox_polish_preview/`：服装与尾巴适配源素材。
- `ysm-test/models/contract_white_fox`、`contract_black_fox`：已适配的 YSM 双狐资源。
- `ysm-test/taisho-flight-source.json`：普通飞行动作的来源与完整署名记录。
- `mod_list_logo/`：模组列表封面的原始图片；通过 `tools/optimize_mod_logo.py` 导出显示尺寸。
- 万华御札素材与导出入口见对应子目录和 `tools`；以正式资源引用为准。

## 历史或暂不发布

- `akatsuki/` 与 `ysm-test/models/contract_akatsuki`：赤月外观源文件，暂不随正式包提供。
- `black_fox/music/`：已撤下背景音乐的来源、许可与处理记录。
- `black_fox/music/retired-runtime/`：退役实现，不属于运行源集，勿复制回正式代码。
- `ysm-test/archived-releases/`：旧模型发布记录，不是当前附件。
- 各处 `before`、`revision-*`、预览帧及 `.bbmodel`：设计迭代留档，不整目录发布。

暂不大规模搬动：历史工具存在路径引用，应先检查再迁移。未提交的设计文件不自动删除或提交。
新增预览请尽量注明对应源文件、版本和用途；包内不需要的录屏、逐帧截图留在本地。

## 许可

统一来源见 [ASSET_SOURCES.md](../ASSET_SOURCES.md)，模型及动作遵守各自随附声明。
代码的 MIT 许可不覆盖所有第三方素材；不要删除署名、把未验证渲染冒充实机截图，
或将历史测试记录写成当前完整兼容承诺。

Editable assets and historical experiments are not installable packs. Preserve
credits and keep unfinished Akatsuki visuals and retired music out of releases.
See [YSM packaging](../docs/development/YSM_PACKAGING.md) and the individual source notices.
