# Spawn — natural-world survival

A Minecraft 26.3 / NeoForge 26.3.0.77 survival overhaul. Mod ID: `spawn_9b5fbb9`.

## Tectonic geography and regional climate

The explicit Tectonic profile integrates **Tectonic 3.0.32 + Lithostitched 2.0.6**
with terrain-relative mineral districts, regional weather, persistent soil water,
physical irrigation, wind exposure, and climate-dependent pasture. Players and
enrolled agents share the same environment; each client sees its own local rain
or snow. Rain falls where the regional weather puts it, and it does what rain does:
fire in the open goes out and will not spread, and a downpour drowns a campfire
that has no roof over it. Backstories and autonomous settlement/trading AI are
outside this milestone.

Use a **new world** with the supplied runtime and configuration. Existing worlds
retain their saved profile, and mismatched generators/configurations are refused.
See the [installation and environment guide](docs/tectonic-environment.md) and
[measured validation report](docs/validation/2026-11-08-tectonic-environment.md).

For development, `powershell -NoProfile -ExecutionPolicy Bypass -File
tools/qube-tectonic.ps1 build` prepares the pinned, hash-verified runtime. Use
`test` for the integration suite. Ordinary Qube commands preserve the legacy
runtime. `/survival climate` reports current conditions; right-click farmland
with an open water pot or use `/survival irrigate x y z` to perform timed irrigation.

Players, fake players, bots, and script-controlled humanoids share the same needs,
injuries, capacities, resource costs, and biological clock. External controllers
can inspect conditions, plan work, and coordinate through the server API.

## Daily survival

| State | What matters |
| --- | --- |
| Recreation | Meet 4.5 game hours of reading, toys, animal interaction, or active company each day. Sleep does not count. Missing the quota reduces mental/work capacity. |
| Hunger | Sprinting/hunting, climbing, swimming, jumping, fighting, mining, and manual production consume more food. At zero hunger, the starvation speed modifier is 10% of base speed. |
| Thirst | Activity and hot/arid exposure increase water use. At 30% or less, mental capacity is capped at 30%. Zero thirst kills. Untreated water can cause illness. |
| Temperature | Climate, altitude, seasons, rain/wetness, shelter, fire, exertion, and garments change body temperature. Sustained cold/heat causes progressive illness and damage. |
| Health | Limbs and individual digits affect manipulation/movement; cranial and sensory injuries affect work. Bleeding, poisoning, infection, and permanent blindness persist. |
| Illness | A respiratory infection turns up by chance about twice a year, a systemic one far less often, and twice as readily in the starved or parched. A carrier passes it on from shortly before the first symptom: within two blocks in the open, across a shared room under one roof. |

One game hour is 1,000 ticks. At 20 TPS, a day lasts 20 real minutes and the
4.5-hour recreation requirement lasts 3 minutes 45 seconds. Seasons default to
28 days each. Creative/spectator physiology pauses; automated survival players
receive no controller exemption.

Press **H** or use `/survival health` for anatomy, capacities, conditions, and
clothing. The HUD shows all five states and the season. Time continues while
inspecting health. Sneak-interact with a nearby humanoid to inspect/socialize;
use `/survival act social <entity>` for an animal or humanoid companion.

Right-click a book or wooden toy, or use `/survival act read` / `play`, to begin
a ten-second recreation interval. Continue using it to renew the interval.
Moving with an engaged nearby companion renews social time. Idle proximity and
sleep do not satisfy the quota.

## First-day progression

1. Find sparse stone debris near rocky ground, fallen sticks near woodland, and
   species-specific plant stands. Gather flax, then hand-process it into plant
   fiber before twisting cordage. Each stand supplies its own produce and seeds.
2. Knap stone edges and bind tools with plant-fiber cordage. Make a hoe and sow
   suitable seeds on moist prepared soil. Rice also needs adjacent paddy water.
3. Build a metal-free kiln and campfire from natural materials. Sticks/logs supply
   initial kiln heat; kiln charcoal improves the reserve.
4. Tend eight lumps of clay in a fueled kiln to make a jar. Collect surface water
   and boil an **open** jar near a lit fire for 1,000 loaded ticks. Jar quality
   determines its capacity.
5. Store food in pottery and seal it with an actual lid. Rough, standard, and
   fine jars preserve food for 2, 4, or 6 times its ordinary life at default
   tuning. Open it before drinking. Food remains finite.
6. Use a loom to spin wool, weave cloth, and sew garments. Wear shirts, pants, jackets,
   and shoes in four clothing slots beneath armor. Dry wool resists cold; warm
   layers also retain heat in hot conditions.
7. Process natural copper/tin/iron through crushing, roasting, bloomery smelting,
   forge work, and anvil finishing. Copper plus tin makes bronze; iron blooms
   must be worked. Equipment needs a trained smith and real forged blanks.
   Natural obsidian yields sharp, brittle flakes and tools with early equipment.

A workstation uses real inputs, elapsed time, and finite durability. Kilns and
bloomeries consume fuel; ordinary heating can continue unattended. Pottery,
forge, anvil, and loom work pauses when its worker is absent,
dead, too far away, or incapable. Work speed follows the worker's current
capacities. Unfinished inputs do not become finished tools or food.

**Quantities are real.** One metal, mineral or chemical item is a kilogram; a
coal, coke or charcoal item is a 25 kg sack; a wire item is a metre; a liquid
item a litre; a brick item one brick; a building block its cubic metre. A cubic
metre of haematite seam breaks down to 1,600 kg of sorted ore, a metre of
copper lode to 420 kg of malachite, a metre of tin lode to 60 kg of black tin,
a coal block to 52 sacks, a clay block to 480 brick-sized lumps. A bloomery
charge is 48 kg of roasted ore with two sacks of charcoal for a 12 kg bloom;
four kilograms of bloom hammer down to 3 kg of bar. Copper gives half the weight of its
ore, tin and lead three quarters. A sack of fuel holds a forge fire eight
hours; a log holds a kiln twelve times as long but cannot reach working heat
in a bloomery or forge; a stick is kindling. Four cubic metres of wood char to
eight sacks over two days. Broken ore, coal and clay lie where they fell and
do not rot, and what you carry weighs on you: 35 kg costs nothing, 120 kg or more
leaves you at a tenth of your pace. Bulk goes on wheels.

Grid crafting also takes 100 loaded ticks of manual work, scaled by mental and
manipulation capacity and the relevant skill. One actor can perform one manual job at a time across
the crafting grid, controller jobs, and specialist stations. Passive heating is separate.
Grid recipes and virtual merchant offers cannot manufacture specialist goods;
collaborators exchange the actual items they produce.

Right-click a station with fuel or inputs. Use an empty hand to collect output;
shift-use to recover unfinished input. For an exact tool/process choice, use
`/survival recipes` and `/survival produce <recipe>` while holding its main input
and the secondary supply in your offhand. `/survival station` shows progress;
`/survival fuel` and `/survival collect` operate the nearest station within six
blocks.

## Learned skills and specialists

Every humanoid has sixteen independent skills at levels 0–20. Useful completed
work earns XP from actual active ticks. Passion changes learning (none 35%,
interested 100%, burning 150%); it does not create materials or grant work
permission. Learning slows after 4,000 net XP per skill per day. Skills at level
10 and above gradually decay, with a buffer after losing a level. Passions
start at none (a 35% learning rate) and are assigned only by an operator; there are no backstories or
permanent profession classes. Learning dies with the learner (see Death under
World rules); existing six-skill saves migrate to the new scale.

| Skill | Default requirements and effects |
| --- | --- |
| Pottery | Fueled kiln and 25% manipulation; effective level 5 makes standard jars, 12 makes fine. |
| Smithing | Copper tools and weapons need level 4, bronze 8, iron 12; copper and bronze armor needs 12 and iron armor 14. Real blanks, fuel and anvil work remain required. |
| Weaving | Hand-process flax into fiber and cordage, then spin, weave and sew at a loom. Pants need 2 and jackets 4. |
| Farming | Tilling, sowing and harvesting improve work speed; seasons and growth time still apply. |
| Foraging | Gathering actual wild stands and fruit improves field work. |
| Hunting | Real animal kills train hunting; pursuit, reach, sight and weapon wear still apply. |
| Mining | Natural ore/stone extraction and mineral preparation train mining. Placed or previously extracted stone cannot renew extraction XP. |
| Construction | Irreversible wooden assemblies and workstation construction train construction. Reversible material compression does not. |
| Cooking | Attended ingredient crafting trains cooking; unattended heating does not. |
| Animals | Useful feeding, breeding and real animal products train animal care. |
| Medicine | Effective treatment with consumed supplies trains medicine. |
| Melee | Real wounds dealt hand to hand train it. Scales a soldier's blows and recovery, shield parries, and a player's melee damage (80–120%). |
| Marksmanship | Real hits with arrows, bolts and musket balls train it. Tightens aim and shortens the loading drill. |
| Riding | Time in the saddle, on the box or at the reins trains it. Raises vehicle pace and makes a rider harder to unhorse. |
| Gunnery | Serving and firing ordnance trains it. Speeds loading and tightens dispersion. |
| Engineering | Attended work at mills, works and yards trains it; most industrial recipes gate on it. |

Manual skill speed ranges from 50% at level 0 to 100% at level 20, multiplied
by current bodily capacity. Canceled, rejected, idle and unloaded work grants
no completion XP. Native interactions and controller work use the same rates.

Learned skill is separate from **manipulation**, mental capacity, and movement.
An expert can still be physically unable to work. Pottery quality uses effective
skill after current physical/mental limits. Rough/standard/fine jars hold 2/3/4
food stacks or water portions; existing unmarked jars keep their previous fine
quality. A loom consumes four yarn or three cordage per cloth, then 4 cloth for
a shirt/pants, 6 for a jacket, or 2 for shoes, plus the binding supply. Looms and
anvils use manual work without fuel; heated stages consume fuel while working.
The fire burns by the clock and the work goes at the worker's pace, so a job
is refused until the fire holds enough for that worker: a beginner needs about
twice the bare figure. A job left by a worker who died stays in the station as
it stood and is taken up by the next person able to do it.

Open **Skills** from the H health screen, or use `/survival skills`, for a
scrollable skill page with levels, XP, passions, daily learning and decay.
`/survival planning` and `recipeinfo` report exact station, skill, input, and time
requirements. `/survival cancel` returns reserved unfinished work supplies.
`/survival forage <x> <y> <z>` and `/survival hunt <animal>` expose field actions;
NPC hunters navigate loaded terrain, while player controllers move their actor
into actual weapon reach. Ordinary crop/patch interactions use the same rules.

## Water, pottery, clothing, and care

Hold a fired pot in your main hand:

- `/survival vessel fill`: collect nearby untreated surface water.
- `/survival vessel boil`: begin boiling beside lit heat; remain nearby holding it.
- `/survival vessel store`: move unpackaged offhand food into the pot.
- `/survival vessel retrieve`: retrieve food, opening the pot when necessary.
- `/survival vessel seal`: consume an offhand lid to seal, or recover the lid when opening.
- `/survival act drink`: drink an open filled vessel, an ordinary water bottle,
  or nearby surface water. Boiled pottery water is safe.

Use a pot on water to fill it, beside a lit campfire to boil it, and on farmland
to irrigate; shift-use seals it, stores into it or takes from it. Pots hold two, three, or four
food stacks according to quality and cannot nest containers. Transport keeps
their contents and freshness.
Worn-out pottery spills stored food and leaves shards.

Right-click garments to wear them; a garment already in that slot comes back to
the hand. `/survival act undress` takes off the outermost garment, or
`undress_jacket`, `undress_shirt`, `undress_pants`, `undress_shoes` a named one.
`/survival dye <0–3>` consumes a held natural dye for the shirt, pants, jacket,
or shoes respectively. Garment wear and color survive swapping and ordinary
inventory/death rules.

Cloth wears with use, not with the calendar. A garment lasts 28 days of steady
walking: half as fast standing about, a quarter as fast asleep, twice as fast
at a run, three times climbing, and half as fast again when wet. Shoes wear on
the road and hardly at all at rest. A cut, a bite or a burn takes half a day of
life out of the cloth over the wound for every point of damage. Worn cloth is
thinner: a garment about to fall apart keeps half its warmth, and the health
screen shows each garment's condition. Sneak-use yarn (wool) or cordage (fibre),
or `/survival act mend`, to patch the most worn garment of that cloth: each
length gives back a fifth of its life, but a patched garment never gets better
than four fifths sound. Worn right through, it falls apart.

Bandages stop excessive bleeding. Painkillers temporarily relieve pain without
repairing injuries. Antitoxin treats poisoning, antiseptic treats infected wounds,
and antibiotic treats systemic/respiratory infection. Destroyed eyes remain
blind for that life. A recovered body develops strain-specific immunity. There
is no second body: death is permanent.

Right-click treatment to use it; interact with a nearby humanoid while holding
medicine to treat them. Sneak-interact with food or clothing to feed or clothe a
companion. Supplies are consumed. Eating follows the item's actual consumption
time and remainder rules. Players must crouch to accept clothing from another
actor or to hand it over. Someone awake keeps the coat already worn unless the
one held out is the warmer and sounder garment, in which case the old one comes
back to the giver; nobody conscious can be stripped, and someone unconscious or
helpless can be dressed and undressed by another.

## Agriculture and livestock

Wheat can be sown in spring/autumn. Rice is a spring paddy crop. Maize is sown in
late spring or early summer. Fiber and medicinal crops, retained vanilla crops,
and annual fruit follow their own seasonal schedules. Moisture, warmth, frost,
dormancy, and elapsed suitable days matter; bone meal cannot skip biological
time. Player-planted fruit trees establish for one year.

| Animal | Gestation/incubation | Maturity | Recovery/product rule |
| --- | ---: | ---: | --- |
| Cow | 12 days | 28 days | Milk after calving, once per adequately fed/watered day |
| Sheep | 8 days | 20 days | Four fed/watered days to regrow wool |
| Chicken | 4 warm-nest days | 12 days | Daily eggs; only fertilized eggs hatch |
| Pig | 8 days | 20 days | Litters of 2–4 with food, water, recovery, and space |

Feed two mature adults nearby to begin pairing. Ordinary feeding does not
instantly mature juveniles or create babies. Stock a trough with feed and pot
water, or provide grazing and surface water. Place fertilized eggs on hay by
lit heat. Throwing ordinary eggs cannot create chicks.

Food spoils, tools/armor/garments wear, and unstored dropped materials weather.
Organic things left on the ground rot to plant fiber, and fiber and spoiled food
left out rot away entirely. Rock, ore, metal scrap and ceramic shards stay.
Splitting, storage transfers, cooking, and reload do not refresh old food.
Unlike freshness components prevent merging old and new stacks into a fresh one.
Stored unused durable materials remain stable.

## Predators

Wolves, brown bears, lions and polar bears have to eat, and behave accordingly.
Each carries the time of its last full meal. Fed, it leaves flocks and people
alone; once the meal has worn off it takes the nearest prey it can manage, eats
what it kills (the hide and bones are left, the meat is not) and is quiet again
for days. A hungry animal also comes to raw meat left lying, so a carcass or an
open larder draws them and a bait can hold one.

| Animal | Where | Size | A meal lasts | Takes | People |
| --- | --- | --- | ---: | --- | --- |
| Wolf | Taiga and forest, as before | 8 health, bite 4 | 3 days | Sheep, goats, pigs, chickens, rabbits; cattle and horses only as a pack of three | Only a pack of three, only someone alone, and only when starving or when the person is hurt or bleeding |
| Brown bear | Taiga, forest and mountain country | 40 health, bite 9; 1.1 m at the shoulder | 2 days | Sheep, goats, pigs, chickens, rabbits, cattle | When starving; and at once, fed or not, if walked up on within five blocks. Lies up while it freezes |
| Lion | Savanna, in groups of two to four | 28 health, bite 8; 1.2 m at the shoulder | 4 days | All stock, cattle and horses included | When hungry, by night; by day only when starving |
| Polar bear | Ice and snow, as before | 44 health, bite 10 | 5 days | All stock | Whenever hungry |

All of them run faster than a person can sprint, so the defences are the real
ones. None will come near open fire: a lit campfire or burning block within six
blocks protects stock and people alike, and so does a torch in the hand. None
will approach people standing together (two for wolves, three for bears and
lions, four for polar bears). Walls and fences keep them from stock they cannot
reach. Badly hurt (half its health for a wolf, a quarter for a brown bear) the
animal breaks off and runs for a minute rather than fight to the death. A
bite opens a bleeding wound, tears the cloth over it, and festers three times
as often as a blade cut. Tame dogs hunt nobody. Bears and lions drop hide and
bone.

## World rules

**Vanilla shortcuts stay shut.** The game's furnace, blast furnace and smoker
do no work, fed by hand or by hopper. The game's own recipes for tools, armour,
buckets, shears, shields and crossbows are struck out, and are neither listed
by `planning` nor accepted by `craft`. The mod's data is loaded above
NeoForge's, which ships its own versions of those recipes, so that they stay
struck out.

**What a client is told.** A player's client is sent that player's own body
every second. Of anyone else it is sent only what they wear, when that changes
or comes into view; another's needs, illness and skills are learned by
`/survival inspect`, within eight blocks in the same world.

**What lies on the ground.** Nothing dropped vanishes after five minutes; it
weathers. Rock, ore, coal, clay and brick lie where they fell. Organic litter
rots to fibre and the fibre to nothing; metal leaves scrap, fired clay its
sherds, and a stone tool the stone. A fertilized egg keeps only on its nest:
taken up, or left lying anywhere else, it is an egg like any other and rots.

**Death is permanent.** A player who dies does not return to the world. They
come back only as a spectator: they can watch, but they cannot touch a block,
use an item, run a command or speak in chat, and the simulation no longer
counts them. Leaving and rejoining changes nothing. What they had learned is
gone with them, their soldiers are released, and their place at a works, in a
gun crew or as a station's worker is given up. A creative or spectating
operator was never part of the simulation and is not ended by dying.
`/survival revive <players>` (operators) returns a dead player to the world as
a new person, in survival mode where they stand, with a fresh body and nothing
learned. Dead villagers, soldiers and animals leave the world as they always
did, and nothing keeps a place for them: a dead draught animal is out of the
harness and a dying animal neither mates, lays nor gives birth.

New terrain generates without structures, dungeons, or ruined portals. Natural
caves, trees, ores, clay, animals, and resource patches remain. Fantasy entities
are rejected from spawning/loading, and supernatural items/progression are
removed from gameplay. Natural equipment loses enchantments. Nether/End portal
progression is disabled.

Surface plants grow in coherent, habitat-limited stands rather than scattered
mixed seed piles. Rice needs warm wet ground, brambles belong to woodland, and
open-ground crops need exposed suitable soil. Apple trees form small groves.
Ordinary soil has very little loose stone; exposed rock has more.

Copper (malachite), tin (cassiterite), iron (hematite), and coal form finite
regional deposits, with rock hosts, mineral grades and barren layers. Granite,
limestone and sandstone form quarry bodies. Districts differ in their dominant
resource and some are barren, giving settlements reasons to share extraction,
transport and processing. This provides resource incentives; autonomous trade
or a currency economy is not included.

`/survival resources [radius]` lists nearby visible evidence. `/survival prospect`
inspects a nearby exposed face or a held mineral sample. These read actual
blocks/items and never disclose hidden generated reserves or award mining XP.
A block counts as seen when any face turned towards the eye can be reached
through open air; the survey lists the 64 nearest such blocks, nearest first.

All item definitions have localized names and icons: 16x16 icons for goods
and blocks, block-built models for the eleven vehicles and siege pieces;
flat tool icons are retained. The [reference audit](docs/superpowers/specs/2026-11-07-item-reference-audit.csv)
records Wikipedia identities and intentional generic materials.

Use a fresh world for a completely structure-free landscape. Existing generated
buildings and player construction are retained; the mod does not erase old
terrain. Definitions can extend `humanoids`, `forbidden_entities`, and
`forbidden_items` data tags for additional entity/content types.

## Industry and realistic production chains

Beyond the kiln, bloomery, forge, anvil and loom, nine **works** carry production
from the gunpowder age to the electronic one. A works holds a bulk ledger of
delivered materials, runs its selected recipe batch after batch, and needs every
input, qualified labour, and the physical plant its process really needs.
The process-by-process comparison with real practice, and the short list of
things that remain Minecraft conventions, is in the
[realism audit](docs/realism-audit.md).

| Works | Built from | What it does | Site must have |
| --- | --- | --- | --- |
| Workshop | planks, logs, cordage (grid) | Wheels, frames, harness, sleepers, gun carriages, muskets, cartridges, powder bags, hand-drawn wire, chimney blocks, lead chambers, cable ducts, cleanroom suits | Roof, 12 m2 of floor |
| Assembly yard | logs, planks, cordage (grid) | Many hands on one job: lime, brick and firebrick clamps, vehicles, ordnance, boilers, engines, generators, every other works and all heavy plant | Open ground |
| Nitre bed | planks, clay, dirt (grid) | 64 kg of rotting matter, ash and straw ripen for a year (112 days) | Roof |
| Powder mill | workshop | Saltpetre, charcoal and sulfur at 75:15:10 by weight | No naked flame within four blocks |
| Foundry | assembly yard (3,000 bricks) | Bulk ore calcining and smelting, crucible steel, pig iron, puddled iron, Bessemer steel, gun founding, cupellation, glass; furnace silicon and phosphorus by arc furnace | 12 firebrick, a 4-high chimney, 16 m2 of roofed floor; 24 and 8 with a blast for iron; a blowing engine for steel |
| Chemical works | assembly yard | Saltpetre, sulfur, coke and tar, wood spirit, alcohol, acids, soda, caustic, alumina, cryolite, rubber, oil refining; aluminium and tungsten by electrolysis; polysilicon | Roof, chimney, water, 16 m2; lead chambers for acids; a distillation column for oil |
| Machine shop | assembly yard, from four tonnes of cast iron and hand-made parts | Rolled plate, rail, machine and precision parts, wire, insulated wire, lenses, tyres, motors, pumps, filters, panels | Roof, 40 m2, and a stationary steam engine with boiler, flue and feed water |
| Electronics works | assembly yard | Valves, passives, packages, boards, crystal growth, wafers, masks, the four-mask MOS flow, chips, computers, servers | Roof, 16 m2, electric power; named equipment; a cleanroom for every wafer step |
| Power station | assembly yard | Supplies kilowatts along cable ducts | Generators, boiler, 6-high chimney, feed water, fuel |

**Time is man-hours.** Every recipe is rated in man-hours at 1,000 ticks to the
hour of the game clock: a wheel is 16, a cart 40 on top of its parts, a cannon
barrel 300, a steam engine 1,500, a locomotive 12,000, a computer 4,000 and a
server 30,000. Hands share the work up to the job's crew. `industryTimeScale`
in the server config scales all of it for servers that want a shorter road.

**Mass is conserved.** A six-pounder barrel is 400 kg of bronze, a bombard
1.5 tonnes of wrought iron, a boiler 1.2 tonnes of plate, a locomotive 14
tonnes of steel, 5 of plate and 3 of cast iron. A blast furnace charge is 3.6
tonnes of ore, 80 sacks of coke and lime for 2 tonnes of pig; a Bessemer
blow turns 5 tonnes into 4.5. A metre of rail is 31 kg. Copper is drawn 140 m
to the kilogram. No recipe turns out more metal than went in, and nothing
assembled weighs more than its parts (tested).

**The tree is era-gated and tested.** Hand, water and charcoal reach muskets,
cannon, acids, soda, glass and a first steam engine built from hammered plate
and cast iron, lubricated with tallow. Only the machine shop that engine
drives makes precision parts, rail, rolled plate, locomotives and dynamos.
Only a power station makes aluminium, furnace silicon, phosphorus, tungsten
and so valves. Only a cleanroom makes chips. Remove any link (pig iron, the
machine shop, the power station, filters, fluorite, salt, bauxite, wolframite,
borax, silver, rubber, lenses, lead chambers, coke, glass) and a computer
cannot be built.

**Site rules.** A works checks its surroundings every five seconds and reports
what is missing in `/survival works status`; asking reads the last check and
does not run the works. A place at a job is given up to the next comer by
walking more than eight blocks away. Hunger, skill and the risk of injury fall
only on hands that are at the work when it is done, and only for seconds in
which fuel and power let it be done. A works holding more than fifty-four
stacks of any one thing, or a finished vehicle, cannot be pulled down until it
is emptied. Working floor is counted in roofed
square metres around the works. Chimney stacks must be open to the sky. Fuel
burns at real rates: a furnace hearth 25 kg an hour, a still 12, the boiler of
a shop engine 100. A generator turned by its own stationary engine gives 35 kW;
each job draws its real load (an arc furnace 400 kW, twelve megawatt-hours to
the tonne of silicon; a potline 25 kWh to the kilogram of aluminium), the station burns 2.5 kg of coal per
kilowatt-hour, and a cable run loses two percent per hundred metres, up to a
mile.

**Cleanrooms.** The electronics works must stand in a sealed room at least
5 x 5 x 3 inside, whose every inward-facing block is cleanroom panel, glass, a
filter unit, a cable duct or an iron door, with one ceiling filter unit per four
square blocks of floor. Cleanliness rises only while the room is sealed and
powered, the door is shut and everyone inside wears a cleanroom suit; it falls
fast otherwise and returns to zero on any breach. Wafer work needs 95%, and
at dicing the yield runs from half the dice at 95% to all of them at 100%.

**Chips are made the way chips were made.** A boule is pulled from polysilicon
and sawn to 400 wafers. Lots of 25 wafers go through field oxidation, boron
diffusion, gate oxidation, contact cut and aluminium metallisation, each
photo step exposing through its own emulsion mask (silver halide on glass,
used up by one lot). A good wafer dices to 160 working dice; sixteen dice,
sixteen ceramic packages and 256 aluminium bond wires make sixteen 16-pin
logic packages. A computer takes 2,800 of them, 120 boards, 8 km of hook-up
wire, 20 kg of solder and 6,000 passives; a server 20,000 chips (320,000
bonds), 900 boards and 60 km of wire.

**Industry injures.** Finished batches at foundries, chemical works and machine
shops carry a skill-dependent chance of burns, acid scalds and poisoning, or a
lost finger.

Right-click a works with a material to deliver the stack (sneak to deliver every
matching stack you carry), with fuel to stoke it, and with an empty hand to
collect finished goods or to take the job. Sneak with an empty hand to recover
unused stock. `/survival works recipes|set|info|bill|deposit|fuel|collect|withdraw|operate|leave|status`
does the same for players, and `/survival control <actor> works ...` for controllers.
`/survival works bill <recipe>` expands a product through every intermediate
down to what is mined, grown or gathered.

Galena and fluorite (with limestone-hosted copper), pyrite, rock salt, tar sand
and borax (with coal measures), wolframite (with tin lodes) and bauxite (with
ironstone) are accessory minerals in newly generated chunks. A computer within
eight blocks of a gun halves its dispersion as a fire-control table. Data packs
may retune or add recipes in the `industry` group of `content_rules.json`.

## Military service, arms and orders

Any humanoid may command. `/survival military recruit <target>` enlists a
volunteer who accepts at least four rations of sound food from the commander's
hand as bounty (players accept by crouching). `conscript <target>` presses an
unarmed subject at weapon point; an armed subject yields only to a commander
with two armed soldiers at their back. Volunteers start at morale 70,
conscripts at 35.

Soldiers fight with what they are handed. `equip <target>`, or sneak-interacting
with your own soldier while holding kit, puts armour on, a shield in the off
hand and anything else in the weapon hand; food, arrows, paper cartridges and
spare flints go into the pack. A replaced item comes back to the giver, and
issued kit drops on death. What a soldier carried in the pack (arrows,
cartridges, flints) falls where he stands when he dies or leaves the service.
A ration is four points of nutrition, whatever the food is. Soldiers draw
rations from any supply vehicle within eight blocks, refill a canteen at water,
and break ranks to forage when either runs out.

`/survival military order <soldiers> ...`: `hold`, `follow`, `retreat`,
`move <pos> [line|column|square]`, `guard <pos> [radius]`, `attack|charge|fire <target>`,
`mount <animal or vehicle>`, `dismount`, `crew <siege piece>`, `drive <vehicle> <pos>`,
`work <pos>` (labour at a works) and `bombard <piece> <pos>`. `<soldiers>` is
one soldier's name or UUID, or `all` for every soldier of yours within
ninety-six blocks; a selector such as `@e` needs operator rights. A soldier
mounts only a tamed, saddled, riderless horse or a vehicle with a free seat.
`bombard` is given standing at the piece, as `siege lay` is. A soldier who
cannot reach the place he was sent holds where he is and says so. `hostile <target>`
marks another commander's side for guards, who keep the list while their
commander is away; `roster` lists morale, orders, kit and supply. A commander's
own blow does not make him his men's enemy. Men whose commander died while
they were out of the loaded world are released when they are next seen. A
player who enlisted leaves with `/survival military resign`. Every command exists under `/survival control <actor> military ...`,
and server code may call `Military.recruit/conscript/equip/order`.

- Melee uses the weapon's real damage and speed, the soldier's manipulation and
  melee skill, and the defender's armour. A shield-bearer facing the blow may
  parry it. A spear couched from a moving horse strikes with the horse's speed;
  cavalry that rides onto levelled spears takes the points first.
- Bows (24 blocks) and crossbows (40, slower, flatter) loose real arrows from
  the quiver, which can be recovered. An archer ordered to `fire` with an empty
  quiver holds; ordered to `attack`, he closes with whatever is in hand.
- The **musket** is a flintlock. A cartridge is a 31 g ball over six drams of
  powder, ninety-six to the kilogram. Loading takes eighteen seconds for a drilled man
  and thirty for a raw recruit; players hold use through the drill and use
  again to fire. It misfires one shot in ten, one in two in the rain, and more
  as it fouls: every round dirties the barrel, slows the next load by 4% and
  adds a point of misfire, until at forty rounds it will not load at all.
  Sneak and use to scour it (a minute); soldiers stop to clean at thirty. A
  flint is good for forty sparks, then wants a spare. It is a volley weapon
  beyond thirty blocks and hits hard through armour and shields. Gunfire
  stampedes loose animals.
- Morale falls with wounds, hunger, thirst, dead comrades and incoming shot, and
  recovers when fed and led. Below 20 a soldier breaks and runs, rallying only
  near the commander; unwatched conscripts and hopeless volunteers desert.
- Riders are thrown when wounded unless practised. Horses must be tamed and
  saddled; saddles and harness are made at a workshop.

## Transport and logistics

| Vehicle | Moves by | Payload | Top speed | Notes |
| --- | --- | --- | --- | --- |
| Cart | 1 draught animal | 500 kg | 10 km/h | |
| Wagon | 2-4 | 2,000 kg | 9 km/h (10 behind four horses) | Wants three horses fully laden |
| Chariot | 2 horses | 100 kg and a fighter | 30 km/h | Crippled off a made road; the fighter shoots from the car |
| Motor truck | combustion engine | 3,000 kg | 25 km/h | A litre of petrol every 3 km (alcohol every 2); bogs down off the road |
| Locomotive | steam | bunker 5,000 kg, 3,000 L | 43 km/h | Rails only |
| Goods wagon / carriage | hauled | 10,000 kg / 8 passengers | | Up to twelve cars per engine |

Cargo goes by weight: a log is 600 kg, a sack of coal 25, a sleeper 85, a
metre of rail 31. A horse starts about 1,200 kg gross on a made road, so a
loaded vehicle may need more animals than its minimum, and tells you how many.
Teams eat: every animal in harness takes a kilogram of fodder from the load
ten times a day, by weight (hay, wheat, maize, roots, apples), or grazes if
halted on grass;
an unfed team weakens and finally starves. Bearings want a litre of tallow or
oil every fifty kilometres and burn out without it.

Nothing starts or stops at once. A team or a lorry gains half a metre per
second each second and takes its real distance to stop, and a driver ordered
to a place slows in time to stop there. Pace depends on the team, the load and
the ground: made roads (path, gravel, cobble, brick, planks) carry wheels at
full speed, turf slows them, and sand, snow, mud and plough bog them down.

Vehicles are assembled at a yard and roll out as entities. Lead a tamed adult
horse, mule, donkey, camel or ox to a vehicle and click it to harness (oxen
pull more and walk slower). Click to board; the first seat drives with the
movement keys. Sneak-click with goods to load, with an empty hand to unload.
`/survival vehicle status|load|unload|deliver|pickup|hitch|unhitch|board|leave|drive|halt|couple|uncouple`
(and the `control` forms) cover everything; `deliver` and `pickup` move bulk
cargo straight between a vehicle and a works drawn up within six blocks.
Bulk cargo is counted by kind and comes off as new stock, so spoiled food and
filled or sealed vessels are not taken aboard. `halt` is for those aboard: a
road vehicle stops being driven, and a train under way is braked to a stand
by its driver. A train ordered to a place stops abreast of it, measured along
the line, and stops at the last rail if the place lies beyond it; driven by
hand it runs off the end.

**Railways** are laid, not placed. Each metre of line takes 2 metres of steel
rail, 2 sleepers, 2 cubic metres of gravel ballast and 8 spikes, a firm bed
three blocks wide, and two and a half man-hours (use a steel rail on the
ground, or `/survival lay_track <pos>`; materials may sit in a wagon
alongside). A line climbs no more than one block in thirty.

A locomotive is a twenty-four tonne tank engine. A cold boiler takes two hours'
firing, with someone on the footplate, before it will move, and burns ten
kilograms of coal an hour standing in steam. Running, it burns a 25 kg sack
every 1.8 km and boils seven litres of water for each kilogram, refilling at
60 L a second when stopped within four blocks of water. It gathers way at a
fifth of a metre per second each second (less with a heavy train), needs 150 m
to stop from full speed, derails on a curve taken above 11 km/h, and runs off
the end of the line if nobody brakes.

## Siege warfare

| Piece | Round | Crew | Team | Character |
| --- | --- | --- | --- | --- |
| Cannon | 3 kg round shot + half a kilogram of powder | 8 | 4-6 | Bronze six-pounder, 440 m/s, two rounds a minute; flat to a hundred metres, about 2.2 km at full elevation; breaks one masonry block a shot and batters the next |
| Bombard | 40 kg stone + 10 kg powder | 8 | 6-10 | 220 m/s; a few shots a day, smashes through several blocks, may burst |
| Trebuchet | 40 kg stone, no powder | 12 | built in place | 50 m/s release in a high arc between about 95 and 230 m; one stone an hour |
| Field gun | 8 kg shell + 2 kg powder | 6 | 4-6 | Rifled steel breech-loader of the 1870s, 455 m/s, a round in thirty seconds, about 6 km at full elevation |

Shot flies at its real speed under 9.8 m/s2 and air drag proportional to the
square of speed, so a cannon ball crosses a siege line in a fraction of a
second and still falls far short of its vacuum range. A shot that leaves the
loaded world is lost, so these reaches are never seen in full. Laying a piece solves
that trajectory within the mounting's limits of elevation.

Put ammunition in the piece's store, man it (`/survival siege man`, a `crew`
order, or the gunner's seat), and the crew loads at a pace set by their number
and gunnery. A seated player aims with the view and jumps to fire;
`/survival siege fire <pos>` or a `bombard` order lays the piece on a point and
keeps firing while ammunition lasts. Damage to walls accumulates: timber gives
way quickly, masonry after repeated hits, and earth banks absorb cannon shot
that would break stone. Shot ploughs through anything alive in its path and
shakes the morale of men nearby. Powder in a wrecked vehicle explodes.

## Bot and script interface

There are two ways to drive an actor. A **protocol bot** joins as a player: the `bots/` folder holds a
[mineflayer](https://github.com/PrismarineJS/mineflayer) package that carries a bot through the NeoForge
login, teaches it the names of the mod's blocks, items and animals, and gives it `bot.survival` for its
own body, climate and actions. Such a bot has no operator rights and lives under every rule a person
does. See [bots/README.md](bots/README.md). `/survival export_registry` (operators) writes the id table
those bots read. The other way is the **controller commands** below, which steer a body that already
exists on the server.

Ordinary players operate themselves. Operator controller commands may select a
living humanoid, including a fake player or an entity enrolled with
`/survival enroll <targets>`:

```text
survival query <actor>
survival control <actor> eat
survival control <actor> drink
survival control <actor> social <companion>
survival control <actor> treat <patient>
survival control <actor> feed <companion>
survival control <actor> clothe <companion>
survival control <actor> undress <companion>
survival control <actor> mend <companion>
survival control <actor> vessel <operation>
survival control <actor> produce <recipe>
survival control <actor> fuel
survival control <actor> collect
survival control <actor> station
survival control <actor> planning
survival control <actor> recipeinfo <recipe>
survival control <actor> gather <x> <y> <z>
survival control <actor> forage <x> <y> <z>
survival control <actor> hunt <animal>
survival control <actor> mine <x> <y> <z>
survival control <actor> till <x> <y> <z>
survival control <actor> sow <x> <y> <z>
survival control <actor> harvest <x> <y> <z>
survival control <actor> craft <recipe>
survival control <actor> resources [radius]
survival control <actor> prospect [x y z]
survival control <actor> jobs
survival control <actor> cancel
survival control <actor> livestock <animal>
survival control <actor> nest <x> <y> <z>
survival control <actor> trough <x> <y> <z>
survival control <actor> lay_track <x> <y> <z>
survival control <actor> works <operation>
survival control <actor> vehicle <operation>
survival control <actor> military <operation>
survival control <actor> siege <operation>
```

`query` returns JSON with needs, temperature, health, capacities, anatomy,
clothing, conditions, all sixteen skills, XP, passions, daily learning, decay,
military service and calendar (schema 6). Recipe planning exposes learning weights and estimated work
time at the current skill/capacity. Normal action commands validate range,
held/offhand supplies, physiological capacity, and processing time; they do not
refill needs or grant completed output for free. Operator `debug` and `injure`
commands exist for scenario setup and diagnostics.

The same work verbs are available directly under `/survival` for a player's
own actions. `planning` returns paged crop windows and recipe requirements;
`recipeinfo` pages ingredient alternatives. Work reserves real supplies from
hands, inventory, nearby containers, and ground items. It pauses or cancels when
its actor, tools, range, permissions, or target no longer qualify. Crafting a
3×3 recipe requires a nearby crafting table. Relogging and sleeping grant no
manual labor: a sleeper starts no work, and work in hand waits for waking.
`cancel` returns reserved materials. Mining needs a block with at least one
face that is not hard against solid rock; what is buried has to be dug to. A
job is held to its tool only where the tool does the work (tilling, or a block
that needs a tool or is sped by one): putting that tool away cancels it, wear
on it does not. `recipeinfo <recipe> [alternativeOffset]` takes names exactly
as `planning` prints them, colon included.

Trusted server-side code can call:

```java
SurvivalApi.isHumanoid(entity);
SurvivalApi.inspect(entity);
SurvivalApi.act(actor, "treat", patient); // ActionResult(success, message)
SurvivalApi.act(actor, "eat", null, InteractionHand.OFF_HAND);
SurvivalApi.work(actor, "produce", "bronze");
SurvivalApi.vessel(actor, "boil");
SurvivalApi.calendar(serverLevel);
WorldActions.planning(actor, 0);
WorldActions.forage(actor, patchPos);
WorldActions.hunt(actor, animal);
```

`Production.RECIPES` exposes exact input/output and timing requirements.
`Husbandry.action(actor, animal)` exposes shared livestock interaction. Mutation
and persistence APIs run on the logical server. HUD packets require a negotiated
client channel; headless controllers still receive all simulation consequences.

Default tuning lives in the datapack resources
`data/spawn_9b5fbb9/survival/physiology.json` and `content_rules.json`.
Override them in a datapack and `/reload` to change physiology thresholds/rates,
pathogens, crop schedules, livestock durations, decay, and production recipes.
Definitions are validated before replacing active groups. Season length,
recreation quota, and overall needs rate also have local mod configuration.

## Development and validation

```text
qube build
qube test
qube server up --check
qube server up --stage
qube shot --dedicated --new-session --segment @validation/realism-skills.json
qube shot --dedicated --segment @validation/realism-resources.json
qube shot --dedicated --segment @validation/realism-items.json
qube shot --dedicated --segment @validation/realism-practice.json
qube shot --dedicated --fresh --segment @validation/realism-reconnect.json
qube shot --dedicated --replay
```

The industry, transport and warfare scenes are `validation/war-*.json`
(vehicles, artillery, railway, items and works, a crewed cannon breaching a
wall, a train and a horse-drawn wagon under way, and a recruited swordsman and
musketeer in action). `tools/generate-industry-data.py` regenerates their data
files and track model, `tools/generate-vehicle-models.py` the true-scale vehicle
models and their layout table, and `tools/render-industry-icons.py` the icons.
`validation/war-materials.json` shows every later material, ore and plant block;
`validation/war-cleanroom.json` builds a fab with its own power station, pumps it
clean, packages chips and then breaches the wall.

The realism implementation and final checks are recorded in the
[realism validation report](docs/superpowers/plans/2026-11-07-realism-validation.md).
Earlier six-skill replay evidence is preserved in the
[previous validation report](docs/superpowers/plans/2026-11-07-skills-validation.md).

The [original survival validation report](docs/superpowers/plans/validation-report.md)
also records the earlier two-client survival replay, server persistence checks,
and a natural-world audit of 2,550 saved chunks.

`qube build` also runs dependency-free simulation checks through the Gradle
`survivalSimulation` task. GameTests verify actual entities, inventories,
processing, crops, vessels, controller actions, and conditions. Textures are
created through `qube texture`; every item binds its model and modern item
definition. Open Qube contact sheets before making visual claims.

The approved design, implementation plan, execution log, and review reports live
under `docs/superpowers/`.
