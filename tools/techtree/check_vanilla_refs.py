#!/usr/bin/env python3
"""Check that every vanilla id the generated data refers to exists in this Minecraft version.

Minecraft renames things between versions (26.x turned the chain block into iron_chain, for
example), and a missing reference only shows up as a log error when the game loads tags. Run after
generate_techtree.py and after a Gradle build (which produces the sources jar this reads):

    python3 tools/techtree/check_vanilla_refs.py

Exits non-zero if anything is missing.
"""
import glob
import json
import os
import re
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
GEN = os.path.join(ROOT, "src", "techtree", "generated", "data")


def find_sources_jar():
    jars = sorted(glob.glob(os.path.join(ROOT, "build", "moddev", "artifacts", "minecraft-patched-*-sources.jar")))
    if not jars:
        sys.exit("No Minecraft sources jar under build/moddev/artifacts; run ./gradlew compileJava first.")
    return jars[-1]


def neoforge_tags():
    """NeoForge's c: tags, from its sources jar in the Gradle cache."""
    found = set()
    for jar in glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/*/*/neoforge-*-sources.jar")):
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                m = re.match(r"data/c/tags/(.+)/(.+)\.json$", n)
                if m:
                    found.add((m.group(1), "c:" + m.group(2)))
    return found


def main():
    with zipfile.ZipFile(find_sources_jar()) as z:
        names = z.namelist()
        blocks = {n.split("/")[-1][:-5] for n in names if n.startswith("assets/minecraft/blockstates/")}
        items = {n.split("/")[-1][:-5] for n in names if n.startswith("assets/minecraft/items/")}
        lang = json.loads(z.read("assets/minecraft/lang/en_us.json"))
        vanilla_tags = set()
        for n in names:
            m = re.match(r"data/minecraft/tags/(.+)/(.+)\.json$", n)
            if m:
                vanilla_tags.add((m.group(1), "minecraft:" + m.group(2)))
    entities = {k.split(".")[2] for k in lang if k.startswith("entity.minecraft.") and k.count(".") == 2}
    biomes = {k.split(".")[2] for k in lang if k.startswith("biome.minecraft.") and k.count(".") == 2}
    effects = {k.split(".")[2] for k in lang if k.startswith("effect.minecraft.") and k.count(".") == 2}
    tags = vanilla_tags | neoforge_tags()
    pools = {"block": blocks, "item": items, "entity_type": entities}
    problems = []

    def check_tag(kind, ref, where):
        ns = ref.split(":")[0]
        if ns in ("minecraft", "c") and (kind, ref) not in tags:
            problems.append(f"{where}: missing tag #{ref} ({kind})")

    for path in glob.glob(os.path.join(GEN, "*", "tags", "**", "*.json"), recursive=True):
        kind = path.split(os.sep + "tags" + os.sep)[1].split(os.sep)[0]
        for v in json.load(open(path))["values"]:
            if v.startswith("#"):
                check_tag(kind, v[1:], path)
            elif v.startswith("minecraft:") and kind in pools and v.split(":")[1] not in pools[kind]:
                problems.append(f"{path}: missing {kind} {v}")
    for path in glob.glob(os.path.join(GEN, "*", "recipe", "*.json")) + glob.glob(os.path.join(GEN, "*", "loot_table", "**", "*.json"), recursive=True):
        text = open(path).read()
        for ref in re.findall(r'"(?:name|id|#|X|block)":\s*"(minecraft:[a-z0-9_]+)"', text):
            if ref.split(":")[1] not in items and ref.split(":")[1] not in blocks:
                problems.append(f"{path}: unknown {ref}")
    for path in glob.glob(os.path.join(GEN, "*", "worldgen", "feature", "*.json")):
        for t in json.load(open(path))["targets"]:
            tgt = t["target"]
            if tgt.get("predicate_type") == "minecraft:block_match" and tgt["block"].split(":")[1] not in blocks:
                problems.append(f"{path}: unknown block {tgt['block']}")
            if tgt.get("predicate_type") == "minecraft:tag_match":
                check_tag("block", tgt["tag"], path)
    for path in glob.glob(os.path.join(GEN, "*", "neoforge", "biome_modifier", "*.json")):
        b = json.load(open(path))["biomes"]
        for x in b if isinstance(b, list) else [b]:
            if x.startswith("#"):
                check_tag("worldgen/biome", x[1:], path)
            elif x.split(":")[1] not in biomes:
                problems.append(f"{path}: unknown biome {x}")
    tree = json.load(open(os.path.join(GEN, "spawn_9b5fbb9", "techtree", "techtree.json")))
    for it in tree["items"]:
        med = it.get("medicine")
        if med and med["effect"].split(":")[1] not in effects:
            problems.append(f"techtree.json: {it['id']} uses unknown effect {med['effect']}")
    for p in problems:
        print("MISSING:", p)
    print(f"checked against {len(blocks)} blocks, {len(items)} items, {len(entities)} entity types, {len(biomes)} biomes")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
