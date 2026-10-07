(() => {
  const bone = name => Group.all.find(g => g.name === name).mesh;
  if(globalThis.foxSwordPreview) {
    Blockbench.removeListener('display_animation_frame',foxSwordPreview.update);
    foxSwordPreview.blade?.removeFromParent(); foxSwordPreview.sheath?.removeFromParent();
  }
  if(globalThis.iaidoEquipment) {
    Blockbench.removeListener('display_animation_frame',iaidoEquipment.update);
    iaidoEquipment.sword.removeFromParent(); iaidoEquipment.sheath.removeFromParent();
  }
  const sword=new THREE.Group(), sheath=new THREE.Group(), steel=new THREE.Group();
  sword.add(steel);
  function box(parent,size,pos,color) {
    const m=new THREE.Mesh(new THREE.BoxGeometry(...size),new THREE.MeshLambertMaterial({color}));
    m.position.set(...pos); parent.add(m);
  }
  box(sword,[.85,5,.85],[0,1.6,0],0x2c203d);
  box(sword,[2.6,.45,1.8],[0,-1,0],0xa98a51);
  box(steel,[.65,23,.22],[0,-12.7,0],0xa6bbc9);
  box(steel,[.2,23,.25],[.34,-12.7,0],0xe1e9ed);
  box(sheath,[1.1,1.1,24],[0,0,11.5],0x261e34);
  box(sheath,[1.35,1.35,.7],[0,0,-.15],0xa98a51);
  bone('BladeLocator').add(sword);
  bone('DownBody').add(sheath);
  globalThis.iaidoEquipment={sword,sheath,steel,update() {
    const t=Timeline.time;
    steel.visible=t>=1.36&&t<2.38;
    const hand=bone('LeftHandLocator');
    hand.updateWorldMatrix(true,false);
    const body=bone('DownBody');
    const left=hand.getWorldPosition(new THREE.Vector3());
    const right=bone('BladeLocator').getWorldPosition(new THREE.Vector3());
    body.worldToLocal(sheath.position.copy(left));
    const bodyQ=body.getWorldQuaternion(new THREE.Quaternion());
    const axis=new THREE.Vector3(0,0,1).applyQuaternion(bodyQ);
    if((t>=.30&&t<=1.32)||(t>=2.3&&t<=2.45))axis.copy(left).sub(right).normalize();
    const sheathQ=new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,0,1),axis);
    if(t<=1.32 || t>=2.3) {
      sheath.quaternion.copy(bodyQ.clone().invert().multiply(sheathQ));
      this.sheathLocal = sheath.quaternion.clone();
    } else if(this.sheathLocal) {
      sheath.quaternion.copy(this.sheathLocal);
    }
  }};
  Blockbench.on('display_animation_frame',iaidoEquipment.update);
  Modes.options.animate.select();
  Animation.all.find(a=>a.name==='animation.contract_blade.iaido_preview').select();
  Timeline.pause(); Timeline.setTime(.8); Animator.preview(); iaidoEquipment.update();
  Preview.selected.camera.zoom=1.35;
  Preview.selected.camera.position.set(-48,35,-75);
  Preview.selected.controls.target.set(0,21,0);
  Preview.selected.controls.update();
  Preview.selected.camera.updateProjectionMatrix();
  return 'Iaido selected. Temporary blade/sheath references only; no effects or combat edits.';
})()
