package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import com.maidweapon.forge.system.ContractNbtGuard;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Opt-in server-thread microbenchmark. Setup/cleanup are not part of timed recovery calls. */
public final class ContractCarrierPerformanceValidation {
    private static volatile int sink;
    private final MinecraftServer server;
    private final ServerPlayer owner;
    private final ContractCarrierLossJournal journal;
    private final ItemStack template;
    private final CompoundTag maidData;
    private final Map<String,Object> results = new LinkedHashMap<>();
    private final com.sun.management.ThreadMXBean allocations;

    private ContractCarrierPerformanceValidation(MinecraftServer server) throws Exception {
        this.server=server;
        var level=server.overworld();
        owner=net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CarrierPerfFixture"));
        owner.getInventory().clearContent(); owner.getInventory().selected=0;
        owner.containerMenu=owner.inventoryMenu; owner.setHealth(20); owner.tickCount=5;
        var spawn=level.getSharedSpawnPos(); owner.setPos(spawn.getX()+.5,100,spawn.getZ()+.5);
        Entity maid=(Entity)TlmEntityAdapter.maidClass().getConstructor(net.minecraft.world.level.Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid,owner); maid.setPos(owner.position()); level.addFreshEntity(maid);
        template=new ItemStack(Items.IRON_SWORD);
        owner.getInventory().setItem(0,template);
        require(ContractLifecycleService.capture(owner,maid,template,false),"native template capture");
        owner.getInventory().clearContent();
        maidData=MaidEntityDataCodec.read(template.getTag());
        journal=ContractCarrierLossJournal.get(server);
        var bean=ManagementFactory.getThreadMXBean();
        allocations=bean instanceof com.sun.management.ThreadMXBean b && b.isThreadAllocatedMemorySupported()?b:null;
        if(allocations!=null) allocations.setThreadAllocatedMemoryEnabled(true);
    }

    public static void run(MinecraftServer server) throws Exception {
        if(!Boolean.getBoolean("contractblade.performance.validation")) throw new IllegalStateException("fixture disabled");
        var test=new ContractCarrierPerformanceValidation(server);
        try { test.measure(); } finally {
            test.journal.pending.remove(test.owner.getUUID()); test.journal.setDirty();
            InfusedMaidDeploymentSystem.onLogout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(test.owner));
            test.owner.getInventory().clearContent();
        }
    }

    private void measure() throws Exception {
        String label=System.getProperty("contractblade.performance.label","after");
        require(label.equals("before")||label.equals("after"),"safe benchmark label");
        results.put("label",label); results.put("java",System.getProperty("java.version"));
        results.put("heapMiB",Runtime.getRuntime().maxMemory()/1048576);
        results.put("processors",Runtime.getRuntime().availableProcessors());
        var ordinary = new ItemStack(Items.IRON_SWORD);
        results.put("durability_noncontract_consume_gate", timed(2000,31,1000,()-> {
            ordinary.setCount(1);
            ContractDurabilityBridge.consume(ordinary, 1, owner);
        }));
        results.put("durability_retained_contract_gate", timed(2000,31,1000,()-> {
            template.setCount(2);
            ContractDurabilityBridge.consume(template, 1, owner);
        }));
        template.setCount(1);
        results.put("idle_has_pending",timed(2000,31,1000,()->{if(ContractCarrierLossService.hasPending(owner))sink++;}));
        results.put("idle_player_5tick_pass",timed(1000,31,250,()->InfusedMaidDeploymentSystem.onPlayerTick(
                new TickEvent.PlayerTickEvent(TickEvent.Phase.END,owner))));
        List<UUID> ids=queue(32,0);
        results.put("waiting_32_tasks",timed(1000,31,250,()->ContractCarrierLossService.process(owner,Map.of(),(p,c)->{
            throw new IllegalStateException("early restore");})));
        results.put("waiting_dirty_marks_per_1000",dirtyMarks(()->ContractCarrierLossService.process(owner,Map.of(),(p,c)->{})));
        clear(ids);
        ids=queue(1,0);
        var candidate=journal.pending.get(owner.getUUID()).values().iterator().next().candidate;
        var small=candidate;
        Runnable duplicate=()->ContractCarrierLossService.scheduleDestroyed(owner,small.maidId(),small.bindingId(),small.snapshot());
        results.put("duplicate_small",timed(2000,31,1000,duplicate));
        results.put("duplicate_dirty_marks_per_1000",dirtyMarks(duplicate));
        clear(ids);
        ids=queue(1,262144);
        candidate=journal.pending.get(owner.getUUID()).values().iterator().next().candidate;
        var large=candidate;
        results.put("large_item_bytes",ContractNbtGuard.serializedSize(large.snapshot().save(new CompoundTag())));
        results.put("large_decoded_maid_bytes",ContractNbtGuard.serializedSize(MaidEntityDataCodec.read(large.snapshot().getTag())));
        results.put("duplicate_256KiB",timed(2000,31,1000,()->ContractCarrierLossService.scheduleDestroyed(
                owner,large.maidId(),large.bindingId(),large.snapshot())));
        clear(ids);
        results.put("restore_32_small",recover(32,0));
        results.put("restore_16_large_256KiB",recover(16,262144));
        if(label.equals("after")) validateDirtyTransitions();
        var output=new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results);
        java.nio.file.Files.writeString(java.nio.file.Path.of("../tmp/carrier-performance-"+label+".json"),output);
        LogUtils.getLogger().info("CARRIER_PERFORMANCE_PASS {}",output);
    }

    private Map<String,Object> timed(int warmup,int trials,int repetitions,Runnable action) {
        for(int i=0;i<warmup;i++)action.run();
        double[] ns=new double[trials],bytes=new double[trials];
        for(int t=0;t<trials;t++) {
            long memory=allocated(),start=System.nanoTime();
            for(int i=0;i<repetitions;i++)action.run();
            ns[t]=(System.nanoTime()-start)/(double)repetitions;
            bytes[t]=Math.max(0,allocated()-memory)/(double)repetitions;
        }
        Arrays.sort(ns); Arrays.sort(bytes);
        var row=new LinkedHashMap<String,Object>();
        row.put("median_us_per_call",ns[trials/2]/1000); row.put("p95_batch_mean_us",ns[(int)Math.ceil(trials*.95)-1]/1000);
        row.put("median_allocated_bytes_per_call",allocations==null?null:bytes[trials/2]); row.put("measured_calls",trials*repetitions);
        return row;
    }
    private int dirtyMarks(Runnable action) {
        int count=0;
        for(int i=0;i<1000;i++){journal.setDirty(false);action.run();if(journal.isDirty())count++;}
        return count;
    }

    private List<UUID> queue(int count,int payload) throws Exception {
        var ids=new ArrayList<UUID>();
        for(int i=0;i<count;i++) {
            UUID id=UUID.randomUUID(); ids.add(id); String binding=UUID.randomUUID().toString();
            var item=template.copy(); var data=maidData.copy(); data.putUUID("UUID",id);
            data.getCompound("ForgeData").putString("MaidWeaponBindingId",binding);
            if(payload>0) {byte[] noise=new byte[payload];new Random(3407+i).nextBytes(noise);data.putByteArray("PerformanceArchive",noise);}
            item.getOrCreateTag().putString("MaidUUID",id.toString()); item.getTag().putString("MaidBindingId",binding);
            MaidEntityDataCodec.write(item.getTag(),data);
            ContractCarrierLossService.scheduleDestroyed(owner,id.toString(),binding,item);
        }
        return ids;
    }

    private Map<String,Object> recover(int count,int payload) throws Exception {
        var calls=new ArrayList<Double>(); var bytes=new ArrayList<Double>(); var totals=new ArrayList<Double>();
        for(int round=0;round<7;round++) {
            var ids=queue(count,payload); advance(45); int[] completed={0}; double sum=0;
            int guard=0;
            while(ContractCarrierLossService.hasPending(owner)) {
                require(++guard<=count,"bounded recovery progress"); advance(5);
                long memory=allocated(),start=System.nanoTime();
                ContractCarrierLossService.process(owner,ContractWeaponLocator.indexByBinding(owner),(p,c)->completed[0]++);
                double ns=System.nanoTime()-start; sum+=ns;
                if(round>=2){calls.add(ns/1000000);bytes.add((double)Math.max(0,allocated()-memory));}
            }
            require(completed[0]==count,"native restored count");
            for(UUID id:ids) require(server.overworld().getEntity(id)!=null,"live native restored maid");
            if(round>=2)totals.add(sum/1000000);
            clear(ids);
        }
        Collections.sort(calls);Collections.sort(bytes);Collections.sort(totals);
        var row=new LinkedHashMap<String,Object>(); row.put("median_ms_per_2_restores",calls.get(calls.size()/2));
        row.put("p95_ms_per_2_restores",calls.get((int)Math.ceil(calls.size()*.95)-1));
        row.put("max_ms_per_pass",calls.get(calls.size()-1)); row.put("median_total_work_ms",totals.get(totals.size()/2));
        row.put("median_allocated_bytes_per_pass",allocations==null?null:bytes.get(bytes.size()/2));
        row.put("native_maids_per_round",count);row.put("measured_rounds",5);
        return row;
    }
    private void clear(List<UUID> ids) {
        journal.pending.remove(owner.getUUID());
        for(UUID id:ids){Entity e=server.overworld().getEntity(id);if(e!=null)e.discard();}
    }
    private void validateDirtyTransitions() throws Exception {
        journal.setDirty(false);
        var ids=queue(1,0);
        require(journal.isDirty(),"new event is persisted");
        var queue=journal.pending.get(owner.getUUID());
        var task=queue.values().iterator().next(); var original=task.candidate;
        journal.setDirty(false);
        ContractCarrierLossService.scheduleDestroyed(owner,original.maidId(),original.bindingId(),original.snapshot());
        require(!journal.isDirty() && task.candidate==original,"duplicate has no dirty mark or snapshot replacement");
        ContractCarrierLossService.process(owner,Map.of(),(p,c)->{});
        require(!journal.isDirty(),"waiting rotation does not mark save dirty");
        ContractCarrierLossService.process(owner,Map.of(original.bindingId(),original.snapshot()),(p,c)->{});
        require(journal.isDirty() && !ContractCarrierLossService.hasPending(owner),"cancellation is persisted");
        clear(ids);
        String binding=UUID.randomUUID().toString(), id=UUID.randomUUID().toString();
        ContractCarrierLossService.scheduleDestroyed(owner,id,binding,new ItemStack(Items.IRON_SWORD));
        task=journal.pending.get(owner.getUUID()).get(binding); original=task.candidate;
        journal.setDirty(false);
        ContractCarrierLossService.scheduleDestroyed(owner,id,binding,template);
        require(journal.isDirty() && task.candidate!=original && task.candidate.detectedAt()==original.detectedAt(),
                "valid snapshot upgrade is persisted without resetting grace period");
        // No payload/live entity: a failed restore must persist its retry deadline.
        task.candidate=new ContractCarrierLossService.Candidate(id,binding,new ItemStack(Items.IRON_SWORD),clock()-45);
        advance(5); journal.setDirty(false);
        ContractCarrierLossService.process(owner,Map.of(),(p,c)->{});
        require(journal.isDirty() && task.nextAttempt>clock(),"retry deadline is persisted");
        journal.setDirty(false);
        ContractCarrierLossService.process(owner,Map.of(),(p,c)->{});
        require(!journal.isDirty(),"backoff waiting is not persisted repeatedly");
        task.durabilityConfirmed=false; journal.setDirty(false);
        ContractCarrierLossService.scheduleDestroyed(owner,id,binding,template);
        require(journal.isDirty() && task.durabilityConfirmed && task.nextAttempt==0,"confirmed durability promotion is persisted");
        CompoundTag saved=journal.save(new CompoundTag());
        require(saved.equals(ContractCarrierLossJournal.load(saved).save(new CompoundTag())),"dirty transitions survive journal reload");
        journal.pending.remove(owner.getUUID());
        LogUtils.getLogger().info("CARRIER_DIRTY_TRANSITIONS_PASS: event, upgrade, cancel, retry, unchanged wait, duplicate and legacy promotion");
    }
    private long clock(){return server.overworld().getGameTime();}
    private void advance(int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData)server.overworld().getLevelData()).setGameTime(server.overworld().getGameTime()+ticks);
    }
    private long allocated(){return allocations==null?0:allocations.getThreadAllocatedBytes(Thread.currentThread().getId());}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
