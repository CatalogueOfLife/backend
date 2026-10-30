package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.SectorPublisher;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DatasetImportDao;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import static life.catalogue.api.vocab.InfoGroup.*;
import static org.junit.Assert.*;

public class DatasetSourceContributionMapperTest extends MapperTestBase<DatasetSourceContributionMapper> {
  private int vsKey = 1000;

  public DatasetSourceContributionMapperTest() {
    super(DatasetSourceContributionMapper.class);
  }

  private Dataset createDataset(DatasetOrigin origin) {
    Dataset d = DatasetMapperTest.create();
    d.setOrigin(origin);
    d.setGbifPublisherKey(null);
    mapper(DatasetMapper.class).create(d);
    return d;
  }

  /**
   * Credits a new record of the release with several groups from one source.
   */
  private VerbatimSource credit(int releaseKey, int sourceKey, InfoGroup... groups) {
    var vsm = mapper(VerbatimSourceMapper.class);
    VerbatimSource vs = new VerbatimSource(releaseKey, vsKey++, null, null, null, null);
    vsm.create(vs);
    vsm.insertSources(vs, DSID.of(sourceKey, "s" + vs.getId()), Set.of(groups));
    return vs;
  }

  @Test
  public void createForRelease() {
    int release = createDataset(DatasetOrigin.RELEASE).getKey();
    int bhl = createDataset(DatasetOrigin.EXTERNAL).getKey();
    int bionames = createDataset(DatasetOrigin.EXTERNAL).getKey();
    int nothing = createDataset(DatasetOrigin.EXTERNAL).getKey();

    credit(release, bhl, PUBLISHED_IN_PAGE, PUBLISHED_IN_PAGE_LINK);
    credit(release, bhl, PUBLISHED_IN_PAGE_LINK);
    credit(release, bionames, PUBLISHED_IN);
    // one record with credits of both sources
    var vs = credit(release, bhl, PUBLISHED_IN_PAGE_LINK);
    mapper(VerbatimSourceMapper.class).insertSources(vs, DSID.of(bionames, "x"), Set.of(PUBLISHED_IN, ETYMOLOGY));
    // another release is none of our business
    credit(createDataset(DatasetOrigin.RELEASE).getKey(), bhl, PUBLISHED_IN);
    commit();

    assertEquals(2, mapper().createForRelease(release));
    commit();

    var c = mapper().get(release, bhl);
    assertEquals(3, c.getEnriched());
    assertEquals(Map.of(PUBLISHED_IN_PAGE, 1, PUBLISHED_IN_PAGE_LINK, 3), c.getByInfo());

    c = mapper().get(release, bionames);
    assertEquals(2, c.getEnriched());
    assertEquals(Map.of(PUBLISHED_IN, 2, ETYMOLOGY, 1), c.getByInfo());

    assertNull(mapper().get(release, nothing));

    // what the sources gave adds up to what the release received
    Map<InfoGroup, Integer> sum = new EnumMap<>(InfoGroup.class);
    for (int src : new int[]{bhl, bionames}) {
      mapper().get(release, src).getByInfo().forEach((k, v) -> sum.merge(k, v, Integer::sum));
    }
    var received = DatasetImportDao.countMap(InfoGroup.class, mapper(DatasetImportMapper.class).countSecondarySourceByInfo(release));
    assertEquals(received, new HashMap<>(sum));

    assertEquals(2, mapper().deleteByRelease(release));
    assertNull(mapper().get(release, bhl));
  }

  @Test
  public void aggregateByPublisher() {
    int release = createDataset(DatasetOrigin.RELEASE).getKey();
    UUID publisher = UUID.randomUUID();
    int[] articles = new int[2];
    for (int i = 0; i < 2; i++) {
      Dataset d = DatasetMapperTest.create();
      d.setOrigin(DatasetOrigin.EXTERNAL);
      d.setGbifPublisherKey(publisher);
      mapper(DatasetMapper.class).create(d);
      articles[i] = d.getKey();
    }
    var sp = new SectorPublisher();
    sp.setId(publisher);
    sp.setDatasetKey(release);
    sp.setAlias("Plazi");
    sp.setTitle("Plazi");
    sp.applyUser(Users.TESTER);
    mapper(SectorPublisherMapper.class).create(sp);

    credit(release, articles[0], PUBLISHED_IN, PUBLISHED_IN_PAGE);
    credit(release, articles[1], PUBLISHED_IN);
    credit(release, articles[1], ETYMOLOGY);
    // someone else
    credit(release, createDataset(DatasetOrigin.EXTERNAL).getKey(), PUBLISHED_IN);
    mapper().createForRelease(release);
    commit();

    assertEquals(3, mapper().sumEnrichedByPublisher(release, publisher));
    assertEquals(Map.of(PUBLISHED_IN, 2, PUBLISHED_IN_PAGE, 1, ETYMOLOGY, 1),
      DatasetImportDao.countMap(InfoGroup.class, mapper().countByInfoByPublisher(release, publisher)));

    UUID nobody = UUID.randomUUID();
    assertEquals(0, mapper().sumEnrichedByPublisher(release, nobody));
    assertTrue(mapper().countByInfoByPublisher(release, nobody).isEmpty());
  }
}
