(async()=>{
  const fs=require('fs'),maid=globalThis.maidTailPreview===true,base=maid?'E:/Little maid/art/fox_polish_preview/tail-adaptation':'E:/Little maid/art/ysm-test',previous=Project;
  const originalHandler=Animator.MolangParser.variableHandler;
  const oldGlobals={...Animator.global_variable_lines};
  const result={scope:'Blockbench main-clip + tail-layer poses, previous-frame Root bounce; numerical spring approximation, not live YSM integration',models:[]};
  let input={speed:0,vertical:0,yawSpeed:0},springs=new Map(),bodyY=0,frame=0;
  Animator.MolangParser.variableHandler=function(name,vars,args){
    const values={'query.anim_time':Timeline.time,'query.life_time':Timeline.time+20,'query.ground_speed':input.speed,'query.vertical_speed':input.vertical,'query.yaw_speed':input.yawSpeed,'query.fox_tail_body_bob':bodyY,'ctrl.walk':input.walk??0,'ctrl.run':input.run??0,'ysm.head_pitch':0,'ysm.head_yaw':0,'ysm.is_close_eyes':0,'query.health':20,'query.max_health':20,'query.is_on_ground':1,'query.is_sprinting':input.run??0,'tlm.is_sitting':0,'ysm.is_sleep':0,'query.is_riding':0,'query.is_in_water':0,'query.is_swimming':0};
    if(name in values)return values[name];
    if(name==='ysm.second_order'){
      const [key,target,frequency,damping]=args;let state=springs.get(key);
      if(!state){state={value:target,velocity:0,frame};springs.set(key,state);}
      if(state.frame!==frame){
        const omega=2*Math.PI*frequency,dt=1/60;
        state.velocity+=(omega*omega*(target-state.value)-2*damping*omega*state.velocity)*dt;
        state.value+=state.velocity*dt;state.frame=frame;
      }
      return state.value;
    }
    return originalHandler.call(this,name,vars,args);
  };
  const pose=(key,t,profile,main=null)=>{
    input=profile;for(const a of Animation.all)a.playing=false;
    const root=Group.all.find(g=>g.name==='Root');bodyY=(root?.mesh.position.y??0)-(root?.origin[1]??0);frame++;
    const a=Animation.all.find(a=>a.name===key);a.select();
    if(main)Animation.all.find(a=>a.name===main).playing=true;
    if(maid)for(const label of ['pre_parallel0','parallel1']){const layer=Animation.all.find(a=>a.name===label);if(layer)layer.playing=true;}
    Timeline.setTime(t);Animator.preview();Project.model_3d.updateMatrixWorld(true);
  };
  const capture=(file,direction)=>{
    const p=Preview.selected,size=[p.renderer.domElement.width,p.renderer.domElement.height],aspect=p.camPers.aspect,pos=p.camera.position.clone(),target=p.controls.target.clone();
    try{
      const box=new THREE.Box3();for(const c of Cube.all)box.expandByObject(c.mesh);
      const center=box.getCenter(new THREE.Vector3());
      p.camera.position.copy(center.clone().add(new THREE.Vector3(...direction).normalize().multiplyScalar(100)));
      p.controls.target.copy(center);p.camPers.aspect=1;p.camera.updateProjectionMatrix();p.controls.update();p.renderer.setSize(850,850,false);
      unselectAll();Canvas.updateCubeHighlights(undefined,true);
      Canvas.withoutGizmos(()=>{p.render();fs.writeFileSync(file,Buffer.from(p.canvas.toDataURL().split(',')[1],'base64'));});
    }finally{
      p.renderer.setSize(...size,false);p.camPers.aspect=aspect;p.camera.position.copy(pos);p.controls.target.copy(target);p.camera.updateProjectionMatrix();p.controls.update();
    }
  };
  try{
    for(const id of (maid?['fox_white','fox_black']:['contract_akatsuki','contract_white_fox','contract_black_fox'])){
      const dir=maid?'E:/Little maid/tmp/maid-tail-sync/updated/assets/contract_fox':base+'/models/'+id;
      const geometryFile=dir+(maid?'/models/entity/'+id+'.json':'/models/main.json');
      const textureFile=dir+(maid?'/textures/entity/'+id+'.png':'/textures/default.png');
      Codecs.bedrock.load(JSON.parse(fs.readFileSync(geometryFile,'utf8')),{path:geometryFile,no_file:true});
      const temporary=Project;
      try{
        if(Texture.all.length===0)new Texture({name:'default.png'}).fromPath(textureFile).add();
        await new Promise(resolve=>setTimeout(resolve,120));
        const animations=maid?{}:JSON.parse(fs.readFileSync(dir+'/animations/main.animation.json','utf8')).animations;
        if(maid){
          const model=JSON.parse(fs.readFileSync(dir+'/maid_model.json','utf8')).model_list.find(m=>m.model_id==='contract_fox:'+id);
          for(const location of model.animation)Object.assign(animations,JSON.parse(fs.readFileSync('E:/Little maid/tmp/maid-tail-sync/updated/assets/'+location.replace(':','/'),'utf8')).animations);
        }
        const tested=maid?[]:['fly'];if(animations.pre_parallel3?.bones && Object.keys(animations.pre_parallel3.bones).length)tested.push('pre_parallel3');
        const previewClips=Object.fromEntries([...new Set([...tested,'idle','walk','run',...(maid?['pre_parallel0','parallel1']:[])])].filter(k=>animations[k]).map(k=>[k,animations[k]]));
        // Blockbench lacks the YSM vector-return query; use the same previous-frame Root input.
        const previewJSON=JSON.stringify(previewClips).replaceAll("ysm.bone_pos('Root').y",'query.fox_tail_body_bob');
        AnimationCodec.codecs.bedrock.loadFile({json:{format_version:'1.8.0',animations:JSON.parse(previewJSON)},path:base+'/preview-only.animation.json'});
        Modes.options.animate.select();let frames=0,invalid=0;
        for(const key of tested)for(const profile of [{speed:0,vertical:0,yawSpeed:0},{speed:3.2,vertical:-3,yawSpeed:180,run:1}]){
          const duration=key==='fly'?animations.fly.animation_length:3;
          springs=new Map();Animator.MolangParser.resetVariables();
          for(let i=0;i<=48;i++){
            pose(key,duration*i/48,profile);frames++;
            for(const c of Cube.all)if(!c.mesh.matrixWorld.elements.every(Number.isFinite))invalid++;
          }
        }
        const dest=base+'/tail-preview/'+id;fs.mkdirSync(dest,{recursive:true});
        if(!maid){pose('fly',0,{speed:0,vertical:0,yawSpeed:0});capture(dest+'/fly-back.png',[0,0.12,1]);capture(dest+'/fly-side.png',[1,0.12,0]);}
        if(tested.includes('pre_parallel3')){
          const scenarios=['idle','walk','run','start-stop','turn'];const rows=[];
          for(const scenario of scenarios){
            springs=new Map();Animator.MolangParser.resetVariables();let lastRootY=0,maxBounce=0;
            for(let i=0;i<=180;i++){
              const t=i/60,moving=scenario==='start-stop'?t>=0.4&&t<1.8:scenario!=='idle';
              const running=moving&&scenario!=='walk';
              const profile={speed:moving?(running?3.2:1.7):0,vertical:0,yawSpeed:scenario==='turn'&&t>0.8&&t<1.3?180:0,walk:moving&&!running?1:0,run:running?1:0};
              pose('pre_parallel3',t,profile,moving?(running?'run':'walk'):'idle');frames++;
              maxBounce=Math.max(maxBounce,Math.abs(bodyY-lastRootY));lastRootY=bodyY;
              for(const c of Cube.all)if(!c.mesh.matrixWorld.elements.every(Number.isFinite))invalid++;
              if(scenario==='run'&&[90,100,110,120].includes(i))capture(dest+'/run-back-'+i+'.png',[0,0.08,1]);
              if(scenario==='run'&&i===110)capture(dest+'/run-side.png',[1,0.08,0]);
              if(scenario==='start-stop'&&i===120)capture(dest+'/braking-back.png',[0,0.08,1]);
            }
            rows.push({scenario,frames:181,maxFrameBodyBounce:maxBounce});
          }
          pose('pre_parallel3',0.8,{speed:0,vertical:0,yawSpeed:0});capture(dest+'/tails-rest-back.png',[0,0.08,1]);
          result.models.push({id,frames,invalidMatrices:invalid,tested,locomotionScenarios:rows,preview:dest});
        }else result.models.push({id,frames,invalidMatrices:invalid,tested,preview:dest});
      }finally{await temporary.close(true);}
    }
    fs.writeFileSync(base+'/tail-preview-validation.json',JSON.stringify(result,null,2));
    return result;
  }finally{Animator.MolangParser.variableHandler=originalHandler;Animator.global_variable_lines=oldGlobals;previous.select();}
})()
