package com.maidweapon.port.client;

import com.maidweapon.port.ContractBladeMod;
import com.maidweapon.port.content.NativeContent;
import com.maidweapon.port.item.ProbeSpiritVeinItem;
import com.maidweapon.port.storage.NativeChannelService;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Original compact lore/Shift presentation, only for implemented formal content. */
@EventBusSubscriber(modid=ContractBladeMod.ID,value=Dist.CLIENT)
public final class NativeSimpleTooltips {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var stack=event.getItemStack();
        if(com.maidweapon.port.projection.NativeProjection.isCopy(stack)){
            event.getToolTip().add(Component.translatable("maid_weapon.native.tip.projection").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if(stack.getItem() instanceof com.maidweapon.port.item.NativeContractSwordItem&&NativeChannelService.displayReference(stack)!=null){
            var viewer=net.minecraft.client.Minecraft.getInstance().player;
            var ref=NativeChannelService.displayReference(stack);
            var status=viewer==null?null:com.maidweapon.port.network.PortStatusNetwork.cached(viewer);
            if(status!=null&&status.authorized()&&ref.ticket().equals(status.ticket())&&viewer.getUUID().equals(ref.owner()))
                line(event.getToolTip(),status.manifested()?"maid_weapon.tooltip.recall_companion":"maid_weapon.tooltip.call_companion",ContractCompanionKeys.CALL.getTranslatedKeyMessage());
            return;
        }
        String lore;Consumer<List<Component>> details;
        boolean oath=stack.is(NativeContent.OATH_RING.get()),endless=stack.is(NativeContent.ENDLESS.get());
        if(stack.is(NativeContent.GUARD.get())){
            lore="maid_weapon.tooltip.support.guard.lore";
            details=lines->{line(lines,"maid_weapon.tooltip.projection.slot");line(lines,"maid_weapon.tooltip.support.guard.effect");line(lines,"maid_weapon.tooltip.support.guard.condition");};
        }else if(stack.is(NativeContent.TASSEL.get())||stack.is(NativeContent.RIBBON.get())||stack.is(NativeContent.KNOT.get())||oath||endless){
            String mode=stack.is(NativeContent.TASSEL.get())?"weapon":stack.is(NativeContent.RIBBON.get())?"armor":"combined";
            lore=oath?"maid_weapon.tooltip.oath.lore":endless?"maid_weapon.tooltip.simple.endless_contract.lore":"maid_weapon.tooltip.projection."+mode+".flavor";
            details=lines->{
                if(oath){
                    line(lines,"maid_weapon.tooltip.oath.details",ContractCompanionKeys.translatedKey());
                    line(lines,"maid_weapon.tooltip.oath.burnout");lines.add(Component.empty());
                    lines.add(Component.translatable("maid_weapon.tooltip.oath.quote").withStyle(ChatFormatting.DARK_PURPLE,ChatFormatting.ITALIC));
                }else{
                    line(lines,"maid_weapon.tooltip.projection.slot");line(lines,"maid_weapon.tooltip.projection."+mode);
                    if(endless)line(lines,"maid_weapon.tooltip.endless_contract");
                }
            };
        }else if(stack.getItem() instanceof ProbeSpiritVeinItem vein&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(ContractBladeMod.ID)){
            lore="maid_weapon.tooltip.simple.vein."+vein.stage()+".lore";
            details=lines->{line(lines,"maid_weapon.tooltip.simple.vein.use",vein.stage());line(lines,"maid_weapon.tooltip.vein.global");};
        }else if(stack.is(NativeContent.CRYSTAL.get())){
            lore="maid_weapon.tooltip.simple.crystal.lore";
            details=lines->{line(lines,"maid_weapon.tooltip.simple.crystal.use");line(lines,"maid_weapon.tooltip.simple.crystal.effect");};
        }else if(stack.is(NativeContent.INJECTOR_ITEM.get())){
            lore="maid_weapon.tooltip.simple.table.lore";details=lines->line(lines,"maid_weapon.tooltip.simple.table.use");
        }else if(stack.getItem() instanceof com.maidweapon.port.item.NativeContractSwordItem&&NativeChannelService.displayReference(stack)==null){
            lore=stack.is(NativeContent.HEAVY.get())?"maid_weapon.tooltip.simple.heavy.lore":stack.is(NativeContent.FAST.get())?"maid_weapon.tooltip.simple.fast.lore":"maid_weapon.tooltip.simple.blade.lore";
            details=lines->line(lines,"maid_weapon.tooltip.simple.blade.bind",ContractCompanionKeys.CALL.getTranslatedKeyMessage());
        }else return;
        if(Screen.hasShiftDown())details.accept(event.getToolTip());
        else{
            event.getToolTip().add(Component.translatable(lore).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable("maid_weapon.tooltip.simple.shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
    private static void line(List<Component> lines,String key,Object...args){lines.add(Component.translatable(key,args).withStyle(ChatFormatting.GRAY));}
    private NativeSimpleTooltips() { }
}
