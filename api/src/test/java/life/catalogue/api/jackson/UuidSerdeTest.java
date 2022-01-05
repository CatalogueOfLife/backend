package life.catalogue.api.jackson;

import java.util.UUID;

import org.junit.Test;

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
    final String with = "e59f8f11-07ae-48e6-b71c-059487397954";
    final String without = "e59f8f1107ae48e6b71c059487397954";
    final UUID original = UUID.fromString(with);
    assertEquals(original, ApiModule.MAPPER.readValue("\""+with+"\"", UUID.class));
    assertEquals(original, ApiModule.MAPPER.readValue("\""+without+"\"", UUID.class));
    assertEquals(original, UUIDSerde.from(with));
    assertEquals(original, UUIDSerde.from(without));
  }

}
