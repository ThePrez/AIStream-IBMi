package io.github.theprez.triggermanager;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import com.github.theprez.jcmdutils.AppLogger;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

public class TriggerConfigurationFile implements ITriggerConfigurationConstants {
    // Default value of the trigger manager library
    private static final String DEFAULT_TRIGGER_MANAGER_LIBRARY = "triggerman";

    private static TriggerConfigurationFile fInstance;
    private Properties mProperties;
    private AppLogger mLogger;
 
    private TriggerConfigurationFile(AppLogger _logger) {
        mProperties = new Properties();
        mLogger = _logger;
    }

    public static TriggerConfigurationFile getDefault(AppLogger _logger) {
        if (fInstance != null) return fInstance;

        String configPath = IBMiDotEnv.getDotEnv().get(ENV_AISTREAM_CONFIG_FILE, 
            IBMiDotEnv.isIBMi() ? DEFAULT_CONFIG_PATH : "");
        if (!configPath.isEmpty()) {
            TriggerConfigurationFile configFile = new TriggerConfigurationFile(_logger);
            try {
                configFile.load(configPath);
                if (configFile.validate()) {
                    fInstance = configFile;
                    return fInstance;
                }
                else
                    return null;
            }
            catch (Exception ex) {
                _logger.printfln_err("Exception thrown: %s->%s", ex.getClass().getSimpleName(), ex.getLocalizedMessage());
            }
        }
        else {
            _logger.println("Environment variable is not set: " + ENV_AISTREAM_CONFIG_FILE);
        }
        return null;
    }

    private void load(String _configPath) throws FileNotFoundException, IOException {
        FileInputStream fis = new FileInputStream(_configPath);
        mProperties.load(fis);
        fis.close();
    }

    /**
     * Validate the configuration file. Return true if the contents are valid.
     */
    private boolean validate() {
        if (!mProperties.containsKey(KEY_KAFKA_BROKER_URI)) {
            mLogger.printfln_err("Property is not set in configuration file: %s", KEY_KAFKA_BROKER_URI);
            return false;
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
