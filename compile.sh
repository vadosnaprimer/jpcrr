#!/bin/bash

find -name "*.class" -exec rm {} \;
javac -Xlint:unchecked JPCApplication.java
javac -Xlint:unchecked ImageLibrary.java
javac -Xlint:unchecked ImageMaker.java

