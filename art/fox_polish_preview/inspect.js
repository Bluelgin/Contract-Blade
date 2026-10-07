(() => {
 function names(c) { const a=[]; for(let p=c.parent;p&&p.name;p=p.parent)a.push(p.name);return a; }
 const hair=Cube.all.filter(c=>names(c).some(n=>/Hair|Bangs/.test(n))&&!names(c).some(n=>/Ornament/.test(n)));
 const signatures=new Map(); const duplicates=[];
 hair.forEach(c=>{const key=JSON.stringify([c.from,c.to,c.origin,c.rotation,c.inflate,names(c)]);if(signatures.has(key))duplicates.push([signatures.get(key),c.name]);else signatures.set(key,c.name);});
 return JSON.stringify({project:Project.name,head:Cube.all.filter(c=>c.name==='Head').map(c=>({from:c.from,to:c.to,faces:Object.fromEntries(Object.entries(c.faces).map(([k,f])=>[k,f.uv]))})),hairCount:hair.length,duplicates,texture:{loaded:Texture.all[0].img.complete,width:Texture.all[0].img.width},preview:{camera:Preview.selected.camera.position.toArray(),target:Preview.selected.controls.target.toArray(),zoom:Preview.selected.camera.zoom},update:Texture.all[0].updateSource.toString().slice(0,500)});
})()
