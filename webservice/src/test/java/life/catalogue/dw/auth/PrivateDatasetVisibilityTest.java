package life.catalogue.dw.auth;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.junit.DatasetInfoCacheMockRule;

import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Searches and listings across datasets leave out private datasets the caller cannot read, see gbif/informatics#85 (A6).
 */
public class PrivateDatasetVisibilityTest {
  static final int PUBLIC = 10;
  static final int PRIVATE = 11;
  static final int PROJECT = 12;
  static final int PRIVATE_RELEASE = 13;

  @Rule
  public final DatasetInfoCacheMockRule infoCache = new DatasetInfoCacheMockRule()
    .put(PUBLIC, DatasetOrigin.EXTERNAL, null)
    .putPrivate(PRIVATE, DatasetOrigin.EXTERNAL, null)
    .put(PROJECT, DatasetOrigin.PROJECT, null)
    .putPrivate(PRIVATE_RELEASE, DatasetOrigin.RELEASE, PROJECT);

  User editor;
  User projectReviewer;
  User admin;

  @Before
  public void setup() {
    editor = new User();
    editor.setKey(100);
    editor.addDatasetRole(User.Role.EDITOR, PRIVATE);

    projectReviewer = new User();
    projectReviewer.setKey(101);
    projectReviewer.addDatasetRole(User.Role.REVIEWER, PROJECT);

    admin = new User();
    admin.setKey(102);
    admin.addRole(User.Role.ADMIN);
  }

  @Test
  public void unreadablePrivateDatasets() {
    assertEquals(Set.of(PRIVATE, PRIVATE_RELEASE), AuthFilter.unreadablePrivateDatasets(Optional.empty()));
    assertEquals(Set.of(PRIVATE_RELEASE), AuthFilter.unreadablePrivateDatasets(Optional.of(editor)));
    // a role on the project covers its private releases
    assertEquals(Set.of(PRIVATE), AuthFilter.unreadablePrivateDatasets(Optional.of(projectReviewer)));
    assertEquals(Set.of(), AuthFilter.unreadablePrivateDatasets(Optional.of(admin)));
  }

  @Test
  public void canSeeJob() {
    var anon = Optional.<User>empty();
    assertTrue(AuthFilter.canSeeJob(anon, null, 1));
    assertTrue(AuthFilter.canSeeJob(anon, PUBLIC, 1));
    assertFalse(AuthFilter.canSeeJob(anon, PRIVATE, 1));

    assertTrue(AuthFilter.canSeeJob(Optional.of(editor), PRIVATE, 1));
    assertFalse(AuthFilter.canSeeJob(Optional.of(editor), PRIVATE_RELEASE, 1));
    // the creator of a job always sees it
    assertTrue(AuthFilter.canSeeJob(Optional.of(editor), PRIVATE_RELEASE, editor.getKey()));
    assertTrue(AuthFilter.canSeeJob(Optional.of(projectReviewer), PRIVATE_RELEASE, 1));
    assertTrue(AuthFilter.canSeeJob(Optional.of(admin), PRIVATE, 1));
  }
}
