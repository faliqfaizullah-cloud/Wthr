#!/data/data/com.termux/files/usr/bin/bash
# Run inside the Wthr folder in Termux: bash termux_publish.sh
set -e
pkg update -y && pkg install -y git gh
gh auth status >/dev/null 2>&1 || gh auth login
[ -d .git ] || git init -b main
git add -A && git commit -m "Wthr weather app" || true
gh repo view wthr >/dev/null 2>&1 || gh repo create wthr --public --source=. --remote=origin
git push -u origin main
git tag -f v1.0.0 && git push -f origin v1.0.0
echo "Done. GitHub Actions builds Wthr.apk; it appears under Releases in ~5 min."
