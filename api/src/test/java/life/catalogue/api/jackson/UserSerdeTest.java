package life.catalogue.api.jackson;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Setting;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

public class UserSerdeTest extends SerdeTestBase<User> {

  public UserSerdeTest() {
    super(User.class);
  }

  @Override
  public User genTestValue() throws Exception {
    User u = new User();
    u.setFirstname("Karl");
    u.setLastname("Marx");
    u.setKey(1);
    u.addDatasetRole(User.Role.EDITOR, 1000);
    return u;
  }

  @Test
  public void convertFromJSON() throws Exception {
    var u = genTestValue();

    var json = testRoundtrip(u);
    assertFalse(json.contains("reviewer\":"));
    assertTrue(json.contains("editor\":"));

    u.getEditor().clear();
    json = testRoundtrip(u);
    assertFalse(json.contains("reviewer\":"));
    assertFalse(json.contains("editor\":"));
  }
}