package io.github.theprez.triggermanager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

import com.github.theprez.jcmdutils.AppLogger;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.IFSFile;

class TriggerManager {
    private static final String GENERATED_NAME_PREFIX = "AI";

    private final String m_dq_library;
    private final Connection m_conn;
    private final AppLogger m_logger;
    private final QCmdExc m_clCommandExecutor;

    TriggerManager(final AS400 as400, final Connection _connection, final AppLogger _logger) throws IOException, SQLException {
        m_conn = _connection;
        m_logger = _logger;
        m_clCommandExecutor = new QCmdExc(m_logger, m_conn);

        // The library where the triggers, variables, and data queues are saved
        m_dq_library = TriggerConfigurationFile.getInstance(m_logger).getTriggerManagerLibrary();
        IFSFile checker = new IFSFile(as400, "/qsys.lib/" + m_dq_library + ".lib");
        if (checker.exists()) {
            m_logger.printfln_verbose("Library %s already exists", m_dq_library);
        } else {
            m_clCommandExecutor.execute("QSYS/CRTLIB " + m_dq_library);
            // TODO Set appropriate authorities
            try {
                m_clCommandExecutor.execute("QSYS/CHGLIB LIB(" + m_dq_library + ") TEXT('AIStream')");
            } catch (SQLException ex) {
                // Failed to set the text, oh well
            }
        }
    }

    synchronized TriggerDescriptor createTrigger(final TableDescriptor table) throws IOException, SQLException {
        TriggerDescriptor existingTrigger = getExistingTriggerForTable(table);
        // If there is an existing trigger for the specified table, we're already monitoring it
        if (Objects.nonNull(existingTrigger)) {
            throw new IOException("Table already monitored: " + existingTrigger);
        }
        String triggerId = getUniqueTriggerName().trim();
        Properties p = new Properties();
        p.setProperty("LIBRARY", m_dq_library);
        p.setProperty("TRIGGER_NAME", triggerId);
        p.setProperty("SOURCE_SCHEMA", table.getSchema());

        p.setProperty("SOURCE_TABLE", table.getName());

        String columnData = table.getColumnData(m_conn);
        if (columnData.isEmpty()) {
            throw new IOException("Table lookup failed!");
        }
        p.setProperty("COLUMN_DATA", columnData);
        p.setProperty("COLUMN_DATA_ON_DELETE", columnData.replace(" n.", " o."));
        p.setProperty("DATA_QUEUE_NAME", triggerId);
        String processedSQL = SqlTemplateProcessor.getProcessed("create.sql", p);
        m_logger.printfln_verbose("Full SQL statement is:\n%s\n=================================================",
                processedSQL);

        // Create the global variable
        String createVarSql = String.format("CREATE OR REPLACE VARIABLE %s.%s CLOB(64000) CCSID 1208", m_dq_library, triggerId); // TODO: remediate SQL injection
        executeSQLInNewStatement(createVarSql);
        // Set the global variable label
        try {
            executeSQLInNewStatement(String.format("LABEL ON VARIABLE %s.%s IS '%s'",
                    m_dq_library,
                    triggerId,
                    table.getLabelText())); // TODO: remediate SQL injection
        } catch (SQLException e) {
            // Failed to set the label, oh well
        }
        
        // Create the data queue
        // TODO is it really necessary to attempt the delete first?  the triggerId should be unique, so we should *never* encounter an existing data queue by that name
        String deleteDqCmd = String.format("QSYS/DLTDTAQ DTAQ(%s/%s) ", m_dq_library, triggerId);
        m_clCommandExecutor.executeAndIgnoreErrors(deleteDqCmd);
        String createDqCmd = String.format("QSYS/CRTDTAQ DTAQ(%s/%s) MAXLEN(64512) SENDERID(*YES) SIZE(*MAX2GB) AUTORCL(*YES) TEXT('%s')",
                 m_dq_library,
                triggerId,
                table.getLabelText());
        m_clCommandExecutor.execute(createDqCmd);

        // Now create the trigger
        // TODO *USER does not have authority to the create trigger command
        //      [SQL0552] Not authorized to CREATE TRIGGER.
        //      https://www.ibm.com/docs/en/i/latest?topic=statements-create-trigger
        executeSQLInNewStatement(processedSQL);
        // Set the trigger label
        try {
            executeSQLInNewStatement(String.format("LABEL ON TRIGGER %s.%s IS '%s'",
                    m_dq_library,
                    triggerId,
                    table.getLabelText())); // TODO: remediate SQL injection
        } catch (SQLException e) {
            // Failed to set the label, oh well
        }

        return new TriggerDescriptor(m_dq_library, triggerId, table);
    }

    List<TriggerDescriptor> listTriggers() throws SQLException {
        LinkedList<TriggerDescriptor> ret = new LinkedList<>();
        try (PreparedStatement stmt = m_conn.prepareStatement(
                "SELECT " + 
                "TRIGGER_NAME, " +
                "QSYS2.DELIMIT_NAME(EVENT_OBJECT_SCHEMA), " +
                "SYSTEM_EVENT_OBJECT_SCHEMA, " +
                "QSYS2.DELIMIT_NAME(EVENT_OBJECT_TABLE), " +
                "SYSTEM_EVENT_OBJECT_TABLE " +
                "FROM QSYS2.SYSTRIGGERS " +
                "WHERE TRIGGER_SCHEMA = ?" +
                "ORDER BY QSYS2.DELIMIT_NAME(EVENT_OBJECT_SCHEMA), QSYS2.DELIMIT_NAME(EVENT_OBJECT_TABLE)")) {
            stmt.setString(1, m_dq_library);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                final String triggerId = rs.getString(1);
                final String schema = rs.getString(2);
                final String systemSchema = rs.getString(3);
                final String table = rs.getString(4);
                final String systemTable = rs.getString(5);
                ret.add(new TriggerDescriptor(m_dq_library, triggerId, new TableDescriptor(schema, systemSchema, table, systemTable)));
            }
            return ret;
        }
    }

    TriggerDescriptor getExistingTriggerForTable(final TableDescriptor table) throws SQLException {
        try (PreparedStatement stmt = m_conn.prepareStatement(
                "SELECT " +
                "TRIGGER_NAME " +
                "FROM QSYS2.SYSTRIGGERS " +
                "WHERE TRIGGER_SCHEMA = ? AND QSYS2.DELIMIT_NAME(EVENT_OBJECT_SCHEMA) = ? AND QSYS2.DELIMIT_NAME(EVENT_OBJECT_TABLE) = ?")) {
            stmt.setString(1, m_dq_library);
            stmt.setString(2, table.getSchema());
            stmt.setString(3, table.getName());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new TriggerDescriptor(m_dq_library, rs.getString(1), table);
            }
            return null;
        }
    }

    private void executeSQLInNewStatement(String processedSQL) throws SQLException {
        try (Statement stmt = m_conn.createStatement()) {
            stmt.execute(processedSQL);
        }
    }

    private boolean doesTriggerExistWithId(String _triggerId) throws SQLException {
        try (PreparedStatement stmt = m_conn.prepareStatement(
                "SELECT " +
                "COUNT(TRIGGER_NAME) " +
                "FROM QSYS2.SYSTRIGGERS " +
                "WHERE TRIGGER_SCHEMA = ? AND TRIGGER_NAME = ?")) {
            stmt.setString(1, m_dq_library);
            stmt.setString(2, _triggerId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return 0 < rs.getInt(1);
        }
    }

    TriggerDescriptor deleteTriggerFromTable(final TableDescriptor table) throws SQLException {
        TriggerDescriptor existingTrigger = getExistingTriggerForTable(table);
        if (Objects.isNull(existingTrigger)) {
            m_logger.printfln_warn("No trigger exists for table %s", table);
            return null;
        }

        // drop the trigger and global variable
        try (Statement stmt = m_conn.createStatement()) {
            stmt.execute(
                    String.format("DROP TRIGGER %s.%s", existingTrigger.getLibrary(), existingTrigger.getTriggerId()));
            stmt.execute(
                    String.format("DROP VARIABLE %s.%s", existingTrigger.getLibrary(), existingTrigger.getTriggerId()));
        }
        // delete the data queue
        m_clCommandExecutor.execute(String.format("QSYS/DLTDTAQ DTAQ(%s/%s)", m_dq_library, existingTrigger.getTriggerId()));

        return existingTrigger;
    }

    private synchronized String getUniqueTriggerName() throws SQLException {
        while (true) {
            String tryMe = (GENERATED_NAME_PREFIX + UUID.randomUUID().toString().replaceAll("[^A-Z0-9]+", "")).substring(0, 10);
            if (!doesTriggerExistWithId(tryMe)) {
                return tryMe;
            }
        }
    }
}
