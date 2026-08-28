const express = require('express');
const { readSources, writeSources } = require('./helpers');

const router = express.Router();

router.post('/source', (req, res) => {
  const payload = req.body;

  res.status(200).json({
    ok: true,
    received: payload,
    timestamp: new Date().toISOString(),
  });
});

router.post('/sources', async (req, res) => {
  try {
    const sources = req.body;
    const entry = {
      receivedAt: new Date().toISOString(),
      sources,
    };

    console.log('Entry received:', entry);

    const list = await readSources();
    list.push(entry);
    await writeSources(list);

    res.status(201).json({
      ok: true,
      stored: entry,
      total: list.length,
    });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

router.get('/health', (_req, res) => {
  res.status(200).json({ status: 'ok' });
});

module.exports = router;
