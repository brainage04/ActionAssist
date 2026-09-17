"""Deterministically extend the approved 52-pebble field; no simulation claim."""
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent
GOLDEN_ANGLE = math.pi * (3 - math.sqrt(5))


def derive(multiplier):
    source = json.loads((ROOT / 'base-field.json').read_text())
    assert source['count'] == {'total': 52, 'resting': 42, 'airborne': 10}
    entries = []
    for layer in range(multiplier):
        angle = layer * GOLDEN_ANGLE
        c, s = math.cos(angle), math.sin(angle)
        for pebble in source['pebbles']:
            def position(key):
                x, y, z = pebble[key]
                return [c * x + s * z, y, -s * x + c * z] if layer else [x, y, z]
            entries.append({
                'instance': pebble['instance'] if layer == 0 else f'density-{layer + 1}:' + pebble['instance'],
                'source_instance': pebble['instance'],
                'source_field_layer': layer,
                'position_rotation_about_world_Y_degrees': math.degrees(angle),
                'state': pebble['state'],
                'entity_position': position('after_entity_position'),
                'mesh_position': position('after_mesh_position'),
                'rotation_xyz_radians': pebble['rotation_xyz_radians'],
                'radial_distance_blocks': pebble['radius_after']})
    result = {
        'candidate': f'actionassist-{multiplier}x',
        'source': 'blender-w/actionassist-2.blend',
        'source_field_sha256': hashlib.sha256((ROOT / 'base-field.json').read_bytes()).hexdigest(),
        'count': {'total': len(entries), 'resting': sum(p['state'] == 'resting' for p in entries),
                  'airborne': sum(p['state'] == 'airborne' for p in entries)},
        'derivation': {
            'type': 'deterministic illustrative extension, not a longer real Minecraft run',
            'original_count': 52, 'added_count': len(entries) - 52,
            'layer_angles_degrees': [math.degrees(i * GOLDEN_ANGLE) for i in range(multiplier)],
            'golden_angle_radians': 'pi * (3 - sqrt(5))',
            'formula': 'x_new=cos(theta)*x+sin(theta)*z; y_new=y; z_new=-sin(theta)*x+cos(theta)*z, theta=layer*pi*(3-sqrt(5))',
            'ordering': 'Original recorded field order, layer 0 then 1 then 2 as required; no random seed or sampling.',
            'preserved': ['All original 52 instances unchanged', 'Each source radial distance and therefore clustered radial distribution', 'Source resting/airborne state and entity Y', 'Vanilla bob/hover mesh Y', 'Original recorded render spin unchanged: [0, source_Y_spin, 0]', 'Original upright XY plate mesh, material and texture'],
            'nested_candidates': 'The complete 104-instance 2x field is the first 104 entries of 3x.',
            'caveats': 'The approved 52-instance field was already derived from a real 26-entity t=5s capture and radially compacted by 0.72. Added layers are illustrative copies, not new observed entities. No collision, physics, collection, visibility optimization or occlusion removal is performed; natural overlaps and player occlusion remain. Position rotation does not rotate the preserved source spin.'},
        'pebbles': entries}
    assert result['count'] == {'total': 52 * multiplier, 'resting': 42 * multiplier, 'airborne': 10 * multiplier}
    (ROOT / f'pebbles-{multiplier}x.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


if __name__ == '__main__':
    datasets = [derive(multiplier) for multiplier in (2, 3)]
    assert datasets[0]['pebbles'] == datasets[1]['pebbles'][:104]
    (ROOT / 'derivation-method.json').write_text(json.dumps({d['candidate']: d['derivation'] for d in datasets}, indent=2) + '\n')
    print(json.dumps({d['candidate']: d['count'] for d in datasets}))
