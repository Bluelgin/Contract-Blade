// Assemble only approved native maid runtime assets; no editor/development artifacts.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const source = path.join(root, 'art/akatsuki/tlm-adaptation/pack');
const destination = fs.mkdtempSync(path.join(root, 'tmp/akatsuki-pack-'));
const write = (relative, contents) => {
  const file = path.join(destination, relative);
  fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, contents);
};
const files = ['pack.png', 'assets/akatsuki_winefox/models/entity/akatsuki.json',
  'assets/akatsuki_winefox/textures/entity/akatsuki.png', 'assets/akatsuki_winefox/textures/maid_icon.png',
  ...['winefox', 'foxmaid.tac', 'akatsuki.adaptation'].map(name => `assets/akatsuki_winefox/animation/${name}.animation.json`)];
const wrap = path.join(root, 'art/akatsuki/crimson-wrap');
const wrapValidation = JSON.parse(fs.readFileSync(path.join(wrap, 'static-validation.json'), 'utf8'));
assert(wrapValidation.boneHierarchyUnchanged && wrapValidation.protectedTexturePixelsUnchanged && wrapValidation.embeddedAnimations === 0);
for (const file of files) {
  const approved = file.endsWith('/models/entity/akatsuki.json') ? path.join(wrap, 'akatsuki-crimson-wrap.geo.json')
    : file.endsWith('/textures/entity/akatsuki.png') ? path.join(wrap, 'akatsuki-crimson-wrap.png') : path.join(source, file);
  write(file, fs.readFileSync(approved));
}
const cover = fs.readFileSync(path.join(root, 'art/maid_pack_icons/trio-pack-icon-256.png'));
write('pack.png', cover);
write('assets/akatsuki_winefox/textures/maid_icon.png', cover);
const metadata = JSON.parse(fs.readFileSync(path.join(source, 'assets/akatsuki_winefox/maid_model.json'), 'utf8'));
metadata.version = '1.1.2'; metadata.date = '2026-10-11';
metadata.author = ['Very Many Authors'];
metadata.model_list[0].description = ['{model.akatsuki_winefox.akatsuki.desc}'];
write('assets/akatsuki_winefox/maid_model.json', JSON.stringify(metadata, null, 2) + '\n');
// Reproduce TLM's built-in fallback followed by the pack's ordered overrides.
const effective = {};
for (const name of ['maid', 'tac', 'iss', 'im']) {
  const clips = JSON.parse(fs.readFileSync(path.join(root, `art/akatsuki/tlm-adaptation/native/${name}.animation.json`), 'utf8')).animations;
  for (const [key, value] of Object.entries(clips)) if (!(key in effective)) effective[key] = value;
}
for (const location of metadata.model_list[0].animation) {
  Object.assign(effective, JSON.parse(fs.readFileSync(path.join(destination, 'assets', location.replace(':', '/')), 'utf8')).animations);
}
assert.deepEqual(effective, JSON.parse(fs.readFileSync(path.join(root, 'art/akatsuki/tlm-adaptation/effective-adapted.animation.json'), 'utf8')).animations);
const validation = path.join(root, 'tmp/akatsuki-validation'); fs.mkdirSync(validation, {recursive: true});
fs.copyFileSync(path.join(destination, 'assets/akatsuki_winefox/models/entity/akatsuki.json'), path.join(validation, 'akatsuki-adapted.geo.json'));
fs.writeFileSync(path.join(validation, 'effective-adapted.animation.json'), JSON.stringify({format_version: '1.8.0', animations: effective}));
write('pack.mcmeta', JSON.stringify({pack: {pack_format: 15, description: '赤月 · 粉白 / Akatsuki · Pink-white | CC BY-NC-SA 4.0'}}, null, 2) + '\n');
for (const [locale, name, description, credit] of [
  ['zh_cn', '赤月 · 粉白', '粉白长发与同色狐尾，奶油白衣裙渐染赤色。适配车万女仆动作。', '原角色与模型：酒石酸菌及车万女仆模型包贡献者；赤月适配：Blade Tetra contributors；本次改造：契约之刃。CC BY-NC-SA 4.0。'],
  ['en_us', 'Akatsuki · Pink-white', 'Pink-white hair and matching fox tail, with cream garments fading into crimson. Native maid motion support.', 'Original character/model: TartaricAcid and Touhou Little Maid model-pack contributors; Akatsuki adaptation: Blade Tetra contributors; derivative: Contract Blade. CC BY-NC-SA 4.0.']
]) {
  write(`assets/akatsuki_winefox/lang/${locale}.json`, JSON.stringify({
    'maid_pack.akatsuki_winefox.name': locale === 'zh_cn' ? '赤月' : 'Akatsuki', 'maid_pack.akatsuki_winefox.desc': description,
    'model.akatsuki_winefox.akatsuki.name': locale === 'zh_cn' ? '赤月' : 'Akatsuki',
    'model.akatsuki_winefox.akatsuki.desc': locale === 'zh_cn' ? '粉白长发映着赤金结饰，酒红叠裙间露出浅色裙褶。' : 'Pink-white hair, crimson-gold ribbons, and wine-red wraps over pale pleats.',
    'model.akatsuki_winefox.akatsuki.credit': credit
  }, null, 2) + '\n');
}
for (const file of ['CREDITS.md', 'README.md']) write(file, fs.readFileSync(path.join(root, 'src/main/resources/licenses/akatsuki', file)));
write('CREDITS.md', fs.readFileSync(path.join(root, 'src/main/resources/licenses/akatsuki/CREDITS.md'), 'utf8') + '\n2026-10-10 approved crimson-wrap derivative: wine-red overlapping outer skirt, pale pleated inner skirt, cream sleeves with burgundy cuffs, red-gold head ribbon and waist cords. Original pink-white hair/tail, face, socks and shoes retained. Existing model ID, bone hierarchy and native maid animations preserved. CC BY-NC-SA 4.0 continues to apply.\n');
write('LICENSE-CC-BY-NC-SA-4.0.txt', fs.readFileSync(path.join(root, 'src/main/resources/licenses/fox_models/LICENSE-CC-BY-NC-SA-4.0.txt')));
const checksums = files.map(file => `${crypto.createHash('sha256').update(fs.readFileSync(path.join(destination, file))).digest('hex')}  ${file}`).join('\n') + '\n';
write('MODEL_ASSET_SHA256SUMS.txt', checksums);
fs.copyFileSync(path.join(destination, 'LICENSE-CC-BY-NC-SA-4.0.txt'), path.join(root, 'src/main/resources/licenses/akatsuki/LICENSE-CC-BY-NC-SA-4.0.txt'));
fs.copyFileSync(path.join(destination, 'MODEL_ASSET_SHA256SUMS.txt'), path.join(root, 'src/main/resources/licenses/akatsuki/MODEL_ASSET_SHA256SUMS.txt'));
console.log(destination);
