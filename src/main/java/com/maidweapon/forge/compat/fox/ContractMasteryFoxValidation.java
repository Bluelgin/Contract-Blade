package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.BlackFoxSlashCompat;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Focused native escrow/victory/mastery regression, independent of historical arena scenery assertions. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractMasteryFoxValidation {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.mastery.foxValidation")) return;
        try {
            var server = event.getServer(); var level = server.overworld(); var anchor = new BlockPos(8, 100, 8);
            level.getChunkAt(anchor);
            var field = net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager"); field.setAccessible(true);
            var sections = (net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);
            sections.updateChunkStatus(new net.minecraft.world.level.ChunkPos(anchor), net.minecraft.server.level.FullChunkStatus.FULL);
            var player = new net.minecraftforge.common.util.FakePlayer(level, new GameProfile(UUID.randomUUID(), "MasteryFoxFixture"));
            player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND), player);
            player.setPos(8.5, 100, 8.5);
            var stand = (ItemFrame)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("slashblade:blade_stand_entity")).create(level);
            if (stand == null) throw new IllegalStateException("Native stand unavailable");
            var tag = stand.saveWithoutId(new CompoundTag());
            tag.putInt("TileX", anchor.getX()); tag.putInt("TileY", anchor.getY()); tag.putInt("TileZ", anchor.getZ());
            tag.putByte("Facing", (byte) 2); tag.putString("StandType", "minecraft:air");
            stand.load(tag); stand.setItem(BlackFoxSlashCompat.blade(level)); level.addFreshEntity(stand);
            BlackFoxShrineReturnValidation.run(player, stand).discard();
            LogUtils.getLogger().info("CONTRACT_MASTERY_FOX_PASS");
        } catch (Throwable failure) { LogUtils.getLogger().error("CONTRACT_MASTERY_FOX_FAIL", failure); }
        finally { event.getServer().halt(false); }
    }
    private ContractMasteryFoxValidation() { }
}
