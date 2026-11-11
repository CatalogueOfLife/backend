package life.catalogue.db.mapper;

import life.catalogue.api.model.GbifRegistration;
import life.catalogue.api.vocab.Users;

import java.util.List;
import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.*;

public class GbifRegistrationMapperTest extends MapperTestBase<GbifRegistrationMapper> {

  public GbifRegistrationMapperTest() {
    super(GbifRegistrationMapper.class);
  }

  @Test
  public void roundtrip() throws Exception {
    assertNull(mapper().get(appleKey));
    assertFalse(mapper().exists(appleKey));
    assertTrue(mapper().list().isEmpty());

    UUID gbifKey = UUID.randomUUID();
    mapper().create(new GbifRegistration(appleKey, gbifKey, Users.TESTER));
    commit();

    var r = mapper().get(appleKey);
    assertNotNull(r);
    assertEquals(appleKey, (int) r.getDatasetKey());
    assertEquals(gbifKey, r.getGbifKey());
    assertEquals(Users.TESTER, (int) r.getCreatedBy());
    assertNotNull(r.getCreated());
    assertTrue(mapper().exists(appleKey));
    assertEquals(List.of(r), mapper().list());

    assertEquals(1, mapper().delete(appleKey));
    commit();
    assertFalse(mapper().exists(appleKey));
    assertEquals(0, mapper().delete(appleKey));
  }

  /**
   * The GBIF registry sync must never delete a dataset ChecklistBank registered in GBIF itself.
   */
  @Test
  public void syncedFromGbifExcludesRegistered() throws Exception {
    var dm = mapper(DatasetMapper.class);
    UUID gbifKey = UUID.randomUUID();
    UUID publisherKey = UUID.randomUUID();
    assertEquals(1, dm.updateGbif(appleKey, gbifKey, publisherKey, Users.TESTER));
    commit();
    var d = dm.get(appleKey);
    assertEquals(gbifKey, d.getGbifKey());
    assertEquals(publisherKey, d.getGbifPublisherKey());
    assertEquals(List.of(appleKey), dm.listKeysSyncedFromGBIF());

    mapper().create(new GbifRegistration(appleKey, gbifKey, Users.TESTER));
    commit();
    assertTrue(dm.listKeysSyncedFromGBIF().isEmpty());

    dm.updateGbif(appleKey, null, null, Users.TESTER);
    commit();
    d = dm.get(appleKey);
    assertNull(d.getGbifKey());
    assertNull(d.getGbifPublisherKey());
  }
}
