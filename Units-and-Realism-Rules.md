---
title: Units and Realism Rules
---
{% include nav.html %}

# Units and Realism Rules

Every quantity in the mod is a real one. This page gives the units, so that a recipe, a cargo limit or a fuel bill can be read as kilograms, litres, kilowatts and hours, and then lists the few places where the game still follows Minecraft instead of the world.

## What one item is

| Thing | One item is |
| --- | --- |
| Metal, mineral, chemical (ingots, ores, salt, lime, acids as solids) | 1 kg |
| Coal, coke, charcoal | one 25 kg sack |
| Liquid (acid, oil, petrol, alcohol, methanol, lubricant) | 1 litre |
| Wire | 1 metre: copper 7 g, insulated 10 g. A bond-wire item is one bond |
| Brick, clay ball | one brick or a brick-sized lump, 3.5 to 3.75 kg |
| Log | 1 cubic metre of air-dried timber, 600 kg |
| Plank | a quarter of a cubic metre of sawn timber, 150 kg |
| Sand, cobblestone | 1 cubic metre, 1,600 kg |
| Gravel | 1 cubic metre, 1,700 kg |
| Stone, limestone | 1 cubic metre, 2,600 kg |
| Other building blocks | 1 cubic metre, counted as 1,500 kg |
| Food, roots, seeds | about 0.3 kg; other loose vanilla goods 0.5 kg |
| Arrow | 50 g |

## What a block of ground holds

Breaking a natural block gives what a cubic metre of that ground really contains. See [Natural Resources and Mining](Natural-Resources-and-Mining) for where each is found.

| Block | Yields |
| --- | --- |
| Haematite ore | 1,600 kg haematite |
| Malachite ore | 420 kg malachite |
| Cassiterite ore | 60 kg cassiterite |
| Galena ore | 250 kg galena |
| Pyrite ore | 300 kg pyrite |
| Rock salt | 2,100 kg salt |
| Fluorite ore | 600 kg fluorite |
| Bauxite ore | 2,400 kg bauxite |
| Wolframite ore | 30 kg wolframite |
| Tar sand | 2,000 kg oil sand |
| Borax ore | 1,500 kg borax |
| Coal ore | 52 sacks of coal (1,300 kg) |
| Clay | 480 clay balls (1,800 kg) |

Broken ore, coal, clay, brick and lime do not rot or vanish: a heap lies where it fell until someone carts it away. Other things left in the open weather: see [Natural Resources and Mining](Natural-Resources-and-Mining).

## Carrying

What you carry weighs on you. Everything in a player's inventory counts (for other actors: hands, armour and pack), except building blocks. Seeds, roots and other things that are planted rather than built with are loose goods and do count. Seeds, roots and other things that are planted rather than built with are loose goods and do count.

| Load | Pace |
| --- | --- |
| Up to 35 kg | Full |
| 35 to 120 kg | Falls in a straight line from full to one tenth |
| 120 kg and more | One tenth |

A stack of 64 iron ingots is 64 kg. Two sacks of coal are 50 kg. Bulk goes on a vehicle: see [Transport and Logistics](Transport-and-Logistics).

## Time

| Unit | Ticks |
| --- | --- |
| One second of real time | 20 |
| One hour of the game clock | 1,000 |
| One game day | 24,000 |
| One season (default) | 28 days |
| One man-hour of work | 1,000 work ticks from one worker at normal rate |

Work rated in man-hours is shared between the hands on a job. A skilled, fit worker contributes faster than a hungry or injured one. The server setting `industryTimeScale` (default 1.0) multiplies every industrial work time.

## Heat, fuel and power

| Fuel | Heat |
| --- | --- |
| Sack of coal, coke or charcoal | 8,000 fuel ticks: a forge or kiln fire for eight hours |
| Log | 96,000 fuel ticks, in kilns, boilers and stills only. Raw wood cannot reach working heat in a bloomery, forge or foundry hearth |
| Stick | 80 fuel ticks |

One fuel tick is about 3.1 g of coal. A works burns fuel faster than a forge: see [Industrial Manufacturing](Industrial-Manufacturing). Electric power is in kilowatts, and a power station burns 2.5 kg of coal for each kilowatt-hour: see [Power and Cleanrooms](Power-and-Cleanrooms).

## Speed and ballistics

One block per tick is 20 metres per second, or 72 km/h. Gravity on shot is 9.8 m/s2. Vehicles accelerate and brake at real rates, and artillery flies at its real muzzle velocity with air drag: see [Railways](Railways) and [Siege Artillery](Siege-Artillery).

## What is still a Minecraft convention

These are the places where the mod does not claim to match the world.

1. **Two clocks.** A Minecraft day is twenty real minutes. Things a person does in seconds (walking, loading a musket, serving a cannon, a shot in flight, how fast a cart rolls) run in real seconds. Things measured in hours and days (all production, raising steam, fodder, nitre beds, loading a bombard) run on the game calendar. Both use real rates. They are 72 times apart because the day is.
2. **Blocks.** The world is made of cubic metres. Breaking a block is one unit of field labour whatever that metre of rock would really cost. A works or a machine is a one-metre block standing for plant that needs the floor area the site rules demand. Railway curves are right angles.
3. **Blocks in the hand.** Building blocks in your inventory do not weigh on you, although they do weigh on a vehicle. Vanilla crafting that is still allowed keeps its own ratios, such as four planks from a log.
4. **Smallest amounts.** Nothing is smaller than one item. A kilogram of dopant, a kilogram of silver for a batch of photomasks, a kilogram of aluminium for bond wire, or a whole sack of anode carbon is charged where the real process uses grams or a few kilograms.
5. **Minor elements and materials.** Manganese for Bessemer steel, zinc, nickel, chromium, the getter and cathode metals inside valves, and fused quartz for crucibles and furnace tubes are not modelled. A rivet is one item whether struck from copper or iron.
6. **Flux as burnt lime.** Blast furnaces and soda works charge quicklime where the real ones charged raw limestone or chalk.
7. **No smokeless powder.** The field gun is a black-powder breech-loader of the 1870s, not a quick-firer.
8. **Oil.** Crude oil is won from surface oil sand. There are no wells.
9. **One integrated circuit.** There is one logic package. Core memory, discrete transistors and valve computers are not separate machines.
10. **Animals in harness** are fed but not watered or rested.
11. **Ballistics** have no wind and no barrel wear, and a shot that leaves the loaded world is lost.
12. **Two illnesses stand for all of them.** One mild respiratory and one serious systemic infection, and the one antibiotic treats both.
13. **Cloth lasts a season, not a year.** The rules of wear are real; the scale follows the short calendar.
14. **Predators** run at about half their real speed (still faster than any person), do not breed, keep no territories and borrow the voices of other animals.
