package life.catalogue.resources;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.AuthorizationDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.UserMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.jupiter.api.extension.ExtendWith;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.client.WebTarget;

@Ignore("To be migrated to junit5 still")
@ExtendWith(DropwizardExtensionsSupport.class)
public class ResourceITBase {

  protected String baseURL;
  protected WebTarget base;
  private final String path;
  private final AuthorizationDao adao;

  public ResourceITBase(String path) {
    this.path = path;
    //baseURL = String.format("http://localhost:%d"+path, RULE.getLocalPort());
    //base = RULE.client().target(baseURL);
    adao = new AuthorizationDao(factory(), null); //TODO: RULE.getServer().getBus()
  }
  
  //@ClassRule
  //public static final WsServerRule RULE = new WsServerRule(ResourceHelpers.resourceFilePath("config-test.yaml"));

  @Before
  public void flushUserCache(){
    //RULE.getServer().getAuthBundle().getIdService().persistCachedUsers();
  }

  public SqlSessionFactory factory() {
    // TODO
    return null;
  }

  public void addUserPermission(String username, int datasetKey) {
    try (SqlSession session = factory().openSession(true)) {
      User u = session.getMapper(UserMapper.class).getByUsername(username);
      addUserPermission(u.getKey(), datasetKey);
    }
  }

  public void addUserPermission(int editorKey, int datasetKey) {
    try (SqlSession session = factory().openSession(true)) {
      session.getMapper(DatasetMapper.class).addEditor(datasetKey, editorKey, Users.TESTER);
    }
    adao.addUser(datasetKey, editorKey, User.Role.EDITOR, TestEntityGenerator.USER_ADMIN);
  }

  protected void printDiff(Object o1, Object o2) {
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(o1, o2);
    System.out.println(diff);
  }

}