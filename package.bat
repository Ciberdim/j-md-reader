@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   j-md-reader EXE Packager (jpackage wrapper)
echo ===================================================
echo.

:: 1. Ensure the shaded JAR is built
if not exist "target\j-md-reader-1.0-SNAPSHOT.jar" (
    echo Shaded JAR not found. Building project first...
    call mvn package
    if !ERRORLEVEL! neq 0 (
        echo.
        echo [ERROR] Maven build failed. Cannot proceed with packaging.
        pause
        exit /b !ERRORLEVEL!
    )
)

:: 2. Locate jpackage executable
set JPACKAGE_CMD=jpackage
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\jpackage.exe" (
        set JPACKAGE_CMD="%JAVA_HOME%\bin\jpackage.exe"
    )
)

echo Using packaging tool: !JPACKAGE_CMD!
echo.

:: 3. Clean up previous distribution folder
if exist "target\dist" (
    echo Cleaning up previous build folder...
    rmdir /s /q "target\dist"
)

:: 4. Detect Icon
set ICON_ARG=
if exist "mark.ico" (
    echo Detected mark.ico, using it as application icon.
    set ICON_ARG=--icon mark.ico
) else if exist "src\main\resources\images\mark.ico" (
    echo Detected src\main\resources\images\mark.ico, using it as application icon.
    set ICON_ARG=--icon src\main\resources\images\mark.ico
) else (
    echo [INFO] No mark.ico found. To add a custom executable icon,
    echo        place a "mark.ico" file in the project root folder.
    echo        Proceeding with default icon...
    echo.
)

:: 5. Set up clean staging input directory to prevent infinite recursion
if exist "target\input" rmdir /s /q "target\input"
mkdir "target\input"
copy "target\j-md-reader-1.0-SNAPSHOT.jar" "target\input\" > nul

:: 6. Execute jpackage to create a self-contained EXE directory
echo Packaging application into target\dist\MarkdownReader...
!JPACKAGE_CMD! --type app-image ^
               --input target\input ^
               --dest target\dist ^
               --name "MarkdownReader" ^
               --main-jar j-md-reader-1.0-SNAPSHOT.jar ^
               --main-class org.ciberdim.mdreader.Main ^
               !ICON_ARG!

if !ERRORLEVEL! neq 0 (
    echo.
    echo [ERROR] jpackage execution failed.
    pause
    exit /b !ERRORLEVEL!
)

echo.
echo ===================================================
echo   SUCCESS! Standalone EXE built.
echo ===================================================
echo Folder:   %~dp0target\dist\MarkdownReader
echo Launcher: %~dp0target\dist\MarkdownReader\MarkdownReader.exe
echo ===================================================
echo.
pause
