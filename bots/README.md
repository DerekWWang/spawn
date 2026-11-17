# Spawn bots

Mineflayer bots that join a Spawn server as ordinary players.

A bot made here logs in the way a person does. The server gives it the same body, needs, skills and
physical rules as any player, with no operator rights and no special channel. It sees the world through
[mineflayer](https://github.com/PrismarineJS/mineflayer): blocks, entities and events pushed to it as they
happen, pathfinding, and direct control of its own movement and hands.

## What this package adds to mineflayer

| Part | File | What it does |
| --- | --- | --- |
| NeoForge login | `lib/neoforge.js` | Answers NeoForge's channel negotiation, declares the channels the server insists on, and sends the two acknowledgements the server waits for. Without it the server refuses the connection as a vanilla client. |
| The mod's ids | `lib/registry.js`, `data/spawn-registry.json` | Teaches mineflayer the names, states, shapes and sizes of the mod's blocks, items and entity types, and checks them against the ids the server announces at login. |
| The mod's interface | `lib/survival.js` | `bot.survival`: the bot's own body, the climate where it stands, the local weather the server sends each second, and the body actions every player has. |
| Server console | `lib/rcon.js` | A small RCON client for supervisors and tests: `rcon.connect({ port, password })`, then `send(command)`. |

## Requirements

- Node.js 22 or newer, and `git` on the path.
- A Spawn server on Minecraft 26.3 (NeoForge 26.3.0.77). For development, `qube server up` in the
  repository root starts one in offline mode and prints its port.

## Install

```bash
npm install --ignore-scripts
```

```bash
npm run setup
```

`npm run setup` fetches the Minecraft 26.3 protocol and data tables (about a minute the first time).

### This runs on unreleased code

Released mineflayer supports Minecraft up to 26.1. Support for 26.3 exists upstream only as open pull
requests, so `package.json` pins exact commits of one contributor's fork branches:

| Package | Pinned commit | Upstream pull request |
| --- | --- | --- |
| mineflayer | `ad4af126` | PrismarineJS/mineflayer#4206 |
| minecraft-protocol | `3473413d` | PrismarineJS/node-minecraft-protocol#1553 |
| prismarine-chunk | `a96fd705` | (26.3 light-mask encoding) |
| prismarine-physics | `e641ceb5` | (26.3 liquid gravity) |
| minecraft-data | `312f3cad` | PrismarineJS/minecraft-data#1345 |

The four code changes were read before pinning and are plain protocol work. The data tables are some
715,000 lines of generated JSON and were not read line by line. Install with `--ignore-scripts` so no
package runs code at install time. When 26.3 is released upstream, replace the pins with the released
versions, delete the `overrides` block and `scripts/setup-data.js`, and run the tests.

## Use

```js
const { createBot } = require('spawn-bots') // or require('./lib') inside this folder

const bot = createBot({ host: '127.0.0.1', port: 25565, username: 'Ada' })

bot.once('spawn', async () => {
  const body = await bot.survival.query()      // hunger, thirst, temperature, capacities, clothing, skills
  const air = await bot.survival.climate()     // air temperature, roof, fire, rain
  console.log(body.hunger, body.conditions, air.temperatureC, bot.survival.weather)

  const kiln = bot.findBlock({ matching: bot.registry.blocksByName['spawn_9b5fbb9:kiln'].id, maxDistance: 32 })
  const bear = Object.values(bot.entities).find(entity => entity.name === 'spawn_9b5fbb9:brown_bear')

  console.log(await bot.survival.act('drink')) // the server's one-line answer
})
```

`createBot` takes every mineflayer option, plus:

| Option | Default | Meaning |
| --- | --- | --- |
| `registryFile` | the bundled export | Path of another registry export |
| `strictRegistry` | `true` | End the bot when the server's ids disagree with the export |
| `commandTimeout` | `5000` | Milliseconds to wait for a `/survival` reply |

Everything else is mineflayer: `bot.blockAt`, `bot.findBlock`, `bot.dig`, `bot.placeBlock`, `bot.equip`,
`bot.setControlState`, `bot.entities`, `bot.inventory`, and plugins such as mineflayer-pathfinder.

### `bot.survival`

| Member | Returns |
| --- | --- |
| `query()` | The bot's body as JSON: needs, temperature, health, capacities, anatomy, clothing, conditions, skills |
| `climate()` | Air temperature, roof, nearby fire and rain where the bot stands |
| `weather` | The latest local weather report (temperature, rain, wind, snow), or `null` before the first |
| `act(action, target?)` | Runs a body action and returns the server's answer. Actions: `eat`, `drink`, `read`, `play`, `clothe`, `undress`, `mend`, `treat`, `social`, `feed`, `give_water` |
| `command(text, accept?, timeout?)` | Sends any command and resolves with the first chat line `accept` takes |

The bot emits `survival_weather` on each report. Commands run one at a time, so replies cannot be confused.

The mod's names carry their namespace (`spawn_9b5fbb9:kiln`); vanilla names do not (`stone`), as in
mineflayer generally.

## Keeping the ids current

`data/spawn-registry.json` is written by the server. Regenerate it whenever the mod's blocks, items or
entity types change, or when the server runs other mods as well:

1. In the server console or over RCON, as an operator: `survival export_registry`
2. Copy `spawn-registry.json` from the server's folder to `data/spawn-registry.json`.

A bot checks the export against the ids the server sends during login. If they disagree it reports which
entries differ and, by default, disconnects rather than misname what it sees. What that check cannot see
is a block that kept its name but changed its states; the export is the only source for those.

## Tests

```bash
npm test
```

Offline: encodings, the channel list, the handshake's replies, and merging the export into mineflayer's
tables.

```bash
node test/live.js <port>
```

Against a running server. The test bot must be an operator (`op SpawnTestBot`, after it has joined once,
because it places a block, gives itself an item and summons an animal). It checks that the bot joins,
reads its body, climate and weather, names a mod block, item and animal, is stopped by a mod block it
walks into, puts a jacket on and takes it off with mod actions, and walks.

```bash
node test/load.js --properties ../run/server.properties --steps 1,20,40,80
```

The load test. It adds bots in steps, 40 to a Node process, and at each step reads the server's tick
time over RCON, along with entity counts and the processor time and memory of the bot processes. Options
are listed at the top of the file: `--mode walk` makes the bots wander, `--spread` gives each its own
stretch of country, `--pid` adds the server's own processor time and memory. The bots need no rights.
A bot keeps its position between runs, so use a fresh `--prefix` to start a run at spawn. Bots are
ordinary survivors: left standing for about an hour they die of thirst and wake at the world spawn.

## Capacity

Measured on 16 November 2026 with `test/load.js`: one dev server (NeoForge 26.3.0.77, 8 GB heap) and the
bots on the same Intel i9-14900K under Windows 11, view and simulation distance 10, with the server
process pinned to the eight performance cores (see below). The figure is the server's average time per
tick. Its budget is 50 ms; past that the world runs slower than real time.

| Bots | Together, idle | Together, wandering | Each in its own area, idle |
| --- | --- | --- | --- |
| 1 | 5.5 ms | | |
| 8 | | | 36.2 ms |
| 16 | | | 42.8 ms |
| 20 | | 16.6 ms | |
| 32 | | | 117.3 ms (half the areas newly generated; server at its 8 GB heap limit) |
| 40 | 10.5 ms | 21.9 ms | |
| 80 | | 35.2 ms | |
| 120 | 24.2 ms | | |
| 200 | 35.2 ms | | |
| 300 | 64.1 ms | | |

As rules of thumb on this machine:

| A bot that is | costs the server about | so one server carries about |
| --- | --- | --- |
| standing among others | 0.15 ms a tick | 250 |
| wandering near others | 0.4 ms a tick | 110 |
| alone in its own loaded area, standing | 2.7 ms a tick | 16 |

Bots that are both far apart and walking cost more again. That case was not re-measured pinned; before
pinning, 40 of them overloaded the server until its watchdog stopped it.

### Pin the server to performance cores

On a processor with performance and efficiency cores, check where the server runs before trusting any
number. Windows ran this dev server, which `qube` starts in the background, on the efficiency cores. The
same 16 spread-out bots on one running server, changing only which cores it may use:

| Server allowed on | Tick |
| --- | --- |
| all cores (what Windows chose by default) | 105 to 160 ms |
| efficiency cores only | 126 to 139 ms |
| performance cores only | 45 to 49 ms |
| two performance cores | 41 to 44 ms |

To pin a running server from PowerShell (logical processors 0 to 15 are the performance cores on this
chip):

```powershell
(Get-Process -Id <server pid>).ProcessorAffinity = 0xFFFF
```

A server started another way, or on another machine, may be scheduled differently. Per-core load in
Task Manager shows where it landed.

### View and simulation distance

The same 16 spread-out bots, pinned, at a fixed noon:

| View distance | Simulation distance | Tick | Server memory |
| --- | --- | --- | --- |
| 10 | 10 | 48 ms | 5.0 GB |
| 10 | 4 | 41 ms | 5.0 GB |
| 6 | 4 | 30 ms | 2.1 GB |

### Where the time goes

- **Vanilla world simulation, on one thread.** With bots spread out, nearly all of the server thread is
  block ticks, natural spawning and animal movement over each bot's own loaded area. More cores do not
  help the tick: it runs on one.
- **Walking costs through entity tracking.** Each position update makes the server re-check that player
  against every tracked entity in the dimension, so walkers are dear where many entities are loaded.
- **Three costs in the mod itself were found here and fixed.** The crop calendar's check, asked for
  every random tick, built a text id and looked it up each time. Forbidden monsters stayed in the biome
  spawn lists, so the spawner built one, saw it discarded and tried again every tick. Dropped items
  never left the world. Together the fixes took 16 spread-out bots from 53 ms to 46 ms at noon.

The bots' side is cheap and does scale with cores. A bot takes about 50 MB and one to three hundredths
of a core. One Node process held 80 comfortably, lagged badly at 120 and dropped bots beyond that, so
run about 40 to a process.

## Limits

- **One mod set per process.** Mineflayer shares its data tables between every bot of a version in a
  process. Bots for servers with different mods must run in separate processes.
- **Tool requirements are not modelled.** `needsTool` is exported, but mineflayer's dig-time estimate
  does not know which of the mod's tools suit which block. The server decides what a dig yields.
- **The body-state channel is not decoded.** The server's binary `spawn_9b5fbb9:state` report is
  accepted and ignored; `query()` asks for the same information as JSON.
- **New biomes are not handled.** A server whose mods add biomes may send biome ids mineflayer's chunk
  reader does not expect.
- **Some packets are misread by the unreleased 26.3 tables.** Seen so far: block particles
  (`world_particles`). The packet is dropped and the protocol library logs a "partial packet" line;
  nothing else is affected.
- **One server thread.** How many bots a server carries depends on where they stand and whether they
  move, not on how many processor cores it has. See Capacity below.
