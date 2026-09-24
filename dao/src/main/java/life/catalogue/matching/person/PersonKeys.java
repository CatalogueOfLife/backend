package life.catalogue.matching.person;

import life.catalogue.common.tax.AuthorshipNormalizer;

import javax.annotation.Nullable;

/**
 * The key name forms and citations are looked up by.
 */
public final class PersonKeys {
  private PersonKeys() {
  }

  /**
   * {@link AuthorshipNormalizer#normalize(String)} until it no longer changes. The comparator hands over authors
   * normalized already, and normalizing is not always idempotent - a capital Đ is only folded once lower cased, and
   * removing an e can make a new "ae", "oe" or "ue" - so a key normalized once would miss them.
   *
   * @return the key, null for a form that leaves nothing to look up
   */
  @Nullable
  public static String key(@Nullable String form) {
    String key = AuthorshipNormalizer.normalize(form);
    for (int i = 0; key != null && i < 5; i++) {
      String next = AuthorshipNormalizer.normalize(key);
      if (key.equals(next)) break;
      key = next;
    }
    return key == null || key.isBlank() ? null : key;
  }
}
