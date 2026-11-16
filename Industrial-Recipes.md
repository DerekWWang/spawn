---
title: Industrial Recipes
---
{% include nav.html %}

# Industrial Recipes

Every industrial recipe, generated from the code. Quantities are per batch: kilograms of metal, mineral and chemical, 25 kg sacks of
coal, coke and charcoal, litres of liquid, metres of wire, single bricks, and cubic-metre blocks. Time is man-hours (1,000 ticks of
the game clock each), shared between the hands on the job up to the crew shown. "Also needs" lists what the site must have beyond
the standing requirements of that kind of works, which are given on [Industrial Manufacturing](Industrial-Manufacturing). The recipe id is what
`/survival works set <recipe>` takes.

## Workshop

| Recipe id | Makes | From | By-product | Man-hours | Crew | Skill | Also needs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `wooden_wheel` | 1 wooden wheel | 1 any planks, 10 iron ingot, 8 metal rivet |  | 16 | 2 | Construction 4 |  |
| `vehicle_frame` | 1 vehicle frame | 2 any planks, 1 any logs, 10 iron ingot, 40 metal rivet |  | 24 | 2 | Construction 6 |  |
| `harness` | 1 harness | 6 leather, 4 cordage, 8 metal rivet |  | 12 | 2 | Weaving 4 |  |
| `saddle` | 1 saddle | 5 leather, 2 cordage, 4 metal rivet, 1 any planks |  | 30 | 2 | Weaving 6 |  |
| `sleeper` | 7 sleeper | 1 any logs |  | 3.5 | 2 | Construction 2 |  |
| `gun_carriage` | 1 gun carriage | 2 any planks, 40 iron ingot, 2 wooden wheel |  | 80 | 2 | Construction 8 |  |
| `musket_stock` | 8 musket stock | 1 any planks |  | 64 | 2 | Construction 4 |  |
| `musket` | 8 musket | 8 musket barrel, 8 musket lock, 8 musket stock, 1 steel ingot, 16 metal rivet |  | 48 | 2 | Smithing 12 |  |
| `paper_cartridge` | 96 paper cartridge | 96 musket ball, 1 gunpowder, 24 paper |  | 4.8 | 2 | Engineering 0 |  |
| `powder_charge` | 16 powder charge | 8 gunpowder, 1 fiber cloth |  | 0.5 | 2 | Engineering 0 |  |
| `powder_mill` | 1 powder mill | 2 stone, 8 any planks, 40 bronze ingot, 8 cordage |  | 200 | 2 | Construction 8 |  |
| `chimney` | 1 chimney | 500 brick, 50 quicklime, 1 sand |  | 8 | 2 | Construction 4 |  |
| `lead_chamber` | 1 lead chamber | 400 lead ingot, 4 any planks |  | 40 | 2 | Construction 6 |  |
| `power_cable` | 1 power cable | 6 copper ingot, 2 insulated wire, 8 lead ingot, 1 coal tar |  | 1 | 2 | Engineering 4 |  |
| `cleanroom_suit` | 1 cleanroom suit | 6 clean cloth, 1 cordage |  | 6 | 2 | Weaving 8 |  |
| `draw_copper_wire` | 140 copper wire | 1 copper ingot |  | 14 | 2 | Smithing 6 |  |

## Assembly yard

| Recipe id | Makes | From | By-product | Man-hours | Crew | Skill | Also needs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `lime_clamp` | 1,400 quicklime | 1 limestone, 3 any logs |  | 60 | 8 | Construction 2 |  |
| `brick_clamp` | 1,900 brick | 2,000 clay ball, 2 any logs |  | 200 | 8 | Construction 2 |  |
| `firebrick` | 1 firebrick | 480 clay ball, 1 sand, 2 any logs |  | 60 | 8 | Construction 6 |  |
| `boiler` | 1 boiler | 1,200 iron plate, 400 metal rivet |  | 800 | 8 | Engineering 8 |  |
| `steam_engine` | 1 steam engine | 1 boiler, 1,500 pig iron, 150 machine parts, 100 iron plate, 40 copper ingot, 10 lubricant |  | 1500 | 8 | Engineering 10 |  |
| `steam_boiler` | 1 steam boiler | 1 boiler, 2 firebrick, 1,000 brick, 100 quicklime |  | 200 | 8 | Engineering 8 |  |
| `stationary_engine` | 1 stationary engine | 1 steam engine, 200 pig iron, 1,000 brick, 100 quicklime |  | 300 | 8 | Engineering 10 |  |
| `foundry` | 1 foundry | 3,000 brick, 6 firebrick, 100 clay ball, 200 iron ingot, 300 quicklime |  | 600 | 8 | Construction 10 |  |
| `chemical_works` | 1 chemical works | 2,000 brick, 300 copper ingot, 400 lead ingot, 40 glass bottle, 200 quicklime |  | 600 | 8 | Construction 10 |  |
| `distillation_column` | 1 distillation column | 1,500 iron plate, 200 copper ingot, 2 firebrick, 500 brick |  | 400 | 8 | Engineering 8 |  |
| `machine_shop` | 1 machine shop | 4,000 pig iron, 400 machine parts, 300 iron plate, 2,000 brick, 16 leather, 20 lubricant |  | 2000 | 8 | Engineering 10 |  |
| `dynamo` | 1 dynamo | 1,000 pig iron, 300 steel plate, 8,000 insulated wire, 40 precision parts, 1 coke |  | 400 | 8 | Engineering 12 |  |
| `generator` | 1 generator | 1 dynamo, 300 pig iron, 20 precision parts, 200 insulated wire, 5 lubricant |  | 200 | 8 | Engineering 12 |  |
| `power_station` | 1 power station | 60 copper ingot, 500 insulated wire, 100 steel plate, 1 stone |  | 200 | 8 | Engineering 12 |  |
| `transformer` | 1 transformer | 120 steel plate, 1,500 insulated wire, 20 lubricant |  | 80 | 8 | Engineering 12 |  |
| `combustion_engine` | 1 combustion engine | 200 pig iron, 60 steel ingot, 60 precision parts, 30 insulated wire, 8 lead ingot, 2 sulfuric acid, 5 rubber, 5 lubricant |  | 600 | 8 | Engineering 14 |  |
| `arc_furnace` | 1 arc furnace | 8 firebrick, 2,000 steel plate, 20 coke, 500 insulated wire, 1 transformer |  | 800 | 8 | Engineering 14 |  |
| `electrolytic_cell` | 1 electrolytic cell | 1,500 steel plate, 40 coke, 200 copper ingot, 300 insulated wire, 4 firebrick |  | 500 | 8 | Engineering 14 |  |
| `water_purifier` | 1 water purifier | 200 steel plate, 20 glass bottle, 60 phenolic resin, 30 copper ingot, 1 electric motor, 5 rubber |  | 200 | 8 | Engineering 14 |  |
| `electronics_works` | 1 electronics works | 300 precision parts, 800 steel plate, 16 glass pane, 2,000 insulated wire, 2,000 brick, 8 electric motor |  | 1500 | 8 | Engineering 14 |  |
| `crystal_puller` | 1 crystal puller | 600 steel plate, 150 precision parts, 1 vacuum pump, 12 vacuum tube, 500 insulated wire, 4 glass bottle, 2 rubber |  | 800 | 8 | Engineering 16 |  |
| `diffusion_furnace` | 1 diffusion furnace | 2 firebrick, 10 glass bottle, 400 insulated wire, 300 steel plate, 4 vacuum tube |  | 500 | 8 | Engineering 16 |  |
| `lithography_stepper` | 1 mask aligner | 12 lens, 200 precision parts, 300 steel plate, 2 electric motor, 6 vacuum tube, 1 tungsten, 2 glass pane |  | 1500 | 8 | Engineering 18 |  |
| `wire_bonder` | 1 wire bonder | 60 precision parts, 2 electric motor, 2 vacuum tube, 40 steel plate, 2 lens, 1 tungsten |  | 600 | 8 | Engineering 16 |  |
| `cart` | 1 cart (vehicle) | 1 vehicle frame, 2 wooden wheel, 1 harness |  | 40 | 8 | Construction 6 |  |
| `wagon` | 1 wagon (vehicle) | 2 vehicle frame, 4 wooden wheel, 2 harness, 4 any planks |  | 120 | 8 | Construction 8 |  |
| `chariot` | 1 chariot (vehicle) | 1 vehicle frame, 2 wooden wheel, 2 harness, 4 leather, 8 cordage |  | 80 | 8 | Construction 8 |  |
| `trebuchet` | 1 trebuchet (vehicle) | 16 any logs, 16 any planks, 64 cordage, 100 iron ingot, 4 cobblestone |  | 2000 | 8 | Construction 10 |  |
| `cannon` | 1 cannon (vehicle) | 1 cannon barrel, 1 gun carriage, 40 metal rivet |  | 60 | 8 | Engineering 6 |  |
| `bombard` | 1 bombard (vehicle) | 1 bombard barrel, 6 any logs, 100 iron ingot, 16 cordage |  | 200 | 8 | Engineering 6 |  |
| `field_gun` | 1 field gun (vehicle) | 1,000 steel ingot, 60 precision parts, 2 wooden wheel, 60 steel plate |  | 3000 | 8 | Engineering 14 |  |
| `motor_truck` | 1 motor truck (vehicle) | 1 combustion engine, 1,200 steel ingot, 800 steel plate, 150 precision parts, 4 rubber tyre, 2 any planks, 2 glass pane, 10 lubricant |  | 1500 | 8 | Engineering 14 |  |
| `locomotive` | 1 locomotive (vehicle) | 14,000 steel ingot, 5,000 steel plate, 3,000 pig iron, 1,200 precision parts, 700 copper ingot, 40 lubricant |  | 12000 | 8 | Engineering 16 |  |
| `rail_wagon` | 1 rail wagon (vehicle) | 2,500 steel ingot, 1,000 pig iron, 60 machine parts, 8 any planks |  | 600 | 8 | Engineering 10 |  |
| `rail_carriage` | 1 rail carriage (vehicle) | 3,500 steel ingot, 1,200 pig iron, 80 machine parts, 16 any planks, 8 glass pane, 8 wool cloth |  | 1500 | 8 | Engineering 10 |  |

## Nitre bed

| Recipe id | Makes | From | By-product | Man-hours | Crew | Skill | Also needs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `niter_earth` | 64 niter earth | 64 spoiled food, 16 wood ash, 16 wheat |  | 112 days unattended |  | none |  |

## Powder mill

| Recipe id | Makes | From | By-product | Man-hours | Crew | Skill | Also needs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `gunpowder` | 166 gunpowder | 125 saltpeter, 1 charcoal, 17 sulfur |  | 50 | 2 | Engineering 4 |  |

## Foundry

| Recipe id | Makes | From | By-product | Man-hours | Crew | Skill | Also needs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `crucible_steel` | 30 steel ingot | 30 iron ingot, 1 charcoal |  | 12 | 3 | Smithing 14 |  |
| `pig_iron_charcoal` | 1,000 pig iron | 1,800 roasted iron, 60 charcoal, 120 quicklime |  | 192 | 8 | Smithing 10 | 24 firebrick, 8-high chimney, blast (water wheel or engine) |
| `cannon_barrel` | 1 cannon barrel | 400 bronze ingot, 60 clay ball, 1 sand |  | 300 | 8 | Smithing 14 |  |
| `iron_cannon_barrel` | 1 cannon barrel | 450 pig iron, 60 clay ball, 1 sand |  | 250 | 8 | Smithing 12 | 24 firebrick, 8-high chimney |
| `bombard_barrel` | 1 bombard barrel | 1,500 iron ingot, 40 charcoal |  | 1500 | 8 | Smithing 12 |  |
| `calcine_iron_ore` | 3,000 roasted iron | 3,200 hematite, 2 any logs |  | 60 | 8 | Smithing 2 |  |
| `smelt_copper_ore` | 500 copper ingot | 1,000 malachite, 40 charcoal |  | 80 | 8 | Smithing 8 |  |
| `smelt_tin_ore` | 150 tin ingot | 200 cassiterite, 12 charcoal |  | 30 | 8 | Smithing 8 |  |
| `smelt_lead_ore` | 240 lead ingot | 320 galena, 8 charcoal |  | 30 | 8 | Smithing 6 |  |
| `cast_bronze` | 400 bronze ingot | 360 copper ingot, 40 tin ingot |  | 16 | 8 | Smithing 8 |  |
| `cast_round_shot` | 100 round shot | 300 pig iron, 20 clay ball |  | 30 | 8 | Smithing 8 | 24 firebrick, 8-high chimney |
| `glass` | 44 glass | 1 sand, 1,200 wood ash |  | 40 | 8 | Smithing 6 |  |
| `glass_panes` | 6 glass pane | 1 glass |  | 3 | 8 | Smithing 8 |  |
| `blow_bottles` | 90 glass bottle | 1 glass |  | 30 | 8 | Smithing 8 |  |
| `silver` | 1 silver | 2,000 lead ingot, 16 charcoal | 1,900 lead ingot | 60 | 2 | Smithing 10 |  |
| `pig_iron` | 2,000 pig iron | 3,600 roasted iron, 80 coke, 240 quicklime |  | 192 | 8 | Smithing 10 | 24 firebrick, 8-high chimney, blast (water wheel or engine) |
| `steel` | 4,500 steel ingot | 5,000 pig iron |  | 12 | 8 | Smithing 14 | 24 firebrick, 8-high chimney, blowing engine |
| `puddle_iron` | 900 iron ingot | 1,000 pig iron, 16 coal, 50 roasted iron |  | 60 | 8 | Smithing 10 | 24 firebrick, 8-high chimney |
| `soda_glass` | 48 glass | 1 sand, 500 soda ash, 150 quicklime |  | 40 | 8 | Smithing 6 |  |
| `metallurgical_silicon` | 450 metallurgical silicon | 1 sand, 20 coke |  | 40 | 3 | Engineering 12 | 400 kW, arc furnace |
| `phosphorus` | 16 phosphorus | 100 bone meal, 1 sand, 2 coke |  | 6 | 3 | Engineering 12 | 100 kW, arc furnace |

## Chemical works

| Recipe id | Makes | From | By-product | Man-hours | Crew | Skill | Also needs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `saltpeter` | 3 saltpeter | 64 niter earth, 8 wood ash |  | 6 | 3 | Engineering 0 |  |
| `sulfur` | 1 sulfur | 4 pyrite |  | 2 | 3 | Engineering 0 |  |
| `wood_distillation` | 8 charcoal | 4 any logs | 30 methanol | 12 | 3 | Engineering 2 |  |
| `ferment_maize` | 12 fermented mash | 64 maize |  | 4 days unattended |  | none |  |
| `ferment_potato` | 3 fermented mash | 64 potato |  | 4 days unattended |  | none |  |
| `ferment_wheat` | 40 fermented mash | 64 wheat |  | 4 days unattended |  | none |  |
| `distill_alcohol` | 24 fuel alcohol | 40 fermented mash |  | 6 | 3 | Engineering 4 |  |
| `sulfuric_acid` | 60 sulfuric acid | 20 sulfur, 1 saltpeter |  | 10 | 3 | Engineering 8 | 2 lead chambers |
| `nitric_acid` | 6 nitric acid | 10 saltpeter, 10 sulfuric acid |  | 4 | 3 | Engineering 8 |  |
| `hydrofluoric_acid` | 5 hydrofluoric acid | 10 fluorite, 12 sulfuric acid |  | 4 | 3 | Engineering 10 | 2 lead chambers |
| `soda_ash` | 10 soda ash | 12 salt, 10 sulfuric acid, 6 quicklime, 1 coal | 8 hydrochloric acid | 8 | 3 | Engineering 8 |  |
| `hydrochloric_acid` | 20 hydrochloric acid | 12 salt, 10 sulfuric acid |  | 4 | 3 | Engineering 8 |  |
| `caustic_soda` | 7 caustic soda | 10 soda ash, 6 quicklime |  | 3 | 3 | Engineering 8 |  |
| `raw_rubber` | 3 raw rubber | 1 jungle log |  | 6 | 3 | Engineering 2 |  |
| `rubber` | 20 rubber | 20 raw rubber, 1 sulfur |  | 4 | 3 | Engineering 6 |  |
| `boron_oxide` | 3 boron oxide | 10 borax, 5 sulfuric acid |  | 3 | 3 | Engineering 10 |  |
| `coke` | 30 coke | 40 coal | 40 coal tar | 16 | 3 | Engineering 4 |  |
| `phenolic_resin` | 10 phenolic resin | 250 coal tar, 6 methanol |  | 6 | 3 | Engineering 10 |  |
| `alumina` | 9 alumina | 20 bauxite, 1 caustic soda |  | 6 | 3 | Engineering 12 |  |
| `cryolite` | 20 cryolite | 12 hydrofluoric acid, 5 alumina, 16 soda ash |  | 6 | 3 | Engineering 12 | 2 lead chambers |
| `extract_crude` | 10 crude oil | 100 oil sand |  | 2 | 3 | Engineering 4 |  |
| `refine_oil` | 20 petrol | 100 crude oil | 10 lubricant | 4 | 3 | Engineering 8 | distillation column |
| `aluminium` | 10 aluminium ingot | 20 alumina, 1 coke, 1 cryolite |  | 7.5 | 3 | Engineering 14 | 100 kW, electrolytic cell |
| `tungsten` | 5 tungsten | 10 wolframite, 6 soda ash, 8 hydrochloric acid |  | 24 | 3 | Engineering 14 | 50 kW, electrolytic cell |
| `polysilicon` | 10 polysilicon | 12 metallurgical silicon, 40 hydrochloric acid |  | 30 | 3 | Engineering 14 | 150 kW, distillation column, electrolytic cell |
| `photoresist` | 5 photoresist | 100 coal tar, 3 methanol, 1 nitric acid |  | 8 | 3 | Engineering 12 |  |

## Machine shop

| Recipe id | Makes | From | By-product | Man-hours | Crew | Skill | Also needs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `steel_plate` | 480 steel plate | 500 steel ingot |  | 4 | 3 | Engineering 6 |  |
| `roll_iron_plate` | 480 iron plate | 500 iron ingot |  | 4 | 3 | Engineering 6 |  |
| `machined_parts` | 20 machine parts | 30 steel ingot |  | 10 | 3 | Engineering 8 |  |
| `precision_parts` | 15 precision parts | 30 steel ingot, 1 lubricant |  | 15 | 3 | Engineering 10 |  |
| `steel_rail` | 10 steel rail | 310 steel ingot |  | 3 | 3 | Engineering 6 |  |
| `mill_copper_wire` | 1,400 copper wire | 10 copper ingot |  | 2 | 3 | Engineering 6 |  |
| `insulated_wire` | 1,000 insulated wire | 1,000 copper wire, 10 yarn, 5 coal tar |  | 5 | 3 | Engineering 8 |  |
| `lens` | 8 lens | 1 glass pane, 2 coal tar |  | 160 | 3 | Engineering 14 |  |
| `explosive_shell` | 1 explosive shell | 7 steel ingot, 1 gunpowder |  | 2 | 3 | Engineering 12 |  |
| `rubber_tyre` | 1 rubber tyre | 20 rubber, 5 steel ingot |  | 3 | 3 | Engineering 8 |  |
| `electric_motor` | 1 electric motor | 15 steel plate, 300 insulated wire, 5 precision parts, 2 copper ingot |  | 20 | 3 | Engineering 12 |  |
| `fine_wire` | 4,000 fine wire | 1 aluminium ingot |  | 2 | 3 | Engineering 12 |  |
| `cleanroom_panel` | 1 cleanroom panel | 40 steel plate, 10 aluminium ingot, 10 phenolic resin |  | 4 | 3 | Engineering 12 |  |
| `filter_unit` | 1 filter unit | 1 electric motor, 30 steel plate, 200 paper, 10 aluminium ingot, 2 rubber |  | 10 | 3 | Engineering 14 |  |
| `vacuum_pump` | 1 vacuum pump | 40 precision parts, 30 steel plate, 1 electric motor, 5 lubricant, 2 rubber |  | 60 | 3 | Engineering 14 |  |

## Electronics works

| Recipe id | Makes | From | By-product | Man-hours | Crew | Skill | Also needs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `vacuum_tube` | 20 vacuum tube | 20 glass bottle, 1 tungsten, 20 copper wire, 1 steel plate |  | 20 | 3 | Engineering 14 | vacuum pump |
| `passive_components` | 500 passive components | 1 aluminium ingot, 4 paper, 1 coke, 2 clay ball, 100 copper wire |  | 10 | 3 | Engineering 12 |  |
| `display_tube` | 1 display tube | 4 glass bottle, 1 glass pane, 1 tungsten, 1 quicklime, 30 copper wire, 1 vacuum tube |  | 30 | 3 | Engineering 16 | vacuum pump |
| `chip_package` | 250 chip package | 1 alumina, 25 copper wire, 1 glass bottle |  | 25 | 3 | Engineering 12 |  |
| `circuit_board` | 4 circuit board | 1 fiber cloth, 1 phenolic resin, 1 copper ingot, 1 nitric acid |  | 4 | 3 | Engineering 12 |  |
| `silicon_boule` | 10 silicon boule | 20 polysilicon, 1 phosphorus |  | 100 | 3 | Engineering 14 | 30 kW, crystal puller |
| `silicon_wafer` | 400 silicon wafer | 1 silicon boule, 1 hydrofluoric acid, 1 nitric acid |  | 40 | 3 | Engineering 14 |  |
| `photomask` | 8 photomask | 1 glass pane, 1 silver, 1 nitric acid, 1 salt, 2 bone meal |  | 320 | 3 | Engineering 16 | mask aligner |
| `oxidise_wafers` | 25 wafer oxidised | 25 silicon wafer |  | 6 | 3 | Engineering 16 | cleanroom, 20 kW, diffusion furnace, water purifier |
| `diffuse_wafers` | 25 wafer diffused | 25 wafer oxidised, 1 photoresist, 1 photomask, 2 hydrofluoric acid, 1 boron oxide |  | 12 | 3 | Engineering 16 | cleanroom, 25 kW, mask aligner, diffusion furnace, water purifier |
| `gate_wafers` | 25 wafer gated | 25 wafer diffused, 1 photoresist, 1 photomask, 2 hydrofluoric acid |  | 12 | 3 | Engineering 16 | cleanroom, 25 kW, mask aligner, diffusion furnace, water purifier |
| `contact_wafers` | 25 wafer contacted | 25 wafer gated, 1 photoresist, 1 photomask, 2 hydrofluoric acid |  | 8 | 3 | Engineering 16 | cleanroom, 15 kW, mask aligner, water purifier |
| `metallise_wafers` | 25 patterned wafer | 25 wafer contacted, 1 aluminium ingot, 1 photoresist, 1 photomask, 1 nitric acid |  | 10 | 3 | Engineering 16 | cleanroom, 20 kW, mask aligner, vacuum pump, water purifier |
| `silicon_die` | 160 silicon die | 1 patterned wafer |  | 6 | 3 | Engineering 14 | cleanroom |
| `chip` | 16 chip | 16 silicon die, 256 fine wire, 16 chip package |  | 1.6 | 3 | Engineering 14 | cleanroom, wire bonder |
| `computer` | 1 computer | 2,800 chip, 120 circuit board, 8,000 insulated wire, 20 solder, 6,000 passive components, 150 steel plate, 2 transformer, 1 display tube, 4 electric motor, 20 precision parts |  | 4000 | 16 | Engineering 18 |  |
| `server` | 1 server | 20,000 chip, 900 circuit board, 60,000 insulated wire, 150 solder, 45,000 passive components, 1,200 steel plate, 12 transformer, 2 display tube, 30 electric motor, 200 precision parts |  | 30000 | 16 | Engineering 20 |  |
