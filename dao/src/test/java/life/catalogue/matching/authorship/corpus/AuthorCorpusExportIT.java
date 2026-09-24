package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.Resources;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.NomCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.postgresql.jdbc.PgConnection;

import static org.junit.Assert.assertEquals;

/**
 * Runs the statement of the export script against the real schema. The script itself is only ever run by hand
 * against production, where a column that was renamed since costs somebody a round trip.
 */
public class AuthorCorpusExportIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.nidx();

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /**
   * @return the COPY statement of the script without the psql meta commands, with the dataset keys filled in
   */
  static String copyStatement(String keys) throws Exception {
    String sql = Resources.lines(CorpusIO.EXPORT_SQL)
                          .filter(l -> !l.startsWith("\\") && !l.startsWith("--") && !l.trim().startsWith("\\"))
                          .collect(Collectors.joining("\n"));
    sql = sql.substring(sql.indexOf("COPY ("), sql.lastIndexOf(';'));
    return sql.replace(":keys", keys);
  }

  @Test
  public void exportReadsBack() throws Exception {
    File export = tmp.newFile("export.tsv");
    try (PgConnection con = pgSetupRule.connect()) {
      try (Statement st = con.createStatement()) {
        // the test data comes without parsed authors
        st.execute("UPDATE name SET combination_authors = ARRAY[authorship] WHERE authorship IS NOT NULL");
        // the classification comes from the metrics of the taxon, for a synonym from those of its accepted taxon,
        // and holds the names the group is derived from only: none below the rank of a suprageneric name
        st.execute("UPDATE name_usage SET status = 'SYNONYM', parent_id = 'u1x' WHERE dataset_key = 102 AND id = 'u2x'");
        st.execute("INSERT INTO taxon_metrics (dataset_key, taxon_id, classification) VALUES "
          + "(100, 'u1', ARRAY[('k', 'KINGDOM', 'Plantae', null)::simple_name, ('c', 'UNRANKED', 'Coniferae', null)::simple_name,"
          + "  ('f', 'FAMILY', 'Pinaceae', null)::simple_name, ('g', 'GENUS', 'Abies', null)::simple_name]),"
          + "(102, 'u1x', ARRAY[('k', 'KINGDOM', 'Plantae', null)::simple_name, ('o', 'ORDER', 'Pinales', null)::simple_name])");
      }
      try (OutputStream out = new FileOutputStream(export)) {
        con.getCopyAPI().copyOut(copyStatement("100,101,102"), out);
      }
    }

    List<List<ExportRow>> groups = new ArrayList<>();
    CorpusIO.readGroups(export, groups::add);

    // Abies alba is matched to names index entry 2 in all three datasets:
    // by Miller in 100, without any authorship in 101, which therefore is no part of the export, and twice in 102
    assertEquals(1, groups.size());
    List<ExportRow> abies = groups.get(0);
    assertEquals(2, abies.get(0).nidx());
    assertEquals(List.of(100, 102, 102), abies.stream().map(ExportRow::datasetKey).toList());
    assertEquals(List.of("n1", "n1", "n2"), abies.stream().map(ExportRow::nameId).toList());
    assertEquals("Abies alba", abies.get(0).scientificName());
    assertEquals("SPECIES", abies.get(0).rank());
    assertEquals(NomCode.BOTANICAL, abies.get(0).code());
    assertEquals(List.of("Miller"), abies.get(0).combAuthors());
    assertEquals(List.of("Mill."), abies.get(2).combAuthors());
    assertEquals(List.of("Plantae", "Coniferae", "Pinaceae"), abies.get(0).classification());
    assertEquals(List.of("Plantae", "Pinales"), abies.get(1).classification());
    assertEquals(List.of("Plantae", "Pinales"), abies.get(2).classification());
  }
}
