"""Generate only the empty GameTest boundary metadata (no built blocks or textures)."""
from pathlib import Path
import gzip
import struct

def string(value):
    data=value.encode('utf-8')
    return struct.pack('>H',len(data))+data

def tag(kind,name,payload):
    return bytes([kind])+string(name)+payload

body=tag(9,'size',bytes([3])+struct.pack('>iiii',3,16,16,16))
body+=tag(9,'palette',bytes([10])+struct.pack('>i',1)+tag(8,'Name',string('minecraft:air'))+b'\0')
body+=tag(9,'blocks',bytes([10])+struct.pack('>i',0))
body+=tag(9,'entities',bytes([10])+struct.pack('>i',0))
data=tag(10,'',body+b'\0')
target=Path(__file__).resolve().parents[1]/'src/main/resources/data/spawn_9b5fbb9/structure/test_arena.nbt'
target.parent.mkdir(parents=True,exist_ok=True)
target.write_bytes(gzip.compress(data,mtime=0))
print(target)
