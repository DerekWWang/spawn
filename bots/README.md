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

npm reads `overrides` only from the `package.json` at the root of an install. They take effect when you
install inside this folder. A project that installs `spawn-bots` as a dependency must copy the
`overrides` block, with the three pinned sources written out in place of the `$name` references, into
its own root `package.json`; otherwise mineflayer's own dependencies resolve to released versions that
do not know 26.3.

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
| `commandSettle` | `150` | Milliseconds to wait before sending a command, so packets sent just before it land first |
| `commandLimit` | follows the server's ticks: about 0.8 a second at full speed, bursts of 7 | Pacing that keeps the bot inside the server's command spam limit: `false` where the server has none, or an object of the settings below |

`commandLimit` settings, each optional. A value that cannot work (not a number, zero or less, a
`ceiling` under 20, a name not listed here) throws when the bot is created.

| Setting | Default | Meaning |
| --- | --- | --- |
| `ceiling` | `140` | The highest spam count the bot lets itself reach. One command counts 20; the server disconnects at 200 |
| `drainPerSecond` | follow the server | A fixed amount taken off the count each second, whatever the server does. Left out, the count falls with the server's ticks, read from the time report it sends every twenty ticks: a server at 5 ticks a second gets a quarter of the commands |
| `safety` | `0.8` | The share of the observed ticks that is trusted, when following the server |
| `fallbackTicksPerSecond` | `10` | The tick rate assumed where it cannot be read: before the server's second time report, and while the world is frozen with `/tick freeze` |

More than twenty ticks a second is never assumed, and if the time reports stop the count is taken to
have stopped falling.

Everything else is mineflayer: `bot.blockAt`, `bot.findBlock`, `bot.dig`, `bot.placeBlock`, `bot.equip`,
`bot.setControlState`, `bot.entities`, `bot.inventory`, and plugins such as mineflayer-pathfinder.

### `bot.survival`

| Member | Returns |
| --- | --- |
| `query()` | The bot's body as JSON: needs, temperature, health, capacities, anatomy, clothing, conditions, skills |
| `climate()` | Air temperature, roof, nearby fire and rain where the bot stands |
| `resources(radius?)` | What the bot can see to gather, fell or mine within `radius` blocks (1 to 16; the server's default is 12) |
| `weather` | The latest local weather report (temperature, rain, wind, snow), or `null` before the first |
| `act(action, target?)` | Runs a body action and returns the server's answer. Actions: `eat`, `drink`, `read`, `play`, `clothe`, `undress`, `mend`, `treat`, `social`, `feed`, `give_water` |
| `command(text, accept?, timeout?)` | Sends any command and resolves with the first system chat line `accept` takes (`accept` is given the line's text; left out, the first line is taken) |

The bot emits `survival_weather` on each report.

Commands run one at a time, each sent only when the one before has been answered or has timed out.
The server does not mark which command a line answers, so a reply is matched by what it is:

- Only system chat is taken. Player chat, action-bar overlays and the server's own announcements
  (joining, leaving, deaths, advancements) never are.
- `query()`, `climate()` and `resources()` take only a JSON report of their own shape, so one is never
  handed another's.
- `act()` and `command()` without `accept` take the next system line. If the server sends this player
  some other line at that moment, that line is the answer; pass `accept` where it matters.
- After a timeout the reply may still come. For thirty seconds a line that would have answered the
  timed-out command is dropped rather than handed to the next one. Where that command is in fact
  never answered, the next command of the same kind can lose its reply and time out once. A server
  that takes longer than `commandTimeout` to answer can still mismatch replies; raise the timeout.

Every call returns a promise that rejects when no reply comes in time (`error.code` is `ETIMEDOUT`),
when the bot disconnects (`EDISCONNECTED`, with the kick reason when there was one; calls waiting
in the queue and every later call reject at once), or, for the three JSON calls, when the bot is dead
(`EDEAD`) or unconscious (`EUNCONSCIOUS`: out cold, a bot is told of its own body by `query()` and
nothing else, and cannot move or speak until it comes round). Handle the rejection: a call whose promise is dropped fails silently, so that one careless
call cannot end a process full of bots.

The mod's names carry their namespace (`spawn_9b5fbb9:kiln`); vanilla names do not (`stone`), as in
mineflayer generally.

### What an actor has to know about commands

- **A command can overtake what was sent just before it.** The server runs chat commands ahead of the
  game packets queued in the same tick, so a command sent straight after changing the held item, or
  after a step, can still see the old hand or the old position. `bot.survival` waits `commandSettle`
  first. If you send commands yourself with `bot.chat`, leave a tenth of a second.
- **The server disconnects a player who sends commands too fast.** It counts 20 for each command and
  takes 1 off every tick, and at 200 a player who is not an operator is kicked for spam: about one
  command a second sustained, with a burst of nine. A server running slow ticks less often, so its
  count falls more slowly. `bot.survival` paces itself inside that, following the server's tick rate,
  so a queued call may wait. A sandbox that wants faster polling sets
  `command-spam-threshold-seconds=0` in `server.properties` and passes `commandLimit: false`.
- **Death is permanent.** When the bot dies, mineflayer respawns it as usual, but the server brings it
  back as a spectator: `bot.game.gameMode` becomes `spectator`, and from then on its commands are
  refused with "The dead take no part in the world", its clicks do nothing and its chat is not
  delivered. Rejoining does not change that. Listen for `death` and stop driving the bot; a dead bot
  that stays connected still holds its chunks loaded, so disconnect it. An operator returns a player
  to the world as a new person with `/survival revive <player>`.

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

Offline: encodings, the channel list, the handshake's replies, merging the export into mineflayer's
tables, `bot.survival` (replies, pacing, disconnects) against a stand-in bot, and the RCON client
against a local socket.

```bash
node test/live.js <port>
```

Against a running server. The test bot must be an operator (`op SpawnTestBot`, after it has joined once,
because it places a block, gives itself an item and summons an animal). It checks that the bot joins,
reads its body, climate and weather, names a mod block, item and animal, is stopped by a mod block it
walks into, puts a jacket on and takes it off with mod actions, and walks.

```bash
node test/lifecycle.js --properties ../run/server.properties
```

A whole life, played by ordinary survival players with no operator rights; the operator console (RCON)
only lays out the scene, hands over feed and turns the calendar forward. In seventeen steps a bot
gathers stone, fibre and sticks, knaps and twists them, fells logs, makes planks, a table and a stone
pickaxe, fails to break stone bare-handed, mines with the pickaxe by command and by striking, roofs
itself over and sees the server recognise the roof, pairs cattle and sees the calf on its twelfth day
and not before, pairs hens, nests the egg by a fire and sees the chick on the fourth day, and survives
the calendar being turned sixteen days. Then a villager and an animal die and are gone, a second bot
dies of thirst and returns as a spectator who can neither act, order nor speak, stays dead across a
rejoin, and is revived by the operator as a new person. Exits 0 only if every step passes. Run it on a
world you do not mind being levelled for fifteen blocks around the bot (`qube server up --stage`).

```bash
node test/active.js --properties ../run/server.properties --bots 20
```

What the mod's own interface costs: bots stand together and, one phase at a time, poll their body,
the climate and the resource survey and run a hand craft, while the server's tick is read.

```bash
node test/load.js --properties ../run/server.properties --steps 1,20,40,80
```

The load test. It adds bots in steps, 40 to a Node process, and at each step reads the server's tick
time over RCON, along with entity counts and the processor time and memory of the bot processes. Options
are listed at the top of the file: `--mode walk` makes the bots wander, `--spread` gives each its own
stretch of country, `--pid` adds the server's own processor time and memory. The bots need no rights.
A bot keeps its position between runs, so use a fresh `--prefix` to start a run at spawn. Bots are
ordinary survivors: left standing for about an hour they die of thirst and wake at the world spawn.

## Evaluation worlds

An evaluation should start every time from the same land, with nobody in it. `tools/eval-world.py` keeps
pinned copies of the dev world and puts one back before a run:

```bash
python tools/eval-world.py list
```

```bash
python tools/eval-world.py restore forest-lake
```

Stop the server first (`qube server down`); the script refuses while the world is in use. Then
`qube server up` serves the restored world, and a bot that joins arrives at the template's spawn as a new
person with empty hands.

Templates live in `run/eval-worlds/<name>/`, outside git. Each one's description, seed and spawn are kept
in the repository as `tools/eval-worlds/<name>.json`, so a template can be made again:
`qube server reset --seed <seed>`, `qube server up`, `setworldspawn`, `gamerule respawn_radius 0`, set the
time and weather, walk a bot round the spawn so the land is generated, `qube server down`, then
`python tools/eval-world.py save <name> --site tools/eval-worlds/<name>.json`. Saving leaves out every
player's data. The saved copy, not the seed, is what pins the land: chunks already generated stay as they
are whatever the mod's world generation does later, while land beyond them is generated from the seed by
whichever build is running.

| Template | Spawn | What is there |
| --- | --- | --- |
| `forest-lake` (seed 20261118) | 42 64 -54 | A sand beach on the north shore of a freshwater lake, with jungle and oak woodland behind it (about 60 trunks within 48 blocks), fallen sticks 6 blocks away, loose stones within 20, wild melons 38 blocks east and a chicken on the beach. Pigs, sheep and shallow clay banks are on the plains across the lake. Spring day 1, morning, clear, 18 °C. |

Drinking straight from the lake works but poisons 18% of the time; boiled water from a fired pot is safe.

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

### What the interface costs

With 20 bots together and each bot inside the server's command limit (about 15 calls a second in all),
no phase moved the tick: body report, climate, resource survey and continuous hand crafting all stayed
within the idle figure. With the limit off and each bot calling ten times a second (200 calls a second),
a body report cost the server about 0.05 ms, the climate too little to measure, and a resource survey
at radius 16 about 1 ms. Commands run between ticks, so they do not show in the tick time; a server
falls behind only when their total passes the slack left in each 50 ms.

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
  move, not on how many processor cores it has. See Capacity above.
