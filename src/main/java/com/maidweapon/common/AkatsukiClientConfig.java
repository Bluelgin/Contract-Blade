package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Independent of optional boss resources and safe on a dedicated server. */
public final class AkatsukiClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SIGNATURE_KATANA;
    static {
        var builder = new ForgeConfigSpec.Builder();
        SIGNATURE_KATANA = builder.comment("Show Akatsuki's cosmetic katana and sword animations during supported melee tasks.",
                "Only affects rendering. Disable to restore the equipped weapon's native appearance.",
                "赤月专属佩刀与近战动作；关闭后恢复实际武器的本体显示，不影响属性和战斗。")
                .define("signatureKatana", true);
        SPEC = builder.build();
    }
    private AkatsukiClientConfig() { }
}
