const fs = require('fs/promises');
const path = require('path');

const SOURCES_FILE = path.join(__dirname, 'sources.json');

async function readSources() {
  try {
    const raw = await fs.readFile(SOURCES_FILE, 'utf8');
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch (err) {
    if (err.code === 'ENOENT') return [];
    throw err;
  }
}

async function writeSources(list) {
  await fs.writeFile(SOURCES_FILE, JSON.stringify(list, null, 2));
}

module.exports = { readSources, writeSources };
