"""Remove buried/copied faces from opaque, unrotated cuboid model assemblies.

Coordinates are model pixels (16 per block). Later boxes own shared exterior
planes so authored trim retains its material. Split quads retain their UV scale;
this is an asset-generation operation, never per-frame rendering work.
"""

from copy import deepcopy

FACE_AXIS = {"west": 0, "east": 0, "down": 1, "up": 1, "north": 2, "south": 2}
POSITIVE_FACES = {"east", "up", "south"}
GEOMETRY_EPSILON = 1e-7


def subtract_rectangle(rectangle, cut):
    """Return the disjoint remainder of a face after covering a rectangle."""
    left, bottom, right, top = rectangle
    x, y, end_x, end_y = cut
    x, y = max(left, x), max(bottom, y)
    end_x, end_y = min(right, end_x), min(top, end_y)
    if end_x - x < GEOMETRY_EPSILON or end_y - y < GEOMETRY_EPSILON:
        return [rectangle]
    candidates = [
        (left, bottom, x, top), (end_x, bottom, right, top),
        (x, bottom, end_x, y), (x, end_y, end_x, top),
    ]
    return [
        part for part in candidates
        if part[2] - part[0] > GEOMETRY_EPSILON
        and part[3] - part[1] > GEOMETRY_EPSILON
    ]


def cropped_face(element, face, data, patch):
    """Follow FaceBakery's direction-dependent UV orientation when trimming."""
    axis = FACE_AXIS[face]
    u_axis = 0 if axis != 0 else 2
    v_axis = 2 if axis == 1 else 1
    uv = data["uv"]
    crop = []
    for coordinate, reverse, low, high in [
        (u_axis, face in ("north", "east"), uv[0], uv[2]),
        (v_axis, face != "up", uv[1], uv[3]),
    ]:
        start, end = element["from"][coordinate], element["to"][coordinate]
        first = (patch["from"][coordinate] - start) / (end - start)
        last = (patch["to"][coordinate] - start) / (end - start)
        if reverse:
            first, last = 1 - last, 1 - first
        crop.append((low + (high - low) * first, low + (high - low) * last))
    result = deepcopy(data)
    result["uv"] = [round(value, 6) for value in
                    (crop[0][0], crop[1][0], crop[0][1], crop[1][1])]
    return result


def exterior_surfaces(model):
    """Keep the solid silhouette and emit only its visible material surfaces.

    This intentionally rejects rotated boxes: their overlap is not rectangular
    in local face coordinates. Clean independently articulated parts separately.
    """
    original = model["elements"]
    if any("rotation" in element for element in original):
        raise ValueError("Clean cuboids before applying independent part rotations")
    result = []
    for index, element in enumerate(original):
        kept = deepcopy(element)
        kept["faces"] = {}
        patches = []
        for face, data in element["faces"].items():
            axis = FACE_AXIS[face]
            positive = face in POSITIVE_FACES
            plane = element["to" if positive else "from"][axis]
            axes = [coordinate for coordinate in range(3) if coordinate != axis]
            a, b = axes
            rectangle = (element["from"][a], element["from"][b],
                         element["to"][a], element["to"][b])
            pieces = [rectangle]
            for other_index, other in enumerate(original):
                if index == other_index:
                    continue
                low, high = other["from"][axis], other["to"][axis]
                if positive:
                    inside = low - GEOMETRY_EPSILON <= plane < high - GEOMETRY_EPSILON
                else:
                    inside = low + GEOMETRY_EPSILON < plane <= high + GEOMETRY_EPSILON
                shared_exterior = (
                    abs(plane - (high if positive else low)) < GEOMETRY_EPSILON
                    and other_index > index
                )
                # Open-backed shells and single-face throat quads are not solid
                # occluders through a deliberately omitted face.
                if (inside or shared_exterior) and face in other["faces"]:
                    cut = (other["from"][a], other["from"][b],
                           other["to"][a], other["to"][b])
                    pieces = [part for piece in pieces
                              for part in subtract_rectangle(piece, cut)]
            if pieces == [rectangle]:
                kept["faces"][face] = deepcopy(data)
                continue
            for piece in pieces:
                patch = deepcopy(element)
                patch["from"][axis] = patch["to"][axis] = plane
                for coordinate, local_axis in enumerate(axes):
                    patch["from"][local_axis] = piece[coordinate]
                    patch["to"][local_axis] = piece[coordinate + 2]
                patch["faces"] = {face: cropped_face(element, face, data, patch)}
                patches.append(patch)
        if kept["faces"]:
            result.append(kept)
        result.extend(patches)
    model["elements"] = result
    return model
