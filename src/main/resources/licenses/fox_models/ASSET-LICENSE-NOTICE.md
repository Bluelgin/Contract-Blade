# 双狐模型许可与来源说明 / White & Black Fox asset notice

整理日期 / Prepared: 2026-10-07

本文件是随包附带的来源、许可及修改说明，不是原作者签署的授权证书，不新增或扩大原有授权，不代表获得商业授权。
This notice records supplied provenance and derivative changes. It is not a signed authorization certificate, a new license grant, or permission for commercial use.

## 覆盖资源 / Covered assets

- 白狐 / White Fox: `contract_fox:fox_white` geometry, textures and companion animations.
- 黑狐 / Black Fox: `contract_fox:fox_black` geometry, textures and companion animations.
- 上述模型派生的独立黑狐 Boss 几何与贴图也保留模型许可；另见 `licenses/black_fox/`。
- Standalone Boss geometry/texture derivatives retain this model license; see `licenses/black_fox/`.

## 来源与署名 / Source and attribution

Original Wine Fox: TartaricAcid / 酒石酸菌 and Touhou Little Maid model-pack contributors.
Source adaptation: Blade Tetra contributors; supplied Akatsuki Wine Fox 1.2.3 source and model pack.
White/Black Fox derivative and pack adaptation: the authors credited in the user-supplied archive; subsequent Contract Blade modifications listed below.

Upstream project: https://github.com/TartaricAcid/TouhouLittleMaid
Unmodified supplied archive: `modelpacks/contract-fox-1.0.0.zip`
SHA-256: `7fc3517d572a09964e97fe42ae68f45c3ad98b108a44b170039341dc0afbe308`
Runtime derivative: `modelpacks/contract-fox-1.0.1.zip`

完整原始署名见同目录 `CREDITS.md` 及模型包中的同名文件。已有署名不因界面简写为 Very Many Authors 而省略。
The full supplied attribution remains in CREDITS.md; the shortened in-game author label does not replace it. Original authors do not endorse this derivative.

## 许可 / License

Model assets: Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0).
License: https://creativecommons.org/licenses/by-nc-sa/4.0/
完整条款 / Full terms: `LICENSE-CC-BY-NC-SA-4.0.txt` in this directory and inside the model archive.

再发布时保留来源与署名、许可链接和修改说明；模型及其衍生物限非商业使用，衍生物须遵守相同许可。需要商业使用时，应向相关权利人另行取得授权，本说明不能替代该授权。实际权利范围以附带完整许可和原始来源为准。
Redistributions must retain attribution, provenance, license and modification notices. Model assets are noncommercial and share-alike; obtain separate authorization from the relevant rights holders for commercial use. This project notice does not supply that authorization.

项目代码许可不覆盖这些模型；PeriTune 音乐的 CC BY 4.0 也不会改变模型的非商业限制。拔刀剑动作转译使用的独立 MIT 通知见 `licenses/black_fox/SlashBlade-animation-MIT.txt`，不替代模型许可。
Code, model and music licenses are separate. The music's commercial permissions do not relicense the fox models. The separate SlashBlade animation MIT notice does not replace the model license.

## Contract Blade 修改记录 / Derivative changes

- 移除可见月冠几何，保留同名骨骼与模型 ID；调整介绍及像素图标。
- 修正头发内部色块、袖口、手、肩部毛发与衣服主体颜色，贴合腰间装饰。
- 相同贴图区域合并到 512×512 图集，不下采样非重复细节；白狐移除完全被遮挡的不透明面，黑狐保留面以供透明渲染。
- 保留原有女仆活动动画、命名骨骼和模型 ID；Boss 使用独立战斗渲染/动画和刀鞘定位，不修改本体酒狐模型。
- 本次仅补齐许可说明与校验记录，不修改角色造型。

Removed visible crown cubes while retaining named bones/IDs; refined hair, hands, cuffs, shoulder fur, garment colors and fitted ornaments; repacked identical atlas regions at 512×512 without downsampling unique detail; omitted fully occluded opaque White Fox faces, retaining Black Fox faces for transparency. Existing companion animation files remain unchanged. The Boss has its own combat animation/rendering and sheath locator. This documentation update does not change the character appearance or upstream Wine Fox.

模型包中 `MODEL_ASSET_SHA256SUMS.txt` 标识具体模型、纹理与动画文件；`SHA256SUMS.txt` 标识包内所有非校验表资源。校验表用于完整性核对，不是作者签名或法律授权证据。
Checksums identify the actual asset files; they are integrity records, not author signatures or legal authorization evidence.
