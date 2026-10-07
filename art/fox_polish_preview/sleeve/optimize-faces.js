(() => {
 if(!Project.name.includes('白狐'))return 'Transparent black fox: face culling intentionally skipped';
 Canvas.updateAll();Project.model_3d.updateMatrixWorld(true);
 const tex=Texture.all[0],canvas=document.createElement('canvas');canvas.width=tex.width;canvas.height=tex.height;const ctx=canvas.getContext('2d');ctx.drawImage(tex.img,0,0);const raw=ctx.getImageData(0,0,tex.width,tex.height).data;
 function visible(c){for(let p=c;p&&p!=='root';p=p.parent)if(p.visibility===false)return false;return true;}
 function opaque(c){return Object.values(c.faces).every(f=>{if(f.texture===null)return false;const u=f.uv;for(let y=Math.floor(Math.min(u[1],u[3]));y<Math.ceil(Math.max(u[1],u[3]));y++)for(let x=Math.floor(Math.min(u[0],u[2]));x<Math.ceil(Math.max(u[0],u[2]));x++)if(raw[(y*tex.width+x)*4+3]!==255)return false;return true;});}
 const cubes=Cube.all.filter(visible),supports=cubes.filter(opaque),cuts=[];
 for(const c of cubes){const inflate=c.inflate||0,lo=c.from.map(v=>v-inflate),hi=c.to.map(v=>v+inflate);
  for(const [side,f] of Object.entries(c.faces)){if(f.texture===null)continue;let corners=[];
   for(const a of [0,1])for(const b of [0,1]){let v;if(side==='north'||side==='south')v=[a?hi[0]:lo[0],b?hi[1]:lo[1],side==='north'?lo[2]:hi[2]];else if(side==='east'||side==='west')v=[side==='west'?lo[0]:hi[0],a?hi[1]:lo[1],b?hi[2]:lo[2]];else v=[a?hi[0]:lo[0],side==='down'?lo[1]:hi[1],b?hi[2]:lo[2]];corners.push(c.mesh.localToWorld(new THREE.Vector3(...v).sub(new THREE.Vector3(...c.origin))));}
   const support=supports.find(s=>s!==c&&s.parent===c.parent&&corners.every(v=>{const p=s.mesh.worldToLocal(v.clone()).add(new THREE.Vector3(...s.origin)),i=s.inflate||0;return [0,1,2].every(k=>p.getComponent(k)>s.from[k]-i+.025&&p.getComponent(k)<s.to[k]+i-.025);}));
   if(support)cuts.push({c,side,support:support.name});
  }
 }
 for(const cut of cuts)cut.c.faces[cut.side].texture=null;
 Project.saved=false;Canvas.updateAll();return JSON.stringify({removedFaces:cuts.length,removedTriangles:cuts.length*2,bonesChanged:false,cubesRemoved:0});
})()
