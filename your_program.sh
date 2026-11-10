#!/bin/sh
#
# Use this script to run your program LOCALLY.
#
# Note: Changing this script WILL NOT affect how CodeCrafters runs your program.
#
# Learn more: https://codecrafters.io/program-interface

set -e # Exit early if any commands fail

# Local only: the pom targets Java 25. If JDK 25 is not installed, use JDK 21.
# CodeCrafters builds remotely with its own setup, so this does not affect it.
JAVA_RELEASE_ARGS=""
# java_home -v 25 can return an older JDK, so check the real major version.
JH=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
if [ -n "$JH" ] && "$JH/bin/java" -version 2>&1 | grep -q 'version "25'; then
  export JAVA_HOME="$JH"
else
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  JAVA_RELEASE_ARGS="-Dmaven.compiler.source=21 -Dmaven.compiler.target=21"
fi
export PATH="$JAVA_HOME/bin:$PATH"

# Copied from .codecrafters/compile.sh
#
# - Edit this to change how your program compiles locally
# - Edit .codecrafters/compile.sh to change how your program compiles remotely
(
  cd "$(dirname "$0")" # Ensure compile steps are run within the repository directory
  mvn -q -B package $JAVA_RELEASE_ARGS -Ddir=/tmp/codecrafters-build-sqlite-java
)

# Copied from .codecrafters/run.sh
#
# - Edit this to change how your program runs locally
# - Edit .codecrafters/run.sh to change how your program runs remotely
exec java --enable-preview -jar /tmp/codecrafters-build-sqlite-java/codecrafters-sqlite.jar "$@"
