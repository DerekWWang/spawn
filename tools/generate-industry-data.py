"""Reproducible data and block-built models for industry, transport and warfare.

Writes loot tables, tags, damage types, station recipes, and the three-wide railway track model.
Vehicle models come from tools/generate-vehicle-models.py. Icons are not produced here: 16x16 item and
block textures go through `qube texture` (see tools/render-industry-icons.py).
"""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
NS='spawn_9b5fbb9'
DATA=ROOT/'src/main/resources/data'
ASSETS=ROOT/'src/main/resources/assets'/NS

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

def merge_tag(path,values):
    current=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace':False,'values':[]}
    for value in values:
        if value not in current['values']:current['values'].append(value)
    write(path,current)

# ---- Data -------------------------------------------------------------------------------------------
WORKS=['workshop','assembly_yard','niter_bed','powder_mill','foundry','chemical_works','machine_shop','electronics_works']
def drops(block,item=None,count=1):
    entry={'type':'minecraft:item','name':f'{NS}:{item or block}'}
    if ':' in (item or ''):entry['name']=item
    # Minecraft 26 loot: an entry's item functions are its "modifier", a pool's or entry's predicate its "condition".
    if count>1:entry['modifier']=[{'type':'minecraft:set_count','count':count}]
    return {'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry],'condition':{'type':'minecraft:survives_explosion'}}]}
PLANT=['firebrick','chimney','steam_boiler','stationary_engine','lead_chamber','generator','power_cable','arc_furnace','electrolytic_cell','vacuum_pump',
       'cleanroom_panel','filter_unit','water_purifier','crystal_puller','diffusion_furnace','lithography_stepper','wire_bonder','distillation_column','power_station']
for block in WORKS+PLANT+['computer','server']:write(DATA/NS/'loot_table/blocks'/f'{block}.json',drops(block))
# What a cubic metre of ground holds, in kilograms of hand-sorted mineral (one item is one kilogram). Lode rock is mostly
# gangue: a metre of good haematite seam is a tonne and a half of ore, a metre of tin lode sixty kilograms of black tin.
# Coal comes in 25 kg sacks; clay in brick-sized 3.75 kg lumps.
YIELD={'hematite_ore':('hematite',1600),'malachite_ore':('malachite',420),'cassiterite_ore':('cassiterite',60),'galena_ore':('galena',250),
       'pyrite_ore':('pyrite',300),'rock_salt':('salt',2100),'fluorite_ore':('fluorite',600),'bauxite_ore':('bauxite',2400),
       'wolframite_ore':('wolframite',30),'tin_ore':('cassiterite',60),'tar_sand':('oil_sand',2000),'borax_ore':('borax',1500)}
for block,(item,count) in YIELD.items():write(DATA/NS/'loot_table/blocks'/f'{block}.json',drops(block,item,count))
for block in ['coal_ore','deepslate_coal_ore']:write(DATA/'minecraft/loot_table/blocks'/f'{block}.json',drops(block,'minecraft:coal',52))
write(DATA/'minecraft/loot_table/blocks/clay.json',drops('clay','minecraft:clay_ball',480))
# Four lumps no longer press back into a cubic metre of clay.
write(DATA/'minecraft/recipe/clay.json',{'type':'minecraft:crafting_shapeless','ingredients':['minecraft:bedrock'],'result':{'id':'minecraft:stick'}})
centre={'type':'minecraft:match_block','blocks':f'{NS}:rail_track','state':{'part':'center'}}
write(DATA/NS/'loot_table/blocks/rail_track.json',{'type':'minecraft:block','pools':[
    {'rolls':1,'condition':centre,'entries':[{'type':'minecraft:item','name':f'{NS}:steel_rail','modifier':[{'type':'minecraft:set_count','count':2}]}]},
    {'rolls':1,'condition':centre,'entries':[{'type':'minecraft:item','name':f'{NS}:sleeper','modifier':[{'type':'minecraft:set_count','count':2}]}]}]})
merge_tag(DATA/'minecraft/tags/block/mineable/pickaxe.json',[f'{NS}:{b}' for b in ['galena_ore','pyrite_ore','rock_salt','fluorite_ore','bauxite_ore','wolframite_ore','borax_ore','foundry','chemical_works','machine_shop','electronics_works','computer','server','rail_track']+PLANT])
merge_tag(DATA/'minecraft/tags/block/mineable/axe.json',[f'{NS}:{b}' for b in ['workshop','assembly_yard','niter_bed','powder_mill']])
merge_tag(DATA/'minecraft/tags/block/needs_stone_tool.json',[f'{NS}:{b}' for b in ['galena_ore','pyrite_ore','rock_salt','fluorite_ore','bauxite_ore','wolframite_ore','borax_ore']])
merge_tag(DATA/'minecraft/tags/block/mineable/shovel.json',[f'{NS}:tar_sand'])
# Musket balls and shot defeat a raised shield; plate still blunts them through the ordinary armour formula.
write(DATA/NS/'damage_type/gunshot.json',{'exhaustion':0.1,'message_id':'gunshot','scaling':'never'})
write(DATA/NS/'damage_type/artillery.json',{'exhaustion':0.1,'message_id':'artillery','scaling':'never'})
merge_tag(DATA/'minecraft/tags/damage_type/bypasses_shield.json',[f'{NS}:gunshot',f'{NS}:artillery'])
def shapeless(result,ingredients):return {'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ingredients,'result':{'id':f'{NS}:{result}','count':1}}
write(DATA/NS/'recipe/workshop.json',shapeless('workshop',['#minecraft:planks']*4+['#minecraft:logs']*2+[f'{NS}:cordage']*2))
write(DATA/NS/'recipe/assembly_yard.json',shapeless('assembly_yard',['#minecraft:logs']*4+['#minecraft:planks']*2+[f'{NS}:cordage']*3))
write(DATA/NS/'recipe/niter_bed.json',shapeless('niter_bed',['#minecraft:planks']*4+['minecraft:clay_ball']*2+['minecraft:dirt']*2))

# ---- Railway track ----------------------------------------------------------------------------------
def cube(a,b,texture,rotation=None):
    faces={}
    size=[abs(b[i]-a[i]) for i in range(3)]
    for face,(u,v) in {'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}.items():
        faces[face]={'uv':[0,0,round(max(1,min(16,size[u])),2),round(max(1,min(16,size[v])),2)],'texture':'#'+texture}
    element={'from':[round(x,3) for x in a],'to':[round(x,3) for x in b],'faces':faces}
    if rotation:element['rotation']=rotation
    return element
TRACK={'gravel':'minecraft:block/gravel','tie':'minecraft:block/dark_oak_planks','rail':'minecraft:block/iron_block','particle':'minecraft:block/gravel'}
ties=[(1.5,4.5),(6.5,9.5),(11.5,14.5)]
write(ASSETS/'models/block/rail_track_center.json',{'ambientocclusion':False,'textures':TRACK,'elements':
    [cube((0,0,0),(16,1,16),'gravel')]+[cube((a,1,0),(b,2,16),'tie') for a,b in ties]})
# A shoulder as seen on the +Z side of a line running along X: rail and sleeper ends lie towards low Z.
write(ASSETS/'models/block/rail_track_side.json',{'ambientocclusion':False,'textures':TRACK,'elements':
    [cube((0,0,0),(16,1,11),'gravel')]+[cube((a,1,0),(b,2,7.5),'tie') for a,b in ties]+[cube((0,2,2),(16,2.6,5),'rail'),cube((0,2.6,2.8),(16,4,4.2),'rail')]})
write(ASSETS/'blockstates/rail_track.json',{'variants':{
    'axis=x,part=center':{'model':f'{NS}:block/rail_track_center'},'axis=z,part=center':{'model':f'{NS}:block/rail_track_center','y':90},
    'axis=x,part=pos':{'model':f'{NS}:block/rail_track_side'},'axis=x,part=neg':{'model':f'{NS}:block/rail_track_side','y':180},
    'axis=z,part=pos':{'model':f'{NS}:block/rail_track_side','y':270},'axis=z,part=neg':{'model':f'{NS}:block/rail_track_side','y':90}}})

print('data and track models written')
