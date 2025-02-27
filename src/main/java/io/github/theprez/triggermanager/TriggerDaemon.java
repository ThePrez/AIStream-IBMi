package io.github.theprez.triggermanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

import com.github.theprez.jcmdutils.AppLogger;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

class TriggerDaemon implements ITriggerConfigurationConstants {

    private final TriggerManager m_triggerManager;
    private final AppLogger m_logger;

    TriggerDaemon(final AppLogger _logger, final TriggerManager _tMan) {
        m_logger = _logger;
        m_triggerManager = _tMan;
    }

    void start() throws Exception {

        // Standard for a Camel deployment. Start by getting a CamelContext object.
        try (final CamelContext context = new DefaultCamelContext()) {
            m_logger.println("Apache Camel version " + context.getVersion());

            final String kafkaBrokerUri = IBMiDotEnv.getDotEnv().get(ITriggerConfigurationConstants.KEY_KAFKA_BROKER_URI);
            if (kafkaBrokerUri == null) {
                TriggerCLI.logFatalErrorAndExit("Error: Property is not set in configuration file or environment variable: %s", ITriggerConfigurationConstants.KEY_KAFKA_BROKER_URI);
            }

            final List<TriggerDescriptor> triggers = m_triggerManager.listTriggers();
            if (!triggers.isEmpty()) {
                m_logger.printfln_verbose("Adding Kafka routing for %d tables...", triggers.size());
            }

            for (final TriggerDescriptor trigger : triggers) {

                final String kafkaUri = String.format("kafka:%s?brokers=%s", trigger.getTriggerId(), kafkaBrokerUri); 
                final String password = IBMiDotEnv.getDotEnv().get("IBMI_PASSWORD", "*CURRENT");
                final String username = IBMiDotEnv.getDotEnv().get("IBMI_USERNAME", "*CURRENT");
                final String hostname = IBMiDotEnv.getDotEnv().get("IBMI_HOSTNAME", "localhost");
                final String dtaqUri = String.format(
                        "jt400://%s:%s@%s/qsys.lib/%s.lib/%s.dtaq?keyed=false&format=binary&guiAvailable=false",
                        username, password, hostname, trigger.getLibrary(), trigger.getTriggerId());

                m_logger.printfln_verbose("%s --> %s", dtaqUri, kafkaUri);
                
               context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from(dtaqUri)
                                // We do this to convert the bytes from the data queue (UTF-8 JSON data) into a
                                // String object in the message
                                .convertBodyTo(String.class, "UTF-8")
                                .to(kafkaUri);
                    }
                });
            }

            // This actually "starts" the route, so Camel will start monitoring and routing
            // activity here.
            context.start();

            // Since this program is designed to just run forever (until user cancel), we can just sleep the
            // main thread. Camel's work will happen in secondary threads.
            Thread.sleep(Long.MAX_VALUE);
            context.stop();
        }
    }

    void stop() throws Exception {
        Process process = Runtime.getRuntime().exec(new String[] {STOP_DAEMON_SCRIPT_PATH});
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line=br.readLine()) != null) {
            System.out.println(line);
        }
        br.close();
    }
}
