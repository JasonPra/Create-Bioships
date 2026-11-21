"""Check authored furniture, mouth and flipper models for Z-fighting surfaces.

Run from any directory with Python 3; no Minecraft runtime or third-party modules
are needed. Quads are compared after element rotation, in model-pixel units.
"""

import itertools
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / "src/main/resources/assets/create_bioships/models"
PLANE_TOLERANCE = 0.025
AREA_TOLERANCE = 1e-5
FACE_AXIS = {"east": 0, "west": 0, "up": 1, "down": 1, "south": 2, "north": 2}


def subtract(a, b):
    return [x - y for x, y in zip(a, b)]


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]]


def signed_area(polygon):
    return sum(a[0] * b[1] - b[0] * a[1]
               for a, b in zip(polygon, polygon[1:] + polygon[:1])) / 2


def intersection_area(first, second):
    """Clip convex face projections; touching edges have zero overlap area."""
    if signed_area(first) < 0:
        first = first[::-1]
    if signed_area(second) < 0:
        second = second[::-1]
    output = first
    for start, end in zip(second, second[1:] + second[:1]):
        incoming, output = output, []
        if not incoming:
            break

        def side(point):
            return ((end[0] - start[0]) * (point[1] - start[1])
                    - (end[1] - start[1]) * (point[0] - start[0]))

        for p, q in zip(incoming, incoming[1:] + incoming[:1]):
            sp, sq = side(p), side(q)
            if sp >= -1e-9:
                output.append(p)
            if (sp < 0) != (sq < 0):
                t = sp / (sp - sq)
                output.append([p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])])
    return abs(signed_area(output)) if len(output) > 2 else 0


def model_faces(model):
    result = []
    for index, element in enumerate(model.get("elements", [])):
        low, high = element["from"], element["to"]
        for face in element["faces"]:
            axis = FACE_AXIS[face]
            plane = (high if face in ("east", "up", "south") else low)[axis]
            other = [coordinate for coordinate in range(3) if coordinate != axis]
            points = []
            for b, c in ((0, 0), (1, 0), (1, 1), (0, 1)):
                point = list(low)
                point[axis] = plane
                point[other[0]] = (high if b else low)[other[0]]
                point[other[1]] = (high if c else low)[other[1]]
                rotation = element.get("rotation")
                if rotation:
                    pivot = rotation["origin"]
                    rotating_axis = "xyz".index(rotation["axis"])
                    # Cyclic axes preserve right-handed rotation, including Y.
                    u, v = (rotating_axis + 1) % 3, (rotating_axis + 2) % 3
                    x, y = point[u] - pivot[u], point[v] - pivot[v]
                    angle = math.radians(rotation["angle"])
                    scale = 1 / math.cos(angle) if rotation.get("rescale") else 1
                    point[u] = pivot[u] + scale * (x * math.cos(angle) - y * math.sin(angle))
                    point[v] = pivot[v] + scale * (x * math.sin(angle) + y * math.cos(angle))
                points.append(point)
            normal = cross(subtract(points[1], points[0]), subtract(points[2], points[1]))
            length = math.sqrt(dot(normal, normal))
            if length <= 1e-10:
                raise AssertionError(f"Degenerate visible face: element {index} {face}")
            result.append((index, face, points, [value / length for value in normal]))
    return result


def overlaps(model, tolerance=PLANE_TOLERANCE):
    return face_overlaps(model_faces(model), tolerance)


def face_overlaps(faces, tolerance=PLANE_TOLERANCE):
    conflicts = []
    for first, second in itertools.combinations(faces, 2):
        if first[0] == second[0] or abs(dot(first[3], second[3])) < 1 - 1e-8:
            continue
        distance = abs(dot(first[3], subtract(second[2][0], first[2][0])))
        if distance > tolerance:
            continue
        normal_axis = max(range(3), key=lambda axis: abs(first[3][axis]))
        axes = [axis for axis in range(3) if axis != normal_axis]
        projected = [[[p[axis] for axis in axes] for p in face[2]] for face in (first, second)]
        if intersection_area(*projected) > AREA_TOLERANCE:
            conflicts.append((first[:2], second[:2], round(distance, 7)))
    return conflicts


def transformed_part(name, index_offset, yaw=0, pitch=0, offset=(0, 0, 0), root=(0, 0, 0)):
    """Match the nested jaw/fang transforms used by ArachnidMouthRenderer."""
    def transform(point, vector=False):
        x, y, z = point
        angle = math.radians(pitch)
        y, z = y * math.cos(angle) - z * math.sin(angle), y * math.sin(angle) + z * math.cos(angle)
        if not vector:
            x, y, z = x + offset[0], y + offset[1], z + offset[2]
        angle = math.radians(yaw)
        x, z = x * math.cos(angle) + z * math.sin(angle), -x * math.sin(angle) + z * math.cos(angle)
        return [x, y, z] if vector else [x + root[0], y + root[1], z + root[2]]

    model = json.loads((MODELS / "block" / (name + ".json")).read_text())
    return [(index + index_offset, face, [transform(point) for point in points], transform(normal, True))
            for index, face, points, normal in model_faces(model)]


def check_articulated_joins():
    # Include straight paddle joins and sampled mouth poses, rather than checking
    # each independently rendered part in isolation.
    paddles = []
    for section in range(3):
        paddles.extend(transformed_part(f"flipper_paddle_{section}", section * 1000,
                                       offset=(0, 0, section * 10.24)))
    assert not face_overlaps(paddles), "Straight paddle sections have overlapping end caps"
    for step in range(21):
        opening = step / 20
        faces = transformed_part("arachnid_maw", 0)
        for index, side_name in enumerate(("left", "right")):
            side = 1 if index == 0 else -1
            yaw = side * (-18 + 50 * opening)
            root = (3 if index == 0 else 13, 6, .5)
            faces.extend(transformed_part(f"arachnid_{side_name}_jaw", 1000 + index * 2000,
                                          yaw=yaw, root=root))
            faces.extend(transformed_part(f"arachnid_{side_name}_fang", 2000 + index * 2000,
                                          yaw=yaw, pitch=18 + 32 * (1 - opening),
                                          offset=(side, -1, -10), root=root))
        conflicts = face_overlaps(faces)
        assert not conflicts, f"Mandible opening {opening}: {conflicts}"


def main():
    paths = set()
    for pattern in ("ossuary_*.json", "flipper*.json", "armored_maw*.json",
                    "arachnid_*jaw.json", "arachnid_*fang.json", "arachnid_maw.json"):
        paths.update((MODELS / "block").glob(pattern))
    paths.update([MODELS / "item/cerebral_codex.json", MODELS / "item/arachnid_maw.json"])
    failures = []
    for path in sorted(paths):
        conflicts = overlaps(json.loads(path.read_text()))
        if conflicts:
            failures.append(f"{path.relative_to(MODELS)}: {conflicts}")
    if failures:
        raise SystemExit("\n".join(failures))
    check_articulated_joins()
    print(f"PASS: {len(paths)} models have no overlapping parallel faces within {PLANE_TOLERANCE} pixels")


if __name__ == "__main__":
    main()
