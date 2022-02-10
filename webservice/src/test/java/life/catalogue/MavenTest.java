 package life.catalogue;

 import life.catalogue.common.id.IdConverter;
 import life.catalogue.common.util.HumanSize;

 import org.junit.Ignore;
 import org.junit.Test;

/**
 * Report JVM settings for debugging
 */
public class MavenTest {
  
  @Test
  public void testMavenHeapSetting() throws Exception {
    System.out.println("Total JVM memory: " + HumanSize.bytes(Runtime.getRuntime().totalMemory()));
    System.out.println("Max JVM memory: " + HumanSize.bytes(Runtime.getRuntime().maxMemory()));
  }

  @Test
  @Ignore("To list encoded ids to prepare test data manually")
  public void encode() throws Exception {
    int y;
    for (int x = 1; x < 100; x++) {
      if (x>8 && x<10) continue;
      if (x>8) {
        y=x-2;
      } else {
        y=x-1;
      }
      System.out.println(x + "  ->  " + IdConverter.LATIN29.encode(y));
    }
  }
}