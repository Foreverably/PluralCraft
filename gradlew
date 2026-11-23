#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -n "${JAVA_HOME:-}" ]; then
	JAVA_EXE="$JAVA_HOME/bin/java"
else
	JAVA_EXE=java
fi

if ! command -v "$JAVA_EXE" >/dev/null 2>&1; then
	printf '%s\n' "ERROR: Java was not found. Set JAVA_HOME or add java to PATH." >&2
	exit 1
fi

exec "$JAVA_EXE" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew \
	-jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
