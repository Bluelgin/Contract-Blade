package com.maidweapon.forge.client.tooltip;
import java.util.Locale;
/** Formatting only: ability values are resolved by the server. */
final class ContractTooltipFormat {
    static String decimal(double n) { return String.format(Locale.ROOT, "%.1f", n); }
    static String percent(double n) { return decimal(n * 100) + "%"; }
    private ContractTooltipFormat() {}
}
