package com.maidweapon.forge.system.deployment;

/** One active bauble per maid; its capabilities may include either or both channels. */
public enum ContractProjectionMode {
    NONE, WEAPON, ARMOR, COMBINED;

    public boolean weapon() { return this == WEAPON || this == COMBINED; }
    public boolean armor() { return this == ARMOR || this == COMBINED; }
}
