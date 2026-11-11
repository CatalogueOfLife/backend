package life.catalogue.dw.auth;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static life.catalogue.api.model.User.Role.ADMIN;
import static life.catalogue.api.model.User.Role.EDITOR;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthFilterIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.datasetMix();

  @Test
  public void isAuthorized() {
    User u = new User();
    assertFalse(AuthFilter.hasReadAccess(u,10));
    assertFalse(AuthFilter.hasReadAccess(u,11));
    assertFalse(AuthFilter.hasReadAccess(u,12));
    assertFalse(AuthFilter.hasReadAccess(u,13));
    assertFalse(AuthFilter.hasReadAccess(u,20));

    u.addDatasetRole(EDITOR, 1); // also adds editor role
    assertFalse(AuthFilter.hasReadAccess(u,10));
    assertFalse(AuthFilter.hasReadAccess(u,11));
    assertFalse(AuthFilter.hasReadAccess(u,12));
    assertFalse(AuthFilter.hasReadAccess(u,13));
    assertFalse(AuthFilter.hasReadAccess(u,20));

    u.addRole(ADMIN);
    assertTrue(AuthFilter.hasReadAccess(u,10));
    assertTrue(AuthFilter.hasReadAccess(u,11));
    assertTrue(AuthFilter.hasReadAccess(u,12));
    assertTrue(AuthFilter.hasReadAccess(u,13));
    assertTrue(AuthFilter.hasReadAccess(u,20));

    u.getRoles().remove(ADMIN);
    u.addDatasetRole(EDITOR, 20);
    assertFalse(AuthFilter.hasReadAccess(u,10));
    assertFalse(AuthFilter.hasReadAccess(u,11));
    assertFalse(AuthFilter.hasReadAccess(u,12));
    assertFalse(AuthFilter.hasReadAccess(u,13));
    assertTrue(AuthFilter.hasReadAccess(u,20));

    u.addDatasetRole(EDITOR, 12); // releases should not be added to permissions, but we make sure here it has no effect
    assertFalse(AuthFilter.hasReadAccess(u,10));
    assertFalse(AuthFilter.hasReadAccess(u,11));
    assertFalse(AuthFilter.hasReadAccess(u,12));
    assertFalse(AuthFilter.hasReadAccess(u,13));
    assertTrue(AuthFilter.hasReadAccess(u,20));

    u.addDatasetRole(EDITOR, 10); // projects give access to all releases
    u.removeDatasetRole(EDITOR, 20);
    assertTrue(AuthFilter.hasReadAccess(u,10));
    assertTrue(AuthFilter.hasReadAccess(u,11));
    assertTrue(AuthFilter.hasReadAccess(u,12));
    assertTrue(AuthFilter.hasReadAccess(u,13));
    assertFalse(AuthFilter.hasReadAccess(u,20));
  }

  /**
   * An organisation editor edits the external datasets of the organisation, but never its projects or releases,
   * and the organisation publisher role grants no access at all.
   */
  @Test
  public void organisationRoles() {
    final UUID org = UUID.randomUUID();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      dm.updateGbif(10, null, org, Users.TESTER);
      dm.updateGbif(20, UUID.randomUUID(), org, Users.TESTER);
    }
    DatasetInfoCache.CACHE.clear();

    User u = new User();
    u.getOrgPublisher().add(org);
    for (int key : new int[]{10, 11, 20}) {
      assertFalse(AuthFilter.hasReadAccess(u, key));
      assertFalse(AuthFilter.hasWriteAccess(u, key));
    }

    u.getOrgEditor().add(org);
    assertTrue(AuthFilter.hasReadAccess(u, 20));
    assertTrue(AuthFilter.hasWriteAccess(u, 20));
    // the project and its releases carry the organisation, but are no external datasets
    assertFalse(AuthFilter.hasReadAccess(u, 10));
    assertFalse(AuthFilter.hasWriteAccess(u, 10));
    assertFalse(AuthFilter.hasWriteAccess(u, 11));
    // another organisation's editor
    User other = new User();
    other.getOrgEditor().add(UUID.randomUUID());
    assertFalse(AuthFilter.hasWriteAccess(other, 20));
  }

}