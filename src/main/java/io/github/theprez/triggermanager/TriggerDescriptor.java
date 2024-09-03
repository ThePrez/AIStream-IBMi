package io.github.theprez.triggermanager;

class TriggerDescriptor {
    private final String m_library;
    private final String m_triggerId;
    private final String m_sourceSchema;
    private final String m_sourceTable;
    private final String m_toString;

    public TriggerDescriptor(final String _library, final String _triggerId, final String _sourceSchema,
            final String _sourceTable) {
        m_library = _library;
        m_triggerId = _triggerId;
        m_sourceSchema = _sourceSchema;
        m_sourceTable = _sourceTable;
        m_toString = String.format("(%s.%s) -> [%s/%s]", _sourceSchema,_sourceTable, _library, _triggerId);
    }

    String getLibrary() {
        return m_library;
    }

    String getTriggerId() {
        return m_triggerId;
    }

    public String getSourceLibrary() {
        return m_sourceSchema;
    }

    public String getSourceTable() {
        return m_sourceTable;
    }

   @Override
   public String toString() {
       return m_toString;
   }
}