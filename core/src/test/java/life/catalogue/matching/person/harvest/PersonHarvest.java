package life.catalogue.matching.person.harvest;

import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.common.io.Resources;
import life.catalogue.matching.person.MemoryPersonStore;
import life.catalogue.matching.person.PersonFiles;

import org.gbif.nameparser.api.NomCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/**
 * Grows the person registry from its authorities, run by hand. It reads the three files, reads every source, merges
 * by the rules of {@link PersonMerger}, and writes the files back only if the result is consistent. The review report
 * holds what the merge could not decide, what the sources could not map and the author map rows no person resolves -
 * candidates for curated lines. Every answer is cached in the work dir, so a rerun resumes.
 * <pre>
 * mvn -q -pl core test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.person.harvest.PersonHarvest \
 *     src/main/resources/authorship/persons target/person-harvest"
 * </pre>
 */
public class PersonHarvest {
  private static final String AUTHOR_MAP = "authorship/authormap.txt";
  private static final int UNRESOLVED_LIMIT = 500;

  public static void main(String[] args) throws Exception {
    if (args.length != 2 && !(args.length == 4 && args[2].equals("--zoobank"))) {
      System.err.println("Usage: PersonHarvest <persons dir> <work dir> [--zoobank <dump>]");
      System.exit(1);
    }
    Path dir = Path.of(args[0]);
    Path work = Path.of(args[1]);
    var wikidata = new WikidataPersonSource(new CachingFetcher(work.resolve("cache/wikidata"),
      new RetryingFetcher(new HttpFetcher("application/json"), Duration.ofSeconds(1), Json::complete), Json::complete));
    var ipni = new IpniPersonSource(new CachingFetcher(work.resolve("cache/ipni"),
      new RetryingFetcher(new HttpFetcher("application/json"), Duration.ofMillis(250), Json::complete), Json::complete));
    List<HarvestSource> sources = new ArrayList<>(List.of(wikidata, ipni));
    if (args.length == 4) {
      sources.add(new ZooBankDumpSource(Path.of(args[3])));
    }
    String report = run(dir, sources, qids -> {
      try {
        return wikidata.redirects(qids);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
    Files.createDirectories(work);
    Files.writeString(work.resolve("report.txt"), report, StandardCharsets.UTF_8);
    System.out.println(report.lines().limit(40).reduce("", (x, y) -> x + y + "\n"));
    System.out.println("Full report in " + work.resolve("report.txt"));
  }

  /**
   * @return the review report
   */
  static String run(Path dir, List<HarvestSource> sources, Function<Collection<String>, Map<String, String>> redirects) throws Exception {
    PersonFiles.Content existing = PersonFiles.read(dir);
    List<PersonRecord> records = new ArrayList<>();
    StringBuilder stats = new StringBuilder();
    for (HarvestSource s : sources) {
      List<PersonRecord> read = s.read();
      System.out.printf("%s: %,d records%n", s.name(), read.size());
      records.addAll(read);
      stats.append(s.stats());
    }
    Set<String> seen = new HashSet<>();
    records.forEach(r -> {
      if (r.wikidata() != null) seen.add(r.wikidata());
    });
    List<String> gone = existing.persons().stream()
      .map(p -> p.wikidata())
      .filter(q -> q != null && !seen.contains(q))
      .toList();
    var result = new PersonMerger().merge(existing, records, gone.isEmpty() ? Map.of() : redirects.apply(gone));
    var registry = new MemoryPersonStore(result.content());
    if (!registry.problems().isEmpty()) {
      throw new IllegalStateException("The merge is inconsistent, nothing written: " + registry.problems().subList(0,
        Math.min(20, registry.problems().size())));
    }
    PersonFiles.write(dir, result.content());

    StringBuilder sb = new StringBuilder("# Person harvest\n\n");
    sb.append(String.format("persons %,d, names %,d, relations %,d%n", result.content().persons().size(),
      result.content().names().size(), result.content().relations().size()));
    sb.append(result.report().render()).append('\n').append(stats);
    unresolvedAuthorMapRows(registry, sb);
    return sb.toString();
  }

  /**
   * Author map rows none of whose forms name a person: hand edits worth keeping become curated lines.
   */
  private static void unresolvedAuthorMapRows(MemoryPersonStore registry, StringBuilder sb) {
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
          if (!registry.candidates(row[i], c).isEmpty()) {
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
    unresolved.stream().limit(UNRESOLVED_LIMIT).forEach(r -> sb.append("  ").append(r).append('\n'));
  }
}
