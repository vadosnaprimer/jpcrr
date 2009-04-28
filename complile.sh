#!/bin/bash

find -name "*.class" -exec rm {} \;
javac org/jpc/j2se/JPCApplication.java

