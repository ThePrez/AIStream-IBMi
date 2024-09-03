package io.github.theprez.triggermanager;

import java.sql.Connection;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCDataSource;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

public final class TriggerCLI {
    private static AppLogger logger;

    private TriggerCLI() {
        // No instances, utility class consisting of static methods and variables
    }

    enum CLIActions {
        /** List the tables currently being monitored */
        LIST(false, false),
        /** Add the table to monitoring */
        ADD(true, false),
        /** Get monitoring details for the table */
        GET(true, false),
        /** Remove the table from monitoring */
        REMOVE(true, false),
        /** Start the router job */
        DAEMONSTART(false, true);

        private final boolean m_isTableAndSchemaRequired;
        private final boolean m_isKafkaBrokerRequired;
        CLIActions(boolean bTableAndSchema, boolean bKafkaBroker) {
            m_isTableAndSchemaRequired = bTableAndSchema;
            m_isKafkaBrokerRequired = bKafkaBroker;
        }
        boolean isTableAndSchemaRequired() {
            return m_isTableAndSchemaRequired;
        }
        boolean isKafkaBrokerRequired() {
            return m_isKafkaBrokerRequired;
        }
    }

    public static void main(String[] _args) {
        LinkedList<String> argsList = new LinkedList<>(Arrays.asList(_args));
        logger = AppLogger.getSingleton(argsList.remove("-v"));

        if (argsList.isEmpty()) {
            // TODO print out the CLI instructions or point the user to the README
            logFatalErrorAndExit("ERROR: input arguments are required");
        }

        ///////////////////////////////////////////////
        // Scrape the command line arguments
        ///////////////////////////////////////////////
        CLIActions action = null;
        String schemaName = null;
        String tableName = null;
        try {
            while (!argsList.isEmpty()) {
                String currentArg = argsList.removeFirst();
                switch (currentArg) {
                    case "--action":
                        action = CLIActions.valueOf(argsList.removeFirst().trim().toUpperCase());
                        break;
                    case "--schema":
                        schemaName = normalizeName(argsList.removeFirst());
                        break;
                    case "--table":
                        tableName = normalizeName(argsList.removeFirst());
                        break;
                    default:
                        logFatalErrorAndExit(String.format("Unrecognized argument: '%s'", currentArg));
                        break;
                }
            }
        } catch (NoSuchElementException oops) {
            logFatalErrorAndExit("ERROR: malformed input arguments");
        }

        ///////////////////////////////////////////////
        // Validate input
        ///////////////////////////////////////////////
        if (action == null) {
            logFatalErrorAndExit("ERROR: No action specified");
            return; // Not necessary at runtime, just makes the IDE happy knowing we aren't dereferencing a null pointer when processing the requested action
        } else if (action.isTableAndSchemaRequired()) {
            if (StringUtils.isEmpty(tableName)) {
                logFatalErrorAndExit("ERROR: No table specified");
            }
            if (StringUtils.isEmpty(schemaName)) {
                logFatalErrorAndExit("ERROR: No schema specified");
            }
        }
        TriggerConfigurationFile configFile = TriggerConfigurationFile.getInstance(logger);
        if (configFile == null) {
            logFatalErrorAndExit("ERROR: AIStream configuration file is not found.");
        } else if (!configFile.validate(action)) {
            logFatalErrorAndExit("ERROR: Invalid content in AIStream configuration file.");
        }

        ///////////////////////////////////////////////
        // Validation successful, process the request
        ///////////////////////////////////////////////

        try (AS400 as400 = IBMiDotEnv.getCachedSystemConnection(true);
             Connection connection = new AS400JDBCDataSource(as400).getConnection()) {

            TableDescriptor table = null;
            if (action.isTableAndSchemaRequired()) {
                table = TableDescriptor.lookup(schemaName, tableName, connection);
                if (table == null) {
                    logger.println_err("ERROR: Specified table could not found.");
                    return;
                }
            }

            TriggerManager tMan = new TriggerManager(as400, connection, logger);
            switch (action) {
                case ADD:
                    TriggerDescriptor newTrigger = tMan.createTrigger(table);
                    logger.println_success("Table monitoring started: " + newTrigger);
                    break;
                case GET:
                    TriggerDescriptor existingTrigger = tMan.getExistingTriggerForTable(table);
                    if (null == existingTrigger) {
                        logger.println("No tables currently monitored");
                    } else {
                        logger.println(existingTrigger.toString());
                    }
                    break;
                case REMOVE:
                    TriggerDescriptor deletedTrigger = tMan.deleteTriggerFromTable(table);
                    if (null != deletedTrigger) {
                        logger.println_success("Table no longer monitored: " + deletedTrigger);
                    }
                    break;
                case DAEMONSTART:
                    new TriggerDaemon(logger, tMan).start();
                    break;
                case LIST:
                default:
                    logger.println("Listing...");
                    List<TriggerDescriptor> triggerList = tMan.listTriggers();
                    if (triggerList.isEmpty()) {
                        logger.println_warn("No tables currently monitored");
                    }
                    for (TriggerDescriptor l : triggerList) {
                        logger.println("       " + l);
                    }
            }

            // TODO move to test suite
            // tMan.createTrigger("JES", "simple");
            // TriggerDescriptor deleted = tMan.deleteTriggerFromTable("jesseg", "simple");
            // if (null != deleted) {
            // logger.println_success("Trigger deleted: " + deleted);
            // }
            // TriggerDescriptor trigger = tMan.createTrigger("jesseg", "simple");
            // logger.println_success("Successfully created trigger: " + trigger);
            // logger.println();
            // logger.println();
            // logger.println();
            // logger.println("Listing triggers....");
            // for (TriggerDescriptor l : tMan.listTriggers()) {
            // logger.println(" " + l);
            // }
            // tMan.deleteTriggerFromTable("qiws", "qcustcdt");
        } catch (Exception e) {
            logger.println_err("ERROR: " + e.getClass().getSimpleName() +  " -> " + e.getLocalizedMessage());
            logger.printExceptionStack_verbose(e);
        }
    }

    private static String normalizeName(final String name) {
        // If null return an empty string
        if (name == null) {
            return "";
        }
        // If delimited just return it
        if (name.startsWith("\"") && name.endsWith("\"")) {
            return name;
        }
        // Otherwise fold to uppercase. The user must explicitly delimit names that require it.
        return name.toUpperCase();
    }

    private static void logFatalErrorAndExit(final String message) {
        logger.println_err(message);
        // Terminate the JVM
        System.exit(17);
    }
}
