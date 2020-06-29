package sqlancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.sqlite3.ast.SQLite3Constant;
import sqlancer.sqlite3.ast.SQLite3Expression;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;

public class StateToReproduce {

    public final List<Query> statements = new ArrayList<>();
    public String queryString;

    private final String databaseName;

    public String databaseVersion;

    protected long seedValue;

    public String values;

    String exception;

    public String queryTargetedTablesString;

    public String queryTargetedColumnsString;

    public StateToReproduce(String databaseName) {
        this.databaseName = databaseName;

    }

    public String getException() {
        return exception;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseVersion() {
        return databaseVersion;
    }

    public List<Query> getStatements() {
        return statements;
    }

    public String getQueryString() {
        return queryString;
    }

    public long getSeedValue() {
        return seedValue;
    }

    public static class MySQLStateToReproduce extends StateToReproduce {

        public Map<MySQLColumn, MySQLConstant> randomRowValues;

        public MySQLExpression whereClause;

        public String queryThatSelectsRow;

        public MySQLStateToReproduce(String databaseName) {
            super(databaseName);
        }

        public Map<MySQLColumn, MySQLConstant> getRandomRowValues() {
            return randomRowValues;
        }

        public MySQLExpression getWhereClause() {
            return whereClause;
        }

    }

    public static class SQLite3StateToReproduce extends StateToReproduce {
        public Map<SQLite3Column, SQLite3Constant> randomRowValues;

        public SQLite3Expression whereClause;

        public SQLite3StateToReproduce(String databaseName) {
            super(databaseName);
        }

        public Map<SQLite3Column, SQLite3Constant> getRandomRowValues() {
            return randomRowValues;
        }

        public SQLite3Expression getWhereClause() {
            return whereClause;
        }

    }

    public static class PostgresStateToReproduce extends StateToReproduce {

        public Map<PostgresColumn, PostgresConstant> randomRowValues;

        public PostgresExpression whereClause;

        public String queryThatSelectsRow;

        public PostgresStateToReproduce(String databaseName) {
            super(databaseName);
        }

        public Map<PostgresColumn, PostgresConstant> getRandomRowValues() {
            return randomRowValues;
        }

        public PostgresExpression getWhereClause() {
            return whereClause;
        }

    }

}
