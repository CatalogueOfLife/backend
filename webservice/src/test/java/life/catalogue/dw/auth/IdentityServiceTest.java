package life.catalogue.dw.auth;

import life.catalogue.api.model.User;
import life.catalogue.db.mapper.UserMapper;

import java.time.LocalDateTime;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class IdentityServiceTest {
  IdentityService ids;
  UserMapper mapper;

  @Before
  public void init() {
    mapper = mock(UserMapper.class);
    var session = mock(SqlSession.class);
    when(session.getMapper(UserMapper.class)).thenReturn(mapper);
    var factory = mock(SqlSessionFactory.class);
    when(factory.openSession(anyBoolean())).thenReturn(session);
    ids = new IdentityService(mock(AuthenticationProvider.class));
    ids.setSqlSessionFactory(factory);
  }

  /**
   * Requests with a token count as a login, but are written to the db at most once a day, see
   * https://github.com/CatalogueOfLife/backend/issues/1212
   */
  @Test
  public void touch() throws Exception {
    User u = new User();
    u.setKey(7);

    ids.touch(u);
    assertNotNull(u.getLastLogin());
    verify(mapper, times(1)).updateLastLogin(eq(7), any());

    // the same day writes nothing
    ids.touch(u);
    ids.touch(u);
    verify(mapper, times(1)).updateLastLogin(eq(7), any());

    // a day later it does
    u.setLastLogin(LocalDateTime.now().minusDays(1).minusMinutes(1));
    ids.touch(u);
    verify(mapper, times(2)).updateLastLogin(eq(7), any());
    assertTrue(u.getLastLogin().isAfter(LocalDateTime.now().minusMinutes(1)));
  }
}
