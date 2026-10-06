const express = require('express');

const app = express();
const PORT = 3000;

app.use(express.json());

app.get('/health', (req, res) => {
  res.json({
    assistant: 'Lovely',
    status: 'online'
  });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Lovely server running on port ${PORT}`);
});
