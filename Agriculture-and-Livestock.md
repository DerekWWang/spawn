---
title: Agriculture and Livestock
---
{% include nav.html %}

# Agriculture and Livestock

Farming runs on the calendar and the weather. A crop can be sown only in its season, it grows only on days when its soil is moist and the air is warm enough, and it needs a fixed number of such days whatever you do to it. Bone meal does nothing. Animals need feed and water every day, take weeks to grow, carry their young for a real term, and give milk, wool and eggs only when they are well kept. This page gives the calendar, every crop's schedule, the soil and frost rules, and the rules for keeping, breeding and working stock.

## The calendar

| Unit | Length |
| --- | --- |
| Game hour | 1,000 ticks |
| Game day | 24,000 ticks |
| Season | `seasonDays` game days, 28 by default, configurable from 4 to 112 |
| Year | Four seasons: spring, summer, autumn, winter |

A new world starts on spring day 1 of year 1. Time skipped by sleeping counts. `/survival calendar` and the survival panel show the season, the day of the season and the year. The season length is fixed when a Tectonic world is created: see [Weather Climate and Geography](Weather-Climate-and-Geography).

## Field work

Every field task is a timed job done within 4 blocks of the target. The times are base work ticks. You work at the lower of your manipulation and mental capacity, times 50% at skill level 0 rising to 100% at level 20, so a beginner takes twice the base time. Each work tick costs 0.0005 hunger. See [Building Crafting and Workstations](Building-Crafting-and-Workstations) for the rule that you can do only one manual job at a time.

| Task | How to start | Base ticks | Skill | Notes |
| --- | --- | --- | --- | --- |
| Till | Right-click dirt, a grass block or a dirt path with a hoe, or `/survival till <x> <y> <z>` | 100 | Farming | The block above must be empty. The hoe takes 1 wear |
| Sow | Right-click with the seed, or `/survival sow <x> <y> <z>` on the empty position above the soil | 60 | Farming | One seed is set aside and returned if you cancel |
| Irrigate | Right-click farmland with an open pot of water, or `/survival irrigate <x> <y> <z>` | 60 | Farming | Tectonic worlds only |
| Harvest | Left-click the crop, or `/survival harvest <x> <y> <z>` | 60 | Farming for sown plants, Foraging for wild ones | |
| Gather | Click a wild stand, or `/survival gather <x> <y> <z>` | 40 | Foraging | |

Crops cannot be broken by holding the mouse button. The first left-click starts the harvest job.

## Crops

Seasons are when the crop may be sown. Days are days of suitable conditions, not days on the calendar.

| Crop | Planted with | Sow in | Growth days | Needs warmth | Killed by frost | Needs flooding |
| --- | --- | --- | --- | --- | --- | --- |
| Wheat | Wheat seeds | Spring, autumn | 8 | no | no | no |
| Carrots | Carrot | Spring, summer | 8 | no | no | no |
| Potatoes | Potato | Spring | 10 | no | yes | no |
| Beetroot | Beetroot seeds | Spring, autumn | 6 | no | no | no |
| Rice | Rice seeds | Spring | 12 | yes | yes | yes |
| Maize | Maize seeds | Spring from day 15, summer to day 18 | 10 | yes | yes | no |
| Flax | Flax seeds | Spring, summer | 8 | no | no | no |
| Yarrow | Yarrow seeds | Spring | 8 | no | no | no |
| Bramble | Bramble seeds | Spring, summer | 8 | no | no | no |
| Fibre crop | Fibre seeds | Spring, summer | 6 | no | no | no |
| Medicinal crop | Medicinal seeds | Spring | 8 | no | no | no |
| Melon | Melon seeds | Spring from day 15, summer to day 18 | 10 for the vine, then 3 per fruit | yes | yes | no |
| Pumpkin | Pumpkin seeds | Spring, summer | 12 for the vine, then 4 per fruit | no | no | no |
| Sweet berry bush | Sweet berries | Spring, autumn | 8 to establish, then 2 per picking | no | no | no |
| Cave vines | Glow berries | Spring, autumn | 8 to establish, then 2 per picking | no | no | no |
| Cocoa | Cocoa beans | Spring, summer | 12 | yes | yes | no |
| Sugar cane | Sugar cane | Spring, summer | 6 per block of height | yes | yes | no |
| Mushrooms | Mushroom | Any season | 4 | no | no | no |

Notes on the table:

- The maize and melon window is given in fixed day numbers. With the default 28-day season it is the second half of spring and the first 18 days of summer.
- All field crops and melon and pumpkin vines need prepared farmland that is already moist when you sow.
- No wild stand drops fibre seeds or medicinal seeds in the current rules. Flax and yarrow are the fibre and medicine crops you will actually find.
- A crop block from another mod that has no schedule does not grow at all.
- The schedule is the `crops` group of the rules file and can be changed by a data pack. See "Tuning".

## How growth is counted

A crop keeps a record of the suitable time it has accumulated. Its visible stage is simply that time divided by its growth days. It is ripe when the time is complete. The count does not depend on random ticks or on the chunk being loaded: when the crop is next looked at, the time it missed is worked out from the calendar, up to one year back.

### In a Tectonic world

The weather at the plot is replayed in steps of 600 ticks. A step counts toward growth when all of these hold:

| Condition | Rule |
| --- | --- |
| Soil water | At least 0.25 on a scale of 0 to 1 |
| Air temperature | At least 2 °C, or at least 12 °C for crops that need warmth, and below 36 °C |
| Flooding | Rice only: water beside the plot |
| Light | Mushrooms only: dim (light below 13) |
| Frost | The crop has not been killed |

There is no rule that winter stops growth. Cold does.

### In a legacy world

Without Tectonic the count is by whole days. A day counts when the farmland is moist (or, off farmland, the plant has water beside it or rain on it), the season is not winter, a warmth-loving crop stands in a biome with base temperature of at least 0.5, and rice has water beside it. Mushrooms grow in any season where it is dark and covered.

## Soil water and irrigation

In a Tectonic world every plot has its own soil water.

| Quantity | Value |
| --- | --- |
| Range | 0 (dust) to 1 (saturated) |
| Growth needs | 0.25 or more |
| New plot | Starts at 0.45 if the farmland is moist, otherwise 0.25 |
| Drying per day | 0.22, plus 0.016 for each °C above 18, plus 0.016 for each m/s of wind |
| Recharge per day | 0.9 x rain strength, plus 0.85 if there is water beside the plot |
| Roof | A roofed plot gets no rain |
| Snow | Gives no recharge |
| One irrigation | Adds 0.35 |

Soil water moves toward the balance of recharge over drying, quickly when drying is high. Two consequences are worth remembering.

- **Water beside the plot is enough by itself.** A plot with water in a neighbouring block never falls below the growth level in any weather the game produces. "Beside" means one of the four horizontal neighbours of the crop or of the farmland under it.
- **Dry spells are short.** With no rain and no water, soil loses a fixed share each day.

| Weather with no rain | A saturated plot (1.0) stays above 0.25 for | One irrigation of a plot at 0.25 (to 0.60) lasts |
| --- | --- | --- |
| 18 °C or cooler, wind 2 m/s | about 5.5 days | about 3.5 days |
| 30 °C, wind 6 m/s | about 2.7 days | about 1.7 days |

To irrigate:

1. Fill a fired pot with water and leave it open (not sealed). See [Water Pottery and Food](Water-Pottery-and-Food).
2. Right-click the farmland, or the crop on it, or use `/survival irrigate <x> <y> <z>`.
3. Wait 60 base work ticks within 4 blocks.

One portion of water is used, the pot takes its normal wear, and the farmland shows as wet. If the plot is already at 0.95 or more, nothing is used, the pot comes back unchanged and no experience is earned. Irrigation changes soil water only. It never adds growth, and it never replaces the flooding that rice needs. Only farmland can be irrigated: berry bushes, cane and mushrooms depend on rain or neighbouring water.

## Paddy rice

Rice needs water in a block beside it, at the level of the plant or of the farmland, both to be sown and for every day of growth. It also needs warmth, and frost kills it at any stage, even ripe. Build the paddy as rows of farmland each touching a water channel, sow in spring, and harvest before the first frost.

## Frost

| World | Rule |
| --- | --- |
| Tectonic | When the air at the plot falls below -2 °C, a frost-tender crop that is not yet fully grown is destroyed and drops nothing. Rice is destroyed even when ripe. Frost is judged in the order the weather happened, so a crop that ripened before the frost is safe |
| Legacy | In winter a frost-tender crop that is not fully grown is destroyed, and rice is destroyed regardless |

Frost-tender crops are potatoes, rice, maize, melon vines, cocoa and sugar cane. High fields freeze earlier: every block of height is 0.065 °C colder.

## Harvest

| Crop | Ripe | Unripe |
| --- | --- | --- |
| Rice, maize, flax, yarrow, bramble, fibre, medicinal | 3 seeds and 3 of the produce (rice grain, maize, flax fibre, yarrow leaves, blackberries, plant fibre, medicinal leaves) | 1 seed |
| Wheat, carrots, potatoes, beetroot, cocoa | The vanilla drops for a ripe plant | The vanilla drops for its stage |
| Melon, pumpkin | The fruit block that the vine set beside it | |
| Sweet berries | 2 or 3 berries, and the bush stays | Nothing |
| Glow berries | 1 berry, and the vine stays | Nothing |

Farming experience is earned for the ticks worked only when the plant was ripe. Flax fibre and yarrow leaves are prepared at a loom into plant fibre and medicinal leaves: see [Building Crafting and Workstations](Building-Crafting-and-Workstations).

## Wild plants and domestication

Every crop begins as a wild stand. Stands are described in [Natural Resources and Mining](Natural-Resources-and-Mining). The steps from wild to field are:

1. Find a stand in its habitat and in its gathering season. Bramble, rice, maize and gourds can be gathered in summer and autumn, flax and yarrow in any season but winter, wheat in spring and autumn, roots at any time.
2. Gather it (40 base ticks each). You get produce and seed. The plant is gone for good.
3. Make a hoe. The first is knapped stone bound with cordage.
4. Till ground near water, or in a rainy district, and wait for the farmland to become moist.
5. Sow in the crop's season.
6. Keep back seed from every harvest. A ripe plant returns three seeds for the one sown.

## Fruit trees

| Rule | Value |
| --- | --- |
| Wild trees | Grow in apple groves and bear in their first season |
| Planting | In spring or autumn only, on a firm block. Sneak and left-click a tree to dismantle it into a plantable tree |
| Establishment | A planted tree bears nothing for one full year (four seasons) |
| Fruiting | Summer and autumn, once per calendar year |
| Harvest | Click the base of the trunk. 60 base work ticks, Foraging |
| Yield | 6 apples |

## Berries, cane, melons and mushrooms

- **Berry bushes and cave vines** take 8 suitable days to establish and 2 between pickings, and can be picked only in summer and autumn. A wild bush found ripe counts as established.
- **Sugar cane** adds one block every 6 suitable days, to three blocks high.
- **Melon and pumpkin vines** set a fruit on a free neighbouring block when the vine is grown. The vine is ready to set the next one 3 days (melon) or 4 days (pumpkin) later, once the last fruit has been taken.
- **Mushrooms** ripen in 4 days and then, every 4 days, spread to a dark covered neighbouring spot while fewer than 8 stand within two blocks.

## Livestock

Cows, sheep, pigs and chickens are managed by the rules below. Wolves, bears and lions take stock when they are hungry, and a fence, a fire by the fold and company are what keep them off: see [Predators](predators). Other animals keep vanilla behaviour.

### Feed, water and condition

Each animal has a nutrition score from 0 to 100 and starts at 75.

| Rule | Value |
| --- | --- |
| A day with both feed and water | +20 nutrition |
| A day without one of them | -20 nutrition |
| Feeding by hand | +25 nutrition per item of the animal's breeding food |
| Juveniles grow | Only while watered and at nutrition 10 or more |
| Products and births | Only at nutrition 50 or more, with water |

| Need | Free source | Stored source |
| --- | --- | --- |
| Water | Any water within 4 blocks horizontally and 1 vertically | Trough water, 1 unit per animal per day |
| Feed | Standing on a grass block, in short grass, or on a hay block | Trough feed, 2 units per animal per day |

In a Tectonic world grazing on grass counts only while the pasture is usable: air between 2 and 34 °C and pasture soil water above 0.25. In winter and in drought, grass does not feed stock. An animal standing on a hay block is fed in any weather, and troughs work in any weather.

Weather also sets comfort. An animal is comfortable when the wind-chilled temperature is above -8 °C (-18 °C for sheep) and the air is below 38 °C, or when a fire is near. Walls reduce the wind chill. Time spent uncomfortable does not count toward growth, wool, milk or eggs.

### Troughs

A trough is crafted from 5 loose stone and 2 clay balls at a crafting table. It serves animals within 4 blocks horizontally and 1 vertically.

| Stock | Capacity | How to add | Lasts |
| --- | --- | --- | --- |
| Feed | 128 units | Right-click with wheat, wheat seeds, a carrot, a potato or any unspoiled food. Each item adds 8 units | One item feeds one animal for 4 days |
| Water | 32 units | Right-click with a pot of water. Each portion adds 1 unit | One portion waters one animal for 1 day |

`/survival trough <x> <y> <z>` does the same with the item in your main hand. Supplies are finite and are used up while you are away.

### Growing up and breeding

| Animal | Maturity | Gestation or incubation | Litter | Wait before breeding again |
| --- | --- | --- | --- | --- |
| Cow | 28 days | 12 days | 1 | 4 days after the birth |
| Sheep | 20 days | 8 days | 1 | 4 days after the birth |
| Pig | 20 days | 8 days | 2 to 4 | 4 days after the birth |
| Chicken | 12 days | 4 days of warm incubation | 1 egg | 8 days after laying |

Only days on which the juvenile was fed, watered and comfortable count toward maturity. Feeding a juvenile by hand does not age it. The other parent waits the full term plus 4 days from the mating.

To breed:

1. Have two mature adults of the same kind within 4 blocks of each other, with water in reach, neither pregnant, and both past their waiting time.
2. Feed one by hand.
3. Within one game hour (1,000 ticks), feed the other.
4. Make sure there is room: no more than 10 animals within 4 blocks counting the young to come (pigs are counted as 4), and open air above the animal.

The second animal fed becomes pregnant, or for chickens lays a fertilized egg. The birth happens when the term is complete and the mother is at nutrition 50 or more, has water and has room. Otherwise it waits. A birth costs the mother 20 nutrition. Vanilla instant breeding is disabled for these animals.

### Milk, wool and eggs

| Product | Requirements | Yield | Recovery |
| --- | --- | --- | --- |
| Milk | A cow that has calved, nutrition 50 or more, water in reach. Use a bucket | 1 bucket of milk, costs 5 nutrition | 1 well-kept day |
| Wool | A mature sheep with fleece, nutrition 50 or more, water in reach. Use shears | 2 white wool, 1 wear on the shears | 4 well-kept days |
| Eggs | An adult hen, nutrition 50 or more, water in reach | 1 egg, costs 5 nutrition | 1 day |

"Well-kept" means the recovery clock runs only while the animal is fed, watered, comfortable and at nutrition 50 or more. A hungry or thirsty sheep shows as shorn and cannot be sheared. A cow that has never calved gives no milk.

### Hatching chicks

1. Breed two adult chickens as above. One fertilized egg drops.
2. Place a hay block so that the space on top of it is within 2 blocks horizontally and 1 vertically of a lit campfire, fire or lava.
3. Right-click the hay with the fertilized egg, or use `/survival nest <x> <y> <z>`.
4. Keep the fire lit for 4 days of incubation. Time without heat does not count.

The chick hatches if fewer than 10 animals stand within 3 blocks. Ordinary eggs never hatch and cannot be thrown.

### Tools for stock work

Buckets and shears are forged and finished by a smith of level 12: see [Building Crafting and Workstations](Building-Crafting-and-Workstations). `/survival livestock <animal>` performs the feed, milk or shear action with the item in your main hand, within 4 blocks. Each useful action earns 25 Animals experience.

## Draught animals

Vehicles are pulled by animals in harness. Only adults can be harnessed, and horses, mules and donkeys must be tamed.

| Animal | Pull (horse = 1) | Pace |
| --- | --- | --- |
| Horse, mule | 1 | full |
| Camel | 1 | full |
| Donkey | 0.7 | full |
| Ox (any cow) | 1.4 | 60% |

- A team can start a gross weight (vehicle plus cargo) of 1,200 kg for each unit of pull, and must also meet the vehicle's minimum team.
- To harness, lead the animal on a rope and click the vehicle, or bring it within 8 blocks and use `/survival vehicle hitch <animal>`. `/survival vehicle unhitch` releases the team. A ridden animal cannot be harnessed, and one animal serves one vehicle.
- A team eats ten times a day. At each feed every animal takes one item of fodder from the cargo: wheat, maize, carrots, potatoes, beetroot, apples or wheat seeds. A team halted on grass grazes instead.
- Each missed feed slows the team by 5%, down to 30% of its pace. Each full feed undoes two missed ones. Once 40 missed feeds have built up, unfed animals take starvation damage at every further missed feed.

Vehicles, loads and roads are covered in [Transport and Logistics](Transport-and-Logistics). Saddles and harness are made at a workshop: see [Industrial Manufacturing](Industrial-Manufacturing).

## Tuning

Crop schedules and livestock terms are defaults from `data/spawn_9b5fbb9/survival/content_rules.json`. A data pack can override them and `/reload` applies the change.

| Group | Fields | Limits |
| --- | --- | --- |
| `crops` | `days`, `mask` (1 spring, 2 summer, 4 autumn, 8 winter, added together), `warm`, `frost`, `flooded` | Days 1 to 3,650. New crop blocks may be added with `days` and `mask` |
| `husbandry` | `maturityDays`, `gestationDays`, `productRecoveryDays`, `breedingCooldownDays` | 1 to 3,650 days. Only cow, sheep, chicken and pig |

The season length is `seasonDays` in the mod configuration.

## Commands

| Command | What it does |
| --- | --- |
| `/survival calendar` | Season, day and year |
| `/survival planning [offset] [cropOffset]` | Lists every crop's sowing window, whether it can be sown now and its growth days, with recipe planning data |
| `/survival till <x> <y> <z>` | Tills with the hoe in your main hand |
| `/survival sow <x> <y> <z>` | Sows the seed in your main hand at an empty position |
| `/survival irrigate <x> <y> <z>` | Waters farmland from the open pot in your main hand |
| `/survival harvest <x> <y> <z>` | Harvests a crop, berry bush, fruit or fruit tree |
| `/survival gather <x> <y> <z>` | Gathers a wild stand |
| `/survival livestock <animal>` | Feeds, milks or shears with the held item |
| `/survival trough <x> <y> <z>` | Stocks a trough from the held item |
| `/survival nest <x> <y> <z>` | Places a fertilized egg on a hay block |
| `/survival jobs` and `/survival cancel` | Show or stop the current job |

Operators can issue the same actions for another humanoid as `/survival control <actor> ...`.

## Common mistakes

- **Sowing out of season.** The seed is refused. Check `/survival planning` or the table above.
- **Sowing maize on spring day 1.** Its window opens on day 15.
- **Sowing on dry farmland.** New farmland must be moist first: put water within reach, wait for rain, or irrigate.
- **Fields under a roof.** A roofed plot gets no rain. Irrigate it or leave it open.
- **Using bone meal.** It has no effect on any plant.
- **Rice without a channel.** Water two blocks away is not a paddy. It must touch the plot.
- **Leaving tender crops for the frost.** Rice dies even when ripe. Maize, potatoes and melons die if unfinished.
- **Harvesting early.** An unripe plant of the mod's crops returns a single seed and no produce.
- **Counting on pasture in winter.** Below 2 °C grass does not feed stock. Lay in trough feed or stand the animals on hay.
- **A trough without water.** Feed alone does nothing. Both are needed every day.
- **Feeding two animals an hour apart.** The second must be fed within 1,000 ticks of the first.
- **Crowded pens.** Births and hatching wait until fewer than 10 animals are close by.
- **Milking a heifer.** Only a cow that has calved gives milk.
- **An open flock at night.** A hungry wolf pack, bear or lion kills stock it can reach and eats it. Fence the fold and keep a roofed fire within six blocks.
- **Unfed teams.** Carry fodder on the wagon: one item per animal, ten times a day.
