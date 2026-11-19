# Shared controller actions and practiced food acquisition

Final verification: the combined Qube suite passed **91/91 tests in 17.6 seconds**
on 2026-11-07. Java and resources are frozen after that run. Dedicated workshop
startup and visual replay belong to the parent integration pass; this report
makes no visual-completion claim. No commits were created by this worker.

## Files

- `controllers/WorldActions.java`: shared validated jobs, hunting, commands and
  bounded planner/recipe inspection.
- `controllers/ControllerGameTests.java`: established action/conservation tests,
  updated for novice skill timing.
- `controllers/SkillActionGameTests.java`: practice, native interactions,
  cancellation, hunting and actual NPC pursuit tests.
- `controllers/NativeForageGameTests.java`: native resource/tree dispatch and
  explicit dismantling tests.
- `agriculture/SeasonalCrops.java`: native farm adapters, practice confirmation,
  harvest eligibility and mature berry yields.
- `content/ContentGameTests.java`: only `wildBerryRecovery` was adapted here,
  with the content worker's coordination, to retain its exact seasonal boundary
  checks while verifying timed harvest.

The initial shared action implementation also owns the existing LivingEntity
entry points in GroundResourceBlock and FruitTreeBlock. The practiced-skills
extension did not edit those blocks or the root skill/body/network/UI files.

## Public action surface

All actors are real LivingEntity instances. There is no Player proxy or AI
planner.

- `start(actor, operation, position)` supports gather/forage, harvest, mine,
  till and sow. `forage(actor, position)` is an explicit gather alias.
- `start(actor, operation, position, hand)` additionally supports actual
  offhand sowing, tilling and harvesting. Supplies come from that real hand.
- `hunt(actor, Animal)` starts a persistent real-quarry hunting job.
- `craft(actor, registeredRecipeId)` reserves actual matching supplies.
- `cancel(actor, reason)` returns world-job reserves, or delegates to the shared
  nearby owned/manual or public/passive station cancellation API when no world
  job exists.
- `livestock`, `nest` and `trough` delegate to the real husbandry/stocking actions.
- `inspectJob` returns current operation, status, progress, target, associated
  skill and estimated remaining ticks at present physiology and practice.

Self commands live under `/survival`; operator-only
`/survival control <actor> ...` exposes the same adapters. New food commands are
`forage x y z` and `hunt <animal>`. Cancel, jobs, planning and recipeinfo remain
available for either input adapter.

## Work, practice and conservation

One actor cannot begin a world/hunting/crafting job while its forge/station or
credited grid work claims its hands. Existing jobs progress only on loaded
entity ticks. They never gain manual work from calendar jumps, sleep or
unloading. Actual progress records LaborGuard.worked, including completion, so
another station cannot claim overlapping elapsed labor. Tool/input changes,
invalid targets, death, dimension changes and distance violations cancel work;
physiological incapacity pauses it.

Foraging/farming progress is multiplied by the shared SkillApi work rate:
`0.5 + level / 200`, also limited by current manipulation and mental capacity.
Practice follows successful physical completion:

| Completed work | Skill | Experience |
| --- | --- | ---: |
| Surface gather/forage | Foraging | 4 |
| Till real soil | Farming | 2 |
| Consume a seed and sow | Farming | 3 |
| Harvest a mature sown crop | Farming | 6 |
| Mature wild crop/berry or fruit-tree harvest | Foraging | 6 |
| Kill a real animal | Hunting | 25, awarded solely by root SkillEvents |

Rejected/cancelled jobs, immature/frost-lost harvests, retries, idle ticks and
reloading a completed actor grant no practice. Old pending world jobs infer the
appropriate skill without converting offline time into work.

Mining passes its actual tool/actor into the normal BlockDropsEvent loot path
and invokes real tool wear. The shared block-drop handler charges the 0.08
block hunger cost once; this adapter does not duplicate it. Crafting matches
registered recipes with actual inventory/hand/nearby container/dropped inputs,
preserves exact reserved components, carries aging once and returns remainders.
Outputs requiring skilled production stations are rejected from normal grid
crafting before reservation and rechecked at completion. Recipes larger than
2x2 require a real nearby crafting table.

## Native interaction parity

Both real player hands route ordinary sowing and hoe use through the shared
jobs. Crop left-click harvesting uses the same timed surface. Native fallback
plant/tool events award practice only after confirming the world change and
actual inventory consumption or durability use; cancelled events and reloads
cannot turn those pending confirmations into free awards.

Eligible berry right-clicks, including the offhand, now begin the same timed
harvest as controllers. Growth/recovery is not reset at initiation. Completion
produces actual berries, resets the bush and grants one award. The deferred
instant-vanilla berry XP route was removed.

Loose stones and wild patches dispatch native left-clicks to timed gathering;
normal break-speed polling cannot bypass it. A fruit tree's normal left-click
harvests ripe food while retaining the tree. Repeated clicks do not dismantle
it. Sneak-left-click explicitly requests timed mining/dismantling and grants no
additional food or foraging practice.

Sowing respects real plant survival rules: moist prepared farmland for crops
and stems, flooded rice paddies, actual water-adjacent cane substrates, inferred
cocoa facing/support logs, and native berry/mushroom/ceiling support rules.
Planting time is recorded. Harvest reconciles seasonal time and frost before
actual drops. Calendar-grown fruit/cane stages carry provenance for practice;
placing and immediately breaking a fruit block is not a mature crop harvest.

## Hunting

The action requires a living eligible survival/adventure humanoid, a usable
physical weapon, an alive natural Animal from the real-animal category and
available physiological capacity. Each strike rechecks the actual weapon
AttackRange, line of sight and target state. Damage uses playerAttack for
players and mobAttack for NPCs, with the actual weapon damage and bodily
manipulation. Native hurt/death/drop events supply real protection, loot and the
single 0.25 damaging-attack hunger cost. Successful contact invokes real weapon
wear. This layer adds no kill XP; root's actual LivingDropsEvent owns that award.

NPC navigation uses a bounded path search, checks loaded terrain and rejects
nodes outside the sixteen-block hunting area. Pursuit stops after 1,200 loaded
pursuit ticks without contact. It does not teleport or create meat. Players and
fake-player controllers move themselves when quarry is outside actual weapon
reach. The runtime test uses an AI-enabled villager, a real floor and a
stationary cow five blocks away, then observes actual movement/contact after
80 server ticks.

## Planning and limits

`planning(actor, recipeOffset[, cropOffset])` retains the no-argument form,
returns at most 64 catalog entries and stays within 24 KiB UTF-8. It includes
skill levels/experience/next-level progress, physiological limits, hunting
requirements, crop schedules and upcoming sowing windows, plus exact production
station, skill/minimum, manipulation, manual/heat and practice requirements.
`nextOffset` and `nextCropOffset` provide continuation. `recipeInfo` pages
ingredient alternatives with counts/truncation indicators.

Normal recipe crafting supports declared layouts up to 3x3. Dynamic special
recipes use their dedicated adapters. Finished NPC items follow SurvivalApi.give
and become actual nearby item entities; controllers arrange their collection
and storage. Navigation is bounded movement support, not autonomous planning or
guaranteed solutions to every obstacle.

## Verification

Missing-method compilation red and the four initial behavioral reds were
observed before food-skill implementation. Three additional native regressions
were observed failing before their fixes. The final **91/91** run includes all
ten new controller skill/native tests, the existing controller suite and the
root/content regressions. Covered outcomes include novice/expert timing,
completion-only/reload-safe XP, native harvest parity, both sowing hands,
station cancellation refunds, weapon/range/LOS/incapacity checks, real animal
loot/wear/hunger, actual NPC pursuit, native berry timing and single practice,
native resource dispatch/exclusivity, fruit retention and explicit dismantling.
