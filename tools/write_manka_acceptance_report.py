"""Write an evidence-based report from actual client QA, native logs and JUnit XML.
Unknown checks stay NOT RUN. This tool never invents runtime results or video frames.
"""
import argparse,json,os,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--seed-world',type=Path,required=True);a=p.parse_args()
out=a.output.resolve();root=Path(__file__).resolve().parents[1]
def obj(path,default):
 try:return json.loads(path.read_text())
 except (FileNotFoundError,json.JSONDecodeError):return default
qa=obj(out/'qa/runtime-qa.json',[]);videos=obj(out/'videos/video-validation.json',[])
xml=[ET.parse(p).getroot() for p in (root/'build/test-results/test').glob('TEST-*.xml')]
count=lambda k:sum(int(x.get(k,0)) for x in xml)
native=a.seed_world.resolve().parent/'logs/latest.log'
native_text=native.read_text(errors='replace') if native.exists() else ''
native_ok='MANKA_NATIVE_PASS' in native_text and 'MANKA_NATIVE_FAIL' not in native_text
lines=['# 万华御札：实际客户端验收报告','',f"代码：`{os.environ.get('GITHUB_SHA','local working tree')}`。CI：[完整任务](https://github.com/Bluelgin/Contract-Blade/actions/runs/{os.environ.get('GITHUB_RUN_ID','')})。",'',
'PASS=实际执行且通过；FAIL=实际执行且失败；BLOCKED=环境阻碍；NOT RUN=未执行。FakePlayer原生夹具、真实客户端、独立JVM联机与静态检查分别说明，不互相替代。','',
'环境：Minecraft 1.20.1 / Forge 47.4.10 / Java17 / TLM1.5.3 / SlashBlade Resharped1.9.65。详细CPU、内存、JDK、FFmpeg版本见 `environment.txt`，OpenGL版本见客户端日志。使用Mesa llvmpipe软件渲染，无Shader包；正式录像客户端未装BladeTetra、Patchouli或TACZ。主客户端限帧30，观察客户端20。','',
'## 构建与原生服务端','', '| 项目 | 状态 | 实际证据 |','| --- | --- | --- |',
 f"| JUnit | {'PASS' if xml and count('failures')==count('errors')==0 else 'FAIL' if xml else 'NOT RUN'} | {len(xml)}套，{count('tests')}项，失败{count('failures')}，错误{count('errors')}，跳过{count('skipped')}；XML随构建Artifact归档 |",
 f"| 十级×四式、精确扣费、零余额拒绝、原生无尽饰品、背包饰品拒绝、32颗实际幻想实体 | {'PASS' if native_ok else 'FAIL' if 'MANKA_NATIVE_FAIL' in native_text else 'NOT RUN'} | dedicated Forge/TLM；明确为FakePlayer夹具，非多人或视觉测试 |",
f"| ./gradlew clean build 与独立test | {'PASS' if os.environ.get('MANKA_WRAPPER_BUILD_PASSED')=='true' else 'NOT RUN'} | 由两条命令实际退出成功后设置的本次CI标记；不以源码推断 |",
'| 正式资源导出、静态与既有共鸣/专服回归 | NOT RUN（本脚本未判定其他CI任务） | 按上方CI对应任务结果及日志核对；不能用客户端成功代替全部CI |','',
'## 原始实机录像','',
'录制来源是实际Minecraft OpenGL窗口的X11 XGetImage（MSS）。FFmpeg H.264/yuv420p，1280×720，VFR墙钟时间戳，保留原速，无音轨。非离线图像合成；源MP4和逐视频capture.json、ffprobe结果一起归档。Artifact保留14天，精确到期时间以GitHub为准；MP4不进入Mod Git历史。','',
'| 文件 | 状态 | 时长 / 字节 / 平均编码FPS |','| --- | --- | --- |']
for v in videos:
 stream=v['video']['streams'][0];fmt=v['video']['format'];num,den=map(int,stream['avg_frame_rate'].split('/'));passed=v['capture']['encoderExit']==0 and v['capture']['framesCaptured']>0
 lines.append(f"| {v['name']} | {'PASS' if passed else 'FAIL'} | {float(fmt['duration']):.2f}s / {fmt['size']} bytes / {num/den:.2f}；游戏FPS另测 |")
for name in ['manka_two_clients.mp4','manka_boss_window_test.mp4']:
 cap=obj(out/'qa'/Path(name).with_suffix('.capture.json'),{})
 lines.append(f"| {name} | {'PASS' if cap.get('encoderExit')==0 and cap.get('framesCaptured',0)>0 else 'NOT RUN'} | 见qa目录原始文件及捕获记录 |")
lines+=['','## 实际客户端断言','', '| 用例 | 状态 | 实际结果 |','| --- | --- | --- |']
for r in qa:lines.append('| '+r['case'].replace('|','/')+' | '+r['status']+' | '+json.dumps(r['evidence'],ensure_ascii=False).replace('|','/')+' |')
if not qa:lines.append('| 客户端QA | NOT RUN | 没有运行结果JSON，不能按通过处理 |')
lines+=['','GUI与四层法阵的可读性还应结合原始视频和PNG截图审阅。两个JVM均为真实Forge客户端、独立开发身份与网络连接，由脚本操作；不是两名真人，也不是FakePlayer模拟。结界用例会先清除上个场景遗留的箭，再记录整次施法的实际拦截事件。','',
'## 实测性能','',
'以下仅统计10秒采样窗口完整落在对应原速录像内的数据。averageFPS为实际渲染帧间隔所得；p99FPS只是p99帧耗时倒数，不是单独测量的1%low平均值。内存是已用JVM堆MiB，不是进程RSS。视觉记录不等于实体：本实现不新增世界视觉实体。活跃弹幕为48格范围实际查询，queued是当前主人待发子弹条目，不冒称全服总数。','']
for name,path in [('单客户端密集施法',out/'videos/performance-metrics.json'),('两客户端：主机',out/'qa/host-two-client-metrics.json'),('两客户端：观察端',out/'qa/observer-two-client-metrics.json')]:
 data=obj(path,{})
 lines+=['### '+name,'']
 if not data:lines+=['NOT RUN：没有对应实测采样JSON。',''];continue
 lines+=['| 指标 | 采样平均 | 最低 | 最高 |','| --- | ---: | ---: | ---: |']
 for key,value in data.get('client',{}).items():
  if value:lines.append(f"| {key} | {value['mean']:.3f} | {value['minimum']:.3f} | {value['maximum']:.3f} |")
 for players,values in data.get('serverByActualClients',{}).items():
  for key,value in values.items():
   if value:lines.append(f"| {players}真实客户端 {key} | {value['mean']:.3f} | {value['minimum']:.3f} | {value['maximum']:.3f} |")
 counts=data.get('nativeCounts',[])
 if counts:lines+=['',f"采样最大48格弹幕数：{max(r.get('active',0) for r in counts):.0f}；当前主人最大待发条目：{max(r.get('queued',0) for r in counts):.0f}。"]
 lines+=['','原始采样：`'+str(path.relative_to(out))+'`。','']
lines+=['## 限制与未测项目','', '| 项目 | 状态 | 边界 |','| --- | --- | --- |',
'| 8/16真实客户端密集战斗 | NOT RUN | 本轮只建立两个真实客户端。4vCPU、软件光栅化的结果不能外推8/16人，也不使用FakePlayer冒充。复现需相应数量独立客户端和足够CPU/GPU/内存 |',
'| 多种可选Boss完整阶段/限伤矩阵 | NOT RUN | vanilla Wither的实际命中/免疫用例与既有原生Soul回归，不能代替所有模组Boss |',
'| 三狐与赤月尝试借普通万华权限的完整客户端矩阵 | NOT RUN | 既有原生与单元回归独立归档；本次不修改三狐/赤月规则 |',
'| 灵脉倍率、显现恢复的全部真人客户端组合 | NOT RUN | 既有共鸣原生回归另列；不把FakePlayer或JUnit当作真人体感验收 |',
'| 30分钟以上持续压力与资源重载耐久 | NOT RUN | 短时36请求、真实双客户端、单次F3+T降级不能代替长时压力 |','',
'同Boss成功命中按owner与tick写入runtime-qa.json；如果分布不均，只记录现象，不能在本轮修改全局两tick窗口。软件渲染下若出现低帧，应如实报告，不能据此承诺所有硬件不卡顿。','',
'## 复现与文件用途','',
'- `tools/run_manka_client_capture.sh`：真实客户端启动、原速三视频、独立双客户端QA、统计及本报告。','- `tools/run_manka_native_validation.py`：独立专服夹具；FakePlayer标签必须保留。','- `tools/run_manka_client_qa.py`：实际键鼠、原生敌人/箭、等级、身份、生命与维度测试。','- `qa/*.png`：实际窗口截图；`videos/*.mp4`与`qa/*.mp4`：真实录屏。','- `client/logs/latest.log`、`qa/observer/observer.log`：真实两个客户端与集成服务端原始日志。','- 正式纹理与SVG、40帧御札保持批准资源字节，路径见源码 `art/danmaku/SPELLCARD_ASSETS.md`。','',
'PR #27保持Draft，不自动合并master。']
(out/'acceptance-report.md').write_text('\n'.join(lines)+'\n');print(out/'acceptance-report.md')
