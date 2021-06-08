package life.catalogue.db;

import life.catalogue.common.util.YamlUtils;

import java.sql.Connection;
import java.sql.Statement;

import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class PgConfigTest {

    @Test
    public void connect() throws Exception {
        PgConfig cfg = YamlUtils.read(PgConfig.class, "/pg-test.yaml");
        connectionTest(cfg);

        //cfg = new PgConfig();
        //cfg.host = "pg1.catalogue.life";
        //cfg.user = "col";
        //cfg.database = "col";
        //cfg.password = "jeK7V19i";
        //connectionTest(cfg);
    }

    public static void connectionTest(PgConfig cfg) throws Exception {
        System.out.println(cfg);
        try (Connection c = cfg.connect()) {
            connectionTest(c);
        }
    }
    public static void connectionTest(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("SHOW SERVER_ENCODING");
            st.getResultSet().next();
            String cs = st.getResultSet().getString(1);
            assertEquals("Bad server encoding", "UTF8", cs);

            st.execute("SHOW CLIENT_ENCODING");
            st.getResultSet().next();
            cs = st.getResultSet().getString(1);
            assertEquals("Bad client encoding", "UTF8", cs);
        }
    }
}