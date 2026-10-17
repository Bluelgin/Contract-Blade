package com.maidweapon.forge.system.resonance;

/** Server-reported facts, ordered from least to most restrictive recovery. */
public enum ResonanceActivity {
    STORED, MANIFESTED_IDLE, ABILITY_IDLE, COMBAT, ABILITY_ATTACKING, BERSERK
}
