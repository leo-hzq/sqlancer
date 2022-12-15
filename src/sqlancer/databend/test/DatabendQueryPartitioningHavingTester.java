package sqlancer.databend.test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.ast.newast.Node;
import sqlancer.common.oracle.TestOracle;
import sqlancer.databend.DatabendErrors;
import sqlancer.databend.DatabendProvider.DatabendGlobalState;
import sqlancer.databend.DatabendSchema;
import sqlancer.databend.DatabendToStringVisitor;
import sqlancer.databend.ast.DatabendConstant;
import sqlancer.databend.ast.DatabendExpression;

public class DatabendQueryPartitioningHavingTester extends DatabendQueryPartitioningBase
        implements TestOracle<DatabendGlobalState> {

    public DatabendQueryPartitioningHavingTester(DatabendGlobalState state) {
        super(state);
        DatabendErrors.addGroupByErrors(errors);
    }

    @Override
    public String check() throws SQLException {
        super.check();
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateExpression(DatabendSchema.DatabendDataType.BOOLEAN));
        }
        // boolean orderBy = Randomly.getBoolean();
        boolean orderBy = false; // 关闭order by
        if (orderBy) { // TODO 生成columns.size()的子集，有个错误：order by 后不能直接union，需要包装一层select
            // select.setOrderByExpressions(gen.generateOrderBys());
            List<Node<DatabendExpression>> constants = new ArrayList<>();
            constants.add(new DatabendConstant.DatabendIntConstant(
                    Randomly.smallNumber() % select.getFetchColumns().size() + 1));
            select.setOrderByExpressions(constants);
        }
        // select.setGroupByExpressions(gen.generateExpressions(Randomly.smallNumber() + 1));
        select.setGroupByExpressions(select.getFetchColumns());
        select.setHavingClause(null);
        String originalQueryString = DatabendToStringVisitor.asString(select);
        // System.out.println(originalQueryString);
        List<String> resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);

        select.setHavingClause(predicate);
        String firstQueryString = DatabendToStringVisitor.asString(select);
        select.setHavingClause(negatedPredicate);
        String secondQueryString = DatabendToStringVisitor.asString(select);
        select.setHavingClause(isNullPredicate);
        String thirdQueryString = DatabendToStringVisitor.asString(select);
        List<String> combinedString = new ArrayList<>();
        List<String> secondResultSet = ComparatorHelper.getCombinedResultSet(firstQueryString, secondQueryString,
                thirdQueryString, combinedString, !orderBy, state, errors);
        ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet, originalQueryString, combinedString,
                state, DatabendQueryPartitioningBase::canonicalizeResultValue);
        return "Not implemented!";
    }

    @Override
    protected Node<DatabendExpression> generatePredicate() {
        return gen.generateHavingClause();
    }

    @Override
    List<Node<DatabendExpression>> generateFetchColumns() {
        return Collections.singletonList(gen.generateHavingClause());
    }

}
