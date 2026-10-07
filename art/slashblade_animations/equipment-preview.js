(() => {
  if (globalThis.foxSwordPreview) {
    Blockbench.removeListener('display_animation_frame', globalThis.foxSwordPreview.update);
    globalThis.foxSwordPreview.blade.removeFromParent();
    globalThis.foxSwordPreview.sheath.removeFromParent();
  }
  const bone = name => Group.all.find(g => g.name === name).mesh;
  const blade = new THREE.Group();
  const sheath = new THREE.Group();
  function box(parent, size, pos, color) {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(...size), new THREE.MeshLambertMaterial({color}));
    mesh.position.set(...pos);
    parent.add(mesh);
  }
  box(blade, [0.8, 24, 0.3], [0,-17,0], 0xa0b7cb);
  box(blade, [0.25,24,0.35], [0.4,-17,0], 0xe3edfa);
  box(blade, [0.9,5,0.9], [0,-2.5,0], 0x281d40);
  box(blade, [2.8,0.5,2], [0,-5,0], 0xba914c);
  bone('BladeLocator').add(blade);
  box(sheath, [1.2,1.2,27], [0,0,12], 0x291f3b);
  box(sheath, [1.5,1.5,1.2], [0,0,-1.2], 0xba914c);
  sheath.position.set(4.8,0.5,0);
  sheath.rotation.x = 0.1;
  bone('DownBody').add(sheath);
  globalThis.foxSwordPreview = {blade, sheath, update() {
    const t = Timeline.time;
    blade.visible = t >= 1.18 && t < 4.5;
  }};
  globalThis.foxSwordPreview.update();
  Blockbench.on('display_animation_frame', globalThis.foxSwordPreview.update);
  Preview.selected.camera.zoom = 0.17;
  Preview.selected.camera.updateProjectionMatrix();
  return 'Temporary equipment overlay installed; not part of model geometry or export';
})()
