package life.catalogue.common.collection;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class CollectionUtilsTest {

  @Test
  public void range() {
    var r = CollectionUtils.range(1,2, 1);
    assertEquals(List.of(1,2), r);

    r = CollectionUtils.range(1,4, 2);
    assertEquals(List.of(1,3), r);

    r = CollectionUtils.range(1,5, 2);
    assertEquals(List.of(1,3,5), r);
  }

  @Test
  public void containsAny() {
    assertTrue(CollectionUtils.overlaps(List.of(1,2), List.of(2,3)));
    assertTrue(CollectionUtils.overlaps(List.of(1), List.of(-1,0,1,2,3)));
    assertFalse(CollectionUtils.overlaps(List.of(1), List.of(-1,0,2,3)));

    assertTrue(CollectionUtils.overlaps(List.of(-1,0,1,2,3), List.of(1)));
    assertFalse(CollectionUtils.overlaps(List.of(-1,0,2,3), List.of(1)));
  }

  @Test
  public void testEquals() {
    List<String> o1 = new ArrayList<>();
    String[] o2 = new String[5];

    for (int i=0; i<5; i++){
      o1.add("mine "+i);
      o2[i]="mine "+i;
    }
    assertTrue(CollectionUtils.equals(o1, o2));

    o2[4]="mine ";
    assertFalse(CollectionUtils.equals(o1, o2));

    o2[4]="mine "+4;
    assertTrue(CollectionUtils.equals(o1, o2));

    o1.remove(4);
    assertFalse(CollectionUtils.equals(o1, o2));

    o2[4] = null;
    assertFalse(CollectionUtils.equals(o1, o2));

    o2 = CollectionUtils.compact(o2);
    assertTrue(CollectionUtils.equals(o1, o2));
    assertEquals(4, o2.length);
  }
}