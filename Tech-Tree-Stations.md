---
title: Tech Tree Stations
---
{% include nav.html %}

# Tech Tree Stations

The tech tree's stations, from the hearth and smelting furnace to the chemical plant and the wafer fab, are buildings. Each is a controller block with a structure you build around it, sized after the real thing. The controller's tooltip and screen (hover the title) describe the structure, and the screen names the first wrong block and its coordinates.

The kiln, bloomery, forge, loom and the survival works are not built this way. They are single survival workstations: see [Building Crafting and Workstations](Building-Crafting-and-Workstations) and [Industrial Manufacturing](Industrial-Manufacturing). Wherever the tree asks for one of them, the survival workstation within 16 blocks is it.

## Building a station

- **Box stations** extend behind the controller, which sits in the middle of the bottom row of the front face. Their walls must be the right material: masonry, refractory brick, steel casing, stainless steel, lead lining, cleanroom panels and so on. Furnaces must be hollow, many have an open top, and anything that burns needs a chimney to the sky.
- **Bigger builds do more.** Furnaces, chemical plants and cleanrooms come in several sizes, and the larger ones run more batches at a time. A 3×3×3 smelting furnace works one charge, a 5×4×5 one two.
- **Rooms** (laboratories, hospital, cleanroom, wafer fabs) are enclosed halls whose inside is left free for equipment. A cleanroom needs one fan filter unit per 16 ceiling blocks. Semiconductor tools such as the mask aligner, vacuum evaporator and CVD reactor must stand inside a complete cleanroom when the process calls for one. Lithography is huge: an advanced wafer fab is 25×9×25, a DUV scanner 5×4×7.
- **Site rules.** A water wheel must dip into flowing water, a windmill must stand in open wind, an atmospheric engine needs water for its boiler, a shipyard's slipway must run into water, and a dam must hold back a reservoir.

| Infrastructure | What it is |
| --- | --- |
| Grain field | A boundary stone with tilled farmland around it |
| Livestock herd | A fenced pen; it slowly yields dung |
| Shaft mine, deep mine | A headframe over an open shaft 24 or 64 blocks deep |
| Drilling rig | A 12-high derrick over oil- or gas-bearing rock |
| Electric grid | A substation that covers 96 blocks |

## Running a station

- Other stations named as means of production must be complete and within 16 blocks of the one doing the work.
- **Power.** Machine tools need a running water wheel, windmill or engine within reach, or the grid. An engine burns fuel only while a machine nearby draws power from it. A survival stationary engine within reach also counts. Electrical plant needs grid coverage.
- **Fuel.** Heat stations burn what is in their fuel slot: wood for hearths, charcoal, coal or coke for smelting furnaces ("charcoal is the only fuel hot and clean enough for metal until coke"). A charge that carries its own fuel, like the charcoal in copper smelting or the coke in a blast furnace, needs none. The Bessemer converter needs no fuel at all.
- **Tools wear.** Crucibles, moulds, tool sets, draw plates, photomasks and the like lose durability on every run.
- **Quantities** follow historical recipes and stoichiometry where it matters: 1:3 lime mortar, 75:12.5:12.5 black powder, 88:12 bronze, two alumina per aluminium, N₂ + 3 H₂ → 2 NH₃. Everything else uses one of each.

## Deposits

The tree places 22 kinds of deposit of its own, beside the survival geology's minerals ([Natural Resources and Mining](Natural-Resources-and-Mining)): fireclay, native copper, bog iron, sphalerite, niter, magnetite, amber sand, cinnabar, graphite, pyrolusite, quartz, platinum placers, bitumen seeps, chromite, pentlandite, phosphorite, arsenopyrite, monazite, ilmenite sand, spodumene, and oil and gas reservoirs.

- **Recognised once known.** Some deposits are in the world from the start, but break into plain rubble until the miner's society knows the right theory: platinum and manganese after Chemical elements, zinc after Alloying, and so on.
- **Access.** Shaft-mine ores (sphalerite, cinnabar, graphite, pyrolusite, arsenopyrite) yield only rubble unless mined below a complete shaft mine within 48 blocks. Chromite, nickel and rare earths need a deep mine. Crude oil and natural gas come only from a drilling rig.

Both rules can be changed in `config/spawn_9b5fbb9-local.toml`. Vanilla iron and coal ores are left to the survival rules unless `gateVanillaOres` is turned on.
