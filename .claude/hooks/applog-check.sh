#!/data/data/com.termux/files/usr/bin/bash
# Hook: PostToolUse (Edit|Write)
# Remind to add AppLog when editing Java files that have user-facing operations
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [ -z "$FILE" ]; then
  exit 0
fi

# Only check Java files in the app source
case "$FILE" in
  *.java)
    # Skip files that are not user-facing (helpers, models, DB)
    case "$(basename "$FILE")" in
      UIHelper.java|SecurePrefs.java|NotificationHelper.java)
        exit 0
        ;;
    esac

    # Check if file has any AppLog calls
    if [ -f "$FILE" ] && ! grep -q "AppLog\." "$FILE"; then
      echo "⚠️ AppLog SOP: $(basename "$FILE") 沒有任何 AppLog 呼叫 — 請確認是否需要加入 logging" >&2
    fi
    ;;
esac

exit 0
