#!/bin/bash
# 
# This script need to be invoked under BASH. 
# The behaviour of the ps command is different under different shells.
#
# Find out the process entry for the aistream daemon java process. 
# The aistream daemon is started with the following command:
# > java -jar /path/aistream.jar --action DAEMONSTART
#
# The aistream jar file name may contain a version number.
#
daemon_java_ps_output=`/bin/ps -Af | grep "java \-jar " | grep "aistream" | grep "\-\-action DAEMONSTART"`
if [ -z "$daemon_java_ps_output" ]; then
    echo "AIStream daemon java process is not found."
    exit 1
fi

# xargs is used to remove the leading and trailing empty spaces in the ps output.
# For the cut command, we use empty space as the delimiter. The second field is PID. 
daemon_java_pid=`echo $daemon_java_ps_output | xargs | cut -d " " -f 2`
if [ -z "$daemon_java_pid" ]; then
    echo "Invalid PID for AIStream daemon java process."
else
    process_to_kill=${daemon_java_pid}
  
    # If we can find a PASE process for the corresponding java process, then we need to kill the PASE process.
    # The PASE process line contains the "jvmStartPase" signature. It also contains the daemon java process id 
    # in the PPID field. 
    daemon_pase_ps_output=`/bin/ps -Af | grep $daemon_java_pid | grep jvmStartPase`
    if [ -n "${daemon_pase_ps_output}" ]; then
    	daemon_pase_ppid=`echo $daemon_pase_ps_output | xargs | cut -d " " -f 3`
  	if [ "$daemon_pase_ppid" = "$daemon_java_pid" ]; then
      	    daemon_pase_pid=`echo $daemon_pase_ps_output | xargs | cut -d " " -f 2`
      	    if [ -n "${daemon_pase_pid}" ]; then
        	process_to_kill=${daemon_pase_pid}
      	    fi
    	fi
    fi

    # Kill either the java process or the jvm Pase process, depending on whether the PASE process is found.
    kill ${process_to_kill}
    echo "Terminated AIStream daemon process: ${process_to_kill}"
fi
