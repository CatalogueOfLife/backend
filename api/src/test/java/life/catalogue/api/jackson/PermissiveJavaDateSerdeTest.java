package life.catalogue.api.jackson;

import org.junit.Test;

public class PermissiveJavaDateSerdeTest {

  @Test
  public void testFormatter() throws Exception {
    String gbifRaw = "2020-05-12T16:32:32.472+0001";
    Object dt = PermissiveJavaDateSerde.FORMATTER.parse(gbifRaw);
    System.out.println(dt.getClass());
    System.out.println(dt);
  }

}