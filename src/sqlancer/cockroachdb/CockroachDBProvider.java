package sqlancer.cockroachdb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import sqlancer.DatabaseProvider;
import sqlancer.GlobalState;
import sqlancer.IgnoreMeException;
import sqlancer.Main.QueryManager;
import sqlancer.Main.StateLogger;
import sqlancer.MainOptions;
import sqlancer.Query;
import sqlancer.QueryAdapter;
import sqlancer.QueryProvider;
import sqlancer.Randomly;
import sqlancer.StateToReproduce;
import sqlancer.StateToReproduce.CockroachDBStateToReproduce;
import sqlancer.TestOracle;
import sqlancer.cockroachdb.CockroachDBProvider.CockroachDBGlobalState;
import sqlancer.cockroachdb.CockroachDBSchema.CockroachDBTable;
import sqlancer.cockroachdb.gen.CockroachDBCommentOnGenerator;
import sqlancer.cockroachdb.gen.CockroachDBCreateStatisticsGenerator;
import sqlancer.cockroachdb.gen.CockroachDBDeleteGenerator;
import sqlancer.cockroachdb.gen.CockroachDBIndexGenerator;
import sqlancer.cockroachdb.gen.CockroachDBInsertGenerator;
import sqlancer.cockroachdb.gen.CockroachDBRandomQuerySynthesizer;
import sqlancer.cockroachdb.gen.CockroachDBSetClusterSettingGenerator;
import sqlancer.cockroachdb.gen.CockroachDBSetSessionGenerator;
import sqlancer.cockroachdb.gen.CockroachDBShowGenerator;
import sqlancer.cockroachdb.gen.CockroachDBTableGenerator;
import sqlancer.cockroachdb.gen.CockroachDBTruncateGenerator;
import sqlancer.cockroachdb.gen.CockroachDBUpdateGenerator;
import sqlancer.cockroachdb.gen.CockroachDBViewGenerator;

public class CockroachDBProvider implements DatabaseProvider<CockroachDBGlobalState, CockroachDBOptions> {

    public enum Action {
        INSERT(CockroachDBInsertGenerator::insert), //
        TRUNCATE(CockroachDBTruncateGenerator::truncate), //
        CREATE_STATISTICS(CockroachDBCreateStatisticsGenerator::create), //
        SET_SESSION(CockroachDBSetSessionGenerator::create), //
        CREATE_INDEX(CockroachDBIndexGenerator::create), //
        UPDATE(CockroachDBUpdateGenerator::gen), //
        CREATE_VIEW(CockroachDBViewGenerator::generate), //
        SET_CLUSTER_SETTING(CockroachDBSetClusterSettingGenerator::create), //
        DELETE(CockroachDBDeleteGenerator::delete), //
        COMMENT_ON(CockroachDBCommentOnGenerator::comment), //
        SHOW(CockroachDBShowGenerator::show), //
        TRANSACTION((g) -> {
            String s = Randomly.fromOptions("BEGIN", "ROLLBACK", "COMMIT");
            return new QueryAdapter(s,
                    Arrays.asList("there is no transaction in progress", "there is already a transaction in progress",
                            "current transaction is aborted", "does not exist" /* interleaved indexes */));
        }), //
        EXPLAIN((g) -> {
            StringBuilder sb = new StringBuilder("EXPLAIN ");
            Set<String> errors = new HashSet<>();
            if (Randomly.getBoolean()) {
                sb.append("(");
                sb.append(Randomly.nonEmptySubset("VERBOSE", "TYPES", "OPT", "DISTSQL", "VEC").stream()
                        .collect(Collectors.joining(", ")));
                sb.append(") ");
                errors.add("cannot set EXPLAIN mode more than once");
                errors.add("unable to vectorize execution plan");
                errors.add("unsupported type");
                errors.add("vectorize is set to 'off'");
            }
            sb.append(CockroachDBRandomQuerySynthesizer.generate(g, Randomly.smallNumber() + 1));
            CockroachDBErrors.addExpressionErrors(errors);
            return new QueryAdapter(sb.toString(), errors);
        }), //
        SCRUB((g) -> new QueryAdapter(
                "EXPERIMENTAL SCRUB table " + g.getSchema().getRandomTable(t -> !t.isView()).getName(),
                // https://github.com/cockroachdb/cockroach/issues/46401
                Arrays.asList("scrub-fk: column \"t.rowid\" does not exist",
                        "check-constraint: cannot access temporary tables of other sessions" /*
                                                                                              * https:// github. com/
                                                                                              * cockroachdb / cockroach
                                                                                              * /issues/ 47031
                                                                                              */))), //
        SPLIT((g) -> {
            StringBuilder sb = new StringBuilder("ALTER INDEX ");
            CockroachDBTable randomTable = g.getSchema().getRandomTable();
            sb.append(randomTable.getName());
            sb.append("@");
            sb.append(randomTable.getRandomIndex());
            if (Randomly.getBoolean()) {
                sb.append(" SPLIT AT VALUES (true), (false);");
            } else {
                sb.append(" SPLIT AT VALUES (NULL);");
            }
            return new QueryAdapter(sb.toString(), Arrays.asList("must be of type"));
        });

        private final QueryProvider<CockroachDBGlobalState> queryProvider;

        Action(QueryProvider<CockroachDBGlobalState> queryProvider) {
            this.queryProvider = queryProvider;
        }

        public Query getQuery(CockroachDBGlobalState state) throws SQLException {
            return queryProvider.getQuery(state);
        }
    }

    public static class CockroachDBGlobalState extends GlobalState<CockroachDBOptions> {

        private CockroachDBSchema schema;

        public void setSchema(CockroachDBSchema schema) {
            this.schema = schema;
        }

        public CockroachDBSchema getSchema() {
            return schema;
        }

    }

    @Override
    public void generateAndTestDatabase(CockroachDBGlobalState globalState) throws SQLException {
        Randomly r = new Randomly();
        Connection con = globalState.getConnection();
        String databaseName = globalState.getDatabaseName();
        QueryManager manager = globalState.getManager();
        StateLogger logger = globalState.getLogger();
        StateToReproduce state = globalState.getState();
        MainOptions options = globalState.getOptions();
        globalState.setSchema(CockroachDBSchema.fromConnection(con, databaseName));

        List<String> standardSettings = new ArrayList<>();
        standardSettings.add("--Don't send automatic bug reports\n"
                + "SET CLUSTER SETTING debug.panic_on_failed_assertions = true;");
        standardSettings.add("SET CLUSTER SETTING diagnostics.reporting.enabled    = false;");
        standardSettings.add("SET CLUSTER SETTING diagnostics.reporting.send_crash_reports = false;");

        standardSettings.add("-- Disable the collection of metrics and hope that it helps performance\n"
                + "SET CLUSTER SETTING sql.metrics.statement_details.enabled = 'off'");
        standardSettings.add("SET CLUSTER SETTING sql.metrics.statement_details.plan_collection.enabled = 'off'");
        standardSettings.add("SET CLUSTER SETTING sql.stats.automatic_collection.enabled = 'off'");
        standardSettings.add("SET CLUSTER SETTING timeseries.storage.enabled = 'off'");

        if (globalState.getDmbsSpecificOptions().testHashIndexes) {
            standardSettings.add("set experimental_enable_hash_sharded_indexes='on';");
        }
        if (globalState.getDmbsSpecificOptions().testTempTables) {
            standardSettings.add("SET experimental_enable_temp_tables = 'on'");
        }
        for (String s : standardSettings) {
            manager.execute(new QueryAdapter(s));
        }

        for (int i = 0; i < Randomly.fromOptions(2, 3); i++) {
            boolean success = false;
            do {
                try {
                    Query q = CockroachDBTableGenerator.generate(globalState);
                    success = manager.execute(q);
                    logger.writeCurrent(state);
                    try {
                        logger.getCurrentFileWriter().close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    logger.currentFileWriter = null;
                } catch (IgnoreMeException e) {
                    // continue trying
                }
            } while (!success);
            globalState.setSchema(CockroachDBSchema.fromConnection(con, databaseName));
        }
        logger.writeCurrent(state);

        int[] nrRemaining = new int[Action.values().length];
        List<Action> actions = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < Action.values().length; i++) {
            Action action = Action.values()[i];
            int nrPerformed = 0;
            switch (action) {
            case INSERT:
                nrPerformed = r.getInteger(0, options.getMaxNumberInserts());
                break;
            case UPDATE:
            case SPLIT:
                nrPerformed = r.getInteger(0, 3);
                break;
            case EXPLAIN:
                nrPerformed = r.getInteger(0, 10);
                break;
            case SHOW:
            case TRUNCATE:
            case DELETE:
            case CREATE_STATISTICS:
                nrPerformed = r.getInteger(0, 2);
                break;
            case CREATE_VIEW:
                nrPerformed = r.getInteger(0, 2);
                break;
            case SET_SESSION:
            case SET_CLUSTER_SETTING:
                nrPerformed = r.getInteger(0, 3);
                break;
            case CREATE_INDEX:
                nrPerformed = r.getInteger(0, 10);
                break;
            case COMMENT_ON:
            case SCRUB:
                nrPerformed = 0; /*
                                  * there are a number of open SCRUB bugs, of which
                                  * https://github.com/cockroachdb/cockroach/issues/47116 crashes the server
                                  */
                break;
            case TRANSACTION:
                nrPerformed = 0; // r.getInteger(0, 0);
                break;
            default:
                throw new AssertionError(action);
            }
            if (nrPerformed != 0) {
                actions.add(action);
            }
            nrRemaining[action.ordinal()] = nrPerformed;
            total += nrPerformed;
        }

        while (total != 0) {
            Action nextAction = null;
            int selection = r.getInteger(0, total);
            int previousRange = 0;
            for (int i = 0; i < nrRemaining.length; i++) {
                if (previousRange <= selection && selection < previousRange + nrRemaining[i]) {
                    nextAction = Action.values()[i];
                    break;
                } else {
                    previousRange += nrRemaining[i];
                }
            }
            assert nextAction != null;
            assert nrRemaining[nextAction.ordinal()] > 0;
            nrRemaining[nextAction.ordinal()]--;
            Query query = null;
            try {
                boolean success;
                int nrTries = 0;
                do {
                    query = nextAction.getQuery(globalState);
                    if (options.logEachSelect()) {
                        logger.writeCurrent(query.getQueryString());
                    }
                    success = manager.execute(query);
                } while (!success && nrTries++ < 1000);
            } catch (IgnoreMeException e) {

            }
            if (query != null && query.couldAffectSchema()) {
                globalState.setSchema(CockroachDBSchema.fromConnection(con, databaseName));
                if (globalState.getSchema().getDatabaseTables().isEmpty()) {
                    throw new IgnoreMeException();
                }
            }
            total--;
        }
        manager.incrementCreateDatabase();
        if (globalState.getDmbsSpecificOptions().makeVectorizationMoreLikely && Randomly.getBoolean()) {
            manager.execute(new QueryAdapter("SET vectorize=on;"));
        }
        TestOracle oracle = globalState.getDmbsSpecificOptions().oracle.create(globalState);
        for (int i = 0; i < options.getNrQueries(); i++) {
            try {
                oracle.check();
                manager.incrementSelectQueryCount();
            } catch (IgnoreMeException e) {

            }
        }
        try {
            if (options.logEachSelect()) {
                logger.getCurrentFileWriter().close();
                logger.currentFileWriter = null;
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    @Override
    public Connection createDatabase(GlobalState<?> globalState) throws SQLException {
        String databaseName = globalState.getDatabaseName();
        String url = "jdbc:postgresql://localhost:26257/test";
        Connection con = DriverManager.getConnection(url, globalState.getOptions().getUserName(),
                globalState.getOptions().getPassword());
        globalState.getState().statements.add(new QueryAdapter("USE test"));
        globalState.getState().statements.add(new QueryAdapter("DROP DATABASE IF EXISTS " + databaseName + " CASCADE"));
        String createDatabaseCommand = "CREATE DATABASE " + databaseName;
        globalState.getState().statements.add(new QueryAdapter(createDatabaseCommand));
        globalState.getState().statements.add(new QueryAdapter("USE " + databaseName));
        try (Statement s = con.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + databaseName);
        }
        try (Statement s = con.createStatement()) {
            s.execute(createDatabaseCommand);
        }
        con.close();
        con = DriverManager.getConnection("jdbc:postgresql://localhost:26257/" + databaseName,
                globalState.getOptions().getUserName(), globalState.getOptions().getPassword());
        return con;
    }

    @Override
    public String getDBMSName() {
        return "cockroachdb";
    }

    @Override
    public StateToReproduce getStateToReproduce(String databaseName) {
        return new CockroachDBStateToReproduce(databaseName);
    }

    @Override
    public CockroachDBGlobalState generateGlobalState() {
        return new CockroachDBGlobalState();
    }

    @Override
    public CockroachDBOptions getCommand() {
        return new CockroachDBOptions();
    }
}
