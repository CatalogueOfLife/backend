package life.catalogue.matching.authorship.corpus;

import life.catalogue.matching.authorship.corpus.AuthorVerdictDiff.VerdictRow;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AuthorVerdictDiffTest {

  private static VerdictRow row(String label, String keyA, int weight, String verdict) {
    return new VerdictRow("BOTANICAL", keyA, ";;;z", label, "CROSS", weight, verdict, verdict, keyA, "Z.");
  }

  @Test
  public void identicalRunsDoNotDiffer() {
    var rows = List.of(row("SAME", ";;;a", 5, "EQUAL"), row("DIFF", ";;;b", 2, "EQUAL"));
    var diff = AuthorVerdictDiff.diff(rows, rows);
    assertTrue(diff.flips().isEmpty());
    assertTrue(AuthorVerdictDiff.render(diff).contains("No verdict changed"));
  }

  @Test
  public void classifiesFlips() {
    var before = List.of(
      row("SAME", ";;;a", 5, "DIFFERENT"),
      row("SAME", ";;;b", 9, "EQUAL"),
      row("DIFF", ";;;c", 2, "EQUAL"),
      row("DIFF", ";;;d", 3, "DIFFERENT"),
      row("SAME", ";;;e", 1, "DIFFERENT")
    );
    var after = List.of(
      row("SAME", ";;;a", 5, "EQUAL"),
      row("SAME", ";;;b", 9, "UNKNOWN"),
      row("DIFF", ";;;c", 2, "DIFFERENT"),
      row("DIFF", ";;;d", 3, "EQUAL"),
      row("SAME", ";;;e", 1, "UNKNOWN")
    );
    var diff = AuthorVerdictDiff.diff(before, after);
    assertEquals(5, diff.flips().size());
    assertEquals(2, diff.fixed());
    assertEquals(2, diff.regressed());

    String text = AuthorVerdictDiff.render(diff);
    assertTrue(text, text.contains("SAME: DIFFERENT -> EQUAL (fixed)"));
    assertTrue(text, text.contains("SAME: EQUAL -> UNKNOWN (regressed)"));
    assertTrue(text, text.contains("DIFF: EQUAL -> DIFFERENT (fixed)"));
    assertTrue(text, text.contains("DIFF: DIFFERENT -> EQUAL (regressed)"));
    // neither right before nor after
    assertTrue(text, text.contains("SAME: DIFFERENT -> UNKNOWN (changed)"));
  }

  @Test
  public void reportsPairsOfOneRunOnly() {
    var diff = AuthorVerdictDiff.diff(List.of(row("SAME", ";;;a", 5, "EQUAL")), List.of(row("SAME", ";;;b", 5, "EQUAL")));
    assertEquals(1, diff.onlyBefore());
    assertEquals(1, diff.onlyAfter());
  }
}
