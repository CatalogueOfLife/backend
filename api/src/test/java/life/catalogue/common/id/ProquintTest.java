package life.catalogue.common.id;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProquintTest {
  
  @Test
  public void encode() {
    int[] ids = new int[]{18, 1089, 1781089, 4781089, 12781089, Integer.MAX_VALUE};
    for (int id : ids) {
      System.out.println("\n" + id);
      String pq = Proquint.encode(id);
      System.out.println(pq);
      int id2 = Proquint.decode(pq);
      System.out.println(id2);
      assertEquals(id, id2);
    }
  }
}