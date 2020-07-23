package sqlancer.clickhouse.gen;

import java.util.HashSet;
import java.util.Set;

import sqlancer.Query;
import sqlancer.QueryAdapter;
import sqlancer.Randomly;
import sqlancer.clickhouse.ClickHouseProvider.ClickHouseGlobalState;
import sqlancer.clickhouse.ClickHouseSchema.ClickHouseLancerDataType;

public class ClickHouseTableGenerator {

    StringBuilder sb = new StringBuilder("CREATE TABLE ");
    Set<String> errors = new HashSet<>();

    public ClickHouseTableGenerator() {
        errors.add("Memory limit");
        errors.add("Directory for table data");
        errors.add("Directory not empty");
    }

    private enum ClickHouseEngine {
        // TinyLog, StripeLog, Log, // Disabled until https://github.com/ClickHouse/ClickHouse/pull/12426 is released
        Memory, MergeTree
    }

    public Query getQuery(ClickHouseGlobalState globalState) {
        ClickHouseEngine engine = Randomly.fromOptions(ClickHouseEngine.values());
        sb.append(globalState.getSchema().getFreeTableName());
        sb.append("(");
        for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append("c");
            sb.append(i);
            sb.append(" ");
            if (Randomly.getBoolean()) {
                sb.append(ClickHouseLancerDataType.getRandom());
            } else {
                // sb.append("Nullable(");
                sb.append(ClickHouseLancerDataType.getRandom());
                // sb.append(")");
            }
            potentiallyAppendCodec();
        }
        sb.append(") ENGINE = ");
        sb.append(engine);
        sb.append("(");
        sb.append(")");
        if (engine == ClickHouseEngine.MergeTree) {
            sb.append(" ORDER BY tuple()");
        }
        sb.append(";");
        return new QueryAdapter(sb.toString(), errors, true);
    }

    private void potentiallyAppendCodec() {
        if (Randomly.getBoolean()) {
            sb.append(" CODEC(");
            errors.add(" in memory is not of fixed size");
            sb.append(Randomly.fromOptions("NONE", "ZSTD", "LZ4HC"));
            sb.append(")");
        }
    }

}
