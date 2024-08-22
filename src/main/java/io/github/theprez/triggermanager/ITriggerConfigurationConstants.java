package io.github.theprez.triggermanager;

public interface ITriggerConfigurationConstants {
    // The library where the triggers, variables, and data queues are saved
    // Default value: TRIGGERMAN
    static final String KEY_TRIGGER_MANAGER_LIBRARY = "TRIGGER_MANAGER_LIBRARY";

    // The Kafka broker uri
    // Default value: idevphp.idevcloud.com:9092
    static final String KEY_KAFKA_BROKER_URI = "KAFKA_BROKER_URI";

    // Default path of AIStream configuration file on IBM i host
    static final String DEFAULT_CONFIG_PATH = "/QOpenSys/etc/aistream/main.conf";

    // Environment variable to override the path of the AIStream configuration file
    static final String ENV_AISTREAM_CONFIG_FILE = "AISTREAM_CONFIG_FILE";

}
