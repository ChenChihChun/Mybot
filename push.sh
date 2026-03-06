#!/bin/bash
cd ~/githup/Mybot

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
