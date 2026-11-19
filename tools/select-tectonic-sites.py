"""Select bounded generated sites from the GM terrain diagnostic; write a repeatable RCON script."""
import json
from pathlib import Path
import statistics
import sys

root = Path(__file__).resolve().parents[1]
for seed in sys.argv[1:]:
    folder = root / 'run/qube/tectonic-surveys-v2' / seed
    raw = (folder / 'terrain-survey.txt').read_text(encoding='utf-8-sig')
    replies = json.loads(raw[raw.index('{'):])['replies']
    samples = [json.loads(v['reply']) for v in replies if v['command'].startswith('survival')]
    points = samples[:25]
    chosen = []
    for mineral in ['TIN', 'COPPER', 'IRON', 'COAL']:
        eligible = [p for p in points if p['mineral'] == mineral]
        if eligible:
            chosen.append(max(eligible, key=lambda p: p['anchorHeight']))
    for key in ['height', 'humidity', 'baselineC', 'relief']:
        chosen += [max(points, key=lambda p: p[key]), min(points, key=lambda p: p[key])]
    commands = []
    for point in chosen:
        x, z = point['depositX'], point['depositZ']
        command = f'forceload add {x-24} {z-24} {x+24} {z+24}'
        if command not in commands:
            commands.append(command)
    commands += ['save-all flush']
    project = f'run/qube/tectonic-surveys-v2/{seed}'
    (folder / 'load-sites.ps1').write_text('& qube rcon --project ' + project + ' ' + ' '.join("'" + c + "'" for c in commands), encoding='utf-8')
    (folder / 'selected-sites.json').write_text(json.dumps(chosen, indent=2), encoding='utf-8')
    print(json.dumps({'seed': seed, 'height': [min(p['height'] for p in points), max(p['height'] for p in points)], 'baselineC': [min(p['baselineC'] for p in points), max(p['baselineC'] for p in points)], 'mineralKinds': sorted(set(p['mineral'] for p in points)), 'coldMedianMicros': statistics.median(p['elapsedMicros'] for p in points), 'warmMedianMicros': statistics.median(p['elapsedMicros'] for p in samples[25:]), 'generationSites': len(commands)-1}))
