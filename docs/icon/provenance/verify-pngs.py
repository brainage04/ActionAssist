"""Decode deliverable PNGs, verify datasets and link saved geometry proof."""
import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parent
base = Image.open(ROOT / 'base-actionassist-2.png').convert('RGBA')
datasets = [json.loads((ROOT / f'pebbles-{n}x.json').read_text()) for n in (2, 3)]
assert datasets[0]['pebbles'] == datasets[1]['pebbles'][:104]
reports = []
for multiplier, dataset in zip((2, 3), datasets):
    stem = f'actionassist-{multiplier}x'
    path = ROOT / (stem + '.png')
    with Image.open(path) as check:
        assert check.format == 'PNG'
        check.verify()
    im = Image.open(path).convert('RGBA')
    assert im.size == (1024, 1024)
    assert im.getchannel('A').getextrema() == (255, 255)
    metadata = json.loads((ROOT / (stem + '-metadata.json')).read_text())
    proof = json.loads((ROOT / (stem + '-verification.json')).read_text())
    assert proof['passed'] and proof['saved_blend_reopened_and_verified']
    assert metadata['png_sha256'] == hashlib.sha256(path.read_bytes()).hexdigest()
    assert proof['counts'] == dataset['count'] == {
        'total': 52 * multiplier, 'resting': 42 * multiplier, 'airborne': 10 * multiplier}
    entries = dataset['pebbles']
    assert len({p['instance'] for p in entries}) == len(entries)
    assert len({tuple(p['mesh_position']) for p in entries}) == len(entries)
    originals = {p['instance']: p for p in entries[:52]}
    maximum_formula_error = 0
    for p in entries:
        source = originals[p['source_instance']]
        theta = p['source_field_layer'] * math.pi * (3 - math.sqrt(5))
        c, s = math.cos(theta), math.sin(theta)
        for key in ('mesh_position', 'entity_position'):
            x, y, z = source[key]
            expected = [c * x + s * z, y, -s * x + c * z]
            error = max(abs(a - b) for a, b in zip(expected, p[key]))
            maximum_formula_error = max(maximum_formula_error, error)
            assert error < 1e-12
        assert p['rotation_xyz_radians'] == source['rotation_xyz_radians']
        assert p['state'] == source['state']
    difference = ImageChops.difference(im.convert('RGB'), base.convert('RGB'))
    assert difference.getbbox() is not None
    gray_difference = difference.convert('L')
    changed = sum(count for value, count in enumerate(gray_difference.histogram()) if value > 10)
    assert changed > 1000
    player_crop = im.crop((425, 300, 585, 545))
    yellow = sum(r > 150 and g > 145 and b < 120 for r, g, b, a in player_crop.get_flattened_data())
    assert yellow > 10000, yellow
    reports.append({'candidate': stem, 'passed': True, 'png': path.name,
                    'sha256': metadata['png_sha256'], 'dimensions': list(im.size),
                    'alpha_range': list(im.getchannel('A').getextrema()),
                    'counts': dataset['count'], 'dataset_nested': True,
                    'formula_maximum_error': maximum_formula_error,
                    'distinct_instance_identifiers_and_mesh_positions': True,
                    'changed_pixels_vs_selected_base_above_10_luma': changed,
                    'yellow_player_pixels_in_center_crop': yellow,
                    'all_geometry_inside_frame_from_reopened_blend': proof['all_geometry_bounds_pixels'],
                    'render': metadata['render']})
assert reports[0]['sha256'] != reports[1]['sha256']
(ROOT / 'png-verification.json').write_text(json.dumps(reports, indent=2) + '\n')
print(json.dumps(reports, indent=2))
