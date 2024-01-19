package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.model.Publisher;

import life.catalogue.api.vocab.Users;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class PublisherMapperTest extends CRUDPageableTestBase<UUID, Publisher, PublisherMapper>{

  public PublisherMapperTest() {
    super(PublisherMapper.class);
  }

  @Test
  public void list() {
  }


  @Override
  Publisher createTestEntity(int datasetKey) {
    return createTestEntityIncId(datasetKey);
  }

  @Override
  Publisher createTestEntityIncId(int datasetKey) {
    var p = new Publisher();
    p.setId(UUID.randomUUID());
    p.setDatasetKey(datasetKey);
    p.setAlias(RandomUtils.randomUnicodeString(5));
    p.setTitle(RandomUtils.randomUnicodeString(40));
    p.setDescription(RandomUtils.randomUnicodeString(250));
    p.applyUser(Users.TESTER);
    return p;
  }

  @Override
  void updateTestObj(Publisher p) {
    p.setAlias("ali");
    p.setDescription("Updated description");
  }
}