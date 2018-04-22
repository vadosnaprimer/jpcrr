#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

unset CLASSPATH
unset GIT_DIR
rm -rf jpcrr-build
mkdir jpcrr-build
git archive --format=zip --prefix=src/ HEAD ./ >jpcrr-build/sources.zip
cd jpcrr-build
unzip sources.zip
echo "java -jar jpcrr.jar" >start.bat
cp src/assemble.jpcrrinit .
mkdir datafiles
cp src/datafiles/extramenu datafiles/extramenu
cp src/LICENSE .
mkdir docs
cp src/docs/manual.txt docs
mkdir lua
cp --recursive src/lua/* lua
cd src
./compile.sh
cp streamtools/dumpconvert.exe ..
jar cvfm jpcrr.jar build-files/manifest.mod `find -name "*.class"` `find datafiles/keyboards` datafiles/luakernel
cd ..
cp src/jpcrr.jar .
rm -rf src
zip -r jpcrr-precompiled.zip *
cd ..
cp jpcrr-build/jpcrr-precompiled.zip .
rm -rf jpcrr-build
