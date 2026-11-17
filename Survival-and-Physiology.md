---
title: Survival and Physiology
---
{% include nav.html %}

# Survival and Physiology

Every human body in the world runs the same simulation: hunger, thirst, body temperature, recreation, a 28-part anatomy, bleeding, poison and infection. Those states are combined into three capacities (movement, manipulation and mental) that directly scale how fast you walk, how hard and fast you hit, how fast you break blocks and how fast you work. Nothing here is cosmetic. Thirst at zero kills on the spot, untreated bleeding never stops by itself, and a body that is cold, starved or carrying 120 kg moves at a crawl. This page gives the exact rules and default numbers so that you can plan around them.

Time is measured in game ticks. One game hour is 1,000 ticks and one day is 24,000 ticks. Health is measured in vanilla health points (a player has 20, and one point is half a heart).

## Who is simulated

| Actor | Simulated |
| --- | --- |
| Players in survival or adventure mode | Yes |
| Players in creative or spectator mode | No. Physiology is paused, capacity penalties are removed, no skill experience is earned and manual survival jobs are refused. |
| Entity types in the `spawn_9b5fbb9:humanoids` tag (default: player, villager, wandering trader, pillager, vindicator) | Yes |
| Any living entity enrolled with `/survival enroll <targets>` (operator command) | Yes |

Bots and script-controlled bodies are ordinary actors. The controller type never grants an exemption. A data pack can add entity types to the `humanoids` tag.

Non-player actors look after themselves when they can: they eat food they hold or find within 8 blocks when hunger is below 60, drink when thirst is below 65, apply a useful treatment they hold or find, put on garments for empty clothing slots or in place of one in rags and seek company for recreation. An actor whose mental capacity is 0 has its AI switched off until it recovers.

Bodies update once per second (every 20 ticks). The mod configuration value `needsRate` (default 1.0, range 0.1 to 10) multiplies how much physiological time passes per update for everyone.

## Reading your condition

| Tool | What it shows |
| --- | --- |
| HUD (top left, config `showHud`) | Recreation, hunger, thirst, body temperature, health, the calendar date, air temperature and active conditions |
| **H** key or `/survival health` | Health screen: the three capacities, every limb and digit, brain, eyes and hearing, and the four clothing slots |
| `/survival` | One status line for yourself |
| `/survival inspect <target>` | The same line for another actor within 8 blocks (operators have no range limit) |
| `/survival json` | Full state as JSON, including every anatomy part |
| `/survival calendar` | Season, day and year |
| `/survival climate` | Air temperature, roof, nearby fire and, in regional-climate worlds, rain, wind, snow and wind shelter |

Possible conditions are BLEEDING, POISONED, RESPIRATORY INFECTION, SYSTEMIC INFECTION, WOUND INFECTION, PERMANENT BLINDNESS, HYPOTHERMIA, HEAT STROKE and PAIN RELIEF. With none of them the status reads "Stable". An infection is listed only once its symptoms begin: during the incubation nothing shows.

## Hunger

Hunger runs from 100 (full) to 0. It drains at a base rate per day multiplied by what you are doing.

| Activity | Multiplier | Hunger lost per day (default) |
| --- | ---: | ---: |
| Sleeping | 0.5 | 20 |
| Idle | 1 | 40 |
| Walking | 2 | 80 |
| Sprinting | 4 | 160 |
| Climbing | 5 | 200 |
| Swimming (any time you are in water) | 6 | 240 |

The base rate is `hungerPerDay` (default 40, data-pack tunable). An idle body goes from full to empty in 2.5 days. A body that walks all day needs more than its whole hunger bar every 1.25 days.

Work costs extra on top of the activity rate:

| Action | Extra hunger |
| --- | ---: |
| Each jump | 0.1 |
| Each block broken | 0.08 |
| Each hit that damages a target | 0.25 |
| Manual work (grid crafting, timed field jobs, workstation jobs) | 0.0005 per work tick (0.5 per 1,000 ticks) |

Eating restores 5 hunger per vanilla nutrition point of the food. See [Water Pottery and Food](Water-Pottery-and-Food) for food safety.

Effects of low hunger:

| Hunger | Effect |
| --- | --- |
| 50 or more | No penalty |
| 30 to 50 | Movement food factor falls from 100% to 80% |
| 10 to 30 | Movement food factor falls from 80% to 50%. The manipulation food factor falls from 100% at hunger 30 toward 20% at hunger 0. |
| 20 or less | Sprinting is switched off |
| Below 10 | Movement food factor falls from 50% to 10% |
| 0 | Movement food factor 10%, manipulation food factor 20%, and 1 health lost per 1,000 ticks |

## Thirst

Thirst runs from 100 to 0 and drains faster than hunger.

| Activity | Multiplier | Thirst lost per day in cool air (default) |
| --- | ---: | ---: |
| Sleeping | 0.5 | 25 |
| Idle | 1 | 50 |
| Walking | 2 | 100 |
| Sprinting, climbing or swimming | 3 | 150 |

The base rate is `thirstPerDay` (default 50). Heat multiplies it again: the factor is 1.0 while the effective air temperature is 26 C or lower and rises in a straight line to 2.5 at 42 C and above. An idle body in cool air empties in 2 days. A body walking in 42 C heat loses 250 thirst per day, which is a full bar in under 10 game hours.

| Thirst | Effect |
| --- | --- |
| 50 or more | No penalty |
| 30 to 50 | Mental capacity is capped, rising from 30% at thirst 30 to 100% at thirst 50 |
| 30 or less (`lowThirst`) | Mental capacity is capped at 30% (`lowThirstMental`) |
| 0 | Immediate death |

A drink from a jar or bottle restores 25 thirst and a drink from surface water restores 10. Drinking also cools an overheated body by 0.3 C, never below 37 C. See [Water Pottery and Food](Water-Pottery-and-Food).

## Body temperature and exposure

Body temperature starts at 37.0 C and is kept between 25 and 45 C. Each update the body moves toward a target temperature set by the surroundings. It closes about one twelfth of the remaining gap every game hour, so exposure builds over hours and so does recovery.

The target is 37 C, changed by these terms:

| Term | Rule |
| --- | --- |
| Cold | Minus 0.13 C for every degree the effective air temperature is below 18 C, multiplied by (1 minus insulation) |
| Heat | For every degree above 26 C, plus 0.25 C multiplied by (1 minus 0.65 x thirst / 100), plus 0.08 C multiplied by insulation |
| Exertion | Plus 0.4 C while sprinting, climbing or swimming |
| Fire | Within reach of a fire the target is never below 37.2 C |

The effective air temperature is the local air temperature adjusted as follows:

| Condition | Effect |
| --- | --- |
| Wet (in water, or rained on without a roof) | Air counts as 4 C colder and clothing insulation is multiplied by 0.65 |
| Wind (regional-climate worlds) | Below 20 C, air counts colder by wind speed x 0.35 x (1 minus wind shelter), scaled up as the air gets colder and reaching full strength at 0 C |
| Roof (worlds without the regional climate) | Under cover the air temperature is pulled halfway toward 18 C |
| Fire | A fire block, lava or a lit campfire within 3 blocks horizontally and 1 block vertically |

Wind shelter is the share of the four horizontal directions that have a full block within 4 blocks of you, at foot or head height. Insulation comes from clothing and is capped at 0.95; see [Clothing and Medical Care](Clothing-and-Medical-Care). Where the air temperature itself comes from (seasons, altitude, regional weather) is covered in [Weather Climate and Geography](Weather-Climate-and-Geography).

Hydration matters in heat: a fully hydrated body gains only 35% of the heat a completely dry body gains from the same air.

Thresholds (all data-pack tunable):

| Body temperature | Rule key | Default | Effect |
| --- | --- | ---: | --- |
| Below cold threshold | `coldThreshold` | 35 C | HYPOTHERMIA: movement, manipulation and mental capacity are multiplied by 0.55 |
| Above hot threshold | `hotThreshold` | 40 C | HEAT STROKE: the same 0.55 multiplier |
| Grace period | `thermalGraceTicks` | 1,000 ticks | Time outside the safe range before damage starts |
| Damage outside the safe range | `thermalDamageTicks` | 1 health per 500 ticks | After the grace period |
| Extreme cold or heat | `extremeCold`, `extremeHot` | below 32 C, above 41 C | Damage rises to 1 health per 200 ticks (`extremeDamageTicks`) |
| Recovery | `thermalRecoveryTicks` | 1,000 ticks | Continuous time back in the safe range that resets the exposure timer |

The capacity penalty applies the moment you cross a threshold. The damage timer does not reset until you have stayed in the safe range for the full recovery time, so stepping briefly to a fire does not clear it.

## Carried weight

Everything you carry weighs on you. A player's whole inventory is weighed once per second, including armour and the offhand. For other actors the hands, armour and pack are weighed.

| Carried weight | Pace |
| ---: | ---: |
| Up to 35 kg | 100% |
| 50 kg | 84% |
| 60 kg | 74% |
| 80 kg | 52% |
| 100 kg | 31% |
| 120 kg or more | 10% |

Between 35 and 120 kg the pace falls in a straight line. Pace multiplies your movement capacity, so load and injuries stack. Below 50% pace (about 82 kg) a player sees the message "Overloaded: carrying N kg. Load a cart."

| Item | Weight per item |
| --- | ---: |
| Building blocks (anything placed as a block) | Exempt, 0 kg |
| Iron, copper and gold ingots, gunpowder, bone meal, wheat | 1 kg |
| The mod's own metal, mineral and chemical items | 1 kg |
| Coal, charcoal, coke | 25 kg (one sack) |
| Food | 0.3 kg |
| Stick | 0.3 kg |
| Brick | 3.5 kg |
| Clay ball | 3.75 kg |
| Other loose vanilla goods | 0.5 kg |

A full stack of 64 ingots is 64 kg. Two stacks leave you at a tenth of your pace. Four sacks of charcoal are 100 kg. Bulk goods belong on a vehicle; see [Transport and Logistics](Transport-and-Logistics) and [Units and Realism Rules](Units-and-Realism-Rules).

## Anatomy

The body has 28 parts, each from 100 (sound) to 0.

| Group | Parts |
| --- | --- |
| Limbs | Left arm, right arm, left leg, right leg |
| Cranial and sensory | Brain, left eye, right eye, hearing |
| Fingers | Thumb, index, middle, ring and little finger on each hand (10) |
| Toes | Five toes on each foot (10) |

### How injuries land

Any damage that costs health, except starvation-type physiological damage, also injures a body part.

| Step | Rule |
| --- | --- |
| Region roll | Brain 12%, left arm 18%, right arm 18%, left leg 22%, right leg 30% |
| Falls | Always a leg, left or right at random |
| Projectiles | A projectile that strikes the top quarter of the body hits the brain |
| Side | An attack from clearly one side moves an arm or leg hit to that side |
| Part damage | Health lost x 6 points |
| Digit | On a limb hit, 35% chance that one of that limb's five digits also takes health lost x 8 points |
| Eye | On a brain hit of 4 or more health, 35% chance that one eye takes health lost x 10 points |
| Hearing | On a brain hit of 4 or more health, 20% chance that hearing takes health lost x 6 points |

Example: a hit that costs 5 health removes 30 points from a limb. A 10-health hit to the head can destroy an eye outright.

### Edged wounds, bleeding and wound infection

A wound is edged when the attacker's main-hand item is a sword, spear or axe (pickaxes count), when the damage is a gunshot or artillery, or when it is the bite or claws of a wolf, bear or lion (see [Predators](predators)).

| Effect | Rule | Default |
| --- | --- | --- |
| Bleeding gained | Part damage divided by 25, so 0.24 per health lost (0.32 more if a digit is hit), up to a maximum of 10 | |
| Bleeding damage | Bleeding level in health per `bleedingDamageTicks` | Level x 1 health per 1,000 ticks |
| Pain | While bleeding with no painkiller active, movement and manipulation are multiplied by 0.75 | |
| Wound infection | Chance per edged hit (`woundInfectionChance`) | 8% |
| Wound infection from a bite | Three times that chance | 24% |
| Torn cloth | An edged wound to an arm or leg tears the outer garment over it; fire scorches every garment (`garmentTearTicksPerDamage`) | 12,000 ticks of wear per health lost |
| Wound infection damage | 1 health per `woundDamageTicks` until treated | 1 per 3,000 ticks |

Bleeding does not clot. It continues at the same level until a bandage is applied. Wound infection likewise lasts until antiseptic is applied. Both block healing while they last. Treatments are described in [Clothing and Medical Care](Clothing-and-Medical-Care).

Industrial work adds its own injuries: burns and acid scalds to an arm, poisoning from some chemicals, and crushed fingers in a machine shop. See [Industrial Manufacturing](Industrial-Manufacturing).

### Poison

Poison comes from untreated water, raw or spoiled food and some industrial chemicals. It lasts `poisonDurationTicks` (default 6,000 ticks) and costs 1 health per `poisonDamageTicks` (default 1,500 ticks), which is 4 health for a full course. Antitoxin ends it at once. Poison blocks natural healing while it lasts.

### Infection

| | Respiratory infection | Systemic infection |
| --- | --- | --- |
| Starts by chance, per day | 2% (`respiratoryOnsetChancePerDay`) | 0.2% (`systemicOnsetChancePerDay`) |
| Incubation (no symptoms, nothing shown) | 6,000 ticks | 12,000 ticks |
| Infectious | Last quarter of the incubation and all through the symptoms | The same |
| Symptoms | 48,000 ticks (2 days) | 60,000 ticks (2.5 days) |
| Mental capacity during symptoms | x 0.7 | x 0.5 |
| Damage during symptoms | None | 1 health per 1,000 ticks |
| Other effects during symptoms | None | No natural healing and no anatomy recovery |
| Immunity after recovery or antibiotic | 336,000 ticks (14 days) | 336,000 ticks (14 days) |

An untreated systemic infection deals 60 health over its symptoms, three times a player's health bar, with healing blocked. Treat it with an antibiotic during incubation or as soon as symptoms appear.

Sources of systemic infection, by default:

| Cause | Chance |
| --- | --- |
| Being poisoned by untreated water | 25% of those poisonings (`waterInfectionChance`) |
| Being poisoned by raw or spoiled food | 15% of those poisonings (`rawFoodInfectionChance`) |
| By chance | 0.2% a day |
| Contact with an infectious carrier | See below |

Nobody has to give you an infection. Each body has the daily chance in the table of coming down with one, and both chances are doubled while hunger or thirst is below 30.

An infectious actor exposes the non-exempt actors around it. In the open that is anyone within `contactRange` (default 2 blocks). Under a roof it is anyone within `sharedRoomRange` (default 6 blocks) who is in line of sight and also under a roof. Each exposed actor has a chance of `contactInfectionChancePerHour` (default 6% per game hour) of catching it. The strain passed on is the carrier's. An actor who is already infected with that strain, or immune to it, cannot catch it again. How to keep a settlement healthy is on [Illness](illness).

Immunity is per strain and is lost on death.

### Blindness and permanent damage

An eye reduced to 0 never recovers in that life. With both eyes at 0 the body is permanently blind: mental capacity is multiplied by 0.4, the vanilla blindness effect is applied and reading is impossible. Every other part, including a finger or toe reduced to 0, can recover.

## Capacities

Three capacities are computed from the states above. Each factor in a row multiplies the others.

| Capacity | Factors |
| --- | --- |
| Movement | Hunger factor (table above) x (0.15 + 0.85 x average of both legs) x (0.7 + 0.3 x average of the ten toes) x thermal x pain. Never below 1%. |
| Manipulation | (0.2 + 0.8 x hunger / 30, at most 1) x (0.1 + 0.9 x average of both arms) x (0.4 + 0.6 x average of the ten fingers) x thermal x pain |
| Mental | 0 if the brain is at 10 or less. Otherwise (0.5 + 0.5 x recreation / 100) x brain x (0.4 if blind) x (0.8 + 0.2 x hearing) x infection factors x thermal, and never higher than the thirst cap. |

Part values count as a fraction of 100. Thermal is 0.55 during hypothermia or heat stroke and 1 otherwise. Pain is 0.75 while bleeding without a painkiller and 1 otherwise.

What the capacities change:

| Capacity | Direct effect |
| --- | --- |
| Movement x load pace | Movement speed |
| Manipulation | Attack speed and attack damage |
| Lower of manipulation and mental (mental counted as at least 5%) | Block breaking speed |
| Lower of manipulation and mental | Speed of all manual work: grid crafting, field jobs and workstation jobs (then multiplied by skill; see [Skills and Learning](Skills-and-Learning)) |

Hard limits:

| Condition | Consequence |
| --- | --- |
| Mental 0, or manipulation below 10% | You cannot attack and cannot break blocks |
| Mental 0, or manipulation 5% or less | You cannot take anything from the crafting grid |
| Lower of mental and manipulation 5% or less | No vessel work, no new manual job, running jobs pause, and `/survival act` refuses everything except eat, drink and social |
| Manipulation below 15% | You cannot apply treatments, put on clothing or dye a garment |
| Manipulation below a recipe's minimum (25% for pottery, 10% to 20% for most other station work) | That workstation recipe cannot be started or continued |
| Mental 0 | No reading or play. A non-player actor stops thinking. |

An expert with ruined hands cannot work. Skill never overrides the body.

## Recreation

Recreation is a 0 to 100 score that sets half of your mental capacity: the factor is 50% at recreation 0 and 100% at recreation 100 (`recreationFloor`, default 0.5).

Each day you must spend a quota of active recreation time. The quota is the configuration value `recreationHours` (default 4.5 game hours, range 4 to 5). At the end of the day, a met quota adds `recreationGain` (default 30) and a missed quota removes `recreationLoss` (default 30) multiplied by the share you missed. A new body's first-day quota is reduced in proportion to the part of the day already gone.

| Activity | How |
| --- | --- |
| Reading | Right-click with a plain book, or hold a book, book and quill or written book and use `/survival act read`. Impossible while blind. |
| Play | Hold a wooden toy (two sticks and one cordage on the crafting grid) and right-click, or `/survival act play` |
| Company | Sneak-interact with an animal or humanoid, or `/survival act social <target>`. The companion must be within 4 blocks. |

Reading and play count for 200 ticks (10 seconds) per use while the same item stays in your main hand; use again to renew. A social session also lasts 200 ticks and renews itself while you keep moving within 4 blocks of your companion. Standing still next to someone does not renew it, and time asleep never counts.

## Sleep

There is no fatigue or sleep need. Sleeping has two effects. While a body is asleep, hunger and thirst drain at half the idle rate. When a night is skipped, the skipped time (up to one day) is applied to every simulated body at once: needs drain, body temperature follows the weather of the skipped hours, and bleeding, poison and infection keep running. Sleepers use the sleeping rate and everyone else the idle rate. The calendar also advances, so seasons move on.

Do not go to bed bleeding, thirsty or cold. The night will be charged to you in one step.

## Healing and recovery

| Recovery | Rule | Conditions |
| --- | --- | --- |
| Health | 1 health every 200 ticks | Hunger above 60, thirst above 60, no bleeding, no poison, no systemic symptoms, and nothing else dealing physiological damage at that moment |
| Anatomy | `anatomyRecoveryPerDay` (default 3 points per part per day) | Hunger above 50, thirst above 50, no bleeding, no systemic symptoms, no wound infection |

Vanilla food-based regeneration is disabled for simulated players. A limb at 0 needs about 33 days of good conditions to return to 100.

## Death

Death is permanent. A player who dies does not return to the world.

- You come back only as a spectator. You can watch, but you cannot touch a block, use an item, run a command or speak in chat, and the simulation no longer counts you.
- Leaving and rejoining changes nothing.
- Everything you had learned is gone with you. Your soldiers are released, and your place at a works, in a gun crew or as a station's worker is given up.
- Worn garments drop with the body, unless the `keepInventory` game rule is on. Non-player actors drop their garments when they die.
- A creative or spectating operator was never part of the simulation and is not ended by dying.

An operator can return a dead player to the world with `/survival revive <players>`. They come back as a new person, in survival mode where they stand: hunger, thirst and recreation at 100, temperature 37 C, every part at 100, no immunity, and nothing learned.

Dead villagers, soldiers and animals leave the world, and nothing keeps a place for them. A dead draught animal is out of the harness, and a dying animal neither mates, lays nor gives birth.

## Commands

| Command | Use |
| --- | --- |
| `/survival` | Status line |
| `/survival health` | Open the health screen (same as **H**) |
| `/survival json` | Full state as JSON |
| `/survival inspect <target>` | Status of another actor within 8 blocks |
| `/survival calendar` | Date |
| `/survival climate` | Local exposure report |
| `/survival act <action> [target]` | Actions: `eat`, `feed`, `drink`, `read`, `play`, `clothe`, `undress`, `mend`, `treat`, `social`, and `give_water` |

Operator commands:

| Command | Use |
| --- | --- |
| `/survival enroll <targets>` | Enroll living entities as simulated humanoids |
| `/survival query <target>` | JSON state of any actor |
| `/survival debug <target> <field> <value>` | Set `hunger`, `thirst`, `recreation` or `temperature` (value 0 to 100; temperature is clamped to 25 to 45) |
| `/survival injure <target> <part> <amount>` | Deal an edged injury of 0 to 100 points to a named part, for example `LEFT_ARM` or `RIGHT_EYE` |
| `/survival control <actor> ...` | Run the player commands on behalf of another actor |

## Tuning

Values shown with a rule key live in the data-pack file `data/spawn_9b5fbb9/survival/physiology.json`. Override the file in a data pack and run `/reload`. Each value has a permitted range and an invalid file is rejected as a whole. Season length (`seasonDays`, default 28), `recreationHours`, `needsRate` and `showHud` are in the mod configuration. The activity multipliers, temperature formula, capacity formulas, hit locations, the load curve and item weights are fixed in code.

## What kills you and common mistakes

- **Thirst at zero is instant death.** There is no slow decline to warn you beyond the thirst bar. In hot weather thirst drains up to 2.5 times faster.
- **Bleeding never stops by itself.** A single 5-health sword cut bleeds 1.2 health per game hour until bandaged, and healing is blocked the whole time. Carry bandages before you fight.
- **Systemic infection outlasts your health bar.** One unlucky drink of untreated water can lead to 60 health of damage. Boil water and keep an antibiotic.
- **Cold is slow, then sudden.** Body temperature lags the weather by hours. By the time HYPOTHERMIA shows, your capacities are already cut to 55% and the walk back to a fire takes longer.
- **Dehydration makes heat worse.** A dry body gains almost three times the heat of a hydrated one.
- **Sleeping through trouble.** A skipped night applies all its hours of bleeding, infection, thirst and cold at once.
- **Carrying the ore home in your pockets.** Two stacks of ingots or five sacks of charcoal reduce you to a tenth of your pace.
- **Starving on the march.** Below 50 hunger you slow down, at 20 you cannot sprint, and walking doubles the drain.
- **Low thirst stops work before it kills.** At thirst 30 your mental capacity is capped at 30%, which caps block breaking and every manual job.
- **Skipping recreation.** Each fully missed day costs 30 recreation, and at recreation 0 your mental capacity is halved.
