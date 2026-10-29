package life.catalogue.resources;

import life.catalogue.api.model.DatasetSimple;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.DatasetDao;
import life.catalogue.junit.DatasetInfoCacheMockRule;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GET /dataset/simple?id= must not hand out private datasets the user cannot read, see #1643.
 * They are answered like an unknown key, with a null entry, so the result still lines up with the requested ids.
 */
public class DatasetResourceTest {
  static final int PUBLIC = 10;
  static final int PROJECT = 11;
  static final int RELEASE = 12;
  static final int UNKNOWN = 13;

  @Rule
  public final DatasetInfoCacheMockRule infoCache = new DatasetInfoCacheMockRule()
    .put(PUBLIC, DatasetOrigin.EXTERNAL, null)
    .put(PROJECT, DatasetOrigin.PROJECT, null)
    .put(RELEASE, DatasetOrigin.RELEASE, PROJECT);

  DatasetResource resource;
  DatasetSimple pub;
  DatasetSimple project;
  DatasetSimple release;
  final List<Integer> keys = List.of(PUBLIC, PROJECT, RELEASE, UNKNOWN);

  @Before
  public void setup() {
    pub = simple(PUBLIC, false);
    project = simple(PROJECT, true);
    release = simple(RELEASE, true);
    DatasetDao dao = mock(DatasetDao.class);
    when(dao.listSimple(keys)).thenReturn(Arrays.asList(pub, project, release, null));
    resource = new DatasetResource(mock(SqlSessionFactory.class), mock(JobExecutor.class), dao);
  }

  private static DatasetSimple simple(int key, boolean privat) {
    var d = new DatasetSimple();
    d.setKey(key);
    d.setPrivat(privat);
    return d;
  }

  @Test
  public void simpleHidesPrivateDatasets() {
    assertEquals(Arrays.asList(pub, null, null, null), resource.projectSourceSimple(keys, Optional.empty()));

    var stranger = new User();
    stranger.setKey(100);
    assertEquals(Arrays.asList(pub, null, null, null), resource.projectSourceSimple(keys, Optional.of(stranger)));

    // a project reviewer reads the project and its releases
    var reviewer = new User();
    reviewer.setKey(101);
    reviewer.addDatasetRole(User.Role.REVIEWER, PROJECT);
    assertEquals(Arrays.asList(pub, project, release, null), resource.projectSourceSimple(keys, Optional.of(reviewer)));

    var admin = new User();
    admin.setKey(102);
    admin.addRole(User.Role.ADMIN);
    assertEquals(Arrays.asList(pub, project, release, null), resource.projectSourceSimple(keys, Optional.of(admin)));
  }
}
