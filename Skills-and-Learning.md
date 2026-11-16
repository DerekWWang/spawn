---
title: Skills and Learning
---
{% include nav.html %}

# Skills and Learning

Every simulated humanoid has sixteen independent skills, each from level 0 to level 20. Skill is earned only by doing the real work: time actually spent at a station, a field job that completes, an animal actually killed, a wound actually treated. Skill makes manual work faster (50% speed at level 0, 100% at level 20), unlocks the harder workstation recipes, sets the quality of fired pottery and sharpens fighting. It never replaces materials, fuel, tools or a working body. There are no classes or backstories; a specialist is simply someone who has put the hours in and keeps practising, because high skills decay.

## The sixteen skills

| Skill | Trained by | What the level does |
| --- | --- | --- |
| Pottery | Tending a fuelled kiln to fire a jar | Work speed. Sets jar quality (see below). |
| Smithing | Manual work at the forge and anvil; the industrial recipes that use it | Work speed. Gates metal tools, weapons, armour and parts. |
| Weaving | Hand-processing flax fibre and twisting cordage on the crafting grid; all loom work | Work speed. Gates pants and jackets. |
| Farming | Timed tilling, sowing, irrigating and crop harvesting jobs | Work speed of those jobs |
| Foraging | Timed gathering of wild stands; harvesting fruit trees | Work speed of those jobs |
| Hunting | Killing a real animal | Speed of each strike in a `/survival hunt` job |
| Mining | Timed extraction of natural stone and ore; crushing mineral at the anvil; dressing stone shot | Work speed. Prospecting confidence is 50% plus 2% per level, up to 90%. |
| Construction | Grid crafting of wooden parts and workstations; laying track; crossbow stocks; the industrial recipes that use it | Work speed. Gates crossbow stocks and industrial building work. |
| Cooking | Grid crafting of anything edible | Work speed of that crafting |
| Animals | Useful livestock work: feeding that is actually consumed, shearing, milking and similar | No further effect in the current rules |
| Medicine | Applying a treatment that works; preparing yarrow leaves at the loom | Speed of leaf preparation only. Treatment itself does not depend on level. |
| Melee | Dealing real hand-to-hand damage; parrying with a shield | Player melee damage from 80% at level 0 to 120% at level 20. Soldiers hit harder, recover faster and parry more often. |
| Marksmanship | Real hits with arrows, bolts and musket balls | Tighter aim (aim error falls from 1.6 to 0.5 degrees over 20 levels) and a faster loading drill (60% to 100%) |
| Riding | Time driving or riding in a moving vehicle | Vehicle top pace from 80% to 100%. Chance to be unhorsed by a hit of 3 or more damage falls from 30% by 1.5 points per level, to zero at level 20. |
| Gunnery | Serving a gun that fires | Faster loading and tighter dispersion (from 1.6 times the piece's spread at level 0 to 0.7 times at level 20) |
| Engineering | Attended work at mills, works and yards | Work speed. Gates most industrial recipes. |

Details for the fighting skills are in [Military Service and Combat](Military-Service-and-Combat) and [Siege Artillery](Siege-Artillery). Industrial gates are listed in [Industrial Manufacturing](Industrial-Manufacturing). Riding is covered in [Transport and Logistics](Transport-and-Logistics).

## Levels and experience

Experience (XP) is counted separately for each skill.

| Level | XP to reach the next level | Total XP at this level |
| ---: | ---: | ---: |
| 0 | 1,000 | 0 |
| 1 | 2,000 | 1,000 |
| 2 | 3,000 | 3,000 |
| 3 | 4,000 | 6,000 |
| 4 | 5,000 | 10,000 |
| 5 | 6,000 | 15,000 |
| 6 | 7,000 | 21,000 |
| 7 | 8,000 | 28,000 |
| 8 | 9,000 | 36,000 |
| 9 | 10,000 | 45,000 |
| 10 | 12,000 | 55,000 |
| 11 | 14,000 | 67,000 |
| 12 | 16,000 | 81,000 |
| 13 | 18,000 | 97,000 |
| 14 | 20,000 | 115,000 |
| 15 | 22,000 | 135,000 |
| 16 | 24,000 | 157,000 |
| 17 | 26,000 | 181,000 |
| 18 | 28,000 | 207,000 |
| 19 | 30,000 | 235,000 |
| 20 | Maximum | 265,000 |

At level 20 a further 32,000 XP can be banked (to a total of 297,000) as a reserve against decay.

## How experience is earned

There are two kinds of award. Both are then multiplied by passion and limited by the daily soft cap.

### Timed work: XP per active tick

For any manual job that takes time, XP is paid when the job completes and is based on the ticks you actually spent working on it.

**Base XP = 0.25 x active ticks x recipe weight**

| Work | Weight | Base XP per 1,000 ticks of work |
| --- | ---: | ---: |
| Field jobs (gather, harvest, mine, till, sow, irrigate, lay track) | 1.0 | 250 |
| Crafting-grid work that has a skill | 1.0 | 250 |
| Attended industrial work | 1.0 | 250 |
| Firing a jar; riveting; finishing tools, weapons and utilities at the anvil | 1.0 | 250 |
| Finishing armour, musket barrels and locks | 1.4 | 350 |
| Machine parts | 1.6 | 400 |
| Crossbow assembly | 1.2 | 300 |
| Bronze; forging armour blanks, plate, barrel and machine-part blanks; sewing garments | 0.8 | 200 |
| Working blooms; forging tool blanks; round shot; stone shot; crossbow stocks | 0.6 | 150 |
| Weaving cloth | 0.48 | 120 |
| Spinning wool; preparing flax or yarrow; crushing mineral; casting musket balls; solder | 0.4 | 100 |

Weights come from each recipe's `practice` value (weight is practice divided by 25) and are data-pack tunable per recipe.

Because the award counts time worked, a slow worker earns the same XP per hour as a fast one and simply produces less. A cancelled, rejected or abandoned job pays nothing. Work only advances while you are present, alive, within range and physically able, so unattended time earns nothing. Unattended heating (smelting, roasting, cooking meat in a kiln, burning charcoal) never trains a skill.

Extra conditions on field jobs:

| Job | Condition for XP |
| --- | --- |
| Mine | The block must be natural stone, limestone, sandstone, calcite, obsidian or an ore, in a spot that has never been mined or placed before. Stone you put down and dig up again pays nothing. |
| Harvest | The crop or berries must actually be ripe |
| Irrigate | The soil must actually take the water |

Crafting-grid work has a skill only for these results: anything edible (Cooking); cordage, plant fibre and dried fibre (Weaving); wooden toys, crossbow stocks, workstations, and wooden planks, stairs, slabs, fences, gates, doors, trapdoors, signs and boats (Construction).

### Outcome awards: fixed XP

| Event | Skill | Base XP |
| --- | --- | ---: |
| You kill a real animal | Hunting | 25 |
| A useful livestock action (feed consumed, tool worn) | Animals | 25 |
| A treatment is applied and works | Medicine | 100 |
| A hand-to-hand hit lands on a humanoid or mob | Melee | 12 per point of health removed |
| A shield parry | Melee | 20 |
| A projectile or musket ball hits | Marksmanship | 12 per point of health removed |
| Each second driving a moving vehicle | Riding | 3 |
| Each second as a passenger on a moving vehicle | Riding | 0.5 |
| The gun you serve fires | Gunnery | 60 to each crew member |

No XP of any kind is earned in creative or spectator mode.

### Worked example

Firing a jar is 2,000 units of work. A level 0 potter in perfect health works at 50% speed, so the job takes 4,000 active ticks. Base XP is 0.25 x 4,000 = 1,000. With no passion that becomes 350 XP, so the first level takes three jars. With an interested passion one jar is enough.

## Passion

Each skill has a passion that multiplies every award to it.

| Passion | Learning rate |
| --- | ---: |
| None | 35% |
| Interested | 100% |
| Burning | 150% |

Every skill starts at None, so a new body learns everything at 35%. Passion changes learning only. It does not make work faster, create materials or lift a skill gate. In the current rules a passion can be changed only by an operator, with `/survival debug_passion <target> <skill> <none|interested|burning>`.

## Daily soft cap

Each skill counts the net XP gained since the start of the day. Up to 4,000 net XP per skill per day is paid in full. Anything beyond that is paid at 20%. The counter resets at the start of each day. Decay is subtracted from the same counter, so it is a net figure.

At the base rate of 250 XP per 1,000 ticks and an interested passion, the cap is reached after 16 game hours of one kind of work.

## Decay

Skills at level 10 and above lose XP continuously, whether or not you are awake or working.

| Level | XP lost per day |
| ---: | ---: |
| 0 to 9 | 0 |
| 10 | 30 |
| 11 | 60 |
| 12 | 120 |
| 13 | 180 |
| 14 | 300 |
| 15 | 540 |
| 16 | 840 |
| 17 | 1,200 |
| 18 | 1,800 |
| 19 | 2,400 |
| 20 | 3,600 |

A level is not lost the moment its XP reaches zero. There is a buffer: the skill can sink 1,000 XP below the start of its level before it drops. When it does drop, it lands 1,000 XP short of regaining the level. A neglected level 10 skill can slip back to level 9 in this way; from level 9 down nothing decays.

Holding level 20 costs 3,600 XP of practice every day, which is most of the daily soft cap for that skill. Holding several skills that high at once is not practical for one person.

## What skill does to work

| Rule | Formula |
| --- | --- |
| Skill work rate | 50% plus 2.5% per level (100% at level 20) |
| Speed of a skilled manual job | Lower of manipulation and mental capacity x skill work rate |
| Speed of an unskilled manual job | Lower of manipulation and mental capacity |
| Effective skill (used for pottery quality) | Level x lower of manipulation and mental capacity |

Capacities are explained in [Survival and Physiology](Survival-and-Physiology). A hungry, cold, wounded or bored expert is a slow worker, and an injured master potter makes rough jars.

Crafting on the grid needs 100 units of manual work before the result can be taken. That is 100 ticks for an unskilled result at full capacity and 200 ticks for a skilled result at level 0. One body can do only one manual job at a time across the crafting grid, field jobs and workstations.

### Pottery quality

The quality of a jar is decided by the lowest effective Pottery skill the potter had at any point during the firing.

| Effective Pottery skill | Jar quality | Capacity |
| --- | --- | ---: |
| Below 5 | Rough | 2 |
| 5 to below 12 | Standard | 3 |
| 12 or more | Fine | 4 |

See [Water Pottery and Food](Water-Pottery-and-Food) for what quality changes.

## Skill gates

A gate is a minimum level needed to start a workstation recipe. Gates apply to making things, not to using them: anyone may wear armour or swing a sword that someone else forged. Default gates in the hand workshops:

| Skill | Level | Recipes |
| --- | ---: | --- |
| Weaving | 2 | Fibre pants, wool pants |
| Weaving | 4 | Fibre jacket, wool jacket |
| Construction | 2 | Crossbow stock |
| Mining | 2 | Dressed stone shot |
| Smithing | 2 | Solder |
| Smithing | 4 | Copper pickaxe, axe, hoe, sword, shovel and spear; spyglass; brush; iron round shot |
| Smithing | 8 | Bronze pickaxe, axe, hoe, sword and shovel; hammered iron plate |
| Smithing | 12 | Forged-iron pickaxe, axe, hoe, sword and shovel; iron spear; shield; crossbow; shears; bucket; flint and steel; all copper and bronze armour |
| Smithing | 14 | Iron armour; musket barrel; musket lock; machine parts |

Every other hand-workshop recipe has no minimum level: firing jars, spinning, weaving cloth, shirts and shoes, crushing mineral, forging blanks, bronze, working blooms and rivets are open to a beginner. Note that the blank for a tool can be forged by anyone, but finishing it at the anvil needs the level above.

Gated goods cannot be made on the crafting grid and are removed from merchant offers. They exist only if someone with the skill makes them. Each recipe also has a minimum manipulation capacity: 25% for pottery, 10% at the loom and for crushing mineral, and mostly 15% to 20% at the forge and anvil.

Recipes and their gates are data-pack tunable in the `production` group of `data/spawn_9b5fbb9/survival/content_rules.json`, but the shipped gates for metal equipment, textiles and crossbow stocks cannot be lowered below their defaults.

## Specialists

Nothing assigns a profession. Specialisation follows from the numbers:

- One body does one manual job at a time, and XP is paid per tick of work, so every hour spent on one skill is an hour not spent on another.
- The daily soft cap of 4,000 net XP limits how fast any one skill can grow, and decay above level 10 taxes every high skill daily.
- The top smithing gates need level 12 and 14, which is 81,000 and 115,000 XP. Since only the first 4,000 XP a day is paid in full, that is roughly 20 and 29 days of dedicated work, before decay.
- Gated goods cannot be bought or grid-crafted, so a settlement needs a smith, a weaver and a potter, and they need each other's output.

Skills survive death. The new body keeps every level, its XP and its passions.

## Commands

| Command | Use |
| --- | --- |
| `/survival skills` | Open the skills page and list every skill: level, passion, XP to the next level, learning rate, today's net XP against the cap, and decay per day |
| `/survival skills <target>` | The same list for another actor within 8 blocks (operators have no range limit) |
| `/survival recipes` | List workstation recipe names |
| `/survival recipeinfo <recipe>` | Station, inputs, skill, minimum level, work ticks and whether you currently qualify, for a workstation recipe or a crafting recipe |
| `/survival planning` | Your capacities, skills, current job, crop calendar and a paged recipe catalogue |
| `/survival jobs` | Your current manual job and its progress |
| `/survival cancel` | Cancel the current job and return reserved supplies. No XP is paid. |
| `/survival forage <pos>`, `/survival hunt <animal>` | Start a timed gathering job or a hunt |

The skills page can also be opened from the health screen (**H**) with the "Skills and interests" button.

Operator commands:

| Command | Use |
| --- | --- |
| `/survival debug_skill <target> <skill> <level>` | Set a skill to a level from 0 to 20 |
| `/survival debug_passion <target> <skill> <passion>` | Set a passion to `none`, `interested` or `burning` |

The same player commands exist for other actors under `/survival control <actor> ...`.

## Common mistakes

- **Expecting to learn quickly.** With the default passion of None you learn at 35%. Reaching level 12 in Smithing is weeks of work, not an afternoon.
- **Spreading effort.** Five skills at level 4 unlock almost nothing. One skill at level 12 unlocks iron tools for the whole group.
- **Working while unfit.** Work speed and jar quality follow your current capacities. Eat, drink, warm up and rest before a job that matters.
- **Cancelling near the end.** XP is paid only when the job completes.
- **Leaving the station.** Manual work stops when you are more than six blocks from the workstation, so nothing is produced and nothing is learned.
- **Re-mining placed stone for practice.** Positions that have been mined or built on never pay Mining XP again.
- **Letting a high skill lapse.** Above level 10 a skill decays every day. A level 14 smith who stops for a season will have to climb back.
- **Forging a blank you cannot finish.** Blanks have no gate but the finished tool does. Check `/survival recipeinfo` first.
