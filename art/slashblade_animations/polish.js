(() => {
  const a = Animation.all.find(a=>a.name==='animation.contract_blade.swordplay_preview');
  const find = name => Group.all.find(g=>g.name===name);
  function key(name,t,rotation) {
    const k=a.animators[find(name).uuid].rotation.find(k=>Math.abs(k.time-t)<0.001);
    if(!k) throw new Error('Missing key '+name+' '+t);
    ['x','y','z'].forEach((axis,i)=>k.data_points[0][axis]=String(rotation[i]));
  }
  function orientHand(t, direction) {
    Timeline.setTime(t); Animator.preview();
    const hand=find('RightHand').mesh;
    const q=new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,-1,0),new THREE.Vector3(...direction).normalize());
    q.premultiply(hand.parent.getWorldQuaternion(new THREE.Quaternion()).invert());
    const e=new THREE.Euler().setFromQuaternion(q,'ZYX');
    key('RightHand',t,[e.x,e.y,e.z].map(v=>Number(THREE.MathUtils.radToDeg(v).toFixed(3))));
  }
  for(const t of [2.5,2.68]) {
    key('RightArm',t,[65,20,-22]);
    key('RightForeArm',t,[24,0,0]);
    orientHand(t,[-1,0.13,-0.05]);
  }
  orientHand(1.3,[1,-0.35,-0.15]);
  orientHand(2.88,[1,-0.4,-0.12]);
  Timeline.setTime(2.5); Animator.preview(); foxSwordPreview.update();
  return 'Guard across front; blade-aligned slash follow-through';
})()
