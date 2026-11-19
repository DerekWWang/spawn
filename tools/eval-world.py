"""Pinned evaluation worlds: save the dev world as a template, and restore it before each run.

    python tools/eval-world.py list
    python tools/eval-world.py save <name> [--site site.json]
    python tools/eval-world.py restore <name>

Templates live in run/eval-worlds/<name>/ (git ignores run/). A template is the world as it stood when
saved, minus every player's data and the session lock, plus site.json: the seed, the spawn, what is
where, and the mod build it was made with. Restoring replaces run/world with a fresh copy, so every run
starts from the same untouched land with nobody in it. Stop the server first (`qube server down`); the
script refuses while run/world is locked.

The copy, not the seed, is what pins the world: chunks already generated stay as they are whatever the
mod's world generation does later. Land beyond them is generated on demand from the seed by the mod
build that is running.
"""
import json
import os
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN = os.path.join(ROOT, 'run')
WORLD = os.path.join(RUN, 'world')
TEMPLATES = os.path.join(RUN, 'eval-worlds')
SKIP = {'session.lock'}


def fail(message):
    print('error:', message)
    sys.exit(1)


def locked():
    """True while a server holds run/world (Windows refuses to rename a folder with open files)."""
    if not os.path.isdir(WORLD):
        return False
    probe = WORLD + '.probe'
    try:
        os.rename(WORLD, probe)
    except OSError:
        return True
    os.rename(probe, WORLD)
    return False


def properties():
    out = {}
    path = os.path.join(RUN, 'server.properties')
    if os.path.exists(path):
        for line in open(path, encoding='utf8'):
            if '=' in line and not line.startswith('#'):
                key, value = line.rstrip('\n').split('=', 1)
                out[key] = value
    return out


def ignore(directory, names):
    rel = os.path.relpath(directory, WORLD).replace('\\', '/')
    skipped = [n for n in names if n in SKIP]
    if rel == 'players':
        skipped += names  # keep the folder, drop every player's data, stats and advancements
    return skipped


def git(*args):
    try:
        return subprocess.run(['git', *args], cwd=ROOT, capture_output=True, text=True).stdout.strip()
    except OSError:
        return ''


def save(name, site_file=None):
    if not os.path.isdir(WORLD):
        fail('there is no run/world to save')
    if locked():
        fail('run/world is in use: stop the server first (qube server down)')
    target = os.path.join(TEMPLATES, name)
    if os.path.exists(target):
        fail(f'{target} exists; restore or delete it first')
    shutil.copytree(WORLD, os.path.join(target, 'world'), ignore=ignore)
    for sub in ('advancements', 'data', 'stats'):
        os.makedirs(os.path.join(target, 'world', 'players', sub), exist_ok=True)
    site = json.load(open(site_file, encoding='utf8')) if site_file else {}
    site.setdefault('name', name)
    site.setdefault('seed', properties().get('level-seed', ''))
    site['saved'] = time.strftime('%Y-%m-%d %H:%M')
    site['commit'] = git('rev-parse', '--short', 'HEAD')
    site['dirty'] = bool(git('status', '--porcelain'))
    json.dump(site, open(os.path.join(target, 'site.json'), 'w', encoding='utf8'), indent=2)
    print(f'saved {name}: {size(target)} in {target}')


def restore(name):
    source = os.path.join(TEMPLATES, name, 'world')
    if not os.path.isdir(source):
        fail(f'no template {name}; `list` shows what there is')
    if locked():
        fail('run/world is in use: stop the server first (qube server down)')
    if os.path.isdir(WORLD):
        shutil.rmtree(WORLD)
    shutil.copytree(source, WORLD)
    site = json.load(open(os.path.join(TEMPLATES, name, 'site.json'), encoding='utf8'))
    print(f"restored {name} to run/world (seed {site.get('seed')}, spawn {site.get('spawn')}); start it with qube server up")


def size(path):
    total = sum(os.path.getsize(os.path.join(d, f)) for d, _, files in os.walk(path) for f in files)
    return f'{total / 1e6:.1f} MB'


def listing():
    if not os.path.isdir(TEMPLATES):
        print('no templates yet')
        return
    for name in sorted(os.listdir(TEMPLATES)):
        site_path = os.path.join(TEMPLATES, name, 'site.json')
        site = json.load(open(site_path, encoding='utf8')) if os.path.exists(site_path) else {}
        print(f"{name}: seed {site.get('seed')}, spawn {site.get('spawn')}, saved {site.get('saved')}, {size(os.path.join(TEMPLATES, name))}")
        if site.get('summary'):
            print('   ', site['summary'])


if __name__ == '__main__':
    args = sys.argv[1:]
    if not args or args[0] not in ('list', 'save', 'restore') or (args[0] != 'list' and len(args) < 2):
        print(__doc__)
        sys.exit(2)
    if args[0] == 'list':
        listing()
    elif args[0] == 'save':
        save(args[1], args[3] if len(args) > 3 and args[2] == '--site' else None)
    else:
        restore(args[1])
