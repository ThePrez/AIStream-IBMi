package io.github.theprez.triggermanager;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.ibm.as400.access.AS400;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

public final class TriggerCLI {
static AppLogger logger;

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
        // TODO document how delimited names must be specified --action add --schemaName \"\"\"Schema with delimited name\"\" --tableName \"\"\"Table with delimited name\"\"
        LinkedList<String> argsList = new LinkedList<>(Arrays.asList(_args));
        AppLogger logger = AppLogger.getSingleton(argsList.remove("-v"));

        if (argsList.isEmpty()) {
            // TODO print out the CLI instructions or point the user to the README
            logger.println_err("ERROR: input arguments are required");
            System.exit(17);
        }

        ///////////////////////////////////////////////
        // Scrape the command line arguments
        ///////////////////////////////////////////////
        CLIActions action = null;
        String schema = null;
        String table = null;
        try {
            while (!argsList.isEmpty()) {
                String currentArg = argsList.removeFirst();
                switch (currentArg) {
                    case "--action":
                        action = CLIActions.valueOf(argsList.removeFirst().trim().toUpperCase());
                        break;
                    case "--schema":
                        schema = normalizeName(argsList.removeFirst());
                        break;
                    case "--table":
                        table = normalizeName(argsList.removeFirst());
                        break;
                    default:
                        logger.printfln_err("Unrecognized argument: '%s'", currentArg);
                        System.exit(19);
                }
            }
        } catch (NoSuchElementException oops) {
            logger.println_err("ERROR: malformed input arguments");
            System.exit(17);
        }

        // validate inputs
        boolean isInputOk = true;
        if (action == null) {
            logger.println_err("ERROR: No action specified");
            isInputOk = false;
        } else if (action.isTableAndSchemaRequired()) {
            if (StringUtils.isEmpty(table)) {
                logger.println_err("ERROR: No table specified");
                isInputOk = false;
            }
            if (StringUtils.isEmpty(schema)) {
                logger.println_err("ERROR: No schema specified");
                isInputOk = false;
            }
        }
        // TODO need to normalize the schema and table names. If not delimited, convert to uppercase.
        if (!isInputOk) {
            System.exit(19);
        }

        TriggerConfigurationFile configFile = TriggerConfigurationFile.getInstance(logger);
        if (configFile == null) {
            logger.println_err("ERROR: AIStream configuration file is not found.");
            System.exit(19);
        }
        if (!configFile.validate(action)) {
            logger.println_err("ERROR: Invalid content in AIStream configuration file.");
            System.exit(19);
        }

        // The library where the triggers, variables, and data queues are saved
        final String triggermanLibrary = configFile.getTriggerManagerLibrary();

        try (AS400 as400 = IBMiDotEnv.getCachedSystemConnection(true)) {
            SelfInstaller installer = new SelfInstaller(logger, as400, triggermanLibrary);
            installer.install();
            TriggerManager tMan = new TriggerManager(logger, as400, triggermanLibrary);
            switch (action) {
                case ADD:
                    TriggerDescriptor newTrigger = tMan.createTrigger(schema, table);
                    logger.println_success("Table monitoring started: " + newTrigger);
                    break;
                case GET:
                    TriggerDescriptor existingTrigger = tMan.getExistingTriggerForTable(schema, table);
                    if (null == existingTrigger) {
                        logger.println("No tables currently monitored");
                    } else {
                        logger.println(existingTrigger.toString());
                    }
                    break;
                case REMOVE:
                    TriggerDescriptor deletedTrigger = tMan.deleteTriggerFromTable(schema, table);
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
}
