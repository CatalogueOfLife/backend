package life.catalogue.importer.corpus;

import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.postgresql.jdbc.PgConnection;

import static org.junit.Assert.*;

/**
 * Runs the statements of the export script against the real schema and the corpus tools on what they export.
 * The script itself is only ever run by hand against production, where a column renamed since costs a round trip.
 */
public class InterpreterCorpusExportIT {
  private static final Pattern COPY = Pattern.compile("COPY \\(.+?\\) TO STDOUT WITH \\([^)]+\\)", Pattern.DOTALL);

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /**
   * @return the COPY statements of the script with the psql variables filled in as the script's \set would
   */
  static List<String> copyStatements(String keys, String tablesample) throws IOException {
    String sql;
    try (InputStream in = InterpreterCorpusExportIT.class.getResourceAsStream("/interpreter-corpus/interpreter-corpus-export.sql")) {
      sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
        .filter(l -> !l.trim().startsWith("\\") && !l.trim().startsWith("--"))
        .collect(Collectors.joining("\n"));
    }
    String datasets = keys == null ? "" : "AND dataset_key IN (" + keys + ")";
    List<String> statements = new ArrayList<>();
    Matcher m = COPY.matcher(sql);
    while (m.find()) {
      statements.add(m.group().replace(":datasets", datasets).replace(":tablesample", tablesample));
    }
    return statements;
  }

  private File export(String keys, String tablesample) throws Exception {
    File export = tmp.newFile();
    List<String> statements = copyStatements(keys, tablesample);
    assertEquals(2, statements.size());
    try (PgConnection con = pgSetupRule.connect();
         OutputStream out = new FileOutputStream(export)
    ) {
      for (String copy : statements) {
        con.getCopyAPI().copyOut(copy, out);
      }
    }
    return export;
  }

  @Test
  public void exportAndRun() throws Exception {
    try (PgConnection con = pgSetupRule.connect(); Statement st = con.createStatement()) {
      st.execute("UPDATE dataset SET settings = '{\"nomenclatural code\": \"zoological\", \"prefer name atoms\": false, \"title\": \"x\"}' WHERE key = 11");
      st.execute("UPDATE dataset SET settings = '{\"dont infer ranks\": true}' WHERE key = 12");
      st.execute("INSERT INTO verbatim (id, dataset_key, line, file, type, terms) VALUES "
        // two identical names collapse into one row with n=2, the taxonomic status is no name term
        + "(1001, 11, 2, 'Taxon.tsv', 'dwc:Taxon', '{\"dwc:scientificName\": \"Abies alba\", \"dwc:scientificNameAuthorship\": \"Mill.\", \"dwc:taxonomicStatus\": \"accepted\"}'),"
        + "(1002, 11, 3, 'Taxon.tsv', 'dwc:Taxon', '{\"dwc:scientificName\": \"Abies alba\", \"dwc:scientificNameAuthorship\": \"Mill.\", \"dwc:taxonomicStatus\": \"synonym\"}'),"
        // empty terms are dropped
        + "(1003, 11, 4, 'Taxon.tsv', 'dwc:Taxon', '{\"dwc:genus\": \"Abies\", \"dwc:specificEpithet\": \"alba\", \"dwc:scientificName\": \"\"}'),"
        // a ColDP Name keeps its name status in col:status, a NameUsage its taxonomic status
        + "(1004, 12, 2, 'Name.tsv', 'col:Name', '{\"col:ID\": \"1\", \"col:scientificName\": \"Abies alba\", \"col:status\": \"manuscript\"}'),"
        + "(1005, 12, 2, 'NameUsage.tsv', 'col:NameUsage', '{\"col:ID\": \"2\", \"col:scientificName\": \"Picea abies\", \"col:status\": \"accepted\", \"col:nameStatus\": \"nom. nud.\"}'),"
        // no name record at all
        + "(1006, 12, 3, 'Reference.tsv', 'col:Reference', '{\"col:ID\": \"r1\", \"col:citation\": \"Miller 1768\"}')"
      );
    }

    File export = export(null, "");
    List<String> lines = java.nio.file.Files.readAllLines(export.toPath());
    assertEquals("dataset_key\ttype\tterms\tn", lines.get(0));
    // the settings come first and keep only what the name interpretation reads
    assertTrue(lines.get(1), lines.get(1).startsWith("11\tdataset\t"));
    assertFalse(lines.get(1), lines.get(1).contains("title"));
    assertTrue(lines.get(2), lines.get(2).startsWith("12\tdataset\t"));

    File run = tmp.newFolder("run");
    String meta = new InterpreterCorpusRunner().run(export, run, 1, 42, 2, 0);
    assertTrue(meta, meta.contains("records: 4 interpreted (5 weighted)"));
    Map<Long, String[]> rows = InterpreterCorpusTest.rows(run);
    Map<String, String[]> byName = rows.values().stream()
      .collect(Collectors.toMap(r -> r[Field.TYPE.ordinal()] + " " + r[Field.LABEL.ordinal()], r -> r));
    String[] abies = byName.get("dwc:Taxon Abies alba Mill.");
    assertEquals("2", abies[Field.N.ordinal()]);
    assertEquals("ZOOLOGICAL", abies[Field.CODE.ordinal()]);
    assertFalse(abies[Field.INPUT.ordinal()].contains("taxonomicStatus"));
    assertNotNull(byName.get("dwc:Taxon Abies alba"));
    assertEquals("MANUSCRIPT", byName.get("col:Name Abies alba")[Field.NOM_STATUS.ordinal()]);
    assertEquals("NOT_ESTABLISHED", byName.get("col:NameUsage Picea abies")[Field.NOM_STATUS.ordinal()]);

    // restricted to one dataset and sampled: the statements must stay valid on the partitioned table
    File one = export("12", "TABLESAMPLE BERNOULLI (100) REPEATABLE (42)");
    List<String> oneLines = java.nio.file.Files.readAllLines(one.toPath());
    assertEquals(4, oneLines.size());
    assertTrue(oneLines.stream().skip(1).allMatch(l -> l.startsWith("12\t")));
  }
}
