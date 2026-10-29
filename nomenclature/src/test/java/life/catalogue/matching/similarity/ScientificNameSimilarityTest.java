package life.catalogue.matching.similarity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScientificNameSimilarityTest {

  @Test
  public void testSimilarities() throws Exception {
    ScientificNameSimilarity sns = new ScientificNameSimilarity();

    // The × in these ensures they don't match the shortcut .equals test.
    assertEquals(100d, sns.getSimilarity("A", "×A"), 0.01d);
    assertEquals(100d, sns.getSimilarity("Aa", "×Aa"), 0.01d);
    assertEquals(100d, sns.getSimilarity("Io", "×Io"), 0.01d);
    assertEquals(100d, sns.getSimilarity("Aus", "×Aus"), 0.01d);
    assertEquals(100d, sns.getSimilarity("Ausbus", "×Ausbus"), 0.01d);
    assertEquals(0d, sns.getSimilarity("Aus", "×Ausausaus"), 0.01d);
    assertEquals(0d, sns.getSimilarity("Ausausaus", "×Aus"), 0.01d);

    assertEquals(100d, sns.getSimilarity("abcdefg", "abcdefg"), 0.01d);
    assertEquals(80d, sns.getSimilarity("abcdefg", "amcdefg"), 0.01d);
    assertEquals(80d, sns.getSimilarity("abcdefg", "abcdeg"), 0.01d);

    assertEquals(0d, sns.getSimilarity("abcdefg", "zyxvwu"), 0.01d);
    assertEquals(0d, sns.getSimilarity("abcdefg", "amncdefg"), 0.01d);
    assertEquals(0d, sns.getSimilarity("abcdefg", "adefg"), 0.01d);
    assertEquals(0d, sns.getSimilarity("abcdefg", "aabbccddeeffgg"), 0.01d);
    assertEquals(100d, sns.getSimilarity("Aka abcdefg", "Aka aabbccddeeffgg"), 0.01d);
    assertEquals(100d, sns.getSimilarity("äöüæøåœđł", "aouaeoaoedl"), 0.01d);

    assertEquals(100d, sns.getSimilarity("xAus", "Aus"), 0.01d);
    assertEquals(100d, sns.getSimilarity("x Aus", "Aus"), 0.01d);
    assertEquals(100d, sns.getSimilarity("×Aus", "Aus"), 0.01d);
    assertEquals(100d, sns.getSimilarity("× Aus", "Aus"), 0.01d);

    assertEquals(100d, sns.getSimilarity("Abies alba", "Abies alba"), 0.01d);
    assertEquals(100d, sns.getSimilarity("Abies alba", "Abies albus"), 0.01d);
    assertEquals(5d, sns.getSimilarity("Abies alba", "Abies olba"), 0.01d);
    assertEquals(5d, sns.getSimilarity("Abies alba", "Abies alta"), 0.01d);

    assertEquals(100d, sns.getSimilarity("Abies ama", "Abies amus"), 0.01d);
    assertEquals(100d, sns.getSimilarity("Abies ama", "Abies amus"), 0.01d);
    assertEquals(100d, sns.getSimilarity("Abies ama", "Abies amum"), 0.01d);

    assertEquals(95d, sns.getSimilarity("Linaria pedunculata", "Linaria pedinculata"), 0.01d);
    assertEquals(90d, sns.getSimilarity("Linaria pedunculata", "Lunaria pedunculata"), 0.01d);
    assertEquals(85d, sns.getSimilarity("Linaria pedunculata", "Linariya pedonculata"), 0.01d);
    assertEquals(93.33d, sns.getSimilarity("Linaria pedunculata vulgaris", "Lunaria pedunculata vulgaris"), 0.01d);
    assertEquals(5d, sns.getSimilarity("Linaria pedunculata vulgaris", "Linaria pedunculata vandalis"), 0.01d);
    assertEquals(5d, sns.getSimilarity("Oreina elegans", "Orfelia elegans"), 0.01d);
    assertEquals(5d, sns.getSimilarity("Lucina scotti", "Lucina wattsi"), 0.01d);
    assertEquals(0d, sns.getSimilarity("scotti", "wattsi"), 0.01d);
  }

  /**
   * A single character typo has a whole word edit distance of 1 wherever it falls, but it shifts
   * every following character along. The first MUST_MATCH characters of the epithet can therefore
   * end up differing by 1 or by 2 edits purely depending on the position of the typo, which used to
   * decide whether the epithet was scored at all.
   *
   * See https://github.com/gbif/matching-ws/issues/13
   */
  @Test
  public void testSingleCharacterEpithetTypos() throws Exception {
    ScientificNameSimilarity sns = new ScientificNameSimilarity();

    // "disc" vs "dico" and "conf" vs "cofu" both differ by 2 edits, the whole epithets by 1
    assertEquals(95d, sns.getSimilarity("Cissus discolor", "Cissus dicolor"), 0.01d);
    assertEquals(95d, sns.getSimilarity("Saintpaulia confusa", "Saintpaulia cofusa"), 0.01d);
    // a typo in both genus and epithet, the genus one still within the genus tolerance
    assertEquals(85d, sns.getSimilarity("Cissus discolor", "Cisus dicolor"), 0.01d);

    // the genus keeps the strict prefix rule: "Sain" vs "Sant" differs by 2 edits and is rejected,
    // as a genus differing in its first characters is usually a genuinely different genus
    assertEquals(5d, sns.getSimilarity("Saintpaulia confusa", "Santpaulia confusa"), 0.01d);
    assertEquals(5d, sns.getSimilarity("Saintpaulia confusa", "Santpaulia cofusa"), 0.01d);

    // epithets differing by more than a single character stay apart even though their first
    // characters are close: these are all distinct, accepted species in COL
    assertEquals(5d, sns.getSimilarity("Abacetus major", "Abacetus macer"), 0.01d);
    assertEquals(5d, sns.getSimilarity("Abacetus ornatus", "Abacetus optatus"), 0.01d);
    assertEquals(5d, sns.getSimilarity("Quercus robur", "Quercus rubor"), 0.01d);
    // and a differing first character still rejects outright
    assertEquals(5d, sns.getSimilarity("Cissus discolor", "Cissus bicolor"), 0.01d);

    // accepted cost: distinct species one edit apart do become similar. Homotypic consolidation
    // additionally requires identical authorship, which keeps these two apart there.
    assertEquals(95d, sns.getSimilarity("Agave aurea", "Agave azurea"), 0.01d);
  }
}