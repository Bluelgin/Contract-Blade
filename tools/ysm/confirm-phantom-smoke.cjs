// Confirm Forge's warning only in the disposable world copied by test-phantom-production.ps1.
const fs = require('node:fs'), path = require('node:path'), zlib = require('node:zlib');
const file = path.resolve(process.argv[2] || '');
const allowed = path.resolve(__dirname, '../../tmp') + path.sep + 'phantom-production-';
if (!file.startsWith(allowed) || !file.endsWith(path.join('saves', 'PhantomSmoke', 'level.dat')))
  throw Error('Not an isolated phantom smoke world');
const raw = zlib.gunzipSync(fs.readFileSync(file));
let cursor = 0, flag = -1;
function name() {
  const count = raw.readUInt16BE(cursor); cursor += 2;
  const text = raw.toString('utf8', cursor, cursor + count); cursor += count; return text;
}
function payload(type, names) {
  if (type === 1) { if (names.join('/') === '/Data/confirmedExperimentalSettings') flag = cursor; cursor++; }
  else if (type === 2) cursor += 2;
  else if (type === 3 || type === 5) cursor += 4;
  else if (type === 4 || type === 6) cursor += 8;
  else if (type === 7 || type === 11 || type === 12) {
    const count = raw.readInt32BE(cursor); cursor += 4;
    if (count < 0) throw Error('Invalid NBT array'); cursor += count * (type === 7 ? 1 : type === 11 ? 4 : 8);
  } else if (type === 8) name();
  else if (type === 9) {
    const element = raw[cursor++], count = raw.readInt32BE(cursor); cursor += 4;
    if (count < 0) throw Error('Invalid NBT list');
    for (let i = 0; i < count; i++) payload(element, [...names, String(i)]);
  } else if (type === 10) {
    let child; while ((child = raw[cursor++]) !== 0) payload(child, [...names, name()]);
  } else throw Error('Unsupported NBT tag: ' + type);
  if (cursor > raw.length) throw Error('Truncated NBT');
}
const rootType = raw[cursor++], rootName = name(); payload(rootType, [rootName]);
if (flag < 0) throw Error('Forge confirmation flag is absent; refuse to alter unrelated data');
fs.copyFileSync(file, file + '.before-smoke-confirmation');
raw[flag] = 1; fs.writeFileSync(file, zlib.gzipSync(raw));
console.log('Confirmed Forge warning in isolated PhantomSmoke copy only.');
