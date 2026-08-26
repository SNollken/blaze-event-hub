// Ad-hoc i18n audit: parity en/pt-BR + dead keys + missing used keys
const fs = require('fs');
const path = require('path');

const src = fs.readFileSync(path.join(__dirname, '../src/i18n/translations.ts'), 'utf8');

function extractBlock(locale) {
  // find `  <locale>: {` then capture until matching closing `  },` at same indent
  const startMarker = locale === 'en' ? /^\s*en: \{/m : /^\s*'pt-BR': \{/m;
  const m = src.match(startMarker);
  if (!m) throw new Error('locale block not found: ' + locale);
  const startIdx = m.index + m[0].length;
  let depth = 1;
  let i = startIdx;
  while (i < src.length && depth > 0) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}') depth--;
    i++;
  }
  return src.slice(startIdx, i - 1);
}

function parseKeys(block) {
  const keys = [];
  const re = /'([^']+)'\s*:/g;
  let m;
  while ((m = re.exec(block)) !== null) keys.push(m[1]);
  return keys;
}

const en = parseKeys(extractBlock('en'));
const pt = parseKeys(extractBlock('pt-BR'));
const enSet = new Set(en);
const ptSet = new Set(pt);

const dupEn = en.filter((k, i) => en.indexOf(k) !== i);
const dupPt = pt.filter((k, i) => pt.indexOf(k) !== i);
const missingInEn = pt.filter((k) => !enSet.has(k));
const missingInPt = en.filter((k) => !ptSet.has(k));

// collect used keys: t('key') / t("key") and labelKey-style references
const used = new Set();
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'dist') continue;
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p);
    else if (/\.(ts|tsx)$/.test(entry.name)) {
      const content = fs.readFileSync(p, 'utf8');
      const re = /\bt\(\s*['"]([^'"]+)['"]/g;
      let m;
      while ((m = re.exec(content)) !== null) used.add(m[1]);
    }
  }
}
walk(path.join(__dirname, '../src'));

// dynamic keys: labelKey values in arrays (nav items etc.)
const allSrc = fs.readdirSync(path.join(__dirname, '../src'), { withFileTypes: true });
// also collect string literals that match key patterns assigned to labelKey-ish props
const keyLike = /^[a-z]+(\.[a-zA-Z0-9]+)+$/;

const deadKeys = [...enSet].filter((k) => !used.has(k));

console.log('en keys:', en.length, '| pt-BR keys:', pt.length);
console.log('duplicates en:', dupEn.length ? dupEn : 'none');
console.log('duplicates pt-BR:', dupPt.length ? dupPt : 'none');
console.log('missing in en:', missingInEn.length ? missingInEn : 'none');
console.log('missing in pt-BR:', missingInPt.length ? missingInPt : 'none');
console.log('used keys found in source:', used.size);
const usedButMissing = [...used].filter((k) => !enSet.has(k) || !ptSet.has(k));
console.log('used but missing from dict:', usedButMissing.length ? usedButMissing : 'none');
console.log('potentially dead (not referenced by static t()):', deadKeys.length);
deadKeys.forEach((k) => console.log('  dead?', k));
