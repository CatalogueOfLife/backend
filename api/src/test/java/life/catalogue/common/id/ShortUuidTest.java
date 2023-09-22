package life.catalogue.common.id;

import java.util.UUID;

import org.junit.Test;

public class ShortUuidTest {

  @Test
  public void issueExamples() {
    for (int x = 0; x < 10; x++) {
      UUID uuid = UUID.randomUUID();
      System.out.println(uuid);
      System.out.println(ShortUUID.build(uuid));
    }
  }
}