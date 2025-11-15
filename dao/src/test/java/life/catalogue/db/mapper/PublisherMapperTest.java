package life.catalogue.db.mapper;

import life.catalogue.api.model.Page;

import life.catalogue.api.model.Publisher;
import life.catalogue.api.vocab.Publishers;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class PublisherMapperTest  extends CRUDTestBase<UUID, Publisher, PublisherMapper> {

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

  @Override
  Publisher createTestEntity() {
    var p = new Publisher();
    p.setKey(UUID.randomUUID());
    p.setTitle("Publisher title");
    p.setDescription("Publisher description");
    p.setCountry("Germany");
    p.setProvince("Berlin");
    p.setCity("Berlin");
    p.setHomepage("http://www.plazi.org");
    p.setLatitude(52.51703);
    p.setLongitude(13.40495);
    return p;
  }

  @Override
  void updateTestObj(Publisher p) {
    p.setTitle("Updated publisher title");
    p.setDescription("Updated description");
    p.setLatitude(52.51);
    p.setLongitude(13.40);
  }
}