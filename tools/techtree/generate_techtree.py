#!/usr/bin/env python3
"""Turn Functional_tech_tree_stone_to_server.xlsx into the mod's data and assets.

Run from the repository root:

    python3 tools/techtree/generate_techtree.py

Everything under src/techtree/generated is deleted and rewritten, so edit this
script (or the spreadsheet) rather than the generated files. The Java code reads
the generated techtree.json at start-up; the rest are ordinary resource-pack and
data-pack files (models, textures, lang, tags, loot tables, recipes, worldgen).

The script fails loudly if the spreadsheet references something it cannot map,
if a function becomes unreachable, or if a structure spec is malformed.
"""
import gzip
import json
import os
import re
import shutil
import sys
from collections import defaultdict

import openpyxl

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import textures as tex  # noqa: E402

MODID = "spawn_9b5fbb9"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
XLSX = os.path.join(HERE, "Functional_tech_tree_stone_to_server.xlsx")
OUT = os.path.join(ROOT, "src", "techtree", "generated")
ASSETS = os.path.join(OUT, "assets", MODID)
DATA = os.path.join(OUT, "data", MODID)


def ns(path):
    return f"{MODID}:{path}"


def slug(name):
    s = name.lower().replace("–", "-").replace("—", "-")
    s = re.sub(r"[()]", "", s)
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return s


def split(cell):
    return [x.strip() for x in str(cell).split(";") if x.strip()] if cell else []


def era_num(era):
    return int(str(era).split()[0])


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False, sort_keys=False)
        f.write("\n")


# --------------------------------------------------------------------------- spreadsheet

def load_sheet():
    wb = openpyxl.load_workbook(XLSX, data_only=True)

    def rows(name):
        ws = wb[name]
        data = list(ws.iter_rows(values_only=True))
        hdr = data[0]
        return [dict(zip(hdr, r)) for r in data[1:] if any(c is not None for c in r)]

    return rows("Functions"), rows("Items"), rows("Resources")


FUNCTIONS, ITEMS, RESOURCES = load_sheet()
ITEM_BY_NAME = {i["Item"]: i for i in ITEMS}
RES_BY_NAME = {r["Resource"]: r for r in RESOURCES}
THEORY_ID = {f["Function"]: f["ID"] for f in FUNCTIONS if f["Type"] == "Theoretical"}
FUNC_BY_ID = {f["ID"]: f for f in FUNCTIONS}
MOP_COL = "Means of production, or evidence on hand (not consumed)"

# --------------------------------------------------------------------------- resources
# How each depth-0 resource exists in the world.
#   tag      -> satisfied by any item in tag resource/<id> (vanilla items and/or our own)
#   env      -> taken from the surroundings (water, air, animals)
#   phen     -> a phenomenon observed at a place and time
# Custom resource items are listed in 'items' and get their own textures.
# 'ore' entries create a deposit block, its loot table and worldgen.

STONE = "stone"
DEEP = "deepslate"
SAND = "sand"

RESOURCE_DEFS = {
    "Wood": {"tag": ["#minecraft:logs"]},
    "Stone": {"tag": ["minecraft:cobblestone", "minecraft:stone", "minecraft:cobbled_deepslate", "minecraft:deepslate",
                      "minecraft:blackstone", "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
                      "minecraft:tuff", "minecraft:sandstone", "minecraft:mossy_cobblestone", ns("limestone")]},
    "Flint": {"tag": ["minecraft:flint"]},
    "Plant fiber": {"custom": "plant_fiber", "tag": [], "shape": "fiber",
                    "how": "Break grass and ferns by hand; about a third yield usable fibre."},
    "Wild game": {"env": "wild_game"},
    "Clay": {"tag": ["minecraft:clay_ball"]},
    "Fireclay": {"custom": "fireclay", "shape": "ball", "colour": ((222, 212, 192), (180, 170, 150)),
                 "ore": {"block": "fireclay_deposit", "name": "Fireclay Deposit", "host": STONE, "count": 4, "size": 12,
                         "min": -8, "max": 56, "drop": [2, 4], "biomes": "#minecraft:is_overworld"}},
    "Sand": {"tag": ["minecraft:sand", "minecraft:red_sand"]},
    "Water": {"env": "water"},
    "Wild grain": {"tag": ["minecraft:wheat_seeds", "minecraft:wheat"]},
    "Wild herd animals": {"env": "wild_herd_animals"},
    "Limestone": {"tag": [ns("limestone"), "minecraft:calcite"]},
    "Salt": {"custom": "salt", "shape": "dust", "colour": ((236, 232, 226), (190, 186, 180)),
             "ore": {"block": "rock_salt_deposit", "name": "Rock Salt Deposit", "host": STONE, "count": 3, "size": 10,
                     "min": 30, "max": 90, "drop": [2, 3],
                     "biomes": ["minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands",
                                "minecraft:wooded_badlands", "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore"],
                     "extra": {"count": 1, "min": 0, "max": 50, "biomes": "#minecraft:is_overworld"}}},
    "Native copper": {"custom": "native_copper", "shape": "nugget", "colour": ((210, 110, 60), (150, 70, 40)),
                      "ore": {"block": "native_copper_deposit", "name": "Native Copper Deposit", "host": STONE, "count": 2, "size": 4,
                              "min": 0, "max": 80, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Malachite": {"custom": "malachite", "shape": "lump", "colour": ((40, 150, 90), (20, 90, 55)),
                  "tag_extra": ["minecraft:raw_copper"],
                  "ore": {"block": "malachite_deposit", "name": "Malachite Deposit", "host": STONE, "count": 6, "size": 8,
                          "min": 32, "max": 130, "drop": [1, 3], "biomes": "#minecraft:is_overworld"}},
    "Cassiterite": {"custom": "cassiterite", "shape": "lump", "colour": ((45, 40, 40), (110, 95, 80)),
                    "ore": {"block": "cassiterite_deposit", "name": "Cassiterite Deposit", "host": STONE, "count": 4, "size": 6,
                            "min": 16, "max": 96, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Iron ore": {"tag": ["minecraft:raw_iron"],
                 "custom_ore_only": True,
                 "ore": {"block": "bog_iron_deposit", "name": "Bog Iron Deposit", "host": "dirt", "count": 6, "size": 8,
                         "min": 50, "max": 70, "drop_item": "minecraft:raw_iron", "drop": [1, 2],
                         "biomes": ["minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:river"]}},
    "Native gold": {"tag": ["minecraft:raw_gold", "minecraft:gold_nugget"]},
    "Galena": {"custom": "galena", "shape": "crystal", "colour": ((120, 124, 140), (80, 84, 96)),
               "ore": {"block": "galena_deposit", "name": "Galena Deposit", "host": STONE, "count": 4, "size": 8,
                       "min": -16, "max": 40, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Zinc ore": {"custom": "zinc_ore", "shape": "lump", "colour": ((150, 120, 80), (100, 80, 50)),
                 "ore": {"block": "sphalerite_deposit", "name": "Sphalerite Deposit (Zinc Ore)", "host": STONE, "count": 4, "size": 8,
                         "min": -16, "max": 40, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Sulfur": {"custom": "sulfur", "shape": "lump", "colour": ((232, 212, 50), (170, 150, 20)),
               "ore": {"block": "sulfur_deposit", "name": "Sulfur Deposit", "host": STONE, "count": 3, "size": 8,
                       "min": -60, "max": 10, "drop": [1, 3], "biomes": "#minecraft:is_overworld",
                       "extra": {"count": 8, "min": 10, "max": 110, "biomes": "#minecraft:is_nether", "target": "netherrack"}}},
    "Niter": {"custom": "niter", "shape": "dust", "colour": ((242, 242, 236), (200, 200, 196)),
              "ore": {"block": "niter_crust", "name": "Niter Crust", "host": STONE, "count": 5, "size": 5,
                      "min": 0, "max": 70, "drop": [1, 3], "biomes": "#minecraft:is_overworld", "air": True}},
    "Lodestone": {"custom": "lodestone", "shape": "lump", "colour": ((50, 50, 58), (110, 110, 125)),
                  "ore": {"block": "magnetite_deposit", "name": "Magnetite Deposit (Lodestone)", "host": STONE, "count": 2, "size": 5,
                          "min": 0, "max": 120, "drop": [1, 1], "biomes": "#minecraft:is_overworld"}},
    "Amber": {"custom": "amber", "shape": "crystal", "colour": ((230, 150, 40), (180, 100, 20)),
              "ore": {"block": "amber_sand", "name": "Amber-Bearing Sand", "host": SAND, "count": 4, "size": 5,
                      "min": 55, "max": 80, "drop": [1, 1], "biomes": "#minecraft:is_beach"}},
    "Cinnabar": {"custom": "cinnabar", "shape": "crystal", "colour": ((200, 40, 40), (130, 20, 20)),
                 "ore": {"block": "cinnabar_deposit", "name": "Cinnabar Deposit", "host": STONE, "count": 2, "size": 6,
                         "min": -10, "max": 40, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Coal": {"tag": ["minecraft:coal"]},
    "Graphite": {"custom": "graphite", "shape": "lump", "colour": ((60, 60, 66), (100, 100, 110)),
                 "ore": {"block": "graphite_deposit", "name": "Graphite Deposit", "host": STONE, "count": 3, "size": 7,
                         "min": -20, "max": 40, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Manganese ore": {"custom": "manganese_ore", "shape": "lump", "colour": ((40, 36, 40), (90, 80, 90)),
                      "ore": {"block": "pyrolusite_deposit", "name": "Pyrolusite Deposit (Manganese Ore)", "host": STONE, "count": 3,
                              "size": 7, "min": -10, "max": 40, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Quartz": {"tag": ["minecraft:quartz"],
               "ore": {"block": "quartz_vein", "name": "Quartz Vein", "host": STONE, "count": 4, "size": 6,
                       "min": -16, "max": 100, "drop_item": "minecraft:quartz", "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Borax": {"custom": "borax", "shape": "dust", "colour": ((240, 238, 228), (200, 196, 180)),
              "ore": {"block": "borax_crust", "name": "Borax Crust", "host": SAND, "count": 6, "size": 8,
                      "min": 58, "max": 90, "drop": [1, 3],
                      "biomes": ["minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"]}},
    "Fluorite": {"custom": "fluorite", "shape": "crystal", "colour": ((150, 80, 200), (70, 170, 110)),
                 "ore": {"block": "fluorite_deposit", "name": "Fluorite Deposit", "host": STONE, "count": 3, "size": 6,
                         "min": -20, "max": 40, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Platinum": {"custom": "platinum", "shape": "nugget", "colour": ((205, 205, 212), (140, 140, 150)),
                 "ore": {"block": "platinum_placer", "name": "Platinum Placer", "host": STONE, "count": 1, "size": 4,
                         "min": -30, "max": 30, "drop": [1, 1], "biomes": "#minecraft:is_overworld"}},
    "Bitumen": {"custom": "bitumen", "shape": "lump", "colour": ((30, 26, 24), (70, 60, 50)),
                "ore": {"block": "bitumen_seep", "name": "Bitumen Seep", "host": "dirt", "count": 2, "size": 8,
                        "min": 40, "max": 80, "drop": [1, 3], "biomes": "#minecraft:is_overworld"}},
    "Rubber latex": {"custom": "rubber_latex", "shape": "flask", "colour": ((240, 238, 220), (200, 190, 160)),
                     "how": "Break jungle logs: tropical trees bleed latex."},
    "Crude oil": {"custom": "crude_oil", "shape": "flask", "colour": ((40, 32, 26), (20, 16, 12)),
                  "reservoir": {"block": "oil_reservoir", "name": "Oil-Bearing Rock", "count": 1, "rarity": 2, "size": 40,
                                "min": -50, "max": 20}},
    "Bauxite": {"custom": "bauxite", "shape": "lump", "colour": ((170, 90, 60), (210, 150, 110)),
                "ore": {"block": "bauxite_deposit", "name": "Bauxite Deposit", "host": STONE, "count": 6, "size": 10,
                        "min": 50, "max": 110, "drop": [1, 3],
                        "biomes": ["#minecraft:is_jungle", "#minecraft:is_savanna", "#minecraft:is_badlands"]}},
    "Chromite": {"custom": "chromite", "shape": "lump", "colour": ((40, 40, 44), (110, 120, 130)),
                 "ore": {"block": "chromite_deposit", "name": "Chromite Deposit", "host": DEEP, "count": 3, "size": 7,
                         "min": -60, "max": -16, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Nickel ore": {"custom": "nickel_ore", "shape": "lump", "colour": ((150, 140, 90), (90, 90, 60)),
                   "ore": {"block": "pentlandite_deposit", "name": "Pentlandite Deposit (Nickel Ore)", "host": DEEP, "count": 3,
                           "size": 7, "min": -60, "max": -16, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Wolframite": {"custom": "wolframite", "shape": "crystal", "colour": ((50, 45, 40), (100, 90, 80)),
                   "ore": {"block": "wolframite_deposit", "name": "Wolframite Deposit", "host": DEEP, "count": 2, "size": 6,
                           "min": -60, "max": -16, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Phosphate rock": {"custom": "phosphate_rock", "shape": "lump", "colour": ((200, 190, 160), (150, 140, 110)),
                       "ore": {"block": "phosphorite_deposit", "name": "Phosphorite Deposit", "host": STONE, "count": 3, "size": 10,
                               "min": 20, "max": 70, "drop": [1, 3], "biomes": "#minecraft:is_overworld"}},
    "Arsenic ore": {"custom": "arsenic_ore", "shape": "crystal", "colour": ((180, 180, 175), (120, 120, 110)),
                    "ore": {"block": "arsenopyrite_deposit", "name": "Arsenopyrite Deposit (Arsenic Ore)", "host": STONE, "count": 2,
                            "size": 6, "min": -16, "max": 40, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Rare-earth ore": {"custom": "rare_earth_ore", "shape": "lump", "colour": ((190, 150, 110), (150, 100, 70)),
                       "ore": {"block": "monazite_deposit", "name": "Monazite Deposit (Rare-Earth Ore)", "host": DEEP, "count": 2,
                               "size": 6, "min": -60, "max": -20, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Air": {"env": "air"},
    "Wildfire": {"phen": "wildfire"},
    "Night sky": {"phen": "night_sky"},
    "Seasonal cycle": {"phen": "seasonal_cycle"},
    "Flowing river": {"phen": "flowing_river"},
    "Sunlight": {"phen": "sunlight"},
    "Medicinal plants": {"tag": ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:azure_bluet",
                                 "minecraft:oxeye_daisy", "minecraft:allium", "minecraft:lily_of_the_valley",
                                 "minecraft:sweet_berries", "minecraft:glow_berries"]},
    "Wild horses": {"env": "wild_horses"},
    "Natural gas": {"custom": "natural_gas", "shape": "canister", "colour": ((240, 180, 80), (255, 255, 255)),
                    "reservoir": {"block": "gas_reservoir", "name": "Gas-Bearing Rock", "count": 1, "rarity": 2, "size": 32,
                                  "min": -50, "max": 30}},
    "Titanium ore": {"custom": "titanium_ore", "shape": "dust", "colour": ((40, 34, 34), (140, 70, 50)),
                     "ore": {"block": "ilmenite_sand", "name": "Ilmenite Sand (Titanium Ore)", "host": SAND, "count": 4, "size": 6,
                             "min": 55, "max": 80, "drop": [1, 2], "biomes": "#minecraft:is_beach"}},
    "Lithium ore": {"custom": "lithium_ore", "shape": "crystal", "colour": ((220, 200, 210), (170, 150, 170)),
                    "ore": {"block": "spodumene_deposit", "name": "Spodumene Deposit (Lithium Ore)", "host": STONE, "count": 2,
                            "size": 6, "min": 0, "max": 60, "drop": [1, 2], "biomes": "#minecraft:is_overworld"}},
    "Wind": {"phen": "wind"},
    "Open water": {"phen": "open_water"},
}

PHENOMENON_TEXT = {
    "wildfire": "Fire or lava burning within 8 blocks of you",
    "night_sky": "An open, clear night sky above you",
    "seasonal_cycle": "Having lived through a full cycle of days and nights",
    "flowing_river": "Flowing water (a stream with current) within 6 blocks",
    "sunlight": "Open sky in daylight",
    "wind": "An exposed, high site with open sky (at least 10 blocks above sea level)",
    "open_water": "A body of open water (lake, sea or wide river) within 8 blocks",
}
ENV_TEXT = {
    "water": "Water: stand within 4 blocks of a water source, or carry a water bucket or bottle",
    "air": "Air: always available",
    "wild_game": "Wild game: a deer-sized wild animal (pig, cow, sheep, goat, rabbit...) within 16 blocks",
    "wild_herd_animals": "Wild herd animals: two adult cows, sheep or goats within 16 blocks",
    "wild_horses": "Wild horses: an untamed horse, donkey or camel within 16 blocks",
}

# --------------------------------------------------------------------------- structures
# Sizes are in blocks (1 block ~ 1 m). w = across the front, h = height, d = depth
# behind the controller. The controller sits in the middle of the bottom of the
# front face ('box'), or in the middle of the footprint ('centered').
# Multiple tiers: bigger builds run more batches in parallel.

CASING_TAGS = {
    "timber": ["#minecraft:logs", "#minecraft:planks", "#minecraft:wooden_fences"],
    "stone": ["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone", "minecraft:stone_bricks",
              "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:chiseled_stone_bricks",
              "minecraft:smooth_stone", "minecraft:cobbled_deepslate", "minecraft:deepslate", "minecraft:deepslate_bricks",
              "minecraft:deepslate_tiles", "minecraft:polished_deepslate", "minecraft:andesite", "minecraft:polished_andesite",
              "minecraft:diorite", "minecraft:polished_diorite", "minecraft:granite", "minecraft:polished_granite",
              "minecraft:tuff", "minecraft:tuff_bricks", "minecraft:polished_tuff", "minecraft:blackstone",
              "minecraft:polished_blackstone", "minecraft:polished_blackstone_bricks", "minecraft:sandstone",
              "minecraft:cut_sandstone", "minecraft:smooth_sandstone", "minecraft:red_sandstone", ns("limestone"),
              ns("limestone_bricks")],
    "clay": ["minecraft:clay", "minecraft:bricks", "minecraft:mud_bricks", "minecraft:packed_mud", "#minecraft:terracotta"],
    "refractory": ["minecraft:bricks", "minecraft:nether_bricks", "minecraft:red_nether_bricks",
                   "minecraft:cracked_nether_bricks", "minecraft:chiseled_nether_bricks"],
    "copper": ["#minecraft:copper"],
    "metal": ["minecraft:iron_block", ns("machine_frame"), ns("cast_iron_block"), ns("steel_casing"), ns("stainless_casing")],
    "concrete": [ns("reinforced_concrete_block")],
    "lead": [ns("lead_lining")],
    "precision": [ns("stainless_casing"), "#c:glass_blocks"],
    "cleanroom": [ns("cleanroom_panel"), ns("fan_filter_unit"), "#c:glass_blocks", "#c:glass_panes", "minecraft:iron_door",
                  "minecraft:iron_trapdoor"],
    "glass": ["#c:glass_blocks"],
    "doors": ["#minecraft:doors", "#minecraft:trapdoors", "#c:glass_panes"],
}
CASING_UNIONS = {
    "masonry": ["stone", "clay"],
    "furnace": ["refractory", "stone"],
    "still": ["copper", "clay"],
    "timber_metal": ["timber", "metal"],
    "timber_stone": ["timber", "stone"],
    "masonry_timber": ["stone", "clay", "timber"],
    "engine_house": ["stone", "clay", "metal"],
    "heavy": ["metal"],
    "refractory_metal": ["refractory", "metal"],
    "industrial": ["metal", "concrete", "refractory"],
    "refractory_industrial": ["refractory", "metal", "concrete"],
    "building": ["stone", "clay", "timber", "concrete", "metal", "glass", "doors"],
    "precision_industrial": ["precision", "metal", "concrete"],
    "glass_precision": ["precision", "glass"],
    "heavy_precision": ["metal", "precision"],
    "cleanroom_room": ["cleanroom"],
}
# family -> (frame colour, accent colour) for station textures
FAMILIES = {
    "stone": ((120, 120, 120), (200, 120, 60)),
    "clay": ((160, 90, 64), (220, 160, 80)),
    "timber": ((140, 100, 60), (90, 140, 70)),
    "metal": ((120, 124, 132), (80, 150, 200)),
    "industrial": ((96, 104, 112), (220, 180, 60)),
    "precision": ((200, 204, 210), (60, 130, 200)),
    "cleanroom": ((230, 232, 236), (120, 200, 230)),
    "concrete": ((170, 170, 165), (120, 120, 120)),
    "lead": ((100, 100, 120), (160, 160, 190)),
    "farm": ((120, 90, 60), (200, 180, 80)),
}


def tier(w, h, d, batch=1):
    assert w % 2 == 1, "front-centred structures need odd widths"
    return {"w": w, "h": h, "d": d, "batch": batch}


def box(tiers, casing, family, interior="any", open_top=False, chimney=False, fuel=0, conds=(), power="none",
        provider=None, room=False, ceiling=None, shape="box", note=None):
    return {"shape": shape, "tiers": [tier(*t) for t in tiers], "casing": casing, "family": family, "interior": interior,
            "openTop": open_top, "chimney": chimney, "fuelTier": fuel, "conditions": list(conds), "power": power,
            "provider": provider, "room": room, "ceiling": ceiling, "note": note}


FFU = {"block": ns("fan_filter_unit"), "perArea": 16}

STATION_SPECS = {
    "Hearth": box([(3, 1, 3)], "stone", "stone", fuel=1, shape="centered",
                  note="A ring of eight stones around the fire pit."),
    "Workbench": dict(box([(1, 1, 1)], "timber", "timber"), shape="single"),
    "Loom": box([(3, 2, 1)], "timber", "timber", note="A timber frame three wide and two high."),
    "Kiln": box([(3, 3, 3), (5, 4, 5, 3)], "clay", "clay", interior="air", open_top=True, chimney=True, fuel=1,
                note="Hollow firing chamber of brick, clay or terracotta, open to the sky for its draught."),
    "Forge": box([(3, 2, 3), (5, 2, 5, 2), (7, 2, 7, 4)], "masonry", "stone", interior="air", open_top=True, fuel=2,
                 note="A solid masonry hearth bed with a raised rim; the open fire bed in the middle grows with the forge."),
    "Bloomery": box([(3, 4, 3)], "masonry", "clay", interior="air", open_top=True, chimney=True, fuel=2,
                    note="A clay or stone shaft furnace, hollow and open at the top."),
    "Smelting furnace": box([(3, 3, 3), (5, 4, 5, 2)], "masonry", "clay", interior="air", open_top=True, chimney=True, fuel=2),
    "Still": box([(3, 2, 2)], "still", "metal", fuel=1, note="Copper pot, helm and worm, on a brick firebox."),
    "Glass furnace": box([(5, 3, 5), (7, 4, 7, 3)], "refractory", "clay", interior="air", chimney=True, fuel=1,
                         note="A closed brick chamber with a flue; the chimney above must reach open sky."),
    "Blast furnace": box([(5, 9, 5, 2), (7, 12, 7, 4)], "furnace", "stone", interior="air", open_top=True, chimney=True, fuel=2,
                         note="A tall brick- or stone-lined stack, charged from the open top."),
    "Coke oven": box([(3, 3, 3), (5, 3, 5, 2)], "refractory", "clay", interior="air", chimney=True, fuel=1,
                     note="A closed beehive oven of brick."),
    "Lathe": box([(3, 1, 1)], "timber_metal", "timber", note="A bed three blocks long."),
    "Screw press": box([(1, 3, 1)], "timber_metal", "timber", note="A frame three blocks tall."),
    "Shipyard": box([(7, 2, 13)], "timber_stone", "timber", open_top=True, conds=["water_at_back"],
                    note="A 7 x 13 slipway whose far end runs into open water."),
    "Infirmary": box([(7, 4, 7)], "building", "timber", room=True),
    "Lead chamber": box([(5, 5, 7)], "lead", "lead", interior="air", note="A hollow chamber lined entirely with lead sheet."),
    "Water wheel": box([(5, 5, 1)], "timber", "timber", conds=["flowing_water"], provider="mechanical",
                       note="A 5 x 5 timber wheel whose bottom dips into flowing water."),
    "Windmill": box([(3, 7, 3)], "masonry_timber", "stone", interior="air", conds=["wind"], provider="mechanical",
                    note="A hollow tower whose top stands in open wind."),
    "Printing press": box([(3, 2, 2)], "timber_metal", "timber"),
    "Boring mill": box([(3, 3, 5)], "heavy", "metal", power="mechanical"),
    "Engine lathe": box([(3, 2, 1)], "heavy", "metal", power="mechanical"),
    "Milling machine": box([(3, 2, 3)], "heavy", "metal", power="mechanical"),
    "Atmospheric engine": box([(3, 6, 5)], "engine_house", "stone", conds=["water_nearby"], fuel=1, provider="mechanical",
                              note="A tall engine house with water for the boiler."),
    "Chemistry laboratory": box([(7, 4, 7), (11, 5, 11, 2)], "building", "stone", room=True),
    "Steam engine": box([(3, 4, 5)], "engine_house", "metal", conds=["water_nearby"], fuel=1, provider="mechanical",
                        note="Boiler and engine; burns fuel only while a nearby machine draws power."),
    "Machine shop": box([(9, 5, 9), (13, 6, 13, 2), (17, 7, 17, 3)], "building", "metal", room=True, power="mechanical",
                        note="A hall of line-shafted machine tools; needs a running engine, wheel or the grid nearby."),
    "Bessemer converter": box([(3, 4, 3)], "refractory_metal", "metal", interior="air", open_top=True,
                              note="Needs no fuel: blowing air through the melt burns out the carbon."),
    "Rolling mill": box([(3, 3, 7)], "heavy", "metal", power="mechanical"),
    "Chemical plant": box([(9, 8, 9), (13, 10, 13, 2)], "industrial", "industrial"),
    "Hospital": box([(11, 5, 11)], "building", "stone", room=True),
    "Oil refinery": box([(5, 16, 5), (7, 22, 7, 2)], "industrial", "industrial", note="A tall fractionating column."),
    "Paper machine": box([(3, 3, 15)], "heavy", "metal", power="mechanical", note="Paper machines are long: 15 blocks."),
    "Air separation plant": box([(5, 12, 5)], "industrial", "industrial", power="grid", note="A tall insulated cold box."),
    "Microbiology laboratory": box([(7, 4, 7)], "building", "stone", room=True),
    "Cold store": box([(5, 4, 5)], "building", "concrete", room=True),
    "Electric arc furnace": box([(5, 5, 5), (7, 6, 7, 3)], "refractory_industrial", "industrial", interior="air",
                                open_top=True, power="grid"),
    "Power station": box([(11, 8, 11)], "industrial", "industrial"),
    "Fermentation plant": box([(7, 8, 7)], "precision_industrial", "precision", power="grid"),
    "High-vacuum system": box([(3, 2, 3)], "precision", "precision", power="grid"),
    "Siemens reactor": box([(3, 5, 3)], "precision", "precision", interior="air", power="grid"),
    "Ultrapure water system": box([(3, 3, 5)], "precision_industrial", "precision", conds=["water_nearby"], power="grid"),
    "Vacuum evaporator": box([(3, 3, 3)], "precision", "precision", interior="air", power="grid"),
    "Blood bank": box([(5, 4, 5)], "building", "stone", room=True),
    "Electronics laboratory": box([(7, 4, 7)], "building", "stone", room=True, power="grid"),
    "Zone refiner": box([(1, 1, 5)], "glass_precision", "precision", power="grid", note="A horizontal quartz tube 5 blocks long."),
    "Crystal puller": box([(3, 6, 3)], "precision", "precision", interior="air", power="grid", note="A Czochralski puller ~6 m tall."),
    "Diffusion furnace": box([(3, 3, 7)], "precision", "precision", interior="air", power="grid", note="A long horizontal tube furnace."),
    "Cleanroom": box([(9, 5, 9), (15, 6, 15, 2), (21, 7, 21, 3)], "cleanroom_room", "cleanroom", room=True, power="grid",
                     ceiling=FFU, note="Sealed panels, glass and iron doors; one fan filter unit per 16 ceiling blocks."),
    "Mask aligner": box([(3, 3, 3)], "precision", "precision", power="grid"),
    "CMP polisher": box([(3, 2, 3)], "precision", "precision", power="grid"),
    "CVD reactor": box([(3, 3, 5)], "precision", "precision", power="grid"),
    "Ion implanter": box([(5, 3, 9)], "precision", "precision", power="grid", note="A 9 m beamline."),
    "Plasma etcher": box([(3, 3, 3)], "precision", "precision", power="grid"),
    "CNC machining centre": box([(5, 4, 5)], "heavy_precision", "metal", power="grid"),
    "Wafer stepper": box([(3, 3, 5)], "precision", "precision", power="grid", note="A reduction stepper is about 2.5 x 2.5 x 4 m."),
    "Wafer fab": box([(15, 7, 15)], "cleanroom_room", "cleanroom", room=True, power="grid", ceiling=FFU,
                     note="A 15 x 15 cleanroom hall."),
    "DUV scanner": box([(5, 4, 7)], "precision", "precision", power="grid", note="A deep-UV scanner is about 4 x 3 x 6 m plus its laser."),
    "Advanced wafer fab": box([(25, 9, 25)], "cleanroom_room", "cleanroom", room=True, power="grid", ceiling=FFU,
                              note="A 25 x 25 cleanroom hall nine blocks high."),
    # infrastructure that something else needs in place
    "Grain field": {"shape": "area", "family": "farm", "area": {"radius": 4, "dy": [-1, 0], "match": "#" + ns("field_soil"), "min": 24},
                    "note": "A boundary stone with at least 24 tilled farmland blocks within 4 blocks of it."},
    "Livestock herd": {"shape": "area", "family": "farm", "area": {"radius": 5, "dy": [0, 1], "match": "#" + ns("pen_fencing"), "min": 20},
                       "producer": {"item": "dung", "interval": 2400, "max": 16, "function": "P-037"},
                       "note": "A pen post inside an enclosure: at least 20 fences, gates or walls within 5 blocks."},
    "Irrigation canal": {"shape": "area", "family": "farm", "area": {"radius": 4, "dy": [-1, 0], "match": "water", "min": 8},
                         "note": "A sluice with at least 8 water blocks within 4 blocks; raises the yield of fields within 12 blocks."},
    "Shaft mine": {"shape": "shaft", "family": "timber", "shaft": {"depth": 24, "headframe": "#" + ns("casing/timber"), "headframeMin": 4},
                   "access": {"radius": 48},
                   "note": "A headframe over an open (or laddered) shaft at least 24 blocks deep; mining deposits below within 48 blocks."},
    "Deep mine": {"shape": "shaft", "family": "metal", "shaft": {"depth": 64, "headframe": "#" + ns("casing/timber_metal"), "headframeMin": 6},
                  "access": {"radius": 64},
                  "note": "A pumped, timbered shaft at least 64 blocks deep; deep deposits below within 64 blocks."},
    "Drilling rig": dict(box([(3, 12, 3)], "timber_metal", "metal", interior="air", power="mechanical", shape="centered",
                             note="A hollow 12 m derrick over oil- or gas-bearing rock."),
                         producer={"interval": 600, "perBlock": 8, "radius": 2}),
    "Dam": box([(9, 6, 2)], "concrete", "concrete", conds=["reservoir_behind"],
               note="A concrete wall 9 wide and 6 high holding back open water."),
    "Electric grid": dict(box([(1, 1, 1)], "metal", "industrial"), shape="single", coverage=True,
                          note="A substation: everything within its coverage radius is on the grid."),
}

# --------------------------------------------------------------------------- functions: quantities and modes
# Consumed amounts per run. The spreadsheet gives no quantities, so these follow
# historical recipes and stoichiometry (e.g. lime:sand 1:3 mortar, 75:15:10 gunpowder,
# 88:12 bronze, 2 alumina -> 1 aluminium). Anything not listed uses 1 of each.

QTY = {
    "P-001": ({"Plant fiber": 3}, {}),
    "P-034": ({"Wild herd animals": 2}, {}),  # a breeding pair
    "P-002": ({"Flint": 2}, {"Flint flakes": 3}),
    "P-008": ({"Wood": 2}, {}),
    "P-009": ({}, {"Bone": 2, "Animal fat": 2}),
    "P-010": ({"Wood": 4}, {}),
    "P-013": ({"Wood": 6, "Clay": 2}, {"Charcoal": 2}),
    "P-019": ({"Plant fiber": 4}, {"Thread": 2}),
    "P-023": ({"Thread": 4}, {}),
    "P-022": ({"Clay": 3, "Wood": 2}, {}),
    "P-025": ({"Bone": 2, "Water": 2}, {"Hide glue": 2}),
    "P-012": ({}, {"Bone needle": 2}),
    "P-029": ({"Clay": 4, "Sand": 2, "Wood": 2}, {"Brick": 4}),
    "P-030": ({"Clay": 3}, {}),
    "P-031": ({"Fireclay": 2}, {}),
    "P-032": ({"Grain": 2}, {}),
    "P-290": ({"Grain": 4}, {"Flour": 3}),
    "P-033": ({"Flour": 2}, {"Bread": 2}),
    "P-035": ({"Grain": 3, "Water": 2}, {"Beer": 2}),
    "P-038": ({"Native copper": 3}, {}),
    "P-044": ({"Malachite": 3, "Charcoal": 2}, {}),
    "P-045": ({"Cassiterite": 2, "Charcoal": 2}, {}),
    "P-046": ({"Clay": 2, "Sand": 2}, {}),
    "P-047": ({"Iron ore": 3, "Charcoal": 4}, {"Slag": 2}),
    "P-049": ({"Copper": 7, "Charcoal": 2}, {"Bronze": 8}),
    "P-051": ({"Bronze": 4, "Timber": 2}, {}),
    "P-052": ({"Wrought iron": 4, "Charcoal": 4}, {"Blister steel": 4}),
    "P-054": ({"Wrought iron": 4, "Timber": 2, "Charcoal": 3}, {}),
    "P-056": ({"Galena": 2, "Charcoal": 2}, {"Lead": 2}),
    "P-057": ({"Lead": 8, "Bone": 2}, {"Litharge": 7}),
    "P-058": ({"Wood ash": 4, "Water": 2}, {}),
    "P-059": ({"Limestone": 2, "Wood": 2}, {}),
    "P-060": ({}, {"Ink": 2}),
    "P-062": ({"Sand": 3}, {"Mortar": 4}),
    "P-063": ({"Water": 2}, {"Paper": 4}),
    "P-069": ({"Beer": 4}, {}),
    "P-070": ({"Copper": 2, "Charcoal": 2}, {"Brass": 3}),
    "P-074": ({"Sand": 4, "Potash": 2, "Wood": 3}, {"Glass": 4}),
    "P-076": ({}, {"Copper wire": 2}),
    "P-079": ({"Glass": 2}, {"Glass plate": 2}),
    "P-080": ({"Niter": 3, "Water": 2, "Wood": 2}, {"Potassium nitrate": 2}),
    "P-082": ({"Potassium nitrate": 6}, {"Black powder": 8}),
    "P-085": ({"Cinnabar": 2}, {}),
    "P-088": ({"Black powder": 4}, {"Corned powder": 4}),
    "P-091": ({}, {"Paper cartridge": 4}),
    "P-094": ({"Iron ore": 3, "Charcoal": 3}, {"Pig iron": 2}),
    "P-095": ({"Paper": 4}, {}),
    "P-098": ({"Salt": 2}, {"Hydrochloric acid": 2}),
    "P-099": ({"Pig iron": 3, "Charcoal": 2}, {"Wrought iron": 2}),
    "P-107": ({"Wood": 6}, {"Charcoal": 2}),
    "P-108": ({"Coal": 4}, {"Coke": 3}),
    "P-110": ({"Blister steel": 2}, {"Crucible steel": 2}),
    "P-111": ({"Sulfur": 2, "Water": 2}, {"Sulfuric acid": 3}),
    "P-112": ({"Zinc ore": 2}, {}),
    "P-114": ({"Coal": 2}, {}),
    "P-117": ({}, {"Sodium hydroxide": 2}),
    "P-126": ({"Iron ore": 3, "Coke": 2}, {"Pig iron": 2}),
    "P-129": ({"Limestone": 3}, {"Cement": 2}),
    "P-132": ({"Pig iron": 6}, {"Mild steel": 5, "Slag": 2}),
    "P-136": ({"Sand": 2, "Stone": 3}, {"Reinforced concrete": 4}),
    "P-138": ({"Mild steel": 2}, {"Steel plate": 2}),
    "P-143": ({"Crude oil": 5}, {}),
    "P-144": ({"Tin": 3, "Lead": 2}, {"Solder": 5}),
    "P-151": ({"Copper wire": 2}, {"Insulated wire": 2}),
    "P-161": ({}, {"Resistor": 8}),
    "P-163": ({"Water": 2}, {"Hydrogen": 2}),
    "P-166": ({"Bauxite": 4, "Water": 2}, {"Alumina": 2}),
    "P-170": ({}, {"Petrochemical feedstock": 2}),
    "P-177": ({"Salt": 2, "Water": 2}, {}),
    "P-179": ({"Alumina": 2}, {}),
    "P-180": ({"Wolframite": 2}, {"Tungsten wire": 4}),
    "P-183": ({}, {"Nitrogen": 4}),
    "P-185": ({"Mild steel": 7, "Ferrochrome": 2}, {"Stainless steel": 10}),
    "P-186": ({"Nickel": 4}, {"Resistance wire": 5}),
    "P-187": ({}, {"Capacitor": 4}),
    "P-190": ({"Hydrogen": 3}, {"Ammonia": 2}),
    "P-191": ({"Petrochemical feedstock": 2}, {"Thermoplastic": 3}),
    "P-194": ({}, {"Nitric acid": 4}),
    "P-202": ({"Zinc residue": 4}, {}),
    "P-184": ({"Bayer liquor": 4}, {}),
    "P-203": ({"Quartz": 2}, {}),
    "P-205": ({"Quartz": 2, "Coke": 2}, {}),
    "P-206": ({"Sand": 2, "Coke": 3}, {"Silicon carbide": 2}),
    "P-208": ({}, {"Copper foil": 2}),
    "P-212": ({}, {"Printed circuit board": 2}),
    "P-218": ({"Trichlorosilane": 2}, {}),
    "P-225": ({}, {"Germanium transistor": 4}),
    "P-230": ({"Polysilicon": 4}, {}),
    "P-233": ({"Ultrapure water": 2}, {"Silicon wafer": 8}),
    "P-236": ({}, {"Silicon transistor": 8}),
    "P-237": ({}, {"Integrated circuit": 4}),
    "P-240": ({}, {"Multilayer circuit board": 2}),
    "P-241": ({}, {"Network cable": 4}),
    "P-258": ({}, {"Microprocessor": 4}),
    "P-259": ({}, {"Memory chip": 4}),
    "P-264": ({}, {"Server CPU": 2}),
    "P-265": ({}, {"Memory module": 2}),
    "P-268": ({"Salt": 2}, {"Preserved food": 4}),
    "P-270": ({"Medicinal plants": 2}, {"Herbal remedies": 2}),
    "P-273": ({}, {"Wound dressings": 2}),
    "P-281": ({"Animal fat": 2}, {"Soap": 2}),
    "P-286": ({"Medicinal plants": 3}, {"Laudanum": 2}),
    "P-301": ({}, {"Smallpox vaccine": 4}),
    "P-303": ({"Medicinal plants": 4}, {"Quinine": 2}),
    "P-309": ({"Water": 4}, {"Antiseptic": 4}),
    "P-315": ({}, {"Canned food": 4}),
    "P-320": ({"Salt": 2}, {"Soda ash": 2}),
    "P-324": ({"Glass": 4}, {"Glass fibre": 4}),
    "P-325": ({"Nitric acid": 3}, {}),
    "P-326": ({}, {"Aspirin": 4}),
    "P-327": ({"Nitroglycerin": 3}, {"Dynamite": 4}),
    "P-341": ({}, {"Sulfuric acid": 4}),
    "P-342": ({"Hydrogen": 2}, {}),
    "P-348": ({"Naphtha": 2}, {"High-octane gasoline": 4}),
    "P-349": ({}, {"Metallic cartridge": 8}),
    "P-353": ({}, {"Vaccines": 4}),
    "P-360": ({"Coke": 3}, {}),
    "P-365": ({}, {"Hydrogen": 3}),
    "P-368": ({"Mild steel": 4}, {"High-speed steel": 4}),
    "P-369": ({"Aluminium": 8}, {"Duralumin": 10}),
    "P-377": ({"Water": 2}, {"Paper": 8}),
    "P-381": ({"Lithium ore": 4}, {}),
    "P-386": ({"Sand": 4}, {"Glass plate": 6}),
    "P-387": ({"Synthetic fibre": 2}, {}),
    "P-391": ({"Grain": 4, "Water": 4}, {"Penicillin": 2}),
    "P-393": ({"Titanium ore": 2, "Chlorine": 2, "Magnesium": 2}, {}),
    "P-406": ({"Grain": 4, "Water": 4}, {"Recombinant medicines": 2}),
}

# Functions acted out in the world rather than at a bench.
WORLD_FUNCTIONS = {
    "P-006": "Kill a wild animal with a hunting weapon (spear, musket or rifle) to get a carcass.",
    "P-028": "Break fully grown crops inside a formed Grain field while holding a Sickle.",
}

# Base seconds per run at each host ('hand' = performed by the player).
BASE_SECONDS = {
    "hand": 3, "Hearth": 10, "Workbench": 6, "Loom": 12, "Kiln": 30, "Forge": 15, "Bloomery": 60, "Smelting furnace": 30,
    "Still": 25, "Blast furnace": 40, "Coke oven": 60, "Glass furnace": 30, "Lathe": 15, "Screw press": 10, "Shipyard": 120,
    "Lead chamber": 40, "Water wheel": 10, "Windmill": 10, "Printing press": 8, "Boring mill": 40, "Engine lathe": 20,
    "Milling machine": 20, "Atmospheric engine": 30, "Chemistry laboratory": 20, "Steam engine": 20, "Machine shop": 30,
    "Bessemer converter": 30, "Rolling mill": 20, "Chemical plant": 40, "Oil refinery": 60, "Paper machine": 15,
    "Air separation plant": 40, "Microbiology laboratory": 60, "Cold store": 30, "Electric arc furnace": 40,
    "Fermentation plant": 120, "High-vacuum system": 30, "Siemens reactor": 90, "Ultrapure water system": 10,
    "Vacuum evaporator": 40, "Electronics laboratory": 30, "Zone refiner": 90, "Crystal puller": 120,
    "Diffusion furnace": 60, "Cleanroom": 60, "Mask aligner": 40, "CVD reactor": 60, "CNC machining centre": 40,
    "Wafer fab": 120, "Advanced wafer fab": 180, "Hospital": 30,
}
# Processes that are slow for physical reasons.
SECONDS_OVERRIDE = {
    "P-011": 40,   # tanning takes months
    "P-013": 30,   # a charcoal clamp smoulders for days
    "P-035": 60,   # fermentation
    "P-052": 90,   # cementation: days at red heat
    "P-081": 120,  # nitre beds take a year or more
    "P-108": 80,   # coking
    "P-230": 180,  # pulling an ingot
    "P-391": 240,  # penicillin fermentation
    "P-406": 240,
}

# --------------------------------------------------------------------------- items

TOOL_WEAR = {  # uses before an MOP tool wears out (0 = does not wear)
    "Flint flakes": 30, "Hand axe": 40, "Stone axe": 0, "Wooden spear": 0, "Hoe": 0, "Fire drill": 20, "Bone needle": 40,
    "Quern": 200, "Spindle": 200, "Sickle": 120, "Crucible": 25, "Mortar and pestle": 200, "Casting mold": 30,
    "Blowpipe": 150, "Bronze tool set": 250, "Iron tool set": 500, "Ox cart": 300, "Draw plate": 150,
    "Surgical instruments": 200, "Soldering iron": 300, "Photomask": 50, "Reticle": 100, "Power shovel": 500,
    "Pneumatic drill": 300, "Welding torch": 200,
}
VANILLA_TOOLS = {"Stone axe": "axe", "Hoe": "hoe", "Wooden spear": "spear"}
FOODS = {  # nutrition, saturation modifier
    "Bread": (5, 0.6), "Preserved food": (6, 0.8), "Canned food": (8, 0.8), "Beer": (2, 0.2), "Carcass": (3, 0.3),
    "Grain": (1, 0.1), "Flour": (1, 0.1),
}
MEDICINES = {  # effect id, seconds, amplifier, extra heal
    "Herbal remedies": ("minecraft:regeneration", 8, 0, 0),
    "Wound dressings": ("minecraft:regeneration", 6, 0, 4),
    "Laudanum": ("minecraft:resistance", 30, 0, 0),
    "Quinine": ("minecraft:regeneration", 10, 0, 0),
    "Aspirin": ("minecraft:regeneration", 10, 0, 2),
    "Antiseptic": ("minecraft:regeneration", 12, 0, 2),
    "Sulfa drugs": ("minecraft:regeneration", 20, 1, 0),
    "Penicillin": ("minecraft:regeneration", 30, 1, 4),
    "Insulin": ("minecraft:saturation", 1, 0, 2),
    "Vaccines": ("minecraft:resistance", 600, 0, 0),
    "Smallpox vaccine": ("minecraft:resistance", 300, 0, 0),
    "Recombinant medicines": ("minecraft:regeneration", 30, 2, 6),
}
CATEGORY_KEY = {
    "Material or component": "material", "Concept (theory)": "concept", "Station": "station",
    "Tool or instrument": "tool", "End product or by-product": "product", "Infrastructure": "infrastructure",
    "Evidence for theory": "evidence",
}

SHAPE_EXACT = {
    "Hand axe": "handaxe", "Flint flakes": "nugget", "Stone axe": "axe", "Wooden spear": "spear", "Hoe": "hoe",
    "Sickle": "sickle", "Fire drill": "rod", "Bone needle": "rod", "Quern": "lump", "Spindle": "rod", "Clay pot": "vessel",
    "Cloth": "cloth", "Crucible": "crucible", "Mortar and pestle": "crucible", "Casting mold": "brick",
    "Balance scale": "instrument", "Blowpipe": "rod", "Bronze tool set": "toolset", "Iron tool set": "toolset",
    "Ox cart": "vehicle", "Draw plate": "plate", "Gear train": "gear", "Surgical instruments": "toolset",
    "Glassware": "flask", "Microscope": "instrument", "Surface plate": "plate", "Theodolite": "instrument",
    "Telescope": "instrument", "Spectroscope": "instrument", "Sextant": "instrument", "Compass": "clock",
    "Mechanical clock": "clock", "Marine chronometer": "clock", "Barometer": "tube", "Timber": "rod", "Wheel": "gear",
    "Clay tablet": "brick", "Cut stone": "brick", "Movable type": "brick", "Hide": "hide", "Leather": "hide",
    "Carcass": "carcass", "Bone": "rod", "Sinew": "wire", "Animal fat": "lump", "Dung": "lump", "Horses": "carcass",
    "Draft ox": "carcass", "Silicon ingot": "crystal", "Ultra-pure germanium": "crystal", "Germanium": "crystal",
    "Paper": "paper", "Survey maps": "paper", "Photographic plate": "plate", "Glass plate": "plate", "Steel plate": "plate",
    "Tinplate": "plate", "Copper foil": "plate", "Steel pipe": "rod", "Graphite electrode": "rod",
    "Lens": "lens", "Precision lens": "lens", "Gold": "ingot", "Silver": "ingot", "Hide glue": "lump",
    "Pneumatic tyre": "vehicle", "Sailing boat": "ship", "Dugout canoe": "ship", "Hot-air balloon": "plane",
    "Gas balloon": "plane", "Cannon": "gun", "Field artillery": "gun", "Paper cartridge": "paper",
    "Metallic cartridge": "nugget", "Wood ash": "dust", "Slag": "lump", "Pitch": "lump", "Iron bloom": "lump",
    "Charcoal": "lump", "Coke": "lump", "Treatise": "book",
}
SHAPE_RULES = [
    (["wire", "cable", "rope", "cordage", "thread"], "wire"),
    (["vaccine", "penicillin", "insulin", "recombinant", "sulfa", "aspirin", "quinine", "laudanum", "remedies",
      "antiseptic"], "bottle"),
    (["hydrogen", "oxygen", "nitrogen", "argon", "chlorine", "ammonia", "coal gas", "process gases", "rare gases"], "canister"),
    (["acid", "ethanol", "beer", "ink", "oil", "solvent", "mercury", "glycerol", "methanol", "ether", "naphtha", "gasoline",
      "kerosene", "diesel", "liquor", "water", "tar", "formaldehyde", "nitroglycerin", "etchant", "trichlorosilane", "phenol",
      "epoxy", "polyol", "isocyanate", "dyes", "aromatics", "feedstock", "photoresist", "silicone", "dopant"], "flask"),
    (["integrated circuit", "microprocessor", "memory", "cpu", "transistor", "diode", "core memory"], "chip"),
    (["board", "motherboard", "network interface"], "board"),
    (["wafer"], "wafer"),
    (["battery", "solar cell", "solar panel"], "battery"),
    (["tube", "lamp", "detector", "jar"], "tube"),
    (["quartz", "crystal", "polysilicon", "silicon carbide", "technical ceramic"], "crystal"),
    (["copper", "tin", "bronze", "brass", "lead", "zinc", "nickel", "aluminium", "magnesium", "titanium", "iron", "steel",
      "superalloy", "duralumin", "gallium", "solder", "ferrochrome", "metallurgical silicon"], "ingot"),
    (["powder", "flour", "potash", "lime", "cement", "soda", "fertilizer", "alumina", "bleach", "carbide", "sulfate",
      "nitrate", "insecticide", "phosphorus", "lithium carbonate", "sodium hydroxide", "thermoplastic", "polyurethane",
      "fluoropolymer", "phenolic"], "dust"),
    (["brick", "porcelain"], "brick"),
    (["bread", "canned", "preserved"], "food"),
    (["books", "book", "maps"], "book"),
    (["cloth", "dressings", "fibre", "composite"], "cloth"),
    (["grain", "seed"], "grain"),
    (["musket", "rifle", "gun", "artillery"], "gun"),
    (["ship", "boat"], "ship"),
    (["rocket", "launch vehicle", "spacecraft", "satellite"], "rocket"),
    (["aircraft", "aeroplane", "airliner", "glider"], "plane"),
    (["cart", "wagon", "truck", "tractor", "locomotive", "tyre", "shovel"], "vehicle"),
    (["clock", "chronometer", "compass", "galvanometer", "gyroscope", "gauge"], "clock"),
    (["gear"], "gear"),
    (["mold", "mould", "die"], "brick"),
]


def item_shape(name):
    if name in SHAPE_EXACT:
        return SHAPE_EXACT[name]
    low = name.lower()
    for keys, shape in SHAPE_RULES:
        if any(k in low for k in keys):
            return shape
    return "device"


# --------------------------------------------------------------------------- build model

class Model:
    def __init__(self):
        self.items = {}        # id -> dict (registered non-block items + block-item metadata for stations)
        self.resources = {}    # id -> dict
        self.stations = {}     # id -> dict (placeable structures)
        self.functions = []
        self.ores = []
        self.blocks = []       # simple blocks
        self.lang = {}
        self.name_to_ref = {}  # spreadsheet name -> ref used in inputs (consumed)
        self.name_to_mop = {}  # spreadsheet name -> ref used in means of production


M = Model()
errors = []


def err(msg):
    errors.append(msg)


# ---- resources
for rname, r in RES_BY_NAME.items():
    d = RESOURCE_DEFS.get(rname)
    if d is None:
        err(f"No RESOURCE_DEFS entry for resource '{rname}'")
        continue
    rid = slug(rname)
    access = r.get("Access needs")
    recog = r.get("Recognised once known")
    entry = {"id": rid, "name": rname, "kind": r["Kind"].lower(), "description": r["Where found and how recognised"],
             "access": slug(access) if access else None,
             "recognisedBy": THEORY_ID[recog] if recog else None}
    if recog and recog not in THEORY_ID:
        err(f"Resource {rname}: unknown recognition theory {recog}")
    if "phen" in d:
        entry["source"] = "phenomenon"
        entry["phenomenon"] = d["phen"]
        entry["requirement"] = PHENOMENON_TEXT[d["phen"]]
        ref = f"phen:{d['phen']}"
    elif "env" in d:
        entry["source"] = "environment"
        entry["env"] = d["env"]
        entry["requirement"] = ENV_TEXT[d["env"]]
        ref = f"env:{d['env']}"
    else:
        entry["source"] = "tag"
        values = list(d.get("tag", []))
        if "custom" in d:
            values.insert(0, ns(d["custom"]))
            M.items[d["custom"]] = {"id": d["custom"], "name": rname, "category": "resource", "sheetCategory": "Resource",
                                    "era": 0, "depth": 0, "madeBy": [], "stack": 64, "shape": d.get("shape", "lump"),
                                    "colour": d.get("colour"), "resource": rid}
        values += d.get("tag_extra", [])
        entry["tag"] = f"resource/{rid}"
        entry["tagValues"] = values
        ref = f"tag:{rid}"
        entry["how"] = d.get("how")
    if "ore" in d:
        o = dict(d["ore"])
        o["resource"] = rid
        o["dropItem"] = o.pop("drop_item", ns(d["custom"]) if "custom" in d else None)
        if not o["dropItem"]:
            err(f"Ore for {rname} has no drop item")
        o["access"] = entry["access"]
        o["recognisedBy"] = entry["recognisedBy"]
        M.ores.append(o)
    if "reservoir" in d:
        rv = dict(d["reservoir"])
        rv["resource"] = rid
        rv["item"] = ns(d["custom"])
        rv["reservoir"] = True
        rv["access"] = entry["access"]
        M.ores.append(rv)
    M.resources[rid] = entry
    M.name_to_ref[rname] = ref
    M.name_to_mop[rname] = ref

# ---- items from the Items sheet
for iname, it in ITEM_BY_NAME.items():
    cat = CATEGORY_KEY[it["Category"]]
    if cat == "concept":
        continue
    iid = slug(iname)
    if iid in M.items:
        err(f"Duplicate item id {iid} ({iname})")
    made_by = [it["Made by"].split()[0]] + [x.split()[0] for x in split(it["Other ways to make it"])]
    entry = {"id": iid, "name": iname, "category": cat, "sheetCategory": it["Category"], "era": era_num(it["First era"]),
             "depth": it["Depth"], "madeBy": made_by, "usedBy": it["Used by (functions)"], "stack": 64,
             "shape": item_shape(iname)}
    M.items[iid] = entry
    M.name_to_ref[iname] = f"item:{iid}"
    if iname in STATION_SPECS:
        M.name_to_mop[iname] = f"station:{iid}"
    else:
        M.name_to_mop[iname] = f"item:{iid}"

# placeable structures
for sname, spec in STATION_SPECS.items():
    if sname not in ITEM_BY_NAME:
        err(f"Station spec for unknown item {sname}")
        continue
    sid = slug(sname)
    st = dict(spec)
    st["id"] = sid
    st["name"] = sname
    st["kind"] = "infrastructure" if ITEM_BY_NAME[sname]["Category"] == "Infrastructure" else "station"
    st.setdefault("conditions", [])
    st.setdefault("power", "none")
    st.setdefault("fuelTier", 0)
    st.setdefault("provider", None)
    st.setdefault("room", False)
    st.setdefault("ceiling", None)
    st.setdefault("interior", "any")
    st.setdefault("openTop", False)
    st.setdefault("chimney", False)
    st.setdefault("tiers", [{"w": 1, "h": 1, "d": 1, "batch": 1}])
    st.setdefault("producer", None)
    st.setdefault("coverage", False)
    st.setdefault("access", None)
    st.setdefault("note", None)
    if st["shape"] in ("area", "shaft"):
        st.setdefault("casing", None)
    elif "casing" not in st or (st["casing"] not in CASING_TAGS and st["casing"] not in CASING_UNIONS):
        err(f"Station {sname} has unknown casing {st.get('casing')}")
    M.stations[sid] = st
    M.items[sid]["station"] = True
    M.items[sid]["stack"] = 1

# every Station/Infrastructure used as means of production must be placeable
for f in FUNCTIONS:
    for m in split(f[MOP_COL]):
        if m in ITEM_BY_NAME and ITEM_BY_NAME[m]["Category"] in ("Station", "Infrastructure") and m not in STATION_SPECS:
            err(f"{f['ID']} needs {m} in place but there is no structure spec for it")

# tool properties: stack sizes and wear
consumed_names = set()
mop_names = set()
for f in FUNCTIONS:
    if f["Type"] == "Practical":
        consumed_names.update(split(f["Consumed inputs"]))
    mop_names.update(split(f[MOP_COL]))
for iid, it in M.items.items():
    name = it["name"]
    if it.get("station") or it["category"] == "resource":
        continue
    if it["category"] in ("tool", "evidence", "infrastructure"):
        if name in consumed_names:
            it["stack"] = 16
        else:
            it["stack"] = 1
            if TOOL_WEAR.get(name):
                it["durability"] = TOOL_WEAR[name]
    if name in VANILLA_TOOLS:
        it["tool"] = VANILLA_TOOLS[name]
        it["stack"] = 1
    if name == "Sickle":
        it["tool"] = "sickle"
    if name in FOODS:
        it["food"] = {"nutrition": FOODS[name][0], "saturation": FOODS[name][1]}
    if name in MEDICINES:
        e = MEDICINES[name]
        it["medicine"] = {"effect": e[0], "seconds": e[1], "amplifier": e[2], "heal": e[3]}
        it["stack"] = 16
    if name == "Fertilizer":
        it["fertilizer"] = True
    if it["category"] == "infrastructure" and not it.get("station"):
        it["capstone"] = True
    if name in ("Glassware", "Cloth", "Clay pot"):
        it["stack"] = 64

# extra items not in the spreadsheet
M.items["treatise"] = {"id": "treatise", "name": "Treatise", "category": "special", "sheetCategory": "Record", "era": 2,
                       "depth": 0, "madeBy": [], "stack": 1, "shape": "book", "treatise": True}

# ---- construction blocks (vanilla crafting recipes join tree items to building blocks)
SIMPLE_BLOCKS = [
    {"id": "limestone", "name": "Limestone", "hardness": 1.5, "resistance": 6.0, "sound": "stone", "tool": "pickaxe",
     "texture": ("stone", (196, 190, 168)), "drops": "self"},
    {"id": "limestone_bricks", "name": "Limestone Bricks", "hardness": 1.5, "resistance": 6.0, "sound": "stone",
     "tool": "pickaxe", "texture": ("brick", (200, 194, 172)), "drops": "self"},
    {"id": "machine_frame", "name": "Timber-and-Iron Machine Frame", "hardness": 3.0, "resistance": 6.0, "sound": "wood",
     "tool": "axe", "texture": ("panel", (140, 110, 80)), "drops": "self"},
    {"id": "cast_iron_block", "name": "Cast Iron Block", "hardness": 5.0, "resistance": 6.0, "sound": "metal",
     "tool": "pickaxe", "texture": ("panel", (90, 90, 96)), "drops": "self"},
    {"id": "steel_casing", "name": "Steel Machine Casing", "hardness": 5.0, "resistance": 9.0, "sound": "metal",
     "tool": "pickaxe", "texture": ("panel", (150, 158, 170)), "drops": "self"},
    {"id": "stainless_casing", "name": "Stainless Steel Casing", "hardness": 5.0, "resistance": 9.0, "sound": "metal",
     "tool": "pickaxe", "texture": ("panel", (200, 205, 212)), "drops": "self"},
    {"id": "reinforced_concrete_block", "name": "Reinforced Concrete", "hardness": 6.0, "resistance": 30.0, "sound": "stone",
     "tool": "pickaxe", "texture": ("concrete", (170, 170, 165)), "drops": "self"},
    {"id": "lead_lining", "name": "Lead Sheet Lining", "hardness": 3.0, "resistance": 9.0, "sound": "metal",
     "tool": "pickaxe", "texture": ("panel", (105, 105, 125)), "drops": "self"},
    {"id": "cleanroom_panel", "name": "Cleanroom Wall Panel", "hardness": 3.0, "resistance": 6.0, "sound": "metal",
     "tool": "pickaxe", "texture": ("panel", (232, 234, 238)), "drops": "self"},
    {"id": "fan_filter_unit", "name": "Fan Filter Unit", "hardness": 3.0, "resistance": 6.0, "sound": "metal",
     "tool": "pickaxe", "texture": ("grid", (220, 224, 230)), "drops": "self"},
]
for o in M.ores:
    if o.get("reservoir"):
        M.blocks.append({"id": o["block"], "name": o["name"], "hardness": 3.0, "resistance": 3.0, "sound": "stone",
                         "tool": "pickaxe", "texture": ("ore", (60, 60, 64)), "drops": "nothing", "ore": True})
    else:
        host = o["host"]
        hard = {"stone": 3.0, "deepslate": 4.5, "sand": 0.6, "dirt": 0.7}[host]
        tool = {"stone": "pickaxe", "deepslate": "pickaxe", "sand": "shovel", "dirt": "shovel"}[host]
        M.blocks.append({"id": o["block"], "name": o["name"], "hardness": hard, "resistance": 3.0,
                         "sound": {"stone": "stone", "deepslate": "deepslate", "sand": "sand", "dirt": "gravel"}[host],
                         "tool": tool, "level": "iron" if host == "deepslate" else ("stone" if host == "stone" else None),
                         "texture": ("ore", None), "drops": "ore", "ore": True})
M.blocks = SIMPLE_BLOCKS + M.blocks
block_ids = [b["id"] for b in M.blocks]
assert len(block_ids) == len(set(block_ids)), "duplicate block ids"
for b in M.blocks:
    if b["id"] in M.items:
        err(f"Block id {b['id']} collides with an item id")


# ---- functions
def ref_for(name, consumed):
    table = M.name_to_ref if consumed else M.name_to_mop
    if name in table:
        return table[name]
    err(f"Cannot resolve '{name}'")
    return f"item:{slug(name)}"


host_count = defaultdict(int)
for f in FUNCTIONS:
    fid = f["ID"]
    typ = "theoretical" if f["Type"] == "Theoretical" else "practical"
    qi, qo = QTY.get(fid, ({}, {}))
    ins = split(f["Consumed inputs"])
    outs = split(f["Outputs"])
    mops = split(f[MOP_COL])
    for k in qi:
        if k not in ins:
            err(f"QTY {fid}: '{k}' is not an input")
    for k in qo:
        if k not in outs:
            err(f"QTY {fid}: '{k}' is not an output")
    fn = {
        "id": fid, "name": f["Function"], "type": typ,
        "inputs": [{"ref": ref_for(n, True), "name": n, "count": qi.get(n, 1)} for n in ins],
        "outputs": [] if typ == "theoretical" else [{"ref": ref_for(n, True), "name": n, "count": qo.get(n, 1)} for n in outs],
        "means": [{"ref": ref_for(n, False), "name": n} for n in mops],
        "theory": [THEORY_ID[t] for t in split(f["Required theory"])],
        "mechanism": f["Mechanism the player must explain"],
        "notes": f["Long-running effect or notes"],
        "era": era_num(f["Era"]), "eraName": f["Era"].split(" ", 1)[1], "route": f["Route"], "depth": f["Depth"],
        "prerequisites": f["Prerequisite functions"], "unlocks": f["Unlocks"],
    }
    for ref in fn["outputs"]:
        if not ref["ref"].startswith("item:"):
            err(f"{fid} outputs non-item {ref}")
    if typ == "theoretical":
        fn["mode"] = "theory"
        fn["host"] = None
    else:
        host = next((m for m in mops if m in STATION_SPECS and ITEM_BY_NAME[m]["Category"] == "Station"), None)
        if fid in WORLD_FUNCTIONS:
            fn["mode"] = "world"
            fn["host"] = None
            fn["worldHint"] = WORLD_FUNCTIONS[fid]
        elif fid == "P-037":
            fn["mode"] = "producer"
            fn["host"] = "livestock_herd"
        elif host:
            fn["mode"] = "station"
            fn["host"] = slug(host)
            host_count[host] += 1
        else:
            fn["mode"] = "hand"
            fn["host"] = None
        base = BASE_SECONDS["hand" if fn["mode"] != "station" else host]
        secs = base + 1.0 * len(ins)
        if fn["mode"] == "hand" and any(ITEM_BY_NAME.get(o, {}).get("Category") in ("Station", "Infrastructure") for o in outs):
            secs *= 2.5
        secs = SECONDS_OVERRIDE.get(fid, secs)
        fn["ticks"] = int(round(secs * 20))
    M.functions.append(fn)

for sid, st in M.stations.items():
    st["hosts"] = [fn["id"] for fn in M.functions if fn.get("host") == sid]

# ---- reachability: every function must be reachable from nature
FN = {fn["id"]: fn for fn in M.functions}
producers = defaultdict(list)
for fn in M.functions:
    for o in fn["outputs"]:
        producers[o["ref"]].append(fn["id"])
station_of_item = {f"station:{sid}": f"item:{sid}" for sid in M.stations}
access_fn = {"shaft_mine": "P-053", "deep_mine": "P-127", "drilling_rig": "P-141"}
known = set()
have = set()
for rid, r in M.resources.items():
    if not r["recognisedBy"] and not r["access"]:
        have.add({"phenomenon": f"phen:{r.get('phenomenon')}", "environment": f"env:{r.get('env')}"}.get(r["source"], f"tag:{rid}"))
changed = True
while changed:
    changed = False
    for rid, r in M.resources.items():
        ref = f"tag:{rid}"
        if ref in have:
            continue
        ok = (not r["recognisedBy"] or r["recognisedBy"] in known) and (not r["access"] or access_fn[r["access"]] in known)
        if ok:
            have.add(ref)
            changed = True
    for fn in M.functions:
        if fn["id"] in known:
            continue
        need = [i["ref"] for i in fn["inputs"]] + [station_of_item.get(m["ref"], m["ref"]) for m in fn["means"]]
        if all(t in known for t in fn["theory"]) and all(n in have for n in need):
            host = fn.get("host")
            if host:
                st = M.stations[host]
                if st["power"] == "grid" and "P-176" not in known:
                    continue
            known.add(fn["id"])
            for o in fn["outputs"]:
                have.add(o["ref"])
            changed = True
unreachable = [fn["id"] for fn in M.functions if fn["id"] not in known]
if unreachable:
    err(f"Unreachable functions: {unreachable}")


# ---- power requirements must not come before the grid exists
def closure(fid, seen=None):
    """Functions on the shallowest route to fid (theory + cheapest producer of each need)."""
    if seen is None:
        seen = set()
    if fid in seen:
        return seen
    seen.add(fid)
    fn = FN[fid]
    for t in fn["theory"]:
        closure(t, seen)
    for ref in [i["ref"] for i in fn["inputs"]] + [station_of_item.get(m["ref"], m["ref"]) for m in fn["means"]]:
        ps = producers.get(ref)
        if ps:
            best = min(ps, key=lambda p: FN[p]["depth"])
            closure(best, seen)
    return seen


for sid, st in M.stations.items():
    if st["power"] != "grid":
        continue
    users = [fn["id"] for fn in M.functions if fn.get("host") == sid or any(m["ref"] == f"station:{sid}" for m in fn["means"])]
    bad = [u for u in users if "P-176" not in closure(u)]
    if bad:
        print(f"note: {sid} cannot require grid power ({bad} come before the grid); dropping that requirement")
        st["power"] = "none"

if errors:
    print("\n".join("ERROR: " + e for e in errors))
    sys.exit(1)

# --------------------------------------------------------------------------- write data
if os.path.isdir(OUT):
    shutil.rmtree(OUT)
os.makedirs(OUT)

tree = {
    "format": 1,
    "source": os.path.basename(XLSX),
    "resources": list(M.resources.values()),
    "items": list(M.items.values()),
    "stations": list(M.stations.values()),
    "functions": M.functions,
    "ores": M.ores,
    "blocks": M.blocks,
}
write_json(os.path.join(DATA, "techtree", "techtree.json"), tree)

# ---- lang
lang = {
    "itemGroup.spawn_9b5fbb9.resources": "Tech Tree: Natural Resources",
    "itemGroup.spawn_9b5fbb9.stations": "Tech Tree: Stations & Infrastructure",
    "itemGroup.spawn_9b5fbb9.items": "Tech Tree: Materials, Tools & Products",
    "key.category.spawn_9b5fbb9.techtree": "Tech Tree",
    "key.spawn_9b5fbb9.journal": "Open Journal",
    "spawn_9b5fbb9.configuration.title": "Tech Tree Configuration",
}
for it in M.items.values():
    key = "block" if it.get("station") else "item"
    lang[f"{key}.{MODID}.{it['id']}"] = it["name"]
for b in M.blocks:
    lang[f"block.{MODID}.{b['id']}"] = b["name"]
lang[f"block.{MODID}.cornerstone"] = "Cornerstone"
with open(os.path.join(HERE, "lang_extra.json"), encoding="utf-8") as f:
    lang.update(json.load(f))
write_json(os.path.join(ASSETS, "lang", "en_us.json"), lang)

# ---- textures, models
tex_item = os.path.join(ASSETS, "textures", "item")
tex_block = os.path.join(ASSETS, "textures", "block")
os.makedirs(tex_item, exist_ok=True)
os.makedirs(tex_block, exist_ok=True)

for it in M.items.values():
    if it.get("station"):
        continue
    tex.render_item(it["shape"], it["name"], os.path.join(tex_item, it["id"] + ".png"), it.get("colour"))
    write_json(os.path.join(ASSETS, "models", "item", it["id"] + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": ns("item/" + it["id"])}})
    model = {"model": {"type": "minecraft:model", "model": ns("item/" + it["id"])}}
    write_json(os.path.join(ASSETS, "items", it["id"] + ".json"), model)


def cube_all(bid, texture):
    write_json(os.path.join(ASSETS, "models", "block", bid + ".json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": ns("block/" + texture)}})
    write_json(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {"": {"model": ns("block/" + bid)}}})
    write_json(os.path.join(ASSETS, "items", bid + ".json"), {"model": {"type": "minecraft:model", "model": ns("block/" + bid)}})


ore_by_block = {o["block"]: o for o in M.ores}
for i, b in enumerate(M.blocks):
    kind, colour = b["texture"]
    path = os.path.join(tex_block, b["id"] + ".png")
    if kind == "stone":
        tex.stone_tile(1000 + i, colour).save(path)
    elif kind == "brick":
        tex.brick_block(path, colour, tex._shade(colour, 0.7), 1000 + i)
    elif kind == "panel":
        tex.panel_block(path, colour, tex._shade(colour, 0.6), 1000 + i)
    elif kind == "grid":
        tex.panel_block(path, colour, tex._shade(colour, 0.55), 1000 + i, rivets=False, grid=True)
    elif kind == "concrete":
        tex.panel_block(path, colour, tex._shade(colour, 0.8), 1000 + i, rivets=False)
    elif kind == "ore":
        o = ore_by_block[b["id"]]
        if o.get("reservoir"):
            speck = (40, 30, 25) if "oil" in b["id"] else (200, 170, 90)
            tex.ore_block(path, (70, 70, 74), speck, 1000 + i)
        else:
            host_col = {"stone": (125, 125, 125), "deepslate": (75, 75, 82), "sand": (219, 207, 163), "dirt": (110, 82, 56)}[o["host"]]
            item = M.items.get(o["dropItem"].split(":")[1]) if o["dropItem"].startswith(MODID) else None
            name = item["name"] if item else o["dropItem"]
            base, _ = tex.colours_for(name, item.get("colour") if item else None)
            if o["dropItem"] == "minecraft:quartz":
                base = (240, 240, 245)
            if o["dropItem"] == "minecraft:raw_iron":
                base = (160, 90, 50)
            tex.ore_block(path, host_col, base, 1000 + i)
    cube_all(b["id"], b["id"])

# station controllers
for i, st in enumerate(M.stations.values()):
    sid = st["id"]
    frame, accent = FAMILIES[st["family"]]
    tex.station_front(os.path.join(tex_block, sid + "_front.png"), frame, accent, 500 + i, False)
    tex.station_front(os.path.join(tex_block, sid + "_front_on.png"), frame, accent, 500 + i, True)
    tex.station_side(os.path.join(tex_block, sid + "_side.png"), frame, 700 + i)
    for suffix, front in (("", "_front"), ("_on", "_front_on")):
        write_json(os.path.join(ASSETS, "models", "block", sid + suffix + ".json"),
                   {"parent": "minecraft:block/orientable",
                    "textures": {"front": ns(f"block/{sid}{front}"), "side": ns(f"block/{sid}_side"),
                                 "top": ns(f"block/{sid}_side")}})
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for formed in ("false", "true"):
            v = {"model": ns("block/" + sid + ("_on" if formed == "true" else ""))}
            if rot:
                v["y"] = rot
            variants[f"facing={facing},formed={formed}"] = v
    write_json(os.path.join(ASSETS, "blockstates", sid + ".json"), {"variants": variants})
    write_json(os.path.join(ASSETS, "items", sid + ".json"), {"model": {"type": "minecraft:model", "model": ns("block/" + sid)}})

# cornerstone (engineering engine): a facing block that is not part of the tech tree itself
tex.brick_block(os.path.join(tex_block, "cornerstone_side.png"), (150, 146, 138), (104, 100, 94), 4242)
tex.station_front(os.path.join(tex_block, "cornerstone_front.png"), (150, 146, 138), (200, 170, 90), 4243, False)
write_json(os.path.join(ASSETS, "models", "block", "cornerstone.json"),
           {"parent": "minecraft:block/orientable",
            "textures": {"front": ns("block/cornerstone_front"), "side": ns("block/cornerstone_side"), "top": ns("block/cornerstone_side")}})
write_json(os.path.join(ASSETS, "blockstates", "cornerstone.json"), {"variants": {
    f"facing={f}": ({"model": ns("block/cornerstone"), "y": r} if r else {"model": ns("block/cornerstone")})
    for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
write_json(os.path.join(ASSETS, "items", "cornerstone.json"), {"model": {"type": "minecraft:model", "model": ns("block/cornerstone")}})

# ---- loot tables
def loot(bid, entries):
    write_json(os.path.join(DATA, "loot_table", "blocks", bid + ".json"),
               {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": entries}] if entries else [],
                "random_sequence": ns("blocks/" + bid)})


def self_drop(bid):
    return [{"type": "minecraft:item", "name": ns(bid)}]


for b in M.blocks:
    if b["drops"] == "self":
        write_json(os.path.join(DATA, "loot_table", "blocks", b["id"] + ".json"),
                   {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                                                          "entries": self_drop(b["id"])}],
                    "random_sequence": ns("blocks/" + b["id"])})
    elif b["drops"] == "nothing":
        loot(b["id"], [])
    else:
        o = ore_by_block[b["id"]]
        lo, hi = o["drop"]
        mods = []
        if hi > 1 or lo > 1:
            mods.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
        mods.append({"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"})
        mods.append({"type": "minecraft:explosion_decay"})
        loot(b["id"], [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": ns(b["id"])},
            {"type": "minecraft:item", "modifier": mods, "name": o["dropItem"]}]}])
write_json(os.path.join(DATA, "loot_table", "blocks", "cornerstone.json"),
           {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                                                  "entries": self_drop("cornerstone")}],
            "random_sequence": ns("blocks/cornerstone")})
for st in M.stations.values():
    write_json(os.path.join(DATA, "loot_table", "blocks", st["id"] + ".json"),
               {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                                                      "entries": self_drop(st["id"])}],
                "random_sequence": ns("blocks/" + st["id"])})


# ---- tags
def tag(kind, path, values, namespace=MODID):
    write_json(os.path.join(OUT, "data", namespace, "tags", kind, path + ".json"), {"values": values})


for r in M.resources.values():
    if r["source"] == "tag":
        tag("item", r["tag"], r["tagValues"])
for name, values in CASING_TAGS.items():
    tag("block", "casing/" + name, values)
for name, parts in CASING_UNIONS.items():
    tag("block", "casing/" + name, ["#" + ns("casing/" + p) for p in parts])
tag("block", "field_soil", ["minecraft:farmland"])
tag("block", "pen_fencing", ["#minecraft:fences", "#minecraft:fence_gates", "#minecraft:walls"])
tag("block", "shaft_passable", ["#minecraft:climbable", "#minecraft:chains", "#minecraft:rails", "minecraft:torch",
                                "minecraft:wall_torch"])
tag("block", "fiber_plants", ["minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
                              "minecraft:bush"])
tag("block", "latex_trees", ["#minecraft:jungle_logs"])
tag("block", "fire_sources", ["#minecraft:fire", "#minecraft:campfires", "minecraft:lava", "minecraft:magma_block"])
gate = defaultdict(list)
gate_vanilla = {"shaft_mine": ["#minecraft:coal_ores"], "deep_mine": [], "drilling_rig": []}
for o in M.ores:
    if o.get("access"):
        gate["requires/" + o["access"]].append(ns(o["block"]))
    if o.get("recognisedBy"):
        gate["unrecognised_until/" + o["recognisedBy"].lower().replace("-", "_")].append(ns(o["block"]))
for acc, vals in gate_vanilla.items():
    gate["requires/" + acc] += vals
for r in M.resources.values():
    if r["recognisedBy"] and r["id"] == "iron_ore":
        gate["unrecognised_until/" + r["recognisedBy"].lower().replace("-", "_")].append("#minecraft:iron_ores")
for path, values in gate.items():
    tag("block", path, values)
for acc in ("shaft_mine", "deep_mine", "drilling_rig"):
    if "requires/" + acc not in gate:
        tag("block", "requires/" + acc, [])
recog_theories = sorted({r["recognisedBy"] for r in M.resources.values() if r["recognisedBy"]})
for t in recog_theories:
    key = "unrecognised_until/" + t.lower().replace("-", "_")
    if key not in gate:
        tag("block", key, [])

# ---- physics: what counts as ground, what carries no load, and each block's structural material
tag("block", "physics/terrain", ["#minecraft:dirt", "#minecraft:base_stone_overworld", "#minecraft:base_stone_nether", "#minecraft:sand",
                                 "minecraft:gravel", "minecraft:clay", "#minecraft:mud", "#minecraft:ice", "#minecraft:snow",
                                 "minecraft:bedrock", "minecraft:end_stone", "minecraft:netherrack", "#c:ores",
                                 "#minecraft:nylium", "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:moss_block"]
    + [ns(b["id"]) for b in M.blocks if b.get("ore")] + [ns("limestone")])
tag("block", "physics/non_structural", ["#minecraft:leaves", "minecraft:cobweb", "minecraft:scaffolding", "minecraft:snow"])
MATERIAL_TAGS = {
    "thatch": ["#minecraft:wool", "#minecraft:wool_carpets", "minecraft:hay_block", "minecraft:bamboo_mosaic", "minecraft:dried_kelp_block",
               "minecraft:sponge", "minecraft:wet_sponge"],
    "timber": ["#minecraft:logs", "#minecraft:planks", "#minecraft:wooden_slabs", "#minecraft:wooden_stairs", "#minecraft:wooden_fences",
               "#minecraft:fence_gates", "#minecraft:wooden_doors", "#minecraft:wooden_trapdoors", "#minecraft:bamboo_blocks",
               "#minecraft:beds", "minecraft:crafting_table", "minecraft:chest", "minecraft:barrel", "minecraft:bookshelf",
               ns("machine_frame"), ns("workbench"), ns("loom")],
    "glass": ["#c:glass_blocks", "#c:glass_panes"],
    "brick": ["minecraft:bricks", "minecraft:brick_slab", "minecraft:brick_stairs", "minecraft:brick_wall", "minecraft:mud_bricks",
              "minecraft:mud_brick_slab", "minecraft:mud_brick_stairs", "minecraft:mud_brick_wall", "minecraft:packed_mud",
              "#minecraft:terracotta", "minecraft:nether_bricks", "minecraft:red_nether_bricks"],
    "concrete": ["#minecraft:concrete", "#minecraft:concrete_slabs", "#minecraft:concrete_stairs"],
    "reinforced_concrete": [ns("reinforced_concrete_block")],
    "iron": ["minecraft:iron_block", "#minecraft:bars", "#minecraft:chains", "minecraft:iron_door", "minecraft:iron_trapdoor",
             "#minecraft:copper", ns("cast_iron_block"), ns("steel_casing"), ns("lead_lining"), "minecraft:anvil"],
    "stainless": [ns("stainless_casing"), ns("cleanroom_panel"), ns("fan_filter_unit")],
    "earth": ["#minecraft:concrete_powders", "minecraft:dirt_path", "minecraft:farmland"],
    "masonry": [],
}
for name, values in MATERIAL_TAGS.items():
    tag("block", "physics/material/" + name, values)

pick = [ns(b["id"]) for b in M.blocks if b["tool"] == "pickaxe"] + [ns(s) for s, st in M.stations.items()
                                                                  if st["family"] in ("stone", "clay", "metal", "industrial", "precision", "cleanroom", "concrete", "lead")]
axe = [ns(b["id"]) for b in M.blocks if b["tool"] == "axe"] + [ns(s) for s, st in M.stations.items() if st["family"] in ("timber", "farm")]
shovel = [ns(b["id"]) for b in M.blocks if b["tool"] == "shovel"]
tag("block", "mineable/pickaxe", pick + [ns("cornerstone")], "minecraft")
tag("block", "mineable/axe", axe, "minecraft")
tag("block", "mineable/shovel", shovel, "minecraft")
tag("block", "needs_stone_tool", [ns(b["id"]) for b in M.blocks if b.get("level") == "stone"], "minecraft")
tag("block", "needs_iron_tool", [ns(b["id"]) for b in M.blocks if b.get("level") == "iron"], "minecraft")

# item tags used by code
tag("item", "hunting_weapons", [ns("wooden_spear"), "#minecraft:spears", ns("musket"), ns("rifle"), ns("repeating_rifle")])
tag("item", "fuel/wood", ["#minecraft:logs"])
tag("item", "fuel/wood_small", ["#minecraft:planks", "minecraft:stick", ns("timber")])
tag("item", "fuel/charcoal", ["minecraft:charcoal", ns("charcoal")])
tag("item", "fuel/coal", ["minecraft:coal", "minecraft:coal_block"])
tag("item", "fuel/coke", [ns("coke")])
tag("item", "water_containers", ["minecraft:water_bucket", "minecraft:potion"])

# ---- logistics: what goes off, and how fast (half-lives of 2, 6 and 30 game days; see Spoilage.java).
# Salted, smoked and canned food keeps, as do seeds, sugar and honey. Stews are left out: they would take their bowls with them.
tag("item", "spoilage/fast", [ns("carcass"), "minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton",
                              "minecraft:rabbit", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish"])
tag("item", "spoilage/medium", [ns("bread"), ns("beer"), ns("smallpox_vaccine"), ns("vaccines"), ns("insulin"), ns("recombinant_medicines"),
                                "minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
                                "minecraft:cooked_mutton", "minecraft:cooked_rabbit", "minecraft:cooked_cod", "minecraft:cooked_salmon",
                                "minecraft:baked_potato", "minecraft:pumpkin_pie", "minecraft:sweet_berries", "minecraft:glow_berries",
                                "minecraft:melon_slice"])
tag("item", "spoilage/slow", [ns("grain"), ns("flour"), ns("hide"), ns("animal_fat"), ns("penicillin"),
                              "minecraft:wheat", "minecraft:potato", "minecraft:carrot", "minecraft:beetroot", "minecraft:apple",
                              "minecraft:egg", "minecraft:cookie"])
tag("entity_type", "wild_game", ["minecraft:pig", "minecraft:cow", "minecraft:sheep", "minecraft:goat", "minecraft:rabbit",
                                 "minecraft:chicken", "minecraft:mooshroom", "minecraft:llama", "minecraft:camel",
                                 "minecraft:armadillo", "minecraft:hoglin"])
tag("entity_type", "wild_herd_animals", ["minecraft:cow", "minecraft:sheep", "minecraft:goat", "minecraft:pig",
                                         "minecraft:mooshroom", "minecraft:llama"])
tag("entity_type", "wild_horses", ["minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:camel"])

# ---- vanilla crafting recipes for building blocks made from tree items
def shaped(rid, pattern, key, result, count=1):
    write_json(os.path.join(DATA, "recipe", rid + ".json"),
               {"type": "minecraft:crafting_shaped", "category": "building", "key": key, "pattern": pattern,
                "result": {"count": count, "id": result}})


def shapeless(rid, ingredients, result, count=1):
    write_json(os.path.join(DATA, "recipe", rid + ".json"),
               {"type": "minecraft:crafting_shapeless", "category": "building", "ingredients": ingredients,
                "result": {"count": count, "id": result}})


shapeless("cornerstone", ["#" + ns("resource/stone"), "minecraft:stick"], ns("cornerstone"))
shaped("bricks_from_fired_brick", ["##", "##"], {"#": ns("brick")}, "minecraft:bricks")
shapeless("stone_bricks_from_cut_stone", [ns("cut_stone")], "minecraft:stone_bricks", 2)
shaped("limestone_bricks", ["##", "##"], {"#": ns("limestone")}, ns("limestone_bricks"), 4)
shaped("machine_frame", ["WTW", "T T", "WTW"], {"W": ns("wrought_iron"), "T": ns("timber")}, ns("machine_frame"), 4)
shaped("cast_iron_block", ["###", "###", "###"], {"#": ns("pig_iron")}, ns("cast_iron_block"))
shaped("steel_casing", ["PMP", "M M", "PMP"], {"P": ns("steel_plate"), "M": ns("mild_steel")}, ns("steel_casing"), 4)
shaped("stainless_casing", ["SPS", "P P", "SPS"], {"S": ns("stainless_steel"), "P": ns("steel_plate")}, ns("stainless_casing"), 4)
shapeless("reinforced_concrete_block", [ns("reinforced_concrete")], ns("reinforced_concrete_block"), 2)
shaped("lead_lining", ["##", "##"], {"#": ns("lead")}, ns("lead_lining"), 2)
shaped("cleanroom_panel", ["SPS", "PGP", "SPS"], {"S": ns("stainless_steel"), "P": ns("thermoplastic"), "G": ns("glass_plate")},
       ns("cleanroom_panel"), 8)
shapeless("fan_filter_unit", [ns("hepa_filter"), ns("electric_motor"), ns("stainless_casing")], ns("fan_filter_unit"))

# ---- worldgen
def height(lo, hi):
    return {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}


TARGETS = {
    "stone": [{"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
              {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}],
    "deepslate": [{"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
                  {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}],
    "sand": [{"predicate_type": "minecraft:block_match", "block": "minecraft:sand"},
             {"predicate_type": "minecraft:block_match", "block": "minecraft:red_sand"}],
    "dirt": [{"predicate_type": "minecraft:block_match", "block": "minecraft:dirt"},
             {"predicate_type": "minecraft:block_match", "block": "minecraft:mud"},
             {"predicate_type": "minecraft:block_match", "block": "minecraft:clay"},
             {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}],
    "netherrack": [{"predicate_type": "minecraft:block_match", "block": "minecraft:netherrack"}],
}


def biome_list(b):
    if isinstance(b, str):
        return b
    return b


def add_ore_feature(fid, block, target, size, count, lo, hi, biomes, air=False, rarity=None):
    write_json(os.path.join(DATA, "worldgen", "feature", fid + ".json"),
               {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": size,
                "targets": [{"state": ns(block), "target": t} for t in TARGETS[target]]})
    placement = []
    if rarity:
        placement.append({"type": "minecraft:rarity_filter", "chance": rarity})
    else:
        placement.append({"type": "minecraft:count", "count": count})
    placement += [{"type": "minecraft:in_square"}, {"type": "minecraft:height_range", "height": height(lo, hi)},
                  {"type": "minecraft:biome"}]
    write_json(os.path.join(DATA, "worldgen", "placed_feature", fid + ".json"), {"feature": ns(fid), "placement": placement})
    bl = biomes
    if isinstance(bl, list) and any(x.startswith("#") for x in bl):
        # a mix of tags: one modifier per tag
        for j, b in enumerate(bl):
            write_json(os.path.join(DATA, "neoforge", "biome_modifier", f"{fid}_{j}.json"),
                       {"type": "neoforge:add_features", "biomes": b, "features": ns(fid), "step": "underground_ores"})
    else:
        write_json(os.path.join(DATA, "neoforge", "biome_modifier", fid + ".json"),
                   {"type": "neoforge:add_features", "biomes": bl, "features": ns(fid), "step": "underground_ores"})


add_ore_feature("limestone_beds", "limestone", "stone", 60, 2, 0, 110, "#minecraft:is_overworld")
for o in M.ores:
    if o.get("reservoir"):
        add_ore_feature(o["block"], o["block"], "deepslate", o["size"], 1, o["min"], o["max"], "#minecraft:is_overworld",
                        rarity=o["rarity"])
        continue
    add_ore_feature(o["block"], o["block"], o["host"], o["size"], o["count"], o["min"], o["max"], o["biomes"])
    if "extra" in o:
        e = o["extra"]
        add_ore_feature(o["block"] + "_extra", o["block"], e.get("target", o["host"]), o["size"], e["count"], e["min"], e["max"],
                        e["biomes"])

# ---- game-test structure: a blank 32 x 24 x 32 volume (same layout as vanilla's empty.nbt)
def nbt_string(s):
    b = s.encode("utf-8")
    return len(b).to_bytes(2, "big") + b


def test_structure(path, size):
    out = bytearray()
    out += b"\x0a" + nbt_string("")
    out += b"\x09" + nbt_string("size") + b"\x03" + (3).to_bytes(4, "big")
    for v in size:
        out += int(v).to_bytes(4, "big", signed=True)
    out += b"\x09" + nbt_string("entities") + b"\x00" + (0).to_bytes(4, "big")
    out += b"\x09" + nbt_string("blocks") + b"\x0a" + (1).to_bytes(4, "big")
    out += b"\x09" + nbt_string("pos") + b"\x03" + (3).to_bytes(4, "big") + (0).to_bytes(4, "big") * 3
    out += b"\x03" + nbt_string("state") + (0).to_bytes(4, "big")
    out += b"\x00"
    out += b"\x09" + nbt_string("palette") + b"\x0a" + (1).to_bytes(4, "big")
    out += b"\x08" + nbt_string("id") + nbt_string("minecraft:air") + b"\x00"
    out += b"\x03" + nbt_string("DataVersion") + (5023).to_bytes(4, "big")
    out += b"\x00"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0, filename="") as f:  # no timestamp: same bytes every run
        f.write(bytes(out))


test_structure(os.path.join(DATA, "structure", "test_platform.nbt"), (32, 24, 32))

# ---- summary
n_items = len([i for i in M.items.values() if not i.get("station")])
print(f"functions: {len(M.functions)} ({sum(1 for f in M.functions if f['type']=='theoretical')} theoretical)")
print(f"items: {n_items}, placeable structures: {len(M.stations)}, blocks: {len(M.blocks)}, ores: {len(M.ores)}")
modes = defaultdict(int)
for f in M.functions:
    modes[f["mode"]] += 1
print("modes:", dict(modes))
print("wrote", OUT)
