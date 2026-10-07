package com.maidweapon.forge.compat.fox;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Camera-facing contact flash fixed in world space, never a screen-space icon. */
final class BlackFoxParryParticle extends TextureSheetParticle {
    private BlackFoxParryParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        lifetime = 14; gravity = 0; hasPhysics = false;
        xd = yd = zd = 0; quadSize = .72f;
        pickSprite(sprites);
    }
    @Override public void tick() {
        super.tick();
        quadSize = .72f + .07f * age;
        alpha = Math.max(0, 1 - age / (float) lifetime);
    }
    @Override public int getLightColor(float partial) { return 15728880; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                  double dx, double dy, double dz) {
            return new BlackFoxParryParticle(level, x, y, z, sprites);
        }
    }
}
