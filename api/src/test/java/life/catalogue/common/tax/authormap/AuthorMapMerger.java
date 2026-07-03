package life.catalogue.common.tax.authormap;

import life.catalogue.common.tax.AuthorshipNormalizer;

import java.util.*;

public class AuthorMapMerger {

  private static class Group {
    String canonical;
    AuthorCode code;
    boolean locked;                                   // came from highest-precedence (manual) source
    final LinkedHashSet<String> aliases = new LinkedHashSet<>();
    final Set<String> keys = new HashSet<>();          // normalized alias keys owned by this group
  }

  /** @param sources highest precedence first (index 0 = manual). */
  public static List<AuthorEntry> merge(List<List<AuthorEntry>> sources) {
    Map<String, Group> keyIndex = new HashMap<>();
    List<Group> groups = new ArrayList<>();

    for (int s = 0; s < sources.size(); s++) {
      boolean manual = (s == 0);
      for (AuthorEntry e : sources.get(s)) {
        List<String> keys = normalizedKeys(e);
        Group g = keys.stream().map(keyIndex::get).filter(Objects::nonNull).findFirst().orElse(null);
        if (g == null) {
          g = new Group();
          g.canonical = e.canonical();
          g.code = e.code();
          g.locked = manual;
          groups.add(g);
        } else {
          // fold code: conflicting BOT/ZOO -> ANY
          if (g.code != e.code() && g.code != AuthorCode.ANY && e.code() != AuthorCode.ANY) {
            g.code = AuthorCode.ANY;
          }
        }
        g.aliases.addAll(e.aliases());
        for (String k : keys) {
          g.keys.add(k);
          keyIndex.put(k, g);
        }
      }
    }

    List<AuthorEntry> out = new ArrayList<>(groups.size());
    for (Group g : groups) {
      out.add(new AuthorEntry(g.canonical, g.code, new ArrayList<>(g.aliases)));
    }
    return out;
  }

  private static List<String> normalizedKeys(AuthorEntry e) {
    List<String> keys = new ArrayList<>();
    for (String a : e.aliases()) {
      String n = AuthorshipNormalizer.normalize(a);
      if (n != null) keys.add(n);
    }
    return keys;
  }
}
