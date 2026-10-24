# White Fox — Purifying Edge integration

Native SE: `maid_weapon:purifying_edge`, registered in `slashblade:special_effect`.
No BladeTetra dependency. Existing shrine White Fox swords receive it through the
existing offering observation/claim path. Ordinary named White Fox swords do not.
The SE stays on the shrine sword when its spirit moves into a soul seal.

## Black Fox encounter hook

Call `BlackFoxEncounterApi.setResolver` in common setup. Return a `HitContext`
only for a valid hit against the actual polluted Black Fox encounter target.
The context contains the participating owner and the original shrine sword.
`BlackFoxEncounters` installs the real encounter resolver during common setup.
Outside its private, authorized Boss session the effect remains inactive.
Blade-on-blade clashes and blocked hits do not bypass the Boss's damage window.

The encounter must enforce participation, attack provenance (especially lingering
projectiles), Boss damage windows, and resolve maid projection attacks back to the
real carrier. Do not identify the Boss by display name, model or generic tags.
Do not pass a projected weapon as the original carrier.

## Damage rules

- Highest usable inventory SlashBlade base/refine panel strength is sampled at
  most once per 20 game ticks. No per-hit inventory traversal. Inventory changes
  become visible within one second; enchantments, rank, SA and SE are not copied.
- Add `max(0, strongest - WhiteFox)` to pre-armor damage of eligible hits.
  This is a flat supplement, not a claim to reproduce every donor's combo/SA.
- On a positive, uncanceled post-armor damage event, add 1% of Boss maximum HP,
  once per 20 ticks shared by all attackers of that target. No recursive damage.
- Broken/sealed swords and projected copies cannot donate. Shrine White Fox
  swords are excluded from the donor pool. No permanent attributes/NBT changes.
- The native Boss fixture verifies the extra effect against the actual encounter.
  Comparative kill-time balance still requires a real fight; no measured gain is promised.

## Private native verification

`runServer -PcompatTest=slashblade -PwhiteFoxSeTest=true -PcompatRunDir=<private-test-world>`
checks the native registry/capabilities and the real Forge event bus, then exits.
Run without SlashBlade to verify the optional compatibility boundary.

Verified on 2026-10-05 with SlashBlade Resharped 1.9.65:
native SE registry/serialization, temporary supplementation, unrelated targets,
canceled and zero hits, shared cooldown, refresh, broken/sealed/projection exclusion,
and unchanged original blade state all passed. The existing native shrine,
contract-table soul transfer and companion lifecycle fixtures also passed, including
the assertion that extraction retains the SE on the original sword. The no-SlashBlade
fixture, architecture/resource validators and build passed as well.
