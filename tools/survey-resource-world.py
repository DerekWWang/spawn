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


def survey(world):
    level=nbt(gzip.decompress((world/'level.dat').read_bytes())).get('Data',{})
    blocks=collections.Counter(); chunks=0; plants=0; unsupported=0; ore_bounds={}; biomes=collections.Counter()
    for region in world.rglob('region/r.*.mca'):
        if 'entities' in str(region): continue
        for chunk in region_chunks(region):
            if chunk.get('Status','').split(':')[-1]!='full': continue
            chunks+=1
            sections={section['Y']:section for section in chunk.get('sections',[])}
            for section in sections.values():
                biomes.update(str(p) for p in section.get('biomes',{}).get('palette',[]))
                states=section.get('block_states',{}); palette=states.get('palette',[])
                if not palette: continue
                names=[p if isinstance(p,str) else p.get('Name',p.get('id',p.get('','?'))) for p in palette]
                if len(palette)==1: blocks[names[0]]+=4096; continue
                bits=max(4,(len(palette)-1).bit_length()); per=64//bits; mask=(1<<bits)-1; words=states.get('data',[])
                for i in range(4096):
                    index=((words[i//per]&((1<<64)-1))>>((i%per)*bits))&mask
                    name=names[index];blocks[name]+=1
                    if name.startswith('spawn_9b5fbb9:wild_'):
                        plants+=1
                        below=i-256
                        if below>=0: support=names[(words[below//per]>>((below%per)*bits))&mask]
                        else:
                            b=sections.get(section['Y']-1,{}).get('block_states',{});bp=b.get('palette',[]);bn=[v if isinstance(v,str) else v.get('Name',v.get('', '?')) for v in bp];bb=max(4,(len(bn)-1).bit_length());be=64//bb;bi=i+3840
                            support=bn[0] if len(bn)==1 else bn[(b['data'][bi//be]>>((bi%be)*bb))&((1<<bb)-1)] if bn else '?'
                        if support not in ['minecraft:grass_block','minecraft:dirt','minecraft:coarse_dirt','minecraft:podzol','minecraft:rooted_dirt','minecraft:mud','minecraft:farmland','minecraft:moss_block']: unsupported+=1
                    if name.endswith('_ore') and ('spawn_' in name or 'coal' in name):
                        point=[chunk['xPos']*16+(i&15),section['Y']*16+(i>>8),chunk['zPos']*16+((i>>4)&15)]
                        low,high=ore_bounds.setdefault(name,[point.copy(),point.copy()])
                        for axis in range(3): low[axis]=min(low[axis],point[axis]);high[axis]=max(high[axis],point[axis])
    interesting={k:v for k,v in sorted(blocks.items()) if k in ['minecraft:granite','minecraft:sandstone','minecraft:red_sandstone','minecraft:clay'] or k.startswith('spawn_9b5fbb9:') or any(t in k for t in ['copper_ore','iron_ore','coal_ore','raw_copper_block','raw_iron_block'])}
    return {'world':str(world),'seedVerifiedViaRcon':int(world.parents[1].name) if world.parents[1].name.isdigit() else None, 'fullChunks':chunks,'resourceBlocks':interesting,'plantBlocks':plants,'unsupportedPlantBlocks':unsupported,'plantCoverageFraction':plants/(chunks*256),'oreBounds':ore_bounds,'biomePaletteOccurrences':dict(biomes.most_common(10))}

if __name__=='__main__':
    print(json.dumps([survey(pathlib.Path(p)) for p in sys.argv[1:]],indent=2))
