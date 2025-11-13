package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.model.SectorPublisher;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.text.StringUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SectorSectorPublisherMapperTest extends CRUDPageableTestBase<UUID, SectorPublisher, SectorPublisherMapper>{
  String KEY = StringUtils.removeLast(UUID.randomUUID().toString(),3);
  AtomicInteger KEY_INC = new AtomicInteger(100);

  public SectorSectorPublisherMapperTest() {
    super(SectorPublisherMapper.class);
  }

  @Test
  public void list() {
    mapper().create(createTestEntity(datasetKey));
    mapper().create(createTestEntity(datasetKey));
    mapper().create(createTestEntity(datasetKey));
    var keys = mapper().listAllKeys(datasetKey);
    assertEquals(3, keys.size());

    keys = mapper().listAllKeys(345654);
    assertEquals(0, keys.size());
  }


  @Override
  SectorPublisher createTestEntity(int datasetKey) {
    return createTestEntityIncId(datasetKey);
  }

  @Override
  SectorPublisher createTestEntityIncId(int datasetKey) {
    var p = new SectorPublisher();
    p.setId(UUID.fromString(KEY+KEY_INC.getAndIncrement()));
    p.setDatasetKey(datasetKey);
    p.setAlias(RandomUtils.randomUnicodeString(5));
    p.setTitle(RandomUtils.randomUnicodeString(40));
    p.setDescription(RandomUtils.randomUnicodeString(250));
    p.applyUser(Users.TESTER);
    return p;
  }

  @Override
  void updateTestObj(SectorPublisher p) {
    p.setAlias("ali");
    p.setDescription("Updated description");
  }
}