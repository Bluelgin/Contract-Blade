"""Summarize measured 10-second runtime samples; never extrapolates fake players into clients."""
import argparse,json,re,statistics,datetime
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('log',type=Path);p.add_argument('--output',type=Path,required=True);p.add_argument('--capture',type=Path);a=p.parse_args();s=a.log.read_text(errors='replace')
window=None
if a.capture:
 c=json.loads(a.capture.read_text());start=datetime.datetime.fromisoformat(c['startedUTC']);window=(start+datetime.timedelta(seconds=10),start+datetime.timedelta(seconds=c['captureSeconds']))
client=[];server=[];counts=[]
for line in s.splitlines():
 if window:
  stamp=re.match(r'\[([^]]+)\]',line)
  try:t=datetime.datetime.strptime(stamp[1],'%d%b%Y %H:%M:%S.%f').replace(tzinfo=datetime.timezone.utc)
  except (TypeError,ValueError):continue
  if not window[0]<=t<=window[1]:continue
 if 'MANKA_CLIENT_METRICS ' in line:
  fields=dict(re.findall(r'(\w+)=([^\s]+)',line));row={}
  for k,v in fields.items():
   try:row[k]=float(v)
   except ValueError:row[k]=v
  client.append(row)
 if 'MANKA_TPS ' in line:
  server.append({k:float(v) for k,v in re.findall(r'(actualTPS|averageMSPT|realPlayers)=([\d.Ee+-]+)',line)})
 if 'MANKA_SERVER_METRICS ' in line:
  counts.append({k:float(v) for k,v in re.findall(r'(realPlayers|averageMSPT|active|queued)=([\d.Ee+-]+)',line)})
def summary(rows,key):
 vals=[r[key] for r in rows if isinstance(r.get(key),(float,int))]
 return None if not vals else dict(samples=len(vals),mean=statistics.mean(vals),minimum=min(vals),maximum=max(vals))
result={'scope':'All measured 10-second samples in this actual runtime log, including menus and idle intervals. Not a hardware GPU benchmark. p99FPS is inverse p99 frame duration, not a separately measured one-percent-low average.',
 'captureWindowUTC':None if window is None else [t.isoformat() for t in window],
 'client':{k:summary(client,k) for k in ['averageFPS','p99frameMs','p99FPS','heapMiB','visualEntries','particleCount']},
 'serverByActualClients':{str(n):{k:summary([r for r in server if r.get('realPlayers')==n],k) for k in ['actualTPS','averageMSPT']} for n in sorted({r.get('realPlayers') for r in server})},
 'nativeCounts':counts,'rawClientSamples':client,'rawServerSamples':server}
if window:result['scope']='Measured 10-second samples wholly inside this real capture window. Software rendering; no extrapolation to untested client counts. p99FPS is inverse p99 frame duration.'
a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(a.output)
