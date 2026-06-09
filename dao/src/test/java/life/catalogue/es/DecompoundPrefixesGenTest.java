package life.catalogue.es;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Merges the per-source prefix files under {@code life/catalogue/es/prefix-sources/} into the single
 * {@code sciname-decompound-prefixes.txt} consumed by the ES schema loader, and guards that the
 * committed merged file stays in sync with its sources.
 *
 * <p>Sources stay pristine and re-importable; curation happens in {@code prefix-sources/_exclude.txt}.
 * Files whose name starts with {@code _} are not treated as prefix sources. {@code .tsv} sources
 * contribute their first (tab-separated) column.</p>
 *
 * <p>After editing a source or the exclude list, regenerate the committed file with:
 * <pre>mvn -pl dao test -Dtest=DecompoundPrefixesGenTest -Ddecompound.write=true</pre>
 * Changing the prefixes requires a reindex of the name-usage ES index.</p>
 */
public class DecompoundPrefixesGenTest {

  private static final String OUTPUT_FILE = "sciname-decompound-prefixes.txt";
  private static final String HEADER = """
    # GENERATED FILE - do not edit directly.
    # Merged from life/catalogue/es/prefix-sources/* (minus _exclude.txt) by DecompoundPrefixesGenTest.
    # Edit a source or prefix-sources/_exclude.txt and regenerate with:
    #   mvn -pl dao test -Dtest=DecompoundPrefixesGenTest -Ddecompound.write=true
    # Changing these prefixes requires a reindex of the name-usage ES index.
    #
    # Latin/Greek formation prefixes stripped from scientific-name tokens at index time.
    # Sorted alphabetically; the loader re-sorts length-desc for the regex.
    #
    """;

  @Test
  public void inSyncWithSources() throws Exception {
    Path esDir = esSrcDir();
    List<String> merged = merge(esDir);
    if (Boolean.getBoolean("decompound.write")) {
      write(esDir, merged);
      return;
    }
    List<String> committed = readPrefixLines(esDir.resolve(OUTPUT_FILE));
    assertEquals(OUTPUT_FILE + " is out of sync with prefix-sources/. Regenerate with: "
        + "mvn -pl dao test -Dtest=DecompoundPrefixesGenTest -Ddecompound.write=true",
      merged, committed);
  }

  /** Regenerates the committed merged file from the sources. */
  public static void main(String[] args) throws Exception {
    Path esDir = esSrcDir();
    write(esDir, merge(esDir));
    System.out.println("Regenerated " + esDir.resolve(OUTPUT_FILE));
  }

  /** Merge all (non-'_') source files, drop the exclude list, dedupe and sort alphabetically. */
  static List<String> merge(Path esDir) throws IOException {
    Path sourcesDir = esDir.resolve("prefix-sources");
    Set<String> exclude = readPrefixSet(sourcesDir.resolve("_exclude.txt"));
    TreeSet<String> prefixes = new TreeSet<>();
    try (var files = Files.list(sourcesDir)) {
      for (Path f : files.sorted().toList()) {
        String name = f.getFileName().toString();
        if (name.startsWith("_")) {
          continue; // _exclude.txt and other non-source files
        }
        boolean tsv = name.endsWith(".tsv");
        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
          String token = (tsv ? line.split("\t", 2)[0] : line).trim().toLowerCase(Locale.ROOT);
          if (token.isEmpty() || token.startsWith("#") || token.equals("prefix")) {
            continue; // blank, comment, or the TSV header column
          }
          if (token.matches("[a-z]+") && !exclude.contains(token)) {
            prefixes.add(token);
          }
        }
      }
    }
    return new ArrayList<>(prefixes);
  }

  private static void write(Path esDir, List<String> prefixes) throws IOException {
    StringBuilder sb = new StringBuilder(HEADER);
    prefixes.forEach(p -> sb.append(p).append('\n'));
    Files.writeString(esDir.resolve(OUTPUT_FILE), sb.toString(), StandardCharsets.UTF_8);
  }

  private static Set<String> readPrefixSet(Path f) throws IOException {
    return new HashSet<>(readPrefixLines(f));
  }

  private static List<String> readPrefixLines(Path f) throws IOException {
    List<String> out = new ArrayList<>();
    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
      String t = line.trim().toLowerCase(Locale.ROOT);
      if (!t.isEmpty() && !t.startsWith("#")) {
        out.add(t);
      }
    }
    return out;
  }

  /** Resolves {@code src/main/resources/life/catalogue/es} from the test classpath, cwd-independent. */
  private static Path esSrcDir() throws Exception {
    URL url = DecompoundPrefixesGenTest.class.getResource("/life/catalogue/es/prefix-sources");
    Path classes = Paths.get(url.toURI()); // .../<module>/target/classes/life/catalogue/es/prefix-sources
    Path p = classes;
    while (p != null && !p.getFileName().toString().equals("target")) {
      p = p.getParent();
    }
    if (p == null || p.getParent() == null) {
      throw new IllegalStateException("Could not locate module root from " + classes);
    }
    return p.getParent().resolve("src/main/resources/life/catalogue/es");
  }
}
