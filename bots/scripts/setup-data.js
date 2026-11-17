#!/usr/bin/env node
// Puts the Minecraft 26.3 protocol and data tables under node_modules/minecraft-data.
//
// The published minecraft-data package stops at 26.1. Until 26.3 is released upstream, the tables come from
// one pinned commit of an unmerged pull request (see package.json "spawn.minecraftData" and the README).
// The commit is fetched by its hash, so a branch that moves cannot change what is installed.
const { execFileSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const { repository, commit } = require('../package.json').spawn.minecraftData
const packageDir = path.dirname(require.resolve('minecraft-data/package.json'))
const dataDir = path.join(packageDir, 'minecraft-data')
const git = (args, cwd) => execFileSync('git', args, { cwd, stdio: ['ignore', 'pipe', 'inherit'] }).toString().trim()

function installed () {
  try { return git(['rev-parse', 'HEAD'], dataDir) === commit && fs.existsSync(path.join(dataDir, '.git')) } catch { return false }
}

if (installed()) {
  console.log('minecraft-data is already at', commit.slice(0, 8))
} else {
  fs.rmSync(dataDir, { recursive: true, force: true })
  fs.mkdirSync(dataDir, { recursive: true })
  git(['init', '-q'], dataDir)
  git(['remote', 'add', 'origin', repository], dataDir)
  console.log('fetching minecraft-data', commit.slice(0, 8), 'from', repository)
  git(['fetch', '-q', '--depth', '1', 'origin', commit], dataDir)
  git(['checkout', '-q', 'FETCH_HEAD'], dataDir)
  if (git(['rev-parse', 'HEAD'], dataDir) !== commit) throw new Error('minecraft-data checkout is not the pinned commit')
}

// The package's own generator rebuilds its index (data.js) from the tables now on disk.
execFileSync(process.execPath, [path.join(packageDir, 'bin', 'generate_data.js')], { stdio: 'inherit' })
for (const key of Object.keys(require.cache)) if (key.startsWith(packageDir)) delete require.cache[key]
const data = require('minecraft-data')('26.3')
if (!data || data.version.version !== 777) throw new Error('minecraft-data has no 26.3 tables after setup')
console.log('minecraft-data ready: 26.3, protocol', data.version.version, '-', data.blocksArray.length, 'blocks,', data.itemsArray.length, 'items')
