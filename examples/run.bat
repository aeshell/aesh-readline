@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Resolve repository paths from this script location.
for %%I in ("%~dp0.") do set "SCRIPT_DIR=%%~fI"
for %%I in ("%SCRIPT_DIR%\..") do set "ROOT_DIR=%%~fI"
set "CLASSES_DIR=%ROOT_DIR%\examples\target\classes"

if not exist "%CLASSES_DIR%\" (
  echo Missing directory: %CLASSES_DIR%
  echo Build examples first ^(eg: run 'mvn package' from the root of the project^).
  exit /b 1
)

set /a count=0
for /f "delims=" %%F in ('dir /b /s /a:-d "%CLASSES_DIR%\*Example.class" 2^>nul') do (
  set /a count+=1
  set "file!count!=%%~fF"
)

if %count% EQU 0 (
  echo No classes ending with Example.class found in %CLASSES_DIR%
  exit /b 1
)

echo Available examples:
for /L %%I in (1,1,%count%) do (
  set "full=!file%%I!"
  set "rel=!full:%CLASSES_DIR%\=!"
  set "name=!rel:\=.!"
  set "name=!name:.class=!"
  echo  %%I^) !name!
)

set "choice="
set /p "choice=Select a number to run: "
if not defined choice (
  echo Invalid selection.
  exit /b 1
)

echo(%choice%| findstr /r "^[1-9][0-9]*$" >nul || (
  echo Invalid selection.
  exit /b 1
)

if %choice% GTR %count% (
  echo Invalid selection.
  exit /b 1
)

set "full=!file%choice%!"
set "rel=!full:%CLASSES_DIR%\=!"
set "name_of_class=!rel:\=.!"
set "name_of_class=!name_of_class:.class=!"

pushd "%ROOT_DIR%" >nul
set "classpath=examples/target/classes;terminal-tty/target/terminal-tty-3.4-dev.jar;terminal-api/target/terminal-api-3.4-dev.jar;readline/target/readline-3.4-dev.jar"
java -cp "%classpath%" "%name_of_class%"
set "rc=%ERRORLEVEL%"
popd >nul

exit /b %rc%

