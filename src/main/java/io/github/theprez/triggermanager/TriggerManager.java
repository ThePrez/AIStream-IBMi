package io.github.theprez.triggermanager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.theprez.jcmdutils.AppLogger;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCDataSource;

public class TriggerManager {

    private AS400 m_as400;
    private String m_dq_library;
    private String m_varLocation;
    private AS400JDBCDataSource m_ds;
    private Connection m_conn;
    private AppLogger m_logger;
    private final QCmdExc m_clCommandExecutor;
    private AtomicInteger m_triggerIdCtr = new AtomicInteger(1);

    public TriggerManager(final AppLogger _logger, final AS400 _as400, final String _dq_library) throws SQLException {
        m_as400 = _as400;
        m_logger = _logger;
        m_dq_library = _dq_library;
        m_varLocation = String.format("%s.dq_json", _dq_library.trim().toUpperCase());
        m_ds = new AS400JDBCDataSource(_as400);
        m_conn = m_ds.getConnection();
        m_clCommandExecutor = new QCmdExc(m_logger, m_conn);
    }

    public synchronized TriggerDescriptor createTrigger(String _srcSchema, String _srcTable)
            throws IOException, SQLException {
        TriggerDescriptor existingTrigger = getExistingTriggerForTable(_srcSchema, _srcTable);
        if (null != existingTrigger) {
            throw new IOException("Trigger already exists: " + existingTrigger);
        }
        SqlTemplateProcessor sql = new SqlTemplateProcessor();
        String triggerId = getUniqueTriggerName().trim();
        Properties p = new Properties();
        p.setProperty("LIBRARY", m_dq_library.toUpperCase().trim());
        p.setProperty("TRIGGER_NAME", triggerId);
        p.setProperty("SOURCE_SCHEMA", _srcSchema);

        p.setProperty("SOURCE_TABLE", _srcTable);

        String columnData = getColumnData(_srcSchema, _srcTable);
        p.setProperty("COLUMN_DATA", columnData);
        p.setProperty("COLUMN_DATA_ON_DELETE", columnData.replace(" n.", " o."));
        p.setProperty("DATA_QUEUE_NAME", triggerId);
        String processedSQL = sql.getProcessed("create.sql", p);
        m_logger.printfln_verbose("Full SQL statement is:\n%s\n=================================================",
                processedSQL);

        // Create the global variable
        String createVarSql = String.format("create or replace variable %s.%s clob(64000) ccsid 1208", m_dq_library,
                triggerId); // TODO: remediate SQL injection
        executeSQLInNewStatement(createVarSql);

        // Create the data queue
        // TODO is it really necessary to attempt the delete first?  the triggerId should be unique, so we should *never* encounter an existing data queue by that name
        String deleteDqCmd = String.format("QSYS/DLTDTAQ DTAQ(%s/%s) ", m_dq_library, triggerId);
        m_clCommandExecutor.executeAndIgnoreErrors(m_logger, deleteDqCmd);
        String createDqCmd = String.format(
                "QSYS/CRTDTAQ DTAQ(%s/%s) MAXLEN(64512) SENDERID(*YES) SIZE(*MAX2GB) AUTORCL(*YES) ", m_dq_library,
                triggerId);
        m_clCommandExecutor.execute(createDqCmd);

        // Now create the trigger
        // TODO *USER does not have authority to the create trigger command
        //      [SQL0552] Not authorized to CREATE TRIGGER.
        //      https://www.ibm.com/docs/en/i/latest?topic=statements-create-trigger
        executeSQLInNewStatement(processedSQL);

        return new TriggerDescriptor(m_dq_library, triggerId, _srcSchema, _srcTable);
    }

    public List<TriggerDescriptor> listTriggers() throws SQLException {
        LinkedList<TriggerDescriptor> ret = new LinkedList<>();
        try (PreparedStatement stmt = m_conn.prepareStatement(
                "SELECT TRIGGER_NAME, EVENT_OBJECT_SCHEMA, EVENT_OBJECT_TABLE from qsys2.systriggers where TRIGGER_SCHEMA like ?")) {
            stmt.setString(1, m_dq_library.trim().toUpperCase());
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
                "SELECT TRIGGER_NAME, EVENT_OBJECT_SCHEMA, EVENT_OBJECT_TABLE from qsys2.systriggers where TRIGGER_SCHEMA like ? AND EVENT_OBJECT_SCHEMA like ? AND EVENT_OBJECT_TABLE like ?")) {
            stmt.setString(1, m_dq_library.trim().toUpperCase());
            stmt.setString(2, _schema);
            stmt.setString(3, _table);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return null;
            }
            String triggerId = rs.getString(1);
            String sourceSchema = rs.getString(2);
            String sourceTable = rs.getString(3);
            return new TriggerDescriptor(m_dq_library, triggerId, sourceSchema, sourceTable);
        }
    }

    private void executeSQLInNewStatement(String processedSQL) throws SQLException {
        try (Statement stmt = m_conn.createStatement()) {
            stmt.execute(processedSQL);
        }
    }

    private String getColumnData(String _srcLib, String _srcTable) throws SQLException {
        String ret = "";
        boolean isFirst = true;
        try (PreparedStatement stmt = m_conn
                .prepareStatement(String.format("select * from %s.%s limit 1", _srcLib, _srcTable))) { // TODO: mitigate
                                                                                                       // SQL injection
            ResultSetMetaData metaData = stmt.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; ++i) {
                String columnName = metaData.getColumnName(i);
                if (!isFirst) {
                    ret += ",\n";
                }
                ret += String.format("            KEY '%s' VALUE n.%s", columnName, columnName);
                isFirst = false;
            }
        }

        return ret;
    }

    private boolean doesTriggerExistWithId(String _triggerId) throws SQLException {
        try (PreparedStatement stmt = m_conn.prepareStatement(String.format(
                "select count(TRIGGER_NAME) from qsys2.systriggers where TRIGGER_SCHEMA like ? and TRIGGER_NAME like ?"))) {
            stmt.setString(1, m_dq_library);
            stmt.setString(2, _triggerId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return 0 < rs.getInt(1);
        }
    }

    public TriggerDescriptor deleteTriggerFromTable(final String _schema, final String _table) throws SQLException {
        TriggerDescriptor existingTrigger = getExistingTriggerForTable(_schema, _table);
        if (null == existingTrigger) {
            m_logger.printfln_warn("No trigger exists for table %s.%s", _schema, _table);
            return null;
        }

        // drop the trigger and global variable
        try (Statement stmt = m_conn.createStatement()) {
            stmt.execute(
                    String.format("drop trigger %s.%s", existingTrigger.getLibrary(), existingTrigger.getTriggerId()));
            stmt.execute(
                    String.format("drop variable %s.%s", existingTrigger.getLibrary(), existingTrigger.getTriggerId()));
        }
        // delete the data queue
        m_clCommandExecutor.execute(String.format("QSYS/DLTDTAQ DTAQ(%s/%s)", m_dq_library, existingTrigger.getTriggerId()));

        return existingTrigger;
    }

    private synchronized String getUniqueTriggerName() throws SQLException {
        while (true) {
            String tryMe = "J" + UUID.randomUUID().toString().replaceAll("[^A-Z0-9]+", "").substring(0, 9);
            if (!doesTriggerExistWithId(tryMe)) {
                return tryMe;
            }
        }
    }
}
