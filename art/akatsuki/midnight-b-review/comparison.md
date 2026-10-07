# B 方案多视图对照

本轮仅生成参考与对照分析，没有修改模型。使用内置 image_gen，通过 imagegen 技能指导生成。

原图：`C:/Users/Administrator/AppData/Local/Temp/codex-clipboard-caa284bc-59c3-480f-bc03-621f75919e38.png`

多角度设计推演：`b-concept-turnaround-v1.png`

实模基准：`../midnight-b-refined/akatsuki-midnight-b-final.bbmodel`，对应目录中的 front / side / angle 预览。

## 边界

生成图保持了正面主要设计，但不是精确正交三视图。部分侧向视图仍带斜视，角度和透视不宜用于直接量尺寸；背面与不可见衣片是保守推演。不能把生成图未画尾巴当作删掉原模型尾巴的依据，不能用推演背面取代原模型的既有角色特征。头后月轮仍按此前约定不改。

## 主要差距

1. 外袍轮廓：参考是包覆、展开、错层翻折的袍服，实模仍接近直筒外罩加悬挂前衣片。要先校准整体轮廓，而不是继续堆分层块。
2. 宽斜襟：参考宽衣片与侧袍、内裙衔接成完整服装，实模的阶梯边、独立前伸和板状厚度仍明显。衣片连接检查通过并不代表衣服形体已贴近。
3. 裙边与红衬：参考有明显斜向金边、弯折角和外层/内衬相互露出的错层，实模主要是接近水平的齐边，翻折角偏小。
4. 腰封与比例：实模黑色腰封像宽厚矩形条，收腰不足；参考更贴身，月扣与细绳的尺寸、位置关系更精致。
5. 内褶裙：实模前部褶皱体积不足，更多像条状明暗；参考内裙有明确的折面、相互遮挡和有节奏的褶脚。
6. 领口与肩衬：实模米白肩衬仍像贴在前面的矩形片；参考更像穿在外袍下面、沿肩和交领露出的内衣。
7. 材质纹样：实模酒红至黑紫的过渡偏均匀，金枝纹偏长而集中，月纹偏小；参考过渡沿褶皱形成不规则色块，金纹分布在衣片边缘且更零散克制。

建议下一次重建先确定无纹样的正面、侧面服装轮廓与折面，通过结构预览后再处理纹样。当前没有据此开始修改。

后续：用户批准完整推进后，已在 `../midnight-b-reconstructed/` 保存独立原生重构版本及四方向结构/配色预览。旧版未覆盖。收腰、斜襟折面、侧后包覆、斜切翻角红衬、内裙褶面与实体交领已重做；这是一次实际衣装迭代，不是与参考完全一致的认证。游戏内运动状态尚未验证，保留的头尾与暂缓修改的月轮仍与参考存在区别。

## 最终生成提示词

Use case: stylized-concept. Asset type: ONE five-view character turnaround sheet for evaluating a Minecraft Blockbench costume reconstruction. Image 1 is the SOLE visual reference and the identity/outfit anchor. Re-render this EXACT character and outfit at additional camera angles, not a new costume design. Wide horizontal sheet with FIVE evenly spaced full-body views, all the same scale and neutral lowered-arm stance: original front, front three-quarter, true side profile, rear three-quarter, back. Small plain labels 'FRONT', '3/4 FRONT', 'SIDE', '3/4 BACK', 'BACK' below the figures. Dark neutral charcoal studio backdrop and consistent soft studio lighting. Preserve the reference's Minecraft-style cuboidal anatomy, pixel-textured surfaces, burgundy layered fox hair and ears, amber eyes, ivory fox mask on the original screen-right side of her head (her left), gold-red crescent behind her head, body proportions, matte cloth, shoes and accessories. LOCK THE COSTUME: wide ivory crossed inner collar plus asymmetrical wine-red outer lapel; slim black obi with wine-red horizontal bands; prominent crescent gold buckle and fine red hanging cords with gold tips; voluminous wine-red bell sleeves with restrained gold twig embroidery, ivory turned cuffs and gold wrist detail. Most important: the skirt is a layered wraparound robe, NOT separated straight strips or dangling apron boards. A BROAD wine-red diagonal overlapping front garment runs continuously from the waist down toward original screen-right hem, widens below, and gradually blends into ink-purple/near-black at its lower edge, with small gold foliage along its slanted lower edge. On original screen-left is a curved/open ink-black outer robe with a clear gold crescent emblem, gold stepped inner hem edging and a curled bottom corner showing wine-red lining. A visibly separate central wine-red pleated inner skirt hangs behind the diagonal front lap. The outer robe wraps around hips and sides coherently with red reverse lining and asymmetrical, folded, staggered hems. Keep garment volume and hem profiles from the reference, no stiff rectangular planks, no uniform narrow red stripe. Every view must describe the SAME garment construction and maintain the same front-design asymmetry (do not mirror it between angles). Preserve the sharp pixel-block aesthetic; do not turn it into a smooth anime drawing or photoreal human. Unseen back surfaces are conservative continuations of this robe, not invented major features: do not add large rear bows, armor, weapons, extra halos or new tails. No redesign, no extra props, no watermark. This is a conceptual inferred turnaround, not a claim to reconstruct hidden geometry exactly.
