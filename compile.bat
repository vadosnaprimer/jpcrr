javac -Xlint:unchecked -encoding utf-8 exceptiondefs/ExceptionDefProcessor.java
java exceptiondefs.ExceptionDefProcessor exceptiondefs/exceptions.def
@if errorlevel 1 goto error
javac -Xlint:unchecked -encoding utf-8 JPCApplication.java
javac -Xlint:unchecked -encoding utf-8 org/jpc/plugins/*.java
javac -Xlint:unchecked -encoding utf-8 org/jpc/modules/*.java
javac -Xlint:unchecked -encoding utf-8 org/jpc/luaextensions/*.java
javac -Xlint:unchecked -encoding utf-8 org/jpc/hud/objects/*.java
@goto end
:error
@echo Compilation aborted due to compile errors.
:end
