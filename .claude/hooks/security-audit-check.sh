#!/data/data/com.termux/files/usr/bin/bash
# Hook: PostToolUse (Edit|Write)
# Trigger security audit reminder every 10 versionCodes
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# Only check when build.gradle is modified
case "$FILE" in
  *build.gradle*)
    ;;
  *)
    exit 0
    ;;
esac

# Extract current versionCode from the file
if [ ! -f "$FILE" ]; then
  exit 0
fi

VERSION_CODE=$(grep -oP 'versionCode\s+\K\d+' "$FILE" 2>/dev/null)
if [ -z "$VERSION_CODE" ]; then
  exit 0
fi

# Read last audit versionCode from marker file
AUDIT_MARKER="${HOME}/githup/Mybot/.claude/last_security_audit"
LAST_AUDIT=0
if [ -f "$AUDIT_MARKER" ]; then
  LAST_AUDIT=$(cat "$AUDIT_MARKER")
fi

# Check if we've crossed a 10-boundary since last audit
NEXT_AUDIT=$(( (LAST_AUDIT / 10 + 1) * 10 + 1 ))

if [ "$VERSION_CODE" -ge "$NEXT_AUDIT" ]; then
  echo "" >&2
  echo "🔒 ======================== SECURITY AUDIT 提醒 ========================" >&2
  echo "   versionCode 已達 $VERSION_CODE（上次審計: $LAST_AUDIT，觸發點: $NEXT_AUDIT）" >&2
  echo "   請執行 OWASP Mobile Top 10 安全掃描：" >&2
  echo "   1. 硬編碼密鑰/敏感資料檢查" >&2
  echo "   2. 不安全的資料儲存（SharedPreferences vs EncryptedSharedPreferences）" >&2
  echo "   3. 網路通訊安全（HTTP vs HTTPS）" >&2
  echo "   4. SQL Injection / Input Validation" >&2
  echo "   5. WebView 安全性" >&2
  echo "   6. Intent 安全性（exported components）" >&2
  echo "   7. 日誌洩漏敏感資訊" >&2
  echo "   完成後執行: echo $VERSION_CODE > $AUDIT_MARKER" >&2
  echo "=======================================================================" >&2
  echo "" >&2
fi

exit 0
