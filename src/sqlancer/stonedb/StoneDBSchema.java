package sqlancer.stonedb;

import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.schema.AbstractRelationalTable;
import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.TableIndex;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class StoneDBSchema extends AbstractSchema<StoneDBProvider.StoneDBGlobalState, StoneDBSchema.StoneDBTable> {

    public enum StoneDBDataType {

        TINYINT, SMALLINT, MEDIUMINT, INT, BIGINT, FLOAT, DOUBLE, DECIMAL, YEAR, TIME, DATE, DATETIME, TIMESTAMP, CHAR,
        VARCHAR, TINYTEXT, TEXT, MEDIUMTEXT, LONGTEXT, BINARY, VARBINARY, TINYBLOB, BLOB, MEDIUMBLOB, LONGBLOB;

        public static StoneDBDataType getRandomWithoutNull() {
            return Randomly.fromOptions(values());
        }

    }

    public static StoneDBCompositeDataType getRandomWithoutNull() {
        StoneDBDataType type = StoneDBDataType.getRandomWithoutNull();
        int size = -1;
        switch (type) {
        case TINYINT:
            size = 1;
            break;
        case SMALLINT:
            size = 2;
            break;
        case MEDIUMINT:
            size = 3;
            break;
        case INT:
            size = 4;
            break;
        case BIGINT:
            size = 8;
            break;
        // todo: add more data type
        default:
            throw new AssertionError(type);
        }

        return new StoneDBCompositeDataType(type, size);
    }

    public static class StoneDBTable
            extends AbstractRelationalTable<StoneDBColumn, StoneDBIndex, StoneDBProvider.StoneDBGlobalState> {

        public StoneDBTable(String tableName, List<StoneDBColumn> columns, List<StoneDBIndex> indexes, boolean isView) {
            super(tableName, columns, indexes, isView);
        }

        public boolean hasPrimaryKey() {
            return getColumns().stream().anyMatch(c -> c.isPrimaryKey());
        }

    }

    public static final class StoneDBIndex extends TableIndex {
        private StoneDBIndex(String indexName) {
            super(indexName);
        }

        public static StoneDBIndex create(String indexName) {
            return new StoneDBIndex(indexName);
        }

        @Override
        public String getIndexName() {
            if (super.getIndexName().contentEquals("PRIMARY")) {
                return "`PRIMARY`";
            } else {
                return super.getIndexName();
            }
        }
    }

    public static StoneDBSchema fromConnection(SQLConnection con, String databaseName) throws SQLException {
        List<StoneDBTable> databaseTables = new ArrayList<>();
        List<String> tableNames = getTableNames(con, databaseName);
        for (String tableName : tableNames) {
            List<StoneDBColumn> databaseColumns = getTableColumns(con, databaseName, tableName);
            List<StoneDBIndex> indexes = getIndexes(con, databaseName, tableName);
            boolean isView = tableName.startsWith("v");
            StoneDBTable t = new StoneDBTable(tableName, databaseColumns, indexes, isView);
            for (StoneDBColumn c : databaseColumns) {
                c.setTable(t);
            }
            databaseTables.add(t);

        }
        return new StoneDBSchema(databaseTables);
    }

    private static List<StoneDBIndex> getIndexes(SQLConnection con, String databaseName, String tableName)
            throws SQLException {
        List<StoneDBIndex> indexes = new ArrayList<>();
        try (ResultSet rs = con.createStatement()
                .executeQuery("SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = '"
                        + databaseName + "' AND TABLE_NAME='" + tableName + "';")) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                indexes.add(StoneDBIndex.create(indexName));
            }
        }
        return indexes;
    }

    private static List<StoneDBColumn> getTableColumns(SQLConnection con, String databaseName, String tableName)
            throws SQLException {
        List<StoneDBColumn> columns = new ArrayList<>();
        try (ResultSet rs = con.createStatement()
                .executeQuery("select * from information_schema.columns where table_schema = '" + databaseName
                        + "' AND TABLE_NAME='" + tableName + "';")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String dataType = rs.getString("DATA_TYPE");
                int precision = rs.getInt("NUMERIC_PRECISION");
                boolean isNullable = !rs.getString("IS_NULLABLE").equals("NO");
                boolean isPrimaryKey = rs.getString("COLUMN_KEY").equals("PRI");
                StoneDBColumn c = new StoneDBColumn(columnName, getColumnCompositeDataType(dataType), isPrimaryKey,
                        isNullable, precision);
                columns.add(c);
            }
        }
        return columns;
    }

    private static StoneDBCompositeDataType getColumnCompositeDataType(String typeString) {
        switch (typeString) {
        case "tinyint":
            return new StoneDBCompositeDataType(StoneDBDataType.TINYINT);
        case "smallint":
            return new StoneDBCompositeDataType(StoneDBDataType.SMALLINT);
        case "mediumint":
            return new StoneDBCompositeDataType(StoneDBDataType.MEDIUMINT);
        case "int":
            return new StoneDBCompositeDataType(StoneDBDataType.INT);
        case "bigint":
            return new StoneDBCompositeDataType(StoneDBDataType.BIGINT);
        case "float":
            return new StoneDBCompositeDataType(StoneDBDataType.FLOAT);
        case "double":
            return new StoneDBCompositeDataType(StoneDBDataType.DOUBLE);
        case "decimal":
            return new StoneDBCompositeDataType(StoneDBDataType.DECIMAL);
        case "year":
            return new StoneDBCompositeDataType(StoneDBDataType.YEAR);
        case "time":
            return new StoneDBCompositeDataType(StoneDBDataType.TIME);
        case "date":
            return new StoneDBCompositeDataType(StoneDBDataType.DATE);
        case "datetime":
            return new StoneDBCompositeDataType(StoneDBDataType.DATETIME);
        case "timestamp":
            return new StoneDBCompositeDataType(StoneDBDataType.TIMESTAMP);
        case "char":
            return new StoneDBCompositeDataType(StoneDBDataType.CHAR);
        case "varchar":
            return new StoneDBCompositeDataType(StoneDBDataType.VARCHAR);
        case "tinytext":
            return new StoneDBCompositeDataType(StoneDBDataType.TINYTEXT);
        case "text":
            return new StoneDBCompositeDataType(StoneDBDataType.TEXT);
        case "mediumtext":
            return new StoneDBCompositeDataType(StoneDBDataType.MEDIUMTEXT);
        case "longtext":
            return new StoneDBCompositeDataType(StoneDBDataType.LONGTEXT);
        case "binary":
            return new StoneDBCompositeDataType(StoneDBDataType.BINARY);
        case "varbinary":
            return new StoneDBCompositeDataType(StoneDBDataType.VARBINARY);
        case "tinyblob":
            return new StoneDBCompositeDataType(StoneDBDataType.TINYBLOB);
        case "blob":
            return new StoneDBCompositeDataType(StoneDBDataType.BLOB);
        case "mediumblob":
            return new StoneDBCompositeDataType(StoneDBDataType.MEDIUMBLOB);
        case "longblob":
            return new StoneDBCompositeDataType(StoneDBDataType.LONGBLOB);
        default:
            throw new AssertionError(typeString);
        }
    }

    private static StoneDBDataType getColumnType(String typeString) {
        switch (typeString) {
        case "tinyint":
            return StoneDBDataType.TINYINT;
        case "smallint":
            return StoneDBDataType.SMALLINT;
        case "mediumint":
            return StoneDBDataType.MEDIUMINT;
        case "int":
            return StoneDBDataType.INT;
        case "bigint":
            return StoneDBDataType.BIGINT;
        case "float":
            return StoneDBDataType.FLOAT;
        case "double":
            return StoneDBDataType.DOUBLE;
        case "decimal":
            return StoneDBDataType.DECIMAL;
        case "year":
            return StoneDBDataType.YEAR;
        case "time":
            return StoneDBDataType.TIME;
        case "date":
            return StoneDBDataType.DATE;
        case "datetime":
            return StoneDBDataType.DATETIME;
        case "timestamp":
            return StoneDBDataType.TIMESTAMP;
        case "char":
            return StoneDBDataType.CHAR;
        case "varchar":
            return StoneDBDataType.VARCHAR;
        case "tinytext":
            return StoneDBDataType.TINYTEXT;
        case "text":
            return StoneDBDataType.TEXT;
        case "mediumtext":
            return StoneDBDataType.MEDIUMTEXT;
        case "longtext":
            return StoneDBDataType.LONGTEXT;
        case "binary":
            return StoneDBDataType.BINARY;
        case "varbinary":
            return StoneDBDataType.VARBINARY;
        case "tinyblob":
            return StoneDBDataType.TINYBLOB;
        case "blob":
            return StoneDBDataType.BLOB;
        case "mediumblob":
            return StoneDBDataType.MEDIUMBLOB;
        case "longblob":
            return StoneDBDataType.LONGBLOB;
        default:
            throw new AssertionError(typeString);
        }
    }

    private static List<String> getTableNames(SQLConnection con, String databaseName) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (ResultSet rs = con.createStatement()
                .executeQuery("select TABLE_NAME, ENGINE from information_schema.TABLES where table_schema = '"
                        + databaseName + "';")) {
            while (rs.next()) {
                if (rs.getString("ENGINE").equals("TIANMU")) {
                    tableNames.add(rs.getString("TABLE_NAME"));
                }
            }
        }
        return tableNames;
    }

    public static class StoneDBColumn extends AbstractTableColumn<StoneDBTable, StoneDBCompositeDataType> {

        private final boolean isPrimaryKey;
        private final boolean isNullable;
        private final int precision;

        public StoneDBColumn(String name, StoneDBCompositeDataType columnType, boolean isPrimaryKey, boolean isNullable,
                int precision) {
            super(name, null, columnType);
            this.isPrimaryKey = isPrimaryKey;
            this.isNullable = isNullable;
            this.precision = precision;
        }

        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }

        public boolean isNullable() {
            return isNullable;
        }

    }

    public StoneDBSchema(List<StoneDBTable> databaseTables) {
        super(databaseTables);
    }

    public static class StoneDBCompositeDataType {
        private final StoneDBDataType dataType;
        private int size;

        public StoneDBCompositeDataType(StoneDBDataType dataType, int size) {
            this.dataType = dataType;
            this.size = size;
        }

        public StoneDBCompositeDataType(StoneDBDataType dataType) {
            this.dataType = dataType;
            size = -1;
            switch (dataType) {
            case TINYINT:
                size = 1;
                break;
            case SMALLINT:
                size = 2;
                break;
            case MEDIUMINT:
                size = 3;
                break;
            case INT:
                size = 4;
                break;
            case BIGINT:
                size = 8;
                break;
            case FLOAT:
                size = 4;
                break;
            case DOUBLE:
                size = 8;
                break;
            // TODO: ADD MORE DATA TYPE
            }
        }

        public StoneDBDataType getPrimitiveDataType() {
            return dataType;
        }

        public int getSize() {
            if (size == -1) {
                throw new AssertionError(this);
            }
            return size;
        }
    }
}