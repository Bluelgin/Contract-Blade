(() => {
 function hide(g) {g.visibility=false;g.children.forEach(c=>{if(c instanceof Group)hide(c);else c.visibility=false;});}
 Group.all.filter(g=>g.name==='FOX'||g.name==='FoxMoonCrown').forEach(hide);
 Modes.options.edit.select();
 Preview.selected.controls.target.set(0,33,0);
 Preview.selected.camera.position.set(-30,37,-55);
 Preview.selected.camera.zoom=1.65;
 Preview.selected.camera.updateProjectionMatrix();
 Canvas.updateAll();
 return 'Head closeup; alternate form and retired crown hidden only in preview';
})()
