package life.catalogue.matching.authorship.corpus;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.common.io.TabWriter;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Parses the names of an export again with the name parser on the classpath and rewrites their parsed author columns.
 * The prod export holds the parse of the day each dataset was imported, defects fixed since included
 * (gbif/name-parser-rust#20 to #22), so without this a corpus measures old parsers as much as the author comparison.
 * <p>
 * It parses the way an import does, name and authorship separately. A name the export script would not have exported
 * with today's parse - not scientific, no author, an author holding the team separator - is dropped.
 * <pre>
 * mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx4g --enable-native-access=ALL-UNNAMED -cp %classpath life.catalogue.matching.authorship.corpus.CorpusReparser \
 *     /path/to/author-corpus.tsv.gz target/author-corpus/reparsed/author-corpus.tsv.gz"
 * </pre>
 */
public class CorpusReparser {
  private static final int ROWS = 0;
  private static final int CHANGED = 1;
  private static final int DROPPED = 2;

  private final NameParser parser;
  private final Map<Integer, long[]> byDataset = new TreeMap<>();

  public CorpusReparser(NameParser parser) {
    this.parser = parser;
  }

  /**
   * @return the row with the parse of today, or null if the export script would not export it
   */
  @Nullable
  public ExportRow reparse(ExportRow row) {
    Name n = new Name();
    n.setScientificName(row.scientificName());
    n.setAuthorship(row.authorship());
    n.setRank(rank(row.rank()));
    n.setCode(row.code());
    Name p = parser.parse(n, IssueContainer.VOID).map(ParsedNameUsage::getName).orElse(null);
    if (p == null || p.getType() != NameType.SCIENTIFIC) {
      return null;
    }
    Authorship comb = p.getCombinationAuthorship();
    Authorship bas = p.getBasionymAuthorship();
    if (comb.getAuthors().isEmpty() && bas.getAuthors().isEmpty()) {
      return null;
    }
    if (Stream.of(comb.getAuthors(), comb.getExAuthors(), bas.getAuthors(), bas.getExAuthors())
        .flatMap(List::stream)
        .anyMatch(a -> a.indexOf(ExportRow.TEAM_SEPARATOR) >= 0)) {
      return null;
    }
    return new ExportRow(row.nidx(), row.datasetKey(), row.nameId(), row.rank(), row.code(), row.nomStatus(),
      row.scientificName(), row.authorship(),
      List.copyOf(comb.getAuthors()), List.copyOf(comb.getExAuthors()), comb.getYear(),
      List.copyOf(bas.getAuthors()), List.copyOf(bas.getExAuthors()), bas.getYear(),
      p.getSanctioningAuthor()
    );
  }

  private static Rank rank(String rank) {
    try {
      return Rank.valueOf(rank);
    } catch (IllegalArgumentException | NullPointerException e) {
      return Rank.UNRANKED;
    }
  }

  /**
   * @return the statistics per dataset: rows read, rows whose parse changed and rows dropped
   */
  public String reparse(File export, File out) throws IOException {
    try (TabWriter writer = CorpusIO.exportWriter(out)) {
      CorpusIO.readGroups(export, group -> {
        for (ExportRow row : group) {
          long[] counts = byDataset.computeIfAbsent(row.datasetKey(), k -> new long[3]);
          counts[ROWS]++;
          ExportRow r = reparse(row);
          if (r == null) {
            counts[DROPPED]++;
            continue;
          }
          if (!r.equals(row)) {
            counts[CHANGED]++;
          }
          try {
            writer.write(r.toRow());
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      });
    }
    CorpusIO.writeParser(out, NameParserVersion.get());
    return render();
  }

  private String render() {
    StringBuilder sb = new StringBuilder();
    sb.append("name parser: ").append(NameParserVersion.get()).append('\n');
    sb.append(String.format("%-8s %12s %12s %12s%n", "dataset", "rows", "changed", "dropped"));
    long[] all = new long[3];
    byDataset.forEach((key, c) -> {
      sb.append(String.format("%-8d %,12d %,12d %,12d%n", key, c[ROWS], c[CHANGED], c[DROPPED]));
      for (int i = 0; i < 3; i++) {
        all[i] += c[i];
      }
    });
    sb.append(String.format("%-8s %,12d %,12d %,12d%n", "all", all[ROWS], all[CHANGED], all[DROPPED]));
    return sb.toString();
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2 || !new File(args[0]).isFile()) {
      System.err.println("Usage: CorpusReparser <export.tsv[.gz]> <reparsed export.tsv[.gz]>");
      System.exit(1);
    }
    File out = new File(args[1]);
    String stats = new CorpusReparser(NameParser.PARSER).reparse(new File(args[0]), out);
    System.out.println(stats);
    Files.writeString(new File(out.getAbsoluteFile().getParentFile(), "reparse-stats.txt").toPath(), stats, StandardCharsets.UTF_8);
  }
}
