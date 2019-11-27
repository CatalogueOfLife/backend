 package life.catalogue;

import life.catalogue.common.util.HumanSize;
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
  
}