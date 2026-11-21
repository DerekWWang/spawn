#!/usr/bin/env bash
# Start one commander: a headless claude session whose only tools direct the actors through the host.
# The host (host.js) must already be running with the same names.
# usage: run.sh <minutes> <model> Name1,Name2,Name3 [logDir]
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
MIN="$1"; MODEL="$2"; NAMES="$3"; LOGS="${4:-$DIR/logs}"
mkdir -p "$LOGS"; LOGS="$(cd "$LOGS" && pwd)"
DEADLINE=$(node -e "console.log(Date.now() + $MIN * 60000)")
LIST=$(echo "$NAMES" | sed 's/,\([^,]*\)$/ and \1/; s/,/, /g')
WORK="$LOGS/commander"; mkdir -p "$WORK"
sed -e "s/{ACTORS}/$LIST/g" "$DIR/prompt.md" > "$WORK/system.md"
node -e "
  const fs=require('fs');
  fs.writeFileSync(process.argv[1], JSON.stringify({mcpServers:{command:{type:'stdio',command:'node',args:[process.argv[2]],env:{EVAL_PORT:'47100',DEADLINE:process.argv[3],ACTORS:process.argv[4]}}}},null,1))
" "$WORK/mcp.json" "$(cygpath -w "$DIR/mcp.js")" "$DEADLINE" "$NAMES"
echo "deadline $(node -e "console.log(new Date($DEADLINE).toISOString())")"
# An empty working directory, no settings, no built-in tools: the commander sees only the game.
cd "$WORK" && claude -p "Your people have arrived. Begin." \
  --model "$MODEL" \
  --system-prompt "$(cat "$WORK/system.md")" \
  --tools "" \
  --strict-mcp-config --mcp-config "$(cygpath -w "$WORK/mcp.json")" \
  --allowedTools "mcp__command__*" \
  --setting-sources "" \
  --no-session-persistence \
  --output-format stream-json --verbose \
  > "$LOGS/commander.jsonl" 2> "$LOGS/commander.err"
echo "commander exited $? at $(date +%T)" >> "$LOGS/done"
