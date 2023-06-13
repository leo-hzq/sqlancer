package sqlancer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;

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
        return Math.abs(a - b) < 0.001 * Math.max(Math.abs(a), Math.abs(b)) + 0.001;
    }

    public static List<String> getResultSetFirstColumnAsString(String queryString, ExpectedErrors errors,
                                                               SQLGlobalState<?, ?> state) throws SQLException {
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
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors);
        List<String> resultSet = new ArrayList<>();
        SQLancerResultSet result = null;
        try {
            result = q.executeAndGet(state);
            if (result == null) {
                throw new IgnoreMeException();
            }
            while (result.next()) {
                String resultTemp = result.getString(1);
                if (resultTemp != null) {
                    resultTemp = resultTemp.replaceAll("[\\.]0+$", ""); // Remove the trailing zeros as many DBMS treat
                    // it as non-bugs
                }
                resultSet.add(resultTemp);
            }
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw e;
            }

            if (e.getMessage() == null) {
                throw new AssertionError(queryString, e);
            }
            if (errors.errorIsExpected(e.getMessage())) {
                throw new IgnoreMeException();
            }
            throw new AssertionError(queryString, e);
        } finally {
            if (result != null && !result.isClosed()) {
                result.close();
            }
        }
        return resultSet;
    }

    public static void assumeResultSetsAreEqual(List<String> result1, List<String> result2, String originalQuery,
                                                List<String> combinedQuery, SQLGlobalState<?, ?> state) {
        if (result1.size() != result2.size()) {
            String queryFormat = "-- %s;\n-- cardinality: %d\n\n\n";
            String firstQuery = String.format(queryFormat, originalQuery, result1.size());
            String combinedQueryStr = String.join(";", combinedQuery);
            String secondQuery = String.format(queryFormat, combinedQueryStr, result2.size());
            state.getState().getLocalState().log(String.format("%s\n%s", firstQuery, secondQuery));
            String assertionMessage = String.format("the size of the result sets mismatch (%d and %d)!\nfirstQuery: %s\nsecondQuery: %s\n\n\n",
                    result1.size(), result2.size(), firstQuery, secondQuery);
            throw new AssertionError(assertionMessage);
        }

        Set<String> firstHashSet = new HashSet<>(result1);
        Set<String> secondHashSet = new HashSet<>(result2);

        if (!firstHashSet.equals(secondHashSet)) {
            Set<String> firstResultSetMisses = new HashSet<>(firstHashSet);
            firstResultSetMisses.removeAll(secondHashSet);
            Set<String> secondResultSetMisses = new HashSet<>(secondHashSet);
            secondResultSetMisses.removeAll(firstHashSet);
            String format = "-- %s;\n-- misses: %s\n\n\n";
            String firstQuery = String.format(format, originalQuery, firstResultSetMisses);
            String secondQuery = String.format(format,
                    String.join(";", combinedQuery), secondResultSetMisses);
            // update the SELECT queries to be logged at the bottom of the error log file
            state.getState().getLocalState().log(String.format("%s\n%s", firstQuery, secondQuery));
            String assertionMessage = String.format("the content of the result sets mismatch!\nfirstQuery: %s\nsecondQuery: %s\n\n\n",
                    firstQuery, secondQuery);
            throw new AssertionError(assertionMessage);
        }
    }

    public static void assumeResultSetsAreEqual(List<String> resultSet, List<String> secondResultSet,
                                                String originalQueryString, List<String> combinedString, SQLGlobalState<?, ?> state,
                                                UnaryOperator<String> canonicalizationRule) {
        // Overloaded version of assumeResultSetsAreEqual that takes a canonicalization function which is applied to
        // both result sets before their comparison.
        List<String> canonicalizedResultSet = resultSet.stream().map(canonicalizationRule).collect(Collectors.toList());
        List<String> canonicalizedSecondResultSet = secondResultSet.stream().map(canonicalizationRule)
                .collect(Collectors.toList());
        assumeResultSetsAreEqual(canonicalizedResultSet, canonicalizedSecondResultSet, originalQueryString,
                combinedString, state);
    }

    public static List<String> getCombinedResultSet(String firstQueryString, String secondQueryString,
                                                    String thirdQueryString, List<String> combinedString, boolean asUnion, SQLGlobalState<?, ?> state,
                                                    ExpectedErrors errors) throws SQLException {
        List<String> secondResultSet;
        if (asUnion) {
            String unionString = firstQueryString + " UNION ALL " + secondQueryString + " UNION ALL "
                    + thirdQueryString;
            combinedString.add(unionString);
            secondResultSet = getResultSetFirstColumnAsString(unionString, errors, state);
        } else {
            secondResultSet = new ArrayList<>();
            secondResultSet.addAll(getResultSetFirstColumnAsString(firstQueryString, errors, state));
            secondResultSet.addAll(getResultSetFirstColumnAsString(secondQueryString, errors, state));
            secondResultSet.addAll(getResultSetFirstColumnAsString(thirdQueryString, errors, state));
            combinedString.add(firstQueryString);
            combinedString.add(secondQueryString);
            combinedString.add(thirdQueryString);
        }
        return secondResultSet;
    }

    public static List<String> getCombinedResultSetNoDuplicates(String firstQueryString, String secondQueryString,
                                                                String thirdQueryString, List<String> combinedString, boolean asUnion, SQLGlobalState<?, ?> state,
                                                                ExpectedErrors errors) throws SQLException {
        String unionString;
        if (asUnion) {
            unionString = firstQueryString + " UNION " + secondQueryString + " UNION " + thirdQueryString;
        } else {
            unionString = "SELECT DISTINCT * FROM (" + firstQueryString + " UNION ALL " + secondQueryString
                    + " UNION ALL " + thirdQueryString + ")";
        }
        List<String> secondResultSet;
        combinedString.add(unionString);
        secondResultSet = getResultSetFirstColumnAsString(unionString, errors, state);
        return secondResultSet;
    }

    public static String canonicalizeResultValue(String value) {
        if (value == null) {
            return value;
        }

        switch (value) {
            case "-0.0":
                return "0.0";
            case "-0":
                return "0";
            default:
        }

        return value;
    }

}
