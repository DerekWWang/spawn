---
title: Siege Artillery
---
{% include nav.html %}

# Siege Artillery

Walls are taken by battering them down. Four kinds of ordnance do it, each built at real weight, served by a real detachment and fed real powder and shot. Shot flies at its true speed under gravity and air drag, so laying a piece is solving a real trajectory.

## The pieces

| | Cannon | Bombard | Trebuchet | Field gun |
| --- | --- | --- | --- | --- |
| What it is | Bronze six-pounder | Wrought-iron siege gun | Counterweight engine | Rifled steel breech-loader of the 1870s |
| Era | Charcoal and bronze | Charcoal and iron | Timber and rope | Steel and machine tools |
| Round | 3 kg round shot | 40 kg stone shot | 40 kg stone shot | 8 kg explosive shell |
| Charge | 1 powder bag (0.5 kg) | 20 powder bags (10 kg) | None | 4 powder bags (2 kg) |
| Muzzle or release speed | 440 m/s | 220 m/s | 50 m/s | 455 m/s |
| Elevation | -5 to 15 degrees | 0 to 20 | 45 to 78 (high arc only) | -5 to 18 |
| Reach on level ground | About 2.2 km | About 1.6 km | 95 to 230 m | About 6.2 km |
| Full detachment | 8 | 8 | 12 | 6 |
| Loading with a full, skilled detachment | 30 seconds | About 4 minutes (five game hours) | 50 seconds (one game hour) | 30 seconds |
| Moved by | 4 to 6 horses, 7 km/h | 6 to 10 horse-equivalents, 2 km/h | Not at all: built where it shoots | 4 to 6 horses, 9 km/h |
| Weight | 730 kg | 5,000 kg with its timber bed | 18,000 kg | 1,200 kg |
| Ammunition store | 300 kg | 600 kg | 600 kg | 300 kg |
| Dispersion (before skill) | 0.5 degrees | 1.2 | 1.0 | 0.15 |
| Chance to burst | 0.2% a shot, rising with use | 3% a shot, rising with use | None | None |

A shot that leaves the loaded world is lost, so in practice a piece can only engage what is in the loaded world around it, far inside these reaches. At siege distances a cannon ball arrives in a fraction of a second on a nearly flat line.

## Building them

All are assembled at an assembly yard ([Industrial Manufacturing](Industrial-Manufacturing)).

| Piece | Needs |
| --- | --- |
| Cannon | A barrel cast at a foundry from 400 kg of bronze (or 450 kg of cast iron), a gun carriage, rivets |
| Bombard | A barrel forged from 1,500 kg of wrought iron, logs, iron, cordage |
| Trebuchet | 16 logs, 16 planks, 64 cordage, 100 kg iron, 4 m3 of stone for the counterweight; 2,000 man-hours |
| Field gun | 1,000 kg steel, 60 precision parts, wheels, plate; 3,000 man-hours, Engineering 14 |

Ammunition: round shot is forged from 3 kg of iron or cast a hundred at a time at a foundry; stone shot is dressed eight to a cubic metre of cobblestone at an anvil; powder bags of half a kilogram are sewn sixteen from 8 kg of gunpowder at a workshop; shells are turned in a machine shop from 7 kg of steel and 1 kg of powder.

## Serving a piece

1. **Stock it.** Click the piece holding shot or powder bags, or sneak-click to load a stack into its ammunition store.
2. **Man it.** `/survival siege man` within five blocks, a `crew` order to soldiers, or take the gunner's seat. `/survival siege leave` stands down.
3. **The crew loads.** Loading draws one round and its charge from the store and proceeds at a pace set by how many of the detachment are present, their fitness and their Gunnery. One man serving an eight-man gun works at an eighth of the pace. Extra hands beyond the detachment add little.
4. **Fire.** From the gunner's seat, look along the barrel and jump. Or lay the piece on a point with `/survival siege fire <pos>` (or a `bombard` order): it fires when loaded and keeps firing while ammunition lasts. `/survival siege cease` stops it. `/survival siege status` reports its state.

Laying fails if the target is beyond reach ("move it closer") or too close for the mounting to depress or steepen onto ("move it back"). A trebuchet cannot hit anything nearer than about 95 m.

Every shot is Gunnery practice for the whole detachment.

## Accuracy

Aim error is the piece's dispersion multiplied by 1.6 at Gunnery 0, falling to 0.7 at Gunnery 20. A computer block within eight blocks of the piece halves it. Loose powder does not like rain: a powder piece fired in rain misfires one time in four and the charge is spoiled.

## What shot does

**To people.** Shot ploughs through everything alive in its path, losing 30% of its force at each body.

| Piece | Damage to a body |
| --- | --- |
| Cannon | 36 |
| Bombard | 60 |
| Trebuchet | 40 |
| Field gun shell | Bursts on the first thing it meets, as an explosion |

Shot landing near soldiers shakes their morale: see [Military Service and Combat](Military-Service-and-Combat).

**To walls.** Damage accumulates. Each block has a resistance, and each shot spends its power against the block it strikes. If the power exceeds what is left of the block, the block breaks and the shot carries on with the remainder; if not, the block is left cracked and weaker for the next shot.

| Material | Resistance |
| --- | --- |
| Glass, leaves and other soft blocks | 0.5 |
| Planks and other timber | 3 |
| Logs | 4 |
| Ordinary stone and brick | 6 |
| Earth, sand, gravel, clay | 10 |
| Very hard blocks | 14 |
| Obsidian | 60 |

| Piece | Power per shot |
| --- | --- |
| Field gun (solid hit) | 8 |
| Cannon | 9 |
| Trebuchet | 13 |
| Bombard | 26 |

A cannon ball breaks one block of masonry and cracks the one behind it. A bombard stone goes through four. Earth soaks up cannon fire that would breach stone, which is why forts grew earthen ramparts.

## Moving ordnance

Guns are drawn vehicles and obey every rule on [Transport and Logistics](Transport-and-Logistics): a team that meets the minimum, enough pull for the gross weight, fodder, grease, and real acceleration. Powder in the store of a wrecked piece explodes.

## Common mistakes

- Manning a gun with one or two men and wondering why it loads slowly.
- Bringing shot without powder bags. A bombard needs twenty for every stone.
- Siting a trebuchet within 95 m of its target.
- Firing powder pieces in the rain.
- Battering an earth bank with a cannon.
- Running a bombard hard. The chance of a burst barrel rises with every shot fired.
