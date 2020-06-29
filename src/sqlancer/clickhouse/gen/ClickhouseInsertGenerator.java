package sqlancer.clickhouse.gen;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import sqlancer.Query;
import sqlancer.QueryAdapter;
import sqlancer.clickhouse.ClickhouseProvider.ClickhouseGlobalState;
import sqlancer.clickhouse.ClickhouseSchema.ClickhouseColumn;
import sqlancer.clickhouse.ClickhouseSchema.ClickhouseTable;
import sqlancer.clickhouse.ClickhouseToStringVisitor;
import sqlancer.gen.AbstractInsertGenerator;

public class ClickhouseInsertGenerator extends AbstractInsertGenerator<ClickhouseColumn> {

    private final ClickhouseGlobalState globalState;
    private final Set<String> errors = new HashSet<>();
    private final ClickhouseExpressionGenerator gen;

    public ClickhouseInsertGenerator(ClickhouseGlobalState globalState) {
        this.globalState = globalState;
        gen = new ClickhouseExpressionGenerator(globalState);
        errors.add("Cannot insert NULL value into a column of type 'Int32'"); // TODO
        errors.add("Cannot insert NULL value into a column of type 'String'");

        errors.add("Cannot parse string");
        errors.add("Cannot parse Int32 from String, because value is too short");
    }

    public static Query getQuery(ClickhouseGlobalState globalState) throws SQLException {
        return new ClickhouseInsertGenerator(globalState).get();
    }

    private Query get() {
        ClickhouseTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
        List<ClickhouseColumn> columns = table.getRandomNonEmptyColumnSubset();
        sb.append("INSERT INTO ");
        sb.append(table.getName());
        sb.append("(");
        sb.append(columns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
        sb.append(")");
        sb.append(" VALUES ");
        insertColumns(columns);
        return new QueryAdapter(sb.toString(), errors);
    }

    @Override
    protected void insertValue(ClickhouseColumn column) {
        String s = ClickhouseToStringVisitor.asString(gen.generateConstant());
        sb.append(s);
    }

}
