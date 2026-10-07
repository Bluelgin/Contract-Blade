(() => {
 const changed=[];
 for(const c of Cube.all){
  const cord=/^(RefCord|RefKnot|RefMagatama)/.test(c.name),facing=/^Layer(?:Continuous|FacingBend|VerticalOuterGold|DiagonalOuterGold|VerticalFacingShadow|DiagonalFacingShadow|RobeFold)/.test(c.name);
  if(!cord&&!facing)continue;
  if(cord){const shift=/^RefKnot/.test(c.name)?.48:.95;for(const a of [c.from,c.to,c.origin])a[2]+=shift;c.rotation[0]=6;}
  else {
   c.rotation[0]=6;c.rotation[1]=0;
   const centre=new THREE.Vector3(...c.from).add(new THREE.Vector3(...c.to)).multiplyScalar(.5),origin=new THREE.Vector3(...c.origin);
   centre.sub(origin).applyEuler(new THREE.Euler(...c.rotation.map(v=>v*Math.PI/180),'ZYX')).add(origin);
   const plane=-2.934+Math.tan(Math.PI/30)*(centre.y-22),depth=(c.to[2]-c.from[2])*.5;
   const gold=/Gold/.test(c.name),shadow=/Shadow/.test(c.name),offset=gold?.10:shadow?.012:.035;
   const dz=plane-depth-offset-centre.z;
   for(const a of [c.from,c.to,c.origin])a[2]+=dz;
  }
  changed.push(c.name);
 }
 Project.saved=false;Project.name=(Project.name.includes('白狐')?'白狐':'黑狐')+' · 衣饰贴合预览';Canvas.updateAll();return JSON.stringify({fittedCubes:changed.length,names:changed});
})()
