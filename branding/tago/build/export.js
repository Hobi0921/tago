// Exports PNGs (and the guide PDF) with headless Chrome, since no SVG rasteriser is installed.
// Usage: node export.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const ROOT = path.join(__dirname, '..');
const TMP = path.join(__dirname, 'tmp');
const PNG = path.join(ROOT, 'png');
fs.mkdirSync(TMP, { recursive: true });
fs.mkdirSync(PNG, { recursive: true });

const url = (p) => 'file:///' + p.replace(/\\/g, '/');

const chrome = (args) => {
  execFileSync(CHROME, ['--headless=new', '--disable-gpu', '--hide-scrollbars',
    '--force-device-scale-factor=1', ...args], { stdio: 'ignore' });
};

/** Screenshot a page at exactly w x h; transparent background unless opaque. */
const shoot = (pageUrl, out, w, h, opaque = false) => {
  const args = [`--window-size=${w},${h}`, `--screenshot=${out}`];
  if (!opaque) args.push('--default-background-color=00000000');
  chrome([...args, pageUrl]);
  console.log('png', path.relative(ROOT, out), `${w}x${h}`);
};

/** Rasterise one SVG at a given pixel size via a throwaway wrapper page. */
const svgToPng = (svgName, outName, w, h, opaque = false) => {
  const wrapper = path.join(TMP, outName.replace(/\.png$/, '.html'));
  fs.writeFileSync(wrapper, `<!doctype html><html><head><style>html,body{margin:0;background:transparent}
img{display:block;width:${w}px;height:${h}px}</style></head>
<body><img src="${url(path.join(ROOT, 'svg', svgName))}"></body></html>`);
  shoot(url(wrapper), path.join(PNG, outName), w, h, opaque);
};

// Store and launcher assets.
svgToPng('tago-app-icon-512.svg', 'tago-play-store-icon-512.png', 512, 512, true); // Play: opaque, square
// Logo PNGs for docs, slides and social (transparent).
svgToPng('tago-symbol-color.svg', 'tago-symbol-color-512.png', 512, 512);
svgToPng('tago-symbol-white.svg', 'tago-symbol-white-512.png', 512, 512);
svgToPng('tago-symbol-small-color.svg', 'tago-symbol-small-color-48.png', 48, 48);
svgToPng('tago-horizontal-color.svg', 'tago-horizontal-color-1208.png', 1208, 512);
svgToPng('tago-horizontal-reversed-color.svg', 'tago-horizontal-reversed-color-1208.png', 1208, 512);
svgToPng('tago-horizontal-black.svg', 'tago-horizontal-black-1208.png', 1208, 512);
svgToPng('tago-stacked-color.svg', 'tago-stacked-color-784.png', 784, 888);

// Play Store feature graphic (exactly 1024 x 500, opaque). Before the board: slide 6 shows it.
shoot(url(path.join(ROOT, 'board', 'feature-graphic.html')), path.join(PNG, 'tago-play-feature-graphic-1024x500.png'), 1024, 500, true);

// Presentation board: one PNG per slide (each slide is shown alone via its #fragment).
const board = path.join(ROOT, 'board', 'board.html');
const slides = (fs.readFileSync(board, 'utf8').match(/<section id="s\d+"/g) || []).length;
fs.mkdirSync(path.join(ROOT, 'board', 'slides'), { recursive: true });
for (let i = 1; i <= slides; i++) {
  shoot(url(board) + `#s${i}`, path.join(ROOT, 'board', 'slides', `slide-${i}.png`), 1600, 1000, true);
}

// Usage guide as a PDF.
const guide = path.join(ROOT, 'guide', 'tago-logo-guide.html');
chrome([`--print-to-pdf=${path.join(ROOT, 'guide', 'tago-logo-guide.pdf')}`, '--no-pdf-header-footer', url(guide)]);
console.log('pdf guide/tago-logo-guide.pdf');

fs.rmSync(TMP, { recursive: true, force: true });
