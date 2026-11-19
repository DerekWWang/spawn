"""Prepare isolated, ignored Qube projects; never copy or alter an existing world."""
import json
from pathlib import Path
import shutil
import sys
import uuid

root = Path(__file__).resolve().parents[1]
height = next((int(v.split('=',1)[1]) for v in sys.argv[1:] if v.startswith('--height=')),None)
if height is not None and height != 640:
    raise ValueError('Only the explicit 640-block validation profile is supported.')
seeds = [v for v in sys.argv[1:] if not v.startswith('--height=')]
for seed in map(int, seeds or ['42', '918273', '20261107']):
    target = root / ('run/qube/tectonic-surveys-v2' + ('-h640-v2' if height else '')) / str(seed)
    target.mkdir(parents=True, exist_ok=True)
    for directory in ['src', 'gradle', 'tools', 'config']:
        shutil.copytree(root / directory, target / directory, dirs_exist_ok=True)
    for name in ['build.gradle', 'settings.gradle', 'gradlew', 'gradlew.bat', 'gradle.properties']:
        shutil.copy2(root / name, target / name)
    with (target / 'gradle.properties').open('a', encoding='utf-8') as stream:
        stream.write('\ntectonicProfile=true\n')
    (target / '.qube').mkdir(exist_ok=True)
    manifest = target / '.qube/project.json'
    if not manifest.exists():
        data = json.loads((root / '.qube/project.json').read_text(encoding='utf-8'))
        data['project_id'] = str(uuid.uuid4())
        manifest.write_text(json.dumps(data, indent=2), encoding='utf-8')
    shutil.copytree(root / 'build/tectonic-runtime', target / 'build/tectonic-runtime', dirs_exist_ok=True)
    run = target / 'run'
    run.mkdir(exist_ok=True)
    (run / 'config').mkdir(exist_ok=True)
    if not (run / 'config/tectonic.json').exists():
        config=json.loads((root / 'config/tectonic-realism.json').read_text(encoding='utf-8'))
        if height:
            config['global_terrain']={'min_y':-64,'max_y':height,'vertical_scale':4.5}
        (run / 'config/tectonic.json').write_text(json.dumps(config,indent=2),encoding='utf-8')
    if not (run / 'server.properties').exists():
        (run / 'server.properties').write_text(f'level-seed={seed}\nview-distance=5\nsimulation-distance=4\n', encoding='utf-8')
    print(target)
