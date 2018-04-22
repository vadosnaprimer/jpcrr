#!/bin/bash

unset CLASSPATH &&
unset GIT_DIR &&
rm -rf jpcrr-build
mkdir jpcrr-build &&
git archive --format=zip --prefix=src/ HEAD ./ >jpcrr-build/sources.zip &&
cd jpcrr-build &&
unzip sources.zip &&
cp src/build-files/BRIEF-INSTALLATION-INSTRUCTIONS . &&
echo "java -jar jpcrr-${2}.jar" >start.bat &&
cp src/assemble.jpcrrinit . &&
mkdir datafiles &&
cp src/datafiles/extramenu datafiles/extramenu &&
cp src/LICENSE . &&
mkdir docs &&
cp src/docs/manual.txt docs &&
mkdir lua &&
cp --recursive src/lua/* lua &&
#cp --recursive ${1}/* . &&
cd src &&
./compile.sh &&
jar cvfm jpcrr.jar build-files/manifest.mod `find -name "*.class"` `find datafiles/keyboards` datafiles/luakernel &&
cd .. &&
cp src/jpcrr.jar . &&
rm -rf src &&
zip -r jpcrr-precompiled.zip * &&
cd .. &&
cp jpcrr-build/jpcrr-precompiled.zip . &&
rm -rf jpcrr-build
