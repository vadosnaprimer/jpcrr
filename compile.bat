javac -Xlint:unchecked -encoding utf-8 exceptiondefs/ExceptionDefProcessor.java
java exceptiondefs.ExceptionDefProcessor exceptiondefs/exceptions.def
javac -Xlint:unchecked -encoding utf-8 JPCApplication.java
javac -Xlint:unchecked -encoding utf-8 ImageMaker.java
javac -Xlint:unchecked -encoding utf-8 org/jpc/plugins/*.java
javac -Xlint:unchecked -encoding utf-8 org/jpc/modules/*.java
javac -Xlint:unchecked -encoding utf-8 org/jpc/luaextensions/*.java
