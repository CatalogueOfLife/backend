package life.catalogue.matching.person.harvest;

import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.common.io.Resources;
import life.catalogue.matching.person.MemoryPersonStore;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonStore;

import org.gbif.nameparser.api.NomCode;

import java.time.LocalDate;
import java.util.*;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One harvest of the person registry, without its database: reading the sources, finding the Wikidata items that
 * disappeared, and merging the harvest into the registry by the rules of {@link PersonMerger}. The job around it keeps
 * the slow reading out of any transaction and the fast merge inside one.
 */
public final class PersonRebuild {
  private static final Logger LOG = LoggerFactory.getLogger(PersonRebuild.class);
  private static final String AUTHOR_MAP = "authorship/authormap.txt";
  private static final int LIST_LIMIT = 500;

  /**
   * @param stats what the sources could not map, for the report
   */
  public record Harvest(List<PersonRecord> records, String stats) {
  }

  /**
   * @param problems what makes the rebuilt registry inconsistent, empty if it can be written
   */
  public record Outcome(PersonFiles.Content content, List<String> problems, String report) {
  }

  private PersonRebuild() {
  }

  /**
   * Reads every source: the slow part, to run with no database session open.
   */
  public static Harvest fetch(List<HarvestSource> sources) throws Exception {
    List<PersonRecord> records = new ArrayList<>();
    StringBuilder stats = new StringBuilder();
    for (HarvestSource s : sources) {
      List<PersonRecord> read = s.read();
      LOG.info("{}: {} records", s.name(), read.size());
      records.addAll(read);
      stats.append(s.stats());
    }
    return new Harvest(records, stats.toString());
  }

  /**
   * @return the Wikidata items of the registry no record carries any more, to be asked for a redirect
   */
  public static List<String> gone(PersonFiles.Content existing, Harvest harvest) {
    Set<String> seen = new HashSet<>();
    harvest.records().forEach(r -> {
      if (r.wikidata() != null) seen.add(r.wikidata());
    });
    return existing.persons().stream()
      .map(Person::wikidata)
      .filter(q -> q != null && !seen.contains(q))
      .toList();
  }

  /**
   * Merges the harvest into the registry and checks the result, which is only fit to be written without problems.
   *
   * @param redirects  Wikidata items of the registry that became a redirect, old Q-id to new Q-id
   * @param today      the day a person no source has any more is retired on
   * @param maxRetired  a harvest retiring more persons than this is a problem - a source may have answered too little -
   *                    null for no limit
   */
  public static Outcome rebuild(PersonFiles.Content existing, Harvest harvest, Map<String, String> redirects, LocalDate today,
                                @Nullable Integer maxRetired) {
    var result = new PersonMerger(today).merge(existing, harvest.records(), redirects);
    var store = new MemoryPersonStore(result.content());
    List<String> problems = new ArrayList<>(store.problems());
    int retired = result.report().retired.size();
    if (maxRetired != null && retired > maxRetired) {
      problems.add(retired + " persons would be retired, more than the limit of " + maxRetired
        + ": a source may have answered too little");
    }
    StringBuilder sb = new StringBuilder("# Person harvest\n\n");
    sb.append(String.format("persons %,d, names %,d, relations %,d%n", result.content().persons().size(),
      result.content().names().size(), result.content().relations().size()));
    if (!problems.isEmpty()) {
      sb.append(String.format("%n## Inconsistent, nothing written: %,d%n", problems.size()));
      problems.stream().limit(LIST_LIMIT).forEach(p -> sb.append("  ").append(p).append('\n'));
    }
    sb.append(result.report().render()).append('\n').append(harvest.stats());
    unresolvedAuthorMapRows(store, sb);
    return new Outcome(result.content(), problems, sb.toString());
  }

  /**
   * Author map rows none of whose forms name a person: hand edits worth keeping become curated lines.
   */
  private static void unresolvedAuthorMapRows(PersonStore store, StringBuilder sb) {
    List<String> unresolved = new ArrayList<>();
    int rows = 0;
    for (String[] row : (Iterable<String[]>) Resources.tabRows(AUTHOR_MAP)::iterator) {
      if (row.length < 3) continue;
      rows++;
      PersonFormCode code = PersonFormCode.valueOf(row[1].trim().toUpperCase());
      List<NomCode> codes = switch (code) {
        case BOT -> List.of(NomCode.BOTANICAL);
        case ZOO -> List.of(NomCode.ZOOLOGICAL);
        case ANY -> List.of(NomCode.BOTANICAL, NomCode.ZOOLOGICAL);
      };
      boolean found = false;
      for (int i = 0; i < row.length && !found; i++) {
        if (i == 1) continue;
        for (NomCode c : codes) {
          if (!store.candidates(row[i], c).isEmpty()) {
            found = true;
            break;
          }
        }
      }
      if (!found) {
        unresolved.add(String.join("\t", row));
      }
    }
    sb.append(String.format("%n## Author map rows no person resolves: %,d of %,d%n", unresolved.size(), rows));
    unresolved.stream().limit(LIST_LIMIT).forEach(r -> sb.append("  ").append(r).append('\n'));
  }
}
