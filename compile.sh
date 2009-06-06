#!/bin/bash

find -name "*.class" -exec rm {} \;
javac JPCApplication.java
javac ImageLibrary.java
javac ImageMaker.java

