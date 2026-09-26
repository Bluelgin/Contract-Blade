# Contract Blade / 车万女仆：契约之刃

## 仓库范围 / Repository scope

默认分支现在只维护 **Core（契约之刃）**，当前版本为 **1.0.6**。旧版 Part（剧情、七宗罪、世界生成等）暂不更新，也不会随 Core 的发布包提供。需要查看旧版 Part 的源码时，请使用 [`codex/part-core-archive`](https://github.com/Bluelgin/Contract-Blade/tree/codex/part-core-archive) 存档分支；不要将它视为当前可维护版本。

The default branch contains the maintained Core addon only. The legacy Part campaign is paused and retained on the archive branch for reference, not shipped in Core releases.

Contract Blade is a Touhou Little Maid addon that lets maids form persistent contracts with supported weapons. Contracted companions can manifest in combat, return safely when their weapon is switched, build Contract Resonance, and strengthen their weapon through shared battles. Versioned compressed storage preserves maid inventories, appearances, capabilities, and progression while reducing contract NBT size.

## Features

- Bind a tamed maid to the dedicated Contract Blade or another supported weapon.
- Automatic manifestation and delayed recall for ordinary contracted weapons.
- Manual summon and recall controls for the dedicated Contract Blade.
- Contract Resonance as a renewable combat resource, separate from TLM favorability.
- Weapon growth, ownership protection, transfer safeguards, and multiplayer-safe contracts.
- Single-authority contract storage, automatic legacy migration, compressed maid data, and anti-recursion protection.
- Optional integrations for Goety, Iron's Spells 'n Spellbooks, Touhou Little Maid: Spell, SlashBlade, TLM: True POWER, TLM: Native POWER, and True POWER of Maid.
- Optional TACZ contract-gun support using TLM's native gun task and owner-inventory ammunition links.
- Optional Patchouli handbook support.
- Cat and Hound Blades store and release your own tamed cat or wolf, preserving its identity and vanilla data.

With Patchouli installed, craft the handbook from a book and an amethyst shard.
The Cat Blade uses an iron sword, cod, and an amethyst shard; the Hound Blade
uses an iron sword, bone, and an amethyst shard. Right-click your own tamed pet
to store it, then use the blade in the air to release it.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Java 17
- Touhou Little Maid 1.1.7 or newer

All compatibility integrations are optional and are detected at runtime.

TACZ contract guns require TACZ 1.1.8 or newer and a TLM version that provides
the `touhou_little_maid:gun_attack` task. The current TLM release is recommended.

## Build

```powershell
.\gradlew.bat clean build
```

The distributable JAR is generated in `build/libs/`.

## License

Code and original assets in this repository are available under the MIT License. See [LICENSE](LICENSE) and [ASSET_SOURCES.md](ASSET_SOURCES.md).
