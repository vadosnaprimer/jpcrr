#!/bin/bash

find -name "*.class" -exec rm {} \;
javac -Xlint:unchecked JPCApplication.java
javac -Xlint:unchecked ImageLibrary.java
javac -Xlint:unchecked ImageMaker.java
javac -Xlint:unchecked org/jpc/plugins/*.java
javac -Xlint:unchecked org/jpc/utils/*.java
javac -Xlint:unchecked SoundTest.java
