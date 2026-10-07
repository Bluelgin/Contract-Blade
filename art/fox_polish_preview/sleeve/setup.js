(() => {
 Modes.options.edit.select();
 Preview.selected.controls.target.set(-3.5,23,0);
 Preview.selected.camera.position.set(-34,28,-58);
 Preview.selected.camera.zoom=2.3;
 Preview.selected.camera.updateProjectionMatrix();
 Canvas.updateAll();
 return 'Same lighting and camera for sleeve before/after';
})()
