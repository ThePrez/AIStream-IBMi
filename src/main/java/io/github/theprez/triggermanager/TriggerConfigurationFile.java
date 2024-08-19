package io.github.theprez.triggermanager;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import com.github.theprez.jcmdutils.AppLogger;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

public class TriggerConfigurationFile {
    public static final String KEY_TRIGGER_MANAGER_LIBRARY = "TRIGGER_MANAGER_LIBRARY";
    public static final String KEY_KAFKA_BROKER_URI = "KAFKA_BROKER_URI";

    // Default path of AIStream configuration file on IBM i host
    private static final String DEFAULT_CONFIG_PATH = "/QOpenSys/etc/aistream/main.conf";
    // Environment variable to override the config file path
    private static final String ENV_AISTREAM_CONFIG_FILE = "AISTREAM_CONFIG_FILE";

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
                configFile.load(configPath);
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

    private void load(String configPath) throws FileNotFoundException, IOException {
        FileInputStream fis = new FileInputStream(configPath);
        mProperties.load(fis);
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
