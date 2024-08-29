package io.github.theprez.triggermanager;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

import com.github.theprez.jcmdutils.AppLogger;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;
import io.github.theprez.triggermanager.TriggerCLI.CLIActions;

public class TriggerConfigurationFile implements ITriggerConfigurationConstants {
    // Default value of the trigger manager library
    private static final String DEFAULT_TRIGGER_MANAGER_LIBRARY = "triggerman";

    private static TriggerConfigurationFile fInstance;
    private final Properties mProperties;
    private final AppLogger mLogger;

    private TriggerConfigurationFile(AppLogger _logger) {
        mProperties = new Properties();
        mLogger = _logger;
    }

    public static TriggerConfigurationFile getDefault(AppLogger _logger) {
        if (Objects.isNull(fInstance)) {
            String configPath = IBMiDotEnv.getDotEnv().get(ENV_AISTREAM_CONFIG_FILE,
                    IBMiDotEnv.isIBMi() ? DEFAULT_CONFIG_PATH : "");
            if (!configPath.isEmpty()) {
                TriggerConfigurationFile configFile = new TriggerConfigurationFile(_logger);
                try {
                    configFile.load(configPath);
                    fInstance = configFile;
                } catch (IOException ex) {
                    _logger.printfln_err("Exception thrown: %s->%s", ex.getClass().getSimpleName(),
                            ex.getLocalizedMessage());
                }
            } else {
                _logger.println("Environment variable is not set: " + ENV_AISTREAM_CONFIG_FILE);
            }
        }
        return fInstance;
    }

    private void load(String _configPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(_configPath)) {
            mProperties.load(fis);
        }
    }

    /**
     * Validate the configuration file. Return true if the contents are valid.
     */
    boolean validate(CLIActions action) {
        // Validate the KAFKA_BROKER_URI. It is only required for the daemon action.
        if (!mProperties.containsKey(KEY_KAFKA_BROKER_URI)) {
            if (action.isKafkaBrokerRequired()) {
                mLogger.printfln_err("Error: Property is not set in configuration file: %s", KEY_KAFKA_BROKER_URI);
                return false;
            }
            // Log as a warning and continue validation
            mLogger.printfln_warn("Warning: Property is not set in configuration file: %s", KEY_KAFKA_BROKER_URI);
        }

        if (!mProperties.containsKey(KEY_TRIGGER_MANAGER_LIBRARY)) {
            mLogger.printfln_warn("Warning: Property '%s' is not set in configuration file. Using default value '%s'.",
                    KEY_TRIGGER_MANAGER_LIBRARY, DEFAULT_TRIGGER_MANAGER_LIBRARY);
            mProperties.put(KEY_TRIGGER_MANAGER_LIBRARY, DEFAULT_TRIGGER_MANAGER_LIBRARY);
        }
        return true;
    }

    public String getTriggerManagerLibrary() {
        return mProperties.getProperty(KEY_TRIGGER_MANAGER_LIBRARY);
    }

    public String getKafkaBrokerUri() {
        return mProperties.getProperty(KEY_KAFKA_BROKER_URI);
    }

    public String getProperty(String key) {
        return mProperties.getProperty(key);
    }
}
