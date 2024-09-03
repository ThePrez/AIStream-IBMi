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
import java.util.StringJoiner;
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

synchronized TriggerDescriptor createTrigger(String _srcSchema, String _srcTable)
            throws IOException, SQLException {
        TriggerDescriptor existingTrigger = getExistingTriggerForTable(_srcSchema, _srcTable);
// If there is an existing trigger for the specified table, we're already monitoring it
        if (Objects.nonNull(existingTrigger)) {
            throw new IOException("Table already monitored: " + existingTrigger);
        }
        String triggerId = getUniqueTriggerName().trim();
        Properties p = new Properties();
        p.setProperty("LIBRARY", m_dq_library);
        p.setProperty("TRIGGER_NAME", triggerId);
        p.setProperty("SOURCE_SCHEMA", _srcSchema);

        p.setProperty("SOURCE_TABLE", _srcTable);

        String columnData = getColumnData(_srcSchema, _srcTable);
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
            executeSQLInNewStatement(String.format(
                "LABEL ON VARIABLE %s.%s IS 'AI Stream Monitoring - %s.%s'",
                    m_dq_library,
                    triggerId,
                    _srcSchema,
                    _srcTable)); // TODO: remediate SQL injection
        } catch (SQLException e) {
            // Failed to set the label, oh well
        }
        
        // Create the data queue
        // TODO is it really necessary to attempt the delete first?  the triggerId should be unique, so we should *never* encounter an existing data queue by that name
        String deleteDqCmd = String.format("QSYS/DLTDTAQ DTAQ(%s/%s) ", m_dq_library, triggerId);
        m_clCommandExecutor.executeAndIgnoreErrors(deleteDqCmd);
        String createDqCmd = String.format(
                "QSYS/CRTDTAQ DTAQ(%s/%s) MAXLEN(64512) SENDERID(*YES) SIZE(*MAX2GB) AUTORCL(*YES) TEXT('AI Stream Monitoring')", m_dq_library,
                triggerId);
        m_clCommandExecutor.execute(createDqCmd);

        // Now create the trigger
        // TODO *USER does not have authority to the create trigger command
        //      [SQL0552] Not authorized to CREATE TRIGGER.
        //      https://www.ibm.com/docs/en/i/latest?topic=statements-create-trigger
        executeSQLInNewStatement(processedSQL);
        // Set the trigger label
        try {
            executeSQLInNewStatement(String.format(
                "LABEL ON TRIGGER %s.%s IS 'AI Stream Monitoring - %s.%s'",
                    m_dq_library,
                    triggerId,
                    _srcSchema,
                    _srcTable)); // TODO: remediate SQL injection
        } catch (SQLException e) {
            // Failed to set the label, oh well
        }

        return new TriggerDescriptor(m_dq_library, triggerId, _srcSchema, _srcTable);
    }

    List<TriggerDescriptor> listTriggers() throws SQLException {
        LinkedList<TriggerDescriptor> ret = new LinkedList<>();
        try (PreparedStatement stmt = m_conn.prepareStatement(
                "SELECT TRIGGER_NAME, EVENT_OBJECT_SCHEMA, EVENT_OBJECT_TABLE from QSYS2.SYSTRIGGERS where TRIGGER_SCHEMA = ?")) {
            stmt.setString(1, m_dq_library);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String triggerId = rs.getString(1);
                String sourceSchema = rs.getString(2);
                String sourceTable = rs.getString(3);
                ret.add(new TriggerDescriptor(m_dq_library, triggerId, sourceSchema, sourceTable));
            }
            return ret;
        }
    }

    public TriggerDescriptor getExistingTriggerForTable(String _schema, String _table) throws SQLException {
        try (PreparedStatement stmt = m_conn.prepareStatement(
                "SELECT TRIGGER_NAME, EVENT_OBJECT_SCHEMA, EVENT_OBJECT_TABLE from QSYS2.SYSTRIGGERS where TRIGGER_SCHEMA = ? AND EVENT_OBJECT_SCHEMA like ? AND EVENT_OBJECT_TABLE like ?")) {
            stmt.setString(1, m_dq_library);
            // TODO the schema and table name could be delimited, so the query values need to be set accordingly
            stmt.setString(2, _schema);
            stmt.setString(3, _table);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String triggerId = rs.getString(1);
                String sourceSchema = rs.getString(2);
                String sourceTable = rs.getString(3);
                return new TriggerDescriptor(m_dq_library, triggerId, sourceSchema, sourceTable);
            }
            return null;
        }
    }

    private void executeSQLInNewStatement(String processedSQL) throws SQLException {
        try (Statement stmt = m_conn.createStatement()) {
            stmt.execute(processedSQL);
        }
    }

    private String getColumnData(String _srcLib, String _srcTable) throws SQLException {
        final StringJoiner sjColumnData = new StringJoiner(",\n");
        // Query the SYSCOLUMNS catalog to get the column data for the specified table.
        // This ensures that implicitly hidden columns are included, where using the
        // ResultSetMetaData from a `SELECT * FROM x` query they would not be.
        try (PreparedStatement stmt = m_conn
                .prepareStatement("select QSYS2.DELIMIT_NAME(column_name) from QSYS2.SYSCOLUMNS where table_schema = ? and table_name = ? order by ordinal_position")) {
                    stmt.setString(1, _srcLib);
                    stmt.setString(2, _srcTable);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String columnName = rs.getString(1);
                sjColumnData.add(String.format("            KEY '%s' VALUE n.%s", columnName, columnName));
            }
        }
        return sjColumnData.toString();
    }

    private boolean doesTriggerExistWithId(String _triggerId) throws SQLException {
        try (PreparedStatement stmt = m_conn.prepareStatement(
                "select count(TRIGGER_NAME) from QSYS2.SYSTRIGGERS where TRIGGER_SCHEMA = ? and TRIGGER_NAME like ?")) {
            stmt.setString(1, m_dq_library);
            stmt.setString(2, _triggerId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return 0 < rs.getInt(1);
        }
    }

    public TriggerDescriptor deleteTriggerFromTable(final String _schema, final String _table) throws SQLException {
        TriggerDescriptor existingTrigger = getExistingTriggerForTable(_schema, _table);
        if (Objects.isNull(existingTrigger)) {
            m_logger.printfln_warn("No trigger exists for table %s.%s", _schema, _table);
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
