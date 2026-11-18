// A bot that joins, reports what it is and where, and keeps an eye on its own needs.
//
//   node examples/hello.js <port> [host] [name]
const { createBot } = require('../lib')

const port = Number(process.argv[2])
if (!port) { console.error('usage: node examples/hello.js <port> [host] [name]'); process.exit(2) }
const bot = createBot({ host: process.argv[3] || '127.0.0.1', port, username: process.argv[4] || 'Hello' })
let watch = null

bot.on('error', error => console.error('error:', error.message))
bot.on('kicked', reason => console.error('kicked:', JSON.stringify(reason)))
bot.on('end', reason => { clearInterval(watch); console.log('disconnected:', reason); process.exit(0) })
// Death is permanent: the server brings the bot back as a spectator whose commands are refused, and
// which still holds its chunks loaded. Stop driving it and leave.
bot.on('death', () => { clearInterval(watch); console.log('died; leaving'); bot.quit() })

bot.once('spawn', async () => {
  const added = bot.spawnMod.added
  console.log(`In the world at ${bot.entity.position.floored()} knowing ${added.blocks} mod blocks, ${added.items} mod items and ${added.entities} mod entity types.`)

  // Anything of the mod's within sixteen blocks, by name.
  const modBlocks = bot.findBlocks({ matching: block => block.name.includes(':'), maxDistance: 16, count: 20 })
  const names = [...new Set(modBlocks.map(position => bot.blockAt(position).name))]
  console.log('Mod blocks nearby:', names.join(', ') || 'none')
  console.log('Animals and people nearby:', Object.values(bot.entities).filter(e => e !== bot.entity).map(e => e.name || e.username).slice(0, 12).join(', ') || 'none')

  watch = setInterval(async () => {
    try {
      const body = await bot.survival.query()
      const weather = bot.survival.weather
      console.log(`hunger ${body.hunger.toFixed(0)} thirst ${body.thirst.toFixed(0)} body ${body.temperature.toFixed(1)} C | ${body.conditions}` +
        (weather ? ` | air ${weather.temperature.toFixed(1)} C, rain ${weather.rain.toFixed(2)}` : ''))
      if (body.thirst < 60) console.log(await bot.survival.act('drink'))
    } catch (error) { console.error('query failed:', error.message) }
  }, 10000)
})
