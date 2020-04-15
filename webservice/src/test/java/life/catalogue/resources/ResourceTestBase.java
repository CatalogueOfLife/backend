package life.catalogue.resources;

import javax.ws.rs.client.WebTarget;

import io.dropwizard.testing.ResourceHelpers;
import life.catalogue.WsServerRule;
import life.catalogue.api.model.User;
import org.apache.ibatis.session.SqlSessionFactory;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.ClassRule;

public class ResourceTestBase {
  
  protected String baseURL;
  protected WebTarget base;
  private final String path;
  
  public ResourceTestBase(String path) {
    this.path = path;
    baseURL = String.format("http://localhost:%d"+path, RULE.getLocalPort());
    base = RULE.client().target(baseURL);

  }
  
  @ClassRule
  public static final WsServerRule RULE = new WsServerRule(ResourceHelpers.resourceFilePath("config-test.yaml"));


  public SqlSessionFactory factory() {
    return RULE.getSqlSessionFactory();
  }

  public void addUserPermissions(String username, int... datasetKey) {
    RULE.addUserPermissions(username, datasetKey);
  }

  protected void printDiff(Object o1, Object o2) {
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(o1, o2);
    System.out.println(diff);
  }

}