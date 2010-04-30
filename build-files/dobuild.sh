#!/bin/bash

unset CLASSPATH &&
unset GIT_DIR &&
mkdir /tmp/jpcrr-build &&
cd /tmp/jpcrr-build &&
git archive --format=zip --prefix=src/ --remote=$1 JPC-RR-${2} >sources.zip &&
unzip sources.zip &&
cp src/build-files/BRIEF-INSTALLATION-INSTRUCTIONS . &&
cp src/build-files/start-jpcrr.bat . &&
cp src/build-files/start-jpcrr.sh . &&
cp src/assemble.bat . &&
cp src/extramenu . &&
cp src/manual.txt . &&
mkdir lua &&
cp --recursive src/lua/* lua &&
cd src &&
./compile.sh &&
jar cvfm jpcrr-${2}.jar build-files/manifest.mod `find -name "*.class"` &&
cd .. &&
cp src/jpcrr-${2}.jar . &&
rm -rf src &&
zip -r jpcrr-${2}-precompiled.zip * &&
cd .. &&
cp jpcrr-build/jpcrr-${2}-precompiled.zip . &&
rm -rf jpcrr-build
