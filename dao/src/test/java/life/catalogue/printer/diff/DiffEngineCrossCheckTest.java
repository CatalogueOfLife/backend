package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import static org.junit.Assert.*;

public class DiffEngineCrossCheckTest {

  private static DiffInput input(String label, List<String> lines) {
    return new DiffInput(label, () -> Stream.of(lines.toArray(new String[0])));
  }

  @Test
  public void enginesAgreeOnSmallInput() {
    List<String> s1 = List.of("Abies alba", "Betula pendula", "Cedrus deodara", "Zea mays L.");
    List<String> s2 = List.of("Abies alba Mill.", "Betula pendula", "Quercus robur", "Zea mays L.");
    NamesDiff m = new StreamingMergeDiffEngine().diff(input("a", s1), input("b", s2), DiffOptions.defaults());
    NamesDiff y = new MyersDiffEngine().diff(input("a", s1), input("b", s2), DiffOptions.defaults());
    assertEquals(m.getRemoved(), y.getRemoved());
    assertEquals(m.getAdded(), y.getAdded());
    assertEquals(m.getChangedCount(), y.getChangedCount());
  }
}
