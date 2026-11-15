"""Generate new 32px equipment art and mesh resources; no dependency textures are modified."""
from pathlib import Path
import json, random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_bioships'
TEX = ROOT / 'textures/item'
MODELS = ROOT / 'models/item'
TEX.mkdir(parents=True, exist_ok=True)
ink = '#251c2c'; plum = '#48293f'; skin = '#764252'; light = '#ae6873'
bone = '#d6c59f'; ivory = '#f2e5c6'; shadow = '#97816a'
teal = '#164b4c'; green = '#54b29b'; mint = '#b8ebc3'

# Each quarter of this newly painted atlas is a separate organic material.
atlas = Image.new('RGBA', (32, 32))
rng = random.Random(774)
palettes = [(plum, skin, '#623448'), (shadow, bone, ivory), (teal, '#286c67', green), ('#61333f', light, '#864b58')]
for tile, palette in enumerate(palettes):
    ox, oy = (tile % 2)*16, (tile // 2)*16
    for y in range(16):
        for x in range(16):
            value = palette[1 if rng.random() > .28 else 0]
            if (x + y//3) % 7 == 0: value = palette[2]
            atlas.putpixel((ox+x, oy+y), tuple(int(value[i:i+2], 16) for i in (1,3,5))+(255,))
ad = ImageDraw.Draw(atlas)
ad.line([(1,17),(4,19),(3,23),(9,26),(8,30)], fill=green)
ad.line([(3,17),(6,20),(5,23)], fill=mint)
ad.rectangle((2,18,3,19), fill=ivory)
atlas.save(TEX/'organic_equipment_atlas.png')

# Front-facing flat icon: folded cartilage strap, twin recessed ocular lenses.
goggles = Image.new('RGBA', (32,32))
d = ImageDraw.Draw(goggles)
d.polygon([(1,12),(5,9),(26,9),(30,12),(29,22),(25,24),(5,24),(1,21)], fill=ink)
d.polygon([(2,13),(6,10),(26,10),(29,13),(28,21),(25,23),(6,23),(2,20)], fill=plum)
d.line([(3,13),(7,11),(24,11),(28,13)], fill=light, width=1)
for x in (4,18):
    d.polygon([(x+2,10),(x+9,10),(x+11,13),(x+11,21),(x+8,24),(x+2,24),(x,21),(x,13)],fill=ink)
    d.polygon([(x+2,11),(x+8,11),(x+10,14),(x+10,20),(x+8,22),(x+2,22),(x+1,20),(x+1,14)],fill=bone)
    d.polygon([(x+3,13),(x+7,13),(x+9,15),(x+9,19),(x+7,21),(x+3,21),(x+2,19),(x+2,15)],fill=teal)
    d.rectangle((x+4,14,x+7,20),fill='#318a7a')
    d.line([(x+7,14),(x+5,16),(x+6,18),(x+5,20)],fill=green)
    d.rectangle((x+3,14,x+4,15),fill=ivory)
    d.point((x+7,19),fill=mint)
    d.line([(x+2,11),(x+7,11)],fill=ivory)
    d.line([(x+2,22),(x+8,22)],fill=shadow)
    d.rectangle((x+1,16,x+1,18),fill=light)
d.rectangle((14,14,17,17),fill=ink)
d.rectangle((14,14,17,15),fill=bone)
d.point((15,14),fill=ivory)
d.line([(1,17),(3,18)],fill=light)
goggles.save(TEX/'symbiotic_goggles_icon.png')

# An ossified, living pincer: diagonal handle with sinew bindings and a cartilage joint.
wrench = Image.new('RGBA',(32,32))
d = ImageDraw.Draw(wrench)
d.polygon([(4,26),(6,29),(10,28),(22,14),(18,10)],fill=ink)
d.polygon([(6,26),(8,27),(20,13),(18,12)],fill=bone)
d.line([(7,25),(18,13)],fill=ivory,width=1)
for x,y in [(8,24),(11,21),(14,18)]:
    d.polygon([(x-2,y-1),(x,y+1),(x+3,y-2),(x+1,y-4)],fill=plum)
    d.line([(x-1,y-1),(x+1,y)],fill=light)
d.polygon([(15,12),(14,8),(17,4),(21,2),(22,3),(19,6),(18,9),(22,12),(25,10),(27,6),(29,6),(29,11),(25,16),(20,17)],fill=ink)
d.polygon([(16,11),(16,8),(18,5),(21,4),(18,8),(19,11),(22,14),(26,11),(28,8),(27,12),(24,15),(20,15)],fill=bone)
d.line([(16,9),(18,6),(19,6)],fill=ivory)
d.line([(24,14),(27,11)],fill=shadow)
d.polygon([(17,12),(20,11),(23,14),(21,17),(18,16)],fill=plum)
d.rectangle((19,13,21,15),fill=teal)
d.point((20,13),fill=mint)
d.point((19,14),fill=green)
d.rectangle((5,26,6,27),fill=ivory)
wrench.save(TEX/'ossified_wrench_icon.png')

uv = {'skin':[0,0,8,8], 'bone':[8,0,16,8], 'lens':[0,8,8,16], 'sinew':[8,8,16,16]}
def box(lo,hi,material,rotation=None):
    result={'from':lo,'to':hi,'faces':{face:{'uv':uv[material],'texture':'#atlas'} for face in ['north','south','west','east','up','down']}}
    if rotation: result['rotation']=rotation
    return result

def save(name, value): (MODELS/(name+'.json')).write_text(json.dumps(value,indent=2)+'\n')
base={'textures':{'atlas':'create_bioships:item/organic_equipment_atlas','particle':'create_bioships:item/organic_equipment_atlas'}}
parts=[]
for x in (1.6,8.6):
    # Bevelled rings retain an actual lens recess and don't cover the entire head with a cuboid.
    parts += [box([x+1,5.6,.8],[x+4.8,6.4,2],'skin'),box([x+1,10.4,.8],[x+4.8,11.2,2],'skin'),
              box([x,6.6,.8],[x+.9,10.2,2],'skin'),box([x+4.9,6.6,.8],[x+5.8,10.2,2],'skin')]
    for dx,dy in [(.4,6.1),(4.55,6.1),(.4,10.1),(4.55,10.1)]:
        parts.append(box([x+dx,dy,.65],[x+dx+.8,dy+.65,2],'bone'))
    parts += [box([x+1.05,6.45,1],[x+4.75,10.35,1.5],'lens'),
              box([x+1.4,9.7,.8],[x+2.2,10.05,1],'bone')]
parts += [box([7.15,7.9,1.1],[8.9,8.9,2.2],'bone'),box([7.45,7.35,1.6],[8.6,8,2.4],'sinew'),
          box([.8,7.2,2],[1.65,9.2,15.2],'sinew'),box([14.35,7.2,2],[15.2,9.2,15.2],'sinew'),
          box([1.1,7.25,14.5],[14.9,9.1,15.3],'skin'),box([1.05,7.65,5],[1.8,8.75,6.3],'bone'),
          box([14.2,7.65,5],[14.95,8.75,6.3],'bone')]
head={**base,'elements':parts,'display':{'head':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}}
save('bioship_goggles_worn',head)
save('bioship_goggles',{'parent':'minecraft:item/generated','textures':{'layer0':'create_bioships:item/symbiotic_goggles_icon'}})

parts=[box([6.9,1,7],[9.1,11,9],'skin'),box([6.5,.5,6.6],[9.5,2.1,9.4],'bone'),
       box([4.2,9.6,6.4],[11.8,11.4,9.6],'bone'),box([3.4,11,6.4],[5.6,14.8,9.6],'bone'),
       box([10.4,11,6.4],[12.6,14.8,9.6],'bone'),
       box([4,14,6.4],[6.1,16,9.6],'bone',{'origin':[5,14,8],'axis':'z','angle':-22.5}),
       box([9.9,14,6.4],[12,16,9.6],'bone',{'origin':[11,14,8],'axis':'z','angle':22.5}),
       box([6.1,9.1,6],[9.9,11.8,10],'sinew'),box([7.1,9.8,5.8],[8.9,11.2,6.2],'lens')]
for y in (3,5,7): parts.append(box([6.65,y,6.75],[9.35,y+.65,9.25],'sinew'))
held={**base,'elements':parts,'display':{
 'thirdperson_righthand':{'rotation':[0,90,-25],'translation':[0,1,0],'scale':[.8,.8,.8]},
 'thirdperson_lefthand':{'rotation':[0,-90,25],'translation':[0,1,0],'scale':[.8,.8,.8]},
 'firstperson_righthand':{'rotation':[0,45,-20],'translation':[1,2,0],'scale':[.8,.8,.8]},
 'firstperson_lefthand':{'rotation':[0,-45,20],'translation':[1,2,0],'scale':[.8,.8,.8]},
 'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.6,.6,.6]}}}
save('bioship_wrench_held',held)
save('bioship_wrench',{'parent':'minecraft:item/handheld','textures':{'layer0':'create_bioships:item/ossified_wrench_icon'}})
