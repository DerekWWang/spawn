---
title: Transport and Logistics
---
{% include nav.html %}

# Transport and Logistics

Nothing heavy moves on a person's back. Ore, fuel, metal and food go by cart, wagon, lorry or rail, and each of those is limited by what it weighs, what pulls it, what it burns and the ground under it. This page covers road vehicles; [Railways](Railways) covers track and trains. Weights are on [Units and Realism Rules](Units-and-Realism-Rules).

## The vehicles

| Vehicle | Moved by | Payload | Unladen | Seats | Top speed on a made road |
| --- | --- | --- | --- | --- | --- |
| Cart | 1 draught animal | 500 kg | 250 kg | 1 | 10 km/h |
| Wagon | 2 to 4 | 2,000 kg | 700 kg | 2 | 9 km/h |
| Chariot | 2 horses | 100 kg | 60 kg | 2 (driver and fighter) | 30 km/h |
| Motor truck | Combustion engine | 3,000 kg | 2,900 kg | 2 | 25 km/h |

Artillery pieces are also drawn vehicles with small ammunition stores; see [Siege Artillery](Siege-Artillery).

Vehicles are built at an assembly yard ([Industrial Manufacturing](Industrial-Manufacturing)) and roll out onto clear ground beside it. A cart is a frame, two wheels and a harness; a truck is a combustion engine, two tonnes of steel and plate, four rubber tyres and more.

## Using a vehicle

| Action | How |
| --- | --- |
| Harness an animal | Lead it to the vehicle on a lead and click the vehicle, or `/survival vehicle hitch <animal>` |
| Release the team | Sneak-click with an empty hand when the bed is empty, or `/survival vehicle unhitch` |
| Board | Click the vehicle, or `/survival vehicle board`. The first seat drives |
| Drive | Movement keys from the driver's seat, or `/survival vehicle drive <pos>` to have the driver steer for a point and stop there. `/survival vehicle halt` cancels |
| Load | Sneak-click holding goods, or `/survival vehicle load` to load every matching stack you carry |
| Unload | Sneak-click with an empty hand, or `/survival vehicle unload [item] [count]` |
| Supply a works | `/survival vehicle deliver` hands everything the works' recipe uses straight across, including fuel, when drawn up within six blocks |
| Collect from a works | `/survival vehicle pickup` takes finished goods on, up to the weight limit |
| Inspect | `/survival vehicle status` |

Worn tools and armour are not bulk cargo and are refused.

## Cargo goes by weight

A vehicle carries kilograms, not stacks. A cart takes 500 one-kilogram ingots, or 20 sacks of coal, or five 85 kg sleepers, and will not take a 600 kg log at all if it already carries 400 kg. Loading stops at the limit and leaves the rest in your hand.

## Teams

| Animal | Pull (horses) | Pace |
| --- | --- | --- |
| Horse, mule, camel | 1.0 | Full |
| Donkey | 0.7 | Full |
| Ox (cattle) | 1.4 | 0.6 of full |

Animals must be adult; horses, mules and donkeys must be tamed. A ridden animal cannot be harnessed, and an animal serves one vehicle at a time.

Two rules decide whether a vehicle moves:

1. The team's combined pull must reach the vehicle's minimum (1 for a cart, 2 for a wagon or chariot).
2. A horse starts about 1,200 kg gross on a made road. Unladen weight plus cargo must not exceed 1,200 kg per horse of pull. A wagon with its full two tonnes weighs 2,700 kg and needs three horses, not two. The status line says how many are needed.

A stronger team than the minimum is somewhat faster, up to a fifth.

## Fodder

Every animal in harness eats one kilogram of fodder from the vehicle's load at each feed, and there are ten feeds a day. Fodder is taken by weight: a hay bale is nine feeds, a kilogram of wheat or maize is one, and roots, apples and seed at about 300 g each go three or four to a feed. If the load has none, an animal standing on grass grazes instead, but only while the vehicle is halted.

| Missed feeds | Effect |
| --- | --- |
| Each one | Team's pull falls by 5%, to a floor of 30% |
| 40 or more | Each further missed feed injures the animal until it starves |
| Each feed eaten | Recovers two missed feeds |

A horse needs 10 kg a day. A four-horse wagon on a ten-day haul eats 400 kg of its own two tonnes.

## Fuel and grease

| Vehicle | Burns |
| --- | --- |
| Motor truck | 1 L of petrol every 3 km, or 1 L of fuel alcohol every 2 km, taken from its load |
| Every wheeled vehicle | 1 L of lubricant (tallow or oil) every 50 km, taken from its load |

A lorry burns 33 L of petrol per 100 km.

A lorry burns 33 L of petrol per 100 km.

A vehicle leaves the yard freshly greased. Run dry, its bearings take damage with every block travelled and it is wrecked after about 20 km. Tallow is rendered at a kiln from four pieces of raw pork, mutton or beef; lubricating oil comes from refining crude.

## Pace

Top speed is reduced by, in order:

| Factor | Effect |
| --- | --- |
| Load | Divided by 1 + 0.6 x (load / payload): a full vehicle makes five eighths of its empty pace |
| Ground | Made road (path, gravel, cobble, stone, stone brick, planks, slabs, brick): full. Turf and dirt: 0.7. Sand, snow, mud, farmland, clay, soul sand: 0.4. Anything else: 0.6 |
| Rain | Off the road, a further 0.8 |
| Water | A quarter |
| Lorries and chariots | The ground factor counts twice: a lorry on turf makes half speed, on sand a sixth |
| Driver | 80% of pace with no riding skill, rising to 100% at Riding 20 |

Nothing starts or stops at once. A team or a lorry gains about half a metre per second each second. A team stops at 1.5 m/s2 and a lorry at 3. A driver sent to a point slows early enough to stop there.

## Damage and wrecks

Blows of less than 2 damage do nothing. A vehicle destroyed by damage spills its cargo, frees its team, throws its passengers, and leaves scrap. Any gunpowder, powder charges, cartridges or shells aboard explode.

| Vehicle | Health |
| --- | --- |
| Chariot | 24 |
| Cart | 30 |
| Wagon | 50 |
| Motor truck | 80 |

## Supplying an army

Soldiers draw rations from any vehicle carrying food within eight blocks. A wagon of food and fodder is what lets a force stay in the field. See [Military Service and Combat](Military-Service-and-Combat).

## Common mistakes

- Loading a wagon to its limit behind the minimum team. It will not start.
- Setting out without fodder across ground with no grass.
- Driving a lorry across country. It needs a road.
- Forgetting lubricant on a long haul.
- Trying to carry logs or stone on a cart: one cubic metre of timber is more than a cart's whole payload.
