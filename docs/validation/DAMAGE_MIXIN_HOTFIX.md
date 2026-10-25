# Damage observation overload hotfix (1.2.0)

## Scope and matching

Baseline: master `941ba52` (PRs #29–#32 reviewed). Only selectors in the damage
observation mixins change. Wrapper bodies, observation/provenance services,
Akatsuki damage policy, resources and gameplay numbers remain unchanged.

The old name-only selectors can select a wrong-signature helper added to the
same class. Mixin 0.8.5's implicit match limit and method order matter: a helper
placed before the original reproduces the crash. Do not assume every overload
is always selected. MixinExtras checks the selected method's full arguments;
`require=0` does not suppress that signature check.

Use full JVM descriptors for `hurt(DamageSource,float)boolean`, both defense
methods `(DamageSource,float)float`, and the three ForgeHooks observation
entrypoints. The seven optional Boss observation mixins use the same precise
hurt selectors; their cap `WrapOperation` enclosing selectors are precise too.

Keep explicit Mojmap and SRG aliases with `remap=false`. This build has no Mixin
refmap configuration or mapping annotation processor for Minecraft targets.
Changing only to `remap=true` cannot establish a working production refmap;
using only SRG would lose the Mojmap userdev hooks. ForgeGradle reobfuscates
ordinary Java references, not these unmapped annotation string literals.
Minecraft 1.20.1 MCP config confirms `m_6469_`, `m_21161_`, `m_6515_`; the fixture
checks both namespaces separately, including Mojmap helpers in an SRG class.

MixinExtras tag `0.5.3` (`d2e3450`) exposes `require/expect/allow` on `WrapMethod`.
`WrapMethodInjectionInfo` inherits normal Mixin selector parsing;
`WrapMethodInjector.checkSignature` runs after selection. **No injection is
made optional**: `defaultRequire=1` and required configurations remain intact.
A missing critical hook fails with Mixin diagnostics rather than silently
removing soul-damage evidence. No degradation path or extra runtime log is needed.

## Reproducible transformer fixture

```sh
python tools/run_damage_mixin_fixture.py --java-home "$JAVA_HOME"
# After the regular reobfuscated distribution build:
python tools/run_damage_mixin_fixture.py --java-home "$JAVA_HOME" \
  --jar build/libs/contract-blade-forge-1.20.1-1.2.0.jar
```

This compiles the **real production mixin**, with stub Minecraft classes and a
recording service, then runs **real Mixin 0.8.5 + MixinExtras 0.5.3 transformation**.
It is not a handwritten selector emulator and not a Forge launch. A tiny test
service supplies bytecode; it is outside all Gradle source sets and release JARs.
The optional `--classpath` permits cached dependencies. CI runs both source and
reobfuscated-JAR variants and uploads transform logs.

Four positive cases cover Mojmap/SRG, each with/without additional private
`hurt`, armor and magic three-argument overloads. Helpers precede native methods
to exercise the failure-producing order. Assertions cover real wrapper execution:
original return/HP result, original call once, armor/magic inputs and outputs,
helper isolation, exception propagation, scope cleanup and nested scopes.
The negative case restores old armor/magic name selectors **and sets require=0**;
it must fail with `InvalidInjectionException` from `WrapMethodInjector`.

Local source fixture: all five cases pass. These damage formulas and the recording
sink are synthetic; this does not prove real Minecraft defense calculations or
real Akatsuki gameplay. Existing Core CI's real soul/provenance fixture covers
those separately. Local full Gradle build was attempted but dependency resolution
could not finish; offline retry identifies uncached ForgeGradle 6.0.54. Do not
report compilation or reobf as passed until CI supplies that evidence.

## Published Torches Become Sunlight bytecode investigation

Downloaded official CurseForge artifacts and inspected `LivingEntityMixin` using
JDK 17 `javap -p -s` (and `-v` for annotations):

| File | CurseForge file | SHA-256 | Finding |
| --- | --- | --- | --- |
| 0.4.4-fix | 6851335 | `000cde07c2f675752b8900cd1fafdf0018f8953908e62e58b1f00a2c013d770b` | Food callback, no armor helper |
| 0.4.7-hotfix | 7488781 | `a16183f781be6c9c32672dfc565d472fe98a5e276405e554fe34477695a11b89` | Armor HEAD callback, no reported helper |
| 0.4.9 | 8258854 | `b269e6e53a562605d2e7b8ca4fa823da3e58135f36d20eb341719953615796ef` | Armor HEAD callback, no reported helper |
| 0.5.0-hotfix2 | 8549179 | `b2d6b4da7f7f81824ce13ace1d0c3cd2d03ef5edf0284a039bcf3112d5d82b80` | Armor HEAD callback, no reported helper |
| 0.5.0-1.20.1-alpha | 8929683 | `d1ba0175148bb901d760358dd1d232207c74d291488f2a8f78aa194fc81e2f68` | Armor HEAD callback, no reported helper |

The newer armor callback is named `getDamageAfterArmorAbsorb` but has descriptor
`(DamageSource,float,CallbackInfoReturnable)void`, with `@Inject(HEAD,cancellable=true)`.
It is **not** `(DamageSource,float,LivingEntity)float`; injector callbacks can also
be renamed by Mixin when merged. These artifacts do not establish the exact
reported crash. The public source master `2354b28` only has the food callback;
its dev branch does not have that Java source path.

Downloads use `https://edge.forgecdn.net/files/<first four ID digits>/<remaining ID digits>/<filename>`.
Do not redistribute third-party JARs in this repository. A real dual-Mod client/
server launch and the reporter's exact artifact/log remain required to claim that
specific combination is confirmed fixed. The same-signature collision class is
reproduced and fixed by the transformer fixture.

## Lightweight audit and remaining risks

All active `src/main` mixins/configurations were scanned for name-only selectors,
mixed mapping names, missing descriptors and injection requirements.

* Fixed: the nine observation mixin files, including ForgeHooks and seven optional
  Boss hooks. The Boss plugin already gates versions/native bytecode, but its
  untransformed-class gate cannot prevent a subsequent merged overload.
* Already precise: `SoulCarrierChangeMixin`, SlashBlade melee/attack helper
  wrappers, `SlashEffectProvenanceMixin`, and several recipe/entity hooks.
* Potential follow-up: TACZ `TaczSpentAmmoMixin.reduceAmmoOnce` is still name-only;
  no conflicting overload was established for the pinned provider. Other name-only
  injection hosts remain in projection, durability, native attack, event amount,
  fox and TACZ hooks. They can be sensitive to future signatures, but no concrete
  current collision was demonstrated; no unrelated broad refactor is included.
* `WrapOperation`/`Redirect` invocation targets already describe the called method;
  a bare enclosing selector is not by itself proof of a handler-signature crash.
* Existing `require=0` on separate dev/SRG invocation aliases is intentional;
  this patch neither relaxes mandatory evidence hooks nor changes optional gates.
