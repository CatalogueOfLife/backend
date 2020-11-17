package life.catalogue.api.jackson;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class UuidSerdeTest extends SerdeTestBase<UUID> {

  public UuidSerdeTest() {
    super(UUID.class);
  }
  
  @Override
  public UUID genTestValue() throws Exception {
    return UUID.randomUUID();
  }

  @Test
  public void testUUIDNoHyphen() throws Exception {
    UUID parsed = ApiModule.MAPPER.readValue("\"e59f8f1107ae48e6b71c059487397954\"", UUID.class);
    System.out.println(parsed);
    assertEquals(UUID.fromString("e59f8f11-07ae-48e6-b71c-059487397954"), parsed);
  }

}
