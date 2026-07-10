package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import static org.junit.Assert.*;

public class StreamingMergeDiffEngineTest {

  private final StreamingMergeDiffEngine engine = new StreamingMergeDiffEngine();

  private static DiffInput input(String label, String... lines) {
    return new DiffInput(label, () -> Stream.of(lines));
  }

  @Test
  public void addRemoveChange() {
    DiffInput a = input("a", "Abies alba", "Quercus robur", "Zea mays");
    DiffInput b = input("b", "Abies alba Mill.", "Quercus robur", "Zea mays");
    NamesDiff d = engine.diff(a, b, DiffOptions.defaults());
    assertEquals(1, d.getChangedCount());
    assertEquals("Abies alba Mill.", d.getChanged().get(0).after());
    assertEquals(0, d.getRemovedCount());
    assertEquals(0, d.getAddedCount());
  }

  @Test
  public void pureAddAndRemove() {
    DiffInput a = input("a", "Aus aus", "Cus cus");
    DiffInput b = input("b", "Bus bus", "Cus cus");
    NamesDiff d = engine.diff(a, b, DiffOptions.defaults());
    assertEquals(List.of("Aus aus"), d.getRemoved());
    assertEquals(List.of("Bus bus"), d.getAdded());
    assertEquals(0, d.getChangedCount());
  }

  @Test
  public void identical() {
    DiffInput a = input("a", "Aus aus", "Bus bus");
    DiffInput b = input("b", "Aus aus", "Bus bus");
    assertTrue(engine.diff(a, b, DiffOptions.defaults()).isIdentical());
  }

  @Test
  public void localInversionIsHealed() {
    // side1 has a local inversion vs code-point order; the merge produces a spurious remove+add of
    // the same string, healed by pass 2.
    DiffInput a = input("a", "Aus", "Aus zz", "Aus b a");
    DiffInput b = input("b", "Aus", "Aus b a", "Aus zz");
    NamesDiff d = engine.diff(a, b, DiffOptions.defaults());
    assertTrue("expected identical after healing but was " + d, d.isIdentical());
  }

  @Test
  public void candidateCapTruncates() {
    DiffInput a = input("a", "A", "B", "C", "D");
    DiffInput b = input("b", "E", "F", "G", "H");
    DiffOptions opts = DiffOptions.defaults().setMaxChangedCandidates(3);
    NamesDiff d = engine.diff(a, b, opts);
    assertTrue(d.isTruncated());
  }
}
