package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UsersTest {

  @Test
  public void isBot() {
    // all internal, non human users have keys below MIN_HUMAN_KEY
    assertTrue(Users.isBot(Users.SUPERUSER));
    assertTrue(Users.isBot(Users.TESTER));
    assertTrue(Users.isBot(Users.DB_INIT));
    assertTrue(Users.isBot(Users.IMPORTER));
    assertTrue(Users.isBot(Users.MATCHER));
    assertTrue(Users.isBot(Users.GBIF_SYNC));
    assertTrue(Users.isBot(Users.RELEASER));
    assertTrue(Users.isBot(Users.HOMOTYPIC_GROUPER));
    assertTrue(Users.isBot(Users.MIN_HUMAN_KEY - 1));

    // real human users
    assertFalse(Users.isBot(Users.MIN_HUMAN_KEY));
    assertFalse(Users.isBot(100));
    assertFalse(Users.isBot(123));
    assertFalse(Users.isBot(999999));
  }
}
