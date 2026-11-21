"""Block-built vehicle models at true scale, with separately rendered rolling wheels.

One block is one metre: track gauge 1.44, a tank engine 6.4 long and 3.6 tall, cart wheels 1.3 across.
Vehicles are authored in blocks about their centre (x right, y up from the ground or rail head, z
forward) and written as item models that VehicleRenderer draws at 1.5x. A model volume spans 4.5
blocks, so long vehicles are sliced along their length into several models. Wheels and coupling rods
are their own models: the renderer turns them with the distance travelled.

Writes assets/.../models/item/vehicle/*.json, items/vehicle/*.json, an inventory icon model per kind,
and client/VehicleLayouts.java (where each part, wheel and rod goes).
"""
from pathlib import Path
import json, math

ROOT=Path(__file__).resolve().parents[1]
NS='spawn_9b5fbb9'
ASSETS=ROOT/'src/main/resources/assets'/NS
JAVA=ROOT/'src/main/java/dev/qube/spawn_9b5fbb9/client/VehicleLayouts.java'
SCALE=1.5
U=16/SCALE
T={'wood':'oak_planks','dark':'spruce_planks','beam':'stripped_oak_log','iron':'iron_block','black':'black_concrete','boiler':'polished_blackstone',
   'steel':'smooth_stone','bronze':'copper_block','brass':'yellow_terracotta','red':'red_concrete','green':'green_terracotta','glass':'glass',
   'coal':'coal_block','leather':'brown_terracotta','grey':'gray_concrete','stone':'cobblestone','cream':'white_terracotta','rope':'stripped_birch_log',
   'tyre':'black_terracotta','lamp':'ochre_froglight_side'}

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,separators=(',',':')),encoding='utf-8')

def faces(size,texture):
    out={}
    for face,(u,v) in {'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}.items():
        out[face]={'uv':[0,0,round(max(.5,min(16,size[u])),2),round(max(.5,min(16,size[v])),2)],'texture':'#'+texture}
    return out

class Shape:
    """A list of boxes in author space. rot=(axis,degrees,(ox,oy,oz)), degrees a multiple of 22.5 within +-45."""
    def __init__(s):s.boxes=[]
    def box(s,x1,y1,z1,x2,y2,z2,tex,rot=None):
        s.boxes.append((min(x1,x2),min(y1,y2),min(z1,z2),max(x1,x2),max(y1,y2),max(z1,z2),tex,rot));return s
    def sym(s,x1,y1,z1,x2,y2,z2,tex,rot=None):
        """The box and its mirror image across the centreline."""
        s.box(x1,y1,z1,x2,y2,z2,tex,rot)
        mirrored=None if rot is None else (rot[0],rot[1] if rot[0]=='x' else -rot[1],(-rot[2][0],rot[2][1],rot[2][2]))
        return s.box(-x1,y1,z1,-x2,y2,z2,tex,mirrored)
    def cyl_z(s,cx,cy,r,z1,z2,tex):
        a=r*math.tan(math.pi/8)
        for turn in (None,('z',45,(cx,cy,0))):
            s.box(cx-a,cy-r,z1,cx+a,cy+r,z2,tex,turn);s.box(cx-r,cy-a,z1,cx+r,cy+a,z2,tex,turn)
        return s
    def cyl_y(s,cx,cz,r,y1,y2,tex):
        a=r*math.tan(math.pi/8)
        for turn in (None,('y',45,(cx,0,cz))):
            s.box(cx-a,y1,cz-r,cx+a,y2,cz+r,tex,turn);s.box(cx-r,y1,cz-a,cx+r,y2,cz+a,tex,turn)
        return s
    def cyl_x(s,cy,cz,r,x1,x2,tex):
        a=r*math.tan(math.pi/8)
        for turn in (None,('x',45,(0,cy,cz))):
            s.box(x1,cy-a,cz-r,x2,cy+a,cz+r,tex,turn);s.box(x1,cy-r,cz-a,x2,cy+r,cz+a,tex,turn)
        return s

def element(box,centre,unit,ground):
    """Author box -> model element. Model faces north, so z is mirrored and x/y rotations change sense."""
    x1,y1,z1,x2,y2,z2,tex,rot=box
    a=[8+x1*unit,y1*unit+ground,8-(z2-centre)*unit];b=[8+x2*unit,y2*unit+ground,8-(z1-centre)*unit]
    for value in a+b:assert -16.001<=value<=32.001,(box,centre,value)
    e={'from':[round(v,3) for v in a],'to':[round(v,3) for v in b],'faces':faces([b[i]-a[i] for i in range(3)],tex)}
    if rot:
        axis,angle,(ox,oy,oz)=rot
        e['rotation']={'axis':axis,'angle':angle if axis=='z' else -angle,'origin':[round(8+ox*unit,3),round(oy*unit+ground,3),round(8-(oz-centre)*unit,3)]}
    return e

def model(elements,display=None):
    used=sorted({part['texture'][1:] for e in elements for part in e['faces'].values()})
    out={'textures':{**{key:f'minecraft:block/{T[key]}' for key in used},'particle':f'minecraft:block/{T[used[0]]}'},'elements':elements}
    if display:out['display']=display
    return out

def emit(name,elements):
    write(ASSETS/'models/item/vehicle'/f'{name}.json',model(elements))
    write(ASSETS/'items/vehicle'/f'{name}.json',{'model':{'type':'minecraft:model','model':f'{NS}:item/vehicle/{name}'}})

# ---- Wheels and rods: authored about their own axle, radius one block, turned by the renderer ----------
def wheel(rim,spoke,hub,spokes=4,width=.16,rim_depth=.16,flange=None):
    s=Shape();t=math.tan(math.pi/16)*1.12
    for base in ((0,1-rim_depth,-t,0,1,t),(0,-1,-t,0,-1+rim_depth,t),(0,-t,1-rim_depth,0,t,1),(0,-t,-1,0,t,-1+rim_depth)):
        for angle in (-45,-22.5,0,22.5):
            s.box(-width/2,base[1],base[2],width/2,base[4],base[5],rim,None if angle==0 else ('x',angle,(0,0,0)))
    angles={2:(0,),4:(0,45),8:(0,45,22.5,-22.5)}[spokes]
    for angle in angles:
        turn=None if angle==0 else ('x',angle,(0,0,0))
        s.box(-width*.3,-1+rim_depth,-.05,width*.3,1-rim_depth,.05,spoke,turn);s.box(-width*.3,-.05,-1+rim_depth,width*.3,.05,1-rim_depth,spoke,turn)
    s.cyl_x(0,0,.17,-width*.75,width*.75,hub)
    if flange:s.cyl_x(0,0,1.07,-width/2-.05,-width/2,flange)  # the flange runs inside the rail, on the inner face
    return s
def emit_centred(name,shape,unit=16):
    emit(name,[element(box,0,unit,8) for box in shape.boxes])
WHEELS={
    'wheel_spoked':wheel('iron','wood','dark',spokes=8,width=.14,rim_depth=.13),
    'wheel_gun':wheel('iron','dark','iron',spokes=8,width=.2,rim_depth=.16),
    'wheel_truck':wheel('tyre','grey','iron',spokes=4,width=.36,rim_depth=.3),
    'wheel_driver':wheel('steel','red','red',spokes=8,width=.2,rim_depth=.14,flange='steel'),
    'wheel_rail':wheel('steel','black','black',spokes=4,width=.22,rim_depth=.34,flange='steel'),
}
# A solid disc behind the spokes reads as a plate wheel on wagons.
WHEELS['wheel_rail'].cyl_x(0,0,.7,-.04,.04,'black')
RODS={'rod_coupling':Shape().box(-.05,-.07,-1.62,.05,.07,1.62,'steel').cyl_x(0,-1.5,.13,-.07,.07,'steel').cyl_x(0,0,.13,-.07,.07,'steel').cyl_x(0,1.5,.13,-.07,.07,'steel')}

# ---- Vehicles ----------------------------------------------------------------------------------------
class Vehicle(Shape):
    def __init__(s):
        super().__init__();s.wheels=[];s.rods=[]
    def wheel(s,name,x,z,radius):
        s.wheels+= [(name,x,z,radius),(name,-x,z,radius)];return s
    def rod(s,name,x,z,axle,crank):
        s.rods+=[(name,x,z,axle,crank,0.0),(name,-x,z,axle,crank,math.pi/2)];return s

def cart():
    v=Vehicle().wheel('wheel_spoked',.86,0,.65)
    v.cyl_x(.65,0,.08,-.9,.9,'beam')
    v.box(-.62,.82,-1.05,.62,.9,1.05,'wood')
    for z in (-.9,0,.9):v.box(-.62,.72,z-.06,.62,.82,z+.06,'beam')
    v.sym(.62,.9,-1.05,.68,1.38,1.05,'wood');v.box(-.62,.9,-1.1,.62,1.38,-1.05,'wood');v.box(-.62,.9,1.05,.62,1.2,1.1,'wood')
    for z in (-1.0,-.35,.35,1.0):v.sym(.68,.86,z-.04,.73,1.42,z+.04,'beam')
    v.sym(.44,.7,1.05,.52,.8,2.45,'beam');v.box(-.52,.7,1.02,.52,.8,1.1,'beam')
    v.box(-.5,1.2,.6,.5,1.26,1.0,'dark')
    return v
def wagon():
    v=Vehicle().wheel('wheel_spoked',.92,1.35,.5).wheel('wheel_spoked',.92,-1.35,.66)
    v.cyl_x(.5,1.35,.08,-.96,.96,'beam');v.cyl_x(.66,-1.35,.08,-.96,.96,'beam')
    v.box(-.5,.58,1.2,.5,.9,1.5,'beam');v.box(-.5,.74,-1.5,.5,.9,-1.2,'beam');v.box(-.08,.6,-1.4,.08,.72,1.4,'beam')
    v.box(-.76,.9,-2.0,.76,.98,2.0,'wood')
    v.sym(.76,.98,-2.0,.83,1.55,2.0,'wood');v.box(-.76,.98,-2.06,.76,1.55,-2.0,'wood');v.box(-.76,.98,2.0,.76,1.4,2.06,'wood')
    for z in (-1.9,-.95,0,.95,1.9):v.sym(.83,.94,z-.045,.88,1.6,z+.045,'beam')
    v.sym(.8,1.55,-2.02,.9,1.62,2.02,'dark')
    v.box(-.8,1.62,1.45,.8,1.7,1.95,'dark');v.box(-.8,1.3,2.06,.8,1.36,2.4,'dark');v.sym(.7,1.7,1.45,.76,1.95,1.52,'dark')
    v.box(-.06,.5,1.5,.06,.6,3.7,'beam');v.box(-.75,.52,3.45,.75,.6,3.55,'beam');v.box(-.6,.52,2.3,.6,.6,2.38,'beam')
    return v
def chariot():
    v=Vehicle().wheel('wheel_spoked',.82,-.2,.55)
    v.cyl_x(.55,-.2,.07,-.86,.86,'beam')
    v.box(-.56,.62,-.62,.56,.69,.42,'wood')
    v.box(-.56,.69,.36,.56,1.3,.42,'leather');v.sym(.5,.69,-.3,.56,1.2,.42,'leather');v.sym(.5,.69,-.62,.56,.95,-.3,'leather')
    v.sym(.5,1.2,-.3,.57,1.27,.42,'beam');v.box(-.57,1.3,.35,.57,1.37,.43,'beam')
    v.box(-.05,.56,.42,.05,.66,2.6,'beam',('x',-22.5,(0,.6,.42)));v.box(-.75,1.4,2.35,.75,1.48,2.43,'beam')
    return v
def plough():
    """A swing plough: an oak beam forward to the hake, an iron share and coulter under it, a mouldboard turning the
    furrow to the right, and two handles back to the ploughman. No wheels: it rides on its sole."""
    v=Vehicle()
    v.box(-.07,.0,-.95,.07,.07,.25,'iron')                                   # sole and landside
    v.box(-.12,.0,.15,.07,.06,.45,'iron')                                    # share point
    v.box(.05,.04,-.6,.12,.36,.25,'iron',('y',-22.5,(.05,0,.25)))             # mouldboard
    v.box(-.05,.06,.0,.04,.55,.12,'iron')                                    # standard
    v.box(-.04,.06,.42,.03,.5,.48,'iron',('x',-22.5,(0,.06,.45)))             # coulter
    v.box(-.07,.5,-.6,.07,.62,1.35,'beam')              # beam
    v.box(-.09,.62,1.3,.09,.76,1.42,'iron')                                  # hake
    v.sym(.16,.1,-.9,.22,.2,-.2,'beam')                                      # handle stays
    for i in range(6):
        z=-.6-.12*i;y=.2+.13*i
        v.sym(.17+.02*i,y,z-.1,.23+.02*i,y+.12,z,'beam')                     # handles climbing back
    v.box(-.32,.95,-1.36,.32,1.01,-1.3,'beam')                               # rung between the handles
    return v
def motor_truck():
    v=Vehicle().wheel('wheel_truck',.98,1.75,.5).wheel('wheel_truck',.98,-1.6,.5)
    v.sym(.44,.62,-2.7,.56,.8,2.7,'black');v.cyl_x(.5,1.75,.07,-.98,.98,'iron');v.cyl_x(.5,-1.6,.07,-.98,.98,'iron')
    for z in (-2.6,-1.0,.3,2.5):v.box(-.56,.64,z-.05,.56,.78,z+.05,'black')
    v.box(-.5,.8,1.5,.5,1.5,2.55,'green');v.box(-.44,1.5,1.5,.44,1.56,2.55,'green');v.box(-.5,.72,2.55,.5,1.56,2.66,'iron');v.box(-.42,.8,2.66,.42,1.48,2.69,'black')
    v.sym(.58,1.05,2.62,.82,1.3,2.74,'lamp');v.sym(.72,1.04,1.15,1.16,1.1,2.35,'black');v.sym(1.1,.8,1.2,1.16,1.04,1.3,'black')
    v.box(-.95,.8,.35,.95,.88,1.5,'dark');v.box(-.95,.88,1.4,.95,1.6,1.5,'green');v.box(-.88,1.6,1.42,.88,2.38,1.46,'glass');v.sym(.88,1.6,1.4,.95,2.4,1.48,'green')
    v.box(-.9,.88,.45,.9,1.28,.95,'leather');v.box(-.9,1.28,.45,.9,1.75,.55,'leather');v.box(-.95,.88,.3,.95,2.4,.38,'green');v.box(-1.0,2.4,.28,1.0,2.48,1.55,'black')
    v.box(.3,1.25,1.05,.6,1.3,1.35,'black',('x',22.5,(.45,1.27,1.2)));v.box(.42,.9,1.2,.48,1.28,1.26,'iron')
    v.box(-1.06,.95,-2.7,1.06,1.04,.25,'wood');v.sym(1.0,1.04,-2.7,1.07,1.62,.25,'wood');v.box(-1.0,1.04,.18,1.0,1.62,.25,'wood');v.box(-1.0,1.04,-2.76,1.0,1.5,-2.7,'wood')
    for z in (-2.6,-1.7,-.8,.1):v.sym(1.07,1.0,z-.04,1.11,1.66,z+.04,'iron');v.box(-1.0,.8,z-.05,1.0,.95,z+.05,'dark')
    return v
def locomotive():
    v=Vehicle()
    for z in (-1.5,0,1.5):v.wheel('wheel_driver',.72,z,.65)
    v.rod('rod_coupling',.9,0,.65,.3)
    v.sym(.56,.5,-3.0,.62,1.05,3.0,'black')
    for z in (-2.9,-.75,.75,2.9):v.box(-.56,.6,z-.05,.56,1.0,z+.05,'black')
    v.box(-1.3,1.05,-3.1,1.3,1.12,3.1,'black')
    for z,turn in ((3.1,1),(-3.2,1)):
        v.box(-1.3,.78,z,1.3,1.2,z+.1,'red')
    for x in (-.85,.85):
        v.cyl_z(x,.98,.09,3.2,3.4,'iron');v.cyl_z(x,.98,.16,3.4,3.46,'iron');v.cyl_z(x,.98,.09,-3.4,-3.2,'iron');v.cyl_z(x,.98,.16,-3.46,-3.4,'iron')
    v.box(-.06,.86,3.2,.06,.98,3.36,'iron');v.box(-.06,.86,-3.36,.06,.98,-3.2,'iron')
    v.cyl_z(0,2.08,.62,-.75,2.5,'boiler')
    for z in (.1,1.1,2.1):v.cyl_z(0,2.08,.645,z-.035,z+.035,'brass')
    v.cyl_z(0,2.08,.68,2.5,3.02,'black');v.cyl_z(0,2.08,.5,3.02,3.07,'boiler');v.box(-.04,2.0,3.07,.04,2.16,3.1,'brass');v.box(-.36,2.04,3.07,.36,2.1,3.09,'iron')
    v.box(-.5,1.12,2.45,.5,1.55,3.0,'black')
    v.cyl_y(0,2.76,.19,2.7,3.42,'black');v.cyl_y(0,2.76,.25,3.4,3.56,'brass');v.cyl_y(0,2.76,.25,2.66,2.76,'black')
    v.cyl_y(0,1.25,.3,2.6,3.0,'brass');v.cyl_y(0,1.25,.22,3.0,3.12,'brass')
    v.cyl_y(-.12,.2,.05,2.68,2.95,'brass');v.cyl_y(.12,.2,.05,2.68,2.95,'brass')
    v.box(-.1,2.78,3.02,.1,2.98,3.2,'lamp')
    v.sym(.7,1.12,-.6,1.25,2.28,2.25,'green');v.sym(.7,2.28,-.6,1.25,2.32,2.25,'black')
    v.sym(.74,.52,1.95,1.2,1.02,2.85,'black');v.sym(.86,.7,2.85,1.02,.86,3.0,'iron')
    v.sym(1.22,1.12,-2.45,1.28,2.25,-.6,'green');v.sym(1.22,2.25,-2.45,1.28,3.2,-2.3,'green');v.sym(1.22,2.25,-.75,1.28,3.2,-.6,'green');v.sym(1.22,2.95,-2.3,1.28,3.2,-.75,'green')
    v.box(-1.22,2.3,-.66,1.22,3.2,-.6,'green');v.sym(.25,2.45,-.6,.85,3.0,-.57,'glass')
    v.box(-1.22,1.12,-2.45,1.22,2.25,-2.39,'green');v.box(-1.22,2.25,-2.45,1.22,3.2,-2.41,'green');v.sym(.25,2.45,-2.47,.85,3.0,-2.44,'glass')
    v.box(-1.25,1.12,-3.05,1.25,2.35,-2.45,'green');v.box(-1.15,2.35,-3.0,1.15,2.5,-2.5,'coal')
    v.box(-1.4,3.2,-2.6,1.4,3.28,-.5,'grey');v.box(-1.15,3.28,-2.6,1.15,3.35,-.5,'grey');v.box(-.75,3.35,-2.6,.75,3.4,-.5,'grey')
    v.box(-1.0,1.12,-2.3,1.0,1.18,-.7,'dark');v.box(-.5,1.18,-.85,.5,2.3,-.7,'boiler');v.box(-.12,1.5,-.9,.12,1.75,-.85,'lamp')
    v.sym(1.28,.55,-1.9,1.36,.62,-1.2,'black');v.sym(1.28,.8,-1.9,1.36,.86,-1.2,'black')
    return v
def underframe(v,half,axles,radius=.5):
    for z in axles:v.wheel('wheel_rail',.72,z,radius);v.cyl_x(radius,z,.06,-.9,.9,'iron');v.sym(.86,.3,z-.16,.98,.8,z+.16,'black');v.sym(.9,.62,z-.5,.96,.7,z+.5,'iron')
    v.sym(.94,.78,-half,1.06,1.02,half,'black');v.sym(.3,.8,-half,.38,1.0,half,'black')
    for z in (-half,half-.1):v.box(-1.22,.76,z,1.22,1.06,z+.1,'black')
    for x in (-.85,.85):
        v.cyl_z(x,.95,.08,half,half+.22,'iron');v.cyl_z(x,.95,.15,half+.22,half+.28,'iron');v.cyl_z(x,.95,.08,-half-.22,-half,'iron');v.cyl_z(x,.95,.15,-half-.28,-half-.22,'iron')
    v.box(-.05,.84,half,.05,.96,half+.18,'iron');v.box(-.05,.84,-half-.18,.05,.96,-half,'iron')
def rail_wagon():
    v=Vehicle();underframe(v,2.4,(-1.4,1.4))
    v.box(-1.2,1.02,-2.4,1.2,1.1,2.4,'dark')
    for i in range(5):
        y=1.1+i*.23;v.sym(1.13,y,-2.4,1.21,y+.215,2.4,'wood');v.box(-1.13,y,-2.4,1.13,y+.215,-2.32,'wood');v.box(-1.13,y,2.32,1.13,y+.215,2.4,'wood')
    for z in (-2.36,-.55,.55,2.36):v.sym(1.21,1.05,z-.05,1.25,2.3,z+.05,'iron')
    for x in (-1.17,1.17):
        for z in (-2.41,2.41):v.box(x-.07,1.05,z-.03,x+.07,2.3,z+.03,'iron')
    v.sym(1.21,1.5,-.5,1.24,1.56,.5,'iron')
    return v
def rail_carriage():
    v=Vehicle();underframe(v,2.9,(-1.9,1.9))
    v.box(-1.3,1.02,-2.9,1.3,1.1,2.9,'dark')
    v.sym(1.24,1.1,-2.9,1.3,1.95,2.9,'green');v.sym(1.24,2.62,-2.9,1.3,2.95,2.9,'cream')
    v.box(-1.24,1.1,-2.9,1.24,2.95,-2.84,'green');v.box(-1.24,1.1,2.84,1.24,2.95,2.9,'green')
    pillars=[-2.9,-2.2,-1.5,-.8,-.1,.6,1.3,2.0,2.7]
    for z in pillars:v.sym(1.24,1.95,z,1.3,2.62,z+.2,'green')
    for z in pillars[:-1]:v.sym(1.26,1.98,z+.2,1.28,2.6,z+.7,'glass')
    for z in (-2.2,-.1,2.0):v.sym(1.3,1.5,z+.02,1.33,1.56,z+.18,'brass')
    v.sym(1.3,1.9,-2.9,1.32,1.96,2.9,'brass')
    v.box(-1.4,2.95,-3.0,1.4,3.03,3.0,'grey');v.box(-1.2,3.03,-3.0,1.2,3.12,3.0,'grey');v.box(-.85,3.12,-3.0,.85,3.19,3.0,'grey')
    for z in (-2.0,-.6,.8,2.2):v.cyl_y(0,z,.09,3.19,3.36,'black')
    for z in (-2.1,-.7,.7,2.1):v.box(-1.2,1.1,z-.05,1.2,1.5,z+.05,'dark');v.box(-1.2,1.45,z-.3,1.2,1.55,z+.3,'leather')
    v.sym(1.3,.7,-2.6,1.45,.76,-1.9,'black');v.sym(1.3,.7,1.9,1.45,.76,2.6,'black')
    return v
def cannon():
    v=Vehicle().wheel('wheel_gun',.82,.3,.7)
    v.cyl_x(.7,.3,.1,-.9,.9,'beam')
    v.sym(.2,.62,-.15,.32,1.1,.95,'dark');v.sym(.2,1.1,.25,.32,1.22,.6,'iron')
    v.box(-.16,.6,-1.55,.16,.82,.3,'dark',('x',-22.5,(0,.72,.3)));v.box(-.2,.58,-1.6,.2,.84,-1.45,'iron',('x',-22.5,(0,.72,.3)))
    v.box(-.32,.6,.0,.32,.72,.4,'iron')
    v.cyl_z(0,1.2,.19,-.5,.55,'bronze');v.cyl_z(0,1.2,.165,.55,1.4,'bronze');v.cyl_z(0,1.2,.14,1.4,2.2,'bronze');v.cyl_z(0,1.2,.18,2.2,2.32,'bronze')
    for z in (-.5,.5,1.38):v.cyl_z(0,1.2,.21,z,z+.06,'bronze')
    v.cyl_z(0,1.2,.1,-.62,-.5,'bronze');v.cyl_z(0,1.2,.07,-.74,-.62,'bronze');v.cyl_x(1.2,.42,.07,-.36,.36,'bronze')
    v.box(-.02,1.39,-.36,.02,1.43,-.3,'iron')
    return v
def field_gun():
    v=Vehicle().wheel('wheel_gun',.82,.3,.66)
    v.cyl_x(.66,.3,.08,-.9,.9,'iron')
    v.box(-.13,.5,-1.75,.13,.68,.3,'steel',('x',-22.5,(0,.6,.3)));v.box(-.3,.4,-1.85,.3,.74,-1.7,'iron',('x',-22.5,(0,.6,.3)))
    v.sym(.14,.55,.1,.24,1.15,.7,'steel')
    v.sym(.16,.5,.44,.9,1.62,.5,'steel');v.box(-.16,1.3,.44,.16,1.62,.5,'steel');v.box(-.9,1.62,.38,.9,1.68,.5,'steel',('x',22.5,(0,1.62,.44)))
    v.cyl_z(0,1.12,.1,-.2,2.75,'iron');v.cyl_z(0,1.12,.15,-.75,-.2,'iron');v.box(-.2,.98,-.9,.2,1.26,-.72,'iron');v.cyl_z(0,.93,.09,-.3,1.5,'steel')
    v.cyl_z(0,1.12,.12,2.75,2.85,'iron');v.box(.3,.9,-.9,.6,.96,-.6,'leather');v.cyl_y(.36,-.55,.1,.96,1.0,'iron')
    return v
def bombard():
    v=Vehicle()
    v.sym(.5,0,-1.6,.78,.3,1.6,'beam')
    for z in (-1.45,-.55,.35,1.25):v.box(-.78,.3,z-.14,.78,.46,z+.14,'beam')
    v.cyl_z(0,.98,.5,-.75,1.6,'black');v.cyl_z(0,.98,.34,-1.55,-.75,'black')
    for z in (-.7,-.15,.4,.95,1.5):v.cyl_z(0,.98,.545,z-.05,z+.05,'iron')
    for z in (-1.5,-1.1):v.cyl_z(0,.98,.385,z-.04,z+.04,'iron')
    v.cyl_z(0,.98,.36,1.6,1.62,'coal')
    v.box(-.78,.3,-1.9,.78,1.3,-1.6,'beam');v.sym(.5,.46,-1.6,.78,1.0,-1.2,'beam')
    v.sym(.3,.46,.2,.5,.62,.9,'dark');v.sym(.3,.46,-.6,.5,.62,.0,'dark')
    return v
def trebuchet():
    v=Vehicle()
    v.sym(1.35,0,-2.4,1.6,.25,2.4,'beam')
    for z in (-2.28,0,2.28):v.box(-1.6,.02,z-.12,1.6,.27,z+.12,'beam')
    v.sym(1.36,.25,-.1,1.58,3.15,.1,'beam')
    for sign in (1,-1):v.sym(1.38,.3,-.09,1.56,3.1,.09,'beam',('x',sign*22.5,(1.47,3.1,0)))
    v.sym(1.38,1.5,-.62,1.56,1.64,.62,'dark')
    v.cyl_x(3.0,0,.1,-1.66,1.66,'iron')
    pivot=('x',22.5,(0,3.0,0))
    v.box(-.11,2.89,-3.3,.11,3.11,1.1,'beam',pivot);v.box(-.13,2.87,-.3,.13,3.13,.3,'iron',pivot);v.box(-.13,2.87,.85,.13,3.13,1.12,'iron',pivot)
    v.box(-.5,1.35,.62,.5,2.3,1.45,'wood');v.sym(.5,1.3,.58,.56,2.35,1.49,'iron');v.box(-.5,2.3,.62,.5,2.42,1.45,'stone');v.sym(.06,2.3,.98,.12,2.72,1.08,'iron')
    v.box(-.03,2.25,-3.02,.03,4.2,-2.96,'rope');v.box(-.2,2.1,-3.12,.2,2.25,-2.86,'leather')
    v.box(-.32,.27,-2.4,.32,.33,.2,'wood');v.cyl_x(.75,-1.95,.11,-1.3,1.3,'beam');v.sym(1.2,.25,-2.05,1.34,.95,-1.85,'beam');v.sym(1.3,.6,-2.0,1.5,.9,-1.9,'dark')
    return v
VEHICLES={'plough':plough(),'cart':cart(),'wagon':wagon(),'chariot':chariot(),'motor_truck':motor_truck(),'locomotive':locomotive(),'rail_wagon':rail_wagon(),
          'rail_carriage':rail_carriage(),'cannon':cannon(),'field_gun':field_gun(),'bombard':bombard(),'trebuchet':trebuchet()}

def clip(box,lo,hi):
    x1,y1,z1,x2,y2,z2,tex,rot=box
    if z2<=lo+1e-9 or z1>=hi-1e-9:return None
    return (x1,y1,max(z1,lo),x2,y2,min(z2,hi),tex,rot)
def static_wheel(name,x,z,radius):
    """A plain stand-in wheel for the inventory icon."""
    return [(x-.07,radius*.3,z-radius*.7,x+.07,radius*1.7,z+radius*.7,'dark' if 'spoked' in name or 'gun' in name else 'black',turn) for turn in (None,('x',45,(x,radius,z)))]
layouts={}
for kind,vehicle in VEHICLES.items():
    lo=min(box[2] for box in vehicle.boxes);hi=max(box[5] for box in vehicle.boxes);top=max(box[4] for box in vehicle.boxes);wide=max(max(abs(box[0]),abs(box[3])) for box in vehicle.boxes)
    assert top<=4.5 and wide<=2.25,(kind,top,wide)
    slices=max(1,math.ceil((hi-lo)/4.3));step=(hi-lo)/slices;centres=[]
    for index in range(slices):
        a,b=lo+index*step,lo+(index+1)*step;centre=(a+b)/2;centres.append(centre)
        emit(f'{kind}_{index}',[element(cut,centre,U,-16) for cut in (clip(box,a,b) for box in vehicle.boxes) if cut])
    layouts[kind]=(centres,vehicle.wheels,vehicle.rods)
    # Inventory icon: the whole vehicle shrunk into one model volume.
    unit=min(44/(hi-lo),30/top,22/wide);middle=(lo+hi)/2
    icon=[element(box,middle,unit,0) for box in vehicle.boxes]+[element(box,middle,unit,0) for wheel in vehicle.wheels for box in static_wheel(*wheel)]
    display={'gui':{'rotation':[25,225,0],'translation':[0,-2.5,0],'scale':[.55,.55,.55]},'ground':{'scale':[.4,.4,.4]},'fixed':{'scale':[.5,.5,.5]},
        'thirdperson_righthand':{'translation':[0,2,0],'scale':[.3,.3,.3]},'thirdperson_lefthand':{'translation':[0,2,0],'scale':[.3,.3,.3]},
        'firstperson_righthand':{'rotation':[0,45,0],'scale':[.3,.3,.3]},'firstperson_lefthand':{'rotation':[0,225,0],'scale':[.3,.3,.3]},'head':{'scale':[.8,.8,.8]}}
    write(ASSETS/'models/item'/f'{kind}.json',model(icon,display))
    write(ASSETS/'items'/f'{kind}.json',{'model':{'type':'minecraft:model','model':f'{NS}:item/{kind}'}})
for name,shape in WHEELS.items():emit_centred(name,shape)
for name,shape in RODS.items():emit_centred(name,shape,12)  # drawn at 4/3 by the renderer

def f(value):return f'{value:.3f}f'
lines=['package dev.qube.spawn_9b5fbb9.client;','','import java.util.Map;','',
       '/** Generated by tools/generate-vehicle-models.py: where each sliced body model, wheel and rod of a vehicle goes. Do not edit. */',
       'public final class VehicleLayouts {','    private VehicleLayouts(){}',
       '    public record Wheel(String model,float x,float z,float radius){}',
       '    public record Rod(String model,float x,float z,float axle,float crank,float phase){}',
       '    public record Layout(float[] centres,Wheel[] wheels,Rod[] rods){}',
       '    public static final Map<String,Layout> LAYOUTS=Map.ofEntries(']
entries=[]
for kind,(centres,wheels,rods) in layouts.items():
    entries.append(f'        Map.entry("{kind}",new Layout(new float[]{{{",".join(f(c) for c in centres)}}},new Wheel[]{{{",".join(f"""new Wheel("{n}",{f(x)},{f(z)},{f(r)})""" for n,x,z,r in wheels)}}},new Rod[]{{{",".join(f"""new Rod("{n}",{f(x)},{f(z)},{f(a)},{f(c)},{f(p)})""" for n,x,z,a,c,p in rods)}}}))')
lines.append(',\n'.join(entries)+');');lines.append('}')
JAVA.write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('vehicle models written:',{kind:len(layouts[kind][0]) for kind in layouts})
