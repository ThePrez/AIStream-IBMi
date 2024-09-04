package io.github.theprez.triggermanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.github.theprez.jcmdutils.AppLogger;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCDataSource;

final class QCmdExc {

    private final PreparedStatement m_stmt;
    private final AppLogger m_logger;

    QCmdExc(final AppLogger _logger, final Connection _conn) throws SQLException {
        m_stmt = _conn.prepareStatement("CALL QSYS2.QCMDEXC(?)");
        m_logger = _logger;
    }

    QCmdExc(final AppLogger _logger, final AS400 _conn) throws SQLException {
        this(_logger, new AS400JDBCDataSource(_conn).getConnection());
    }

    synchronized void execute(final String _cmd) throws SQLException {
        m_logger.printfln_verbose("Executing CL command ==> \"%s\"", _cmd);
        m_stmt.setString(1, _cmd);
        m_stmt.execute();
    }

    void executeAndIgnoreErrors(final String cmd) {
        try {
            execute(cmd);
        } catch (final SQLException e) {
            m_logger.printfln_verbose(e.getLocalizedMessage());
        }
    }
}
