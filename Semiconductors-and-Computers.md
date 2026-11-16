---
title: Semiconductors and Computers
---
{% include nav.html %}

# Semiconductors and Computers

A computer is the far end of the tree. This page walks the whole road from sand to a server in order, with the quantity each step takes and makes. Recipe ids are in `code`; the full bills are on [Industrial Recipes](Industrial-Recipes), and the plant rules on [Industrial Manufacturing](Industrial-Manufacturing) and [Power and Cleanrooms](Power-and-Cleanrooms).

## What you must already have

A foundry with an arc furnace, a chemical works with an electrolytic cell and lead chambers, a machine shop, a power station with at least twelve generating sets, and an electronics works. The raw materials that cannot be substituted are sand, coke, salt, fluorite, bauxite, wolframite, borax, lead ore for silver, bone meal, rubber and clay.

## Silicon

| Step | Recipe | In | Out | Plant and power |
| --- | --- | --- | --- | --- |
| Furnace silicon | `metallurgical_silicon` | 1 m3 sand, 20 sacks coke | 450 kg | Foundry, arc furnace, 400 kW |
| Hydrochloric acid | `hydrochloric_acid` | 12 kg salt, 10 L sulfuric acid | 20 L | Chemical works |
| Polysilicon | `polysilicon` | 12 kg furnace silicon, 40 L hydrochloric acid | 10 kg | Chemical works, distillation column, electrolytic cell, 150 kW |
| Phosphorus (dopant) | `phosphorus` | 100 bone meal, 1 m3 sand, 2 sacks coke | 16 kg | Foundry, arc furnace, 100 kW |
| Crystal | `silicon_boule` | 20 kg polysilicon, 1 kg phosphorus | 10 boules | Electronics works, crystal puller, 30 kW |
| Wafers | `silicon_wafer` | 1 boule, 1 L hydrofluoric acid, 1 L nitric acid | 400 wafers | Electronics works |

## Masks and chemicals

| Item | Recipe | In | Out | Notes |
| --- | --- | --- | --- | --- |
| Silver | `silver` | 2,000 kg lead, 16 sacks charcoal | 1 kg silver, 1,900 kg lead back | Foundry |
| Photomask | `photomask` | 1 glass pane, 1 kg silver, nitric acid, salt, 2 bone meal | 8 masks | Mask aligner. An emulsion mask is used up by one lot |
| Photoresist | `photoresist` | 100 kg coal tar, 3 L methanol, 1 L nitric acid | 5 | Chemical works |
| Hydrofluoric acid | `hydrofluoric_acid` | 10 kg fluorite, 12 L sulfuric acid | 5 L | Two lead chambers. Poisonous |
| Boron oxide | `boron_oxide` | 10 kg borax, 5 L sulfuric acid | 3 kg | Chemical works |

## The wafer process

Wafers move in lots of 25 through five stages. Every stage is done in the cleanroom with purified water, and each of the four photo stages uses one photoresist and one mask.

| Stage | Recipe | Adds | Equipment | Load |
| --- | --- | --- | --- | --- |
| 1. Field oxide | `oxidise_wafers` | Nothing but heat and water | Diffusion furnace, water purifier | 20 kW |
| 2. Source and drain | `diffuse_wafers` | Resist, mask, 2 L hydrofluoric acid, 1 kg boron oxide | Mask aligner, diffusion furnace, water purifier | 25 kW |
| 3. Gate oxide | `gate_wafers` | Resist, mask, 2 L hydrofluoric acid | Mask aligner, diffusion furnace, water purifier | 25 kW |
| 4. Contacts | `contact_wafers` | Resist, mask, 2 L hydrofluoric acid | Mask aligner, water purifier | 15 kW |
| 5. Metal | `metallise_wafers` | Resist, mask, 1 kg aluminium, 1 L nitric acid | Mask aligner, vacuum pump, water purifier | 20 kW |

A lot takes 48 man-hours across the five stages. No stage starts below 95% cleanliness; the yield itself is taken at dicing.

## Dicing and packaging

| Step | Recipe | In | Out |
| --- | --- | --- | --- |
| Dicing and test | `silicon_die` | 1 patterned wafer | 160 good dice in a perfect room, 80 at 95% cleanliness |
| Ceramic packages | `chip_package` | 1 kg alumina, 25 m copper wire, 1 glass bottle | 250 packages |
| Bond wire | `fine_wire` | 1 kg aluminium | 4,000 m |
| Packaging | `chip` | 16 dice, 16 packages, 256 bond wires | 16 chips. Needs a wire bonder |

Each chip is a sixteen-pin logic package with one bond wire per pin.

## The rest of the machine

| Part | Recipe | In outline |
| --- | --- | --- |
| Circuit board | `circuit_board` | Fibre cloth, phenolic resin, copper, nitric acid: 4 boards |
| Passive components | `passive_components` | Aluminium, paper, coke, clay, copper wire: 500 |
| Valve | `vacuum_tube` | 20 glass bottles, 1 kg tungsten, copper wire, plate: 20. Needs a vacuum pump |
| Display tube | `display_tube` | Glass, tungsten and lime for the phosphor, a valve. Needs a vacuum pump |
| Transformer | `transformer` | 120 kg steel plate, 1,500 m insulated wire, 20 L oil |
| Solder | hand recipe at the forge | 3 kg tin and 2 kg lead make 5 kg |

## Computer and server

| | Computer | Server |
| --- | --- | --- |
| Chips | 2,800 | 20,000 |
| Circuit boards | 120 | 900 |
| Insulated wire | 8,000 m | 60,000 m |
| Solder | 20 kg | 150 kg |
| Passive components | 6,000 | 45,000 |
| Steel plate | 150 kg | 1,200 kg |
| Transformers | 2 | 12 |
| Display tubes | 1 | 2 |
| Electric motors | 4 | 30 |
| Precision parts | 20 | 200 |
| Assembly | 4,000 man-hours, crew of 16, Engineering 18 | 30,000 man-hours, crew of 16, Engineering 20 |

A server's chips alone take 320,000 bond wires, 125 good wafers and five lots through the cleanroom.

A computer block within eight blocks of an artillery piece halves its dispersion, as a fire-control table. See [Siege Artillery](Siege-Artillery).

## Skills

Almost every step is Engineering: 12 for boards and passives, 14 for valves, wafers and packaging, 16 for the wafer stages and masks, 18 for a computer, 20 for a server. See [Skills and Learning](Skills-and-Learning).
