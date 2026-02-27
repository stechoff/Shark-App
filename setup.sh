#!/bin/bash
# setup.sh - Einmalig ausführen vor dem ersten Build
# Lädt den Gradle Wrapper JAR herunter

set -e

WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "📥 Gradle Wrapper wird heruntergeladen..."
    curl -fL "$WRAPPER_URL" -o "$WRAPPER_JAR"
    echo "✅ Fertig!"
else
    echo "✅ Gradle Wrapper bereits vorhanden."
fi
