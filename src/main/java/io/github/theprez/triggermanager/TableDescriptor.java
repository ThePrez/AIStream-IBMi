package io.github.theprez.triggermanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

class TableDescriptor {
    private final String schema;
    private final String systemSchema;
    private final String name;
    private final String systemName;
    TableDescriptor(final String _schema, final String _systemSchema, final String _name, final String _systemName) {
        schema = _schema;
        systemSchema = _systemSchema;
        name = _name;
        systemName = _systemName;
    }

    String getSchema() {
        return schema;
    }
    String getName() {
        return name;
    }

    /**
     * Returns a string that can be used as a label for a related object (trigger, global variable, data queue)
     */
    String getLabelText() {
        final String format = "AIStream - %s.%s";
        // Object labels are limited to 50 characters, so use the "fullest" qualified name possible, giving preference to the table name, staying within that limit
        return Stream.of(
            String.format(format, schema, name),
            String.format(format, systemSchema, name),
            String.format(format, schema, systemName),
            String.format(format, systemSchema, systemName)
        )
        .filter(s -> s.length() <= 50)
        .findFirst()
        .orElse("");
    }

    @Override
    public String toString() {
        return String.format("%s.%s", schema, name);
    }

    static TableDescriptor lookup(final String _schema, final String _table, final Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT " +
                "QSYS2.DELIMIT_NAME(TABLE_SCHEMA), " +
                "SYSTEM_TABLE_SCHEMA, " +
                "QSYS2.DELIMIT_NAME(TABLE_NAME), " +
                "SYSTEM_TABLE_NAME " +
                "FROM QSYS2.SYSTABLES " + 
                "WHERE (QSYS2.DELIMIT_NAME(TABLE_SCHEMA) = ? OR SYSTEM_TABLE_SCHEMA = ?) " +
                "AND (QSYS2.DELIMIT_NAME(TABLE_NAME) = ? OR SYSTEM_TABLE_NAME = ?)")) {
            stmt.setString(1, _schema);
            stmt.setString(2, _schema);
            stmt.setString(3, _table);
            stmt.setString(4, _table);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new TableDescriptor(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
            }
        }
        return null;
    }
}
