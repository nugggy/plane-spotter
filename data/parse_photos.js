const fs = require('fs');
const LIC = ['CC BY-SA 3.0 au', 'CC BY-SA 4.0', 'CC BY-SA 3.0', 'CC BY-SA 2.0', 'CC BY 3.0', 'CC BY 2.0', 'GFDL 1.2', 'GFDL', 'Public domain', 'OGL v1.0', 'CC0'];
const out = [];
for (const line of fs.readFileSync(__dirname + '/photos_raw.txt', 'utf8').split(/\r?\n/).filter(Boolean)) {
  const sp = line.indexOf(' ');
  const id = line.slice(0, sp);
  let rest = line.slice(sp + 1);
  const m = rest.match(/^(.*?\.(?:jpg|JPG|png|jpeg)) (.*)$/);
  const file = m[1];
  rest = m[2];
  const lic = LIC.find(l => rest.endsWith(' ' + l));
  const artist = rest.slice(0, rest.length - lic.length - 1).trim();
  out.push({ id, file, artist, license: lic, page: 'https://commons.wikimedia.org/wiki/File:' + encodeURIComponent(file.replace(/ /g, '_')) });
}
fs.writeFileSync(__dirname + '/photos.json', JSON.stringify(out, null, 1));
console.log(out.length);
