package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Publisher;
import life.catalogue.api.model.PublisherInfo;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.License;
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
  public void listAndDatasetGroups() throws Exception {
    var p1 = createTestEntity();
    p1.setTitle("Natural history museum");
    mapper().create(p1);
    var p2 = createTestEntity();
    p2.setTitle("Botanic garden");
    mapper().create(p2);

    var dm = mapper(DatasetMapper.class);
    for (int i = 0; i < 3; i++) {
      Dataset d = TestEntityGenerator.newDataset("museum " + i);
      TestEntityGenerator.setUser(d);
      d.setOrigin(DatasetOrigin.EXTERNAL);
      d.setLicense(License.CC0);
      d.setGbifPublisherKey(p1.getKey());
      d.setPrivat(i == 2);
      dm.create(d);
    }
    commit();

    // the private dataset is not counted for anonymous users
    var list = mapper().list(null, PublisherInfo.SortBy.RELEVANCE, null, new Page());
    assertEquals(2, mapper().count(null));
    assertEquals(2, list.size());
    assertEquals(p1.getKey(), list.get(0).getKey());
    assertEquals(2, list.get(0).getDatasets());
    assertEquals(0, list.get(1).getDatasets());

    list = mapper().list(null, PublisherInfo.SortBy.TITLE, User.ADMIN_MAGIC_KEY, new Page());
    assertEquals(p2.getKey(), list.get(0).getKey());
    assertEquals(3, list.get(1).getDatasets());

    list = mapper().list("museum", PublisherInfo.SortBy.RELEVANCE, null, new Page());
    assertEquals(1, mapper().count("museum"));
    assertEquals(1, list.size());
    assertEquals(p1.getKey(), list.get(0).getKey());

    var groups = mapper().datasetGroups(p1.getKey(), User.ADMIN_MAGIC_KEY);
    assertEquals(2, groups.size());
    assertEquals(3, groups.stream().mapToInt(g -> g.count).sum());
    assertTrue(groups.stream().allMatch(g -> g.origin == DatasetOrigin.EXTERNAL && g.license == License.CC0));
    assertEquals(1, groups.stream().filter(g -> g.privat).mapToInt(g -> g.count).sum());

    assertEquals(1, mapper().datasetGroups(p1.getKey(), null).size());
    assertTrue(mapper().datasetGroups(p2.getKey(), null).isEmpty());
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