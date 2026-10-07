(() => {
  const preview=globalThis.foxSwordPreview;
  Blockbench.removeListener('display_animation_frame',preview.update);
  preview.update=()=>{preview.blade.visible=true;};
  Blockbench.on('display_animation_frame',preview.update);
  Modes.options.animate.select();
  Animation.all.find(a=>a.name==='animation.contract_blade.combo_b_preview').select();
  Timeline.setTime(0); Animator.preview();
  Preview.selected.camera.zoom=.09;
  Preview.selected.camera.updateProjectionMatrix();
  return 'Combo B selected; temporary sword overlay follows equipment bone';
})()
