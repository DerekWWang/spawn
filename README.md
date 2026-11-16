# Spawn — natural-world survival

A Minecraft 26.3 / NeoForge 26.3.0.77 survival overhaul. Mod ID: `spawn_9b5fbb9`.

## Tectonic geography and regional climate

The explicit Tectonic profile integrates **Tectonic 3.0.32 + Lithostitched 2.0.6**
with terrain-relative mineral districts, regional weather, persistent soil water,
physical irrigation, wind exposure, and climate-dependent pasture. Players and
enrolled agents share the same environment; each client sees its own local rain
or snow. Backstories and autonomous settlement/trading AI are outside this milestone.

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
4. Tend a block of clay in a fueled kiln to make a jar. Collect surface water
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
start neutral and can be assigned explicitly; there are no backstories or
permanent profession classes. Learning survives respawn and existing six-skill
saves migrate to the new scale.

| Skill | Default requirements and effects |
| --- | --- |
| Pottery | Fueled kiln and 25% manipulation; effective level 5 makes standard jars, 12 makes fine. |
| Smithing | Copper equipment needs level 4, bronze 8, iron 12; iron armor needs 14. Real blanks, fuel and anvil work remain required. |
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

Shift-use a pot on a block for its context action. Pots hold two, three, or four
food stacks according to quality and cannot nest containers. Transport keeps
their contents and freshness.
Worn-out pottery spills stored food and leaves shards.

Right-click garments to wear them. `/survival dye <0–3>` consumes a held natural
dye for the shirt, pants, jacket, or shoes respectively. Garment wear and color
survive swapping and ordinary inventory/death rules.

Bandages stop excessive bleeding. Painkillers temporarily relieve pain without
repairing injuries. Antitoxin treats poisoning, antiseptic treats infected wounds,
and antibiotic treats systemic/respiratory infection. Destroyed eyes remain
blind for that life. A recovered body develops strain-specific immunity. A new
body on respawn resets bodily injuries and immunity.

Right-click treatment to use it; interact with a nearby humanoid while holding
medicine to treat them. Sneak-interact with food or clothing to feed or clothe a
companion. Supplies are consumed. Eating follows the item's actual consumption
time and remainder rules. Players must crouch to accept clothing from another
actor, and companion clothing cannot replace an occupied slot.

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
Splitting, storage transfers, cooking, and reload do not refresh old food.
Unlike freshness components prevent merging old and new stacks into a fresh one.
Stored unused durable materials remain stable.

## World rules

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

All item definitions have localized names and icons: 16x16 icons for goods
and blocks, block-built models for the eleven vehicles and siege pieces;
flat tool icons are retained. The [reference audit](docs/superpowers/specs/2026-11-07-item-reference-audit.csv)
records Wikipedia identities and intentional generic materials.

Use a fresh world for a completely structure-free landscape. Existing generated
buildings and player construction are retained; the mod does not erase old
terrain. Definitions can extend `humanoids`, `forbidden_entities`, and
`forbidden_items` data tags for additional entity/content types.

## Industry and realistic production chains

Beyond the kiln, bloomery, forge, anvil and loom, eight **works** carry
production into the gunpowder and machine ages. A works holds a bulk ledger of
delivered materials (industrial quantities never fit in slots), runs its selected
recipe batch after batch, and needs every input, heat or power, and qualified
labour to do so. Nothing is produced without its full bill of materials.

| Works | Built from | What it does |
| --- | --- | --- |
| Workshop | planks, logs, cordage (grid) | Wheels, vehicle frames, harness, saddles, sleepers, gun carriages, musket stocks and final musket assembly, paper cartridges, powder charges |
| Assembly yard | logs, planks, cordage (grid) | Many hands on one job: carts, wagons, chariots, siege engines, boilers, engines, locomotives, rolling stock, and the other works |
| Nitre bed | planks, clay, dirt (grid) | Spoiled food, wood ash and straw mature unattended for five days into nitre earth |
| Powder mill | workshop | Saltpetre, charcoal and sulfur at 15:3:2. A naked flame within four blocks of a working mill destroys it |
| Foundry | assembly yard | Steel, bronze cannon barrels, bombard barrels, furnace silicon (heated) |
| Chemical works | assembly yard | Saltpetre leaching, sulfur from pyrite, coke and coal tar, fermentation and distillation of fuel alcohol, acid, resin, photoresist, polysilicon (heated) |
| Machine shop | assembly yard (needs a steam engine) | Plate, machine parts, rail, wire drawing, shells (steam powered: burns fuel) |
| Electronics works | assembly yard (needs a steam engine and dynamos) | Crystal growth, wafers, lithography, chips, circuit boards, computers and servers (powered) |

Right-click a works with a material to deliver the stack (sneak to deliver every
matching stack you carry), with fuel to stoke it, and with an empty hand to
collect finished goods or to take the job. Sneak with an empty hand to recover
unused stock. `/survival works recipes|set|info|bill|deposit|fuel|collect|withdraw|operate|leave|status`
does the same for players, and `/survival control <actor> works ...` for controllers.
`/survival works bill <recipe>` expands a product through every intermediate
down to what is mined, grown or gathered.

Quantities are deliberately industrial. A computer takes 1,200 integrated
circuits, a server 4,800; each chip needs a die cut from a patterned wafer, a
ceramic package and forty bond wires, so a server's chips alone consume 192,000
fine wires (drawn from 48,000 copper wires), plus 30,000 more for its wiring,
160 circuit boards, 8,000 solder joints, steel, transformers and machine parts.
Hand routes (wire drawn at the anvil, machine parts filed from forged blanks)
exist so the first machine shop can be built; after that, scale needs powered
works, several crews, and transport. Galena (with limestone-hosted copper) and
pyrite (in coal seams) are new accessory minerals for lead and sulfur. A
computer within eight blocks of a gun halves its dispersion as a fire-control
table. Data packs may retune or add recipes in the `industry` group of
`content_rules.json`.

## Military service, arms and orders

Any humanoid may command. `/survival military recruit <target>` enlists a
volunteer who accepts at least four rations of sound food from the commander's
hand as bounty (players accept by crouching). `conscript <target>` presses an
unarmed subject at weapon point; an armed subject yields only to a commander
with two armed soldiers at their back. Volunteers start at morale 70,
conscripts at 35.

Soldiers fight with what they are handed. `equip <target>`, or sneak-interacting
with your own soldier while holding kit, puts armour on, a shield in the off
hand and anything else in the weapon hand; food, arrows and paper cartridges go
into the pack. A replaced item comes back to the giver, and issued kit drops on
death. Soldiers draw rations from any supply vehicle within eight blocks, refill
a canteen at water, and break ranks to forage when both run out.

`/survival military order <soldiers> ...`: `hold`, `follow`, `retreat`,
`move <pos> [line|column|square]`, `guard <pos> [radius]`, `attack|charge|fire <target>`,
`mount <animal or vehicle>`, `dismount`, `crew <siege piece>`, `drive <vehicle> <pos>`,
`work <pos>` (labour at a works) and `bombard <piece> <pos>`. `hostile <target>`
marks another commander's side for guards; `roster` lists morale, orders, kit
and supply. Every command exists under `/survival control <actor> military ...`,
and server code may call `Military.recruit/conscript/equip/order`.

- Melee uses the weapon's real damage and speed, the soldier's manipulation and
  melee skill, and the defender's armour. A shield-bearer facing the blow may
  parry it. A spear couched from a moving horse strikes with the horse's speed;
  cavalry that rides onto levelled spears takes the points first.
- Bows (24 blocks) and crossbows (40, slower, flatter) loose real arrows from
  the quiver, which can be recovered. An archer ordered to `fire` with an empty
  quiver holds; ordered to `attack`, he closes with whatever is in hand.
- The **musket** is a flintlock. Loading takes a paper cartridge and about
  twenty seconds for a raw recruit, twelve for a drilled one; players hold use
  through the drill and use again to fire. It misfires one shot in ten, one in
  two in the rain, is a volley weapon beyond thirty blocks, and hits hard
  through armour and shields. Gunfire stampedes loose animals.
- Morale falls with wounds, hunger, thirst, dead comrades and incoming shot, and
  recovers when fed and led. Below 20 a soldier breaks and runs, rallying only
  near the commander; unwatched conscripts and hopeless volunteers desert.
- Riders are thrown when wounded unless practised. Horses must be tamed and
  saddled; saddles and harness are made at a workshop.

## Transport and logistics

| Vehicle | Moves by | Crew and load | Notes |
| --- | --- | --- | --- |
| Cart | 1 draught animal | driver, 576 units | |
| Wagon | 2–4 | driver and mate, 1,728 | The supply train of an army |
| Chariot | 2 horses | driver and one fighter | Twice a wagon's pace on a made road, crippled off it; the fighter shoots from the car |
| Motor truck | combustion engine | 2, 3,456 | Burns fuel alcohol; bogs down off the road |
| Locomotive | steam | 2, tender 512 | Rails only; needs coal, coke or charcoal and boiler water |
| Goods wagon / carriage | hauled | 6,912 units / 8 passengers | Up to twelve cars per engine |

Vehicles are assembled at a yard from frames, wheels, harness, engines and plate
and roll out as entities. Lead a tamed adult horse, mule, donkey, camel or ox
to a vehicle and click it to harness (oxen pull more and walk slower). Click to
board; the first seat drives with the movement keys. Sneak-click with goods to
load, with an empty hand to unload. Pace depends on the team, the load and the
ground: made roads (path, gravel, cobble, brick, planks) carry wheels at full
speed, turf slows them, and sand, snow, mud and plough bog them down.
`/survival vehicle status|load|unload|deliver|pickup|hitch|unhitch|board|leave|drive|halt|couple|uncouple`
(and the `control` forms) cover everything; `deliver` and `pickup` move bulk
cargo straight between a vehicle and a works drawn up within six blocks.

**Railways** are laid, not placed. Each metre of line takes 2 steel rails,
2 sleepers, 2 gravel ballast and 4 spikes, a firm bed three blocks wide, and
400 base work ticks of construction labour (use a steel rail on the ground, or
`/survival lay_track <pos>`; materials may sit in a wagon alongside). A line
rises one block only after a level run. An engine needs fuel in its tender and
takes on water when stopped within four blocks of it; heavier trains and
grades are slower, and a curve taken above half speed derails the train.

## Siege warfare

| Piece | Ammunition | Crew | Character |
| --- | --- | --- | --- |
| Cannon | round shot + 1 powder charge | 3 | Flat direct fire to ~320 blocks; breaks one masonry block a shot and batters the next |
| Bombard | stone shot + 3 charges | 4 | Four minutes to load, smashes through several blocks, may burst |
| Trebuchet | stone shot, no powder | 4 | Built where it shoots; lobs in a high arc between ~45 and ~105 blocks |
| Field gun | explosive shell or round shot + 1 charge | 3 | Rifled steel breech-loader; long range, bursting shell |

Put ammunition in the piece's store, man it (`/survival siege man`, a `crew`
order, or the gunner's seat), and the crew loads at a pace set by their number
and gunnery. A seated player aims with the view and jumps to fire;
`/survival siege fire <pos>` or a `bombard` order lays the piece on a point and
keeps firing while ammunition lasts. Damage to walls accumulates: timber gives
way quickly, masonry after repeated hits, and earth banks absorb cannon shot
that would break stone. Shot ploughs through anything alive in its path and
shakes the morale of men nearby. Powder in a wrecked vehicle explodes.

## Bot and script interface

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
manual labor. `cancel` returns reserved materials.

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
files, track model and vehicle models; `tools/render-industry-icons.py` the icons.

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
