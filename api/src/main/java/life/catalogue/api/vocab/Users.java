package life.catalogue.api.vocab;

import life.catalogue.api.model.User;

import java.util.Set;

/**
 * Constants for user keys mostly.
 */
public class Users {
  public final static int TESTER = -1;
  public final static int DB_INIT = 0;
  public final static int IMPORTER = 10;
  public final static int MATCHER = 11;
  public final static int GBIF_SYNC = 12;
  public final static int RELEASER = 13;
  public final static int HOMOTYPIC_GROUPER = 14;

  private final static Set<Integer> BOTS = Set.of(
    DB_INIT, IMPORTER, MATCHER, GBIF_SYNC, RELEASER
  );

  private Users() {
  }

  public static User user(int key) {
    User u = new User();
    u.setKey(key);
    return u;
  }

  public static boolean isBot(int key) {
    return BOTS.contains(key);
  }
}
