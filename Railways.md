---
title: Railways
---
{% include nav.html %}

# Railways

A railway is the only way to move tens of tonnes overland, and it is the most expensive thing most settlements will ever build. Track is laid a metre at a time from rolled steel, a locomotive is twenty-four tonnes of machine that takes two hours to raise steam, and a train takes 150 metres to stop.

## Track

A line is three blocks wide: a centre block and a shoulder each side. It cannot be placed from the inventory; it is laid as timed work.

| One metre of line takes | |
| --- | --- |
| Steel rail | 2 m (31 kg a metre) |
| Sleepers | 2 |
| Gravel ballast | 2 m3 |
| Spikes (rivets, forged four to the kilogram from iron) | 8 |
| Labour | 2.5 man-hours of construction work |

**Laying.** Use a steel rail on the ground, or `/survival lay_track <pos>`. The line runs along the direction you face. The materials may be in your inventory, in a container within three blocks, or in a vehicle within six, so a wagon of rail can follow the railhead. Nothing is placed or consumed until the work is complete.

**The bed** must be clear and firm across all three blocks.

**Gradient.** A railway climbs one block in thirty. A step up or down is refused if another step lies within thirty blocks along the line.

**Curves** are right-angle turns of the centre line. A train must take them at 11 km/h or less.

Breaking the centre block returns two rails and two sleepers; the shoulders come away with it.

What a line costs: a kilometre is 62 tonnes of steel rail, 2,000 sleepers (about 290 logs), 2,000 m3 of gravel and 2,500 man-hours.

## Rolling stock

| Vehicle | Weight empty | Carries | Seats |
| --- | --- | --- | --- |
| Locomotive | 23.9 t | 5,000 kg in the bunker, 3,000 L of water | 2 |
| Goods wagon | 4.75 t | 10,000 kg | none |
| Carriage | 7.2 t | 1,000 kg and passengers | 8 |

All are built at an assembly yard and roll out onto track within eight blocks of it. A locomotive is 14 t of steel, 5 t of plate, 3 t of cast iron, 1,200 precision parts and 700 kg of copper, and 12,000 man-hours. Rolling stock cannot be set down off the rails.

## Running an engine

An engine moves only when all of these are true:

| Need | Detail |
| --- | --- |
| Fuel | Coal, coke or charcoal loaded into the bunker (sneak-click with the sacks) |
| Water | The tender fills at 60 L a second while the engine stands within four blocks of water |
| Steam | A cold boiler takes two hours of the game clock (2,000 ticks) to reach working pressure and four to reach full pressure, and only while someone is on the footplate with fuel and water. It moves at half pressure or better. Left untended, a full boiler falls below working pressure in two and a half hours |
| A driver | In the first seat |

Drive with the forward and back keys, or `/survival vehicle drive <pos>` to have the driver run to a point on the line and brake for it. A place beside the line is reached when the engine is abreast of it, and a train sent beyond the railhead stops at the last rail. `/survival vehicle halt`, from aboard, has the driver brake to a stand.

| Consumption | Rate |
| --- | --- |
| Standing in steam | A sack of coal every 2,400 ticks: about 10 kg an hour |
| Running | A sack of coal every 1.8 km, about 14 kg a kilometre (coke 1.95 km, charcoal 1.65 km), plus 15% for each vehicle behind |
| Water | 100 L a kilometre, plus 15% for each vehicle behind |
| Lubricant | 1 L every 50 km |

Full tanks last about 30 km light engine.

## Trains

`/survival vehicle couple` joins the nearest uncoupled wagon or carriage to the engine or to the end of its train; `/survival vehicle uncouple` drops everything from the chosen vehicle back. The train must be stopped. An engine takes up to twelve vehicles.

Performance depends on the gross weight of the whole train.

| Rule | Value |
| --- | --- |
| Top speed | 43 km/h up to 150 tonnes gross, falling in proportion above that |
| On a gradient | 0.6 of that, and less again above 60 tonnes |
| Acceleration | 0.2 m/s2 up to 80 tonnes, less in proportion above |
| Braking | 0.5 m/s2: about 150 m from full speed |
| Coasting | Very little resistance: a train with steam shut off rolls a long way |

An engine with twelve loaded goods wagons weighs about 205 tonnes, tops out near 31 km/h and takes several minutes to reach it.

## Accidents

| Cause | Result |
| --- | --- |
| A curve taken above 11 km/h | Derailment: the engine and every vehicle are damaged and the crew hurt |
| Reaching the end of the line faster than about 6 km/h | The same |
| Backing round a curve above 11 km/h, or backing off the last rail faster than about 6 km/h | The same: the rear of a train backing up leads it |
| Running out of fuel or water | The train stops where it is |
| Running without lubricant | Damage with every block until the engine is wrecked |

There are no buffer stops. A driver ordered to a point brakes in time; a player has to.

## Common mistakes

- Boarding a cold engine and expecting it to move. Raise steam first; the status line shows the pressure.
- Laying a step in the line too close to the last one.
- Entering a curve at speed.
- Building the yard away from the line. A finished locomotive needs track within eight blocks to roll out onto.
- Stopping away from water with a low tender.
