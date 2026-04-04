#!/bin/bash
cd ~/githup/Mybot

# ===== 1. Commit & Push =====
if [ $# -gt 0 ]; then
  MSG="$*"
else
  echo -n "Commit message: "
  read MSG
  MSG="${MSG:-update}"
fi

git add -A
git commit -m "$MSG"
git push origin main

# ===== 2. Security Review =====
CLAUDE_FIX="$HOME/.local/bin/claude-fix"
CHANGELOG="$HOME/.claude/projects/-data-data-com-termux-files-home/memory/changelog.md"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M')

# Get Java diff from last commit
DIFF=$(git diff HEAD~1 HEAD -- '*.java' 2>/dev/null)

if [ -z "$DIFF" ]; then
  echo "ℹ️  No Java changes detected, skipping security review."
  exit 0
fi

echo ""
echo "🔍 Running security review on Java changes..."

# Ask Claude to analyze the diff for security issues
ANALYSIS=$($CLAUDE_FIX --print -p "You are a security auditor for an Android Java app. Analyze this git diff for security vulnerabilities (OWASP Mobile Top 10, injection, data leakage, insecure crypto, hardcoded secrets, etc).

Output ONLY valid JSON in this exact format, no other text:
{
  \"issues\": [
    {
      \"severity\": \"low|medium|high\",
      \"file\": \"filename.java\",
      \"description\": \"issue description\",
      \"fix\": \"suggested fix\"
    }
  ]
}

If no issues found, output: {\"issues\": []}

Diff:
$DIFF" 2>/dev/null)

if [ $? -ne 0 ] || [ -z "$ANALYSIS" ]; then
  echo "⚠️  Security review failed (claude-fix error), skipping."
  exit 0
fi

# Try to extract JSON from response (handle markdown code blocks)
JSON=$(echo "$ANALYSIS" | sed -n '/^[{]/,/^[}]/p')
if [ -z "$JSON" ]; then
  # Try stripping ```json ... ``` wrapper
  JSON=$(echo "$ANALYSIS" | sed -n '/```json/,/```/p' | sed '1d;$d')
fi
if [ -z "$JSON" ]; then
  JSON=$(echo "$ANALYSIS" | sed -n '/```/,/```/p' | sed '1d;$d')
fi

# Validate JSON and count issues
ISSUE_COUNT=$(echo "$JSON" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(len(data.get('issues', [])))
except:
    print(-1)
" 2>/dev/null)

if [ "$ISSUE_COUNT" = "-1" ] || [ -z "$ISSUE_COUNT" ]; then
  echo "⚠️  JSON parse failed. Raw output:"
  echo "$ANALYSIS"
  echo ""
  echo "---" >> "$CHANGELOG"
  echo "" >> "$CHANGELOG"
  echo "### 🔒 Security Review: $TIMESTAMP" >> "$CHANGELOG"
  echo "⚠️ JSON parse failed, raw output logged. No auto-fix applied." >> "$CHANGELOG"
  echo "" >> "$CHANGELOG"
  exit 0
fi

if [ "$ISSUE_COUNT" = "0" ]; then
  echo "✅ No security issues found."
  exit 0
fi

echo "Found $ISSUE_COUNT issue(s)."
echo ""

# Process each issue
REVIEW_LOG=""
echo "$JSON" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for i, issue in enumerate(data['issues']):
    print(f\"{i}|{issue['severity']}|{issue['file']}|{issue['description']}|{issue['fix']}\")
" 2>/dev/null | while IFS='|' read -r IDX SEVERITY FILE DESC FIX; do

  echo "[$((IDX+1))/$ISSUE_COUNT] [$SEVERITY] $FILE"
  echo "  Issue: $DESC"
  echo "  Fix:   $FIX"
  echo ""

  if [ "$SEVERITY" = "low" ]; then
    # --- Auto-fix low severity ---
    FILEPATH=$(find app/src -name "$FILE" 2>/dev/null | head -1)
    if [ -z "$FILEPATH" ]; then
      echo "  ⚠️  File not found: $FILE, skipping."
      echo "- ⚠️ [$SEVERITY] $FILE: $DESC — file not found, skipped" >> /tmp/security_review_log.txt
      continue
    fi

    # Backup
    cp "$FILEPATH" "${FILEPATH}.bak"

    # Apply fix via claude-fix
    $CLAUDE_FIX --print -p "Fix this security issue in the file below. Only output the complete fixed file content, no explanation.

Issue: $DESC
Suggested fix: $FIX

File content:
$(cat "$FILEPATH")" > /tmp/security_fix_output.txt 2>/dev/null

    if [ $? -eq 0 ] && [ -s /tmp/security_fix_output.txt ]; then
      cp /tmp/security_fix_output.txt "$FILEPATH"
      # Verify it still compiles (basic check - file not empty and has class keyword)
      if grep -q "class " "$FILEPATH" 2>/dev/null; then
        git add "$FILEPATH"
        git commit -m "fix: auto security patch - $DESC"
        git push origin main
        rm -f "${FILEPATH}.bak"
        echo "  ✅ 已自動修復：$DESC"
        echo "- ✅ [$SEVERITY] $FILE: $DESC — auto-fixed" >> /tmp/security_review_log.txt
      else
        # Restore from backup
        cp "${FILEPATH}.bak" "$FILEPATH"
        rm -f "${FILEPATH}.bak"
        echo "  ⚠️  Fix produced invalid file, restored backup."
        echo "- ⚠️ [$SEVERITY] $FILE: $DESC — fix invalid, restored" >> /tmp/security_review_log.txt
      fi
    else
      cp "${FILEPATH}.bak" "$FILEPATH"
      rm -f "${FILEPATH}.bak"
      echo "  ⚠️  claude-fix failed, restored backup."
      echo "- ⚠️ [$SEVERITY] $FILE: $DESC — claude-fix failed, restored" >> /tmp/security_review_log.txt
    fi
    rm -f /tmp/security_fix_output.txt

  else
    # --- Interactive confirm for medium/high ---
    echo -n "  要套用修復嗎？[y/N] "
    read CONFIRM
    if [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
      FILEPATH=$(find app/src -name "$FILE" 2>/dev/null | head -1)
      if [ -z "$FILEPATH" ]; then
        echo "  ⚠️  File not found: $FILE, skipping."
        echo "- ⚠️ [$SEVERITY] $FILE: $DESC — file not found, skipped" >> /tmp/security_review_log.txt
        continue
      fi

      # Backup
      cp "$FILEPATH" "${FILEPATH}.bak"

      $CLAUDE_FIX --print -p "Fix this security issue in the file below. Only output the complete fixed file content, no explanation.

Issue: $DESC
Suggested fix: $FIX

File content:
$(cat "$FILEPATH")" > /tmp/security_fix_output.txt 2>/dev/null

      if [ $? -eq 0 ] && [ -s /tmp/security_fix_output.txt ]; then
        cp /tmp/security_fix_output.txt "$FILEPATH"
        if grep -q "class " "$FILEPATH" 2>/dev/null; then
          git add "$FILEPATH"
          git commit -m "fix: security patch ($SEVERITY) - $DESC"
          git push origin main
          rm -f "${FILEPATH}.bak"
          echo "  ✅ 已修復：$DESC"
          echo "- ✅ [$SEVERITY] $FILE: $DESC — fixed (confirmed)" >> /tmp/security_review_log.txt
        else
          cp "${FILEPATH}.bak" "$FILEPATH"
          rm -f "${FILEPATH}.bak"
          echo "  ⚠️  Fix produced invalid file, restored backup."
          echo "- ⚠️ [$SEVERITY] $FILE: $DESC — fix invalid, restored" >> /tmp/security_review_log.txt
        fi
      else
        cp "${FILEPATH}.bak" "$FILEPATH"
        rm -f "${FILEPATH}.bak"
        echo "  ⚠️  claude-fix failed, restored backup."
        echo "- ⚠️ [$SEVERITY] $FILE: $DESC — claude-fix failed, restored" >> /tmp/security_review_log.txt
      fi
      rm -f /tmp/security_fix_output.txt

    else
      echo "  ⏭️  Skipped."
      echo "- ⏭️ [$SEVERITY] $FILE: $DESC — skipped by user. Fix: $FIX" >> /tmp/security_review_log.txt
    fi
  fi

done

# ===== 3. Append results to changelog =====
if [ -f /tmp/security_review_log.txt ]; then
  echo "" >> "$CHANGELOG"
  echo "### 🔒 Security Review: $TIMESTAMP" >> "$CHANGELOG"
  cat /tmp/security_review_log.txt >> "$CHANGELOG"
  echo "" >> "$CHANGELOG"
  rm -f /tmp/security_review_log.txt
  echo ""
  echo "📝 Results appended to changelog.md"
fi

echo ""
echo "🔍 Security review complete."
