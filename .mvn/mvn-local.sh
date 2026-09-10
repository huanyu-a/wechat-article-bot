#!/bin/sh
# Local Maven launcher for offline / restricted-network environments.
#
# Why: `./mvnw` downloads the Maven distribution from repo.maven.apache.org on first use.
# When that host is unreachable (offline or restricted network) the wrapper fails before the
# build starts. This script reuses an already-extracted Maven distribution from the local
# Maven wrapper cache, so the normal `test` / `compile` / `package` commands keep working.
#
# Usage:
#   ./.mvn/mvn-local.sh -o test          # -o = offline, use only the local repository
#   ./.mvn/mvn-local.sh test             # online (resolves missing dependencies)
#
# Resolution order for the Maven home:
#   1. $MAVEN_HOME_DIR if set
#   2. any extracted distribution under $HOME/.m2/wrapper/dists/apache-maven-*/<hash>
#   3. fall back to ./mvnw
set -e

PROJECT_DIR_WIN=$(cygpath -w "$(pwd)" 2>/dev/null || pwd)
JAVA_BIN="${JAVA_BIN:-${JAVA_HOME:-/c/Program Files/Java/jdk-17}/bin/java}"
if [ ! -x "$JAVA_BIN" ]; then
  JAVA_BIN="java"
fi

if [ -z "$MAVEN_HOME_DIR" ]; then
  MAVEN_HOME_DIR=$(ls -d "$HOME"/.m2/wrapper/dists/apache-maven-*/*/ 2>/dev/null | head -1)
fi
# 某些环境（如沙箱 HOME）与真实用户目录不同，补充搜索常见位置
if [ -z "$MAVEN_HOME_DIR" ]; then
  MAVEN_HOME_DIR=$(ls -d /c/Users/*/.m2/wrapper/dists/apache-maven-*/*/ 2>/dev/null | head -1)
fi

if [ -n "$MAVEN_HOME_DIR" ] && [ -f "$MAVEN_HOME_DIR/boot/plexus-classworlds-2.11.0.jar" ]; then
  MAVEN_HOME_WIN=$(cygpath -w "$MAVEN_HOME_DIR" 2>/dev/null || echo "$MAVEN_HOME_DIR")
  exec "$JAVA_BIN" \
    -cp "$MAVEN_HOME_WIN\\boot\\plexus-classworlds-2.11.0.jar" \
    -Dclassworlds.conf="$MAVEN_HOME_WIN\\bin\\m2.conf" \
    -Dmaven.home="$MAVEN_HOME_WIN" \
    -Dmaven.multiModuleProjectDirectory="$PROJECT_DIR_WIN" \
    org.codehaus.plexus.classworlds.launcher.Launcher "$@"
fi

echo "未找到本地 Maven 发行包，改用 ./mvnw（需要网络）" >&2
exec "$(dirname "$0")/../mvnw" "$@"
