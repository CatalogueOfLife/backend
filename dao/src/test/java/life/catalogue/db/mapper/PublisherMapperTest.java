package life.catalogue.db.mapper;

import life.catalogue.api.model.Page;

import life.catalogue.api.vocab.Publishers;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

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

  @Test
  public void exists() throws Exception {
    assertFalse(mapper().exists(Publishers.PLAZI));
  }
}