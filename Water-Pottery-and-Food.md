---
title: Water Pottery and Food
---
{% include nav.html %}

# Water, Pottery and Food

Water and food are finite, perishable and sometimes dangerous. Any water you have not boiled can poison you, and that poisoning can turn into a systemic infection that outlasts your health bar. Food carries an age from the moment it exists, spoils on a schedule that depends on what it is, and cannot be made fresh again by splitting, stacking, crafting or cooking. The fired clay jar is the tool that solves both problems: it carries and boils water, and with a lid it stores food for two to six times its normal life. This page covers water sources and safety, firing and using jars, spoilage, cooking and military rations.

One game hour is 1,000 ticks and one day is 24,000 ticks. Thirst and hunger themselves are explained in [Survival and Physiology](Survival-and-Physiology).

## Water sources and safety

| Source | Thirst restored | Safe |
| --- | ---: | --- |
| Boiled water in an open jar | 25 per portion | Yes |
| Untreated water in an open jar | 25 per portion | No |
| Vanilla water bottle | 25 | No. A bottle always counts as untreated. |
| Surface water (a water source block within 2 blocks horizontally and 1 vertically) | 10 per drink | No |

Each unsafe drink has these default risks (data-pack tunable in `physiology.json`):

| Risk | Rule key | Default |
| --- | --- | ---: |
| Poisoning | `waterPoisonChance` | 18% per drink |
| Systemic infection, if poisoned | `waterInfectionChance` | 25% |
| Poison duration | `poisonDurationTicks` | 6,000 ticks |
| Poison damage | `poisonDamageTicks` | 1 health per 1,500 ticks (4 health for a full course) |

That is roughly a 4.5% chance of systemic infection from every unsafe drink. Filling a thirst bar from surface water takes ten drinks. Boiled water carries no risk at all. Treatment for poison and infection is in [Clothing and Medical Care](Clothing-and-Medical-Care).

To drink, hold the jar or bottle in your main hand (or stand by surface water with neither) and use `/survival act drink`. The command refuses when thirst is 99 or higher. A sealed jar must be opened first. Drinking a vanilla water bottle in the ordinary way also works and returns the glass bottle.

## Firing a jar

A jar (item name: fired pot) is made at a kiln from eight clay balls, about thirty kilograms of clay. A clay block gives 480 balls.

1. Craft a kiln from 3 loose stones and 2 clay balls, and place it.
2. Add fuel by right-clicking the kiln with it, or with `/survival fuel`. Firing needs at least 2,000 heat ticks in the kiln before it will start.
3. Hold at least eight clay balls in your main hand and right-click the kiln, or use `/survival produce fire_pot`.
4. Stay within six blocks until the work is done. Firing is tended work: it stops whenever you leave, die or become unable to work.
5. Collect the jar by right-clicking the kiln with an empty hand, or with `/survival collect`.

| Firing rule | Default |
| --- | --- |
| Work required | 2,000 work units |
| Station | Kiln, fuelled, tended |
| Skill | Pottery, no minimum level |
| Minimum manipulation | 25% |
| Speed | Lower of manipulation and mental capacity x (50% plus 2.5% per Pottery level) |
| Heat used | One heat tick per tick you spend working, so a slow potter burns more fuel |
| Hunger | 0.5 per 1,000 ticks of work |

A level 0 potter in perfect condition needs 4,000 ticks and 4,000 heat ticks for one jar.

| Kiln fuel | Heat ticks |
| --- | ---: |
| Stick | 80 |
| Charcoal, coal or coke (one sack) | 8,000 |
| Log (kiln only) | 96,000 |
| Maximum heat reserve | 128,000 |

Kilns and the other workstations are described in [Building Crafting and Workstations](Building-Crafting-and-Workstations).

### Jar quality

Quality is fixed when the jar is fired and depends on the potter's effective Pottery skill, which is skill level multiplied by the lower of manipulation and mental capacity. The lowest value during the firing counts. See [Skills and Learning](Skills-and-Learning).

| Quality | Effective Pottery skill | Capacity (food stacks or water portions) | Sealed food lasts |
| --- | --- | ---: | ---: |
| Rough | Below 5 | 2 | 2 times as long |
| Standard | 5 to below 12 | 3 | 4 times as long |
| Fine | 12 or more | 4 | 6 times as long |

The jar's tooltip shows its quality, capacity, water content and whether it is sealed.

### Jar wear

A jar has 96 uses and does not stack. Each of these costs one use:

- every portion of water taken out (drinking, giving water, irrigating, filling a trough, sterilising cloth)
- every completed boil
- every stack of food stored and every stack retrieved

A jar that wears out while you are storing or retrieving food spills its remaining food, returns its lid if it was sealed and leaves 2 ceramic shards. A jar that wears out on a water use is simply gone.

## Using a jar for water

A jar holds either water or food, never both.

| Action | Command | Shortcut with the jar in your main hand | Conditions |
| --- | --- | --- | --- |
| Fill | `/survival vessel fill` | Right-click water, or a block face that touches water | Jar open and holding no food. Any water within 3 blocks horizontally and 2 vertically. Fills the jar to capacity. |
| Boil | `/survival vessel boil` | Right-click a lit campfire | Jar open and holding water. A lit campfire, fire block or lava within 3 blocks horizontally and 2 vertically. |
| Drink | `/survival act drink` | None | Jar open and holding water |
| Give water | `/survival act give_water <target>` | None | Target humanoid within 3 blocks and thirst below 99 |

### Boiling step by step

1. Fill the jar.
2. Stand beside a lit campfire, a fire block or lava and start the boil.
3. Stay within 4 blocks of that fire and keep the open jar in your main hand for 1,000 ticks (one game hour, 50 seconds of real time at normal speed). Progress is counted once per second and only while the jar is in your main hand.
4. The message "Water boiled: safe to drink." confirms the whole jar is now safe.

Moving more than 4 blocks from that fire, the fire going out, or sealing the jar resets the progress to zero. One boil treats every portion in the jar, so a fine jar gives 4 safe drinks (100 thirst) per boil and a rough jar gives 2.

Filling always marks the whole jar as untreated. Topping up a jar that still holds boiled water throws that boil away.

Jar water is also used to irrigate farmland (right-click prepared farmland with an open water jar, or `/survival irrigate <pos>`), to water a livestock trough, and, when boiled, to sterilise cloth. See [Agriculture and Livestock](Agriculture-and-Livestock) and [Clothing and Medical Care](Clothing-and-Medical-Care).

## Using a jar for food

| Action | Command | Shortcut: sneak and right-click any block with the jar in your main hand | Conditions |
| --- | --- | --- | --- |
| Store | `/survival vessel store` | Food in the offhand | Jar holds no water and has a free place. The offhand item must be food and must not itself be a container. The whole offhand stack goes in as one place. |
| Retrieve | `/survival vessel retrieve` | Empty offhand | Takes out the most recently stored stack |
| Seal | `/survival vessel seal` | Lid in the offhand | Consumes one pot lid |
| Open | `/survival vessel seal` again | None | Returns the lid |

A pot lid is made on the crafting grid from 1 clay ball and 1 loose stone.

Rules to remember:

- Capacity counts stacks, not items. A fine jar holds four stacks of any size.
- Preservation applies only while the jar is sealed. Food in an open jar ages at the normal rate.
- Storing or retrieving opens a sealed jar and hands back the lid. Seal it again afterwards.
- Jars cannot be nested and food already inside a container cannot be stored.
- A jar keeps its contents and their ages when it is carried, dropped or put in a chest.

## Food ageing and spoilage

Every food stack carries an age. When the age reaches the food's shelf life the stack is spoiled.

| Food | Shelf life (default) | Rule key |
| --- | ---: | --- |
| Raw meat and fish: beef, pork, chicken, mutton, rabbit, cod, salmon | 0.5 day | `rawMeatDays` |
| Cooked food: anything cooked, bread, stews | 1 day | `cookedFoodDays` |
| Dry grain: wheat, rice grain, seeds | 7 days | `dryGrainDays` |
| All other food: vegetables, fruit, berries, maize | 3 days | `produceDays` |

Where the food is kept changes how fast it ages:

| Where | Ageing rate | Rule key |
| --- | ---: | --- |
| Carried by a player or held by another actor | 100% | |
| In a chest or other block container | 90% | `storageRate` |
| In an open jar | 100% | |
| In a sealed rough jar | 50% | `sealedPotRate` x 3 |
| In a sealed standard jar | 25% | `sealedPotRate` x 1.5 |
| In a sealed fine jar | 16.7% | `sealedPotRate` (default one sixth) |
| Lying on the ground | 100% | |
| Lying on the ground in rain | 200% | `rainMultiplier` |
| Lying on the ground in a hot biome | 150% | `hotMultiplier` |

Rain and heat multiply together, so food left out in hot rain ages three times as fast.

Resulting shelf life in a sealed jar:

| Food | Open | Rough jar | Standard jar | Fine jar |
| --- | ---: | ---: | ---: | ---: |
| Raw meat and fish | 0.5 day | 1 day | 2 days | 3 days |
| Cooked food | 1 day | 2 days | 4 days | 6 days |
| Other produce | 3 days | 6 days | 12 days | 18 days |
| Dry grain | 7 days | 14 days | 28 days | 42 days |

Age cannot be cheated:

- Stacks of different ages do not merge.
- Splitting a stack keeps the age on both halves.
- Anything crafted or processed from food inherits the age of its oldest ingredient, and is spoiled if any ingredient was spoiled.
- Sealing slows the clock. It never turns it back.

The tooltip of every food shows "Freshness age" in days, or "SPOILED".

### Eating

Eating restores 5 hunger per vanilla nutrition point. The mod's own foods:

| Food | Nutrition | Hunger restored |
| --- | ---: | ---: |
| Rice grain | 4 | 20 |
| Maize | 4 | 20 |
| Wild berries | 2 | 10 |
| Blackberries | 2 | 10 |
| Any spoiled food | 1 | 5 |

| What you eat | Poisoning chance | Systemic infection, if poisoned |
| --- | ---: | ---: |
| Spoiled food of any kind | 100% | 15% |
| Raw beef, pork, chicken, mutton, rabbit, cod or salmon, rotten flesh, poisonous potato | 40% (`rawFoodPoisonChance`) | 15% (`rawFoodInfectionChance`) |
| Anything else that is not spoiled | None | None |

Commands: `/survival act eat` starts eating the food in your main hand and refuses at hunger 99 or higher. `/survival act feed <target>` gives one item of your held food to a hungry humanoid within 3 blocks whose offhand is free; sneak-interacting with them while holding food does the same.

## Cooking

Meat and fish are cooked in the kiln. The vanilla furnace, blast furnace and smoker cannot be opened, and the vanilla furnace, smoker and campfire recipes for the seven meats are disabled.

| Kiln process | Input | Output | Time | Tended |
| --- | --- | --- | ---: | --- |
| `cook_beef`, `cook_porkchop`, `cook_chicken`, `cook_mutton`, `cook_rabbit`, `cook_cod`, `cook_salmon` | 1 raw | 1 cooked | 1,000 ticks | No |

Right-click a fuelled kiln with the raw meat in your main hand, or use `/survival produce cook_beef` and so on. The kiln needs at least 1,000 heat ticks to start. Cooking runs unattended while the kiln has heat, and it handles one item per job. Use `/survival station` to read progress and `/survival collect` to take the result.

Cooking does three things. It removes the 40% raw-meat poisoning risk. It moves the food onto the 1-day cooked shelf life in place of the half-day raw one. It keeps the age the meat already had, so meat cooked at 0.4 days old has 0.6 days left, not a full day. Cooking spoiled meat gives spoiled cooked meat.

Unattended kiln cooking does not train the Cooking skill. Cooking is trained by crafting edible things on the crafting grid, which is timed manual work like all grid crafting.

## Rations

Enlisted non-player soldiers eat and drink from an abstract pack and canteen so that an army can be supplied in bulk. The full rules are in [Military Service and Combat](Military-Service-and-Combat); the numbers that concern food are these.

| Rule | Value |
| --- | --- |
| Ration value of a food item | Its nutrition divided by 4, rounded, and at least 1 |
| Enlistment bounty for a volunteer | At least 4 rations of unspoiled food |
| Eating | A soldier below hunger 60 eats 1 ration and regains 20 hunger |
| Resupply | A soldier with fewer than 4 rations draws food from a supply vehicle within 8 blocks. Spoiled food is not drawn. |
| Canteen | Refilled to 4 drinks whenever water is within 4 blocks. A soldier below thirst 60 drinks 1 and regains 25 thirst. |
| Canteen safety | Canteen water counts as untreated, so soldiers run the normal 18% poisoning risk per drink |

Rations in a soldier's pack are a simple count. They are no longer items and do not age.

## Tuning

Shelf lives, storage rates and weather multipliers are in the `decay` group of `data/spawn_9b5fbb9/survival/content_rules.json`, which also accepts a `shelfDays` map that sets the shelf life of individual items. Kiln processes are in the `production` group of the same file. Water and food risks are in `physiology.json`. Override either file in a data pack and run `/reload`. Jar capacities, the 1,000 tick boil, the 96 uses and the thirst restored per drink are fixed in code.

## Commands

| Command | Use |
| --- | --- |
| `/survival vessel <fill\|boil\|seal\|store\|retrieve>` | Jar actions, with a fired pot in your main hand |
| `/survival act drink` | Drink from the held jar or bottle, or from surface water |
| `/survival act eat` | Eat the held food |
| `/survival act feed <target>` | Feed a humanoid within 3 blocks |
| `/survival act give_water <target>` | Give a portion from your open jar to a humanoid within 3 blocks |
| `/survival recipes` | List workstation process names |
| `/survival produce <recipe>` | Start a process at the matching station within six blocks |
| `/survival fuel` | Add the held fuel to the nearest kiln, bloomery or forge within six blocks |
| `/survival station` | Show the nearest station's process, progress, heat and output |
| `/survival collect` | Take finished output from the nearest station |
| `/survival cancel` | Take back an unfinished input |

The same commands exist for other actors under `/survival control <actor> ...` (operator only).

## What kills you and common mistakes

- **Drinking from the river.** Every unsafe drink is an 18% poisoning roll, and surface water gives only 10 thirst a drink, so you roll often. One in four poisonings becomes a systemic infection.
- **Trusting a water bottle.** A bottle filled from a boiled jar does not exist; bottles always count as untreated.
- **Walking away from the boil.** Leaving the 4-block range resets the full 1,000 ticks.
- **Topping up boiled water.** Filling marks the whole jar untreated again.
- **Trying to drink from a sealed jar.** Open it first.
- **Storing food and forgetting the lid.** An open jar preserves nothing.
- **Retrieving one stack and walking off.** Retrieval opens the jar. Seal it again or the rest ages at full speed.
- **Eating raw meat when desperate.** A 40% poisoning chance costs more than the meal gives. Cook it.
- **Eating spoiled food.** It always poisons and restores only 5 hunger.
- **Hoarding raw meat.** Half a day of shelf life means a morning kill is spoiled by evening unless it is cooked or sealed.
- **Expecting cooking to reset freshness.** Cooked food keeps the age of the raw meat.
- **Running a kiln with no fuel reserve.** A slow or injured potter needs more heat than the 2,000 tick minimum, and the work pauses when the heat runs out.
