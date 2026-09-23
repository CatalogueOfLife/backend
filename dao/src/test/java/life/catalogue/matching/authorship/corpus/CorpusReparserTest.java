package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.TabWriter;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class CorpusReparserTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private final CorpusReparser reparser = new CorpusReparser(NameParser.PARSER);

  private static ExportRow row(String rank, String name, String authorship, List<String> comb, String combYear) {
    return new ExportRow(7, 2011, "n1", rank, NomCode.BOTANICAL, null, name, authorship,
      comb, List.of(), combYear, List.of(), List.of(), null, null);
  }

  /** the export holds the parse of the day a dataset was imported, name-parser-rust#20 among it */
  @Test
  public void rewritesAStaleParse() {
    ExportRow stale = row("SPECIES", "Actinocyclus australis", "Grunow in Van Heurck, 1883", List.of("Grunow in Van Heurck"), "1883");
    ExportRow r = reparser.reparse(stale);
    assertEquals(List.of("Grunow"), r.combAuthors());
    assertEquals("1883", r.combYear());
    // everything that is not a parse stays as exported, the code above all: the miner pairs by it
    assertEquals(7, r.nidx());
    assertEquals(2011, r.datasetKey());
    assertEquals("n1", r.nameId());
    assertEquals("SPECIES", r.rank());
    assertEquals(NomCode.BOTANICAL, r.code());
    assertEquals("Actinocyclus australis", r.scientificName());
    assertEquals("Grunow in Van Heurck, 1883", r.authorship());
  }

  @Test
  public void keepsAParseThatIsStillRight() {
    ExportRow linne = row("SPECIES", "Aus bus", "L., 1753", List.of("L."), "1753");
    assertEquals(linne, reparser.reparse(linne));
  }

  /** the export script only exports names with an author, so a name that lost its authors is dropped */
  @Test
  public void dropsANameWithoutAuthors() {
    assertNull(reparser.reparse(row("SPECIES", "Aus bus", "1883", List.of("1883"), null)));
    // not a scientific name at all
    assertNull(reparser.reparse(row("SPECIES", "Tobacco mosaic virus", "Smith", List.of("Smith"), null)));
  }

  @Test
  public void unknownRankIsUnranked() {
    ExportRow r = reparser.reparse(row("NO_SUCH_RANK", "Aus bus", "L.", List.of("L."), null));
    assertEquals(List.of("L."), r.combAuthors());
    assertEquals("NO_SUCH_RANK", r.rank());
  }

  @Test
  public void rowRoundTrip() {
    ExportRow r = new ExportRow(1, 2, "x", "SPECIES", null, "nom. illeg.", "Aus bus", "(Mill.) L. ex DC., 1753",
      List.of("L."), List.of("DC."), "1753", List.of("Mill."), List.of(), null, "Fr.");
    assertEquals(r, ExportRow.of(r.toRow()));
  }

  @Test
  public void file() throws Exception {
    File export = tmp.newFile("export.tsv.gz");
    try (TabWriter w = CorpusIO.exportWriter(export)) {
      w.write(row("SPECIES", "Actinocyclus australis", "Grunow in Van Heurck, 1883", List.of("Grunow in Van Heurck"), "1883").toRow());
      w.write(row("SPECIES", "Aus bus", "L., 1753", List.of("L."), "1753").toRow());
      w.write(row("SPECIES", "Aus bus", "1883", List.of("1883"), null).toRow());
    }
    File out = new File(tmp.getRoot(), "reparsed/export.tsv.gz");
    String stats = reparser.reparse(export, out);

    List<ExportRow> rows = new ArrayList<>();
    CorpusIO.readGroups(out, rows::addAll);
    assertEquals(2, rows.size());
    assertEquals(List.of("Grunow"), rows.get(0).combAuthors());
    assertEquals(List.of("L."), rows.get(1).combAuthors());

    assertTrue(stats, stats.contains("name parser: " + NameParserVersion.get()));
    // 3 rows read, the Grunow one changed, the year only one dropped
    assertTrue(stats, stats.matches("(?s).*\\ball\\s+3\\s+1\\s+1\\b.*"));
  }

  /** pg COPY escapes a backslash and a tab, and a re-parsed export must read back exactly as it was written */
  @Test
  public void escapedRoundTrip() throws Exception {
    ExportRow r = row("SPECIES", "Aus bus", "Smith\\Jones\tx", List.of("Smith"), null);
    File export = tmp.newFile("escaped.tsv.gz");
    try (TabWriter w = CorpusIO.exportWriter(export)) {
      w.write(r.toRow());
    }
    List<ExportRow> rows = new ArrayList<>();
    CorpusIO.readGroups(export, rows::addAll);
    assertEquals(List.of(r), rows);
  }

  @Test
  public void parserVersion() {
    String v = NameParserVersion.get();
    assertTrue(v, v.matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)? \\(.+ of .+\\)"));
  }
}
