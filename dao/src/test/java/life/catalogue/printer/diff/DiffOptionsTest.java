package life.catalogue.printer.diff;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class DiffOptionsTest {

  @Test
  public void defaults() {
    DiffOptions o = DiffOptions.defaults();
    assertEquals(1, o.getCanonicalMaxDistance());
    assertEquals(0, o.getMaxItems());
    assertSame(DiffOptions.CODEPOINT, o.getOrder());
  }

  @Test
  public void codepointOrderMatchesByteOrder() {
    // C collation / byte order: uppercase before lowercase; space (0x20) before letters
    List<String> in = new ArrayList<>(List.of("Zea", "aus", "Aus bus", "Aus", "Aus aus"));
    in.sort(DiffOptions.CODEPOINT);
    assertEquals(List.of("Aus", "Aus aus", "Aus bus", "Zea", "aus"), in);
  }

  @Test
  public void codepointHandlesNonAscii() {
    // 'Z' (0x5A) < 'É' (U+00C9); ASCII sorts before Latin-1 supplement
    assertTrue(DiffOptions.CODEPOINT.compare("Zea", "Ébre") < 0);
  }
}
