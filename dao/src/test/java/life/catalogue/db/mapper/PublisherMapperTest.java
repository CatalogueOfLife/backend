package life.catalogue.db.mapper;

import life.catalogue.api.model.Page;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PublisherMapperTest  extends MapperTestBase<PublisherMapper> {

  public PublisherMapperTest() {
    super(PublisherMapper.class);
  }

  @Test
  public void getNull() throws Exception {
    assertNull(mapper().get(UUID.randomUUID()));
  }

  @Test
  public void search() throws Exception {
    assertTrue(mapper().search("aqwsdfgh", new Page(0, 10)).isEmpty());
  }
}