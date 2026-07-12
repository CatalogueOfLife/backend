package life.catalogue.common.tax.authormap;

import java.nio.file.*;
import java.util.*;

/**
 * Developer tool (NOT part of the app or test suite) that regenerates
 * api/src/main/resources/authorship/authormap.txt from multiple sources.
 *
 * Run from the module root, e.g. from the IDE main, or:
 *   mvn -q -pl api exec:java -Dexec.classpathScope=test \
 *       -Dexec.mainClass=life.catalogue.common.tax.authormap.AuthorMapGenerator \
 *       -Dexec.args="api/src/main/resources/authorship"
 *
 * Precedence (highest first): manual > existing(IPNI) > wikidata > dumps.
 */
public class AuthorMapGenerator {

  public static void main(String[] args) throws Exception {
    Path dir = Paths.get(args.length > 0 ? args[0] : "api/src/main/resources/authorship");
    Path existing = dir.resolve("authormap.txt");
    Path manual = dir.resolve("authormap-manual.txt");

    List<AuthorSource> sources = new ArrayList<>();
    sources.add(TsvDumpSource.manual(manual));      // precedence 0 (highest, locked)
    sources.add(TsvDumpSource.existingMap(existing)); // precedence 1 (IPNI base + continuity)
    sources.add(new WikidataSource());              // precedence 2
    // Optional downloaded dumps: pass as extra args "ipni=/path" / "huh=/path"
    for (int i = 1; i < args.length; i++) {
      String[] kv = args[i].split("=", 2);
      if (kv.length == 2) {
        // IPNI/HUH dump columns: standardForm, abbreviation, fullName  (adjust to the actual dump)
        sources.add(TsvDumpSource.dump(kv[0], Paths.get(kv[1]), 0, -1, AuthorCode.BOT, 1, 2));
      }
    }

    // snapshot the current file for the diff before overwriting
    List<AuthorEntry> before = Files.exists(existing) ? AuthorMapIO.read(existing) : List.of();

    List<List<AuthorEntry>> read = new ArrayList<>();
    for (AuthorSource s : sources) {
      List<AuthorEntry> e = s.read();
      System.out.printf("source %-10s : %d entries%n", s.name(), e.size());
      read.add(e);
    }

    List<AuthorEntry> merged = AuthorMapMerger.merge(read, 2);
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
