// node peek.js [wait seconds] : the team in one line each, for testing the host by hand.
const http = require('http')
const secs = Number(process.argv[2] || 0)
const req = http.request({ host: '127.0.0.1', port: Number(process.env.EVAL_PORT || 47100), method: 'POST', path: '/' }, res => {
  let d = ''; res.on('data', c => { d += c }); res.on('end', () => {
    const r = JSON.parse(d).result
    for (const a of r.actors) console.log(a.name, JSON.stringify(a.pos), a.water, 'hp', a.health, a.body && `hunger ${a.body.hunger} thirst ${a.body.thirst}`, '|', JSON.stringify(a.doing), '| items', JSON.stringify(a.items), '\n   last', JSON.stringify(a.last || null).slice(0, 500))
    if (r.alerts) console.log('alerts', r.alerts); if (r.heard) console.log('heard', r.heard)
  })
})
req.end(JSON.stringify(secs ? { op: 'wait', args: { seconds: secs } } : { op: 'team' }))
