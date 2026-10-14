package com.maidweapon.forge.client;

import com.maidweapon.forge.network.AkatsukiDomainNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

/** Client-only accents. Never spawns damaging entities. */
public final class AkatsukiDomainVisuals {
    public static void accept(AkatsukiDomainNetwork.Cue cue) {
        var level = Minecraft.getInstance().level;
        if (level == null || cue.kind() <= AkatsukiDomainNetwork.END) return;
        var color = new DustParticleOptions(new Vector3f(.85f, .025f, .09f), 1f);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            level.addParticle(color, cue.target().x + Math.cos(angle), cue.target().y + .5,
                    cue.target().z + Math.sin(angle), 0, .01, 0);
        }
    }
    private AkatsukiDomainVisuals() {}
}
