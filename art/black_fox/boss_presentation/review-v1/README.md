# 黑狐 Boss 血条与音乐：第一版待审

状态：WAITING_FOR_USER_APPROVAL。所有候选尚未接入游戏。

本目录不在 src/main/resources 内；未修改 HUD、sounds.json、播放逻辑或发行包。音乐只有候选链接，尚未下载、裁剪或转码。

## 血条

`healthbar-concept-v1.png` 是使用内置生图模型生成的概念图，不是可直接投产的贴图。

从上到下：常态、二阶段污染、失衡。黑铁与暗金刀鞘轮廓、紧凑狐耳端饰、紫色填充和五枚振刀标记。确认后才拆分边框、填充、污染覆层与标记，制作透明素材并验证实际 HUD 尺寸。文字、填充长度、五枚标记亮灭/破碎与污染动画由渲染控制，不直接使用整张概念图。图中示例点亮两枚仅为造型参考；实际第五次触发失衡时应统一破碎。

待检查：左右端饰是否过大；暗金是否保留；二阶段紫光强度；五枚标记的形状；缩小后的辨识度。黑洞时的封缄覆层与核心血条仍需在主造型确认后出稿，不能视为已完成。

## 音乐候选

以下定位仅根据作者曲目介绍、乐器与节奏资料推断，尚未实际试听或确认循环点。不承诺曲目之间具有同一旋律，也不承诺无 Content ID 认领。

| 建议位置 | 候选 | 作者目录信息 | 官方试听/曲目页 |
| --- | --- | --- | --- |
| 第一阶段 | Mountain Emperor — Kevin MacLeod | 3:20；120 BPM；太鼓、甘美兰；作者标注 Action / Driving / Mystical | https://incompetech.com/music/royalty-free/index.html?Search=Search&isrc=USUAN1700012 |
| 第二阶段 | Darkling — Kevin MacLeod | 2:50；140 BPM；打击乐、弦乐、木管；作者标注 Action / Driving / Dark，介绍指出适合循环 | https://incompetech.com/music/royalty-free/index.html?Search=Search&isrc=USUAN1700050 |
| 黑洞/污染氛围备选 | Dragon and Toast — Kevin MacLeod | 作者现行目录 5:53（搜索索引曾标 5:52）；100 BPM；极简配器；作者标注 Dark / Eerie / Intense / Mystical / Unnerving | https://incompetech.com/music/royalty-free/index.html?Search=Search&isrc=USUAN1100251 |

如果第三首不够强烈，可以从已批准的第二阶段曲目选择更紧张的片段，而非一定加入第三首。第一阶段太鼓方案也未必符合拨弦旋律目标，最终以用户试听为准。

## 授权核对

核对日期：2026-10-06。

三首官方曲目页的署名区均标注 Creative Commons: By Attribution 4.0；作者官方页面动态读取 pieces.json 曲目目录。只使用作者的官方来源，不混用其他站点的不同授权或下载版本。

- 官方目录：https://incompetech.com/music/royalty-free/pieces.json
- 作者授权说明：https://incompetech.com/music/royalty-free/licenses/
- CC BY 4.0：https://creativecommons.org/licenses/by/4.0/

CC BY 4.0 允许商用、修改及再分发，需要适当署名、来源/授权链接、修改说明；不能对这些音乐附加限制其授权权利的条款。项目代码授权与音乐授权应分别标注。确认曲目后，在下载时保存相应授权证据并记录文件哈希；剪辑、循环、转码等修改据实填写。仍需检查实际使用/录像可能出现的版权认领，授权不等于绝无认领。

候选署名模板（尚非已使用名单）：

“Mountain Emperor” / “Darkling” / “Dragon and Toast” — Kevin MacLeod (incompetech.com).
Licensed under Creative Commons Attribution 4.0: https://creativecommons.org/licenses/by/4.0/
Source: 对应上述各曲目的官方链接。
Modifications: 未使用、未修改；确定并处理后再填写。

## 接入门槛

血条与音乐分别确认。未确认的候选不得复制进运行时资源。主血条确认后，继续提交拆层透明素材、核心血条/封缄状态及小尺寸预览；音乐确认后，提交循环/过渡试听、音量方案与署名文件，全部检查通过才接入。播放控制需要统一生命周期，处理进场、阶段切换、破核、死亡、离场与断线；保留关闭/音量设置，并让出振刀与技能提示音。
