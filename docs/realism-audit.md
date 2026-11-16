# Realism audit: industry, transport and warfare

Final pass, 2026-11-15, against the code on `realism-survival`. Two earlier passes built the systems
and then gated the tech tree and the facility layouts. This pass removes the abstractions those left
behind and checks every process once more against the real one. The question for each was the same:
what did this take in the real world, in what quantities, with what plant, labour, energy and time,
and does the mod now ask for exactly that.

Where the answer is still no, the item is in the last section. The report was then re-checked
independently against the code and against published figures; what that turned up is either fixed
or added to that section.

## The unit system

Everything below rests on fixed real units. They are what make the numbers checkable.

| Thing | One item is |
| --- | --- |
| Metal, mineral, chemical | 1 kg |
| Coal, coke, charcoal | one 25 kg sack |
| Liquid (acid, oil, petrol, alcohol, methanol) | 1 litre |
| Wire | 1 metre (copper 7 g, insulated 10 g); a bond-wire item is one bond |
| Brick, clay lump | one brick, 3.5 to 3.75 kg |
| Log | 1 m3 of air-dried timber, 600 kg; a plank item is a quarter of that sawn |
| Sand, stone, gravel, limestone, firebrick block | 1 m3 at its real density |
| Fuel tick | 3.1 g of coal: a sack holds a forge fire 8,000 ticks, eight hours |
| Work | man-hours, 1,000 ticks to the hour of the game clock |
| Speed | 1 block per tick is 20 m/s; gravity is 0.0245 blocks per tick squared |
| Power | kilowatts; 1 kW at the switchboard burns 2.5 kg of coal per hour |

`Ledger.mass` holds the weights; cargo, haulage, train weight and what a person can carry all read
from it.

## What this pass found and fixed

1. **A cubic metre of ore gave one item.** An ore block now breaks down to what a metre of that
   ground holds: 1,600 kg of sorted haematite, 420 kg of malachite, 60 kg of black tin, 250 kg of
   galena, 2,100 kg of rock salt, 52 sacks of coal, 480 lumps of clay. The same fix exposed that
   Minecraft 26.3 renamed its loot fields, so every count and block-state rule in the generated
   loot tables had been silently ignored since the first pass (rail shoulders dropped rails they
   never contained; obsidian gave one flake instead of three). Corrected and tested.
2. **Smelting was one for one and free of reductant.** Bloomery, copper, tin and lead charges now
   carry their charcoal and return real yields. Bulk routes (calcining kilns, reverberatory
   smelting, puddling, lime, brick and firebrick clamps) exist because nobody hand-feeds forty
   tonnes of ore through a one-metre kiln.
3. **Recipes counted "units".** Every industrial bill is now in kilograms, metres and litres at the
   real weight of the thing made, and a test asserts no recipe turns out more metal than went in.
4. **Time was compressed.** Every job is rated in real man-hours. A nitre bed takes a full year.
5. **Whole material families were missing.** Petroleum, rubber, bauxite and the Bayer process,
   cryolite, Leblanc soda and caustic, tungsten, silver, borax and boron, glass by batch, tallow
   and oil lubricants are now mined, made and required where they really were.
6. **One lithography step stood for a process flow.** Chips now go through a four-mask MOS flow,
   stage by stage, with emulsion masks that wear out.
7. **Power was "load units".** It is now kilowatts, with real specific energies, coal burn per
   kilowatt-hour and cable loss.
8. **Vehicles carried item counts, started and stopped instantly, ran on nothing.** Cargo is by
   weight; teams eat; engines burn petrol, coal and water at real rates and need grease; a boiler
   takes two hours to raise steam; everything accelerates and brakes at real rates.
9. **Artillery flew at a tenth of its speed in a vacuum.** Shot now leaves at its real muzzle
   velocity under real gravity and quadratic air drag, served by real detachments with real charges.
10. **Muskets never fouled and flints never wore.** They do.
11. **A person could carry two tonnes.** Carried weight now slows the carrier.

## Process by process

Each row gives the real process, what the mod requires, and the verdict: **=** matches in
quantity, plant and sequence; **~** matches in kind with a stated simplification (all of which are
repeated in the last section).

### Mining, fuel and heat

| Process | Real | In the mod | |
| --- | --- | --- | --- |
| Ore in the ground | A metre of lode is mostly gangue | Per-block yields above, by mineral | = |
| Spoil | A heap of broken rock lies where it fell and does not rot | Ore, coal, clay, brick and lime never despawn or weather | = |
| Carrying | A porter manages 35 kg all day, 60 to 80 briefly | No penalty to 35 kg, falling to a tenth of pace at 120 kg | = |
| Charcoal | A clamp of cordwood chars for days at about 10% yield by weight | 4 m3 of wood, 48,000 ticks, 8 sacks (200 kg from 2.4 t) | = |
| Fuel values | Coal, coke and charcoal about 28 MJ/kg; air-dried wood about half | Sack 8,000 ticks; log 96,000; stick 80 | = |
| Wood as fuel | Raw wood fires kilns and boilers but cannot reach smelting or welding heat | Logs are refused by bloomery, forge and foundry hearth | = |
| Coke | An oven charge cokes to about 75% with 4% tar | 40 sacks of coal to 30 of coke and 40 kg of tar, 16 man-hours | = |
| Wood distillation | Charcoal plus pyroligneous liquor, 1 to 2% methanol | 4 logs to 8 sacks and 30 L methanol | = |
| Hearth fuel | A furnace burns tens of kilograms an hour | Foundry 25 kg/h, stills 12 kg/h, shop engine boiler 100 kg/h | = |

### Metals

| Process | Real | In the mod | |
| --- | --- | --- | --- |
| Crushing by hand | About 50 kg an hour | 48 kg per 1,000 ticks at the anvil | = |
| Bloomery iron | 4 kg of ore and as much charcoal per kg of bloom | 48 kg ore, 2 sacks charcoal, a sack of heat, 12 kg bloom | = |
| Working the bloom | A quarter is lost as slag and scale | 4 kg of bloom to 3 kg of bar | = |
| Copper from carbonate | Pure malachite is 57% copper | 32 kg ore and a sack of charcoal to 16 kg; 1,000 kg to 500 kg in a hearth | = |
| Tin, lead | Cassiterite 79% tin, galena 87% lead | Three quarters recovered | = |
| Bronze | About 10% tin | 9 copper and 1 tin to 10; 360 and 40 to 400 at the foundry | = |
| Charcoal blast furnace | 1.7 to 2 t of ore, 1.5 t charcoal and flux per tonne, days in blast | 1,800 kg ore, 60 sacks, 120 kg lime, 1,000 kg pig, 192 man-hours; blast from water wheel or engine | ~ |
| Coke blast furnace | Larger burden, about a tonne of coke per tonne | 3,600 kg ore, 80 sacks coke, 240 kg lime, 2,000 kg pig | ~ |
| Puddling | Pig to wrought iron at about 90%, two thirds of a tonne of coal per tonne, hearth fettled with oxide | 1,000 kg pig, 16 sacks coal, 50 kg ore to 900 kg, 60 man-hours | = |
| Crucible steel | Bar iron melted with charcoal in pots | 30 kg iron and a sack of charcoal to 30 kg, smith 14 | = |
| Bessemer | Five tonnes blown in twenty minutes, 10% loss, needs a blowing engine and a manganese addition | 5,000 kg pig to 4,500 kg steel, engine required; no manganese | ~ |
| Plate | Hammered at first, rolled once there are mills | 20 kg to 19 at the forge; 500 to 480 in the machine shop | = |
| Rail | About 62 lb per yard | 31 kg per metre: 310 kg of steel rolls ten metres | = |
| Wire | 1 mm copper runs 140 m to the kilogram; hand drawing is slow | 1 kg to 140 m in 14 man-hours by hand; 10 kg to 1,400 m in 2 by machine | = |
| Silver | Cupelled from argentiferous lead holding a few hundred grams to the tonne | 2,000 kg lead to 1 kg silver and 1,900 kg lead back | = |
| Lime, brick, firebrick | Burned in clamps with cordwood; a tonne of stone gives 560 kg of lime | 1 m3 limestone and 3 logs to 1,400 kg; 2,000 lumps to 1,900 bricks; 480 lumps and sand to a cubic metre of firebrick | = |
| Glass | Sand, alkali, lime. Forest glass took as much ash as sand, and ash is half a percent of the wood burned | A cubic metre of sand with 1,200 kg of wood ash, or with 500 kg of soda ash and 150 kg of lime | = |

### Chemistry

| Process | Real | In the mod | |
| --- | --- | --- | --- |
| Nitre bed | About a year to ripen; a few percent of the earth by weight in saltpetre | 64 kg matter, 16 ash, 16 straw, 112 days (the full year of four 28-day seasons); 64 kg earth leaches to 3 kg | = |
| Sulfur | Roasted from pyrite | 4 kg pyrite to 1 kg | = |
| Black powder | 75:15:10, milled wet away from fire | 125 kg saltpetre, one 25 kg sack of charcoal, 17 kg sulfur to 166 kg; a flame within four blocks destroys the mill | = |
| Sulfuric acid | Sulfur burned with nitre in lead chambers | 20 kg sulfur and 1 kg nitre to 60 L, two lead chambers of 400 kg each | = |
| Nitric, hydrochloric, hydrofluoric acid | Nitre, salt or fluorspar with sulfuric acid; HF only in lead | As stated; HF needs the chambers and poisons | = |
| Leblanc soda | Salt and acid to salt cake, then chalk and coal; HCl as by-product | 12 salt, 10 acid, 6 lime, a sack of coal to 10 kg soda ash and 8 L HCl; the lime is charged burnt rather than as chalk | ~ |
| Caustic soda | Soda ash causticised with lime | 10 and 6 to 7 kg | = |
| Bayer alumina | 2 to 2.5 t bauxite per tonne, caustic mostly recycled | 20 kg bauxite and 1 kg caustic to 9 kg | = |
| Cryolite | Synthesised from HF, alumina and soda | As stated | = |
| Aluminium | Hall-Heroult, 2 kg alumina and about 25 kWh per kg in early cells, carbon anodes, cryolite bath | 20 alumina, a sack of coke, 1 cryolite to 10 kg at 100 kW for 2.5 hours | = |
| Furnace silicon | Quartz and carbon in an arc furnace, 12 MWh per tonne | A cubic metre of sand and 20 sacks of coke to 450 kg at 400 kW | = |
| Polysilicon | Trichlorosilane fractionated and reduced in hydrogen, 100 to 200 kWh per kg | 12 kg silicon and 40 L HCl to 10 kg at 150 kW for 10 hours, with a column and a cell for the hydrogen | = |
| Phosphorus | Bone ash (a sixth phosphorus), sand and coke in an arc furnace, 12 to 15 kWh per kg | 100 kg bone ash to 16 kg at 100 kW for two hours | = |
| Tungsten | Soda leach of wolframite, acid precipitation, hydrogen reduction | 10 kg ore to 5 kg with soda, HCl and an electrolytic cell for the hydrogen | = |
| Petroleum | Crude from wells, fractionally distilled to about a fifth petrol | 100 kg of surface oil sand to 10 L crude; 100 L crude to 20 L petrol and 10 L lubricant in a column | ~ |
| Rubber | Latex tapped from tropical trees, vulcanised with sulfur | Jungle log to 3 kg raw; 20 kg with 1 kg sulfur | = |
| Phenolic resin, photoresist | Phenols are about 3% of coke-oven tar | 250 kg tar for 10 kg resin; 100 kg for 5 of resist | = |

### Machines and plant

| Thing | Real | In the mod | |
| --- | --- | --- | --- |
| First boiler and engine | Hammered plate, cast cylinder, tallow | 1,200 kg plate and 400 rivets; 1,500 kg pig, 150 hand-made parts, lubricant; no machine shop needed | = |
| Machine shop | Line shafting off a stationary engine | Four tonnes of iron to build; engine, boiler, flue, feed water and 40 m2 of floor to run | = |
| Works buildings | Thousands of bricks and their lime | Foundry 3,000 bricks, chemical works 2,000 and 400 kg of lead, electronics works 2,000 | = |
| Chimney, lead chamber | Brick stacks; chambers of sheet lead | 500 bricks and 50 kg lime per metre; 400 kg lead each | = |
| Dynamo, generator | A 35 kW machine weighs over a tonne and needs an engine of fifty horsepower to turn it | 1,000 kg iron, 300 kg plate, 8 km insulated wire; one stationary engine per generator | = |
| Power | Early steam plant; low-voltage direct current served about a mile with losses of ten to twenty percent | 35 kW a generating set, 2.5 kg coal per kWh, two percent loss per hundred metres, a mile of lead-sheathed main at 6 kg of copper a metre | = |
| Working floor | Processes need room under cover | Roofed square metres counted around each works | = |
| Hazards | Burns, acid, machinery | Chance per batch, lower with skill | = |

### Semiconductors

| Step | Real (1960s MOS) | In the mod | |
| --- | --- | --- | --- |
| Crystal | Czochralski boule from doped polysilicon | 20 kg polysilicon and phosphorus to ten 2 kg boules, 30 kW, puller | = |
| Wafers | Sawn, lapped, etched; 400 to 500 from a 2 kg two-inch boule | 400 per boule with HF and nitric acid | = |
| Masks | Silver-halide emulsion on glass through a reduction camera; contact printing wears them out | Glass, silver, nitric acid, salt, gelatin; one mask per lot per layer | = |
| Field oxide | Thermal oxidation in a tube furnace | Lot of 25, diffusion furnace, purified water | = |
| Source and drain | Photo, HF etch, boron diffusion | Resist, mask, HF, boron oxide, mask aligner, furnace | = |
| Gate oxide, contacts | Two more photo steps | Two more stages, each with its own mask | = |
| Metal | Aluminium evaporated under vacuum and patterned | Aluminium, mask, vacuum pump | = |
| Yield | Some 800 gross dice a millimetre and a half square on a two-inch wafer, one in five good | 160 per wafer at dicing in a perfect room, half that at 95% cleanliness, below which no wafer work starts | = |
| Packaging | 16-pin ceramic package, one bond wire per pin | 16 dice, 16 packages, 256 bonds, wire bonder | = |
| Cleanroom | Sealed, filtered, gowned | Sealed panel room 5 x 5 x 3 or more, a filter per 4 m2, iron door, suits, powered filters | = |
| Computer | A minicomputer of small-scale logic: thousands of packages, wire-wrapped | 2,800 chips, 120 boards, 8 km wire, 4,000 man-hours | = |
| Server | A mainframe: tens of thousands of packages | 20,000 chips, 900 boards, 60 km wire, 30,000 man-hours | = |

### Transport

| Thing | Real | In the mod | |
| --- | --- | --- | --- |
| Payload | Cart half a tonne, wagon two, lorry three, goods wagon ten | The same, by weight of what is loaded | = |
| Haulage | A horse starts about 1.2 t gross on a made road | Checked against tare plus load; says how many horses are needed | = |
| Pace | Draught at a walk, 5 to 10 km/h; a chariot gallops; an early lorry 25 km/h | 10, 9, 30 and 25 km/h tops, less with load and ground | = |
| Fodder | 10 kg a day for a working horse | A kilogram per animal ten times a day, drawn from the load by weight (hay, grain, roots), or grazing when halted on grass; weakness, then starvation | = |
| Petrol | About 30 L per 100 km in a 1910s lorry | A litre per 3 km (33 L per 100 km), alcohol per 2 km | = |
| Grease | Axles and engines seize without it | A litre per 50 km; damage without | = |
| Starting and stopping | Fractions of a metre per second squared | 0.5 m/s2 away, 1.5 to 3 m/s2 stopping; drivers brake in time | = |
| Track | Rail, sleepers, ballast, spikes and labour by the metre | 2 m of rail, 2 sleepers, 2 m3 ballast, 8 spikes, 2.5 man-hours, three blocks wide | = |
| Gradient | Adhesion railways climb a few percent | One block in thirty | = |
| Locomotive | A 24 t tank engine; two hours to working pressure; about 14 kg of coal (50 lb a mile) and 100 L of water a kilometre | The same; 43 km/h; 150 m to stop | = |
| Curves | Hundreds of metres of radius | Right angles, taken at 11 km/h or derailed | ~ |
| Building one | Months of a works' labour | 14 t steel, 5 t plate, 3 t iron, 12,000 man-hours: 23.9 t of parts for a 23.9 t engine | = |

### Warfare

| Thing | Real | In the mod | |
| --- | --- | --- | --- |
| Musket cartridge | 31 g ball, six drams (10.6 g) of powder with priming | 96 rounds from 96 balls and a kilogram of powder | = |
| Musket fire | Three rounds a minute drilled; one misfire in six to ten | Eighteen seconds drilled, thirty raw; 10% misfire, 50% in rain | = |
| Fouling and flints | Barrels foul within tens of rounds; a flint lasts about forty | 4% slower and 1% more misfires per round, unloadable at forty; flint spent at forty | = |
| Ball in flight | 450 m/s, well under a second to any target in view | Hitscan to 120 m; drop is inside the dispersion | = |
| Six-pounder | 400 kg bronze barrel, 0.57 kg charge, 440 m/s, two rounds a minute, eight men, four to six horses | 400 kg barrel, half-kilogram bag, 440 m/s, two rounds a minute, eight men, four to six horses | = |
| Bombard | Tonnes of wrought iron on a timber bed, stone shot, a few shots a day | 1,500 kg barrel, 5 t with its bed, 40 kg stone over 10 kg, 220 m/s, eight men, about 2 km/h behind horses and slower behind oxen, may burst | = |
| Trebuchet | Counterweight engine built on site, 100 to 250 m | 50 m/s release, 45 to 78 degrees, twelve men, a stone an hour | = |
| Field gun | An 1870s rifled breech-loader: 8 kg shell, about 2 kg of black powder, 455 m/s, about 7 km, relaid after each round | The same; 6.2 km at full elevation; a round in thirty seconds | = |
| Trajectory | Gravity and drag | 9.8 m/s2 and quadratic drag from each shot's mass and area; a shot is lost where the loaded world ends | ~ |
| Walls | Timber splinters, masonry is battered down, earth absorbs | Cumulative damage by material | = |
| Men | Fed, led and unbroken, or gone | Rations, water, morale, rout and desertion | = |

## What is deliberately brutal

- A locomotive is some fifty tonnes of ore through the furnaces, twelve thousand man-hours in the
  yard, and a line that costs 62 kg of steel and two and a half man-hours every metre.
- A server is twenty thousand chips from 125 good wafers through five cleanroom stages,
  on power that burns two and a half kilograms of coal for every kilowatt-hour.
- A nitre bed is a year. A bombard is weeks at the forge for a few shots a day.
- Industrial work injures; a flame near a powder mill kills; a cleanroom breach scraps the lot.
- A team with no fodder stops. An engine with no water, coal, grease or steam stops. A train
  that is not braked in time leaves the rails.
- Nobody carries a tonne of ore home. It goes in a cart, and the cart needs a horse, and the
  horse eats.

## Models

Vehicles are true scale against the 1.44-block gauge: the tank engine is 6.4 blocks long and 3.6
tall with six coupled 1.3-block drivers, side tanks, cab, dome, chimney and buffers; wagons and
carriages ride on plate wheels with flanges inside the rails. Wheels are round, spoked and separate
from the body so they turn with distance travelled, and the coupling rods ride their crank pins.
Scale and the build-on-the-track approach follow the conventions of Immersive Railroading; the
cleanroom follows the idea of GregTech's sealed, filtered multiblock, with physical rules of its
own. No assets or code from either were used.

## What is still not one to one

This is the whole list. Each is a property of Minecraft itself or a bound this pass chose not to
cross, and none is hidden behind a number that pretends otherwise.

1. **Two clocks.** Minecraft's day is twenty minutes. Anything a person does in seconds (walking,
   loading a musket, serving a cannon, a shot in flight, a vehicle's speed) runs in real seconds.
   Anything measured in hours or days (all production, steam raising, bombard and trebuchet
   loading, fodder, nitre) runs on the game calendar at 1,000 ticks to the hour. Both are real
   rates; they are 72 times apart, and that ratio is Minecraft's, not the mod's.
2. **Blocks.** The world is cubic metres. Breaking a block is one unit of field labour whatever a
   metre of that rock would really cost to win; a works, a boiler or a mask aligner is a one-metre
   block standing for a machine that needs the floor the site rules count; railway curves are
   right angles; models are cuboids.
3. **Blocks in the hand.** Building blocks carried in an inventory weigh nothing on the carrier
   (they do weigh on a vehicle). Carrying and placing whole blocks is how Minecraft is built.
   Vanilla crafting that is still allowed keeps its own ratios (a log to four planks, glass to
   panes).
4. **Smallest amounts.** Nothing is smaller than one item. A kilogram of dopant, a kilogram of
   silver for a batch of masks, a kilogram of tungsten for twenty valves, a whole sack of anode
   carbon, or a kilogram of aluminium for bond wire is charged where the real process uses grams.
5. **Minor elements and materials.** Manganese for Bessemer steel, zinc, nickel, chromium, the
   cathode and getter metals inside valves, and fused quartz for crucibles and furnace tubes are
   not modelled. Rivets and spikes are one item whether struck from copper or iron.
6. **Flux as burnt lime.** Blast furnaces and the Leblanc process charge quicklime where the real
   ones charged raw limestone or chalk, because limestone exists only as a cubic-metre block.
7. **No smokeless powder.** There is no nitrocellulose, brass case or fuze, so the field gun is a
   black-powder breech-loader of the 1870s and not a quick-firer.
8. **Oil.** Crude comes from surface oil sand; there are no wells, and straight-run petrol from
   bitumen is more than a real still would give.
9. **One integrated circuit.** There is one logic package, as early machines really were built
    from one or two gate types; core memory, discrete transistors and valve computers are not
    separate machines.
10. **Animals in harness** are fed but not watered or rested, and a mounted soldier's horse is not
    fed at all.
11. **Vehicle hitboxes** are one square box per vehicle, shorter than the longest models.
12. **Ballistics** have no wind, barrel wear or air density, drag is a single coefficient per shot,
    and a shot that leaves the loaded world is lost, so the computed ranges of kilometres are
    never seen in play.

## Verification

An independent re-check of this report against the code and published figures found, and this
pass then fixed: a standing fire that doubled a running engine's coal; cleanroom yield compounding
over seven steps; gunpowder charged with sacks of charcoal as if they were kilograms; generators
with no engine to turn them and a tenth of their real weight; assembled vehicles heavier than their
parts; carrots and seeds weighed as cubic metres; phosphorus at twelve times its real energy; and
figures for silver, glass, puddling, wafers, alcohol, tar chemicals, cartridges and gun charges that
were off the record. The field gun was re-based on a gun its propellant could really drive.
Writing the player wiki from the code turned up three more, also fixed: a jar could no longer be
made because it wanted a clay block that nothing yields any more (it now takes eight lumps); ore
and coal left on the ground weathered into plant fibre after four days; and prospecting did not
recognise the accessory ores. Charcoal and ash can now be made from any timber, not one species.

142 GameTests pass. Those that changed or were added in this pass check: per-block ore yields
through real mining; charcoal as reductant; no recipe creating mass and nothing assembled weighing
more than its parts; every recipe naming only
registered items; the year-long nitre bed and its yield; kilowatt capacity, coal burn and
cable loss at a real power station; roofed floor area; cargo by weight and its refusal of a 600 kg
log; carried weight and pace; a horse leaning into the collar, a load one horse cannot start, a
team going hungry and then eating from the load; steam raising, train weight and slow starts;
musket fouling, cleaning and flint life; drag-limited ranges for trebuchet and cannon and a firing
solution that lands on its mark. The era-gate tests now also prove a computer cannot be built
without bauxite, wolframite, borax, tungsten, silver, rubber, caustic soda or lubricant.

On a live dedicated server: all forty later material icons and twenty-five plant and ore blocks
render; an eight-man detachment loads a cannon from its store and the shot crosses to a cobblestone
wall inside one tick and breaches it; a locomotive with steam up draws a goods wagon away at its
real acceleration while a two-horse wagon starts alongside; a fab on two engine-driven
generating sets (70 kW, 17 kW drawn) reaches 100% cleanliness, packages sixteen chips and falls to 0%
when its wall is breached; a conscript loads through the full
drill and fires. Images are in `docs/validation/2026-11-15-war-industry/`.

Sources consulted for the reference mods: [Immersive Railroading overview](https://www.9minecraft.net/immersive-railroading-mod/wiki/),
[GregTech CEu Modern changelog](https://modrinth.com/mod/gregtechceu-modern/changelog?page=1).
