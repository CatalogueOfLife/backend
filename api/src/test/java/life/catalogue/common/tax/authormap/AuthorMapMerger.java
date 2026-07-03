package life.catalogue.common.tax.authormap;

import life.catalogue.common.tax.AuthorshipNormalizer;

import java.util.*;

public class AuthorMapMerger {

  private static class Group {
    final int order;
    String canonical;
    AuthorCode code;
    boolean curated;
    final LinkedHashSet<String> aliases = new LinkedHashSet<>();
    final Set<String> keys = new HashSet<>();
    Group(int order) { this.order = order; }
  }

  /**
   * @param sources      highest precedence first (index 0 = manual). Canonical form comes from the
   *                     earliest (highest-precedence) contributor.
   * @param curatedCount the first curatedCount sources are authoritative (manual + existing IPNI);
   *                     their alias keys are protected when disambiguating ambiguous keys.
   */
  public static List<AuthorEntry> merge(List<List<AuthorEntry>> sources, int curatedCount) {
    Map<String, Group> bridgeIndex = new HashMap<>();   // multi-token identity key -> group
    List<Group> groups = new ArrayList<>();
    int counter = 0;

    for (int s = 0; s < sources.size(); s++) {
      boolean curated = s < curatedCount;
      for (AuthorEntry e : sources.get(s)) {
        List<String> allKeys = normalizedKeys(e);
        List<String> bridge = new ArrayList<>();
        for (String k : allKeys) if (k.indexOf(' ') >= 0) bridge.add(k);   // multi-token = discriminating identity

        LinkedHashSet<Group> matched = new LinkedHashSet<>();
        for (String k : bridge) {
          Group hit = bridgeIndex.get(k);
          if (hit != null) matched.add(hit);
        }

        Group g;
        if (matched.isEmpty()) {
          g = new Group(counter++);
          g.canonical = e.canonical();
          g.code = e.code();
          groups.add(g);
        } else {
          g = matched.iterator().next();
          for (Group cand : matched) if (cand.order < g.order) g = cand;
          for (Group other : matched) {
            if (other == g) continue;
            g.aliases.addAll(other.aliases);
            g.keys.addAll(other.keys);
            foldCode(g, other.code);
            g.curated = g.curated || other.curated;
            for (String k : other.keys) if (k.indexOf(' ') >= 0) bridgeIndex.put(k, g);
            groups.remove(other);
          }
          foldCode(g, e.code());
        }
        g.curated = g.curated || curated;
        g.aliases.addAll(e.aliases());
        g.keys.addAll(allKeys);
        for (String k : bridge) bridgeIndex.put(k, g);
      }
    }

    disambiguate(groups);

    List<AuthorEntry> out = new ArrayList<>();
    for (Group g : groups) {
      if (g.aliases.isEmpty()) continue;
      out.add(new AuthorEntry(g.canonical, g.code, new ArrayList<>(g.aliases)));
    }
    return out;
  }

  /** Backwards-compatible: treat all sources as curated. */
  public static List<AuthorEntry> merge(List<List<AuthorEntry>> sources) {
    return merge(sources, sources.size());
  }

  /**
   * Ensure every remaining normalized alias key resolves to a single canonical. For a key held by
   * multiple groups: if exactly one curated group holds it, keep it only there and strip the colliding
   * aliases from the others; otherwise drop the key from all groups.
   */
  private static void disambiguate(List<Group> groups) {
    Map<String, List<Group>> holders = new HashMap<>();
    for (Group g : groups) {
      Set<String> seen = new HashSet<>();
      for (String a : g.aliases) {
        String k = AuthorshipNormalizer.normalize(a);
        if (k != null && seen.add(k)) holders.computeIfAbsent(k, x -> new ArrayList<>()).add(g);
      }
    }
    for (var entry : holders.entrySet()) {
      String key = entry.getKey();
      List<Group> hs = entry.getValue();
      if (hs.size() < 2) continue;
      List<Group> curated = new ArrayList<>();
      for (Group g : hs) if (g.curated) curated.add(g);
      Group keep = curated.size() == 1 ? curated.get(0) : null;
      for (Group g : hs) {
        if (g == keep) continue;
        g.aliases.removeIf(a -> key.equals(AuthorshipNormalizer.normalize(a)));
      }
    }
  }

  private static void foldCode(Group g, AuthorCode other) {
    if (g.code != other && g.code != AuthorCode.ANY && other != AuthorCode.ANY) g.code = AuthorCode.ANY;
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
