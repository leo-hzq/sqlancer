package sqlancer;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ComparatorHelper {

    private ComparatorHelper() {
    }

    public static boolean isEqualDouble(String first, String second) {
        try {
            double val = Double.parseDouble(first);
            double secVal = Double.parseDouble(second);
            return equals(val, secVal);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean equals(double a, double b) {
        if (a == b) {
            return true;
        }
        // If the difference is less than epsilon, treat as equal.
        return Math.abs(a - b) < 0.0001 * Math.max(Math.abs(a), Math.abs(b));
    }

    public static List<String> getResultSetFirstColumnAsString(String queryString, Set<String> errors, Connection con,
            GlobalState<?> state) throws SQLException {
        if (state.getOptions().logEachSelect()) {
            // TODO: refactor me
            state.getLogger().writeCurrent(queryString);
            try {
                state.getLogger().getCurrentFileWriter().flush();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        QueryAdapter q = new QueryAdapter(queryString, errors);
        List<String> resultSet = new ArrayList<>();
        ResultSet result = null;
        try {
            result = q.executeAndGet(con);
            if (result == null) {
                throw new IgnoreMeException();
            }
            while (result.next()) {
                resultSet.add(result.getString(1));
            }
            result.getStatement().close();
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw e;
            }
            if (e instanceof NumberFormatException) {
                // https://github.com/tidb-challenge-program/bug-hunting-issue/issues/57
                throw new IgnoreMeException();
            }
            if (e.getMessage() == null) {
                throw new AssertionError(queryString, e);
            }
            for (String error : errors) {
                if (e.getMessage().contains(error)) {
                    throw new IgnoreMeException();
                }
            }
            throw new AssertionError(queryString, e);
        } finally {
            if (result != null && !result.isClosed()) {
                result.getStatement().close();
                result.close();
            }
        }
        return resultSet;
    }

    public static void assumeResultSetsAreEqual(List<String> resultSet, List<String> secondResultSet,
            String originalQueryString, List<String> combinedString, GlobalState<?> state) {
        if (resultSet.size() != secondResultSet.size()) {
            String queryFormatString = "%s; -- cardinality: %d";
            String firstQueryString = String.format(queryFormatString, originalQueryString, resultSet.size());
            String secondQueryString = String.format(queryFormatString,
                    combinedString.stream().collect(Collectors.joining(";")), secondResultSet.size());
            state.getState().statements.add(new QueryAdapter(firstQueryString));
            state.getState().statements.add(new QueryAdapter(secondQueryString));
            String assertionMessage = String.format("the size of the result sets mismatch (%d and %d)!\n%s\n%s",
                    resultSet.size(), secondResultSet.size(), firstQueryString, secondQueryString);
            throw new AssertionError(assertionMessage);
        }

        Set<String> firstHashSet = new HashSet<>(resultSet);
        Set<String> secondHashSet = new HashSet<>(secondResultSet);

        if (!firstHashSet.equals(secondHashSet)) {
            Set<String> firstResultSetMisses = new HashSet<>(firstHashSet);
            firstResultSetMisses.removeAll(secondHashSet);
            Set<String> secondResultSetMisses = new HashSet<>(secondHashSet);
            secondResultSetMisses.removeAll(firstHashSet);
            String queryFormatString = "%s; -- misses: %s";
            String firstQueryString = String.format(queryFormatString, originalQueryString, firstResultSetMisses);
            String secondQueryString = String.format(queryFormatString,
                    combinedString.stream().collect(Collectors.joining(";")), secondResultSetMisses);
            state.getState().statements.add(new QueryAdapter(firstQueryString));
            state.getState().statements.add(new QueryAdapter(secondQueryString));
            String assertionMessage = String.format("the content of the result sets mismatch!\n%s\n%s",
                    firstQueryString, secondQueryString);
            throw new AssertionError(assertionMessage);
        }
    }

    public static List<String> getCombinedResultSet(String firstQueryString, String secondQueryString,
            String thirdQueryString, List<String> combinedString, boolean asUnion, GlobalState<?> state,
            Set<String> errors) throws SQLException {
        List<String> secondResultSet;
        if (asUnion) {
            String unionString = firstQueryString + " UNION ALL " + secondQueryString + " UNION ALL "
                    + thirdQueryString;
            combinedString.add(unionString);
            secondResultSet = getResultSetFirstColumnAsString(unionString, errors, state.getConnection(), state);
        } else {
            secondResultSet = new ArrayList<>();
            secondResultSet
                    .addAll(getResultSetFirstColumnAsString(firstQueryString, errors, state.getConnection(), state));
            secondResultSet
                    .addAll(getResultSetFirstColumnAsString(secondQueryString, errors, state.getConnection(), state));
            secondResultSet
                    .addAll(getResultSetFirstColumnAsString(thirdQueryString, errors, state.getConnection(), state));
            combinedString.add(firstQueryString);
            combinedString.add(secondQueryString);
            combinedString.add(thirdQueryString);
        }
        return secondResultSet;
    }

    public static List<String> getCombinedResultSetNoDuplicates(String firstQueryString, String secondQueryString,
            String thirdQueryString, List<String> combinedString, boolean asUnion, GlobalState<?> state,
            Set<String> errors) throws SQLException {
        if (!asUnion) {
            throw new AssertionError();
        }
        List<String> secondResultSet;
        String unionString = firstQueryString + " UNION " + secondQueryString + " UNION " + thirdQueryString;
        combinedString.add(unionString);
        secondResultSet = getResultSetFirstColumnAsString(unionString, errors, state.getConnection(), state);
        return secondResultSet;
    }

}
