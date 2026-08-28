const express = require('express');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

app.post('/source', (req, res) => {
  const payload = req.body;

  console.log('Received payload:', payload);

  res.status(200).json({
    ok: true,
    received: payload,
    timestamp: new Date().toISOString(),
  });
});

app.get('/health', (_req, res) => {
  res.status(200).json({ status: 'ok' });
});

app.listen(PORT, () => {
  console.log(`msc-api listening on http://localhost:${PORT}`);
});
