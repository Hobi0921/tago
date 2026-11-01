// Generates every Tago logo SVG from one set of geometry, so all files agree.
// Usage: node gen-kit.js   (writes into ../svg)
// Coordinates are on a 256-high canvas. Masters are filled shapes only: no strokes, text,
// masks or filters, so they scale and cut anywhere.
const fs = require('fs');
const path = require('path');
const OUT = path.join(__dirname, '..', 'svg');
if (require.main === module) fs.mkdirSync(OUT, { recursive: true });

const f = (n) => +n.toFixed(2);

// ------------------------------------------------------------------ palette
const C = {
  indigo: '#5B56A6', // brand colour (the app's accent)
  coral: '#F0623A',  // start node only
  ink: '#1E1B26',    // wordmark on light, dark backgrounds
  paper: '#FFF7F4',  // reversed artwork
  black: '#000000',
  white: '#FFFFFF',
};

// ------------------------------------------------------------------ primitives
const circle = (cx, cy, r) =>
  `M${f(cx - r)} ${f(cy)}A${r} ${r} 0 1 1 ${f(cx + r)} ${f(cy)}A${r} ${r} 0 1 1 ${f(cx - r)} ${f(cy)}Z`;

/** Straight segment with round caps, as a filled outline. */
const capsule = (x1, y1, x2, y2, w) => {
  const h = w / 2, len = Math.hypot(x2 - x1, y2 - y1);
  const nx = (-(y2 - y1) / len) * h, ny = ((x2 - x1) / len) * h;
  return `M${f(x1 + nx)} ${f(y1 + ny)}L${f(x2 + nx)} ${f(y2 + ny)}` +
    `A${h} ${h} 0 0 0 ${f(x2 - nx)} ${f(y2 - ny)}L${f(x1 - nx)} ${f(y1 - ny)}` +
    `A${h} ${h} 0 0 0 ${f(x1 + nx)} ${f(y1 + ny)}Z`;
};

/** Ring (circle outline of width w around centre-line radius r); holes via evenodd. */
const ring = (cx, cy, r, w) => circle(cx, cy, r + w / 2) + circle(cx, cy, r - w / 2);

/**
 * Arc of a monoline stroke with round caps, from angle a0 to a1 (degrees, screen space,
 * clockwise positive), travelling clockwise if cw, else anticlockwise.
 */
const arcBand = (cx, cy, r, w, a0, a1, cw) => {
  const h = w / 2, ro = r + h, ri = r - h;
  const p = (rad, deg) => [cx + rad * Math.cos((deg * Math.PI) / 180), cy + rad * Math.sin((deg * Math.PI) / 180)];
  const span = cw ? ((a1 - a0) % 360 + 360) % 360 : ((a0 - a1) % 360 + 360) % 360;
  const large = span > 180 ? 1 : 0;
  const [o0x, o0y] = p(ro, a0), [o1x, o1y] = p(ro, a1), [i1x, i1y] = p(ri, a1), [i0x, i0y] = p(ri, a0);
  const s = cw ? 1 : 0, rs = cw ? 0 : 1;
  return `M${f(o0x)} ${f(o0y)}A${ro} ${ro} 0 ${large} ${s} ${f(o1x)} ${f(o1y)}` +
    `A${h} ${h} 0 0 ${s} ${f(i1x)} ${f(i1y)}` +
    `A${ri} ${ri} 0 ${large} ${rs} ${f(i0x)} ${f(i0y)}` +
    `A${h} ${h} 0 0 ${s} ${f(o0x)} ${f(o0y)}Z`;
};

// ------------------------------------------------------------------ symbol
// Nine-dot grid at 64/128/192. Traced path 1-2-3 then 2-5-8 (a T). Node 1 is the start.
const GRID = [64, 128, 192];
const TRACED = [[64, 64], [128, 64], [192, 64], [128, 128], [128, 192]];
const IDLE = [[64, 128], [192, 128], [64, 192], [192, 192]];

const SYMBOL = {
  regular: { bar: 28, node: 22, idle: 12 },
  reversed: { bar: 26, node: 21, idle: 11 }, // light-on-dark reads heavier, so thinner
  small: { bar: 36, node: 26, idle: 16 },    // 24 px and below
};

/** Returns { body, start }: path data for everything, and for the start node on its own. */
const symbolParts = (k) => {
  const g = SYMBOL[k], h = g.bar / 2;
  const bars = `M64 ${64 - h}H192V${64 + h}H64Z` + `M${128 - h} 64H${128 + h}V192H${128 - h}Z`;
  const nodes = TRACED.slice(1).map(([x, y]) => circle(x, y, g.node)).join('');
  const idle = IDLE.map(([x, y]) => circle(x, y, g.idle)).join('');
  return { body: bars + nodes + idle, start: circle(64, 64, g.node) };
};

const svg = (w, h, title, inner, x0 = 0, y0 = 0) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${x0} ${y0} ${w} ${h}" width="${w}" height="${h}" role="img" aria-labelledby="title">
  <title id="title">${title}</title>
${inner}
</svg>
`;

const symbolGroup = (k, colour, startColour = colour, tx = 0, ty = 0) => {
  const p = symbolParts(k);
  const t = tx || ty ? ` transform="translate(${tx} ${ty})"` : '';
  return `  <g id="symbol"${t}>
    <path fill="${colour}" d="${p.body}"/>
    <path fill="${startColour}" d="${p.start}"/>
  </g>`;
};

// ------------------------------------------------------------------ wordmark
// Lowercase "tago", monoline 22, x-height 88..168 (baseline 168), bowls r=30 centre-line
// (outer 41: 1-unit overshoot), t ascender to 49, g descender to about 221.
// Local x: t's left edge at 0, o's right edge at 370. Spacing set optically (rounds closer).
const W = 22;
const wordmarkPaths = () => {
  const tx = 29, ax = tx + 96, gx = ax + 102, ox = gx + 102;
  const parts = [
    // t: stem, foot curving right, crossbar on the x-height
    capsule(tx, 60, tx, 150, W),
    arcBand(tx + 18, 150, 18, W, 180, 90, false),
    capsule(tx + 18, 168, tx + 28, 168, W),
    capsule(tx - 18, 99, tx + 30, 99, W),
    // a: bowl + stem
    ring(ax, 128, 30, W),
    capsule(ax + 30, 99, ax + 30, 157, W),
    // g: bowl + stem + hook
    ring(gx, 128, 30, W),
    capsule(gx + 30, 99, gx + 30, 180, W),
    arcBand(gx, 180, 30, W, 0, 135, true),
    // o
    ring(ox, 128, 30, W),
  ];
  return parts;
};
const WORD_W = 370;

const wordmarkGroup = (colour, tx = 0, ty = 0, k = 1) => {
  const t = tx || ty || k !== 1
    ? ` transform="translate(${f(tx)} ${f(ty)})${k !== 1 ? ` scale(${k})` : ''}"` : '';
  return `  <g id="wordmark" fill="${colour}" fill-rule="evenodd"${t}>
${wordmarkPaths().map((d) => `    <path d="${d}"/>`).join('\n')}
  </g>`;
};

// ------------------------------------------------------------------ files
const files = {};
const put = (name, content) => { files[name] = content; };

// Symbol: colour (indigo + coral start), one-colour, reversed, small cut.
put('tago-symbol-color.svg', svg(256, 256, 'Tago symbol', symbolGroup('regular', C.indigo, C.coral)));
put('tago-symbol-indigo.svg', svg(256, 256, 'Tago symbol, indigo', symbolGroup('regular', C.indigo)));
put('tago-symbol-black.svg', svg(256, 256, 'Tago symbol, black', symbolGroup('regular', C.black)));
put('tago-symbol-white.svg', svg(256, 256, 'Tago symbol, reversed', symbolGroup('reversed', C.white)));
put('tago-symbol-reversed-color.svg', svg(256, 256, 'Tago symbol, reversed colour', symbolGroup('reversed', C.paper, C.coral)));
put('tago-symbol-small-color.svg', svg(256, 256, 'Tago symbol, small sizes', symbolGroup('small', C.indigo, C.coral)));
put('tago-symbol-small-black.svg', svg(256, 256, 'Tago symbol, small sizes, black', symbolGroup('small', C.black)));
put('tago-symbol-small-white.svg', svg(256, 256, 'Tago symbol, small sizes, reversed', symbolGroup('small', C.white)));

// Wordmark.
put('tago-wordmark-ink.svg', svg(WORD_W, 256, 'Tago wordmark', wordmarkGroup(C.ink)));
put('tago-wordmark-black.svg', svg(WORD_W, 256, 'Tago wordmark, black', wordmarkGroup(C.black)));
put('tago-wordmark-white.svg', svg(WORD_W, 256, 'Tago wordmark, reversed', wordmarkGroup(C.white)));

// Lockups set the wordmark at 82%: at full size its 80-unit x-height outweighed the symbol.
const WK = 0.82;
// Horizontal lockup: symbol, gap of one node diameter (44) from its right edge (214), wordmark
// centred on the symbol's middle (y=128). Padding 42 each side so the bounds are symmetric.
const H_WX = 258, H_W = Math.ceil(H_WX + WORD_W * WK + 42);
const horizontal = (sym, start, word, kind = 'regular') =>
  symbolGroup(kind, sym, start) + '\n' + wordmarkGroup(word, H_WX, 128 - 128 * WK, WK);
put('tago-horizontal-color.svg', svg(H_W, 256, 'Tago logo', horizontal(C.indigo, C.coral, C.ink)));
put('tago-horizontal-black.svg', svg(H_W, 256, 'Tago logo, black', horizontal(C.black, C.black, C.black)));
put('tago-horizontal-white.svg', svg(H_W, 256, 'Tago logo, reversed', horizontal(C.white, C.white, C.white, 'reversed')));
put('tago-horizontal-reversed-color.svg', svg(H_W, 256, 'Tago logo, reversed colour', horizontal(C.paper, C.coral, C.paper, 'reversed')));

// Stacked lockup: symbol centred above, wordmark 44 below the symbol's bottom edge (214).
const S_W = Math.ceil(WORD_W * WK + 88), S_SX = (S_W - 256) / 2;
const S_WX = (S_W - WORD_W * WK) / 2, S_WY = 258 - 49 * WK, S_H = Math.ceil(S_WY + 221 * WK + 44);
const stacked = (sym, start, word, kind = 'regular') =>
  symbolGroup(kind, sym, start, S_SX, 0) + '\n' + wordmarkGroup(word, S_WX, S_WY, WK);
put('tago-stacked-color.svg', svg(S_W, S_H, 'Tago logo, stacked', stacked(C.indigo, C.coral, C.ink)));
put('tago-stacked-black.svg', svg(S_W, S_H, 'Tago logo, stacked, black', stacked(C.black, C.black, C.black)));
put('tago-stacked-white.svg', svg(S_W, S_H, 'Tago logo, stacked, reversed', stacked(C.white, C.white, C.white, 'reversed')));

// App icon artwork (square, for previews and the Play Store icon): symbol on indigo.
// Symbol at 64% of the tile (46 of 72 dp visible): inside the adaptive 66 dp safe circle.
// One colour: at launcher size a coral start node reads as a notification dot.
const ICON = 512, scale = (ICON * 0.64) / 176, off = ICON / 2 - 128 * scale;
const iconTile = (kind, startColour) => {
  const p = symbolParts(kind);
  return `  <rect width="${ICON}" height="${ICON}" fill="${C.indigo}"/>
  <g transform="translate(${f(off)} ${f(off)}) scale(${f(scale)})">
    <path fill="${C.paper}" d="${p.body}"/>
    <path fill="${startColour}" d="${p.start}"/>
  </g>`;
};
put('tago-app-icon-512.svg', svg(ICON, ICON, 'Tago app icon', iconTile('reversed', C.paper)));

if (require.main === module) {
  for (const [name, content] of Object.entries(files)) fs.writeFileSync(path.join(OUT, name), content);
  console.log(`wrote ${Object.keys(files).length} files to ${OUT}`);
}

module.exports = { C, symbolParts, wordmarkPaths, SYMBOL };
