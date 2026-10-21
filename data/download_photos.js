// Downloads each selected Wikimedia Commons photo at 1024px wide into the app's assets.
const fs = require('fs');
const path = require('path');
const photos = JSON.parse(fs.readFileSync(__dirname + '/photos.json', 'utf8'));
const outDir = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'photos');
fs.mkdirSync(outDir, { recursive: true });
const UA = 'PlaneSpotterApp/1.0 (https://github.com/nugggy/plane-spotter; personal educational app)';

(async () => {
  for (const p of photos) {
    const existing = fs.readdirSync(outDir).find(f => f.startsWith(p.id + '.'));
    if (existing) { p.asset = 'photos/' + existing; continue; }
    const url = 'https://commons.wikimedia.org/wiki/Special:FilePath/' + encodeURIComponent(p.file.replace(/ /g, '_')) + '?width=1024';
    for (let attempt = 0; attempt < 4; attempt++) {
      const r = await fetch(url, { headers: { 'User-Agent': UA }, redirect: 'follow' });
      if (r.ok) {
        const type = r.headers.get('content-type') || '';
        const ext = type.includes('png') ? 'png' : 'jpg';
        const buf = Buffer.from(await r.arrayBuffer());
        fs.writeFileSync(path.join(outDir, p.id + '.' + ext), buf);
        p.asset = 'photos/' + p.id + '.' + ext;
        console.log(p.id, ext, Math.round(buf.length / 1024) + ' KB');
        break;
      }
      console.log(p.id, 'HTTP', r.status, 'retrying');
      await new Promise(res => setTimeout(res, 3000 * (attempt + 1)));
    }
    await new Promise(res => setTimeout(res, 400));
  }
  fs.writeFileSync(__dirname + '/photos.json', JSON.stringify(photos, null, 1));
  console.log('missing:', photos.filter(p => !p.asset).map(p => p.id));
})();
