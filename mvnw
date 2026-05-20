#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup shell script, version 3.3.2
# ----------------------------------------------------------------------------
set -e

WRAPPER_DIR="$(cd "$(dirname "$0")" && pwd)/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_PROPS="$WRAPPER_DIR/maven-wrapper.properties"

if [ -z "$JAVA_HOME" ]; then
    JAVA_EXE="$(command -v java || true)"
else
    JAVA_EXE="$JAVA_HOME/bin/java"
fi

if [ -z "$JAVA_EXE" ] || [ ! -x "$JAVA_EXE" ]; then
    echo "Error: JAVA_HOME is not set and java is not on PATH." >&2
    exit 1
fi

if [ ! -f "$WRAPPER_JAR" ]; then
    WRAPPER_URL="$(grep '^wrapperUrl=' "$WRAPPER_PROPS" | cut -d'=' -f2-)"
    if [ -z "$WRAPPER_URL" ]; then
        echo "Error: wrapperUrl not found in $WRAPPER_PROPS." >&2
        exit 1
    fi
    echo "Downloading Maven Wrapper..."
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -o "$WRAPPER_JAR" "$WRAPPER_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL"
    else
        echo "Error: need curl or wget to bootstrap the Maven Wrapper." >&2
        exit 1
    fi
fi

exec "$JAVA_EXE" \
    -classpath "$WRAPPER_JAR" \
    "-Dmaven.multiModuleProjectDirectory=$(cd "$(dirname "$0")" && pwd)" \
    org.apache.maven.wrapper.MavenWrapperMain "$@"
