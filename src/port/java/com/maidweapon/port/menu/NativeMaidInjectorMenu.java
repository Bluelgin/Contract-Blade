package com.maidweapon.port.menu;

import com.maidweapon.port.content.NativeContent;
import com.maidweapon.port.tlm.NativeSoulSlabService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Same four-slot layout and normal inventory protocol as the maintained Forge altar. */
public final class NativeMaidInjectorMenu extends AbstractContainerMenu {
    public static final int INJECT_BUTTON=0;
    private final SimpleContainer contents=new SimpleContainer(4);
    private final ContainerLevelAccess access;
    private final Player owner;
    public NativeMaidInjectorMenu(int id,Inventory inv,RegistryFriendlyByteBuf data){this(id,inv,data.readBlockPos());}
    public NativeMaidInjectorMenu(int id,Inventory inv,BlockPos pos){
        super(NativeContent.INJECTOR_MENU.get(),id);owner=inv.player;access=ContainerLevelAccess.create(owner.level(),pos);
        addSlot(new Slot(contents,0,35,35){@Override public boolean mayPlace(ItemStack s){return s.getItem() instanceof com.maidweapon.port.item.NativeContractSwordItem;}@Override public int getMaxStackSize(){return 1;}});
        addSlot(new Slot(contents,1,71,35){@Override public boolean mayPlace(ItemStack s){return NativeSoulSlabService.filled(s)||NativeSoulSlabService.empty(s);}@Override public int getMaxStackSize(){return 1;}});
        addSlot(new Slot(contents,2,116,35){@Override public boolean mayPlace(ItemStack s){return false;}});
        addSlot(new Slot(contents,3,142,35){@Override public boolean mayPlace(ItemStack s){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,142));
    }
    public boolean canInject(){return (NativeSoulSlabService.blankWeapon(contents.getItem(0))&&NativeSoulSlabService.filled(contents.getItem(1))||isExtracting())&&contents.getItem(2).isEmpty()&&contents.getItem(3).isEmpty();}
    public boolean isExtracting(){return NativeSoulSlabService.canExtract(owner,contents.getItem(0),contents.getItem(1));}
    @Override public boolean clickMenuButton(Player p,int button){
        if(p!=owner||button!=INJECT_BUTTON||!(p instanceof ServerPlayer sp)||!stillValid(p)||!p.isAlive()||p.isSpectator()||!canInject())return false;
        var weapon=contents.getItem(0);var slab=contents.getItem(1);
        if(isExtracting()){
            var result=NativeSoulSlabService.extract(sp,weapon,slab);if(result.isEmpty())return false;
            contents.setItem(0,ItemStack.EMPTY);contents.setItem(1,ItemStack.EMPTY);contents.setItem(2,weapon);contents.setItem(3,result);broadcastChanges();return true;
        }
        if(!NativeSoulSlabService.importInto(sp,weapon,slab)){
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("maid_weapon.transfer.unavailable"),true);return false;
        }
        contents.setItem(0,ItemStack.EMPTY);contents.setItem(1,ItemStack.EMPTY);contents.setItem(2,weapon);
        contents.setItem(3,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("touhou_little_maid:smart_slab_empty"))));
        broadcastChanges();return true;
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var source=slot.getItem();var old=source.copy();
        if(index<4){if(!moveItemStackTo(source,4,40,true))return ItemStack.EMPTY;}
        else if(source.getItem() instanceof com.maidweapon.port.item.NativeContractSwordItem){if(!moveItemStackTo(source,0,1,false))return ItemStack.EMPTY;}
        else if(NativeSoulSlabService.filled(source)||NativeSoulSlabService.empty(source)){if(!moveItemStackTo(source,1,2,false))return ItemStack.EMPTY;}
        else return ItemStack.EMPTY;
        if(source.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,source);return old;
    }
    @Override public void removed(Player p){super.removed(p);if(!p.level().isClientSide)clearContainer(p,contents);}
    @Override public boolean stillValid(Player p){return p==owner&&stillValid(access,p,NativeContent.INJECTOR.get());}
}
