"""Regenerates wiki/Industrial-Recipes.md from the recipe block in Industry.java, so the tables cannot drift from the code.

Run from the project root: python wiki/_tools/recipe-tables.py
"""
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[2]
SOURCE=(ROOT/'src/main/java/dev/qube/spawn_9b5fbb9/industry/Industry.java').read_text(encoding='utf-8')
SKILLS={'C':'Construction','E':'Engineering','S':'Smithing','W':'Weaving'}
STATIONS=[('workshop','Workshop'),('assembly_yard','Assembly yard'),('niter_bed','Nitre bed'),('powder_mill','Powder mill'),('foundry','Foundry'),
          ('chemical_works','Chemical works'),('machine_shop','Machine shop'),('electronics_works','Electronics works')]
DEFAULT_CREW={'assembly_yard':8,'workshop':2,'powder_mill':2,'foundry':8}

def split(text):
    """Top-level comma split of an argument list."""
    parts=[];depth=0;current='';quoted=False
    for ch in text:
        if ch=='"':quoted=not quoted
        if not quoted:
            if ch=='(':depth+=1
            elif ch==')':depth-=1
            elif ch==',' and depth==0:parts.append(current.strip());current='';continue
        current+=ch
    if current.strip():parts.append(current.strip())
    return parts
def ident(token):
    token=token.strip()
    if token.startswith('V+'):return 'minecraft:'+token[2:].strip('"')
    return token.strip('"')
def name(item):
    if item.startswith('vehicle:'):return item[8:].replace('_',' ')+' (vehicle)'
    if item.startswith('#'):return 'any '+item.split(':')[1].replace('_',' ')
    return item.split(':')[-1].replace('_',' ').replace('lithography stepper','mask aligner')
def bill(token):
    inner=token[token.index('(')+1:token.rindex(')')];parts=split(inner)
    return [(ident(parts[i]),int(parts[i+1])) for i in range(0,len(parts),2)]
def listing(pairs):return ', '.join(f'{count:,} {name(item)}' for item,count in pairs)

recipes=[]
for line in SOURCE.splitlines():
    line=line.strip()
    match=re.match(r'(make|passive)\((.*)\);\s*$',line)
    if not match:continue
    kind,args=match.group(1),split(match.group(2))
    if kind=='passive':
        key,station,output,amount,days,inputs=args[0].strip('"'),args[1].strip('"'),ident(args[2]),int(args[3]),float(args[4]),bill(args[5])
        recipes.append(dict(key=key,station=station,output=output,amount=amount,by=[],time=f'{days:g} days unattended',skill='none',needs=[],inputs=inputs,crew=''))
        continue
    key,station,output,amount=args[0].strip('"'),args[1].strip('"'),ident(args[2]),int(args[3]);rest=args[4:];by=[]
    if rest[0].startswith('bill('):by=bill(rest[0]);rest=rest[1:]
    hours=float(rest[0]);skill=SKILLS[rest[1]];minimum=int(rest[2]);inputs=bill(rest[3]);needs=[n.strip('"') for n in rest[4:]]
    crew=DEFAULT_CREW.get(station,3)
    for need in list(needs):
        if need.startswith('crew:'):crew=int(need[5:]);needs.remove(need)
    shown=[]
    for need in needs:
        if need.startswith('electric:'):shown.append(need[9:]+' kW')
        elif need.startswith('equip:'):shown.append(need[6:].replace('_',' ').replace('lithography stepper','mask aligner'))
        elif need.startswith('firebrick:'):shown.append(need[10:]+' firebrick')
        elif need.startswith('chimney:'):shown.append(need[8:]+'-high chimney')
        elif need.startswith('lead_chamber:'):shown.append(need[13:]+' lead chambers')
        elif need=='blast':shown.append('blast (water wheel or engine)')
        elif need=='engine':shown.append('blowing engine')
        else:shown.append(need)
    recipes.append(dict(key=key,station=station,output=output,amount=amount,by=by,time=f'{hours:g}',skill=f'{skill} {minimum}' if minimum else skill+' 0',needs=shown,inputs=inputs,crew=str(crew)))

out=['Every industrial recipe, generated from the code. Quantities are per batch: kilograms of metal, mineral and chemical, 25 kg sacks of',
     'coal, coke and charcoal, litres of liquid, metres of wire, single bricks, and cubic-metre blocks. Time is man-hours (1,000 ticks of',
     'the game clock each), shared between the hands on the job up to the crew shown. "Also needs" lists what the site must have beyond',
     'the standing requirements of that kind of works, which are given on [[Industrial-Manufacturing]]. The recipe id is what',
     '`/survival works set <recipe>` takes.','']
for station,title in STATIONS:
    rows=[r for r in recipes if r['station']==station]
    if not rows:continue
    out+=[f'## {title}','','| Recipe id | Makes | From | By-product | Man-hours | Crew | Skill | Also needs |','| --- | --- | --- | --- | --- | --- | --- | --- |']
    for r in rows:
        out.append(f"| `{r['key']}` | {r['amount']:,} {name(r['output'])} | {listing(r['inputs'])} | {listing(r['by'])} | {r['time']} | {r['crew']} | {r['skill']} | {', '.join(r['needs'])} |")
    out.append('')
(ROOT/'wiki/Industrial-Recipes.md').write_text('\n'.join(out),encoding='utf-8')
print(len(recipes),'recipes written')
