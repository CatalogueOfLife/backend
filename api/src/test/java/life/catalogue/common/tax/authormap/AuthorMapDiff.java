package life.catalogue.common.tax.authormap;

import life.catalogue.common.tax.AuthorshipNormalizer;

import java.util.*;

public class AuthorMapDiff {

  public record Result(List<String> removedCanonicals, List<String> removedAliasKeys) {}

  public static Result diff(List<AuthorEntry> oldEntries, List<AuthorEntry> newEntries) {
    Set<String> newCanon = new HashSet<>();
    Set<String> newKeys = new HashSet<>();
    for (AuthorEntry e : newEntries) {
      newCanon.add(e.canonical());
      collectKeys(e, newKeys);
    }
    List<String> removedCanon = new ArrayList<>();
    Set<String> removedKeys = new LinkedHashSet<>();
    for (AuthorEntry e : oldEntries) {
      if (!newCanon.contains(e.canonical())) removedCanon.add(e.canonical());
      Set<String> keys = new LinkedHashSet<>();
      collectKeys(e, keys);
      for (String k : keys) if (!newKeys.contains(k)) removedKeys.add(k);
    }
    Collections.sort(removedCanon, String.CASE_INSENSITIVE_ORDER);
    return new Result(removedCanon, new ArrayList<>(removedKeys));
  }

  private static void collectKeys(AuthorEntry e, Set<String> into) {
    for (String a : e.aliases()) {
      String n = AuthorshipNormalizer.normalize(a);
      if (n != null) into.add(n);
    }
  }

  public static String render(Result r) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Author map regeneration diff\n\n");
    sb.append("## Removed canonical authors (").append(r.removedCanonicals().size()).append(")\n");
    r.removedCanonicals().forEach(c -> sb.append("- ").append(c).append('\n'));
    sb.append("\n## Removed alias keys (").append(r.removedAliasKeys().size()).append(")\n");
    r.removedAliasKeys().forEach(k -> sb.append("- ").append(k).append('\n'));
    sb.append("\nReview these and move any worth keeping into authormap-manual.txt.\n");
    return sb.toString();
  }
}
