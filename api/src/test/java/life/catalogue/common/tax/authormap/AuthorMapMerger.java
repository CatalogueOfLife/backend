package life.catalogue.common.tax.authormap;

import life.catalogue.common.tax.AuthorshipNormalizer;

import java.util.*;

public class AuthorMapMerger {

  private static class Group {
    final int order;                                   // creation order = precedence (lower = higher precedence)
    String canonical;
    AuthorCode code;
    final LinkedHashSet<String> aliases = new LinkedHashSet<>();
    final Set<String> keys = new HashSet<>();
    Group(int order) { this.order = order; }
  }

  /** @param sources highest precedence first (index 0 = manual). */
  public static List<AuthorEntry> merge(List<List<AuthorEntry>> sources) {
    Map<String, Group> keyIndex = new HashMap<>();
    List<Group> groups = new ArrayList<>();
    int counter = 0;

    for (List<AuthorEntry> source : sources) {
      for (AuthorEntry e : source) {
        List<String> keys = normalizedKeys(e);
        // collect every distinct existing group these keys already resolve to
        LinkedHashSet<Group> matched = new LinkedHashSet<>();
        for (String k : keys) {
          Group hit = keyIndex.get(k);
          if (hit != null) matched.add(hit);
        }

        Group g;
        if (matched.isEmpty()) {
          g = new Group(counter++);
          g.canonical = e.canonical();
          g.code = e.code();
          groups.add(g);
        } else {
          // primary = highest-precedence (earliest created) matched group; its canonical wins
          g = matched.iterator().next();
          for (Group cand : matched) {
            if (cand.order < g.order) g = cand;
          }
          // fold every other matched group into the primary and retire it
          for (Group other : matched) {
            if (other == g) continue;
            g.aliases.addAll(other.aliases);
            foldCode(g, other.code);
            for (String k : other.keys) {
              g.keys.add(k);
              keyIndex.put(k, g);
            }
            groups.remove(other);
          }
          // fold the incoming entry's code
          foldCode(g, e.code());
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

  /** Widen toward ANY when two single-code contributions conflict; ANY absorbs everything. */
  private static void foldCode(Group g, AuthorCode other) {
    if (g.code != other && g.code != AuthorCode.ANY && other != AuthorCode.ANY) {
      g.code = AuthorCode.ANY;
    }
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
