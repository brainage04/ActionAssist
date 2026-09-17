"""Finite sequential rendering batch. Shared watchdog applies Blender CPUWeight 20.
No GUI/audio server is started; nix Blender runs background/-noaudio, CPU only.
"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def descendants(pid):
    listing = subprocess.run(['ps', '-eo', 'pid=,ppid=,comm='], capture_output=True, text=True, check=True)
    rows = [line.split(None, 2) for line in listing.stdout.splitlines()]
    found = {pid}
    while True:
        extended = found | {int(p) for p, parent, command in rows if int(parent) in found}
        if extended == found:
            return [(int(p), command) for p, parent, command in rows if int(p) in found]
        found = extended


def run(multiplier):
    stem = f'actionassist-{multiplier}x'
    cpus = sorted(os.sched_getaffinity(0))[:2]
    command = ['taskset', '-c', ','.join(map(str, cpus)), 'nix', 'shell', 'nixpkgs#blender', '--command',
               'blender', '--background', '-noaudio', '--threads', '2', '--python-exit-code', '1',
               '--python', str(ROOT / (stem + '.py'))]
    environment = {k: v for k, v in os.environ.items() if k not in ('DISPLAY', 'WAYLAND_DISPLAY')}
    environment.update({'CUDA_VISIBLE_DEVICES': '', 'HIP_VISIBLE_DEVICES': '', 'ROCR_VISIBLE_DEVICES': '',
                        'OMP_NUM_THREADS': '2', 'OPENBLAS_NUM_THREADS': '2', 'PYTHONDONTWRITEBYTECODE': '1'})
    started = time.monotonic()
    resource_samples = []
    log_path = ROOT / 'evidence' / (stem + '.log')
    with log_path.open('w') as log:
        process = subprocess.Popen(command, env=environment, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        while process.poll() is None:
            for pid, name in descendants(process.pid):
                if 'blender' not in name:
                    continue
                try:
                    cg = Path(f'/proc/{pid}/cgroup').read_text()
                    status = Path(f'/proc/{pid}/status').read_text()
                    row = {'pid': pid, 'cgroup': cg, 'elapsed_seconds': time.monotonic() - started,
                           'status': [s for s in status.splitlines() if s.startswith(('Threads:', 'Cpus_allowed_list:'))]}
                    if not resource_samples or row['cgroup'] != resource_samples[-1]['cgroup']:
                        resource_samples.append(row)
                except FileNotFoundError:
                    pass
            time.sleep(.5)
    properties = subprocess.run(['systemctl', '--user', 'show', 'render-blender.service', '-p', 'CPUWeight',
                                 '-p', 'CPUQuotaPerSecUSec', '-p', 'ControlGroup'], capture_output=True, text=True)
    report = {'candidate': stem, 'command': command, 'returncode': process.returncode,
              'wall_seconds': time.monotonic() - started, 'resource_samples': resource_samples,
              'shared_cgroup_properties': properties.stdout, 'affinity_cpus': cpus,
              'render_threads': 2, 'device': 'CPU', 'noaudio': True, 'display_environment_removed': True,
              'log': str(log_path.relative_to(ROOT))}
    (ROOT / (stem + '-run.json')).write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report), flush=True)
    if process.returncode:
        print(log_path.read_text(), flush=True)
        raise RuntimeError(f'{stem} failed with status {process.returncode}')
    return report


if __name__ == '__main__':
    print('DENSITY_CPU_BATCH_STARTED', flush=True)
    multipliers = [int(v) for v in sys.argv[1:]] or [2, 3]
    results = [run(multiplier) for multiplier in multipliers]
    (ROOT / 'run-summary.json').write_text(json.dumps(results, indent=2) + '\n')
