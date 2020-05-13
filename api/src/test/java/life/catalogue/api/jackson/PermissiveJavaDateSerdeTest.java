package life.catalogue.api.jackson;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PermissiveJavaDateSerdeTest {

  @Test
  public void testFormatter() throws Exception {
    for (String gbifRaw : new String[]{"2020-05-12T16:32:32.472+0001", "2020-05-13", "2020-05-13T16:32:32", "2020-05-13T16:32"}){
      Object dt = PermissiveJavaDateSerde.FORMATTER.parse(gbifRaw);
      assertNotNull(dt);
      System.out.println(gbifRaw + "  -->  "+ dt);
    }
  }

}