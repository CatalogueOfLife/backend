package life.catalogue.api.vocab;

import life.catalogue.api.model.User;

/**
 * Constants for user keys mostly.
 */
public class Users {
  /**
   * The lowest key a real, human user managed through the GBIF registry can have.
   * All keys below this are reserved for internal, non human "bot" users - see {@link #isBot(int)}.
   * This threshold is mirrored in SQL when granting the creator editor access (see the aclEditor fragment in DatasetMapper.xml).
   */
  public final static int MIN_HUMAN_KEY = 100;

  /**
   * Not a real user, but a fake user key that has permissions to view all
   */
  public final static int SUPERUSER = User.ADMIN_MAGIC_KEY;
  public final static int TESTER = -1;
  public final static int DB_INIT = 0;
  public final static int IMPORTER = 10;
  public final static int MATCHER = 11;
  public final static int GBIF_SYNC = 12;
  public final static int RELEASER = 13;
  public final static int HOMOTYPIC_GROUPER = 14;

  private Users() {
  }

  public static User user(int key) {
    User u = new User();
    u.setKey(key);
    return u;
  }

  /**
   * @return true for any internal, non human user. Real human users always have keys >= {@link #MIN_HUMAN_KEY}.
   */
  public static boolean isBot(int key) {
    return key < MIN_HUMAN_KEY;
  }
}
