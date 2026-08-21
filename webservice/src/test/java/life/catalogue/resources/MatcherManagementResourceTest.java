package life.catalogue.resources;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.matching.UsageMatcherFactory;

import jakarta.ws.rs.ForbiddenException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@code /matcher/{key}} is not a {@code /dataset/{key}} URI, so AuthFilter.isUserInRole cannot scope the
 * EDITOR role to the dataset in question and the resource has to check the write permission itself.
 */
public class MatcherManagementResourceTest {
  private static final int KEY = 1000;

  private DatasetInfoCache originalCache;
  private UsageMatcherFactory matcherFactory;
  private MatcherManagementResource resource;

  @Before
  public void setUp() {
    originalCache = DatasetInfoCache.CACHE;
    var cache = mock(DatasetInfoCache.class);
    when(cache.info(eq(KEY), anyBoolean()))
      .thenReturn(new DatasetInfoCache.DatasetInfo(KEY, DatasetOrigin.EXTERNAL, null, null, false));
    DatasetInfoCache.CACHE = cache;
    matcherFactory = mock(UsageMatcherFactory.class);
    resource = new MatcherManagementResource(matcherFactory);
  }

  @After
  public void tearDown() {
    DatasetInfoCache.CACHE = originalCache;
  }

  private static User user(int key) {
    var u = new User();
    u.setKey(key);
    u.setUsername("user" + key);
    return u;
  }

  @Test
  public void editorOfDatasetCanDeleteItsMatcher() {
    var u = user(7);
    u.getEditor().add(KEY);            // the private dataset's own editor frees its matcher before the TTL
    resource.deleteMatcher(KEY, u);
    verify(matcherFactory).remove(KEY);
  }

  @Test
  public void editorOfAnotherDatasetCannotDeleteMatcher() {
    var u = user(8);
    u.getEditor().add(KEY + 1);        // an editor, but not of this dataset
    assertThrows(ForbiddenException.class, () -> resource.deleteMatcher(KEY, u));
    verify(matcherFactory, never()).remove(anyInt());
  }

  @Test
  public void adminCanDeleteAnyMatcher() {
    var u = user(9);
    u.getRoles().add(User.Role.ADMIN);
    resource.deleteMatcher(KEY, u);
    verify(matcherFactory).remove(KEY);
  }
}
