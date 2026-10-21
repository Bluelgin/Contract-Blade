"""Summarize measured 10-second runtime samples; never extrapolates fake players into clients."""
import argparse,json,re,statistics
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('log',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args();s=a.log.read_text(errors='replace')
client=[];server=[];counts=[]
for line in s.splitlines():
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
 'client':{k:summary(client,k) for k in ['averageFPS','p99frameMs','p99FPS','heapMiB','visualEntries','particleCount']},
 'serverByActualClients':{str(n):{k:summary([r for r in server if r.get('realPlayers')==n],k) for k in ['actualTPS','averageMSPT']} for n in sorted({r.get('realPlayers') for r in server})},
 'nativeCounts':counts,'rawClientSamples':client,'rawServerSamples':server}
a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(a.output)
