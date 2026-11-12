package life.catalogue.dao;

import life.catalogue.TestUtils;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.GbifRegistration;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.GbifConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.GbifRegistrationMapper;
import life.catalogue.db.mapper.OutboxMapper;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.outbox.Outbox;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Datasets ChecklistBank registers in GBIF itself: their registration, the guards on dataset updates and the
 * GBIF_REGISTRY outbox commands that keep the GBIF registry in step.
 */
public class DatasetDaoGbifTest extends DaoTestBase {
  // the external dataset of the apple test data
  static final int KEY = 12;
  static final int USER = Users.TESTER;
  DatasetDao dao;

  @Before
  public void init() {
    var diDao = new DatasetImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    dao = new DatasetDao(factory(), new NormalizerConfig(), new ReleaseConfig(), new GbifConfig(), new DoiConfig(),
      null, ImageService.passThru(), diDao, null, NameUsageIndexService.passThru(), null, TestUtils.mockedBroker(), validator
    );
    Dataset d = dao.get(KEY);
    d.setPrivat(false);
    d.setLicense(License.CC_BY);
    dao.update(d, USER);
  }

  @Test
  public void lifecycle() throws Exception {
    final UUID gbifKey = UUID.randomUUID();
    final UUID org = UUID.randomUUID();
    assertNull(command());

    // register
    dao.updateGbif(KEY, new GbifRegistration(KEY, gbifKey, USER), gbifKey, org, USER);
    assertTrue(registered());
    Dataset d = dao.get(KEY);
    assertEquals(gbifKey, d.getGbifKey());
    assertEquals(org, d.getGbifPublisherKey());
    assertNotNull(command());
    assertTrue(Outbox.gbifDeregisterKeys(command().getParams()).isEmpty());
    // the first command uploads the EML
    assertTrue(Outbox.gbifPushMetadata(command().getParams()));
    deleteCommand();

    // a metadata edit is passed on to GBIF and never touches the GBIF keys
    d = dao.get(KEY);
    d.setTitle("A registered dataset");
    d.setGbifKey(null);
    d.setGbifPublisherKey(UUID.randomUUID());
    dao.update(d, USER);
    d = dao.get(KEY);
    assertEquals(gbifKey, d.getGbifKey());
    assertEquals(org, d.getGbifPublisherKey());
    assertNotNull(command());
    // GBIF reads the edit from the EML endpoint
    assertFalse(Outbox.gbifPushMetadata(command().getParams()));
    deleteCommand();

    // it has to stay public and openly licensed
    final Dataset priv = dao.get(KEY);
    priv.setPrivat(true);
    assertThrows(IllegalArgumentException.class, () -> dao.update(priv, USER));
    final Dataset other = dao.get(KEY);
    other.setLicense(License.OTHER);
    assertThrows(IllegalArgumentException.class, () -> dao.update(other, USER));
    assertNull(command());

    // another organisation
    final UUID org2 = UUID.randomUUID();
    dao.updateGbif(KEY, null, gbifKey, org2, USER);
    assertEquals(org2, dao.get(KEY).getGbifPublisherKey());
    assertTrue(registered());
    assertNotNull(command());
    deleteCommand();

    // deregister
    dao.updateGbif(KEY, null, null, null, USER);
    assertFalse(registered());
    d = dao.get(KEY);
    assertNull(d.getGbifKey());
    assertNull(d.getGbifPublisherKey());
    assertEquals(Set.of(gbifKey), Outbox.gbifDeregisterKeys(command().getParams()));
    deleteCommand();

    // an unregistered dataset can be made private again and passes nothing on
    d.setPrivat(true);
    dao.update(d, USER);
    assertNull(command());
  }

  /**
   * Deregistering twice before the command ran must keep both GBIF keys to delete, they merge into one command.
   */
  @Test
  public void deregisterTwice() throws Exception {
    final UUID key1 = UUID.randomUUID();
    final UUID key2 = UUID.randomUUID();
    final UUID org = UUID.randomUUID();
    dao.updateGbif(KEY, new GbifRegistration(KEY, key1, USER), key1, org, USER);
    dao.updateGbif(KEY, null, null, null, USER);
    dao.updateGbif(KEY, new GbifRegistration(KEY, key2, USER), key2, org, USER);
    dao.updateGbif(KEY, null, null, null, USER);
    assertEquals(Set.of(key1, key2), Outbox.gbifDeregisterKeys(command().getParams()));
    // deregistering a dataset that is not registered is refused and changes nothing
    assertThrows(IllegalArgumentException.class, () -> dao.updateGbif(KEY, null, null, null, USER));
  }

  /**
   * Deleting a registered dataset deletes it in GBIF too: the registration keeps the GBIF key the deletion nulls.
   */
  @Test
  public void delete() throws Exception {
    final UUID gbifKey = UUID.randomUUID();
    dao.updateGbif(KEY, new GbifRegistration(KEY, gbifKey, USER), gbifKey, UUID.randomUUID(), USER);
    deleteCommand();

    dao.delete(KEY, USER);
    assertTrue(registered());
    assertNotNull(command());
  }

  private boolean registered() {
    try (var session = factory().openSession()) {
      return session.getMapper(GbifRegistrationMapper.class).exists(KEY);
    }
  }

  @Nullable
  private OutboxEntry command() {
    try (var session = factory().openSession()) {
      return session.getMapper(OutboxMapper.class).getByKey(OutboxType.GBIF_REGISTRY, String.valueOf(KEY));
    }
  }

  private void deleteCommand() {
    var cmd = command();
    if (cmd != null) {
      try (var session = factory().openSession(true)) {
        session.getMapper(OutboxMapper.class).delete(cmd.getId());
      }
    }
  }
}
