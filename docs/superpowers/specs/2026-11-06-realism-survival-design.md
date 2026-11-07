# Realism survival overhaul

Status: proposed design for user review; gameplay implementation has not started.

## Intent and repository baseline

Turn Spawn into a natural-world survival simulation. Daily recreation, food,
water, temperature, bodily injuries, farming, livestock, clothing, and production
must influence one another and create reasons to plan ahead.

The repository currently contains the NeoForge starter mod, example items and
block, a config example, and two arithmetic GameTests. It targets Minecraft 26.3,
NeoForge 26.3.0.77, and Java 25. None of the requested gameplay systems exist yet.
The mod ID remains `spawn_9b5fbb9`.

This is one coordinated overhaul delivered in dependency-ordered subsystems.
The order below does not remove later requirements from scope.

## Proposed interpretation

- Hours and days mean Minecraft time. At the default day length, 4.5 game hours
  are 4,500 ticks, or 3 minutes 45 seconds at 20 ticks per second.
- Four seasons last 28 game days each. Biological and manufacturing durations
  are compressed game schedules, configurable by the server, rather than
  literal real-world months.
- All survival/adventure players use the simulation. Creative and spectator
  players have their needs paused and are exempt from physiological damage.
- A data-driven humanoid category includes players, villagers, wandering
  traders, pillagers, and vindicators. Fantasy humanoids are removed by the
  world rules. Other mods can explicitly add eligible entity types.
- Humanoid means a humanlike entity; ordinary animals retain normal health and
  receive the husbandry system rather than human recreation or clothing needs.
- Clothing occupies four separate slots beneath the existing armor slots.
  "Armor above armor" is interpreted as armor above clothing.
- New humanoids start with empty clothing and armor slots. Player skin artwork
  is unchanged. Existing equipment is retained on an existing save; death follows
  the world's item-drop/keep-inventory rules rather than deleting equipment.
- All generated structures are disabled in new terrain. Already generated
  buildings cannot be reliably distinguished from player construction, so this
  design does not automatically erase existing blocks. A fresh world gives the
  complete structure-free result.

## Approach and boundaries

Recommended: a shared server-authoritative simulation with small modules for
needs, health, natural-world rules, agriculture, husbandry, and production.
Players and NPCs use the same rules; their input adapters differ. This costs more
initial integration work but avoids separate player and NPC balance models.

A player-only implementation would be faster but would miss the humanoid
requirement. A collection of independent status effects would be simpler but
would not support persistent injuries, reliable item aging, or shared NPC work
penalties. Neither is the selected approach.

Numerical values below are proposed gameplay defaults. Server configuration
controls rates, thresholds, season length, and recipe durations. Data tags and
definitions control membership, crop schedules, pathogens, insulation, and
material recipes. Registry IDs remain present for save compatibility when an
item/entity is prohibited; prohibition means preventing gameplay use/existence,
not deleting Minecraft registry entries.

## 1. State, time, and visibility

Each humanoid has persisted, versioned state containing recreation, hunger,
thirst, body temperature, anatomy, active conditions, immunity, clothing, and
accumulated fractional changes. Recreation/hunger/thirst use 0–100; body
temperature uses degrees Celsius; anatomy parts use 0–100 condition.

The server advances physiology every 20 ticks using the elapsed simulation
interval. Actual attacks, drinking, treatment, harvests, and crafting are handled
at their authoritative event. Fractional changes are retained; a low tick rate
does not multiply costs or erase small changes. One overworld calendar drives
all dimensions and advances through skipped sleep time once. `/time set` cannot
rewind ages, disease, food expiry, or the daily recreation requirement.

Physiology pauses while a player is disconnected or an NPC is unloaded. Crops,
livestock age/gestation, and stored items catch up against the persisted calendar
when loaded. Catch-up does not create births without space/food, reproduce
multiple generations, or apply retroactive damage to an unloaded entity.
Newborns start aging at their scheduled birth time; deferred births are limited
to one pending litter for each pregnancy.

Player hunger has one source of truth integrated with vanilla food/saturation.
The vanilla hunger display mirrors it, and exhaustion is replaced or accounted
for exactly once. Food must never grant nutrition twice. NPC hunger uses the
same nutrition and expenditure rules without vanilla `FoodData`.

The HUD displays Recreation, Hunger, Thirst, Temperature, and Health with values
and readable condition warnings. A health/clothing screen shows the three
health categories, body diagram, movement/manipulation/mental capacity, daily
recreation progress, worn garments, and active conditions. A season/day readout
supports farming plans. Inspecting a nearby humanoid shows the same categories.
Warnings use text/icons as well as color.

Only the server changes state, grants outputs, rolls disease, or accepts treatment.
Clients receive bounded snapshots on changes and periodic refreshes. Remote
inspection and equipment actions validate range, ownership, item availability,
and recipe requirements. Player relog, non-death cloning, dimension travel,
NPC saves, and chunk reload preserve state. Death ends bodily injuries and
current-life immunity; respawn starts a healthy new body with the world's normal
inventory policy.

## 2. Recreation

The daily target is 4.5 game hours, configurable within the requested 4–5 hours.
Sleep restores no recreation and contributes zero recreational time.

Qualifying activity includes reading a book, actively using a crafted toy,
interacting with an animal, or spending social time with a nearby humanoid.
Reading/toys require active use. Entity interaction begins a short renewable
social session within four blocks; movement together or renewed interaction
keeps it alive. Mere proximity, holding a book, fighting, trading clicks, and
standing beside an idle entity do not accrue an entire day of social time.
Eligible NPCs seek available companions/items during their recreation schedule.

Successful recreation accumulates credited time, capped at the day's target.
At day rollover, meeting the quota restores 30 reserve points, capped at 100;
an unmet quota instead subtracts `30 × missing_fraction` reserve points. Partial
completion therefore reduces that day's penalty without making repeated partial
days equivalent to meeting the requirement. Neglect across consecutive days
matters. Low recreation
reduces mental and work capacity, down to 50% at zero reserve. Recreation alone
does not kill an entity. A full active day offers enough non-sleep time to meet
the quota. A newly created entity receives a quota prorated for the remaining day.

## 3. Hunger, thirst, and capacities

Proposed basal hunger use is 40 points per day. Activity uses the largest current
movement multiplier rather than counting walking, sprinting, and swimming
simultaneously:

| Activity | Hunger rate relative to basal |
| --- | ---: |
| Sleeping | 0.5× |
| Idle | 1× |
| Walking | 2× |
| Sprinting/chasing | 4× |
| Climbing | 5× |
| Swimming | 6× |

Jumping costs 0.1 hunger, a damaging attack 0.25, and a broken block 0.08.
Production recipes declare a hunger cost proportional to their manual-work
duration, defaulting to 0.5 points per game hour of work. Hunting costs arise
from actual pursuit and attacks, not a nearby-animal
check. Food restores hunger according to nutrition; starvation damage remains
possible independently of the movement penalty.

The hunger movement multiplier interpolates through 100/50 hunger = 1.0,
30 = 0.8, 10 = 0.5, and 0 = 0.1. Sprinting is unavailable at or below 20 hunger.
Zero hunger therefore applies exactly a 10%-of-base starvation speed modifier;
injuries or temperature may further impair total movement. Starvation also
reduces manipulation/attack capacity.

Basal thirst use is 50 points per day. Walking costs 2×, strenuous movement 3×;
hot/arid exposure multiplies current use by 2.5×. Rain, shade, biome temperature,
body heat, and protective clothing influence exposure. Swimming does not count
as drinking. At thirst at or below 30%, mental capacity is limited to 30% of
normal. At zero thirst an eligible entity dies on the next physiology update,
independent of the world's starvation difficulty setting.

Surface water can be drunk by hand or collected in a vessel. Untreated sources
carry configurable contamination risk. Boiling water in pottery produces safe
water; a filled vessel records quantity and safety. Drinking consumes water and
restores thirst; waterlogged blocks are not an infinite safe-water exception.

Movement, manipulation, and mental capacity are derived values, recomputed from
needs, anatomy, temperature, and conditions. Stable attribute IDs prevent
stacking on relog. Manipulation affects mining speed, attack strength/speed,
and timed manual production. Mental capacity affects advanced production time
and NPC work cadence; at zero, intellectual work and NPC work stop. The UI
explains which state is limiting a job. Existing instant recipe paths must not
bypass a required timed workstation or bodily incapacity.

## 4. Temperature and clothing

Normal body temperature is 37°C. The environment model combines biome climate,
season, elevation, snowfall/rain, wetness, shelter, nearby fire, exertion, and
garment insulation/breathability. Environment and body temperature are separate:
entering a desert does not instantly set the body to desert ambient temperature.

Sustained body temperature below 35°C causes hypothermia; above 40°C causes heat
stroke. Both reduce capacity and, after one game hour of continuous illness,
deal one health point per 500 ticks. Below 32°C or above 41°C escalates damage to
one point per 200 ticks. Timers use actual exposure and reset after sustained
recovery, rather than triggering a fresh damage burst on relog. Recovery requires
returning toward the safe range, rather
than only clearing an effect icon. Cold exposure should offer time to seek
shelter before injury; extended unprotected exposure remains lethal.

Wool becomes spun yarn and woven cloth, then shirts, pants, jackets, and shoes.
Plant fiber supplies cordage and lighter cloth. A complete dry wool outfit
protects against ordinary winter cold; extreme mountains still require shelter
or heat. Wet wool loses some insulation. Heavy warm layers in heat make cooling
harder. Armor supplies no automatic wool-equivalent insulation.

Drinking supports evaporative cooling and gives modest immediate cooling only
when overheated; it cannot lower a healthy body into hypothermia. Heatstroke
recovery requires hydration and reduced exposure. Thermal properties and
durability are visible in garment tooltips. Garments render below armor on
players and eligible NPCs without consuming armor slots.

## 5. Health: anatomy, sensory damage, and conditions

Health is shown as three categories in addition to the familiar hit-point total.

| Category | Persisted state | Gameplay consequences |
| --- | --- | --- |
| Limbs & digits | Left/right arms and legs; individual fingers and toes | Arms/fingers lower manipulation; legs/toes lower movement; bilateral damage compounds |
| Cranial/sensory | Skull/brain, left/right eye, hearing | Brain trauma lowers mental capacity; severe trauma prevents work; loss of both eyes causes permanent blindness for that life |
| Health effects | Bleeding, poisoning, pathogen-specific infection, thermal illness | Severity, timers, treatment, contagion, and damage persist |

Damage localization uses projectile impact position, attack direction, and damage
type when available. Ordinary melee has a documented weighted body-region
fallback because vanilla hits do not identify individual fingers. Individual
digit injuries are derived within the selected extremity. Armor mitigates
damage for the region it covers. Existing wounds are not reset by receiving a
different injury. Destroyed eyes do not regenerate from milk, ordinary healing,
or an effect-clearing command.

Edged weapons, including the new spear, can open bleeding wounds. Excessive
bleeding drains health until stopped and can kill even if the original attack
was survived. Applying a bandage consumes it and resolves excessive bleeding;
the injury itself still needs recovery. Painkillers temporarily reduce the pain
penalty; they do not repair limbs, cure infection, or stop bleeding.

Raw meat, inadequately processed recipes, spoiled food, and contaminated water
can cause poisoning. Cooking has a completed-processing requirement: removing a
partly cooked product does not turn it into safe food. Poisoning treatment
reduces/removes the poisoning condition; hydration remains necessary.

Infections have pathogen IDs and definitions for incubation, transmission,
duration, severity, lethality, and treatment class. The initial mild respiratory
strain incubates for six game hours and produces two days of reduced mental
capacity without direct lethal damage. The severe systemic strain incubates for
twelve hours, lasts up to three days, and after symptom onset suppresses natural
health regeneration and deals one point of damage per game hour unless treated.
This makes contagious illness and lethal progression independently demonstrable.
Symptomatic hosts expose susceptible humanoids within six blocks at a default
6% infection probability per game hour of continuous contact, normalized for
elapsed time rather than rolled at that probability each tick. Healthy entities
and immune entities do not spread disease. Recovery grants pathogen-specific
immunity for 14 days by default. Immunity prevents reinfection by the matching
strain, not every disease. Wounds may become infected; contaminated water and
food can transmit the relevant infection as well as poisoning.

Antiseptic treats infected wounds; anti-infective medicine treats the matching
systemic infection; antitoxin treats poisoning. Recipes provide a non-fantasy
route from charcoal, boiled water, clean cloth, and cultivated medicinal plants.
Treatment effects are explicitly game abstractions. NPCs can consume available
supplies and receive direct treatment through interaction. Untreated deadly
infection and excessive bleeding must be capable of killing an otherwise fed,
hydrated entity. Medicine has duration/dose rules and cannot be repeatedly
applied without consuming inventory.

## 6. Natural-world rules

Use explicit data-driven real-animal/humanoid categories and forbidden-content
definitions. Default prohibited entities include undead, creepers, endermen,
slimes, phantoms, magical illagers/witches, golems, guardians, nether creatures,
the wither, the dragon, and other fantasy species. Zombies, skeletons, creepers,
and endermen are mandatory regression cases. Natural hostile animals remain.

Block prohibited entity additions from natural spawning, spawners, eggs,
commands, conversions, and structure-related sources. Remove existing prohibited
entities when their chunks load, without drops or new spawns. Do not merely
disable the global hostile-mob rule, which would conflate fantasy with animals.

Prohibit supernatural items/materials, including ender pearls/eyes, blaze and
ghast materials, totems, enchanted artifacts, magic potion variants, netherite,
and redstone circuitry. Keep ordinary water bottles through a content-aware
exception rather than banning every potion-item container. Real gems/metals,
ordinary books, natural dyes, and water remain usable. Replace fantasy-dependent
recipes and exclude prohibited outputs from loot, trades, creative listings,
crafting, pickup, and gameplay use. Existing prohibited stacks are removed when
inventories/containers are loaded or accessed. Generic enchantment components
are stripped so enchanting an otherwise natural item cannot bypass the rule.

Disable all structure starts in every dimension: villages, mineshafts,
strongholds, temples, monuments, shipwrecks, ruined portals, chambers, fortresses,
bastions, and end cities, including future structure definitions under the
global rule. Also remove dungeon/spawner-room and comparable placed features,
which are not necessarily registered structures. Preserve caves, ore veins,
trees, lakes, and other natural terrain features. Disable portals and magical
progression so the absent structures do not leave a required progression gate.
Do not rewrite every existing chunk to remove construction.

## 7. Resources and production

The first-day route must work without village loot, fantasy drops, iron, wool,
or a previously crafted metal tool:

1. Gather sticks, loose surface stones, plant fiber, wild food, and clay.
2. Knap a stone cutting edge and bind it with fiber to make early tools.
3. Establish a fire and a stone/clay kiln; mold and fire a vessel.
4. Boil drinking water, cook food, store it in a sealed pot, and make clothing.
5. Mine, process, smelt, alloy, and forge metals to improve tools and equipment.

| Resource | Acquisition and processing |
| --- | --- |
| Wood | Naturally generated trees; cutting tools improve yield and speed |
| Stone | Loose surface stones bootstrap knapping; mining produces material that must be shaped into a useful tool head |
| Copper | Natural ore; crushing and roasting precede charcoal-fueled furnace smelting; ingots become worked parts/tools |
| Bronze | Add natural tin ore because bronze requires an alloying material; furnace alloy recipe combines copper and tin |
| Iron | Natural ore processed into a furnace bloom, then worked into ingots/parts at a forge |
| Obsidian | Retain natural formation; substantially lower mining resistance and allow early stone tools to harvest flakes; flakes knap into sharp, brittle tools |
| Clay | Natural deposits; molding plus firing makes pottery and bricks |
| Plant fiber | Wild fiber plants; drying/twisting makes cordage and weaving makes light cloth |
| Dyes | Harvest/processing recipes from natural flowers and plants; cosmetic garment/pottery coloring |

The kiln has fuel, firing progress, an inventory, and unfinished/fired outputs.
The furnace/forge pipeline uses recipes with server-owned progress and consumes
fuel. Existing direct ore-smelting and instant-tool recipes are replaced where
they would bypass required processing. Copper, bronze, iron, stone, and obsidian
tools have distinct speed, durability, harvest capability, and recipes. The
stone-tool stage can reach copper/tin; later stages can reach iron. Clay vessels
and the first kiln require no metal bucket or metal tool.

## 8. Seasonal farming

Wild crop patches and fiber plants generate naturally to provide initial seeds.
Breaking grass alone must not be the only way to obtain all crop species.
Sowing consumes a seed on prepared farmland. Rice additionally requires a
flooded paddy. Fruit includes a naturally seeded fruit tree with annual
flowering/fruiting stages and a harvestable fruit item.

| Crop | Sowing window | Harvest/growth rule |
| --- | --- | --- |
| Wheat | Spring or autumn | Eight suitable growth days; winter dormancy |
| Rice | Spring | Twelve warm, flooded growth days; harvest by autumn frost |
| Maize | Spring days 15–28 or summer days 1–18 | Ten warm growth days; frost kills immature plants |
| Fiber | Spring or summer | Six suitable growth days |
| Fruit tree | Plant spring/autumn | Juvenile trees establish for one year; mature trees flower in spring and fruit in summer/autumn |
| Medicinal plants | Spring | Eight suitable growth days; harvested leaves/seeds supply treatment recipes |

Growth accumulates from elapsed suitable time, soil moisture, and temperature.
Random ticks and bone meal cannot skip required days or the season window.
Unsuitable plants become dormant, wilt, or die according to their definition.
Harvest returns food/material and a sustainable seed yield. Tooltips and the
calendar explain sowing windows. Existing wheat and other retained food crops
receive schedules too; leaving vanilla potatoes as an instant alternative
would bypass the system:

| Retained crop | Sowing/establishment window | Growth/harvest rule |
| --- | --- | --- |
| Carrot | Spring or summer | Eight suitable days; dormant in winter |
| Potato | Spring | Ten suitable days; autumn harvest, winter frost damages immature plants |
| Beetroot | Spring or autumn | Six suitable days; dormant in winter |
| Sweet/glow berries | Spring or autumn | Eight days to establish; two suitable days between summer/autumn harvests |
| Melon | Spring days 15–28 or summer days 1–18 | Ten warm days to first fruit; three warm days per later fruit; frost damage |
| Pumpkin | Spring or summer | Twelve suitable days to first fruit; four days per later fruit; dormant in winter |
| Cocoa | Spring or summer in warm climates | Twelve warm days; frost damage; no winter growth |
| Sugar cane | Spring or summer beside water | Six warm days per growth stage; frost damage; no winter growth |

Apples from the new fruit tree and berry bushes use the same calendar rules;
ordinary tree-leaf drops cannot supply an out-of-season fruit bypass. Other
retained harvestable plants use a declared season/growth definition; the fallback
for an undeclared crop is disabled growth with a diagnostic, rather than vanilla
instant growth. Natural mushrooms retain a damp, shaded, frost-free growth rule
with four days to maturity, independent of direct sunlight.

## 9. Husbandry

Replace immediate feeding-to-baby behavior and rapid vanilla maturation for
cows, sheep, chickens, and pigs. Persist maturity, nutrition, breeding cooldown,
pregnancy/incubation, scheduled birth, and product recovery. Require two mature,
fed adults plus suitable space. Interactions may retain Minecraft's pairing
convention; biological sex simulation is outside this request.

| Animal | Gestation/incubation | Juvenile maturity | Products |
| --- | ---: | ---: | --- |
| Cow | 12 days | 28 days | Milk after calving; one collection per day while adequately fed |
| Sheep | 8 days | 20 days | Wool; regrowth takes four days with adequate food |
| Chicken | 4 days in a warm nest | 12 days | Eggs no faster than one per day; only fertilized eggs hatch |
| Pig | 8 days | 20 days | Meat; litter of 2–4 with space and maternal nutrition |

Food/water provision gates breeding, product recovery, and growth. These animals
can graze suitable terrain or use player-stocked feed/water troughs. A pregnancy
produces one scheduled birth/litter; chunk reload and sleep cannot duplicate it.
Egg throwing cannot instantly create chicks. Feeding does not instantly age a
juvenile. Shearing/milking obey recovery rather than resetting on relog.

## 10. Preservation and decay

Food carries persisted freshness/contamination data. Default unpreserved shelf
lives are half a day for raw meat, one day for cooked meat, three days for
fruit/vegetables, and seven days for dry grain. All are configurable. Spoiled
food gives little nutrition and risks illness; cooking does not reset spoiled
ingredients to fresh.

Fired pots with lids provide meaningful preservation: a sealed pot slows food
decay to one sixth of the unpreserved rate. A normal chest offers only modest
protection and cannot substitute for pottery. A pot supports food storage, a
visible seal state, and safe transport with contents; it cannot nest containers
or duplicate inventory when broken. Opening unseals it; preservation resumes
only after resealing. Boiling water is a separate explicit use of an unsealed
pot at heat.

Weapons, tools, garments, armor, vessels, and workstations wear through their
appropriate uses. Unstored dropped items also accumulate environmental
weathering; organic materials weather faster than metal, stone, or ceramics.
Exposure accelerates in rain/heat. Weathered nonfood objects yield defined scrap
or fragments; exhausted food becomes spoiled/rotted. Stored unused durable
materials are stable; this preserves a useful reason to build storage.

Decay age survives saving, item pickup, dropping, crafting, and moving among
containers. Split stacks retain age; merges conservatively retain the oldest
freshness rather than refreshing contents. Crafting carries ingredient age
where applicable. Hoppers and portable pots cannot launder expiry. Catch-up is
bounded and computed from timestamps, without ticking every unloaded inventory.

## 11. Implementation sequence

1. **Shared state and natural-world rules:** replace starter examples; register
   config, content categories, persistence, calendar, diagnostic commands, and
   forbidden-content/structure rules. Audit the first-day resource route.
2. **Needs and interaction:** recreation, activity hunger, safe/unsafe water,
   thirst/death, temperature, capacity integration, NPC scheduling, HUD/sync.
3. **Health and clothing:** anatomy damage, sensory disability, bleeding,
   poisoning, contagious infections/immunity, treatments, clothing slots/rendering.
4. **Production and preservation:** natural resource additions, knapping,
   kiln/pottery, water boiling, metallurgy, textile recipes, freshness and wear.
5. **Seasonal living world:** crop schedules, wild seed sources, fruit trees,
   feed/water provision, gestation/incubation, maturation, milk/wool recovery.
6. **Integration and balance:** a complete fresh-world survival route, dedicated
   multiplayer synchronization, NPC autonomy, save/reload, and visual checks.

Each stage must leave the project buildable. Stage completion is not a claim
that the overall request is complete. The finished overhaul includes all six.
Exact NeoForge/Minecraft hooks are verified through the project's `qube api`
before implementation; use narrow compatibility hooks only where events cannot
cover world generation, vanilla hunger, crafting, or rendering.

## 12. Acceptance and verification

Replace arithmetic-only coverage with deterministic simulation and GameTests.
Use injectable calendar/exposure/randomness to test months and deadly conditions
without waiting through real-time seasons. Validate effects on actual entities,
recipes, inventories, blocks, and server/client boundaries, not only arithmetic.

| Requirement | Required evidence |
| --- | --- |
| Persistent five-state model | Player/NPC save/reload and dimension-change checks preserve values; death follows the specified reset policy |
| Recreation | 4.5 qualifying hours meet a full-day quota; sleep/idle proximity do not; deficit lowers work capacity |
| Activity hunger | Identical-duration swimming, climbing, and pursuit cost substantially more than idling; food/exhaustion is counted once |
| Starvation | Zero-hunger movement modifier is 0.1 of base; it does not accumulate across reloads |
| Thirst | Hot-biome activity drains faster; exactly 30% triggers mental limitation; zero kills |
| Thermal illness | Cold and heat cause progressive illness/damage; wool, shelter, and hydration counter the relevant exposure |
| Body damage | Left/right arm/leg and digit damage affect the appropriate capacity; bilateral eye destruction remains blind for that life |
| Treatment | Bandages stop bleeding; painkillers only relieve pain; correct treatments resolve their defined conditions and consume items |
| Infection | Seeded exposure transmits; deadly untreated cases can kill; recovered immunity prevents the same strain until expiry |
| Natural world | Representative forbidden entities fail all spawn paths; existing ones are discarded without loot; natural animals remain |
| Forbidden items | Loot, crafting, trades, inventory load, creative listing, and component variants cannot reintroduce banned content |
| Structures | Fresh normal terrain has no registered structures or dungeon features while retaining natural terrain/resources |
| Progression | Fresh-world player can obtain stone tools, kiln, safe water, pots, clothing, copper, bronze, and iron without prohibited content |
| Farming | Seeds, moisture, elapsed days, seasons, winter/frost, unloading, and sleep behave consistently; bone meal cannot bypass time |
| Husbandry | Premature breeding/maturation fails; births/incubation and milk/wool recovery take scheduled time and do not duplicate |
| Decay | Pot storage extends freshness; exposed food spoils; wear works; merging, hoppers, cooking, and reload cannot reset age |
| Multiplayer | Dedicated-server test with two clients verifies inspection, treatment, clothing, HUD state, and rejected invalid actions |

Run `qube build`, `qube test`, and dedicated-server startup checks after the
relevant changes. Generate new textures through `qube texture`; require empty
`bindings_missing`. Follow `qube shot` with opening its contact sheets, then
replay representative HUD, clothing, crop, livestock, kiln, pot, and health
scenes before describing appearance or claiming visual completion. A build or
server boot alone does not establish the gameplay requirements above.

## Review points

The principal proposed choices are game-time hours, 28-day seasons, compressed
husbandry durations, permanent sensory loss lasting the current life, the
humanoid membership list, prohibition of supernatural/redstone progression,
and structure removal applying to newly generated terrain. These are explicit
defaults for review rather than silent reductions of the requested systems.
