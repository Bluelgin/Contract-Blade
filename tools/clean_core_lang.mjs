import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const sourceRoots = [
  path.join(root, "src/main/java"),
  path.join(root, "src/main/resources/assets/maid_weapon/patchouli_books"),
  path.join(root, "src/main/resources/data/maid_weapon/advancements"),
  path.join(root, "src/main/resources/data/maid_weapon/patchouli_books")
];

function walk(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const absolute = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(absolute) : [absolute];
  });
}

const used = new Set([
  "itemGroup.maid_weapon",
  "item.maid_weapon.maid_sword",
  "item.maid_weapon.maid_sword_empty",
  "item.maid_weapon.maid_fast",
  "item.maid_weapon.maid_heavy",
  "item.maid_weapon.fast",
  "item.maid_weapon.heavy",
  "item.maid_weapon.spirit_crystal",
  "item.maid_weapon.test_slashblade",
  "item.maid_weapon.contract_fragments",
  "item.maid_weapon.maid_injector",
  "block.maid_weapon.maid_injector",
  "container.maid_weapon.maid_injector",
  "button.maid_weapon.inject",
  "button.maid_weapon.extract",
  "gui.maid_weapon.no_nearby_maid",
  "gui.maid_weapon.nearby_maid",
  "gui.maid_weapon.film_recipe"
]);

const keyPattern = /(?:maid_weapon|patchouli\.maid_weapon|itemGroup\.maid_weapon|item\.maid_weapon|block\.maid_weapon|container\.maid_weapon|button\.maid_weapon|gui\.maid_weapon)[a-zA-Z0-9_.-]*/g;
for (const sourceRoot of sourceRoots) {
  for (const file of walk(sourceRoot)) {
    const text = fs.readFileSync(file, "utf8");
    for (const match of text.matchAll(keyPattern)) used.add(match[0]);
  }
}

const replacements = {
  en_us: {
    "patchouli.maid_weapon.contract.landing": "These pages explain weapon contracts, manifestation, Contract Resonance, growth, ownership rules, and optional integrations.$(br2)Begin with $(l:basics/first_steps)First Steps$().",
    "patchouli.maid_weapon.entry.first_steps.page.0": "$(thing)Contract Blade$() is an addon for Touhou Little Maid. Craft a Contract Blade or a Contract Signing Table, bind one of your tamed maids, and fight together to build resonance and weapon experience.",
    "patchouli.maid_weapon.entry.first_steps.page.1.title": "Contract Signing Table",
    "patchouli.maid_weapon.entry.first_steps.page.1": "The table can bind a maid stored in a film to a supported weapon. It also protects contract ownership and prevents an active maid from being lost through unsafe inventory transfers.",
    "patchouli.maid_weapon.entry.troubleshooting.page.1.title": "Optional integrations",
    "patchouli.maid_weapon.entry.troubleshooting.page.1": "If a magic staff or SlashBlade falls back to ordinary attacks, verify that both client and server use matching versions of Contract Blade, TLM, and the optional addon.",
    "patchouli.maid_weapon.entry.integrations.name": "Optional Integrations",
    "patchouli.maid_weapon.entry.integrations.page.0": "Goety, Iron's Spells, and Touhou Little Maid: Spell enable compatible ranged magic behavior. SlashBlade and the True POWER addons enable dedicated blade work modes when their APIs are available.",
    "patchouli.maid_weapon.entry.integrations.page.1.title": "Safe fallback",
    "patchouli.maid_weapon.entry.integrations.page.1": "Every integration is optional. Missing or older addons fall back to Contract Blade's built-in behavior instead of preventing the game from loading."
  },
  zh_cn: {
    "patchouli.maid_weapon.contract.landing": "这些残页记录了武器契约、女仆显现、契约共鸣、成长与所有权规则，以及可选联动的使用方式。$(br2)请从$(l:basics/first_steps)初次缔约$()开始。",
    "patchouli.maid_weapon.entry.first_steps.page.0": "$(thing)契约之刃$()是车万女仆的附属模组。制作契约之刃或契约签订台，与一名已驯服女仆缔结武器契约，并通过共同战斗积累共鸣与武器成长。",
    "patchouli.maid_weapon.entry.first_steps.page.1.title": "契约签订台",
    "patchouli.maid_weapon.entry.first_steps.page.1": "签订台可以把魂符中的女仆与受支持武器缔结契约，同时保护契约所有权，避免显现中的女仆因危险的物品栏转移而丢失。",
    "patchouli.maid_weapon.entry.troubleshooting.page.1.title": "可选联动",
    "patchouli.maid_weapon.entry.troubleshooting.page.1": "如果法杖或拔刀剑退回普通攻击，请确认客户端与服务端使用相同版本的契约之刃、车万女仆及对应联动模组。",
    "patchouli.maid_weapon.entry.integrations.name": "可选联动",
    "patchouli.maid_weapon.entry.integrations.page.0": "Goety、铁魔法书与车万女仆：法术可提供兼容的远程施法行为；拔刀剑及真正的力量系列会在接口可用时启用专属工作模式。",
    "patchouli.maid_weapon.entry.integrations.page.1.title": "安全回退",
    "patchouli.maid_weapon.entry.integrations.page.1": "所有联动均为可选。缺少联动或使用较旧版本时，会回退到契约之刃的内置行为，不会阻止游戏启动。"
  }
};

for (const locale of ["en_us", "zh_cn"]) {
  const file = path.join(root, `src/main/resources/assets/maid_weapon/lang/${locale}.json`);
  const original = JSON.parse(fs.readFileSync(file, "utf8"));
  const filtered = {};
  for (const [key, value] of Object.entries(original)) {
    if (used.has(key)) filtered[key] = value;
  }
  Object.assign(filtered, replacements[locale]);
  const sorted = Object.fromEntries(Object.entries(filtered).sort(([a], [b]) => a.localeCompare(b)));
  fs.writeFileSync(file, `${JSON.stringify(sorted, null, 2)}\n`, "utf8");
  console.log(`${locale}: ${Object.keys(original).length} -> ${Object.keys(sorted).length}`);
}
