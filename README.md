# Contract Blade / 车万女仆：契约之刃

## 仓库范围 / Repository scope

默认分支现在只维护 **Core（契约之刃）**，当前版本为 **1.1.0**。旧版 Part（剧情、七宗罪、世界生成等）暂不更新，也不会随 Core 的发布包提供。需要查看旧版 Part 的源码时，请使用 [`codex/part-core-archive`](https://github.com/Bluelgin/Contract-Blade/tree/codex/part-core-archive) 存档分支；不要将它视为当前可维护版本。

The default branch contains the maintained Core addon only. The legacy Part campaign is paused and retained on the archive branch for reference, not shipped in Core releases.

Architecture and extension boundaries are documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Legacy seven-sins NBT from older worlds is preserved as archival metadata, but it no longer affects Core combat, resonance, deployment, or progression.

Contract Blade is a Touhou Little Maid addon that lets maids form persistent contracts with supported weapons. Call companions manually or let them protect you after enemy damage, build Contract Resonance, and strengthen their weapon through shared battles. Switching weapons does not dismiss a manually called companion. Versioned compressed storage preserves maid inventories, appearances, capabilities, and progression while reducing contract NBT size.

## Features

- Bind a tamed maid to the dedicated Contract Blade or another supported weapon.
- Press the configurable call key (default `]`) while holding a contract weapon to call or recall your maid.
- Protective manifestation after enemy damage, with restrained greeting and post-combat dialogue.
- Native TLM home/stay choices keep a companion resident; recall requires the same dimension and at most 32 blocks, without force-loading her chunk.
- Contract Resonance as a renewable combat resource, separate from TLM favorability.
- Weapon growth, ownership protection, transfer safeguards, and multiplayer-safe contracts.
- Single-authority contract storage, automatic legacy migration, compressed maid data, and anti-recursion protection.
- Optional integrations for Goety, Iron's Spells 'n Spellbooks, Touhou Little Maid: Spell, SlashBlade, TLM: True POWER, TLM: Native POWER, and True POWER of Maid.
- Optional TACZ contract-gun support using TLM's native gun task and owner-inventory ammunition links.
- Optional Epic Fight: Touhou Little Maid combat-task selection for supported melee contract weapons.
- Optional Patchouli handbook support.
- Cat and Hound Blades store and release your own tamed cat or wolf, preserving its identity and vanilla data.

With Patchouli installed, making your first Contract Blade item (including native
altar baubles) awards the tutorial handbook once per player. Login and merely
obtaining items do not award it. A full inventory defers delivery until space is
available. The receipt survives respawn and reconnecting. Lost books can still
be crafted from a book and an amethyst shard.
The Cat Blade uses an iron sword, cod, and an amethyst shard; the Hound Blade
uses an iron sword, bone, and an amethyst shard. Right-click your own tamed pet
to store it, then use the blade in the air to release it.

## Calling a companion / 呼唤与陪伴

Holding a contract no longer summons its maid automatically. Use `]` (changeable
in Controls) to call her manually; switching items does not dismiss her. A maid
still stored in your held weapon can come to your aid after actual enemy damage,
remain for at least 20 seconds and return after danger subsides. Environmental,
cancelled, fully absorbed and lethal damage do not trigger protective manifestation.

Use TLM's native home mode or sitting/stay command to leave her in your house.
Residents keep their own gear/work, survive logout and normal world saves, and
are not pulled away when you are hurt. Recall is same-dimension and within 32
blocks; unreachable residents stay where they are and cannot be duplicated.
Contract Interiors continue to manage their own residents separately.

## Maid baubles / 女仆饰品

Place these items in the maid's native Touhou Little Maid bauble slots; Curios is not required.

- **共鸣剑穗 / Resonance Sword Tassel** projects the contract weapon and enables automatic combat-task selection.
- **守护缎带 / Guardian Ribbon** projects the owner's armor without changing the maid's work.
- **同心结 / Heartbound Knot** combines weapon and armor projection with automatic work in one slot.
- Without an active tassel or knot, select the maid's work manually. Effects are exclusive: the first of these baubles in slot order takes priority. Projections are inactive inside the contract home.

All three baubles use the native TLM altar, not a crafting table (one ingredient
per pedestal). Tassel: 3 string, gold nugget, amethyst shard, red dye (0.15 P).
Ribbon: 3 string, 2 leather, amethyst shard (0.15 P). Knot: tassel, ribbon,
diamond, gold ingot, amethyst shard, Spirit Crystal (0.3 P).

Projection does not additionally copy the player's base attack or armor attributes. Removing a bauble restores the original equipment; if a player replaced it manually, the saved original is returned to the maid's backpack (or dropped nearby if full).

## Shrine fox spirits / 神社狐灵

With SlashBlade installed, Shinkitsu Shrines can appear in newly generated
snowy plains, even without BladeTetra. Taking the
shrine's White Fox blade establishes her own spirit contract on TLM 1.5.3 or
newer. Hold it and press `]` to call or recall her; holding it alone does not
summon her.

Use an empty soul talisman at the Contract Table to move White Fox out, then
move her into another supported weapon without an existing contract. Her
equipment, appearance and contract-home binding are retained. Keep her original
blade: her special story only continues while she lives in that exact blade.
Ordinary crafting cannot consume her occupied weapon or soul talisman.

The supplied White/Black Fox model pack is prepared automatically for TLM's
native loader. Existing same-ID packs are preserved. With BladeTetra, the shrine
encounter and existing divine-domain progress provide additional dialogue;
without it, no divine-domain quest is offered. Black Fox's model is available,
but her Boss, purification, and Wine Fox rescue story are not yet playable.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Java 17
- Touhou Little Maid 1.1.7 or newer

All compatibility integrations are optional and are detected at runtime.

With Epic Fight and EpicFight: Touhou Little Maid installed (including the latter's
required Avalon dependency), manifested contract maids wearing an active Resonance Sword Tassel or Heartbound Knot automatically select
`ef_tlm:fight_mode_task` for equipped melee weapons with actual maid attack motions.
SlashBlade, spell and gun integrations retain priority. Unsupported weapons use
ordinary combat; contract-home living and the maid's original work remain intact.
Animations and learned skills stay owned by the provider; no Epic Fight assets
are bundled. The development profile is `-PcompatTest=epicfight`, with an opt-in
isolated server fixture enabled by `-PepicFightTest=true` (never use this fixture
flag in a player world; it shuts down the test server when checks finish).

TACZ contract guns require TACZ 1.1.8 or newer and a TLM version that provides
the `touhou_little_maid:gun_attack` task. The current TLM release is recommended.

## Build

```powershell
.\gradlew.bat clean build
```

The distributable JAR is generated in `build/libs/`.

## License

Code and original assets in this repository are available under the MIT License. See [LICENSE](LICENSE) and [ASSET_SOURCES.md](ASSET_SOURCES.md).
The bundled derivative White/Black Fox model pack retains its separate
CC BY-NC-SA 4.0 license and original attribution, included inside its archive.
