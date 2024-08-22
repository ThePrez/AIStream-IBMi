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
    // Default value of the Kafka broker uri
    private static final String DEFAULT_KAFKA_BROKER_URI = "idevphp.idevcloud.com:9092";

    private static TriggerConfigurationFile fInstance;
    private Properties mProperties;
 
    private TriggerConfigurationFile() {
        mProperties = new Properties();
    }

    public static TriggerConfigurationFile getDefault(AppLogger _logger) {
        if (fInstance != null) return fInstance;

        String configPath = IBMiDotEnv.getDotEnv().get(ENV_AISTREAM_CONFIG_FILE, 
            IBMiDotEnv.isIBMi() ? DEFAULT_CONFIG_PATH : "");
        if (!configPath.isEmpty()) {
            TriggerConfigurationFile configFile = new TriggerConfigurationFile();
            try {
                configFile.load(configPath, _logger);
                fInstance = configFile;
                return fInstance;
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

    private void load(String _configPath, AppLogger _logger) throws FileNotFoundException, IOException {
        FileInputStream fis = new FileInputStream(_configPath);
        mProperties.load(fis);
        fis.close();

        setDefaultValue(KEY_TRIGGER_MANAGER_LIBRARY, DEFAULT_TRIGGER_MANAGER_LIBRARY, _logger);
        setDefaultValue(KEY_KAFKA_BROKER_URI, DEFAULT_KAFKA_BROKER_URI, _logger);
    }

    /**
     * Set the specified key to the default value if the key does not exist in the properties list.
     */
    private void setDefaultValue(String _key, String _defaultValue, AppLogger _logger) {
        if (!mProperties.containsKey(_key)) {
            _logger.printfln_warn("Warning: Property '%s' is not specified in configuration file. Using default value '%s'",
                _key, _defaultValue);
            mProperties.put(_key, _defaultValue);
        }
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
