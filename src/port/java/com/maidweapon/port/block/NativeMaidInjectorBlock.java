package com.maidweapon.port.block;
import com.maidweapon.port.menu.NativeMaidInjectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class NativeMaidInjectorBlock extends Block {
 private static final VoxelShape SHAPE=Shapes.or(box(2,0,2,14,4,14),box(5,4,5,11,10,11),box(1,10,3,15,16,13));
 public NativeMaidInjectorBlock(Properties p){super(p);}
 @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos pos,Player p,BlockHitResult hit){
  if(p instanceof ServerPlayer sp)sp.openMenu(new SimpleMenuProvider((id,inv,player)->new NativeMaidInjectorMenu(id,inv,pos),Component.translatable("container.maid_weapon.maid_injector")),buf->buf.writeBlockPos(pos));
  return InteractionResult.sidedSuccess(l.isClientSide);
 }
 @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
}
