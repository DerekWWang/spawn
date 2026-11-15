# Spawn: a functional tech tree, stone to server

A NeoForge mod for Minecraft 26.3 that turns the spreadsheet
`tools/techtree/Functional_tech_tree_stone_to_server.xlsx` into game mechanics: 526 functions
(109 theories, 417 practical recipes), 389 items, 66 buildable stations and pieces of
infrastructure, and 31 kinds of natural deposit.

Nothing is granted by an era or a research budget. A society can rely on a function only once it
has **discovered** it, by holding everything the function needs and taking the time to work it out.
On top of the tech tree sit three engines that make a settlement something to be managed:
**physics** (structures carry load, weather and decay), **engineering** (buildings with a use, and
reusable designs) and **logistics** (stores, spoilage, and books that never quite match reality).

## Playing

Press **J** to open the journal.

1. **Gather.** Break grass for plant fibre, gravel for flint, and so on. Item tooltips say what each
   thing is made by and whether your society knows how.
2. **Pick a goal.** Discovery is a means, not a ladder to climb. Choose any item or function as a
   goal (the *Goal* filter, or `/techtree plan <name>`) and the journal lists only the undiscovered
   steps between you and it, in an order you can follow.
3. **Discover.** Hold every consumed input and every means of production (tools in your inventory,
   stations built and complete within 16 blocks, phenomena such as a night sky or flowing water
   where you stand) and press *Study*. Study takes time: a few seconds for a simple craft, longer for
   deep ideas. Scientific method and recorded scholarship shorten it. Nothing is consumed.
4. **Make.** Functions with no station are done by hand from the journal (*Make x1 / x8*): they take
   time, and inputs are taken only when each run finishes. Everything else runs at a station.
   Hunting and harvesting happen in the world (kill game with a spear; cut ripe crops in a grain
   field with a sickle).

**Theory does not block practice.** People smelted copper for millennia before anyone could say why
it worked. A practical function can be discovered and run without the theories behind it; the price
is spoiled batches. Each missing theory wastes half the batches at first, every run is experience
that cuts the waste by 15%, and learning the theory removes it for every craft that depends on it.
Theories are still needed to reach other theories. So a society learns a theory when the waste hurts
more than the study, which is a decision, not a gate.

Knowledge belongs to a **society**: a scoreboard team (members share everything) or a lone player.
Knowledge is saved with the world and is never lost on death. A player who joins a team brings what
they know.

Servers that want the original rules can turn them back on in `config/spawn_9b5fbb9-local.toml`:
`quizMode` (pick the true mechanism from four to discover a function) and
`theoryRequiredForPractice`.

## Stations are buildings

Every station (hearth, kiln, forge, blast furnace, machine shop, cleanroom, wafer fab...) is a
controller block plus a structure you build around it, sized after the real thing. The controller's
tooltip and screen (hover the title) describe the structure; the screen names the first wrong block
and its coordinates.

* **Box stations** extend behind the controller, which sits in the middle of the bottom of the front
  face. Their walls must be the right material (masonry, refractory brick, steel casing, stainless
  steel, lead lining, cleanroom panels...). Furnaces must be hollow, many have an open top, and
  anything that burns needs a chimney to the sky.
* **Bigger builds do more.** Kilns, forges, furnaces, chemical plants, machine shops and cleanrooms
  have several sizes; larger ones run more batches per cycle. A 3x3 forge works one bloom at a
  time, 5x5 two, 7x7 four.
* **Rooms** (laboratories, machine shop, hospital, cleanroom, wafer fabs) are enclosed halls whose
  inside is free for equipment. Cleanrooms need one fan filter unit per 16 ceiling blocks.
  Semiconductor tools (diffusion furnace, mask aligner, vacuum evaporator, CVD reactor...) must stand
  *inside* a complete cleanroom when the process calls for one. Lithography is huge: a 25x9x25
  advanced wafer fab, a 5x4x7 DUV scanner.
* **Site rules.** A water wheel must dip into flowing water, a windmill must stand in open wind,
  a steam engine needs water for its boiler, a shipyard's slipway must run into water, a dam must hold
  back a reservoir.
* **Infrastructure:** a grain field is a boundary stone with tilled farmland around it; a livestock
  herd is a fenced pen (it slowly yields dung); a shaft or deep mine is a headframe over an open
  shaft 24 or 64 blocks deep; a drilling rig is a 12-high derrick over oil- or gas-bearing rock;
  the electric grid is a substation that covers 96 blocks.

Other stations named as means of production must be complete and within 16 blocks of the one doing
the work. Engines (steam, atmospheric) burn fuel only while a nearby machine draws power from them;
machine tools need a running wheel, windmill or engine nearby, or the grid. Electrical plant needs
grid coverage.

**Fuel.** Heat stations burn fuel from their fuel slot: wood for hearths and kilns; charcoal, coal or
coke for forges and smelting furnaces ("charcoal is the only fuel hot and clean enough for metal
until coke"). A charge that carries its own fuel (charcoal in a bloomery, coke in a blast furnace)
needs none. The Bessemer converter needs no fuel at all.

**Tools wear.** Hand axes, crucibles, moulds, tool sets, draw plates, photomasks and the like lose
durability each run.

**Quantities** follow historical recipes and stoichiometry where it matters: 1:3 lime mortar,
75:12.5:12.5 black powder, 88:12 bronze, two alumina per aluminium, N2 + 3 H2 -> 2 NH3, and so on.
Everything else uses one of each, as the spreadsheet does.

## Natural resources

Depth-0 resources map to vanilla items where they exist (logs, flint, clay, sand, coal, raw iron...)
and to new deposits elsewhere: malachite, cassiterite, galena, sphalerite, cinnabar, niter, sulfur,
fireclay, rock salt, borax, bauxite, chromite, wolframite, monazite, ilmenite sand and more, each
generated where it occurs in nature (salt in deserts and on beaches, amber and ilmenite in beach
sand, bauxite in tropical soils, sulfur near lava and in the Nether, chromite and nickel ore deep).

* **Recognised once known:** a deposit sits in the world from the start but breaks into plain rubble
  until the miner's society knows the right theory (iron and tin ores after Smelting, zinc after
  Alloying, platinum and manganese after Chemical elements...).
* **Access needs:** galena, cinnabar, coal and other shaft-mine ores yield only rubble unless mined
  below a complete shaft mine within 48 blocks; chromite, nickel, wolframite and rare earths need a
  deep mine; crude oil and natural gas come only from a drilling rig.

Both rules are configurable (`config/spawn_9b5fbb9-local.toml`), including whether they apply to
vanilla iron and coal ores.

## Physics: load, weather and decay

One block is one cubic metre of a real material. Block tags (`physics/material/*`) sort blocks into
thatch, timber, glass, brick, concrete, reinforced concrete, iron, stainless steel, earth and masonry,
each with a mass, a bending strength, a crushing strength, a wind resistance and a life span.

* **Nothing floats.** When a player or an explosion removes a block, anything it was holding up
  that no longer reaches the ground falls: cut the trunk and the tree comes down, undermine a wall
  and it drops on whoever is below. (Natural terrain is left alone; groups of more than 512 blocks
  are taken to be terrain.)
* **Spans are earned.** Each block of an overhang carries everything beyond it. Timber reaches four
  blocks from its support, stone and brick two, reinforced concrete five, steel eight; glass and
  earth almost nothing. A joint is as strong as the weaker of the two materials. Posts, arches,
  buttresses and hangers all work because the load path is computed, not pattern-matched, so any
  shape that would stand, stands.
* **Buildings are judged.** Structures you register (see below) are analysed for load. Parts
  stressed past 150% come down at once; parts over 100% survive until the next thunderstorm, which
  adds wind load that grows with height and exposure, and strips thatch long before stone.
* **Water pushes.** A barrier is checked metre by metre against the water standing behind it, for
  sliding and for overturning. A masonry wall needs thickness; timber, steel and reinforced concrete
  can stand thin if they are driven into the ground. Storms add a 2 m surge. A barrier that fails
  is breached where it is weakest.
* **Materials age.** Thatch lasts about 60 days dry and 8 under water, timber 400 dry, 60 in the rain
  and 25 submerged, iron rusts, earth washes away, brick and concrete outlast everyone. Worn blocks
  crack or mossy over before they go. A timber seawall is cheap and soon rebuilt; a concrete one is
  dear and stays.

`floatingBlockLimit`, `structuralStress`, `stormSeverity` and `decayRate` are configurable.

## Engineering: buildings and schemas

A **building** is whatever stands in a box around a **cornerstone** (stone + stick). The game does
not prescribe its shape or material: a house of clay, brick or steel is a house. What makes it one is
its **use**, and each use asks something of the structure:

| Use | Needs | Gives |
| --- | --- | --- |
| structure | soundness only | |
| residence | an enclosed, roofed room with a door, and 8 blocks of air per bed | beds for residents |
| storage | containers; enclosed under a roof to keep food | counted stock, slower spoilage |
| pen | ground closed in by fences or walls | room for livestock (3 blocks each) |
| barrier | to hold the water standing against it, and a storm surge | protected land |

Right-click the cornerstone for the menu: survey (shows the box and every problem with its
coordinates), fit the box to what is built, set the use, rename, grow or shrink a side.

A **schema** is a building's design, saved by name: its blocks relative to the cornerstone and the
bill of materials. Plan a schema at another cornerstone, facing any way, and the site lists what is
still missing block by block. Schemas belong to the society, so one design builds a whole street.

## Logistics: stores, spoilage and the books

Every quantity a society has exists twice.

* The **ground truth** is what is really there: counted by the game from the chests of storage
  buildings, the animals in pens, the residents in homes, and everything gathered, made, used and
  spoiled.
* The **observed truth** is what the society's records say. It is all a manager ever sees (the
  journal's *Ledger*, `/ledger report`).

They drift apart the way real books do. Goods moving in and out of a store are entered with error;
the clerks have habits, so the error leans one way; and what rots is never entered at all, so stores
hold less than the books claim. Headcounts are a fresh, imperfect census each morning.

How wrong the books are depends on what the society can count with:

| Records kept by | Learned with | Typical error |
| --- | --- | --- |
| memory and guesswork | | 40% |
| tally marks | Counting and tally | 25% |
| written records | Writing | 15% |
| written accounts with arithmetic | Arithmetic | 10% |
| accounts in standard measures | Weights and measures | 7% |
| paper ledgers | Papermaking | 5% |
| ledgers audited by sampling | Probability and statistics | 3% |
| machine-added ledgers | Calculator making | 2% |
| computer records | Vacuum-tube computer building | 0.5% |
| a networked database | Server | 0.1% |

A **stock-take** (`/ledger stocktake`, or *Take stock* on a store's cornerstone) sends someone to
count the shelves and corrects the books to the count. A count from memory is twice as good as
the running records; a society with Writing does twice as well again on a clay tablet and three
times on paper and ink (either is used up). A store may be counted once a game hour.

**Food spoils.** Fresh meat and fish have a half-life of 2 days; bread, cooked food, beer, vaccines
and insulin 6; grain, roots, flour, hides and fat 30. Salted and canned food keeps. A sound,
enclosed store halves the rate and a cold store nearby cuts it to a tenth. Food in a player's
pockets and in loose chests spoils at the full rate.

**Animals carry their young.** Mating starts a pregnancy; the birth comes a day later
(`gestationDays`). The census counts each pen's animals by kind, with those pregnant and young, and
tells animals herded in from animals born.

`spoilageRate`, `recordKeepingError` and `gestationDays` are configurable; operators can compare both
truths with `/ledger truth` and `/ledger history`.

## Long-running effects

Some theories change the world beyond unlocking recipes: Writing lets a society write treatises
that teach a theory to another literate society, and makes stock-takes on paper possible; Counting,
Arithmetic, Statistics and computing make its records more accurate; Scientific method halves the
time to study a theory and Recorded scholarship takes off another quarter; Thermodynamics stretches
every fuel by a quarter; Crop rotation, a seed drill and an irrigation canal raise grain yields;
Prospecting adds a *Survey ground* button that reports deposits beneath you.

## Commands

Everything a player can do from a screen can be done by command, so a bot can manage a society.

* `/techtree plan <item or function>`: the undiscovered steps to a goal, in order
* `/techtree status <function id>`: what is still missing to discover or run it here
* `/techtree discover <function id>`, `/techtree make <function id> [times]`, `/techtree stop`
* `/techtree list [player]`, `/techtree info <function id>`
* `/techtree grant <players> <function id|all>` and `/techtree revoke ...` (operators)
* `/building list`, `/building info|survey|fit <cornerstone>`, `/building use <cornerstone> <use>`,
  `/building name|grow|extent ...`
* `/building schema save <cornerstone> <name>`, `/building schema list|info|delete`,
  `/building plan <cornerstone> <schema>`, `/building missing <cornerstone>`, `/building unplan`
* `/building construct <cornerstone>` (operators: place the planned blocks)
* `/ledger report`, `/ledger census`, `/ledger stocktake <cornerstone>`,
  `/ledger get <metric>` (one recorded figure as the command's result, e.g. `stock/minecraft:wheat`,
  `people/alive`, `herd/cow`)
* `/ledger truth [player]`, `/ledger history [days]` (operators: the real figures beside the recorded)

## Not simulated yet

* **Workers.** There are no worker entities yet: players (or bots driving players) farm, haul, count
  and build. The engines expose what such workers will need: bills of materials and missing-block
  lists for builders, per-store stock for haulers, stock-takes for clerks.
* **Distribution.** Residents do not yet eat from stores, so nothing forces food to be where the
  people are. The books already say where everything is.
* **People.** The census counts players and villagers living in residences. Pregnancy is simulated
  for animals only.
* **Stations** are validated by shape, not yet by load: the physics engine judges registered buildings
  and anything a player undermines.

## Development

The spreadsheet is the source of truth. After editing it, regenerate and check:

```bash
python3 tools/techtree/generate_techtree.py
```

```bash
python3 tools/techtree/check_vanilla_refs.py
```

```bash
./gradlew runGameTestServer
```

The generator writes `src/techtree/generated` (tech-tree JSON, lang, models, textures, tags, loot
tables, recipes, worldgen). It fails if anything in the sheet cannot be mapped or if any function
becomes unreachable from nature. Structure sizes, quantities, fuel rules and deposit placement live
in tables at the top of the script.

Needs Java 25. Game tests cover structure formation and tiers, processing and batching, fuel,
water containers, tool wear, engines, discovery by study and by quiz, practice without theory,
goal planning, hand work, deposit gating, treatises, persistence and worldgen; cantilevers, arches,
collapse, storms, water pressure and decay; building uses and schemas; stock books, stock-takes,
spoilage, the census, gestation and record-keeping levels.

Mapping names: this project uses Mojang's official mappings
(see https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
