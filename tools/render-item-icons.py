"""Original pixel grids and Qube templates; all PNG writes are delegated to qube texture.

The reference audit establishes identities. This script is reproducible art source,
not a photo downloader or a PNG encoder. Use --resume after an interrupted run.
"""
from pathlib import Path
import argparse,csv,json,math,subprocess,shutil

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/spawn_9b5fbb9'
WORK=ROOT/'run/qube/icon-grids'
REPORT=ROOT/'run/qube/icon-generation.json'
NS='spawn_9b5fbb9'
COLORS={'knapped_stone':'#8B887C','obsidian':'#424151','copper':'#CD8054','bronze':'#B28B45','forged_iron':'#8B999D','iron':'#8B999D','tin':'#B6C5C7'}

def mix(color,other,fraction):
    rgb=[int(color[i:i+2],16) for i in (1,3,5)]; to=[int(other[i:i+2],16) for i in (1,3,5)]
    return '#'+''.join(f'{round(a+(b-a)*fraction):02X}' for a,b in zip(rgb,to))

class Grid:
    def __init__(self,color='#AD9164',accent='#587343'):
        self.palette=['#00000000',mix(color,'#000000',.72),mix(color,'#000000',.32),color,mix(color,'#FFFFFF',.32),mix(color,'#FFFFFF',.62),accent,'#81603C']
        self.p=[[0]*16 for _ in range(16)]
    def dot(self,x,y,c=3):
        if 1<=x<=14 and 1<=y<=14:self.p[y][x]=c
    def line(self,x,y,X,Y,c=3):
        n=max(abs(X-x),abs(Y-y),1)
        for i in range(n+1):self.dot(round(x+(X-x)*i/n),round(y+(Y-y)*i/n),c)
    def rect(self,x,y,X,Y,c=3):
        for yy in range(y,Y+1):
            for xx in range(x,X+1):self.dot(xx,yy,c)
    def ellipse(self,x,y,rx,ry,c=3):
        for yy in range(y-ry,y+ry+1):
            for xx in range(x-rx,x+rx+1):
                if (xx-x)**2/max(1,rx*rx)+(yy-y)**2/max(1,ry*ry)<=1.05:self.dot(xx,yy,c)
    def poly(self,points,c=3):
        for y in range(1,15):
            for x in range(1,15):
                inside=False;j=len(points)-1
                for i,(xi,yi) in enumerate(points):
                    xj,yj=points[j]
                    if (yi>y)!=(yj>y) and x<(xj-xi)*(y-yi)/(yj-yi)+xi:inside=not inside
                    j=i
                if inside:self.dot(x,y,c)
        for i,(x,y) in enumerate(points):self.line(x,y,*points[(i+1)%len(points)],c)
    def shade(self):
        for y in range(1,15):
            for x in range(1,15):
                if self.p[y][x]==3:self.p[y][x]=4 if x+y<12 else 2 if x+y>21 else 3
        return self
    def outline(self):
        source=[row[:] for row in self.p]
        for y in range(1,15):
            for x in range(1,15):
                if source[y][x]==0 and any(source[Y][X] for X,Y in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]):self.p[y][x]=1
        return self
    def json(self):return {'palette':self.palette,'rows':[''.join(format(c,'X') for c in row) for row in self.p]}

def plant(name,world=False):
    green='#6F8C48';accent='#C8B7A1'
    if 'flax' in name:accent='#8CA9DE'
    elif 'yarrow' in name:accent='#E8E5C5'
    elif 'bramble' in name or 'berr' in name:accent='#765578'
    elif 'maize' in name:accent='#D9B84F'
    elif 'rice' in name or 'wheat' in name:green='#969856';accent='#D5BC75'
    g=Grid(green,accent)
    if 'rice' in name:
        for x,top in [(3,6),(7,3),(10,5)]:
            g.line(x,14,x,top,2);g.line(x,10,x-2,8,3);g.line(x,top,x+3,top+2,3)
            for dx in range(1,4):g.line(x+dx,top+1+dx//2,x+dx,top+3+dx//2,6)
        return g.shade().outline()
    if 'bramble' in name or 'berr' in name:
        for x,y in [(4,10),(7,8),(10,10),(8,12)]:g.ellipse(x,y,3,2)
        for x,y in [(5,8),(9,10),(7,11)]:g.dot(x,y,6)
    else:
        for x,top in [(4,6),(8,3),(11,5)]:
            g.line(x,14,x,top,2);g.line(x,11,x-2,9,3);g.line(x,9,x+2,7,4)
            if 'flax' in name or 'yarrow' in name:
                for dx,dy in [(0,0),(-1,0),(1,0),(0,-1),(0,1)]:g.dot(x+dx,top+dy,6)
                g.dot(x,top,5)
            elif 'wheat' in name or 'rice' in name:
                for yy in range(top,top+5):g.dot(x+(yy%2),yy,6)
            elif 'maize' in name:g.rect(x-1,7,x,10,6)
        if 'carrot' in name and not world:g.poly([(7,10),(10,10),(8,14)],6);g.palette[6]='#D98C43'
        if 'potato' in name and not world:g.ellipse(8,12,3,2,6);g.palette[6]='#BA9868'
        if 'beet' in name and not world:g.ellipse(8,12,2,2,6);g.palette[6]='#975460'
        if 'pumpkin' in name:g.ellipse(8,11,4,3,6);g.palette[6]='#C88A3F'
        if 'melon' in name:g.ellipse(8,11,4,3,6);g.palette[6]='#7A9C59'
    return g.shade().outline()

def custom(name):
    # Plant sources are botanical stands; inventory representations may show their harvested root.
    if name.startswith('wild_') and name not in ['wild_plants','wild_berries']:return plant(name)
    if name.endswith('_crop'):return plant(name,True)
    if name=='fruit_tree':
        g=Grid('#63834C','#B96148');g.line(8,14,8,5,7);g.line(8,8,4,5,7);g.ellipse(7,5,4,3);g.dot(5,6,6);g.dot(9,5,6);return g.shade().outline()
    if name in ['wild_plants','medicinal_leaves','yarrow_leaves']:
        g=Grid('#65814E','#E6DEC0');g.line(5,13,10,3,2)
        for x,y in [(5,10),(7,7),(9,4)]:g.poly([(x,y),(x-3,y-2),(x-2,y),(x,y+1)]);g.line(x,y,x+3,y-1,4)
        if name=='yarrow_leaves':g.dot(11,3,6)
        return g.outline()
    if name=='blackberries' or name=='wild_berries':
        g=Grid('#65516E' if name=='blackberries' else '#8C5964','#789856')
        for x,y in [(6,5),(9,5),(4,8),(7,8),(10,8),(6,11),(9,11)]:g.ellipse(x,y,2,2);g.dot(x,y-1,5)
        g.line(7,4,9,2,6);return g.shade().outline()
    if name.endswith('_seeds') or name=='rice_grain':
        color='#C4AE78' if 'rice' in name else '#D7BB55' if 'maize' in name else '#9C704A' if 'flax' in name or 'fiber' in name else '#B59B77'
        if name=='rice_grain':color='#E3DCBA'
        g=Grid(color)
        if 'flax' in name:
            for x,y in [(4,9),(8,5),(11,10)]:g.ellipse(x,y,2,1);g.dot(x,y,5)
            return g.shade().outline()
        if 'yarrow' in name:
            for x,y in [(3,8),(6,5),(8,10),(11,6),(12,12)]:g.line(x,y,x,y+1,3)
            return g.shade().outline()
        if 'bramble' in name:
            for x,y in [(4,10),(8,5),(11,10)]:g.poly([(x,y-1),(x+1,y+1),(x-1,y+1)])
            return g.shade().outline()
        for x,y in [(4,9),(8,5),(11,10)]:
            if 'maize' in name:g.poly([(x-1,y-1),(x+2,y-1),(x+1,y+2),(x-1,y+2)])
            else:g.line(x,y+2,x+1,y-1,3);g.dot(x,y,4)
        return g.shade().outline()
    if name=='maize':
        g=Grid('#D5B34D','#658447');g.ellipse(8,7,3,5)
        for y in range(4,12,2):g.line(6,y,10,y,2)
        g.poly([(4,8),(7,13),(8,13),(6,9)],6);g.poly([(12,7),(10,13),(8,13),(10,9)],6);return g.outline()
    if name in ['plant_fiber','dried_fiber','flax_fiber','brush_bundle']:
        g=Grid('#BFAF81' if name!='plant_fiber' else '#9FA573','#737F45')
        for i in range(6):g.line(3+i,13,5+i+(i%2),3+i%3,3 if i%2 else 4)
        g.line(3,10,11,10,7);g.line(4,11,10,11,2);return g.outline()
    if name=='cordage':
        g=Grid('#AE9266');g.ellipse(7,7,5,4);g.ellipse(7,7,3,2,0);g.line(4,10,3,13);g.line(10,10,12,13)
        for x,y in [(3,6),(5,4),(9,4),(11,7),(8,10)]:g.dot(x,y,5)
        return g.shade().outline()
    if name=='yarn' or name=='bandage':
        g=Grid('#D2CCB0' if name=='yarn' else '#D6DCCE');g.ellipse(7,7,5,4)
        for x in [4,6,8,10]:g.line(x,5,x+1,10,2)
        g.line(11,9,13,12,4);g.line(13,12,11,14,3);return g.shade().outline()
    if name in ['wool_cloth','fiber_cloth','clean_cloth']:
        g=Grid('#AEB4AD' if name=='wool_cloth' else '#BAA47B' if name=='fiber_cloth' else '#DADDCF')
        g.poly([(3,4),(11,3),(13,11),(5,13),(3,10)])
        for y in [6,9,11]:g.line(4,y,11,y-1,2)
        g.poly([(9,3),(11,3),(12,6),(9,6)],5);return g.shade().outline()
    if name.startswith(('wool_','fiber_')):
        piece=name.split('_')[-1];g=Grid('#A8ACA5' if name.startswith('wool') else '#B8A281')
        if piece in ['shirt','jacket']:g.poly([(5,3),(6,4),(9,4),(10,3),(13,6),(11,8),(10,7),(11,13),(4,13),(5,7),(3,8),(1,6)])
        elif piece=='pants':g.poly([(4,3),(11,3),(12,13),(8,13),(8,7),(7,7),(6,13),(3,13)])
        else:g.poly([(3,7),(6,7),(6,11),(8,12),(7,13),(2,13),(2,10)]);g.poly([(9,6),(12,6),(12,10),(14,11),(13,12),(9,12)])
        if piece=='jacket':g.line(7,4,7,13,2);g.dot(8,7,5);g.dot(8,10,5)
        return g.shade().outline()
    if name in ['unfired_pot','fired_pot','pot_lid','ceramic_shard']:
        g=Grid('#9E927D' if name=='unfired_pot' else '#C28C64')
        if name=='pot_lid':g.ellipse(8,9,6,2);g.rect(7,6,9,7,4)
        elif name=='ceramic_shard':g.poly([(3,4),(11,5),(13,9),(7,13),(5,9)]);g.line(4,5,10,6,5)
        else:g.poly([(5,3),(11,3),(10,5),(13,8),(12,12),(10,14),(5,14),(3,11),(3,8),(6,5)]);g.ellipse(8,3,3,1,1);g.line(5,8,5,11,5)
        return g.shade().outline()
    if name in ['antibiotic','antiseptic','antitoxin','painkiller']:
        liquid={'antibiotic':'#8EAA76','antiseptic':'#7CA2AF','antitoxin':'#B69A6A','painkiller':'#BA925B'}[name]
        g=Grid('#A9B6B2',liquid);g.rect(5,5,11,13);g.rect(7,2,9,4,7);g.rect(6,8,10,12,6);g.line(6,6,6,10,5);g.rect(7,8,9,10,4)
        if name=='antibiotic':g.line(8,8,8,10,2)
        elif name=='antitoxin':g.line(7,9,9,9,2)
        elif name=='antiseptic':g.dot(8,9,2)
        return g.outline()
    if name=='fertilized_egg':
        g=Grid('#D9D0AD');g.ellipse(8,9,4,5);g.ellipse(8,6,3,3);g.dot(6,10,7);g.dot(10,8,7);return g.shade().outline()
    if name=='spear':
        g=Grid('#918D7A');g.line(2,14,11,5,7);g.line(3,14,12,5,7);g.poly([(10,3),(14,1),(13,6),(11,6)]);g.dot(11,7,2);return g.shade().outline()
    if name=='wooden_toy':
        g=Grid('#B39162');g.rect(4,7,10,10);g.line(4,10,4,13,2);g.line(10,10,10,13,2);g.poly([(9,8),(10,3),(12,3),(13,6),(11,7)]);g.line(3,8,2,5,2);g.dot(12,4,1);return g.shade().outline()
    if name in ['loose_stone','loose_stones','limestone','tin_ore','malachite_ore','cassiterite_ore','hematite_ore','iron_bloom','metal_scrap','stone_edge','obsidian_flake','spoiled_food']:
        color='#B8B49A' if name=='limestone' else '#3C3A48' if name=='obsidian_flake' else '#6F7776' if name in ['metal_scrap','iron_bloom'] else '#99978B'
        g=Grid(color,'#66854F' if name=='spoiled_food' else '#8C6345')
        points=[(3,7),(6,3),(11,4),(13,8),(11,12),(5,13),(2,10)]
        if name in ['stone_edge','obsidian_flake']:points=[(3,12),(6,5),(13,2),(12,9),(8,13)]
        if name=='metal_scrap':points=[(3,4),(11,3),(10,7),(13,10),(7,13),(3,10),(6,8)]
        g.poly(points)
        if name.endswith('_ore'):
            g.palette[6]='#488F66' if 'malachite' in name else '#69503A' if 'cassiterite' in name or name=='tin_ore' else '#9F6255'
            for x,y in [(5,7),(9,5),(10,9),(7,11)]:g.dot(x,y,6);g.dot(x+1,y,6)
        if name in ['iron_bloom','spoiled_food']:
            for x,y in [(5,8),(9,6),(8,11),(11,9)]:g.dot(x,y,1 if name=='iron_bloom' else 6)
        if name in ['stone_edge','obsidian_flake']:g.line(5,10,11,4,5);g.line(8,10,11,7,2)
        return g.shade().outline()
    if name=='fallen_sticks':
        g=Grid('#AA8659');g.line(3,12,12,4);g.line(4,12,13,4,2);g.line(3,5,12,12,7);g.line(9,9,12,8,7);return g.outline()
    if name=='metal_rivet':
        g=Grid('#AFA99B');g.rect(7,6,9,12,2);g.ellipse(8,5,4,2);g.line(6,4,9,4,5);return g.outline()
    if name.endswith(('helmet','chestplate','leggings','boots','helmet_blank','chestplate_blank','leggings_blank','boots_blank')):
        color=COLORS.get(name.split('_')[0],COLORS['iron']);g=Grid(color);piece=name.removesuffix('_blank').split('_')[-1]
        if piece=='helmet':g.poly([(3,6),(5,3),(10,3),(13,6),(13,12),(10,12),(10,8),(5,8),(5,12),(3,12)])
        elif piece=='chestplate':g.poly([(3,3),(6,4),(9,4),(12,3),(13,7),(11,8),(11,13),(4,13),(4,8),(2,7)])
        elif piece=='leggings':g.rect(4,3,11,6);g.rect(4,7,6,13);g.rect(9,7,11,13)
        else:g.rect(3,5,6,11);g.rect(3,11,8,13);g.rect(9,4,12,10);g.rect(9,10,14,12)
        if name.endswith('_blank'):g.dot(4,6,0);g.dot(11,6,0)
        else:g.line(5,5,10,5,5)
        return g.shade().outline()
    if name.endswith('_spear_blank'):
        g=Grid(COLORS['copper'] if name.startswith('copper') else COLORS['iron']);g.poly([(7,2),(11,7),(9,10),(9,13),(6,13),(6,10),(4,7)]);return g.shade().outline()
    if name in ['crossbow_stock','shield_blank','crossbow_blank','shears_blank','bucket_blank','flint_and_steel_blank','spyglass_blank','brush_blank']:
        g=Grid('#A17D52' if name=='crossbow_stock' else '#AE815C' if name in ['spyglass_blank','brush_blank'] else '#929B9B')
        if name=='crossbow_stock':g.poly([(3,12),(5,10),(8,10),(10,4),(12,3),(12,7),(10,13)])
        elif name=='shield_blank':g.ellipse(8,8,5,5);g.ellipse(8,8,2,2,2)
        elif name=='crossbow_blank':g.line(2,11,5,5);g.line(5,5,10,5);g.line(10,5,13,11);g.rect(7,6,9,10)
        elif name=='shears_blank':g.line(4,3,5,12);g.line(11,3,10,12);g.line(5,12,10,12,2)
        elif name=='bucket_blank':g.poly([(3,4),(12,4),(11,13),(5,13)]);g.ellipse(8,4,4,1,2)
        elif name=='flint_and_steel_blank':g.line(4,4,11,4);g.line(11,4,12,9);g.line(12,9,8,12);g.line(8,12,4,10)
        else:g.poly([(4,11),(10,4),(13,6),(7,13)]);g.line(6,11,11,6,5)
        return g.shade().outline()
    if name in ['kiln','forge','bloomery','anvil','loom','trough']:
        g=Grid('#AA8263' if name=='kiln' else '#797C71' if name in ['forge','bloomery','anvil'] else '#A18458','#D7A256')
        if name in ['kiln','bloomery']:g.poly([(4,13),(3,6),(5,3),(11,3),(13,6),(12,13)]);g.rect(6,8,10,12,1);g.line(6,13,11,13,2)
        elif name=='forge':g.rect(2,8,13,13);g.rect(4,4,5,8,2);g.rect(5,8,11,10,1);g.dot(8,8,6);g.dot(9,7,6)
        elif name=='anvil':g.poly([(2,4),(13,4),(12,7),(9,8),(9,10),(12,12),(12,13),(4,13),(4,12),(6,10),(6,8),(3,7)])
        elif name=='trough':g.poly([(2,6),(13,6),(12,12),(4,12)]);g.rect(4,7,11,8,1);g.line(4,13,4,14,2);g.line(11,13,11,14,2)
        else:
            g.rect(3,2,4,14,7);g.rect(11,2,12,14,7);g.line(4,3,11,3,2);g.line(4,12,11,12,2)
            for x in range(5,11):g.line(x,4,x,11,5 if x%2 else 6)
        return g.shade().outline()
    raise ValueError('No authored icon shape for '+name)

def invoke(qube,name,kind,arguments):
    command=[qube,'texture','write',NS,name,kind,*arguments]
    result=subprocess.run(command,cwd=ROOT,capture_output=True,encoding='utf-8',errors='replace',timeout=90)
    if result.returncode:
        (WORK/'failure.log').write_text(result.stdout+result.stderr,encoding='utf-8');raise RuntimeError(f'Qube failed for {name}; see {WORK}/failure.log')
    start=result.stdout.find('{');report=json.JSONDecoder().raw_decode(result.stdout[start:])[0]
    if report.get('bindings_missing'):raise RuntimeError(f'{name}: missing bindings {report["bindings_missing"]}')
    return report

def write_grid(qube,name,kind,grid):
    path=WORK/f'{name}.{kind}.json';path.write_text(json.dumps(grid),encoding='utf-8')
    return invoke(qube,name,kind,['--grid','@'+str(path)])

def head_from_template(report):
    grid=report['grid'];palette=grid['palette'];p=[[int(c,36) for c in row] for row in grid['rows']]
    def secondary(index):
        r,g,b=[int(palette[index][-6:][i:i+2],16) for i in (0,2,4)]
        return b>r*1.25 and b>g*1.15
    for y in range(16):
        for x in range(16):
            if p[y][x]==1 or p[y][x] and secondary(p[y][x]):p[y][x]=0
    # Keep the actual unfinished metal piece, dropping disconnected shaft highlights.
    remaining={(x,y) for y in range(16) for x in range(16) if p[y][x]};components=[]
    while remaining:
        component={remaining.pop()};front=list(component)
        while front:
            x,y=front.pop()
            for dx in (-1,0,1):
                for dy in (-1,0,1):
                    point=(x+dx,y+dy)
                    if point in remaining:remaining.remove(point);component.add(point);front.append(point)
        components.append(component)
    metal=max(components,key=len) if components else set()
    output=[[0]*16 for _ in range(16)]
    for x,y in metal:
        if 1<=x<=14 and 1<=y<=14:output[y][x]=p[y][x]
    source=[r[:] for r in output]
    for y in range(1,15):
        for x in range(1,15):
            if not source[y][x] and any(source[Y][X] for X,Y in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]):output[y][x]=1
    return {'palette':palette,'rows':[''.join('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'[v] for v in row) for row in output]}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--resume',action='store_true');parser.add_argument('--only',nargs='*');args=parser.parse_args()
    qube=shutil.which('qube');assert qube,'qube executable is required'
    WORK.mkdir(parents=True,exist_ok=True)
    completed=json.loads(REPORT.read_text(encoding='utf-8')) if REPORT.exists() else {}
    # Qube requires a modern item binding even for crop blocks carried as seeds.
    # These render-only definitions do not register or manufacture additional items.
    for crop in ['flax_crop','yarrow_crop','bramble_crop']:
        (ASSETS/'items'/f'{crop}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':f'{NS}:item/{crop}'}}),encoding='utf-8')
    # Bind every existing definition before asking Qube to validate texture writes.
    names=sorted(p.stem for p in (ASSETS/'items').glob('*.json'))
    for name in names:
        parent='minecraft:item/handheld' if any(name.endswith('_'+t) for t in ['pickaxe','axe','hoe','shovel','sword']) else 'minecraft:item/generated'
        (ASSETS/'models/item'/f'{name}.json').write_text(json.dumps({'parent':parent,'textures':{'layer0':f'{NS}:item/{name}'}},indent=2)+'\n',encoding='utf-8')
    for index,name in enumerate(names):
        if args.only and name not in args.only:continue
        if args.resume and 'item/'+name in completed:continue
        material=next((key for key in COLORS if name.startswith(key+'_')),None)
        shape=next((tool for tool in ['pickaxe','axe','hoe','shovel','sword'] if name.endswith('_'+tool) or name.endswith('_'+tool+'_blank')),None)
        template=shape or ('ingot' if name.endswith('_ingot') else 'dust' if name.startswith(('crushed_','roasted_')) else 'nugget' if name in ['malachite','cassiterite','hematite'] else None)
        if template:
            color=COLORS.get(material,'#9D9073')
            if name.startswith(('crushed_','roasted_')):
                metal=name.split('_')[-1];color={'copper':'#588568','tin':'#786553','iron':'#995E4B'}[metal]
                if name.startswith('roasted_'):color=mix(color,'#272A28',.35)
            elif name in ['malachite','cassiterite','hematite']:color={'malachite':'#3B9869','cassiterite':'#6D5743','hematite':'#7F6563'}[name]
            arguments=['--template',template,'--color',color]
            if name.endswith('_blank'):arguments+=['--secondary-color','#3B57F0']
            report=invoke(qube,name,'item',arguments)
            if name.endswith('_blank'):report=write_grid(qube,name,'item',head_from_template(report))
        else:report=write_grid(qube,name,'item',custom(name).json())
        completed['item/'+name]=report;REPORT.write_text(json.dumps(completed),encoding='utf-8')
        if index%12==0:print(f'Item icons: {index+1}/{len(names)} ({name})',flush=True)
    # World plant sprites and host/ore textures use the same identity and palette references.
    block_names=['fallen_sticks']+[s for s in names if s.startswith('wild_') and s not in ['wild_berries','wild_plants']]+['flax_crop','yarrow_crop','bramble_crop','limestone','malachite_ore','cassiterite_ore','hematite_ore']
    for name in block_names:
        if args.only and name not in args.only:continue
        if args.resume and 'block/'+name in completed:continue
        if name.endswith('_crop'):
            # Base model provides a complete texture binding. Stage planes reveal progressively more of the sprite.
            (ASSETS/'models/block'/f'{name}.json').write_text(json.dumps({'parent':'minecraft:block/cross','render_type':'minecraft:cutout','textures':{'cross':f'{NS}:block/{name}'}}),encoding='utf-8')
            for age in range(8):
                height=4+round(age*12/7);elements=[]
                for angle in [-45,45]:elements.append({'from':[0,0,8],'to':[16,height,8],'rotation':{'origin':[8,0,8],'axis':'y','angle':angle,'rescale':True},'shade':False,'faces':{d:{'texture':'#cross','uv':[0,16-height,16,16]} for d in ['north','south']}})
                (ASSETS/'models/block'/f'{name}_{age}.json').write_text(json.dumps({'render_type':'minecraft:cutout','textures':{'cross':f'{NS}:block/{name}','particle':f'{NS}:block/{name}'},'elements':elements}),encoding='utf-8')
        if name=='limestone':
            g=Grid('#BCB89F')
            g.p=[[3+(1 if (x*7+y*11)%17==0 else -1 if (x*13+y*3)%19==0 else 0) for x in range(16)] for y in range(16)]
            for y in [5,11]:
                for x in range(16):
                    if (x+y)%5:g.p[y][x]=2
            report=write_grid(qube,name,'block',g.json())
        elif name.endswith('_ore'):
            color={'malachite_ore':'#438F60','cassiterite_ore':'#71503B','hematite_ore':'#8C534A'}[name];host_color={'malachite_ore':'#BAB59E','cassiterite_ore':'#A68B7D','hematite_ore':'#77736C'}[name]
            report=invoke(qube,name,'block',['--template','ore','--color',color,'--secondary-color',host_color])
        else:report=write_grid(qube,name,'cutout',(custom(name) if name=='fallen_sticks' else plant(name,True)).json())
        completed['block/'+name]=report;REPORT.write_text(json.dumps(completed),encoding='utf-8')
        print(f'World texture: {name}',flush=True)
    print(f'Completed {len(completed)} Qube texture bindings. Reports: {REPORT}',flush=True)

if __name__=='__main__':main()
