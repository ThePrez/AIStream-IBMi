# AIStream-IBMi

### Installation
The aistream driver contains the following files:

    Under /opt/aistream/bin:
        startDaemon.sh    Script used to start the monitoring daemon
        stopDaemon.sh     Script used to stop the monitoring daemon
    Under /opt/aistream:
        aistream.jar

The aistream jar file may contain a version number in the file name.

### Configuration settings
The default configuration file for aistream is located on the following path:

    /QOpenSys/etc/aistream/main.conf

You can use environment variable **AISTREAM_CONFIG_FILE** to override the fully qualified path of the configuration file.

The configuration file is a text file that contains multiple key/value pairs. Each line in the text file is in "key=value" format.

Here is the list of all supported keys:

    TRIGGER_MANAGER_LIBRARY     The library where the triggers, variables, and data queues are saved
    KAFKA_BROKER_URI            The Kafka broker uri
    IBMI_HOSTNAME               The IBM i host for the JDBC connection
    IBMI_USERNAME               The user name for the JDBC connection
    IBMI_PASSWORD               The password for the JDBC connection user

To override a setting in the configuration file, you can set an environment variable that has the same name as the key name.

### Usage notes:

```bash
java -jar aistream.jar [[option] ... ]
   Options include:
     --action <action>      The action to perform
              LIST          Lists all monitored tables
              ADD           Add the specified table to monitoring
              GET           Get monitoring status of the specified table
              REMOVE        Remove the specified table from monitoring
              DAEMONSTART   Starts Kafka routing for monitored tables
              DAEMONSTOP    Stop the monitoring daemon
     --table  <tablename>   The name of the table    (required for ADD/GET/REMOVE actions)
     --schema <schemaname>  The schema of the table  (required for ADD/GET/REMOVE actions)

In addition to the java command, you can also use the builtin scripts startDaemon.sh and stopDaemon.sh to start and stop the monitoring daemon.
```

### Examples:
```bash
# List currently monitored tables
java -jar aistream.jar --action LIST

# Add a table to monitoring
java -jar aistream.jar --action ADD --schema AITESTLIB --table AITESTTABLE

# Get monitoring info for specified table.
# Note the escpaed double quotes required when specifying a delimited name.
java -jar aistream.jar --action GET --schema AITESTLIB --table \"\"\"AI Test Table\"\"\"
```
