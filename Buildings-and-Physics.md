---
title: Buildings and Physics
---
{% include nav.html %}

# Buildings and Physics

One block is one cubic metre of a real material. Structures carry load, stand up to weather or fail, and age. A building is whatever you put up around a cornerstone, and what makes it a house or a store is what it is used for.

## Materials

Block tags (`physics/material/*`) sort blocks into thatch, timber, glass, brick, concrete, reinforced concrete, iron, stainless steel, earth and masonry. Each has a mass, a bending strength, a crushing strength, a wind resistance and a life span.

## Load, weather and decay

- **Nothing floats.** When a player or an explosion removes a block, anything it held up that no longer reaches the ground falls. Cut the trunk and the tree comes down; undermine a wall and it drops on whoever is below. Natural terrain is left alone, and groups of more than 512 blocks are taken to be terrain.
- **Spans are earned.** Each block of an overhang carries everything beyond it. Timber reaches four blocks from its support, stone and brick two, reinforced concrete five, steel eight; glass and earth almost nothing. A joint is as strong as the weaker of its two materials. Posts, arches, buttresses and hangers all work, because the load path is computed rather than matched to patterns: any shape that would stand, stands.
- **Buildings are judged.** Registered buildings are analysed for load. Parts stressed past 150% come down at once. Parts over 100% last until the next thunderstorm, whose wind load grows with height and exposure, and which strips thatch long before stone.
- **Water pushes.** A barrier is checked metre by metre against the water standing behind it, for sliding and for overturning. A masonry wall needs thickness; timber, steel and reinforced concrete can stand thin if they are driven into the ground. Storms add a 2 m surge. A barrier that fails is breached where it is weakest.
- **Materials age.** Thatch lasts about 60 days dry and 8 under water. Timber lasts 400 days dry, 60 in the rain and 25 submerged. Iron rusts, earth washes away, and brick and concrete outlast everyone. Worn blocks crack or grow moss before they go. A timber seawall is cheap and soon rebuilt; a concrete one is dear and stays.

Siege damage to blocks is a separate rule: see [Siege Artillery](Siege-Artillery).

## Buildings

A **building** is whatever stands in a box around a **cornerstone** (crafted from stone and a stick). The game does not prescribe its shape or material: a house of clay, brick or steel is a house. What makes it one is its **use**, and each use asks something of the structure.

| Use | Needs | Gives |
| --- | --- | --- |
| Structure | Soundness only | |
| Residence | An enclosed, roofed room with a door, and 8 blocks of air per bed | Beds for residents |
| Storage | Containers, enclosed under a roof to keep goods | Counted stock and slower spoilage ([Logistics and Records](Logistics-and-Records)) |
| Pen | Ground closed in by fences or walls | Room for livestock, 3 blocks each |
| Barrier | To hold the water standing against it, and a storm surge | Protected land |

Right-click the cornerstone for its menu. *Survey* shows the box and every problem with its coordinates. You can also fit the box to what is built, set the use, rename the building, and grow or shrink a side.

## Schemas

A **schema** is a building's design, saved by name: its blocks relative to the cornerstone and its bill of materials. Plan a schema at another cornerstone, facing any way, and the site lists what is still missing, block by block. Schemas belong to the society, so one design can build a whole street.

## Commands

| Command | What it does |
| --- | --- |
| `/building list` | Your society's buildings |
| `/building info`, `survey` or `fit <cornerstone>` | Describe, check, or fit the box |
| `/building use <cornerstone> <use>` | Set the use |
| `/building name`, `grow` or `extent ...` | Rename or resize |
| `/building schema save <cornerstone> <name>` | Save a design; `schema list`, `info` and `delete` manage them |
| `/building plan <cornerstone> <schema>` | Lay out a design; `/building missing <cornerstone>` lists what is left, `/building unplan` clears it |
| `/building construct <cornerstone>` | Operators only: place the planned blocks |

Server owners can tune `floatingBlockLimit`, `structuralStress`, `stormSeverity`, `decayRate` and `maxBuildingVolume`.
