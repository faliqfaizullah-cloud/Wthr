#!/data/data/com.termux/files/usr/bin/bash
# Run inside the Wthr folder in Termux: bash termux_publish.sh
set -e
pkg update -y && pkg install -y git gh
gh auth status >/dev/null 2>&1 || gh auth login
[ -d .git ] || git init -b main
git add -A && git commit -m "${1:-Wthr update}" || true
gh repo view wthr >/dev/null 2>&1 || gh repo create wthr --public --source=. --remote=origin
git push -u origin main
echo "Done. GitHub builds a new numbered release automatically (about 5 minutes). Update from inside the app."
