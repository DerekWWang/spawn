// node ways.js <actor> <item> [count] : every way that person could come by an item, for testing by hand.
const http = require('http')
const [actor, name, count] = process.argv.slice(2)
const t = Date.now()
const req = http.request({ host: '127.0.0.1', port: Number(process.env.EVAL_PORT || 47100), method: 'POST', path: '/' }, res => {
  let d = ''; res.on('data', c => { d += c }); res.on('end', () => {
    const r = JSON.parse(d); if (!r.ok) return console.log(r.error)
    console.log(`== ${r.result.item} (have ${r.result.have}) in ${Date.now() - t} ms`)
    if (typeof r.result.ways === 'string') return console.log('  ' + r.result.ways)
    for (const w of r.result.ways.slice(0, 8)) console.log(' ', w.open ? 'OPEN' : 'shut', w.kind, w.id, w.name || '', w.at ? 'at ' + w.at : '', w.why || '')
  })
})
req.end(JSON.stringify({ op: 'look', args: { actor, what: 'ways', name, count: Number(count || 1) } }))
