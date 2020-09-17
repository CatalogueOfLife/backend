package life.catalogue.api.jackson;

import java.util.UUID;

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

}
