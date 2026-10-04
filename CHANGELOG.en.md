# Changelog

## 1.1.0

- Fixed carrier-loss recovery for separate contracts interfering with one another.
- Replaced the Spirit Crystal in the Heartbound Knot altar recipe with an ender pearl.
- Fixed contract-carrier loss incorrectly removing manifested maids. Carrier loss is now confirmed by binding after a grace period; a broken-but-present SlashBlade keeps its contract, while a truly destroyed carrier leaves the maid alive or manifests her safely before detaching the lost carrier.
- Added the Shinkitsu Shrine integration with SlashBlade, found in newly generated snowy plains, with an enshrined White Fox blade.
- Claiming the blade requires a combined kill count of 520 across carried SlashBlades. Right-click the offering stand while holding a cake, then right-click again with an empty hand to take the blade.
- The shrine's White Fox has her own spirit and forms a contract when claimed, supporting calling, recall, protection and life together.
- White Fox can move between supported weapons through the Contract Table using an empty Soul Talisman. Her exclusive dialogue resumes upon returning to her original shrine blade.
- Protected spirit carriers against consumption in standard crafting, anvil material use and grindstone processing.
- Added Black and White Fox maid models and a pixel-art dual-fox icon, with native Touhou Little Maid animations and furniture interactions. Automatic model preparation requires Touhou Little Maid 1.5.3 or newer.
- Added BladeTetra encounter dialogue and responses to existing divine-domain progress. With SlashBlade alone, White Fox remains available without divine-domain story progression.
- Updated White Fox dialogue, offering hints and model descriptions.
- Maids in Contract Interiors now use Touhou Little Maid's native roaming, pathfinding, schedules and furniture interactions, including ordinary wandering when no furniture is available.
- Improved continuity of the maid's position and activity when returning home. Once inside, she follows native behavior without repeated activity reassignment.
- Fixed interrupted furniture interactions and overwritten manually selected schedules. Leaving the interior restores the maid's previous work and schedule settings.
- With the default Minecraft clock, the interior's day-night cycle stays consistent with the maid's schedule.

## 1.0.8

- Making any item from this mod awards the tutorial handbook once per player, including altar baubles. Updated calling, protection, residence, bauble and integration instructions, removing outdated guidance.

- Updated the Contract Heart Key, Resonance Sword Tassel, Guardian Ribbon and Heartbound Knot textures with subtle animations and resting intervals.
- Fixed following companions' resonance drain, recovery and cooperation tracking stopping when the player switched held items.
- Protective manifestations no longer play attention responses or idle voices; delayed idle voices are suppressed during danger.

- Added a configurable Call / Recall Maid key, default ]. Manually called companions remain when switching items.
- Merely holding a contract weapon no longer summons its maid. Actual enemy damage can summon a stored companion to protect you before returning to rest after danger subsides.
- Native TLM home mode or sitting/stay can leave a maid in your house. Residence and position survive normal world saves and logout, and injury does not pull her away.
- Manual recall requires the same world and at most 32 blocks, with messages for distant, cross-world or unreachable companions.
- Added greeting, protective and post-combat dialogue, and updated player-facing hints and handbook instructions.

- Added the Resonance Sword Tassel, Guardian Ribbon and Heartbound Knot for the maid's native Touhou Little Maid bauble slots. All use native altar recipes; Curios is not required.
- The tassel enables contract weapon projection and automatic work selection. Without an active tassel or knot, the maid keeps her own weapon and manually selected work and schedule.
- The ribbon projects the owner's armor without changing work. The knot combines both projection abilities in one slot. Projection baubles do not stack; the first equipped slot takes priority.
- Added animated bauble textures and altar recipe instructions in the handbook.
- Removed the additional copying of the owner's base attack and armor stats. Maids retain their own base attributes, and projected weapons carry no duplicate contract growth data.
- Removing a bauble or recalling the maid restores her original equipment. Projections remain disabled in Contract Interiors.
- Existing projection data is migrated safely, restoring original equipment and base attributes.

## 1.0.7

### New Features

- Added Contract Interiors: a home you can build and decorate with your contracted maid. Choose from Plains Garden, Sakura Garden, Bamboo Grove, Lake Islet, or Hill Garden on your first visit. Your available land expands as your contract levels up, while existing builds remain intact.
- Added the Contract Heart Key. Hold the key in your main hand and a contract weapon you own in your offhand, then use the key to enter your home. Use it again inside to return. Crafting requires 2 Amethyst Shards, 2 Paper, and 1 Ender Pearl.
- Maids can stroll, rest, and use supported Touhou Little Maid furniture at home, following their schedule and surroundings. When you return, your maid resumes from her previous location or appears beside furniture suited to her current activity.
- Added a personal booklet written by your maid, sharing her thoughts on spirit manifestation and your life together.
- Added EpicFight: Touhou Little Maid integration. Manifested maids automatically enter Epic Fight mode when you hold a supported melee contract weapon. Dedicated SlashBlade, magic, and gun integrations retain priority.

### Improvements and Fixes

- Dedicated Contract Blades can form a contract directly with your own maid, without using the Contract Binding Table. Once bound, holding the blade in your main hand automatically manifests her.
- Contract Interiors now use Minecraft time by default, with a normal day-night cycle.
- Fixed an issue where interacting with a contract weapon inside an interior could unexpectedly return the player to the Overworld and cause the weapon to disappear.
- Maids keep their own equipment inside Contract Interiors instead of using equipment projections from outside. Equipping them or placing weapons and armor in their backpacks is restricted there to prevent item loss when leaving. Food and other ordinary items can still be stored.
- Fixed beds exploding inside Contract Interiors. Sleeping there does not overwrite your respawn point outside.
- Fixed maids repeatedly switching between ordinary combat and Epic Fight mode.
- Updated the Contract Heart Key texture and corrected the Contract Binding Table's inventory display angle and scale.
- Improved Contract Interior messages.

### Integration Requirements

Epic Fight integration requires Epic Fight, EpicFight: Touhou Little Maid, and its required Avalon dependency.
Contract Blade remains fully usable without these mods. Integration mods are not bundled with this release.
