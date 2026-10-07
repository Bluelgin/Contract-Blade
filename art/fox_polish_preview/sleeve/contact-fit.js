(() => {
 Canvas.updateAll();Project.model_3d.updateMatrixWorld(true);
 const body=Cube.all.filter(c=>(c.name==='UpperBody'&&c.from[1]>22&&c.to[1]<26)||/^MergedFrontRobe/.test(c.name)||/^F[LRM]1?$/.test(c.name));
 const obi=Cube.all.find(c=>c.name==='CovenantObi'),waist=Cube.all.find(c=>c.name==='CovenantWaistCord');
 const targets=[obi,waist,...Cube.all.filter(c=>/^(RefCord|RefKnot|RefMagatama|RefCharmSuspension)/.test(c.name))].filter(Boolean);
 const report=[],shifts=new Map();
 for(const c of targets){
  const centre=new THREE.Box3().setFromObject(c.mesh).getCenter(new THREE.Vector3()),normal=new THREE.Vector3(0,0,1).transformDirection(c.mesh.matrixWorld);
  const rear=centre.clone().addScaledVector(normal,(c.to[2]-c.from[2])/2+(c.inflate||0));
  const ray=new THREE.Raycaster(new THREE.Vector3(rear.x,rear.y,-100),new THREE.Vector3(0,0,1));
  const support=c===obi?body:[...body,obi].filter(Boolean),hit=ray.intersectObjects(support.map(s=>s.mesh),false)[0];
  if(!hit)throw Error('No clothing support for '+c.name);
  const owner=c.name.replace('TasselBinding','Tassel'),dz=/TasselBinding/.test(c.name)?shifts.get(owner):hit.point.z+.018-rear.z;
  if(!Number.isFinite(dz))throw Error('Missing tassel owner');shifts.set(c.name,dz);
  for(const a of [c.from,c.to,c.origin])a[2]+=dz;
  Canvas.updateAll();Project.model_3d.updateMatrixWorld(true);
  report.push({name:c.name,shiftZ:+dz.toFixed(4),support:hit.object.name});
 }
 Project.saved=false;Project.name=(Project.name.includes('白狐')?'白狐':'黑狐')+' · 衣饰接触修正';return JSON.stringify(report);
})()
