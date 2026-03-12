#!/data/data/com.termux/files/usr/bin/bash
# Hook: PostToolUse (Edit|Write)
# Remind to restart Bridge server when bridge files are modified
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [ -z "$FILE" ]; then
  exit 0
fi

case "$FILE" in
  *bridge/bridge.py*|*bridge/config.json*|*bridge/google_flights.py*)
    echo "⚠️ Bridge 檔案已修改: $(basename "$FILE") — 完成後記得執行 cd ~/bridge && sh start.sh stop && sh start.sh" >&2
    ;;
esac

exit 0
