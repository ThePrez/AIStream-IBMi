#!/bin/sh
#
# The parent directory of the current script
PARENT_PATH=$(dirname "$0")

# Find the aistream jar. The jar file name may include a version number.
aistream_jar=`find ${PARENT_PATH}/../aistream*.jar`

# Exit from the script if the jar file is not found.
if [ -z "${aistream_jar}" ]; then	
    echo "AIStream jar is not found."  
    exit 1
fi

# If JAVA_HOME is set, then use the java command under $JAVA_HOME/bin, otherwise just use "java".
java_command="java"
if [ -n "${JAVA_HOME}" ]; then
    java_command=${JAVA_HOME}/bin/java
fi

exec ${java_command} -jar ${aistream_jar} --action DAEMONSTART -v
