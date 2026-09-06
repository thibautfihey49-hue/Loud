#!/bin/sh
PRG="$0"
while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : ".*-> \(.*\)$"`
    if expr "$link" : "/.*" > /dev/null; then PRG="$link"; else PRG=`dirname "$PRG"`/"$link"; fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
cygwin=false; msys=false; darwin=false; nonstop=false
case "`uname`" in CYGWIN*) cygwin=true;; Darwin*) darwin=true;; MINGW*|MSYS*) msys=true;; NONSTOP*) nonstop=true;; esac
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then JAVACMD="$JAVA_HOME/jre/sh/java"
    else JAVACMD="$JAVA_HOME/bin/java"; fi
    if [ ! -x "$JAVACMD" ]; then echo "ERROR: JAVA_HOME invalide: $JAVA_HOME"; exit 1; fi
else
    JAVACMD="java"
    if ! command -v java >/dev/null 2>&1; then echo "ERROR: Java non trouvé"; exit 1; fi
fi
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
