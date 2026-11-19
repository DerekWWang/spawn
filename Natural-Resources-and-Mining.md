---
title: Natural Resources and Mining
---
{% include nav.html %}

# Natural Resources and Mining

Minerals are not scattered evenly through the rock. Each 256-block district holds at most one finite ore body of one mineral, set in its proper host rock, and many districts hold nothing. Which mineral a district carries depends on the geological province around it, so a settlement on tin ground has to trade or travel for coal. This page explains where each resource occurs, how to find it from what you can actually see, what tools and time it takes to win, how much a block of ground holds, and the world rules that remove fantasy items, creatures and generated structures.

## Provinces, districts and host rocks

The world is divided at three scales.

| Scale | Size | What it decides |
| --- | --- | --- |
| Geological province | 2,048 x 2,048 blocks | The quarry rock of the region and which minerals its districts favour |
| Mineral district | 256 x 256 blocks | One ore body, or none. Its centre lies at least 48 blocks inside the district edge |
| Quarry cell | 128 x 128 blocks | Whether a body of quarry rock comes to the surface there |

In a world with the Tectonic profile there are four kinds of province, equally common.

| Province | Quarry rock | Barren districts | Main mineral (42% of districts) | Second (18%) | Third (10%) |
| --- | --- | --- | --- | --- | --- |
| Granite | Granite | 30% | Tin | Copper | Iron |
| Sandstone basin | Sandstone | 30% | Coal | Iron | Copper |
| Limestone | Limestone | 30% | Copper | Iron | Tin |
| Plain stone | Stone | 72% | none | Coal | Iron |

Each mineral sits in its own host rock, which replaces the ordinary stone all through the ore body. The host is the best sign that you are close.

| Mineral | Ore block | Host rock | Depth of the body below the surface at the district centre |
| --- | --- | --- | --- |
| Copper | Malachite ore | Limestone, or granite in a granite province | 24 to 48 blocks |
| Tin | Cassiterite ore | Granite | 35 to 59 blocks |
| Iron | Haematite ore | Plain stone | 24 to 48 blocks |
| Coal | Coal ore | Sandstone | 14 to 38 blocks |

The depth is measured once, from the natural ground height at the centre of the district, so the body stays one coherent mass across chunk borders. Where a valley, a cliff or a cave cuts below that level, the body can be exposed.

The mod removes the vanilla scattered coal, iron and copper ore, the vanilla granite pockets, and the vanilla large copper and iron veins. Regional bodies are the only source of these minerals in new terrain.

## The shape of an ore body

| Property | Value |
| --- | --- |
| Shape | A flattened ellipsoid at a random compass bearing |
| Length | 20 to 48 blocks |
| Width | 16 to 32 blocks |
| Thickness | 4 to 8 blocks |
| Dip | Rises 0.065 blocks for every block along its length |
| Grade | 15% to 30%, fixed for the whole district |
| What is replaced | Natural stone only (stone, granite, diorite, andesite, tuff, deepslate, limestone). Dirt, caves and water are left alone |

Inside the ellipsoid every stone block becomes host rock, and some of those become ore.

| Mineral | How the ore lies |
| --- | --- |
| Copper, tin | Evenly through the body. The share of ore blocks equals the grade |
| Iron | Banded. Two layers in every four carry ore at twice the grade (30% to 60%). The other two are barren host rock |
| Coal | Seams. Two layers in every three carry coal at one and a half times the grade (22.5% to 45%). The third is barren sandstone |

Copper, tin and iron bodies also have a feeder: a narrow column of mineralised host rock, about two blocks by four, rising up to 18 blocks above the centre of the body at 18% grade. It is the shallowest part of the deposit. In a Tectonic world its top lies 6 to 30 blocks under the ground at the district centre for copper and iron, and 17 to 41 blocks for tin. Coal has no feeder.

The bodies are finite. Once an ore block is mined it is gone, and nothing regrows.

## Accessory minerals

An ore position in a body sometimes holds a different mineral that belongs with it. The shares below are of the ore blocks in the body, not of all the rock.

| District | Accessory | Share of ore blocks | Block |
| --- | --- | --- | --- |
| Copper | Galena (lead, with silver) | 14% | Galena ore |
| Copper | Fluorite | 6% | Fluorite ore |
| Coal | Pyrite (sulfur) | 12% | Pyrite ore |
| Coal | Rock salt | 10% | Rock salt |
| Coal | Tar sand (oil) | 10% | Tar sand |
| Coal | Borax | 4% | Borax ore |
| Tin | Wolframite (tungsten) | 15% | Wolframite ore |
| Iron | Bauxite (aluminium) | 10% | Bauxite ore |

So 80% of the ore in a copper body is malachite, 64% of the ore in a coal body is coal, 85% in a tin body is cassiterite, and 90% in an iron body is haematite. Every one of the accessories is needed somewhere on the road to industry. See [Industrial Manufacturing](Industrial-Manufacturing).

## Quarry rock and clay

- **Quarry rock.** 55% of the 128-block quarry cells hold a round body of rock 20 to 40 blocks in radius. It replaces the natural stone in the top 13 blocks under the surface, and within 10 blocks of its centre it replaces the soil too, so the rock crops out. In a Tectonic world the rock is the quarry rock of the province, which in a plain stone province is ordinary stone. Limestone is burned for lime.
- **Clay.** Clay replaces the top three blocks of soil or gravel where there is water within 3 blocks, in about 35% of 12-block patches along shores and banks. Clay follows real nearby water and nothing else.

## What lies on the surface

| Resource | Where | Chance | Gathering gives |
| --- | --- | --- | --- |
| Loose stones | On bare rock, gravel or sandstone | 3% of positions | 3 loose stone |
| Loose stones | On any other firm ground | 0.1% of positions | 3 loose stone |
| Fallen sticks | On soil within 4 blocks of logs or leaves | 1.8% of positions | 2 sticks |

Ordinary soil has almost no loose stone. Look on exposed rock.

Wild plants grow in stands of one species, 5 to 12 plants within a radius of 3 to 6 blocks. There is one possible stand in each 32 x 64 block cell, of one of eight kinds, and it appears only where the habitat fits. All stands need open sky, soil underfoot and a temperate biome. In a Tectonic world they also need relief of 45 or less, a baseline temperature between 3 and 34 °C and humidity of at least 0.2.

| Stand | Habitat | Gather in | Gives |
| --- | --- | --- | --- |
| Wild flax | Open dry ground, not woodland | Spring, summer, autumn | 2 flax fibre, 2 flax seeds |
| Wild yarrow | Open dry ground, not woodland | Spring, summer, autumn | 2 yarrow leaves, 2 yarrow seeds |
| Wild wheat | Open dry ground, not woodland | Spring, autumn | 1 wheat, 2 wheat seeds |
| Wild carrot, potato or beetroot | Open dry ground, not woodland | Any season | 2 carrots, or 2 potatoes, or 1 beetroot and 2 beetroot seeds |
| Wild bramble | Forest or taiga | Summer, autumn | 3 blackberries, 1 bramble seed |
| Wild rice | Warm ground within 3 blocks of water | Summer, autumn | 2 rice grain, 2 rice seeds |
| Wild maize | Warm dry ground away from water | Summer, autumn | 1 maize, 2 maize seeds |
| Wild pumpkin or melon | Warm dry ground away from water | Summer, autumn | 2 pumpkin seeds, or 2 melon slices and 1 melon seed |

Rice, maize and gourds also need a baseline of at least 14 °C in a Tectonic world. A gathered plant is removed for good. The stands are where the seed for the mod's crops comes from, so sow what you gather. See [Agriculture and Livestock](Agriculture-and-Livestock).

Apple trees stand in small groves of 2 to 5 trees, at most one grove in each 128-block cell, in mild biomes on soil. In a Tectonic world they need a baseline of 5 to 27 °C, relief of 35 or less and humidity of at least 0.3. Oak leaves do not drop apples.

## Finding deposits

Nothing in the game tells you where buried ore is. You find it by looking.

**Read the country.** Granite quarries mean a tin province, sandstone means coal, limestone means copper, and plain stone usually means poor ground. Inside a promising province, search each 256-block district: valley sides, cliffs and caves that cut 15 to 60 blocks below the general ground level are where a body or its feeder shows. A patch of host rock in the wrong kind of stone is the edge of a body.

**Observe.** `/survival resources [radius]` lists up to 64 of the nearest resources you can actually see from where you stand, nearest first.

| Rule | Value |
| --- | --- |
| Radius | 1 to 16 blocks, 12 if not given, and at most 8 blocks up or down |
| Line of sight | Required from your eyes to the block: any face turned towards you that open air reaches. Buried blocks are never listed |
| Who | A living, sighted humanoid. The blind see nothing |
| Listed | Hand-gatherable surface resources, fruit trees, crops, malachite, cassiterite, haematite and coal ore, and quarry material (limestone, granite, sandstone, red sandstone, clay, gravel, sand) |
| Each entry | Name, kind, host, position, whether it is ready to harvest, and whether to gather, harvest or mine it |

The accessory ore blocks (galena, fluorite, pyrite, rock salt, tar sand, borax, wolframite, bauxite) are listed as minerals too.

**Prospect.** `/survival prospect <x> <y> <z>` examines one exposed mineral or quarry block within 4 blocks and in line of sight. `/survival prospect` with no position examines a piece of malachite, cassiterite or haematite held in the main hand. The answer names the material and its host with a confidence of 50% plus 2% per Mining level, up to 90%. It reports visible material only, never the size of a buried reserve, and it gives no Mining experience.

Operators have `/survival environmentSurvey <x> <z>`, which names the mineral and position of a district's deposit. It is a game-master tool and is not available to ordinary players.

## Tools required

| Block | Tool | Hardness |
| --- | --- | --- |
| Malachite, cassiterite, haematite, galena, pyrite, fluorite, wolframite ore | Pickaxe of stone grade or better | 3 |
| Rock salt, bauxite ore | Pickaxe of stone grade or better | 2.5 |
| Borax ore | Pickaxe of stone grade or better | 2 |
| Coal ore | Pickaxe | vanilla |
| Limestone | Pickaxe | 2.5 |
| Tar sand | Shovel | 2 |
| Obsidian | None. It breaks like stone (hardness 1.5) into 3 obsidian flakes | 1.5 |
| Clay | None. A shovel is quicker | vanilla |

Without the right tool the job is refused and nothing drops. The first pickaxe is knapped stone, bound with cordage, which is of stone grade. Obsidian tools cut fast and last 54 uses. Bronze tools last 420 uses. Vanilla wooden, stone, copper, iron, golden and diamond tool recipes are disabled; metal tools are forged and finished at the forge and anvil. See [Building Crafting and Workstations](Building-Crafting-and-Workstations).

## How mining works

Rock is not broken by holding the mouse button. Hitting any block that a pickaxe mines starts a timed job.

1. Stand within 4 blocks, hold the pickaxe in your main hand, and left-click the block once, or use `/survival mine <x> <y> <z>`.
2. The job needs `hardness x 30 / tool speed` work ticks, and never less than 30.
3. You do one work tick per game tick, multiplied by your work rate: the lower of your manipulation and mental capacity, times 50% at Mining level 0 rising to 100% at level 20. A healthy beginner breaks an ordinary ore block in about 60 ticks, three seconds.
4. Stay within 4 blocks, and keep the tool in hand where the tool does the work (a block that needs a tool or is sped by one). Walking away, putting that tool away, or a change to the block cancels the job; wear on the tool does not, and nor does eating while you dig something that needed no tool. Work waits while you sleep.
5. A block walled in by solid blocks on every side cannot be mined. Dig to a face of it first.
6. When it finishes the block breaks, the drops fall, the tool takes its normal wear, and you gain Mining experience for the ticks you actually worked.

Each work tick costs 0.0005 hunger and each block broken another 0.08. You can do only one manual job at a time: see "One pair of hands" in [Building Crafting and Workstations](Building-Crafting-and-Workstations). Soil, sand, gravel, clay and wood are not pickaxe blocks and break in the vanilla way, slowed by injury.

## What a block yields

One item of a metal or mineral is one kilogram. One item of coal is a 25 kg sack. A broken block gives what a cubic metre of that ground holds.

| Block | Drops | Weight |
| --- | --- | --- |
| Haematite ore | 1,600 haematite | 1,600 kg |
| Malachite ore | 420 malachite | 420 kg |
| Cassiterite ore | 60 cassiterite | 60 kg |
| Galena ore | 250 galena | 250 kg |
| Pyrite ore | 300 pyrite | 300 kg |
| Rock salt | 2,100 salt | 2,100 kg |
| Fluorite ore | 600 fluorite | 600 kg |
| Bauxite ore | 2,400 bauxite | 2,400 kg |
| Wolframite ore | 30 wolframite | 30 kg |
| Tar sand | 2,000 oil sand | 2,000 kg |
| Borax ore | 1,500 borax | 1,500 kg |
| Coal ore | 52 coal | 52 sacks, 1,300 kg |
| Clay | 480 clay balls | 1,800 kg |
| Obsidian | 3 obsidian flakes | |
| Limestone | 1 limestone block | 2,600 kg |

These amounts are fixed by the loot tables and can be changed by a data pack. The vanilla recipe that presses clay balls back into a clay block is disabled.

What the hand processes then make of it:

| Mineral | Route | Metal per kilogram of mineral |
| --- | --- | --- |
| Malachite | Crush 48, roast 32, smelt 32 with 1 sack of charcoal for 16 copper | 0.5 kg copper |
| Cassiterite | Crush 48, roast 32, smelt 32 with 1 sack of charcoal for 24 tin | 0.75 kg tin |
| Haematite | Crush 48, roast 32, smelt 48 with 2 sacks of charcoal for 12 bloom, then work 4 blooms into 3 iron | about 0.19 kg iron |
| Galena | Smelt 32 with 1 sack of charcoal for 24 lead | 0.75 kg lead |

One block of malachite ore is 210 kg of copper. One block of haematite ore is about 300 kg of bar iron and more than thirty bloomery charges. The step-by-step recipes are in [Building Crafting and Workstations](Building-Crafting-and-Workstations), and the bulk routes that handle tonnes are in [Industrial Manufacturing](Industrial-Manufacturing).

## Weight and spoil heaps

What you carry slows you. Up to 35 kg costs nothing. From 35 to 120 kg your pace falls steadily to one tenth, and stays there. Two stacks of ore are 128 kg. A single block of haematite ore is 25 stacks. Bring a cart to the face: see [Transport and Logistics](Transport-and-Logistics) and [Units and Realism Rules](Units-and-Realism-Rules).

The heap can wait while you fetch one.

- Broken ore, crushed and roasted ore, coal, charcoal, coke, clay, brick, lime, limestone, sulfur and pig iron do not weather at all. A heap lies where it fell until it is carted away. What a block breaks down to falls as one heap of each kind: it shows a single stack and is labelled with the whole count, for example "1,600 Hematite". Walk onto it to take the stack that shows; the next is uncovered at once, so a full heap is taken up a stack every half second for as long as you have room. Loose stacks of the same thing lying against a heap join it.
- No dropped item is removed on a timer. What removes other things left in the open is weathering and rot.
- Other loose material is ruined after 4 game days exposed, and what is left is a few scraps of plant fibre. Items whose names mark them as stone, ingots, copper, bronze, iron, gold, diamond, pottery, scrap or obsidian last 28 days and end as metal scrap.
- Plant fibre left out rots away to nothing after another 4 days exposed, and so does spoiled food, 4 days after it spoils. Metal scrap and ceramic shards stay where they lie.
- Rain doubles the rate of weathering and the hottest biomes multiply it by 1.5. Both rates are set in the `decay` rules and can be changed by a data pack.
- Weathering stops while an item is in an inventory, a container or a vehicle.

Plan to clear a heap within a day, and never leave ore on the ground for more than four.

## Natural and placed blocks

The world remembers every position from which natural rock or ore has been taken, and every position where a player has placed such a block.

| Case | Drops | Mining experience |
| --- | --- | --- |
| A natural block mined for the first time | Yes | Yes |
| A block a player placed, mined again | Yes | No |
| Any block at a position already mined once | Yes | No |

This covers stone and the other base rocks, limestone, sandstone, red sandstone, calcite, obsidian and every ore block. Placing a block and digging it up again teaches nothing. Drops are never affected.

## World rules

New terrain generates as wild country.

- **No structures.** No villages, temples, mineshafts, strongholds, ruined portals, dungeons or desert wells generate. Caves, trees, animals and the resources on this page remain. Chunks generated before the mod was installed keep whatever they had.
- **No fantasy creatures.** Entities on the forbidden list are removed as they spawn or load. The list holds the undead, creepers, spiders, slimes, endermen, witches, evokers, ravagers, golems, nether and end creatures, wardens, phantoms and the like, and also mooshrooms, sniffers and allays.
- **No other dimensions.** Portals do not form and travel to the Nether or the End is cancelled.
- **No enchantments.** Enchantments are stripped from every item, including items inside containers.
- **Prohibited items vanish.** They are deleted from inventories, containers, drops and trades. The list includes ender pearls and eyes, blaze rods, ghast tears, nether stars, totems, elytra, experience bottles, enchanted books, enchanting tables, brewing stands, beacons, the main redstone components (dust, blocks, torches, repeaters, comparators, observers, pistons, dispensers, droppers, daylight detectors, targets, lamps, powered, detector and activator rails, crafters, sculk sensors), anything netherite, shulker, chorus, purpur or end stone, copper bulbs, spawn eggs, command blocks, golden apples, glowstone, soul sand, nether wart, the mace, and every potion except a plain water bottle. Splash potions, lingering potions and tipped arrows are prohibited.
- **Furnaces do nothing.** A furnace, blast furnace or smoker does no work even when fed by a hopper. Right-clicking a furnace, blast furnace or smoker is cancelled, and the vanilla smelting recipes for copper and iron and the furnace, smoker and campfire recipes for meat are disabled. Heat work is done at the kiln, bloomery and forge.
- **Merchants cannot sell finished work.** Any trade whose result needs a workstation, or which involves a prohibited item, is removed.

Server owners can extend the lists with the data tags `spawn_9b5fbb9:forbidden_entities` and `spawn_9b5fbb9:forbidden_items`.

## The tech tree's deposits

The tech tree adds 22 kinds of deposit for minerals this geology does not place, from fireclay and native copper to cinnabar, platinum, oil and gas. Some yield only rubble until your society knows the right theory, and some can only be worked from a mine or a drilling rig. See [Tech Tree Stations](Tech-Tree-Stations#deposits).

## Legacy worlds

A world without the Tectonic profile uses the same ore bodies with simpler rules.

| | Legacy |
| --- | --- |
| District mineral | 20% barren, 28% copper, 24% iron, 12% tin, 16% coal, with no provinces |
| Depth | Fixed heights: copper Y 36 to 68, tin Y 20 to 52, iron Y 28 to 60, coal Y 44 to 76 |
| Copper host | Always limestone |
| Quarry rock | Granite, limestone or sandstone at random |

Old chunks may still hold the earlier `tin_ore` block. It now yields 60 kg of cassiterite like any tin lode.

## Commands

| Command | What it does |
| --- | --- |
| `/survival resources [radius]` | Lists visible resources within 1 to 16 blocks (default 12) |
| `/survival prospect` | Identifies the mineral sample in your main hand |
| `/survival prospect <x> <y> <z>` | Identifies an exposed block within 4 blocks |
| `/survival gather <x> <y> <z>` | Starts gathering a loose stone, stick or wild plant block. `/survival forage` is the same command |
| `/survival mine <x> <y> <z>` | Starts a timed mining job on a block within 4 blocks |
| `/survival jobs` | Shows your current job and its progress |
| `/survival cancel` | Stops the current job |
| `/survival environmentSurvey <x> <z>` | Operators only. Reveals a district's mineral and deposit position |

Operators can issue the same actions for another humanoid as `/survival control <actor> ...`.

## Common mistakes

- **Digging at random.** Seven districts in ten of a plain stone province are barren, and three in ten everywhere else. Identify the province from its quarry rock first.
- **Looking for copper under sandstone.** Only one district in ten of a coal basin holds copper, and none holds tin. Go to limestone for copper and granite for tin.
- **Digging too shallow.** The top of a copper or iron body is usually more than 20 blocks down, and a tin body more than 30.
- **Giving up on a barren layer.** Iron and coal are banded. A barren layer of host rock has ore one or two blocks above or below it.
- **Carrying the ore home.** A block of haematite weighs 1,600 kg. Above 120 kg you move at a tenth of your pace.
- **Leaving heaps.** Ore on the ground is safe for a day and ruined after four days exposed, sooner in rain.
- **Mining without a pickaxe of stone grade.** The job is refused. Knap one first.
- **Placing and re-mining stone to train.** Placed and previously mined positions give no experience.
- **Expecting `/survival resources` to find buried ore.** It lists only what you can see.
