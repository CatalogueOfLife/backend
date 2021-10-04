package life.catalogue.command;

import life.catalogue.api.vocab.DatasetOrigin;
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

public class AddTableCmdTest {

    @ClassRule
    public static PgSetupRule pgSetupRule = new PgSetupRule();

    @Rule
    public final TestDataRule testDataRule = TestDataRule.apple();


    @Test
    public void keys() throws Exception {
        try (Connection con = pgSetupRule.connect()) {
          Set<String> keys = AddTableCmd.partitionSuffices(con, null);
          assertEquals(Set.of("3","11", "mod1", "mod0"), keys); // 12 is external, so kept in default partition

          keys = AddTableCmd.partitionSuffices(con, DatasetOrigin.MANAGED);
          assertEquals(Set.of("3","11"), keys);

          keys = AddTableCmd.partitionSuffices(con, DatasetOrigin.EXTERNAL);
          assertEquals(Set.of("mod1", "mod0"), keys);

          keys = AddTableCmd.partitionSuffices(con, DatasetOrigin.RELEASED);
          assertEquals(Set.of(), keys);
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