package life.catalogue.command;

import life.catalogue.command.AddTableCmd;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class AddTableCmd2Test {

    @ClassRule
    public static PgSetupRule pgSetupRule = new PgSetupRule();

    @Rule
    public final TestDataRule testDataRule = TestDataRule.apple();


    @Test
    public void keys() throws Exception {
        try (Connection con = pgSetupRule.connect()) {
            Set<Integer> keys = AddTableCmd.datasetKeys(con, null);
            Set<Integer> expected = TestDataRule.APPLE.datasetKeys;
            assertEquals(expected, keys);
        }
    }

    @Test
    public void analyze() throws Exception {
        try (Connection con = pgSetupRule.connect();
             Statement st = con.createStatement()
        ) {
            List<AddTableCmd.ForeignKey> fks = AddTableCmd.analyze(st, "type_material");
            assertEquals(3, fks.size());
            assertEquals("verbatim_key", fks.get(0).column);
            assertEquals("name_id", fks.get(1).column);
            assertEquals("reference_id", fks.get(2).column);
        }
    }

}