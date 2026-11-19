---
title: Building Crafting and Workstations
---
{% include nav.html %}

# Building Crafting and Workstations

Nothing in this mod is made in an instant. Gathering, mining, farming, crafting in the grid and work at a station all take real working time from a body that can do one manual thing at a time, and the time depends on that body's skill and condition. Finished metal goods, pottery and textiles cannot be made in the crafting grid at all: they come from five workstations, the kiln, bloomery, forge, anvil and loom, which take real inputs, real fuel and real hours. Placing blocks is the exception. Building stays as free as in vanilla. This page explains how work is done, how each station is fuelled and run, and lists every station recipe.

## Building is free

- Placing a block is immediate. There is no timed job for placing ordinary blocks, and the look of a building is entirely yours.
- Building blocks carried in an inventory weigh nothing, although the same blocks weigh their real mass as cargo on a vehicle. See [Units and Realism Rules](Units-and-Realism-Rules).
- What a building does is measured in a few places only. A roof keeps rain off bodies and soil, and walls cut wind: see [Weather Climate and Geography](Weather-Climate-and-Geography). The large works check their surroundings for roofed floor, chimneys, firebrick, water and equipment: see [Industrial Manufacturing](Industrial-Manufacturing) for those site rules.
- Railway track is laid as a timed job with real materials, not placed: see [Railways](Railways).

Players in creative or spectator mode are exempt from all work time.

## One pair of hands

Manual work comes in three kinds, and a body can do only one at a time.

| Kind | Examples |
| --- | --- |
| A world job | Gathering, mining, tilling, sowing, harvesting, laying track, hunting, crafting by command |
| Grid crafting | A recipe waiting in the inventory grid or a crafting table |
| A tended station job | Pottery, forge, anvil and loom work that you started |

The rules that keep it to one:

- A new world job is refused while you have another world job, a grid recipe in progress, or a tended station job within reach.
- Grid crafting makes no progress while a world job is running or a tended station job is yours.
- A tended station recipe will not start while you are busy with either of the others.
- A tended station job is yours for as long as you are within 6 blocks of it, it still has unfinished input, and it has heat if it needs heat. Walk more than 6 blocks away and the work pauses and your hands are free. Come back and it resumes.
- Unattended heating is separate. A kiln can roast ore and a bloomery can smelt while you do something else.

Work is done only by a body that is present. Nothing manual advances while you are logged out, and sleeping through the night does no work for you.

## Work rate

Every manual job is rated in base work ticks. There are 1,000 ticks in a game hour and 20 in a real second.

**Work per tick = the lower of manipulation and mental capacity x (50% + 2.5% per level of the job's skill).**

| Skill level | Rate at full capacity | A 1,000-tick job takes |
| --- | --- | --- |
| 0 | 50% | 2,000 ticks |
| 10 | 75% | 1,333 ticks |
| 20 | 100% | 1,000 ticks |

- A job with no skill attached runs at capacity alone.
- An injured hand, hunger, thirst, illness or a missed recreation quota lower capacity and slow every job. At 5% capacity or less, work stops until you recover. See [Survival and Physiology](Survival-and-Physiology).
- Each tick of work costs 0.0005 hunger, half a point for every game hour worked.
- Experience is earned only when a job is completed, for the ticks actually worked. Cancelled work teaches nothing. See [Skills and Learning](Skills-and-Learning).

## Timed world jobs

| Job | Base work ticks | Skill | Started by |
| --- | --- | --- | --- |
| Gather | 40 | Foraging | Clicking a loose stone, stick or wild plant block |
| Harvest | 60 | Farming or Foraging | Left-clicking a crop, clicking a berry bush or fruit tree |
| Till | 100 | Farming | Right-clicking soil with a hoe |
| Sow | 60 | Farming | Right-clicking with a seed |
| Irrigate | 60 | Farming | Right-clicking farmland with an open pot of water |
| Mine | hardness x 30 / tool speed, at least 30 | Mining | Left-clicking any block a pickaxe mines |
| Craft | 100 | By product | `/survival craft <recipe>` |
| Lay track | 2,500 | Construction | Using a steel rail on the ground |
| Hunt | 20 per strike | Hunting | `/survival hunt <animal>` |

Rules common to all of them:

- You must stay within 4 blocks of the target. Leaving, dying, changing dimension, a change to the target block, or swapping or damaging the tool in hand cancels the job.
- Seeds, water pots and crafting ingredients are taken out of your inventory when the job starts and returned if it is cancelled.
- `/survival jobs` shows the job and its progress. `/survival cancel` stops it.
- Blocks that a pickaxe mines, crops, wild plants and fruit trees cannot be broken by holding the mouse button. The first click starts the job.

The field jobs are described in [Agriculture and Livestock](Agriculture-and-Livestock), mining in [Natural Resources and Mining](Natural-Resources-and-Mining) and track laying in [Railways](Railways).

## Grid crafting

The crafting grid still works for simple things, with two changes.

**It takes time.** Each craft needs 100 work ticks. Put the ingredients in place and leave them there: work is added once a second at your work rate, and the result cannot be taken out until the 100 are done. Changing the recipe starts the count again. Unskilled products take 5 seconds at full capacity. Products with a skill take 10 seconds at level 0 and 5 at level 20.

| Product | Skill that sets the rate and earns experience |
| --- | --- |
| Any food | Cooking |
| Cordage, plant fibre, dried fibre | Weaving |
| Workstations, works, wooden toys, planks, stairs, slabs, fences, gates, doors, trapdoors, signs, boats | Construction |
| Anything else | none |

**It cannot make specialist goods.** These can never be taken from a crafting grid or bought from a merchant. They are made only at a station:

- Fired and unfired pots.
- Metal rivets and every forged blank.
- Yarn, wool cloth, fibre cloth and every garment.
- Crossbow stocks.
- Every metal tool, weapon and piece of armour: copper, bronze, iron, gold, diamond and chainmail.
- Shields, crossbows, shears, buckets, flint and steel, spyglasses and brushes.

The vanilla recipes for wooden, stone, copper, iron, golden and diamond tools, swords and spears, for metal armour, for shields, shears, buckets, crossbows, flint and steel, brushes and spyglasses, for furnace, smoker and campfire cooking of meat, for furnace smelting of copper and iron, and for pressing clay balls into a clay block are disabled.

The hand recipes that start a settlement:

| Product | Ingredients | Table needed |
| --- | --- | --- |
| 2 stone edges | 2 loose stone | no |
| 2 cordage | 2 plant fibre | no |
| 1 plant fibre | 1 flax fibre | no |
| Campfire | 3 sticks, 1 loose stone, 1 plant fibre | yes |
| Knapped stone pickaxe | 3 stone edges, 1 cordage, 2 sticks | yes |
| Knapped stone axe | 3 stone edges, 1 cordage, 1 stick | yes |
| Knapped stone hoe | 2 stone edges, 1 cordage, 2 sticks | yes |
| Spear | 1 stone edge, 1 cordage, 2 sticks | yes |
| Obsidian pickaxe, axe, hoe | As the knapped tools, with obsidian flakes | yes |
| Pot lid | 1 clay ball, 1 loose stone | no |
| Wooden toy | 2 sticks, 1 cordage | no |
| Brush bundle | 1 stick, 1 feather, 1 cordage | no |
| 2 bandages | 1 clean cloth, 1 cordage | no |

`/survival craft <recipe>` does a grid recipe as a world job. It takes the ingredients from your inventory, from containers within 3 blocks and from items lying within 3 blocks, needs a crafting table within 4 blocks for anything larger than two by two, and takes the same 100 base ticks. The recipe id is typed in full, for example `spawn_9b5fbb9:twist_cordage`; an id without a namespace is taken as a vanilla recipe.

## The five workstations

| Station | Crafted from | Fuel | Worker | Used for |
| --- | --- | --- | --- | --- |
| Kiln | 3 loose stone, 2 clay balls | Yes | Unattended, except pottery | Firing pots and bricks, roasting ore, charring wood, cooking, drying, rendering tallow, burning ash |
| Bloomery | 7 loose stone, 2 clay balls | Yes | Unattended | Smelting roasted ore and galena with charcoal |
| Forge | 2 brick blocks, 2 loose stone | Yes | Tended | Alloying bronze, working blooms, forging blanks, plate, shot and solder |
| Anvil | 6 loose stone, 2 clay balls | No | Tended | Crushing ore, finishing blanks into tools, arms and armour, dressing stone shot |
| Loom | 4 planks, 2 cordage | No | Tended | Preparing fibre, spinning, weaving, sewing, crossbow stocks |

All but the forge need a crafting table to build, because they have more than four ingredients. Building one earns Construction experience. Each station is a single block and holds one job at a time: one input, one second input, one output.

The kiln, bloomery, forge and loom are also the tech tree's stations of those names. Work the tree does at one of them is done by hand from the journal (**J**) while you stand within 16 blocks of the workstation. See [Tech Tree and Discovery](Tech-Tree-and-Discovery).

## Fuel

The kiln, bloomery and forge burn a heat reserve measured in ticks.

| Fuel item | Heat | Where it can be burned |
| --- | --- | --- |
| Charcoal, coal or coke (one item is a 25 kg sack) | 8,000 ticks | Kiln, bloomery, forge |
| Stick | 80 ticks | Kiln, bloomery, forge |
| Log (one item is a cubic metre) | 96,000 ticks | Kiln only |

- The reserve holds at most 128,000 ticks. A full station refuses more.
- Each click, or each `/survival fuel`, burns one item.
- Raw wood cannot reach working heat in a bloomery or forge. Burn charcoal there.
- A heated recipe will not start unless the reserve is at least the recipe's ticks.
- An unattended job burns one tick of heat for each tick of progress.
- A tended job burns one tick of heat for each real tick you work on it, so a slow worker burns more. A 4,000-tick forge job done at a 50% rate burns 8,000 ticks of heat, a whole sack. A job is refused until the fire holds enough for the worker starting it, and the refusal says how much that is. A job left by a worker who died stays in the station as it stood, and the next person able to do it takes it up by starting the same process there.
- If the heat runs out the job pauses until the station is stoked again.
- Unused heat stays in the station for the next job. Heat already burned is not returned when a job is cancelled.

The anvil and loom need no fuel.

## Running a station

### What a right-click does

A right-click with the main hand is read in this order.

1. Holding charcoal, coal, coke or a stick at a kiln, bloomery or forge: stokes the fire. A log also stokes, unless you are sneaking.
2. Empty hand, and a finished product is waiting: collects it.
3. Sneaking, and a job is in progress: cancels it.
4. A job is in progress: shows its progress and the heat left.
5. Otherwise: starts the first recipe that fits the item in your main hand and, where the recipe has a second input, the item in your off hand.

### Fuel

1. Hold the fuel in your main hand.
2. Right-click the station, or stand within 6 blocks and use `/survival fuel`.
3. Repeat until the reserve covers the recipe.

### Start

1. Hold the whole input in your main hand. A recipe that takes 48 needs a stack of at least 48.
2. Hold the second input in your off hand, if the recipe has one. For smelting this is the charcoal that reduces the ore, and it is separate from the fuel in the fire.
3. Right-click the station, or use `/survival produce <recipe>` within 6 blocks of the right kind of station.

The start is refused, with the reason, if the station is worn out, already holds a job or an uncollected product, is the wrong kind, the input or second input is missing or short, your skill is below the gate, your manipulation is below the recipe's minimum, the heat is short, or your hands are busy.

When several recipes take the same item, a right-click picks the first one. A copper ingot at a forge makes rivets unless a tin ingot is in your off hand, in which case it makes bronze. Use `/survival produce <recipe>` to choose exactly. `/survival recipes` lists every recipe name.

Three cases need care:

- **Charring wood and burning ash.** A log in the hand stokes the kiln. To start `charcoal` or `burn_ash` by hand, sneak while you click an empty kiln, or use the command.
- **Ash from charcoal.** Charcoal in the hand always stokes. `ash_from_charcoal` can be started only with `/survival produce ash_from_charcoal`.
- **Boiled water.** `sterilize_cloth` takes one portion of boiled water from an open pot in your off hand.

### Tend

Unattended recipes need nothing more. They advance with the calendar, even while the area is unloaded, and catch up when it loads again as far as the heat allows.

Tended recipes advance only while the worker who started them is alive, within 6 blocks, capable, and not doing other manual work. Progress is added once a second at the worker's rate. You do not need to keep clicking. Pottery is a tended kiln job, and the quality of the pot follows the potter's effective skill over the whole firing: see [Water Pottery and Food](Water-Pottery-and-Food).

Right-click the station, or use `/survival station`, to see progress.

### Collect

Right-click with an empty main hand, or use `/survival collect` within 6 blocks. The station can take no new job until its product is collected.

### Cancel

Sneak and right-click the station, or use `/survival cancel`. The unfinished input and the second input come back in full. The progress and the heat burned are lost, and no experience is earned. Water already poured for `sterilize_cloth` is not returned.

Breaking a station drops its input, second input and product.

## Wear

Every completed job adds one wear to the station. At 256 it is worn out and refuses to start anything. Wear stays with the block when it is broken and placed again, so moving a station does not renew it. Build a new one.

## Station recipes

Times are base ticks. Unattended recipes take exactly that long. Tended recipes take that long divided by the worker's rate. "Any level" means the skill sets the rate and earns experience but there is no minimum.

### Kiln

| Recipe | Input (main hand) | Second input (off hand) | Output | Ticks | Station | Skill gate |
| --- | --- | --- | --- | --- | --- | --- |
| `fire_pot` | 8 clay balls | none | 1 fired pot | 2,000 | Kiln, tended | Pottery, any level |
| `fire_brick` | 64 clay balls | none | 60 bricks | 24,000 | Kiln | none |
| `roast_copper` | 32 crushed copper | none | 32 roasted copper | 2,000 | Kiln | none |
| `roast_tin` | 32 crushed tin | none | 32 roasted tin | 2,000 | Kiln | none |
| `roast_iron` | 32 crushed iron | none | 32 roasted iron | 2,000 | Kiln | none |
| `charcoal`, `charcoal_<wood>` | 4 logs of one kind | none | 8 charcoal | 48,000 | Kiln, no fuel needed | none |
| `dry_fiber` | 3 plant fibre | none | 3 dried fibre | 1,000 | Kiln | none |
| `sterilize_cloth` | 1 fibre cloth | 1 portion of boiled water | 2 clean cloth | 1,000 | Kiln | none |
| `cook_beef`, `cook_porkchop`, `cook_chicken`, `cook_mutton`, `cook_rabbit`, `cook_cod`, `cook_salmon` | 1 raw meat or fish | none | 1 cooked | 1,000 | Kiln | none |
| `render_tallow_porkchop`, `render_tallow_mutton`, `render_tallow_beef` | 4 raw meat | none | 1 lubricant (tallow) | 1,000 | Kiln | none |
| `burn_ash` | 1 birch log | none | 3 wood ash | 1,000 | Kiln | none |
| `ash_from_charcoal` | 2 charcoal | none | 1 wood ash | 600 | Kiln | none |
| `iron_rivets` | 1 iron ingot | none | 4 metal rivets | 800 | Forge | Smithing, any level |

Notes:

- Any kind of log chars (`charcoal` for oak, `charcoal_spruce` and so on) and any kind burns to ash (`burn_ash` for birch, `burn_ash_oak` and so on). Four cubic metres of wood char for two game days on their own charge and give eight sacks.
- `fire_brick` is the small kiln's load: a stack of clay lumps fired for a day and a night. Bulk brick, lime, firebrick and glass are not kiln recipes. They are made in clamps at the assembly yard and in the foundry: see [Industrial Manufacturing](Industrial-Manufacturing).
- `fire_pot` takes eight clay balls, thirty kilograms of clay, and needs 25% manipulation.

### Bloomery

| Recipe | Input (main hand) | Second input (off hand) | Output | Ticks | Station | Skill gate |
| --- | --- | --- | --- | --- | --- | --- |
| `smelt_copper` | 32 roasted copper | 1 charcoal | 16 copper ingots | 6,000 | Bloomery | none |
| `smelt_tin` | 32 roasted tin | 1 charcoal | 24 tin ingots | 6,000 | Bloomery | none |
| `smelt_iron` | 48 roasted iron | 2 charcoal | 12 iron blooms | 8,000 | Bloomery | none |
| `smelt_galena` | 32 galena | 1 charcoal | 24 lead ingots | 3,000 | Bloomery | none |

The charcoal in the off hand is the reductant and must be charcoal. It is used up in addition to the heat. A full iron charge is therefore 48 kg of roasted ore, two sacks of charcoal in the charge and one sack on the fire, for 12 kg of bloom.

### Forge

Every forge recipe is tended and heated, and needs 15% manipulation unless noted.

| Recipe | Input (main hand) | Second input (off hand) | Output | Ticks | Station | Skill gate |
| --- | --- | --- | --- | --- | --- | --- |
| `bronze` | 9 copper ingots | 1 tin ingot | 10 bronze ingots | 4,000 | Forge | Smithing, any level |
| `work_bloom` | 4 iron blooms | none | 3 iron ingots | 3,000 | Forge | Smithing, any level |
| `copper_rivets` | 1 copper ingot | none | 4 metal rivets | 800 | Forge | Smithing, any level |
| `forge_<metal>_pickaxe_blank` | 3 ingots | none | 1 pickaxe blank | 1,000 | Forge | Smithing, any level |
| `forge_<metal>_axe_blank` | 3 ingots | none | 1 axe blank | 1,000 | Forge | Smithing, any level |
| `forge_<metal>_hoe_blank` | 2 ingots | none | 1 hoe blank | 1,000 | Forge | Smithing, any level |
| `forge_<metal>_sword_blank` | 2 ingots | none | 1 sword blank | 1,000 | Forge | Smithing, any level |
| `forge_<metal>_shovel_blank` | 1 ingot | none | 1 shovel blank | 1,000 | Forge | Smithing, any level |
| `forge_<metal>_helmet_blank` | 5 ingots | none | 1 helmet blank | 1,600 | Forge | Smithing, any level |
| `forge_<metal>_chestplate_blank` | 8 ingots | none | 1 chestplate blank | 1,600 | Forge | Smithing, any level |
| `forge_<metal>_leggings_blank` | 7 ingots | none | 1 leggings blank | 1,600 | Forge | Smithing, any level |
| `forge_<metal>_boots_blank` | 4 ingots | none | 1 boots blank | 1,600 | Forge | Smithing, any level |
| `forge_copper_spear_blank`, `forge_iron_spear_blank` | 3 copper or iron ingots | none | 1 spear blank | 1,200 | Forge | Smithing, any level |
| `forge_shield_blank` | 1 iron ingot | none | 1 shield blank | 1,000 | Forge | Smithing, any level |
| `forge_crossbow_blank` | 1 iron ingot | none | 1 crossbow blank | 1,200 | Forge | Smithing, any level |
| `forge_shears_blank` | 2 iron ingots | none | 1 shears blank | 1,000 | Forge | Smithing, any level |
| `forge_bucket_blank` | 3 iron ingots | none | 1 bucket blank | 1,000 | Forge | Smithing, any level |
| `forge_flint_and_steel_blank` | 1 iron ingot | none | 1 flint and steel blank | 1,000 | Forge | Smithing, any level |
| `forge_spyglass_blank` | 2 copper ingots | none | 1 spyglass blank | 1,000 | Forge | Smithing, any level |
| `forge_brush_blank` | 1 copper ingot | none | 1 brush blank | 1,000 | Forge | Smithing, any level |
| `forge_musket_barrel_blank` | 3 iron ingots | none | 1 musket barrel blank | 1,600 | Forge | Smithing, any level |
| `forge_musket_lock_blank` | 1 iron ingot | none | 1 musket lock blank | 1,000 | Forge | Smithing, any level |
| `forge_machine_parts_blank` | 2 iron ingots | none | 1 machine parts blank | 1,600 | Forge | Smithing, any level |
| `forge_iron_plate` | 20 iron ingots | none | 19 iron plate | 1,600 | Forge | Smithing 8, 20% manipulation |
| `forge_round_shot` | 3 iron ingots | none | 1 round shot | 1,200 | Forge | Smithing 4 |
| `cast_musket_balls` | 1 lead ingot | none | 32 musket balls | 600 | Forge | Smithing, any level, 10% manipulation |
| `solder` | 3 tin ingots | 2 lead ingots | 5 solder | 800 | Forge | Smithing 2, 10% manipulation |

`<metal>` is `copper` (copper ingots), `bronze` (bronze ingots) or `forged_iron` (iron ingots). Blooms are worked four at a time, and a quarter of their weight is lost as slag.

### Anvil

Every anvil recipe is tended, needs no fuel, and needs 15% manipulation unless noted.

| Recipe | Input (main hand) | Second input (off hand) | Output | Ticks | Station | Skill gate |
| --- | --- | --- | --- | --- | --- | --- |
| `crush_malachite` | 48 malachite | none | 48 crushed copper | 1,000 | Anvil | Mining, any level, 10% manipulation |
| `crush_cassiterite` | 48 cassiterite | none | 48 crushed tin | 1,000 | Anvil | Mining, any level, 10% manipulation |
| `crush_hematite` | 48 haematite | none | 48 crushed iron | 1,000 | Anvil | Mining, any level, 10% manipulation |
| `<metal>_pickaxe`, `<metal>_axe`, `<metal>_hoe`, `<metal>_sword`, `<metal>_shovel` | 1 matching blank | 1 cordage | The tool | 1,500 | Anvil | Smithing 4 for copper, 8 for bronze, 12 for forged iron |
| `<metal>_helmet`, `<metal>_chestplate`, `<metal>_leggings`, `<metal>_boots` | 1 matching blank | 2 metal rivets | The armour piece | 2,000 | Anvil | Smithing 12 for copper and bronze, 14 for forged iron |
| `forge_copper_spear` | 1 copper spear blank | 1 cordage | 1 copper spear | 1,600 | Anvil | Smithing 4 |
| `forge_iron_spear` | 1 iron spear blank | 1 cordage | 1 iron spear | 1,600 | Anvil | Smithing 12 |
| `shield`, `shield_with_<wood>_planks` | 1 shield blank | 6 planks | 1 shield | 1,600 | Anvil | Smithing 12 |
| `crossbow` | 1 crossbow blank | 1 crossbow stock | 1 crossbow | 2,000 | Anvil | Smithing 12 |
| `shears` | 1 shears blank | 1 metal rivet | 1 shears | 1,400 | Anvil | Smithing 12 |
| `bucket` | 1 bucket blank | 2 metal rivets | 1 bucket | 1,400 | Anvil | Smithing 12 |
| `flint_and_steel` | 1 flint and steel blank | 1 flint | 1 flint and steel | 1,400 | Anvil | Smithing 12 |
| `spyglass` | 1 spyglass blank | 1 amethyst shard | 1 spyglass | 1,400 | Anvil | Smithing 4 |
| `brush` | 1 brush blank | 1 brush bundle | 1 brush | 1,400 | Anvil | Smithing 4 |
| `musket_barrel` | 1 musket barrel blank | none | 1 musket barrel | 2,400 | Anvil | Smithing 14, 20% manipulation |
| `musket_lock` | 1 musket lock blank | 1 flint | 1 musket lock | 2,000 | Anvil | Smithing 14, 20% manipulation |
| `machine_parts` | 1 machine parts blank | 2 metal rivets | 1 machine parts | 3,000 | Anvil | Smithing 14, 20% manipulation |
| `dress_stone_shot` | 1 cobblestone | none | 8 stone shot | 32,000 | Anvil | Mining 2 |

The plain `shield` recipe takes oak planks. The others are named for their wood: acacia, bamboo, birch, cherry, crimson, dark oak, jungle, mangrove, pale oak, poplar, spruce and warped.

### Loom

Every loom recipe is tended, needs no fuel, and needs 10% manipulation.

| Recipe | Input (main hand) | Second input (off hand) | Output | Ticks | Station | Skill gate |
| --- | --- | --- | --- | --- | --- | --- |
| `prepare_flax_fiber` | 1 flax fibre | none | 1 plant fibre | 400 | Loom | Weaving, any level |
| `prepare_yarrow_leaves` | 1 yarrow leaves | none | 1 medicinal leaves | 400 | Loom | Medicine, any level |
| `spin_wool`, `spin_<colour>_wool` | 1 wool of any of the 16 colours | none | 4 yarn | 800 | Loom | Weaving, any level |
| `weave_wool_cloth` | 4 yarn | none | 1 wool cloth | 1,000 | Loom | Weaving, any level |
| `weave_fiber_cloth` | 3 cordage | none | 1 fibre cloth | 1,000 | Loom | Weaving, any level |
| `weave_fiber_shirt`, `weave_wool_shirt` | 4 cloth | 1 cordage | 1 shirt | 1,000 | Loom | Weaving, any level |
| `weave_fiber_pants`, `weave_wool_pants` | 4 cloth | 1 cordage | 1 pants | 1,000 | Loom | Weaving 2 |
| `weave_fiber_jacket`, `weave_wool_jacket` | 6 cloth | 1 cordage | 1 jacket | 1,600 | Loom | Weaving 4 |
| `weave_fiber_shoes`, `weave_wool_shoes` | 2 cloth | 1 cordage | 1 shoes | 1,000 | Loom | Weaving, any level |
| `crossbow_stock`, `crossbow_stock_from_<wood>_planks` | 3 planks | 2 cordage | 1 crossbow stock | 1,200 | Loom | Construction 2 |

`spin_wool` takes white wool. The plain `crossbow_stock` takes oak planks.

## From ore to a tool

A copper pickaxe, starting from malachite on the ground:

1. **Anvil.** Crush 48 malachite to 48 crushed copper (1,000 ticks, tended).
2. **Kiln.** Stoke with 2,000 ticks of heat and roast 32 crushed copper (2,000 ticks, unattended).
3. **Bloomery.** Stoke with a sack of charcoal. With 32 roasted copper in the main hand and 1 charcoal in the off hand, smelt for 16 copper ingots (6,000 ticks, unattended).
4. **Forge.** Stoke, then forge 3 ingots into a pickaxe blank (1,000 ticks, tended).
5. **Anvil.** With the blank in the main hand and 1 cordage in the off hand, finish the pickaxe (1,500 ticks, tended, Smithing 4).

A new smith reaches level 4 on work that has no gate: rivets, blanks, bronze and blooms. Iron follows the same road with two differences. The bloomery takes 48 roasted ore and 2 charcoal for 12 blooms, and the blooms must then be worked at the forge, four at a time, into 3 iron ingots.

## Tuning

Station recipes are defaults from the `production` group of `data/spawn_9b5fbb9/survival/content_rules.json`. A data pack can change them or add new ones, and `/reload` applies the change.

| Field | Meaning | Limits |
| --- | --- | --- |
| `input`, `count` | Main-hand item and amount | Up to one stack |
| `second`, `secondCount` | Off-hand item and amount. `@safe_water` means boiled water | Up to one stack, or 4 portions of water |
| `output`, `amount` | Product and amount | Up to one stack. Prohibited items are refused |
| `ticks` | Base work ticks | 1 to 64,000 |
| `station` | `kiln`, `bloomery`, `forge`, `anvil` or `loom` | |
| `skill`, `minimumSkill` | Skill and gate | 0 to 20 |
| `manual`, `heated` | Tended, and needs heat | Anvil and loom recipes must be tended and unheated |
| `minimumManipulation` | Share of hand function needed | 0.05 to 1 |
| `practice` | Weight of the experience earned | 0 to 2,500 |

Some contracts cannot be tuned away. Finished metal equipment must remain a smith's anvil job from its matching blank at no less than its default skill gate. Blanks must be forged by a smith at a heated forge. Bindings and rivets cannot be dropped from a finishing recipe. Textiles must be woven at a loom. A pot must be fired from a clay block by a potter at a tended, fuelled kiln with at least 25% manipulation.

## Commands

| Command | What it does |
| --- | --- |
| `/survival recipes` | Lists the names of all station recipes |
| `/survival produce <recipe>` | Starts a recipe at the nearest station of the right kind within 6 blocks, with the input in your main hand and the second input in your off hand |
| `/survival fuel` | Burns one held fuel item in the nearest kiln, bloomery or forge within 6 blocks |
| `/survival station` | Reports the nearest station: position, recipe, progress, heat and product |
| `/survival collect` | Collects from the nearest station with a finished product |
| `/survival cancel` | Stops your world job. With no world job, cancels the nearest station job that is yours or unattended and returns its inputs |
| `/survival jobs` | Shows your world job and its progress |
| `/survival recipeinfo <recipe> [alternativeOffset]` | Shows one recipe: station, inputs, skill gate, base ticks and the time at your present skill and capacity |
| `/survival planning [offset] [cropOffset]` | Pages through every grid and station recipe with the same detail |
| `/survival craft <recipe>` | Does a grid recipe as a timed world job |
| `/survival gather`, `harvest`, `mine`, `till`, `sow`, `irrigate`, `lay_track`, each followed by `<x> <y> <z>` | Starts that world job on a block within 4 blocks |

Operators can issue the same actions for another humanoid as `/survival control <actor> ...`.

## Common mistakes

- **Taking the result too soon.** A grid craft needs 100 work ticks with the ingredients left in place. Moving them starts it again.
- **Looking for a pickaxe recipe.** Metal tools are not grid recipes. Forge the blank, then finish it on the anvil.
- **No charcoal in the off hand.** A bloomery will not start a smelt on fuel alone. The charge needs its own charcoal.
- **Logs in the forge.** Raw wood heats only a kiln. Char it first: 4 logs give 8 sacks.
- **Stoking when you meant to char.** A log in the hand feeds the fire. Sneak to load logs as a charge.
- **Too little in the hand.** The whole input must be in one stack in the main hand: 48 ore to crush, 32 to roast, 64 clay balls for bricks.
- **Walking away from the forge.** Tended work stops beyond 6 blocks. The kiln and bloomery carry on without you.
- **Starving the fire.** A slow or injured smith burns more heat per job. Stoke with a margin.
- **Doing two things.** Mining or gathering while a forge job is yours is refused. Finish, cancel or step away first.
- **Leaving the product in the station.** Nothing new starts until it is collected with an empty hand.
- **Expecting an old station to last.** After 256 jobs it is finished, wherever you move it.
- **Building for looks and expecting function.** Only roofs, walls and the site features of the works are measured. Everything else is yours to design.
