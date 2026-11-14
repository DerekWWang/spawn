# Spawn: a functional tech tree, stone to server

A NeoForge mod for Minecraft 26.3 that turns the spreadsheet
`tools/techtree/Functional_tech_tree_stone_to_server.xlsx` into game mechanics: 526 functions
(109 theories, 417 practical recipes), 389 items, 66 buildable stations and pieces of
infrastructure, and 31 kinds of natural deposit.

Nothing is granted by an era or a research budget. A society can use a function only once it has
**discovered** it, and it can discover it only by holding everything the function needs and
explaining how it works.

## Playing

Press **J** to open the journal.

1. **Gather.** Break grass for plant fibre, gravel for flint, and so on. Item tooltips say what each
   thing is made by and whether your society knows how.
2. **Discover.** The journal lists functions whose required theories you already know. To claim one
   you must hold every consumed input and every means of production (tools in your inventory,
   stations built and complete within 16 blocks, phenomena such as a night sky or flowing water
   where you stand). Then press *Explain & discover* and pick the true mechanism from four
   (the spreadsheet's column H is the answer key; the others come from related functions).
   A wrong answer fails even when the recipe is right, and you must wait before trying again.
3. **Make.** Functions with no station are done by hand from the journal (*Make x1 / x8*): they take
   time, and inputs are taken only when each run finishes. Everything else runs at a station.
   Hunting and harvesting happen in the world (kill game with a spear; cut ripe crops in a grain
   field with a sickle).

Knowledge belongs to a **society**: a scoreboard team (members share everything) or a lone player.
Knowledge is saved with the world and is never lost on death. A player who joins a team brings what
they know.

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

## Long-running effects

Some theories change the world beyond unlocking recipes: Writing lets a society write treatises
that teach a theory to another literate society; Recorded scholarship makes theory questions
easier; Scientific method halves the wait after a wrong answer; Thermodynamics stretches every
fuel by a quarter; Crop rotation, a seed drill and an irrigation canal raise grain yields;
Prospecting adds a *Survey ground* button that reports deposits beneath you.

## Commands

* `/techtree grant <players> <function id|all>` and `/techtree revoke ...` (operators)
* `/techtree list [player]`, `/techtree info <function id>`

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
water containers, tool wear, engines, discovery and its quiz, hand work, deposit gating,
treatises, persistence and worldgen.

Mapping names: this project uses Mojang's official mappings
(see https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
