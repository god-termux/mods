#!/usr/bin/env bash
# Собирает моды из mods/<имя>/ в site/: <имя>-<версия>.zip, index.json, index.html.
#   mods/<имя>/mod.json      описание (name, version, title, description, icon, main, ...)
#   mods/<имя>/java/**       исходники (необязательно): javac + d8 -> classes.dex
#   mods/<имя>/lib/**        нативные библиотеки (необязательно, попадают в zip как есть)
# Нужны: JDK 17+, Android SDK (ANDROID_HOME: build-tools с d8 и platforms/android-*/android.jar), python3, zip.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd); SITE=$ROOT/site; WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
BASE_URL=${BASE_URL:-https://god-termux.github.io/mods}
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}; [ -n "$SDK" ] || { echo "нет ANDROID_HOME" >&2; exit 1; }
JAR=$(ls -d "$SDK"/platforms/android-*/android.jar | sort -V | tail -1)
D8=$(ls -d "$SDK"/build-tools/*/d8 | sort -V | tail -1)
rm -rf "$SITE"; mkdir -p "$SITE/mods"

# интерфейс Mod из APK god-termux: только для компиляции
mkdir -p "$WORK/stubs"; javac --release 8 -cp "$JAR" -d "$WORK/stubs" "$ROOT/stubs/com/termux/mod/Mod.java" 2>/dev/null

for dir in "$ROOT"/mods/*/; do
  name=$(basename "$dir"); stage=$WORK/pkg-$name; mkdir -p "$stage"
  cp "$dir/mod.json" "$stage/"
  python3 - "$dir/mod.json" "$name" <<'PY'
import json,sys
j=json.load(open(sys.argv[1]))
assert j.get("name")==sys.argv[2], "name в mod.json должен совпадать с именем каталога"
assert isinstance(j.get("version"),int), "version: целое число"
PY
  if [ -d "$dir/java" ]; then
    mkdir -p "$WORK/cls-$name"
    find "$dir/java" -name '*.java' > "$WORK/src-$name.txt"
    javac --release 8 -Xlint:-options -cp "$JAR:$WORK/stubs" -d "$WORK/cls-$name" @"$WORK/src-$name.txt"
    find "$WORK/cls-$name" -name '*.class' > "$WORK/cls-$name.txt"
    "$D8" --release --min-api 26 --lib "$JAR" --classpath "$WORK/stubs" --output "$stage" @"$WORK/cls-$name.txt"
  fi
  [ -d "$dir/lib" ] && cp -r "$dir/lib" "$stage/lib"
  ver=$(python3 -c "import json;print(json.load(open('$dir/mod.json'))['version'])")
  find "$stage" -exec touch -d '2020-01-01 00:00:00' {} +   # воспроизводимый архив
  (cd "$stage" && find . -type f | sort | sed 's|^\./||' | zip -q -X -@ "$SITE/mods/$name-$ver.zip")
  echo "собран $name $ver ($(du -h "$SITE/mods/$name-$ver.zip" | cut -f1))"
done

python3 - "$ROOT" "$SITE" "$BASE_URL" <<'PY'
import json,sys,os,hashlib,glob
root,site,base=sys.argv[1:4]
mods=[]
for d in sorted(glob.glob(root+"/mods/*/")):
    j=json.load(open(d+"mod.json")); n=j["name"]; v=j["version"]
    f=f"{site}/mods/{n}-{v}.zip"; b=open(f,"rb").read()
    mods.append({"name":n,"version":v,"title":j.get("title",n),"description":j.get("description",""),"icon":j.get("icon","🧩"),
                 "url":f"{base}/mods/{n}-{v}.zip","sha256":hashlib.sha256(b).hexdigest(),"size":len(b)})
json.dump({"format":1,"mods":mods},open(site+"/index.json","w"),ensure_ascii=False,indent=1)
rows="".join(f"<li><b>{m['icon']} {m['title']}</b> ({m['name']} v{m['version']}) — {m['description']} <a href=\"mods/{m['name']}-{m['version']}.zip\">zip</a></li>" for m in mods)
open(site+"/index.html","w").write(f"<!doctype html><meta charset=utf-8><title>god-termux mods</title><h1>Моды god-termux</h1><p>Каталог для приложения: <a href=index.json>index.json</a>. Исходники: <a href=https://github.com/god-termux/mods>github.com/god-termux/mods</a>.</p><ul>{rows}</ul>")
PY
