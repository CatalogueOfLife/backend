package life.catalogue.common.collection;

import org.junit.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

public class IterUtilsTest {

  static class XY {
    int x;
    int y;
    public XY(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  @Test
  public void group() {
    for (List<XY> g : IterUtils.group(List.of(
      new XY(1, 2), new XY(2, 2),
      new XY(3, 10), new XY(4, 10),
      new XY(5, 12), new XY(6, 12)
    ), new Comparator<XY>() {
      @Override
      public int compare(XY o1, XY o2) {
        return Integer.compare(o1.y, o2.y);
      }
    })) {
      assertEquals(2, g.size());
      assertEquals(g.get(0).y, g.get(1).y);
    }
  }
}