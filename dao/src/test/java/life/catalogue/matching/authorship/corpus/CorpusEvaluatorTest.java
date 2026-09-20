package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;
import life.catalogue.matching.authorship.corpus.LabelRules.Source;

import org.gbif.nameparser.api.NomCode;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CorpusEvaluatorTest {
  static final String KNOWN_MISJUDGEMENTS = "author-corpus/known-misjudgements-pairs.tsv";

  private final CorpusEvaluator evaluator = new CorpusEvaluator(new AuthorComparator(AuthorshipNormalizer.INSTANCE));

  private static AuthorPair pair(Label label, NomCode code, int weight, String authorA, String yearA, String authorB, String yearB) {
    var a = new AuthorPair.Side(1, "a", authorA, List.of(authorA), List.of(), yearA, List.of(), List.of(), null, null);
    var b = new AuthorPair.Side(2, "b", authorB, List.of(authorB), List.of(), yearB, List.of(), List.of(), null, null);
    return new AuthorPair(label, label == Label.SAME ? Source.CROSS : Source.INTRA_YEARDIFF, weight,
      new PairStat(weight, 0, 0, 9, 9, 0, 0, 0),
      code, "SPECIES", 1, "Aus bus", "a", "b", a, b);
  }

  /**
   * A characterisation, not a statement of what is right: five pairs of different people the comparator takes for
   * one and three citations of one act, two of which it keeps apart. Whoever fixes one of them moves the numbers
   * in here, which is the point. "Sw." / "Swainson" was the third until the code of the names reached the author map.
   */
  @Test
  public void knownMisjudgements() throws Exception {
    var result = evaluator.evaluate(CorpusIO.readPairs(Resources.toFile(KNOWN_MISJUDGEMENTS)));
    var all = result.matrix(CorpusEvaluator.ALL, true);
    assertEquals(5, all.count(Label.DIFF, Equality.EQUAL));
    assertEquals(0, all.count(Label.DIFF, Equality.DIFFERENT));
    assertEquals(2, all.count(Label.SAME, Equality.DIFFERENT));
    assertEquals(1, all.count(Label.SAME, Equality.EQUAL));
  }

  @Test
  public void splitsByCode() throws Exception {
    var result = evaluator.evaluate(CorpusIO.readPairs(Resources.toFile(KNOWN_MISJUDGEMENTS)));
    assertEquals(2, result.matrix("ZOOLOGICAL", true).count(Label.DIFF, Equality.EQUAL));
    assertEquals(0, result.matrix("ZOOLOGICAL", true).count(Label.SAME, Equality.DIFFERENT));
    assertEquals(1, result.matrix("ZOOLOGICAL", true).count(Label.SAME, Equality.EQUAL));
    assertEquals(2, result.matrix("BOTANICAL", true).count(Label.SAME, Equality.DIFFERENT));
    assertEquals(2, result.matrix(CorpusEvaluator.OTHER, true).count(Label.DIFF, Equality.EQUAL));
  }

  /** the label speaks about the authors, so the years are taken out to see what the author logic alone says */
  @Test
  public void verdictWithoutYears() {
    var v = evaluator.evaluate(pair(Label.SAME, null, 1, "Bruand", "1850", "Bruand", "1900"));
    assertEquals(Equality.DIFFERENT, v.verdict());
    assertEquals(Equality.EQUAL, v.verdictNoYear());
  }

  @Test
  public void weightsBySupport() {
    var result = evaluator.evaluate(List.of(
      pair(Label.SAME, NomCode.BOTANICAL, 40, "Mill.", null, "Miller", null),
      pair(Label.SAME, NomCode.BOTANICAL, 2, "Lindl.", null, "Lindley", null),
      pair(Label.DIFF, NomCode.BOTANICAL, 7, "Lindl.", null, "Bluff", null)
    ));
    var m = result.matrix(CorpusEvaluator.ALL, false);
    assertEquals(2, m.count(Label.SAME, Equality.EQUAL));
    assertEquals(42, m.weight(Label.SAME, Equality.EQUAL));
    assertEquals(7, m.weight(Label.DIFF, Equality.DIFFERENT));
  }

  /** a dubious pair is most likely a duplicate record and must not count for or against the comparator */
  @Test
  public void dubiousPairsAreNotMeasured() {
    var result = evaluator.evaluate(List.of(pair(Label.DUBIOUS, null, 3, "Bryk", "1948", "Bryk", "1948")));
    assertEquals(0, result.matrix(CorpusEvaluator.ALL, true).total());
    assertEquals(1, result.verdicts().size());
  }
}
