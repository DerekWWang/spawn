"""Reproducible data and block-built models for industry, transport and warfare.

Writes loot tables, tags, damage types, station recipes, the three-wide railway track model and
the vehicle models that VehicleRenderer draws at 1.5x. Icons are not produced here: 16x16 item and
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
    if count>1:entry['functions']=[{'function':'minecraft:set_count','count':count}]
    return {'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry],'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
for block in WORKS+['computer','server']:write(DATA/NS/'loot_table/blocks'/f'{block}.json',drops(block))
write(DATA/NS/'loot_table/blocks/galena_ore.json',drops('galena_ore','galena'))
write(DATA/NS/'loot_table/blocks/pyrite_ore.json',drops('pyrite_ore','pyrite'))
centre=[{'condition':'minecraft:block_state_property','block':f'{NS}:rail_track','properties':{'part':'center'}}]
write(DATA/NS/'loot_table/blocks/rail_track.json',{'type':'minecraft:block','pools':[
    {'rolls':1,'conditions':centre,'entries':[{'type':'minecraft:item','name':f'{NS}:steel_rail','functions':[{'function':'minecraft:set_count','count':2}]}]},
    {'rolls':1,'conditions':centre,'entries':[{'type':'minecraft:item','name':f'{NS}:sleeper','functions':[{'function':'minecraft:set_count','count':2}]}]}]})
merge_tag(DATA/'minecraft/tags/block/mineable/pickaxe.json',[f'{NS}:{b}' for b in ['galena_ore','pyrite_ore','foundry','chemical_works','machine_shop','electronics_works','computer','server','rail_track']])
merge_tag(DATA/'minecraft/tags/block/mineable/axe.json',[f'{NS}:{b}' for b in ['workshop','assembly_yard','niter_bed','powder_mill']])
merge_tag(DATA/'minecraft/tags/block/needs_stone_tool.json',[f'{NS}:galena_ore',f'{NS}:pyrite_ore'])
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

# ---- Vehicles ---------------------------------------------------------------------------------------
# Authoring space is blocks about the vehicle's centre: x right, y up from the ground, z forward.
# VehicleRenderer scales the model 1.5x, so one block is 16/1.5 model units; models face north (-Z).
U=16/1.5
T={'plank':'minecraft:block/oak_planks','dark':'minecraft:block/spruce_planks','log':'minecraft:block/stripped_oak_log','iron':'minecraft:block/iron_block',
   'black':'minecraft:block/blackstone','bronze':'minecraft:block/copper_block','glass':'minecraft:block/glass','stone':'minecraft:block/cobblestone',
   'steel':'minecraft:block/smooth_stone','green':'minecraft:block/green_terracotta','red':'minecraft:block/red_terracotta','rope':'minecraft:block/hay_block_side',
   'leather':'minecraft:block/brown_terracotta','coal':'minecraft:block/coal_block','particle':'minecraft:block/oak_planks'}
def box(x1,y1,z1,x2,y2,z2,texture,tilt=None):
    """tilt=(angle, pivot_y, pivot_z) rotates about the cross axis (x) in 22.5 degree steps."""
    a=(8+min(x1,x2)*U,min(y1,y2)*U,8-max(z1,z2)*U);b=(8+max(x1,x2)*U,max(y1,y2)*U,8-min(z1,z2)*U)
    for value in a+b:assert -16<=value<=32,(x1,y1,z1,x2,y2,z2,value)
    rotation={'angle':tilt[0],'axis':'x','origin':[8,round(tilt[1]*U,3),round(8-tilt[2]*U,3)]} if tilt else None
    return cube(a,b,texture,rotation)
def wheel(x,z,radius,texture='dark',width=.1):
    """A spoked wheel read as a square rim and the same rim turned 45 degrees."""
    out=[]
    for angle in (0,45):
        tilt=(angle,radius,z) if angle else None
        out.append(box(x-width/2,radius*.3,z-radius*.7,x+width/2,radius*1.7,z+radius*.7,texture,tilt))
    out.append(box(x-width/2-.03,radius-.08,z-.08,x+width/2+.03,radius+.08,z+.08,'iron'))
    return out
def wheels(xs,zs,radius,texture='dark'):return [part for x in xs for z in zs for part in wheel(x,z,radius,texture)]
MODELS={}
MODELS['cart']=wheels((-.74,.74),(0,),.5)+[
    box(-.74,.44,-.05,.74,.56,.05,'log'),box(-.6,.56,-.9,.6,.66,.9,'plank'),
    box(-.66,.66,-.9,-.6,1.02,.9,'plank'),box(.6,.66,-.9,.66,1.02,.9,'plank'),box(-.6,.66,-.96,.6,1.02,-.9,'plank'),box(-.6,.66,.9,.6,1.02,.96,'plank'),
    box(-.5,.5,.9,-.42,.58,1.95,'log'),box(.42,.5,.9,.5,.58,1.95,'log'),box(-.5,.66,.55,.5,.72,.85,'dark')]
MODELS['wagon']=wheels((-.92,.92),(-1.0,1.0),.55)+[
    box(-.92,.49,-1.05,.92,.61,-.95,'log'),box(-.92,.49,.95,.92,.61,1.05,'log'),box(-.8,.66,-1.5,.8,.78,1.5,'plank'),
    box(-.86,.78,-1.5,-.8,1.3,1.5,'plank'),box(.8,.78,-1.5,.86,1.3,1.5,'plank'),box(-.8,.78,-1.56,.8,1.3,-1.5,'plank'),box(-.8,.78,1.5,.8,1.3,1.56,'plank'),
    box(-.75,1.3,1.0,.75,1.4,1.45,'dark'),box(-.06,.5,1.5,.06,.6,2.2,'log')]
MODELS['chariot']=wheels((-.68,.68),(-.1,),.5)+[
    box(-.68,.44,-.15,.68,.56,-.05,'log'),box(-.5,.5,-.5,.5,.57,.3,'plank'),
    box(-.5,.57,.26,.5,1.05,.31,'leather'),box(-.5,.57,-.3,-.45,.95,.3,'leather'),box(.45,.57,-.3,.5,.95,.3,'leather'),
    box(-.05,.46,.3,.05,.54,1.9,'log'),box(-.6,.5,1.8,.6,.57,1.88,'log')]
MODELS['motor_truck']=wheels((-.98,.98),(-1.15,1.15),.45,'black')+[
    box(-.9,.5,-1.75,.9,.66,1.75,'black'),box(-.95,.66,-1.75,.95,.76,.45,'plank'),
    box(-.98,.76,-1.75,-.92,1.3,.45,'plank'),box(.92,.76,-1.75,.98,1.3,.45,'plank'),box(-.92,.76,-1.8,.92,1.3,-1.75,'plank'),
    box(-.9,.66,.5,.9,1.9,1.2,'green'),box(-.8,1.25,1.2,.8,1.8,1.24,'glass'),box(-.7,.66,1.2,.7,1.22,1.75,'green'),box(-.55,.72,1.75,.55,1.15,1.79,'iron'),
    box(-.94,1.9,.45,.94,1.96,1.25,'black')]
MODELS['locomotive']=wheels((-.86,.86),(-.95,.05,1.0),.55,'red')+[
    box(-.9,.5,-1.65,.9,.72,1.65,'black'),box(-.8,.15,1.65,.8,.5,1.78,'red'),
    box(-.6,.72,-.5,.6,1.9,1.62,'black'),box(-.5,.82,1.62,.5,1.8,1.68,'iron'),box(-.66,1.2,-.5,.66,1.42,1.5,'bronze'),
    box(-.2,1.9,1.05,.2,2.6,1.45,'black'),box(-.27,2.5,.98,.27,2.66,1.52,'iron'),box(-.25,1.9,.15,.25,2.22,.6,'bronze'),
    box(-.98,.72,-1.65,.98,2.46,-.5,'green'),box(-1.06,2.46,-1.75,1.06,2.6,-.38,'black'),
    box(-.99,1.55,-1.35,-.97,2.2,-.8,'glass'),box(.97,1.55,-1.35,.99,2.2,-.8,'glass'),box(-.7,1.6,-.5,.7,2.2,-.47,'glass'),
    box(-.92,.9,-.3,-.82,1.02,1.2,'iron'),box(.82,.9,-.3,.92,1.02,1.2,'iron')]
MODELS['rail_wagon']=wheels((-.86,.86),(-1.0,1.0),.42,'black')+[
    box(-.95,.45,-1.55,.95,.66,1.55,'black'),box(-.98,.66,-1.5,.98,.76,1.5,'dark'),
    box(-1.02,.76,-1.5,-.95,1.7,1.5,'dark'),box(.95,.76,-1.5,1.02,1.7,1.5,'dark'),box(-.95,.76,-1.54,.95,1.7,-1.47,'dark'),box(-.95,.76,1.47,.95,1.7,1.54,'dark'),
    box(-1.04,1.15,-1.5,-1.02,1.25,1.5,'iron'),box(1.02,1.15,-1.5,1.04,1.25,1.5,'iron'),
    box(-.5,.5,1.55,-.3,.62,1.68,'iron'),box(.3,.5,1.55,.5,.62,1.68,'iron'),box(-.5,.5,-1.68,-.3,.62,-1.55,'iron'),box(.3,.5,-1.68,.5,.62,-1.55,'iron')]
MODELS['rail_carriage']=wheels((-.86,.86),(-1.0,1.0),.42,'black')+[
    box(-.95,.45,-1.55,.95,.66,1.55,'black'),box(-1.0,.66,-1.5,1.0,1.4,1.5,'green'),box(-1.0,1.95,-1.5,1.0,2.3,1.5,'green'),
    box(-.98,1.4,-1.5,.98,1.95,1.5,'glass'),
    box(-1.0,1.4,-1.5,-.94,1.95,-1.3,'green'),box(-1.0,1.4,-.1,-.94,1.95,.1,'green'),box(-1.0,1.4,1.3,-.94,1.95,1.5,'green'),
    box(.94,1.4,-1.5,1.0,1.95,-1.3,'green'),box(.94,1.4,-.1,1.0,1.95,.1,'green'),box(.94,1.4,1.3,1.0,1.95,1.5,'green'),
    box(-1.06,2.3,-1.58,1.06,2.45,1.58,'steel'),
    box(-.5,.5,1.55,-.3,.62,1.68,'iron'),box(.3,.5,1.55,.5,.62,1.68,'iron'),box(-.5,.5,-1.68,-.3,.62,-1.55,'iron'),box(.3,.5,-1.68,.5,.62,-1.55,'iron')]
MODELS['cannon']=wheels((-.72,.72),(.2,),.55)+[
    box(-.72,.49,.15,.72,.61,.25,'log'),box(-.3,.55,-.1,-.2,.95,.5,'plank'),box(.2,.55,-.1,.3,.95,.5,'plank'),
    box(-.14,.3,-1.2,.14,.5,.2,'plank',(-22.5,.5,.2)),
    box(-.17,.78,-.55,.17,1.12,1.2,'bronze'),box(-.2,.75,-.6,.2,1.15,-.45,'bronze'),box(-.2,.75,1.1,.2,1.15,1.2,'bronze'),box(-.08,.87,-.72,.08,1.03,-.6,'bronze'),
    box(-.3,.9,.15,.3,1.0,.25,'bronze')]
MODELS['bombard']=[
    box(-.7,.0,-1.45,-.45,.3,1.45,'log'),box(.45,.0,-1.45,.7,.3,1.45,'log'),box(-.7,.3,-1.2,.7,.45,-.9,'log'),box(-.7,.3,-.15,.7,.45,.15,'log'),box(-.7,.3,.9,.7,.45,1.2,'log'),
    box(-.42,.45,-.9,.42,1.3,1.45,'black'),box(-.3,.55,-1.25,.3,1.2,-.9,'black'),
    box(-.46,.41,-.6,.46,1.34,-.48,'iron'),box(-.46,.41,.1,.46,1.34,.22,'iron'),box(-.46,.41,.8,.46,1.34,.92,'iron'),box(-.46,.41,1.36,.46,1.34,1.46,'iron'),
    box(-.7,.3,-1.45,.7,1.1,-1.3,'log')]
MODELS['trebuchet']=[
    box(-1.2,0,-1.45,-1.0,.2,1.45,'log'),box(1.0,0,-1.45,1.2,.2,1.45,'log'),box(-1.2,0,-1.45,1.2,.2,-1.25,'log'),box(-1.2,0,1.25,1.2,.2,1.45,'log'),box(-1.2,0,-.1,1.2,.2,.1,'log'),
    box(-1.18,.2,-.1,-1.02,2.3,.1,'log'),box(1.02,.2,-.1,1.18,2.3,.1,'log'),
    box(-1.16,.2,-.08,-1.04,1.9,.08,'log',(22.5,.2,0)),box(-1.16,.2,-.08,-1.04,1.9,.08,'log',(-22.5,.2,0)),
    box(1.04,.2,-.08,1.16,1.9,.08,'log',(22.5,.2,0)),box(1.04,.2,-.08,1.16,1.9,.08,'log',(-22.5,.2,0)),
    box(-1.2,2.2,-.08,1.2,2.36,.08,'iron'),
    # The arm is shown at rest: counterweight box hanging low, long throwing end raised behind.
    box(-.09,2.2,-1.75,.09,2.36,.75,'log',(-22.5,2.28,0)),box(-.4,1.75,.45,.4,2.25,1.05,'plank',(-22.5,2.28,0)),box(-.42,1.9,.43,.42,1.98,1.07,'iron',(-22.5,2.28,0)),
    box(-.25,.2,-1.3,.25,.26,.9,'plank')]
MODELS['field_gun']=wheels((-.78,.78),(.2,),.5,'black')+[
    box(-.78,.44,.15,.78,.56,.25,'iron'),box(-.34,.3,-1.25,-.22,.44,.2,'steel',(-22.5,.44,.2)),box(.22,.3,-1.25,.34,.44,.2,'steel',(-22.5,.44,.2)),
    box(-.7,.5,.34,-.14,1.4,.4,'steel'),box(.14,.5,.34,.7,1.4,.4,'steel'),box(-.14,1.1,.34,.14,1.4,.4,'steel'),
    box(-.1,.82,-.5,.1,1.02,1.9,'steel'),box(-.14,.78,-.6,.14,1.06,.0,'iron'),box(-.12,.6,-.1,.12,.8,.6,'iron')]
DISPLAY={'gui':{'rotation':[30,225,0],'translation':[0,-1.5,0],'scale':[.34,.34,.34]},'ground':{'scale':[.3,.3,.3]},'fixed':{'scale':[.3,.3,.3]},
    'thirdperson_righthand':{'translation':[0,2,0],'scale':[.25,.25,.25]},'thirdperson_lefthand':{'translation':[0,2,0],'scale':[.25,.25,.25]},
    'firstperson_righthand':{'rotation':[0,45,0],'scale':[.25,.25,.25]},'firstperson_lefthand':{'rotation':[0,225,0],'scale':[.25,.25,.25]},'head':{'scale':[.6,.6,.6]}}
for kind,elements in MODELS.items():
    used={part['texture'][1:] for element in elements for part in element['faces'].values()}|{'particle'}
    write(ASSETS/'models/item'/f'{kind}.json',{'textures':{key:T[key] for key in sorted(used)},'elements':elements,'display':DISPLAY})
    write(ASSETS/'items'/f'{kind}.json',{'model':{'type':'minecraft:model','model':f'{NS}:item/{kind}'}})
print('data and models written:',len(MODELS),'vehicles')
