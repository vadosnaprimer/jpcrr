#!/bin/bash

find -name "*.class" -exec rm {} \;
javac -Xlint:unchecked JPCApplication.java
javac -Xlint:unchecked ImageLibrary.java
javac -Xlint:unchecked ImageMaker.java
javac -Xlint:unchecked org/jpc/plugins/VirtualKeyboard.java
javac -Xlint:unchecked org/jpc/plugins/PNGDumper.java
javac -Xlint:unchecked org/jpc/plugins/PCMonitor.java
javac -Xlint:unchecked org/jpc/plugins/PCControl.java
javac -Xlint:unchecked org/jpc/plugins/PCRunner.java
javac -Xlint:unchecked org/jpc/plugins/PCStartStopTest.java
