"""Extend the exact approved scene and independently measure the saved .blend.

Run actionassist-2x.py / actionassist-3x.py with headless Blender, -noaudio,
Cycles CPU, two render threads. The source scene is packed and copied locally.
No original scene object, camera, light, material or world node is edited.
"""
import hashlib
import json
import math
import time
from pathlib import Path

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

ROOT = Path(__file__).resolve().parent


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def mesh_hash(obj):
    return hashlib.sha256(repr((
        [tuple(v.co) for v in obj.data.vertices],
        [tuple(p.vertices) for p in obj.data.polygons],
        [[tuple(uv.uv) for uv in layer.data] for layer in obj.data.uv_layers],
        [m.name for m in obj.data.materials])).encode()).hexdigest()


def value(v):
    if isinstance(v, (str, int, float, bool)) or v is None:
        return v
    try:
        return [value(item) for item in v]
    except TypeError:
        return str(v)


def nodes(tree):
    if tree is None:
        return None
    return {'nodes': [{'name': n.name, 'type': n.bl_idname,
                       'inputs': {s.name: value(s.default_value) for s in n.inputs if hasattr(s, 'default_value')},
                       'image_sha256': hashlib.sha256(bytes(n.image.packed_file.data)).hexdigest()
                       if n.type == 'TEX_IMAGE' and n.image and n.image.packed_file else None,
                       'interpolation': getattr(n, 'interpolation', None)} for n in tree.nodes],
            'links': [(l.from_node.name, l.from_socket.name, l.to_node.name, l.to_socket.name) for l in tree.links]}


def snapshot():
    bpy.context.view_layer.update()
    scene = bpy.context.scene
    result = {}
    for obj in scene.objects:
        row = {'matrix_world': [list(v) for v in obj.matrix_world],
               'location': list(obj.location), 'rotation': list(obj.rotation_euler),
               'scale': list(obj.scale), 'parent': obj.parent.name if obj.parent else None,
               'shadow': obj.visible_shadow, 'hide_render': obj.hide_render, 'type': obj.type}
        if obj.type == 'MESH':
            row['mesh_sha256'] = mesh_hash(obj)
        if obj.type == 'CAMERA':
            row['camera'] = [obj.data.type, obj.data.ortho_scale, obj.data.shift_x, obj.data.shift_y]
        if obj.type == 'LIGHT':
            row['light'] = [obj.data.type, obj.data.energy, list(obj.data.color),
                            getattr(obj.data, 'size', None), nodes(obj.data.node_tree)]
        result[obj.name] = row
    return {'objects': result, 'world': nodes(scene.world.node_tree),
            'materials': {m.name: nodes(m.node_tree) for m in bpy.data.materials}}


def points(objects):
    return [obj.matrix_world @ v.co for obj in objects for v in obj.data.vertices]


def screen(scene, point):
    p = world_to_camera_view(scene, scene.camera, point)
    return [1024 * p.x, 1024 * (1 - p.y)]


def verify(multiplier, baseline, dataset):
    scene = bpy.context.scene
    current = snapshot()
    assert current['world'] == baseline['world']
    assert current['materials'] == baseline['materials']
    for name, row in baseline['objects'].items():
        assert current['objects'][name] == row, ('Original object changed', name)
    assert len(current['objects']) - len(baseline['objects']) == 52 * (multiplier - 1)
    pebbles = [obj for obj in scene.objects if obj.get('role') == 'pebble']
    blocks = [obj for obj in scene.objects if obj.get('role') == 'dirt_block']
    player = bpy.data.objects['Player using supplied skin']
    player_meshes = [obj for obj in player.children_recursive if obj.type == 'MESH']
    meshes = [obj for obj in scene.objects if obj.type == 'MESH']
    assert set(meshes) == set(pebbles + blocks + player_meshes)
    assert len(pebbles) == 52 * multiplier and len(blocks) == 25
    counts = {'total': len(pebbles), 'resting': sum(obj['state'] == 'resting' for obj in pebbles),
              'airborne': sum(obj['state'] == 'airborne' for obj in pebbles)}
    assert counts == dataset['count']
    by_id = {obj['instance']: obj for obj in pebbles}
    assert len(by_id) == len(pebbles)
    radii, resting_gaps, instance_proof = [], [], []
    for row in dataset['pebbles']:
        obj = by_id[row['instance']]
        original = by_id[row['source_instance']]
        assert obj.data == original.data
        assert (obj.location - Vector(row['mesh_position'])).length < 1e-6
        assert (Vector(obj.rotation_euler) - Vector(row['rotation_xyz_radians'])).length < 1e-6
        assert obj.rotation_euler.x == obj.rotation_euler.z == 0
        assert (obj.matrix_world.to_3x3() @ Vector((0, 1, 0)) - Vector((0, 1, 0))).length < 1e-6
        thickness = max(v.co.z for v in obj.data.vertices) - min(v.co.z for v in obj.data.vertices)
        assert abs(thickness - .03125) < 1e-8
        assert obj.location.y == original.location.y
        assert obj.visible_shadow and not obj.hide_render
        assert obj['state'] == row['state']
        radius = math.hypot(obj.location.x, obj.location.z)
        assert abs(radius - row['radial_distance_blocks']) < 1e-6
        radii.append(radius)
        if obj['state'] == 'resting':
            resting_gaps.append(min(p.y for p in points([obj])))
        instance_proof.append({'instance': row['instance'], 'object': obj.name,
                               'state': obj['state'], 'materials': [m.name for m in obj.data.materials],
                               'actual_mesh_position': list(obj.location), 'actual_rotation_xyz': list(obj.rotation_euler)})
    assert max(radii) < 1.9
    ground = points(blocks)
    low = [min(p[k] for p in ground) for k in range(3)]
    high = [max(p[k] for p in ground) for k in range(3)]
    assert low == [-2.5, -1, -2.5] and high == [2.5, 0, 2.5]
    centers = [sum(points([obj]), Vector()) / len(obj.data.vertices) for obj in blocks]
    assert {(round(p.x, 6), round(p.y, 6), round(p.z, 6)) for p in centers} == {
        (x, -.5, z) for x in range(-2, 3) for z in range(-2, 3)}
    front = (player.matrix_world.to_3x3() @ Vector((0, -1, 0))).normalized()
    assert (front - Vector((-1, 0, 0))).length < 1e-6
    camera = scene.camera
    basis = camera.matrix_world.to_3x3()
    direction = (basis @ Vector((0, 0, -1))).normalized()
    yaw = math.degrees(math.atan2(-direction.x, direction.z)) % 360
    pitch = math.degrees(math.atan2(-direction.y, math.hypot(direction.x, direction.z)))
    unrolled_right = direction.cross(Vector((0, 1, 0))).normalized()
    unrolled_up = unrolled_right.cross(direction).normalized()
    right = (basis @ Vector((1, 0, 0))).normalized()
    roll = math.degrees(math.atan2(right.dot(unrolled_up), right.dot(unrolled_right)))
    assert abs(yaw - 225) < .0001 and abs(pitch - 45) < .0001 and abs(roll) < .0001
    assert camera.data.type == 'ORTHO' and abs(camera.data.ortho_scale - 7.85) < 1e-5
    vertical = [screen(scene, Vector((0, y, 0))) for y in (0, 1)]
    assert abs(vertical[1][0] - vertical[0][0]) < .001 and vertical[1][1] < vertical[0][1]
    projected = [screen(scene, p) for p in points(meshes)]
    bounds = [min(p[0] for p in projected), min(p[1] for p in projected),
              max(p[0] for p in projected), max(p[1] for p in projected)]
    assert min(bounds) > 35 and max(bounds) < 989, bounds
    expected_textures = {sha(p) for p in (ROOT / 'assets').glob('*.png')}
    images = [im for im in bpy.data.images if im.source == 'FILE']
    assert all(im.packed_file for im in images)
    actual_textures = {hashlib.sha256(bytes(im.packed_file.data)).hexdigest() for im in images}
    assert expected_textures == actual_textures
    assert scene.render.engine == 'CYCLES' and scene.cycles.device == 'CPU'
    assert scene.cycles.samples == 64 and scene.render.threads == 2
    assert scene.render.resolution_x == scene.render.resolution_y == 1024
    return {'passed': True, 'candidate': f'actionassist-{multiplier}x', 'counts': counts,
            'all_original_objects_exactly_unchanged': True, 'materials_lighting_world_exactly_unchanged': True,
            'camera_measured_degrees': {'yaw': yaw, 'pitch': pitch, 'roll': roll},
            'player_front_world': list(front), 'player_cardinal': 'west',
            'platform': {'blocks': 25, 'grid': [5, 5], 'world_min': low, 'world_max': high},
            'pebble_geometry': {'upright_XY_plates': True, 'thin_axis_Z_blocks': .03125,
                               'rotation_X_Z_zero': True, 'source_Y_spin_exactly_preserved': True,
                               'shadows_enabled': True, 'resting_mesh_ground_gap_blocks': [min(resting_gaps), max(resting_gaps)]},
            'cluster': {'mean_radius_blocks': sum(radii) / len(radii), 'maximum_radius_blocks': max(radii),
                        'every_source_radius_preserved': True},
            'all_geometry_bounds_pixels': bounds, 'packed_original_texture_count': len(images),
            'instance_proof': instance_proof}


def render(multiplier):
    assert multiplier in (2, 3) and bpy.app.background
    provenance = json.loads((ROOT / 'source-provenance.json').read_text())
    assert sha(ROOT / 'base-actionassist-2.blend') == provenance['copied_inputs']['base-actionassist-2.blend']['sha256']
    dataset_path = ROOT / f'pebbles-{multiplier}x.json'
    dataset = json.loads(dataset_path.read_text())
    bpy.ops.wm.open_mainfile(filepath=str(ROOT / 'base-actionassist-2.blend'))
    baseline = snapshot()
    scene = bpy.context.scene
    originals = {obj['instance']: obj for obj in scene.objects if obj.get('role') == 'pebble'}
    assert len(originals) == 52
    for row in dataset['pebbles']:
        if row['source_field_layer'] == 0:
            continue
        original = originals[row['source_instance']]
        obj = original.copy()
        obj.name = f"Density layer {row['source_field_layer']} " + original.name
        scene.collection.objects.link(obj)
        obj['instance'] = row['instance']
        obj['density_source_instance'] = row['source_instance']
        obj['density_layer'] = row['source_field_layer']
        obj.location = row['mesh_position']
        obj.rotation_euler = row['rotation_xyz_radians']
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = 64
    scene.cycles.use_denoising = True
    scene.render.threads_mode = 'FIXED'
    scene.render.threads = 2
    scene.render.resolution_x = scene.render.resolution_y = 1024
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.filepath = str(ROOT / f'actionassist-{multiplier}x.png')
    bpy.context.preferences.filepaths.save_version = 0
    bpy.context.preferences.addons['cycles'].preferences.compute_device_type = 'NONE'
    for image in bpy.data.images:
        if image.source == 'FILE':
            # Drop stale packed-file path metadata without writing anywhere.
            # Then repack the byte-identical real asset from this directory.
            if image.packed_file:
                image.unpack(method='REMOVE')
            image.filepath = '//assets/' + Path(image.filepath).name
            image.pack()
    for text in list(bpy.data.texts):
        bpy.data.texts.remove(text)
    embedded = ['recreate.py', 'derive-field.py', 'run-blender.py', f'actionassist-{multiplier}x.py',
                f'pebbles-{multiplier}x.json', 'source-provenance.json', 'derivation-method.json']
    for name in embedded:
        bpy.data.texts.load(str(ROOT / name)).use_fake_user = True
    bpy.ops.file.pack_all()
    report = verify(multiplier, baseline, dataset)
    metadata = {'candidate': f'actionassist-{multiplier}x', 'base': 'blender-w/actionassist-2.png',
                'base_blend_sha256': sha(ROOT / 'base-actionassist-2.blend'),
                'counts': dataset['count'], 'dataset_sha256': sha(dataset_path),
                'derivation': dataset['derivation'],
                'render': {'blender_version': bpy.app.version_string, 'engine': 'CYCLES', 'device': 'CPU',
                           'samples': 64, 'threads': 2, 'resolution': [1024, 1024],
                           'background': bpy.app.background, 'noaudio': True},
                'script_sha256': {name: sha(ROOT / name) for name in embedded}}
    scene['feedback_metadata'] = json.dumps(metadata)
    blend_path = ROOT / f'actionassist-{multiplier}x.blend'
    bpy.ops.wm.save_as_mainfile(filepath=str(blend_path))
    started = time.perf_counter()
    bpy.ops.render.render(write_still=True)
    metadata['render']['duration_seconds'] = time.perf_counter() - started
    metadata['png_sha256'] = sha(ROOT / f'actionassist-{multiplier}x.png')
    scene['feedback_metadata'] = json.dumps(metadata)
    bpy.ops.wm.save_as_mainfile(filepath=str(blend_path))
    bpy.ops.wm.open_mainfile(filepath=str(blend_path))
    report = verify(multiplier, baseline, dataset)
    assert all(bpy.data.texts[name].as_string() == (ROOT / name).read_text() for name in embedded)
    report['saved_blend_reopened_and_verified'] = True
    report['embedded_scripts_and_dataset_match_disk'] = True
    (ROOT / f'actionassist-{multiplier}x-verification.json').write_text(json.dumps(report, indent=2) + '\n')
    (ROOT / f'actionassist-{multiplier}x-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print('DENSITY_RENDER_VERIFIED ' + json.dumps(metadata['render']), flush=True)
