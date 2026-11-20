"""Generate authored cuboid furniture models with face-sized UVs, loot, and recipes."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/create_bioships'
DATA = ROOT / 'data/create_bioships'
TEXTURES = dict(bone='minecraft:block/bone_block_side', marrow='minecraft:block/bone_block_top',
                flesh='biomancy:block/packed_flesh', chitin='create_bioships:block/living_core',
                particle='minecraft:block/bone_block_side')
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2)+'\n')
def cuboid(start, end, texture):
    x,y,z=start; X,Y,Z=end
    uv={'up':[x,z,X,Z], 'down':[x,z,X,Z], 'north':[x,16-Y,X,16-y],
        'south':[x,16-Y,X,16-y], 'east':[z,16-Y,Z,16-y], 'west':[z,16-Y,Z,16-y]}
    return dict(from_=start, to=end, faces={f:dict(texture='#'+texture,uv=v) for f,v in uv.items()})
def model(name, boxes, **extra):
    elements=[]
    for a,b,t in boxes:
        element=cuboid(a,b,t);element['from']=element.pop('from_');elements.append(element)
    write(ASSETS / f'models/block/{name}.json', dict(textures=TEXTURES,elements=elements,**extra))
def item(name, parent=None):
    write(ASSETS/f'models/item/{name}.json',dict(parent='create_bioships:block/'+(parent or name)))
def loot(name):
    write(DATA/f'loot_table/blocks/{name}.json',dict(type='minecraft:block',pools=[dict(rolls=1,entries=[dict(type='minecraft:item',name='create_bioships:'+name)],conditions=[dict(condition='minecraft:survives_explosion')])]))
def recipe(name, pattern, key, count=1):
    write(DATA/f'recipe/{name}.json',dict(type='minecraft:crafting_shaped',pattern=pattern,key={k:dict(item=v) for k,v in key.items()},result=dict(id='create_bioships:'+name,count=count)))
def title(name, text):
    path=ASSETS/'lang/en_us.json';data=json.loads(path.read_text());data['block.create_bioships.'+name]=text;write(path,data)
def table():
    model('ossuary_table_top', [([0,13,0],[16,16,16],'chitin'),([0,12,0],[16,13,16],'bone'),([3,15.9,3],[13,16.1,13],'marrow')])
    # Separate corner supports make straight, L-shaped and rectangular assemblies work alike.
    parts=[dict(apply=dict(model='create_bioships:block/ossuary_table_top'))]
    for ns,z in [('north',1),('south',12)]:
        for ew,x in [('west',1),('east',12)]:
            name=f'ossuary_table_leg_{ns}_{ew}'
            model(name,[([x,0,z],[x+3,12,z+3],'bone'),([x-.5,1,z-.5],[x+3.5,3,z+3.5],'chitin'),([x-.2,9,z-.2],[x+3.2,11,z+3.2],'flesh')])
            parts.append(dict(when={ns:'false',ew:'false'},apply=dict(model='create_bioships:block/'+name)))
    write(ASSETS/'blockstates/ossuary_table.json',dict(multipart=parts))
    boxes=[([0,13,0],[16,16,16],'chitin'),([0,12,0],[16,13,16],'bone'),([3,15.9,3],[13,16.1,13],'marrow')]
    boxes += [([x,0,z],[x+3,12,z+3],'bone') for x in [1,12] for z in [1,12]]
    model('ossuary_table_inventory',boxes,parent='minecraft:block/block');item('ossuary_table','ossuary_table_inventory');loot('ossuary_table')
    recipe('ossuary_table',['CCC','BFB','B B'],dict(C='create_bioships:chitin_armor',B='minecraft:bone',F='biomancy:packed_flesh'),2)
    title('ossuary_table','Ossuary Table')
def horizontal(name, canonical='south'):
    angles={'south':0,'west':90,'north':180,'east':270}
    offset=angles[canonical]
    write(ASSETS/f'blockstates/{name}.json',dict(variants={f'facing={d}':dict(model='create_bioships:block/'+name,y=(a-offset)%360) for d,a in angles.items()}))
def seating():
    boxes=[([2,6,2],[14,8,14],'chitin'),([3,8,4],[13,9,13],'flesh'),([2,9,1],[14,16,4],'chitin'),([4,10,3.9],[12,15,4.3],'flesh')]
    for x in [2,11]:
        for z in [2,11]: boxes += [([x,0,z],[x+3,6,z+3],'bone')]
    boxes += [([2,8,1],[4,16,4],'bone'),([12,8,1],[14,16,4],'bone'),([4,14,1],[12,16,3],'marrow')]
    model('ossuary_chair',boxes,parent='minecraft:block/block');horizontal('ossuary_chair');item('ossuary_chair');loot('ossuary_chair');title('ossuary_chair','Ossuary Chair')
    recipe('ossuary_chair',['B B','CFC','B B'],dict(B='minecraft:bone',C='create_bioships:chitin_armor',F='biomancy:packed_flesh'),2)
    boxes=[([1,0,1],[15,2,15],'chitin'),([3,2,3],[13,4,13],'bone'),([6,4,5],[10,13,10],'bone'),([5,5,6],[11,11,9],'flesh'),([1,12,2],[15,14,15],'chitin'),([1,14,2],[15,18,6],'bone'),([1,12,6],[15,16,10],'bone'),([1,10,10],[15,14,15],'bone'),([2,14,13],[14,15,15],'flesh')]
    model('ossuary_lectern',boxes,parent='minecraft:block/block');horizontal('ossuary_lectern','south');item('ossuary_lectern');loot('ossuary_lectern');title('ossuary_lectern','Ossuary Reading Stand')
    recipe('ossuary_lectern',['CCC',' B ','BFB'],dict(B='minecraft:bone',C='create_bioships:chitin_armor',F='biomancy:packed_flesh'))
def storage():
    TEXTURES['books']='minecraft:block/bookshelf'
    frame=[([0,0,0],[16,2,16],'chitin'),([0,14,0],[16,16,16],'chitin'),([0,2,0],[2,14,16],'bone'),([14,2,0],[16,14,16],'bone'),([2,2,0],[14,14,2],'chitin'),([2,7,2],[14,9,16],'bone')]
    books=[]
    for y in [2,9]:
        for x in range(2,14,2):
            books += [([x,y,4],[x+1.7,y+4.5+(x%3)*.2,15],'books')]
    model('ossuary_bookcase',frame+books,parent='minecraft:block/block');horizontal('ossuary_bookcase');item('ossuary_bookcase');loot('ossuary_bookcase');title('ossuary_bookcase','Ossuary Bookcase')
    recipe('ossuary_bookcase',['BCB','RRR','BCB'],dict(B='minecraft:bone',C='create_bioships:chitin_armor',R='minecraft:book'))
    model('ossuary_archive_frame',frame,parent='minecraft:block/block')
    parts=[]
    for d,angle in [('south',0),('west',90),('north',180),('east',270)]:
        parts.append(dict(when=dict(facing=d),apply=dict(model='create_bioships:block/ossuary_archive_frame',y=angle)))
        for i in range(6):
            parts.append(dict(when={'facing':d,f'slot_{i}_occupied':'true'},apply=dict(model=f'create_bioships:block/ossuary_archive_book_{i}',y=angle)))
    for i in range(6):
        x=2+4*(i%3);y=9 if i<3 else 2
        model(f'ossuary_archive_book_{i}',[([x,y,4],[x+3,y+5,15],'books')])
    write(ASSETS/'blockstates/ossuary_archive.json',dict(multipart=parts))
    model('ossuary_archive_inventory',frame+[([2,9,4],[5,14,15],'books'),([10,2,4],[13,7,15],'books')],parent='minecraft:block/block')
    item('ossuary_archive','ossuary_archive_inventory');loot('ossuary_archive');title('ossuary_archive','Chiseled Ossuary Bookcase')
    recipe('ossuary_archive',['BCB','B B','BCB'],dict(B='minecraft:bone',C='create_bioships:chitin_armor'))
    cabinet=[([0,0,0],[16,16,15],'chitin'),([0,0,15],[2,16,16],'bone'),([14,0,15],[16,16,16],'bone'),([2,0,15],[14,2,16],'bone'),([2,14,15],[14,16,16],'bone'),([2,2,15],[14,14,15.5],'flesh'),([7,2,15.5],[9,14,16],'bone'),([5,7,15.5],[6.5,9,16.5],'marrow'),([9.5,7,15.5],[11,9,16.5],'marrow')]
    model('ossuary_cabinet',cabinet,parent='minecraft:block/block');item('ossuary_cabinet');loot('ossuary_cabinet');title('ossuary_cabinet','Ossuary Cabinet')
    variants={f'facing={d}':dict(model='create_bioships:block/ossuary_cabinet',y=a) for d,a in [('south',0),('west',90),('north',180),('east',270)]}
    variants['facing=up']=dict(model='create_bioships:block/ossuary_cabinet',x=270)
    variants['facing=down']=dict(model='create_bioships:block/ossuary_cabinet',x=90)
    write(ASSETS/'blockstates/ossuary_cabinet.json',dict(variants=variants))
    recipe('ossuary_cabinet',['BCB','F F','BCB'],dict(B='minecraft:bone',C='create_bioships:chitin_armor',F='biomancy:packed_flesh'))
    write(ROOT/'data/minecraft/tags/block/enchantment_power_provider.json',dict(replace=False,values=['create_bioships:ossuary_bookcase']))
if __name__=='__main__':
    table();seating();storage()
