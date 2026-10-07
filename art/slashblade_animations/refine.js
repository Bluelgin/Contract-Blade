(() => {
  const a = Animation.all.find(a => a.name === 'animation.contract_blade.swordplay_preview');
  for (const animator of Object.values(a.animators)) {
    for (const key of animator.keyframes || []) {
      if (key.channel === 'rotation') {
        key.data_points.forEach(p => { p.x = String(-Number(p.x)); p.y = String(-Number(p.y)); });
      }
    }
  }
  Animator.preview();
  foxSwordPreview.update();
  return 'Converted authoring rotation axes to Blockbench local axes';
})()
