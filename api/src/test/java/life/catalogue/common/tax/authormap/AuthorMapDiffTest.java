package life.catalogue.common.tax.authormap;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthorMapDiffTest {
  @Test
  public void reportsRemovedAuthorsAndAliases() {
    List<AuthorEntry> oldE = List.of(
      new AuthorEntry("C Linnaeus", AuthorCode.BOT, List.of("L.", "Carl Linnaeus")),
      new AuthorEntry("Old Author", AuthorCode.BOT, List.of("Oldauth.")));
    List<AuthorEntry> newE = List.of(
      new AuthorEntry("C Linnaeus", AuthorCode.BOT, List.of("L.", "Carl Linnaeus")));

    AuthorMapDiff.Result r = AuthorMapDiff.diff(oldE, newE);
    assertTrue(r.removedCanonicals().contains("Old Author"));
    assertTrue(r.removedAliasKeys().contains("oldauth"));      // normalize("Oldauth.")
    assertFalse(r.removedAliasKeys().contains("l"));           // still present
    assertTrue(AuthorMapDiff.render(r).contains("Old Author"));
  }
}
