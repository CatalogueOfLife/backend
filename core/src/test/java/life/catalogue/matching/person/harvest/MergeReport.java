package life.catalogue.matching.person.harvest;

import java.util.ArrayList;
import java.util.List;

/**
 * What a merge did and everything a person should look at.
 */
public class MergeReport {
  private static final int LIST_LIMIT = 500;
  public int existing;
  public int added;
  public int merged;
  public int redirected;
  public int withoutName;
  public int withoutId;
  public int relationsDropped;
  public final List<String> conflicts = new ArrayList<>();
  /** a value of the files a source now gives otherwise: the harvest keeps the file's, a person decides */
  public final List<String> changed = new ArrayList<>();
  public final List<String> ambiguous = new ArrayList<>();
  public final List<String> notSeen = new ArrayList<>();

  public String render() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("persons before %,d, added %,d, merged into another %,d, following a Wikidata redirect %,d%n",
      existing, added, merged, redirected));
    sb.append(String.format("dropped: records without a name %,d, records without an id %,d, relations to no person %,d%n",
      withoutName, withoutId, relationsDropped));
    list(sb, "Sources disagree", conflicts);
    list(sb, "The files and a source disagree", changed);
    list(sb, "Authority ids claimed by two persons", ambiguous);
    list(sb, "In the files but in no source any more", notSeen);
    return sb.toString();
  }

  private static void list(StringBuilder sb, String title, List<String> lines) {
    sb.append(String.format("%n## %s: %,d%n", title, lines.size()));
    lines.stream().limit(LIST_LIMIT).forEach(l -> sb.append("  ").append(l).append('\n'));
  }
}
