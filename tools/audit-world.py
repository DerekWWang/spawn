"""Read-only saved-world audit. Run after save-all flush or a clean server stop."""
import collections
import gzip
import io
import json
import pathlib
import struct
import sys
import zlib


def nbt(data):
    stream = io.BytesIO(data)

    def number(fmt):
        return struct.unpack(">" + fmt, stream.read(struct.calcsize(fmt)))[0]

    def string():
        return stream.read(number("H")).decode("utf-8", errors="replace")

    def payload(kind):
        if kind in range(1, 7):
            return number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return stream.read(number("i"))
        if kind == 8:
            return string()
        if kind == 9:
            item_kind, length = number("B"), number("i")
            return [payload(item_kind) for _ in range(length)]
        if kind == 10:
            result = {}
            while (item_kind := number("B")):
                key = string()
                result[key] = payload(item_kind)
            return result
        if kind in (11, 12):
            fmt = "i" if kind == 11 else "q"
            return [number(fmt) for _ in range(number("i"))]
        raise ValueError(f"Unknown NBT type {kind}")

    root_kind = number("B")
    string()
    return payload(root_kind)


def region_chunks(path):
    data = path.read_bytes()
    for index in range(1024):
        entry = int.from_bytes(data[index * 4:index * 4 + 4], "big")
        offset = (entry >> 8) * 4096
        if not offset:
            continue
        length = int.from_bytes(data[offset:offset + 4], "big")
        compression = data[offset + 4]
        body = data[offset + 5:offset + 4 + length]
        if compression == 1:
            body = gzip.decompress(body)
        elif compression == 2:
            body = zlib.decompress(body)
        elif compression != 3:
            raise ValueError(f"Unsupported compression {compression} in {path}")
        yield nbt(body)


world = pathlib.Path(sys.argv[1])
level = nbt(gzip.decompress((world / "level.dat").read_bytes())).get("Data", {})
palettes = collections.Counter()
biomes = collections.Counter()
statuses = collections.Counter()
starts, references, chunks = [], [], 0
region_dir = world / "dimensions/minecraft/overworld/region"
if not region_dir.exists():
    region_dir = world / "region"
for region in sorted(region_dir.glob("*.mca")):
    for chunk in region_chunks(region):
        chunks += 1
        statuses.update([chunk.get("Status", "unknown")])
        location = [chunk.get("xPos"), chunk.get("zPos")]
        structures = chunk.get("structures", {})
        if structures.get("starts"):
            starts.append({"chunk": location, "ids": list(structures["starts"])})
        if any(structures.get("References", {}).values()):
            references.append(location)
        for section in chunk.get("sections", []):
            palettes.update(p if isinstance(p, str) else p.get("Name", p.get("id", p.get("", "unknown")))
                            for p in section.get("block_states", {}).get("palette", []))
            biomes.update(section.get("biomes", {}).get("palette", []))
resources = {key: value for key, value in sorted(palettes.items()) if
             key.startswith("spawn_9b5fbb9:") or any(s in key for s in
             ("_ore", "clay", "_log", "obsidian", "water", "stone"))}
properties = world.parent / "server.properties"
settings = [line for line in properties.read_text().splitlines()
            if line.startswith(("generate-structures=", "level-type="))] if properties.exists() else []
report = {"world": str(world), "chunks": chunks, "chunk_statuses": dict(statuses),
          "generation_settings": settings,
          "structure_starts": starts, "structure_reference_chunks": references,
          "resource_palette_occurrences": resources, "biomes": dict(biomes)}
print(json.dumps(report, indent=2))
if chunks == 0 or starts or references:
    sys.exit(1)
