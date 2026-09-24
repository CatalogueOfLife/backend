package life.catalogue.matching.authorship.corpus;

import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import life.catalogue.common.io.UTF8IoUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CorpusIOTest {
  private static final String HEADER = String.join("\t", ExportRow.COLUMNS) + "\n";

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private File export(String name, String... lines) throws IOException {
    return exportWithHeader(name, HEADER, lines);
  }

  private File exportWithHeader(String name, String header, String... lines) throws IOException {
    File f = tmp.newFile(name);
    try (Writer w = name.endsWith(".gz") ? UTF8IoUtils.writerFromGzipFile(f) : UTF8IoUtils.writerFromFile(f)) {
      w.write(header);
      for (String l : lines) {
        w.write(l);
        w.write('\n');
      }
    }
    return f;
  }

  private static List<List<ExportRow>> groups(File f) throws IOException {
    List<List<ExportRow>> groups = new ArrayList<>();
    CorpusIO.readGroups(f, groups::add);
    return groups;
  }

  @Test
  public void readsAllColumns() throws Exception {
    File f = export("e.tsv",
      "7\t2006\tn1\tSPECIES\tBOTANICAL\tACCEPTABLE\tAus bus\t(L.) Hook. ex Mill., 1768\tMill.\tHook.\t1768\tL.\t\t1753\tFr.\t"
        + "Plantae|Pinaceae",
      "7\t2004\tn2\tSPECIES\t\t\tAus bus\tL.\tL.\t\t\t\t\t\t"
    );
    var rows = groups(f).get(0);
    assertEquals(2, rows.size());

    ExportRow r = rows.get(0);
    assertEquals(7, r.nidx());
    assertEquals(2006, r.datasetKey());
    assertEquals("n1", r.nameId());
    assertEquals("SPECIES", r.rank());
    assertEquals(NomCode.BOTANICAL, r.code());
    assertEquals("ACCEPTABLE", r.nomStatus());
    assertEquals("Aus bus", r.scientificName());
    assertEquals("(L.) Hook. ex Mill., 1768", r.authorship());
    assertEquals(List.of("Mill."), r.combAuthors());
    assertEquals(List.of("Hook."), r.combExAuthors());
    assertEquals("1768", r.combYear());
    assertEquals(List.of("L."), r.basAuthors());
    assertEquals(List.of(), r.basExAuthors());
    assertEquals("1753", r.basYear());
    assertEquals("Fr.", r.sanctioningAuthor());
    assertEquals(List.of("Plantae", "Pinaceae"), r.classification());
    assertEquals(List.of(), rows.get(1).classification());
  }

  /** an export from before the classification column still reads, without a classification */
  @Test
  public void exportWithoutClassification() throws Exception {
    String header = String.join("\t", ExportRow.COLUMNS.subList(0, ExportRow.COLUMNS.size() - 1)) + "\n";
    File f = exportWithHeader("e.tsv", header, "7\t2006\tn1\tSPECIES\t\t\tAus bus\tL.\tL.\t\t\t\t\t\t");
    ExportRow r = groups(f).get(0).get(0);
    assertEquals(List.of("L."), r.combAuthors());
    assertEquals(List.of(), r.classification());
  }

  /** the group is not stored anywhere, it is derived from the classification as the matching derives it */
  @Test
  public void classificationGivesTheGroup() throws Exception {
    File f = export("e.tsv",
      "7\t2006\tn1\tSPECIES\t\t\tAus bus\tL.\tL.\t\t\t\t\t\t\tAnimalia|Mollusca|Gastropoda",
      "7\t2004\tn2\tSPECIES\tZOOLOGICAL\t\tAus bus\tL.\tL.\t\t\t\t\t\t\t");
    var rows = groups(f).get(0);
    assertEquals(TaxGroup.Gastropods, rows.get(0).group());
    assertEquals(TaxGroup.Eukaryotes, rows.get(1).group());
  }

  /**
   * Nomenclators hold bare names without a taxon, so without a classification. A dataset of one group gives its names
   * that group then, but never overrides a classification.
   */
  @Test
  public void datasetDefaultGroup() throws Exception {
    File f = export("e.tsv",
      "7\t2003\tn1\tSPECIES\tBOTANICAL\t\tAus bus\tL.\tL.\t\t\t\t\t\t\t",
      "7\t2073\tn2\tSPECIES\tBOTANICAL\t\tAus bus\tL.\tL.\t\t\t\t\t\t\t",
      "7\t2073\tn3\tSPECIES\tBOTANICAL\t\tAus bus\tL.\tL.\t\t\t\t\t\t\tAnimalia|Mollusca|Gastropoda",
      "7\t2037\tn4\tSPECIES\t\t\tAus bus\tL.\tL.\t\t\t\t\t\t\t",
      "7\t2006\tn5\tSPECIES\tBOTANICAL\t\tAus bus\tL.\tL.\t\t\t\t\t\t\t");
    var rows = groups(f).get(0);
    assertEquals(TaxGroup.Algae, rows.get(0).group());
    assertEquals(TaxGroup.Fungi, rows.get(1).group());
    assertEquals(TaxGroup.Gastropods, rows.get(2).group());
    assertEquals(TaxGroup.Eukaryotes, rows.get(3).group());
    assertEquals(TaxGroup.Eukaryotes, rows.get(4).group());
  }

  /**
   * The last column, the sanctioning author, is empty in nearly every row.
   * A reader that drops trailing empty columns must not make such a row unreadable.
   */
  @Test
  public void emptyTrailingColumns() throws Exception {
    File f = export("e.tsv",
      "7\t2006\tn1\tSPECIES\tBOTANICAL\t\tAus bus\tL.\tL.\t\t\t\t\t\t",
      "7\t2004\tn2\tSPECIES\t\t\tAus bus\tMill.\tMill."
    );
    var rows = groups(f).get(0);
    assertEquals(List.of("L."), rows.get(0).combAuthors());
    assertNull(rows.get(0).sanctioningAuthor());
    assertNull(rows.get(0).combYear());
    assertNull(rows.get(1).code());
    assertEquals(List.of("Mill."), rows.get(1).combAuthors());
    assertEquals(List.of(), rows.get(1).basAuthors());
  }

  @Test
  public void splitsAuthorTeams() throws Exception {
    File f = export("e.tsv", "7\t2006\tn1\tSPECIES\t\t\tAus bus\tx\tA.J. White|Herbert|P.J. Harvey\t\t\t\t\t\t");
    assertEquals(List.of("A.J. White", "Herbert", "P.J. Harvey"), groups(f).get(0).get(0).combAuthors());
  }

  /** postgres COPY escapes a backslash as two */
  @Test
  public void unescapesPgCopy() throws Exception {
    File f = export("e.tsv", "7\t2006\tn1\tSPECIES\t\t\tAus bus\tL. \\\\ Mill.\tL.\t\t\t\t\t\t");
    assertEquals("L. \\ Mill.", groups(f).get(0).get(0).authorship());
  }

  @Test
  public void groupsByNidxAndRank() throws Exception {
    File f = export("e.tsv",
      "7\t2006\tn1\tSPECIES\t\t\tAus bus\tL.\tL.\t\t\t\t\t\t",
      "7\t2004\tn2\tSPECIES\t\t\tAus bus\tL.\tL.\t\t\t\t\t\t",
      "7\t2004\tn3\tSUBSPECIES\t\t\tAus bus\tL.\tL.\t\t\t\t\t\t",
      "8\t2004\tn4\tSUBSPECIES\t\t\tAus cus\tL.\tL.\t\t\t\t\t\t"
    );
    var groups = groups(f);
    assertEquals(3, groups.size());
    assertEquals(2, groups.get(0).size());
    assertEquals("n3", groups.get(1).get(0).nameId());
    assertEquals("n4", groups.get(2).get(0).nameId());
  }

  @Test
  public void readsGzip() throws Exception {
    File f = export("e.tsv.gz", "7\t2006\tn1\tSPECIES\t\t\tAus bus\tL.\tL.\t\t\t\t\t\t");
    assertEquals("n1", groups(f).get(0).get(0).nameId());
  }

  /** a file that is not the export, or one from an older script, has to fail and not be read as something else */
  @Test
  public void failsOnWrongHeader() throws Exception {
    File f = tmp.newFile("bad.tsv");
    try (Writer w = UTF8IoUtils.writerFromFile(f)) {
      w.write("index_id\tdataset_key\tname\n7\t2006\tAus bus\n");
    }
    var e = assertThrows(IllegalArgumentException.class, () -> groups(f));
    assertTrue(e.getMessage(), e.getMessage().contains("header"));
  }
}
