package life.catalogue.matching.person.harvest;

import java.util.ArrayList;
import java.util.List;

/**
 * What a merge did - a diff of the registry - and everything a person should look at.
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
  public final List<String> addedPersons = new ArrayList<>();
  /** persons of before that became part of another: "wd:Q1 into wd:Q2: a Wikidata redirect of Q1 to Q2" */
  public final List<String> joined = new ArrayList<>();
  public final List<String> retired = new ArrayList<>();
  public final List<String> unretired = new ArrayList<>();
  /** a value of a person of before the sources give otherwise now: "wd:Q1 born: 1700 -> 1750" */
  public final List<String> changed = new ArrayList<>();
  /** a value of a curated person the sources give otherwise, which stays */
  public final List<String> curatedKept = new ArrayList<>();
  public final List<String> formsAdded = new ArrayList<>();
  public final List<String> formsRemoved = new ArrayList<>();
  public final List<String> relationsAdded = new ArrayList<>();
  public final List<String> relationsRemoved = new ArrayList<>();
  public final List<String> conflicts = new ArrayList<>();
  public final List<String> ambiguous = new ArrayList<>();

  public String render() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("persons before %,d, added %,d, merged into another %,d, following a Wikidata redirect %,d%n",
      existing, added, merged, redirected));
    sb.append(String.format("dropped: records without a name %,d, records without an id %,d, relations to no person %,d%n",
      withoutName, withoutId, relationsDropped));
    list(sb, "Values changed", changed);
    list(sb, "Persons added", addedPersons);
    list(sb, "Persons joined", joined);
    list(sb, "Persons retired", retired);
    list(sb, "Persons no longer retired", unretired);
    list(sb, "Forms added", formsAdded);
    list(sb, "Forms removed", formsRemoved);
    list(sb, "Relations added", relationsAdded);
    list(sb, "Relations removed", relationsRemoved);
    list(sb, "Curated values the sources give otherwise", curatedKept);
    list(sb, "Sources disagree", conflicts);
    list(sb, "Authority ids claimed by two persons", ambiguous);
    return sb.toString();
  }

  private static void list(StringBuilder sb, String title, List<String> lines) {
    sb.append(String.format("%n## %s: %,d%n", title, lines.size()));
    lines.stream().limit(LIST_LIMIT).forEach(l -> sb.append("  ").append(l).append('\n'));
  }
}
