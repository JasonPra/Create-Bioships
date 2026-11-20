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
if __name__=='__main__': table()
