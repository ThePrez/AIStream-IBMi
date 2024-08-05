package io.github.theprez.triggermanager;

import java.io.IOException;
import java.sql.SQLException;

import com.github.theprez.jcmdutils.AppLogger;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.IFSFile;

public class SelfInstaller {
    private final String m_library;
    private final AS400 m_as400;
    private final AppLogger m_logger;

    public SelfInstaller(AppLogger _logger, AS400 _as400, String _library) {
        m_library = _library;
        m_as400 = _as400;
        m_logger = _logger;
    }
    public void install() throws IOException, SQLException {
        createLibraryIfNeeded();
    }

    private void createLibraryIfNeeded() throws IOException, SQLException {
        IFSFile checker = new IFSFile(m_as400, "/qsys.lib/" + m_library + ".lib");
        if(checker.exists()) {
            m_logger.printfln_verbose("Library %s already exists", m_library);
            return;
        }
        QCmdExc clCmdExc = new QCmdExc(m_logger, m_as400);
        clCmdExc.execute("QSYS/CRTLIB " + m_library);
    }

}
