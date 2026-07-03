package life.catalogue.common.tax.authormap;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthorMapIOTest {
  @Test
  public void roundTrip() throws Exception {
    Path f = Files.createTempFile("authormap", ".txt");
    List<AuthorEntry> in = List.of(
      new AuthorEntry("C Linnaeus", AuthorCode.BOT, List.of("L.", "Carl Linnaeus")),
      new AuthorEntry("G Cuvier", AuthorCode.ZOO, List.of("Georges Cuvier")));
    AuthorMapIO.write(f, in);
    List<AuthorEntry> out = AuthorMapIO.read(f);
    assertEquals(in.size(), out.size());
    assertEquals("C Linnaeus", out.get(0).canonical());
    assertEquals(AuthorCode.BOT, out.get(0).code());
    assertEquals(List.of("L.", "Carl Linnaeus"), out.get(0).aliases());
  }
}
