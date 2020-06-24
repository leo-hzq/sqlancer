package sqlancer.dbms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import sqlancer.Main;

public class TestDuckDB {

    @Test
    public void testDuckDB() {
        // run with one thread due to multithreading issues, see https://github.com/sqlancer/sqlancer/pull/45
        assertEquals(0, Main.executeMain(new String[] { "--timeout-seconds", TestConfig.SECONDS, "--num-threads", "1",
                "--num-queries", TestConfig.NUM_QUERIES, "duckdb", "--oracle", "NoREC" }));
        assertEquals(0, Main.executeMain(new String[] { "--timeout-seconds", TestConfig.SECONDS, "--num-threads", "1",
                "--num-queries", TestConfig.NUM_QUERIES, "duckdb", "--oracle", "QUERY_PARTITIONING" }));
    }

}
