package com.maidweapon.forge.system.deployment;

/** The first equipped projection bauble selects either or both channels; independent effects may coexist. */
public enum ContractProjectionMode {
    NONE, WEAPON, ARMOR, COMBINED;

    public boolean weapon() { return this == WEAPON || this == COMBINED; }
    public boolean armor() { return this == ARMOR || this == COMBINED; }
}
