(() => {
  function hide(g) { g.visibility = false; g.children.forEach(c => { if (c instanceof Group) hide(c); else c.visibility = false; }); }
  Group.all.filter(g => g.name === 'FOX' || g.name === 'FoxMoonCrown').forEach(hide);
  Preview.selected.camera.zoom = 0.21;
  Preview.selected.camera.updateProjectionMatrix();
  Canvas.updateAll();
  return 'Alternate fox and retired crown hidden in preview only';
})()
