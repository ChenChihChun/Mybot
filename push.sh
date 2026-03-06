#!/bin/bash
cd ~/githup/Mybot
MSG="${*:-update}"
git add -A
git commit -m "$MSG"
git push origin main
