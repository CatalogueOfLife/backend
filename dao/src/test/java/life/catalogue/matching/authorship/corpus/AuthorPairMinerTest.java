package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;
import life.catalogue.matching.authorship.corpus.LabelRules.Source;

import org.gbif.nameparser.api.NomCode;

import java.io.File;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Mines <code>author-corpus/fixture-export.tsv</code>, in which every labelling rule has one group with a known outcome.
 */
public class AuthorPairMinerTest {
  static final String FIXTURE = "author-corpus/fixture-export.tsv";

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private AuthorPairMiner.Stats stats;
  private Map<String, AuthorPair> pairs;

  @Before
  public void mine() throws Exception {
    File out = tmp.newFile("pairs.tsv.gz");
    stats = new AuthorPairMiner(LabelRules.DEFAULT, 20).mine(Resources.toFile(FIXTURE), out);
    pairs = CorpusIO.readPairs(out).stream().collect(Collectors.toMap(p -> p.keyA() + " " + p.keyB(), Function.identity()));
  }

  private AuthorPair pair(String keyA, String keyB) {
    return pairs.get(keyA + " " + keyB);
  }

  /** the parser a re-parsed export was parsed with travels on to the pairs, for the report header */
  @Test
  public void parserTravelsWithThePairs() throws Exception {
    File export = new File(tmp.getRoot(), "export.tsv");
    java.nio.file.Files.copy(Resources.toFile(FIXTURE).toPath(), export.toPath());
    CorpusIO.writeParser(export, "0.2.2-SNAPSHOT (test)");
    File out = new File(tmp.getRoot(), "reparsed-pairs.tsv.gz");
    new AuthorPairMiner(LabelRules.DEFAULT, 20).mine(export, out);
    assertEquals("0.2.2-SNAPSHOT (test)", CorpusIO.readParser(out));
  }

  /** an export straight from the database carries the parses of its imports, and says nothing */
  @Test
  public void noParserWithoutAReparse() throws Exception {
    assertEquals(null, CorpusIO.readParser(new File(tmp.getRoot(), "pairs.tsv.gz")));
  }

  @Test
  public void writesLabelledPairsOnly() {
    assertEquals(
      List.of(";;;bryk ;;;bryk|eisner", ";;;dc ;;;decandolle", ";;;hook ;;;mill", ";;;hooker ;;;miller", ";;;l ;;;linné",
        ";;;pallas ;;;pimbus"),
      pairs.keySet().stream().sorted().toList()
    );
  }

  @Test
  public void aliasRecurringAcrossDatasetsIsTheSame() {
    var p = pair(";;;l", ";;;linné");
    assertEquals(Label.SAME, p.label());
    assertEquals(Source.CROSS, p.source());
    assertEquals(3, p.weight());
    assertEquals(new PairStat(3, 1, 0, 3, 3, 0, 0, 0), p.stat());
  }

  /** the first key in alphabetical order is side A, whichever dataset it came from */
  @Test
  public void sidesAreOrderedByKey() {
    var p = pair(";;;l", ";;;linné");
    assertEquals("L., 1753", p.a().authorship());
    assertEquals("Linné, 1753", p.b().authorship());
    assertEquals(List.of("L."), p.a().combAuthors());
    assertEquals(List.of("Linné"), p.b().combAuthors());
  }

  /** a pair is shown with years wherever the corpus has them, as that is how production gets to see it */
  @Test
  public void representativePrefersAgreeingYears() {
    var p = pair(";;;l", ";;;linné");
    assertEquals(2, p.nidx());
    assertEquals("Aus bus", p.scientificName());
    assertEquals("1753", p.a().combYear());
    assertEquals(1, p.a().datasetKey());
    assertEquals("b2", p.b().nameId());
  }

  /** only one of the two datasets knows the code of the name */
  @Test
  public void codeComesFromTheGroup() {
    assertEquals(NomCode.BOTANICAL, pair(";;;l", ";;;linné").code());
    assertEquals(NomCode.ZOOLOGICAL, pair(";;;pallas", ";;;pimbus").code());
  }

  /** the long form sits in two datasets, which makes two dataset pairs per name but still one name */
  @Test
  public void supportCountsNamesNotDatasets() {
    var p = pair(";;;dc", ";;;decandolle");
    assertEquals(Label.SAME, p.label());
    assertEquals(3, p.stat().support());
    assertEquals(3, p.stat().freqA());
    assertEquals(3, p.stat().freqB());
  }

  /**
   * Two datasets that both hold "Mill." and "Hook." say nothing about which of the other's citations is which.
   * Pairing them all would turn every two prolific authors into aliases.
   */
  @Test
  public void ambiguousGroupsYieldNoAlias() {
    assertFalse(pairs.containsKey(";;;mill ;;;miller"));
    assertFalse(pairs.containsKey(";;;hook ;;;miller"));
    assertEquals(1, stats.ambiguous);
  }

  @Test
  public void keptApartByOneDatasetIsDifferent() {
    var p = pair(";;;pallas", ";;;pimbus");
    assertEquals(Label.DIFF, p.label());
    assertEquals(Source.INTRA_YEARDIFF, p.source());
    assertEquals(1, p.weight());
    assertEquals(1, p.a().datasetKey());
    assertEquals(1, p.b().datasetKey());

  }

  /** no year tells these homonyms from two records of one name */
  @Test
  public void keptApartWithoutAYearIsDubious() {
    assertEquals(Label.DUBIOUS, pair(";;;hook", ";;;mill").label());
    assertEquals(Source.INTRA_NOYEAR, pair(";;;hook", ";;;mill").source());
    assertEquals(Label.DUBIOUS, pair(";;;hooker", ";;;miller").label());
  }

  @Test
  public void sameYearInsideOneDatasetIsDubious() {
    var p = pair(";;;bryk", ";;;bryk|eisner");
    assertEquals(Label.DUBIOUS, p.label());
    assertEquals(Source.INTRA_YEARAGREE, p.source());
    assertEquals(List.of("Bryk", "Eisner"), p.b().combAuthors());
  }

  @Test
  public void punctuationAloneIsNoPair() {
    assertTrue(pairs.keySet().stream().noneMatch(k -> k.contains("gray")));
  }

  @Test
  public void namesOfTwoCodesAreHomonymsNotAliases() {
    assertTrue(pairs.keySet().stream().noneMatch(k -> k.contains("smith")));
    assertEquals(1, stats.crossCode);
  }

  @Test
  public void conflictingYearsGiveNoSupport() {
    assertTrue(pairs.keySet().stream().noneMatch(k -> k.contains("walk")));
  }

  @Test
  public void counts() {
    assertEquals(31, stats.rows);
    assertEquals(13, stats.groups);
    assertEquals(0, stats.capped);
    // l/linné, dc/decandolle, walk/walker and the basionym against the combination walker
    assertEquals(4, stats.crossPairs);
    assertEquals(2, stats.labelled.get(Label.SAME).intValue());
    assertEquals(1, stats.labelled.get(Label.DIFF).intValue());
    assertEquals(3, stats.labelled.get(Label.DUBIOUS).intValue());
    assertEquals(2, stats.labelled.get(Label.UNLABELLED).intValue());
  }

  /** a name with dozens of different citations is a mess nobody can label, and it would square into pairs */
  @Test
  public void skipsGroupsWithTooManyKeys() throws Exception {
    File export = tmp.newFile("many.tsv");
    try (Writer w = UTF8IoUtils.writerFromFile(export)) {
      w.write(String.join("\t", ExportRow.COLUMNS) + "\n");
      for (String a : List.of("Abel", "Behr", "Cole", "Dahl")) {
        w.write("1\t1\tn" + a + "\tSPECIES\t\t\tAus bus\t" + a + "\t" + a + "\t\t\t\t\t\t\n");
      }
    }
    File out = tmp.newFile("many-pairs.tsv");
    var s = new AuthorPairMiner(LabelRules.DEFAULT, 3).mine(export, out);
    assertEquals(1, s.capped);
    assertTrue(CorpusIO.readPairs(out).isEmpty());

    s = new AuthorPairMiner(LabelRules.DEFAULT, 4).mine(export, out);
    assertEquals(0, s.capped);
    assertEquals(6, CorpusIO.readPairs(out).size());
  }
}
