"""Generate new seamless purple armor tiles; no existing textures are modified."""
from pathlib import Path
from PIL import Image
import json, random

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_bioships'
PALETTE = [(69, 20, 54), (75, 21, 55), (85, 26, 65), (88, 27, 68),
           (91, 29, 71), (106, 35, 83), (125, 44, 96)]

def generate():
    variants = []
    tiles = []
    for variant in range(8):
        rng = random.Random(7283 + variant)
        image = Image.new('RGBA', (16, 16))
        pixels = image.load()
        # The shared perimeter is symmetric under quarter turns and reflection.
        # Interiors vary; there is no painted outline or recognizable central emblem.
        coarse = [[rng.randrange(1, 5) for _ in range(4)] for _ in range(4)]
        for y in range(16):
            for x in range(16):
                a, b = sorted((min(x, 15-x), min(y, 15-y)))
                if a == 0:
                    shade = [2, 3, 3, 2, 2, 3, 4, 3][b]
                else:
                    shade = max(0, min(6, coarse[y//4][x//4] + rng.choice([-1, 0, 0, 0, 1])))
                pixels[x,y] = (*PALETTE[shade], 255)
        name = f'living_hull_tier_2_{variant}'
        image.save(ASSETS / 'textures/block' / f'{name}.png')
        tiles.append(image)
        (ASSETS / 'models/block' / f'{name}.json').write_text(json.dumps({
            'parent': 'minecraft:block/cube_all',
            'textures': {'all': f'create_bioships:block/{name}'}}, indent=2)+'\n')
        for angle in (0, 90, 180, 270):
            variants.append({'model': f'create_bioships:block/{name}', 'y': angle, 'uvlock': True})
    # Every exposed edge matches every neighbor, including randomized rotations.
    rotated = [im.rotate(angle) for im in tiles for angle in (0,90,180,270)]
    for first in rotated:
        for second in rotated:
            assert [first.getpixel((15,y)) for y in range(16)] == [second.getpixel((0,y)) for y in range(16)]
            assert [first.getpixel((x,15)) for x in range(16)] == [second.getpixel((x,0)) for x in range(16)]
    assert len({im.tobytes() for im in tiles}) == 8
    (ASSETS / 'blockstates/living_hull_tier_2.json').write_text(json.dumps({'variants': {'': variants}}, indent=2)+'\n')
    (ASSETS / 'models/item/living_hull_tier_2.json').write_text(json.dumps({'parent': 'create_bioships:block/living_hull_tier_2_0'}, indent=2)+'\n')
    loot = ASSETS.parents[1] / 'data/create_bioships/loot_table/blocks/living_hull_tier_2.json'
    loot.write_text(json.dumps({'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item','name': 'create_bioships:living_hull_tier_2'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}, indent=2)+'\n')
    lang = ASSETS / 'lang/en_us.json'
    data = json.loads(lang.read_text())
    data['block.create_bioships.living_hull_tier_2'] = 'Living Hull Armor II'
    lang.write_text(json.dumps(data, indent=2, ensure_ascii=False)+'\n')

if __name__ == '__main__':
    generate()
    print('Generated 8 distinct tiles; all 32 rotations passed pairwise seam checks.')
