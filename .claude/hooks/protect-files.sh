#!/data/data/com.termux/files/usr/bin/bash
# Hook: PreToolUse (Edit|Write)
# Block direct modification of sensitive files
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [ -z "$FILE" ]; then
  exit 0
fi

BLOCKED=false
REASON=""

case "$FILE" in
  *SecurePrefs*)
    BLOCKED=true
    REASON="SecurePrefs.java contains encrypted credential logic — do not modify without explicit user approval"
    ;;
  *.env*)
    BLOCKED=true
    REASON=".env file may contain secrets — do not modify"
    ;;
  *google-services.json*)
    BLOCKED=true
    REASON="google-services.json contains API keys — do not modify"
    ;;
  *keystore*|*.jks)
    BLOCKED=true
    REASON="Keystore files must not be modified"
    ;;
esac

if [ "$BLOCKED" = true ]; then
  echo "BLOCKED: $REASON ($FILE)" >&2
  exit 2
fi

exit 0
