package io.github.theprez.triggermanager;

class TriggerDescriptor {
    private final String m_library;
    private final String m_triggerId;
    private final TableDescriptor m_table;

    TriggerDescriptor(final String _library, final String _triggerId, final TableDescriptor _table) {
        m_library = _library;
        m_triggerId = _triggerId;
        m_table = _table;
    }

    String getLibrary() {
        return m_library;
    }

    String getTriggerId() {
        return m_triggerId;
    }

   @Override
   public String toString() {
       return String.format("(%s) -> [%s/%s]", m_table, m_library, m_triggerId);
   }
}