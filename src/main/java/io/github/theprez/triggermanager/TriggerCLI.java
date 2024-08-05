package io.github.theprez.triggermanager;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.ibm.as400.access.AS400;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

public class TriggerCLI {
    private enum CLIActions {
        /** List the tables currently being monitored */
        LIST,
        /** Add the table to monitoring */
        ADD,
        /** Get monitoring details for the table */
        GET,
        /** Remove the table from monitoring */
        REMOVE
    }

    public static void main(String[] _args) {
        // TODO The library where the triggers, variables, and data queues are saved needs to be configurable
        final String LIBRARY = "triggerman";

        LinkedList<String> argsList = new LinkedList<>(Arrays.asList(_args));
        AppLogger logger = AppLogger.getSingleton(argsList.remove("-v") );

        if (argsList.isEmpty()) {
            logger.println_err("ERROR: input arguments are required");
            System.exit(17);
        }

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
                        schema = argsList.removeFirst();
                        break;
                    case "--table":
                        table = argsList.removeFirst();
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
        //
        boolean isInputOk = true;
        if (Objects.isNull(action)) {
            logger.println_err("ERROR: No action specified");
            isInputOk = false;
        } else if (CLIActions.LIST != action) {
            if (StringUtils.isEmpty(table)) {
                logger.println_err("ERROR: No table specified");
                isInputOk = false;
            }
            if (StringUtils.isEmpty(schema)) {
                logger.println_err("ERROR: No schema specified");
                isInputOk = false;
            }
        }
        // TODO need to normalize the schema and table names.  If not delimited, convert to uppercase.
        if (!isInputOk) {
            System.exit(19);
        }
        try (AS400 as400 = IBMiDotEnv.getCachedSystemConnection(true)) {
            SelfInstaller installer = new SelfInstaller(logger, as400, LIBRARY);
            installer.install();
            TriggerManager tMan = new TriggerManager(logger, as400, LIBRARY);
            // tMan.createTrigger("JES", "simple");
            switch (action) {
                case ADD:
                    TriggerDescriptor newTrigger = tMan.createTrigger(schema, table);
                    logger.println_success("Trigger added: " + newTrigger);
                    break;
                case GET:
                    TriggerDescriptor existingTrigger = tMan.getExistingTriggerForTable(schema, table);
                    if (null == existingTrigger) {
                        logger.println("No triggers installed");
                    } else {
                        logger.println(existingTrigger.toString());
                    }
                    break;
                case REMOVE:
                    TriggerDescriptor deletedTrigger = tMan.deleteTriggerFromTable(schema, table);
                    if (null != deletedTrigger) {
                        logger.println_success("Trigger deleted: " + deletedTrigger);
                    }
                    break;
                default:
                    logger.println("Listing triggers....");
                    List<TriggerDescriptor> triggerList = tMan.listTriggers();
                    if(triggerList.isEmpty()) {
                        logger.println_warn("No triggers installed");
                    }
                    for (TriggerDescriptor l : triggerList) {
                        System.out.println("       " + l);
                    }
            }

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
            // System.out.println(" " + l);
            // }
            // tMan.deleteTriggerFromTable("qiws", "qcustcdt");
        } catch (Exception e) {
            logger.println_err(e.getLocalizedMessage());
            logger.printExceptionStack_verbose(e);
        }
    }
}
