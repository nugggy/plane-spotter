// Merges the researched aircraft data with photo credits into app/src/main/assets/planes.json.
// Run with: node data/build_data.js
const fs = require('fs');
const path = require('path');

const master = fs.readFileSync(path.join(__dirname, 'master_list.tsv'), 'utf8')
  .split(/\r?\n/).slice(1).filter(Boolean).map(l => l.split('\t'));
const parts = fs.readdirSync(path.join(__dirname, 'parts')).filter(f => f.endsWith('.json'))
  .flatMap(f => JSON.parse(fs.readFileSync(path.join(__dirname, 'parts', f), 'utf8')));
const byId = Object.fromEntries(parts.map(p => [p.id, p]));
const photos = Object.fromEntries(JSON.parse(fs.readFileSync(path.join(__dirname, 'photos.json'), 'utf8')).map(p => [p.id, p]));

const problems = [];
const planes = master.map(([id, name, category, , melb]) => {
  const p = byId[id];
  if (!p) { problems.push('missing data: ' + id); return null; }
  const ph = photos[id];
  if (!ph || !ph.asset) problems.push('missing photo: ' + id);
  const text = JSON.stringify(p);
  if (/—/.test(text)) problems.push('em dash in ' + id);
  return {
    ...p, id, name, category,
    passengers: p.passengers == null ? null : String(p.passengers),
    localSpotting: melb === 'yes',
    photo: ph && { asset: ph.asset, artist: ph.artist, license: ph.license, page: ph.page, file: ph.file },
  };
}).filter(Boolean);

const out = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'planes.json');
fs.writeFileSync(out, JSON.stringify(planes));
console.log(planes.length + ' aircraft written to ' + out);
if (problems.length) { console.log(problems.join('\n')); process.exitCode = 1; }
