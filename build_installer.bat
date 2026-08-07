@echo off
echo.
echo ========================================================
echo GFA - Generatore Installer Windows (MSI)
echo ========================================================
echo.
echo Avvio della compilazione e pacchettizzazione...
echo Questo processo potra richiedere alcuni minuti, attendere prego.
echo.

call gradlew.bat packageMsi

if %errorlevel% neq 0 (
    echo.
    echo [ERRORE] Generazione dell'installer fallita.
    echo Assicurati di avere installato WiX Toolset V3, necessario per generare MSI.
    echo Puoi scaricarlo da: https://wixtoolset.org/releases/v3.11.2/
) else (
    echo.
    echo ========================================================
    echo [SUCCESSO] Installer MSI generato correttamente!
    echo ========================================================
    echo Lo trovi in: build\compose\binaries\main\msi\
    echo.
)

pause
