package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.stream.Stream;

import org.junit.Test;

import static org.junit.Assert.*;

public class MyersDiffEngineTest {

  private final MyersDiffEngine engine = new MyersDiffEngine();

  private static DiffInput input(String label, String... lines) {
    return new DiffInput(label, () -> Stream.of(lines));
  }

  @Test
  public void addRemoveChange() {
    DiffInput a = input("a", "Abies alba", "Quercus robur", "Zea mays");
    DiffInput b = input("b", "Abies alba Mill.", "Quercus robur", "Zea mays");
    NamesDiff d = engine.diff(a, b, DiffOptions.defaults());
    assertEquals(1, d.getChangedCount());
    assertEquals(0, d.getRemovedCount());
    assertEquals(0, d.getAddedCount());
  }

  @Test
  public void sizeGuard() {
    MyersDiffEngine small = new MyersDiffEngine(2);
    DiffInput a = input("a", "A", "B", "C");
    DiffInput b = input("b", "A");
    try {
      small.diff(a, b, DiffOptions.defaults());
      fail("expected size guard");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("2"));
    }
  }
}
