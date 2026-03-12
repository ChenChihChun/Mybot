#!/data/data/com.termux/files/usr/bin/bash
# Hook: PreToolUse (Bash)
# Block dangerous git commands
INPUT=$(cat)
CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

if [ -z "$CMD" ]; then
  exit 0
fi

if echo "$CMD" | grep -qE 'git\s+(reset\s+--hard|push\s+--force|push\s+-f|clean\s+-f|checkout\s+\.\s*$|branch\s+-D)'; then
  echo "BLOCKED: 危險 git 指令被攔截: $CMD — 請確認後手動執行" >&2
  exit 2
fi

exit 0
