// node call.js <op> '<json args>' : one request to the commander host, for testing.
const http = require('http')
const [op, args] = [process.argv[2], JSON.parse(process.argv[3] || '{}')]
const req = http.request({ host: '127.0.0.1', port: Number(process.env.EVAL_PORT || 47100), method: 'POST', path: '/' }, res => { let d = ''; res.on('data', c => { d += c }); res.on('end', () => console.log(JSON.stringify(JSON.parse(d), null, 1))) })
req.end(JSON.stringify({ op, args }))
