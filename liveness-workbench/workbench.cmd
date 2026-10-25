@echo off
rem Runs the Liveness Workbench. Needs a JDK with JavaFX on PATH or in JAVA_HOME (for example Zulu FX 11 or 21)
rem and Maven. Builds the modules, copies the runtime jars (JavaFX comes from the JDK), then starts the app with the
rem repository's model bundle.
rem Optional: set WORKBENCH_RECORDINGS to a folder outside git, and WORKBENCH_CONFIG to a properties file.
setlocal
set ROOT=%~dp0..
call mvn -q -f "%ROOT%\pom.xml" -pl liveness-workbench -am install -DskipTests || exit /b 1
call mvn -q -f "%ROOT%\liveness-workbench\pom.xml" dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\lib || exit /b 1
set PROPS=-Dworkbench.models="%ROOT%\models"
if defined WORKBENCH_RECORDINGS set PROPS=%PROPS% -Dworkbench.recordings="%WORKBENCH_RECORDINGS%"
if defined WORKBENCH_CONFIG set PROPS=%PROPS% -Dworkbench.config="%WORKBENCH_CONFIG%"
set JAVA=java
if defined JAVA_HOME set JAVA="%JAVA_HOME%\bin\java"
%JAVA% %PROPS% -cp "%ROOT%\liveness-workbench\target\classes;%ROOT%\liveness-workbench\target\lib\*" io.mosip.registration.liveness.workbench.WorkbenchApp
