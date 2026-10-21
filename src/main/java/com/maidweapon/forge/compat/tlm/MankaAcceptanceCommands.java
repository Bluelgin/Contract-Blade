package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.danmaku.*;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.maidweapon.forge.system.resonance.ResonanceService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Opt-in admin scene builder. Uses real slots, normal capture and server casts, never fake damage. */
@Mod.EventBusSubscriber(modid="maid_weapon")
public final class MankaAcceptanceCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("contractblade.danmaku.acceptance")) return;
        event.getDispatcher().register(Commands.literal("mankatest").requires(s -> s.hasPermission(2))
                .then(Commands.literal("setup").executes(c -> {
                    try { setup(c.getSource().getPlayerOrException()); return 1; }
                    catch (Exception failure) { throw new IllegalStateException("Manka test scene setup failed", failure); }
                }))
                .then(Commands.literal("lan").executes(c -> {
                    // Isolated opt-in developer LAN only; never changes a dedicated server's authentication.
                    var server=c.getSource().getServer();
                    if (!server.isSingleplayer()) return 0;
                    server.setUsesAuthentication(false);
                    return 1;
                }))
                .then(Commands.literal("second").executes(c -> {
                    try { createCarrier(c.getSource().getPlayerOrException(),2); return 1; }
                    catch (Exception failure) { throw new IllegalStateException("Second native contract failed", failure); }
                }))
                .then(Commands.literal("cast").then(Commands.argument("form",IntegerArgumentType.integer(0,3))
                    .executes(c -> {
                        var player=c.getSource().getPlayerOrException();
                        var form=DanmakuForm.safe(IntegerArgumentType.getInteger(c,"form"));
                        boolean accepted=DanmakuCombatService.cast(player,form);
                        org.slf4j.LoggerFactory.getLogger("MankaAcceptance").info(
                                "MANKA_CAST_REQUEST owner={} form={} accepted={} tick={}",
                                player.getUUID(),form,accepted,player.level().getGameTime());
                        return accepted ? 1 : 0;
                    })))
                .then(Commands.literal("bauble").then(Commands.argument("mode",IntegerArgumentType.integer(0,2))
                    .executes(c -> { bauble(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"mode")); return 1; })))
                .then(Commands.literal("arrows").executes(c -> { arrows(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("metrics").executes(c -> {
                    var p=c.getSource().getPlayerOrException();
                    org.slf4j.LoggerFactory.getLogger("MankaAcceptance").info(
                        "MANKA_SERVER_METRICS realPlayers={} averageMSPT={} active={} queued={}",
                        p.getServer().getPlayerCount(),p.getServer().getAverageTickTime(),
                        p.serverLevel().getEntitiesOfClass(com.maidweapon.forge.compat.tlm.danmaku.ContractDanmakuProjectile.class,
                        p.getBoundingBox().inflate(48)).size(),com.maidweapon.forge.compat.tlm.danmaku.DanmakuShotQueue.pending(p));
                    return 1;
                })));
    }
    public static void setup(ServerPlayer p) throws Exception {
        var level=p.serverLevel();
        level.setChunkForced(0,0,true);
        for(int x=-18;x<=18;x++) for(int z=-8;z<=24;z++) {
            level.setBlock(new net.minecraft.core.BlockPos(x,64,z),
                    net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
            for(int y=65;y<=69;y++) level.setBlock(new net.minecraft.core.BlockPos(x,y,z),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        }
        if(p instanceof net.minecraftforge.common.util.FakePlayer) p.moveTo(.5,65,.5,0,0);
        else p.teleportTo(level,.5,65,.5,0,0);
        p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);
        var carrier=createCarrier(p,0);p.getInventory().selected=0;
        p.getInventory().setItem(1,new ItemStack(ModItems.MANKA_TALISMAN.get()));
        ContractMasteryService.setLevel(p.getServer().createCommandSourceStack(),p,10);
        ResonanceService.set(p,carrier,ContractCarrierData.getBindingId(carrier),1000);
        for(var old:level.getEntitiesOfClass(Zombie.class,p.getBoundingBox().inflate(24)))
            if(old.getName().getString().startsWith("Manka target ")) old.discard();
        for(int i=0;i<3;i++) {
            var target=new Zombie(level);target.moveTo((i-1)*4+.5,65,9.5,180,0);target.setNoAi(true);
            target.setCustomName(net.minecraft.network.chat.Component.literal("Manka target "+i));
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
            target.setHealth(1000);
            target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,testHelmet());level.addFreshEntity(target);
        }
        org.slf4j.LoggerFactory.getLogger("MankaAcceptance").info("MANKA_SCENE_READY nativeBauble=true survival=true level=10");
    }
    private static ItemStack createCarrier(ServerPlayer p,int slot) throws Exception {
        var level=p.serverLevel();
        var maid=(Mob)TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid,p);maid.moveTo(p.getX()+2,p.getY(),p.getZ(),0,0);level.addFreshEntity(maid);
        var slots=(IItemHandlerModifiable)maid.getClass().getMethod("getMaidBauble").invoke(maid);
        slots.setStackInSlot(0,new ItemStack(ModItems.MANKA_TALISMAN.get()));
        var carrier=new ItemStack(ModItems.MAID_SWORD.get());p.getInventory().setItem(slot,carrier);
        if(!ContractLifecycleService.capture(p,maid,carrier,false)) throw new IllegalStateException("Normal capture denied");
        ResonanceService.set(p,carrier,ContractCarrierData.getBindingId(carrier),1000);
        return carrier;
    }
    public static void bauble(ServerPlayer p,int mode) {
        var carrier=p.getMainHandItem();var payload=ContractMaidStorage.read(p,carrier);
        var slots=new net.minecraftforge.items.ItemStackHandler(30);
        if(mode>0) slots.setStackInSlot(0,new ItemStack(ModItems.MANKA_TALISMAN.get()));
        if(mode>1) slots.setStackInSlot(1,new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
        payload.put("MaidBaubleInventory",slots.serializeNBT());
        if(!ContractMaidStorage.commit(p,carrier,payload)) throw new IllegalStateException("Slot update refused");
    }
    private static ItemStack testHelmet() {
        var helmet=new ItemStack(net.minecraft.world.item.Items.IRON_HELMET);
        helmet.getOrCreateTag().putBoolean("Unbreakable",true);return helmet;
    }
    private static void arrows(ServerPlayer p) {
        var level=p.serverLevel();var shooter=new Skeleton(net.minecraft.world.entity.EntityType.SKELETON,level);shooter.setNoAi(true);
        shooter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,testHelmet());
        shooter.moveTo(p.getX()+5,p.getY(),p.getZ()+4,0,0);level.addFreshEntity(shooter);
        for(int i=0;i<8;i++) {
            var arrow=new Arrow(level,shooter);arrow.setPos(shooter.getX(),shooter.getEyeY(),shooter.getZ()+i*.04);
            var aim=p.getEyePosition().subtract(arrow.position()).normalize();arrow.shoot(aim.x,aim.y,aim.z,.45f,0);
            level.addFreshEntity(arrow);
        }
    }
    private MankaAcceptanceCommands() { }
}
