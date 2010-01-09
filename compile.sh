#!/bin/bash

echo "Cleaning up..."
find -name "*.class" -exec rm {} \;
echo "Compiling exception list processor..."
javac -Xlint:unchecked ExceptionDefProcessor.java || exit 1
echo "Transforming exceptions list..."
java ExceptionDefProcessor exceptions.def || exit 1
echo "Compiling main application..."
javac -Xlint:unchecked JPCApplication.java || exit 1
echo "Compiling ImageMaker..."
javac -Xlint:unchecked ImageMaker.java || exit 1
echo "Compiling plugins..."
javac -Xlint:unchecked org/jpc/plugins/*.java || exit 1
echo "Compiling utilities..."
javac -Xlint:unchecked org/jpc/utils/*.java || exit 1
echo "Compiling SoudTest..."
javac -Xlint:unchecked SoundTest.java || exit 1
echo "Compling streamtools..."
cd streamtools || make
echo "Done."
