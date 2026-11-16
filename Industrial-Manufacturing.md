---
title: Industrial Manufacturing
---
{% include nav.html %}

# Industrial Manufacturing

Industry begins where the five hand workstations stop. Nine kinds of **works** carry production from the gunpowder age to the electronic one. A works is a block that holds a ledger of delivered materials and turns them, batch by batch, into its selected product, provided every input is present, qualified hands are on the job, and the plant that process really needs has been built around it. What the building looks like is up to you. What the process needs is not.

The full list of recipes is on [Industrial Recipes](Industrial-Recipes). Units are explained on [Units and Realism Rules](Units-and-Realism-Rules).

## The nine works

| Works | How you get it | What it is for | The site must always have |
| --- | --- | --- | --- |
| Workshop | Crafting grid: 4 planks, 2 logs, 2 cordage | Joinery, harness, small arms, cartridges, hand-drawn wire, chimney and lead-chamber blocks, cable ducts | A roof and 12 m2 of roofed floor |
| Assembly yard | Crafting grid: 4 logs, 2 planks, 3 cordage | Clamps for lime, brick and firebrick; vehicles and ordnance; boilers and engines; every other works; all heavy plant | Open ground |
| Nitre bed | Crafting grid: 4 planks, 2 clay balls, 2 dirt | Nitre earth | A roof |
| Powder mill | Built at a workshop | Gunpowder | No naked flame within four blocks |
| Foundry | Built at an assembly yard | Ore by the tonne, pig iron, wrought iron, steel, gun founding, silver, glass; silicon and phosphorus once it has an arc furnace | 12 firebrick blocks within three blocks, a chimney at least 4 high, 16 m2 of roofed floor |
| Chemical works | Built at an assembly yard | Saltpetre, sulfur, coke, acids, soda, alumina, rubber, oil; aluminium, tungsten and polysilicon with electricity | A roof, a chimney at least 4 high, water within six blocks, 16 m2 of roofed floor |
| Machine shop | Built at an assembly yard | Rolled plate, rail, machined and precision parts, wire, lenses, tyres, motors, pumps, filters, panels | A roof, 40 m2 of roofed floor, and a stationary engine |
| Electronics works | Built at an assembly yard | Valves, passives, boards, crystal, wafers, chips, computers | A roof, 16 m2 of roofed floor, and electric power |
| Power station | Built at an assembly yard | Electricity | See [Power and Cleanrooms](Power-and-Cleanrooms) |

## Running a works

1. **Choose the recipe.** `/survival works set <recipe>` while standing within six blocks. An empty works also adopts the first recipe that uses whatever you deliver first. A works cannot be retooled while a batch is in progress.
2. **Deliver the materials.** Right-click the works holding a material to deliver that stack. Sneak and right-click to deliver every matching stack you carry. Only materials the selected recipe uses are accepted. A vehicle drawn up within six blocks can hand its whole load across with `/survival vehicle deliver`.
3. **Fuel it** if it burns fuel (foundry, chemical works, machine shop): right-click with coal, coke, charcoal, logs or sticks. A foundry hearth refuses raw wood. A works bunkers up to 320 sacks.
4. **Put hands on the job.** Right-click with an empty hand, or `/survival works operate`. Each recipe has a skill and a minimum level, and a largest crew. Workers must stay within eight blocks, must not be doing other manual work, and contribute at their own work rate. An actor works one job at a time.
5. **Collect.** Right-click with an empty hand when goods are finished, or `/survival works collect`. A vehicle can take finished goods on with `/survival vehicle pickup`, up to its weight limit. Finished vehicles roll out onto clear ground beside the yard, and rolling stock onto track within eight blocks.

A batch begins only when its whole bill is in stock, and the bill is consumed at that moment. Nothing is made from a partial bill. Unused stock can be taken back by sneaking with an empty hand (`/survival works withdraw`). If the works block is broken, stock and finished goods spill onto the ground and the unfinished batch is lost.

### Commands

| Command | Effect |
| --- | --- |
| `/survival works recipes` | Lists the recipes of the nearest works |
| `/survival works info <recipe>` | Inputs, output, man-hours, skill, site needs and load of a recipe |
| `/survival works bill <recipe>` | Expands a product through every intermediate down to what is mined, grown or gathered |
| `/survival works set <recipe>` | Selects the recipe |
| `/survival works deposit` | Delivers every matching stack you carry |
| `/survival works fuel` | Stokes with the fuel in hand |
| `/survival works operate` and `leave` | Takes or leaves the job |
| `/survival works collect` and `withdraw` | Takes finished goods, or recovers unused stock |
| `/survival works status` | Recipe, progress, stock, finished goods, fuel, workers, and exactly what the site lacks |

## Time and labour

Every recipe is rated in man-hours, at 1,000 work ticks to the hour. A worker at normal rate supplies 20 work ticks a second, so one man-hour is 50 seconds of one worker's attention, and more hands divide the time up to the recipe's crew. Some examples:

| Job | Man-hours |
| --- | --- |
| Seven railway sleepers from a log | 3.5 |
| A cart wheel | 16 |
| A cart, from its frame, wheels and harness | 40 |
| A cannon barrel | 300 |
| A steam engine | 1,500 |
| A machine shop | 2,000 |
| A locomotive | 12,000 |
| A computer | 4,000 |
| A server | 30,000 |

Unattended processes (nitre beds, fermentation) need only elapsed time: a nitre bed takes 112 days, the whole year of four 28-day seasons. The server setting `industryTimeScale` multiplies all industrial times.

Completed attended work is practice in the recipe's skill for everyone who worked the batch. See [Skills and Learning](Skills-and-Learning).

## Fuel

A works that burns fuel burns it for every second its crew is working.

| Works | Burn rate | Equivalent |
| --- | --- | --- |
| Foundry | 8 fuel ticks per tick | 25 kg of coal an hour |
| Chemical works | 4 | 12 kg an hour |
| Machine shop | 32 | 100 kg an hour, the boiler of a fifty-horsepower shop engine |

Electric processes burn nothing on site. They draw kilowatts from a power station, which burns the coal instead.

## The eras

The tree is gated by what each age could really make, and the gates are tested: ban any link and everything that stood on it becomes impossible.

1. **Hand, water and charcoal.** The workstations, the workshop, the yard, the nitre bed and the powder mill. A foundry built of 3,000 bricks gives bulk smelting, crucible steel, charcoal pig iron with a water-wheel blast, bronze guns and glass. A chemical works gives saltpetre, sulfur, acids in lead chambers, soda and caustic. This age reaches muskets, cannon, bombards, trebuchets, carts, wagons and chariots.
2. **Steam.** The first boiler is hammered iron plate, riveted with iron, and the first engine cast iron and hand-made parts, lubricated with tallow. That engine drives a machine shop, and only the machine shop makes rolled plate, rail, precision parts and insulated wire. Coke, Bessemer steel (which needs a blowing engine), locomotives, field guns and petroleum belong here.
3. **Electricity.** Dynamos and generators come out of the machine shop, and each generator needs its own stationary engine. A power station makes possible aluminium, furnace silicon, phosphorus, tungsten, polysilicon, electric motors and vacuum pumps.
4. **Electronics.** Valves, crystal growth, lithography and chips need the electronics works, its named equipment, and for every wafer step a cleanroom.

A computer cannot be built without every one of: blast-furnace iron, the machine shop, the power station, cleanroom filters, fluorite, salt, bauxite, wolframite, borax, silver, rubber, lenses, lead chambers, coke and glass.

## Site rules

A works surveys its surroundings every five seconds. Until every requirement of the selected recipe is met it does nothing and consumes nothing, and `/survival works status` names what is missing.

| Requirement | What satisfies it |
| --- | --- |
| Roof | Any block with a collision shape somewhere in the 64 blocks above the works |
| Roofed floor (so many m2) | Cells at the level of the works, joined to it, with solid ground beneath, standing room or plant on them, and cover overhead. Machines standing on the floor count |
| Chimney (so many high) | A stack of chimney blocks within three blocks, whose top is open to the sky |
| Firebrick (so many) | Firebrick blocks within three blocks |
| Lead chambers | Lead chamber blocks within three blocks |
| Water | Water within six blocks |
| Blast | Water within four blocks (a water wheel) or a stationary engine within six |
| Engine | A stationary engine within six blocks, a steam boiler within three of the engine, a chimney at least 4 high for the boiler, and feed water within six blocks of the boiler |
| Named equipment | That plant block within five blocks |
| Electric | A cable duct run to a working power station |
| Cleanroom | See [Power and Cleanrooms](Power-and-Cleanrooms) |

Some recipes ask for more than their works' standing requirement. A blast furnace needs 24 firebricks, an 8-high chimney and a blast. Bessemer steel needs the engine. Acids need two lead chambers. Oil refining needs a distillation column.

### Plant blocks

Firebrick, chimney, steam boiler, stationary engine, lead chamber, distillation column, generator, power cable, arc furnace, electrolytic cell, vacuum pump, cleanroom panel, filter unit, water purifier, crystal puller, diffusion furnace, lithography stepper and wire bonder are placed in the world like any block. They do nothing on their own. A works checks for them.

## Real weights

Bills are at the real weight of the thing made. No recipe turns out more metal than went in, and nothing assembled weighs more than its parts.

| Product | Bill, in outline |
| --- | --- |
| Six-pounder barrel | 400 kg bronze, or 450 kg cast iron |
| Bombard barrel | 1,500 kg wrought iron |
| Boiler | 1,200 kg iron plate, 400 rivets |
| Steam engine | A boiler, 1,500 kg cast iron, 150 machine parts, 100 kg plate, 40 kg copper, 10 L lubricant |
| Tonne of charcoal pig iron | 1,800 kg roasted ore, 60 sacks charcoal, 120 kg lime |
| Two tonnes of coke pig iron | 3,600 kg roasted ore, 80 sacks coke, 240 kg lime |
| 4.5 tonnes of Bessemer steel | 5 tonnes pig iron and a blowing engine |
| 166 kg of gunpowder | 125 kg saltpetre, one 25 kg sack of charcoal, 17 kg sulfur |
| Ten metres of rail | 310 kg steel |
| Locomotive | 14 t steel, 5 t plate, 3 t cast iron, 1,200 precision parts, 700 kg copper |

## Injuries

Industry maims. Every finished batch at a foundry, chemical works or machine shop carries a chance of injury for each worker on it, lower with skill.

| Works | Base chance per batch | What happens |
| --- | --- | --- |
| Foundry | 2% | A burn to an arm |
| Chemical works | 1%, or 4% when acid is involved | An acid scald; hydrofluoric acid and lead work also poison |
| Machine shop | 1.5% | A finger or thumb caught in the machinery |

The chance is multiplied by 1.5 at skill 0 and falls to half at skill 20. A powder mill working within four blocks of fire, lava, a lit campfire or a hot workstation explodes with everything in it.

## Common mistakes

- Selecting a recipe and delivering materials before building the plant. Check `status` first; a works with an unmet site does not start.
- Roofing only the works block. Floor area is counted in roofed cells.
- Capping a chimney, or running it up inside a roof. The top must be open to the sky.
- Fuelling a foundry with logs.
- Expecting a crew larger than the recipe's limit to speed the job.
- Carrying finished metal home by hand. See [Transport and Logistics](Transport-and-Logistics).
