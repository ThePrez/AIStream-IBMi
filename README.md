# AIStream-IBMi


Usage notes:

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
