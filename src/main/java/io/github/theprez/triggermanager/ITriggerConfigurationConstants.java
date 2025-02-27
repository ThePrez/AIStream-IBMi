package io.github.theprez.triggermanager;

interface ITriggerConfigurationConstants {
    // The library where the triggers, variables, and data queues are saved
    static final String KEY_TRIGGER_MANAGER_LIBRARY = "TRIGGER_MANAGER_LIBRARY";

    // The Kafka broker uri
    static final String KEY_KAFKA_BROKER_URI = "KAFKA_BROKER_URI";

    // The root path for AIStream on IBM i
    static final String AISTREAM_ROOT_PATH = "/QOpenSys/QIBM/ProdData/aistream";

    // Default path of AIStream configuration file on IBM i host
    static final String DEFAULT_CONFIG_PATH = AISTREAM_ROOT_PATH + "/etc/main.conf";

    // Fully qualified path of the stop daemon script
    static final String STOP_DAEMON_SCRIPT_PATH = AISTREAM_ROOT_PATH + "/bin/stopDaemon.sh";

    // Environment variable to override the path of the AIStream configuration file
    static final String ENV_AISTREAM_CONFIG_FILE = "AISTREAM_CONFIG_FILE";
}
