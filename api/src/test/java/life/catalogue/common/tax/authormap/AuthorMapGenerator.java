package life.catalogue.common.tax.authormap;

import java.nio.file.*;
import java.util.*;

/**
 * Developer tool (NOT part of the app or test suite) that grows
 * api/src/main/resources/authorship/authormap.txt with authors from Wikidata.
 *
 * Run from the repository root, e.g. from the IDE main, or:
 *   mvn -q -pl api exec:java -Dexec.classpathScope=test \
 *       -DmainClass=life.catalogue.common.tax.authormap.AuthorMapGenerator \
 *       -Dexec.args="api/src/main/resources/authorship"
 *
 * Precedence (highest first): existing map > wikidata. The map itself is curated in place,
 * so every hand correction in it survives a regeneration.
 */
public class AuthorMapGenerator {

  public static void main(String[] args) throws Exception {
    Path dir = Paths.get(args.length > 0 ? args[0] : "api/src/main/resources/authorship");
    Path existing = dir.resolve("authormap.txt");

    // snapshot the current file for the diff before overwriting, it is also the curated source
    List<AuthorEntry> before = Files.exists(existing) ? AuthorMapIO.read(existing) : List.of();
    System.out.printf("source %-10s : %d entries%n", "existing", before.size());

    AuthorSource wikidata = new WikidataSource();
    List<AuthorEntry> wd = wikidata.read();
    System.out.printf("source %-10s : %d entries%n", wikidata.name(), wd.size());

    List<AuthorEntry> merged = AuthorMapMerger.merge(List.of(before, wd), 1);
    System.out.printf("merged            : %d entries%n", merged.size());

    AuthorMapDiff.Result diff = AuthorMapDiff.diff(before, merged);
    Path report = dir.resolve("authormap-diff-report.txt");
    Files.writeString(report, AuthorMapDiff.render(diff));
    System.out.printf("removed canonicals: %d, removed alias keys: %d -> %s%n",
      diff.removedCanonicals().size(), diff.removedAliasKeys().size(), report);

    AuthorMapIO.write(existing, merged);
    System.out.println("wrote " + existing);
  }
}
