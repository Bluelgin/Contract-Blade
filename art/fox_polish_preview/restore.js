(() => {
 if(globalThis.foxPreviewVisibility)foxPreviewVisibility.forEach(([c,v])=>c.visibility=v);
 Preview.selected.controls.target.set(0,33,0);
 Preview.selected.camera.position.set(-30,37,-55);
 Preview.selected.camera.zoom=1.65;
 Preview.selected.camera.updateProjectionMatrix();
 Canvas.updateAll();
 return 'Restored exterior preview';
})()
