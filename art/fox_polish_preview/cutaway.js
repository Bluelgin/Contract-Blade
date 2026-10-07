(() => {
 globalThis.foxPreviewVisibility=Cube.all.map(c=>[c,c.visibility]);
 Cube.all.forEach(c=>c.visibility=c.name==='Head');
 Preview.selected.controls.target.set(0,35,0);
 Preview.selected.camera.position.set(22,29,30);
 Preview.selected.camera.zoom=2.3;
 Preview.selected.camera.updateProjectionMatrix();
 Canvas.updateAll();
 return 'Hidden outer layers temporarily to inspect the base scalp, back and underside';
})()
