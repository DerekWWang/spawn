# Spawn — natural-world survival

A Minecraft 26.3 / NeoForge 26.3.0.77 survival overhaul. Mod ID: `spawn_9b5fbb9`.

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

1. Gather loose stones/sticks and wild fiber/seed plants. Natural patches supply
   early food and seeds without villages or hostile drops.
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
manipulation capacity. One actor can perform one manual job at a time across
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

Every humanoid starts as a novice in six independent skills. Completed useful
work grants experience in the skill used; rejected actions, cancellations,
waiting, and relogging grant none. Later levels require more practice. There
are no permanent profession classes: individuals specialize through what they do.
Learning survives respawn while bodily injuries reset.

| Skill | Default requirements and effects |
| --- | --- |
| Pottery | Clay block + fueled kiln, with at least 25% manipulation. Effective scores below 25 make rough jars, 25 makes standard, and 60 makes fine. |
| Smithing | Bloomery → forge blanks → stone anvil. Copper tools require 20, bronze 40, iron 60; metal armor 60, iron armor 70. Useful novice rivet/blank/bloom work builds practice. |
| Weaving | Loom work spins any wool color, weaves cloth, and sews garments. Pants require 10, jackets 20; simple garments and cloth train novices. |
| Farming | Tilling, sowing, and harvesting develop skill and improve manual work speed. Skill does not skip crop seasons or biological growth time. |
| Foraging | Gathering wild patches and fruit develops more efficient field work. Actual resources are still exhausted/harvested. |
| Hunting | Successful real animal kills develop hunting. Weapon reach, sight, actual pursuit, wear, hunger, and physical capacity still matter. |

Learned skill is separate from **manipulation**, mental capacity, and movement.
An expert can still be physically unable to work. Pottery quality uses effective
skill after current physical/mental limits. Rough/standard/fine jars hold 2/3/4
food stacks or water portions; existing unmarked jars keep their previous fine
quality. A loom consumes four yarn or three cordage per cloth, then 4 cloth for
a shirt/pants, 6 for a jacket, or 2 for shoes, plus the binding supply. Looms and
anvils use manual work without fuel; heated stages consume fuel while working.

Press **H** for levels and next-level progress, or `/survival skills` for XP.
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

Use a fresh world for a completely structure-free landscape. Existing generated
buildings and player construction are retained; the mod does not erase old
terrain. Definitions can extend `humanoids`, `forbidden_entities`, and
`forbidden_items` data tags for additional entity/content types.

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
survival control <actor> jobs
survival control <actor> cancel
survival control <actor> livestock <animal>
survival control <actor> nest <x> <y> <z>
survival control <actor> trough <x> <y> <z>
```

`query` returns JSON with needs, temperature, health, capacities, anatomy,
clothing, conditions, skill levels/XP/progress, and calendar. Normal action commands validate range,
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
qube shot --dedicated --new-session --segment @validation/skills-workshop.json
qube shot --dedicated --segment @validation/skills-fieldwork.json
qube shot --dedicated --segment @validation/skills-reconnect.json
qube shot --dedicated --replay
```

The skills extension passes 39 pure simulation assertions and all 91 registered
GameTests (90 mod tests plus the harness check), including native/controller
action parity, skill gates, complete metalworking, jar quality, loom costs,
and input/experience conservation. Dedicated workshop and fieldwork captures
exercise a real client, a headless player, and NPC artisans/hunters. Reconnecting
retained all six skills and actual earned foraging experience. Evidence and
limits are in [the skills validation report](docs/superpowers/plans/2026-11-07-skills-validation.md).

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
