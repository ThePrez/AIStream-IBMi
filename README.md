# AIStream-IBMi


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
     --table  <tablename>   The name of the table    (required for ADD/GET/REMOVE actions)
     --schema <schemaname>  The schema of the table  (required for ADD/GET/REMOVE actions)
```

### Examples:
```bash
# List currently monitored tables
java -jar aistream.jar --action LIST

# Add a table to monitoring
java -jar aistream.jar --action ADD --schema AITESTLIB --table AITESTTABLE

# Get monitoring info for specified table.  Note the escpaed double quotes required when specifying a delimited name.
java -jar aistream.jar --action GET --schema AITESTLIB --table \"\"\"AI Test Table\"\"\"
```