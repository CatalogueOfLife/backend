package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.TabReader;
import life.catalogue.common.io.TabWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

/**
 * Reads and writes the tab delimited files of the author corpus. Any file ending in <code>.gz</code> is gzipped.
 * Every file starts with a header that is verified, so a file of another kind or an outdated layout fails
 * instead of being read as something else.
 */
public class CorpusIO {
  public static final String EXPORT_SQL = "author-corpus/author-corpus-export.sql";

  private CorpusIO() {
  }

  static InputStream open(File f) throws IOException {
    InputStream in = new BufferedInputStream(new FileInputStream(f), 1 << 16);
    return f.getName().endsWith(".gz") ? new GZIPInputStream(in, 1 << 16) : in;
  }

  /**
   * Streams the export, handing over the consecutive rows that share a names index id and a rank.
   * The export is sorted by exactly that, so a group is complete when it is handed over.
   */
  public static void readGroups(File export, Consumer<List<ExportRow>> groupConsumer) throws IOException {
    try (TabReader reader = TabReader.tab(open(export), StandardCharsets.UTF_8, 0, 1)) {
      var iter = reader.iterator();
      verifyHeader(export, ExportRow.COLUMNS, iter.hasNext() ? iter.next() : new String[0]);

      List<ExportRow> group = new ArrayList<>();
      while (iter.hasNext()) {
        ExportRow row = ExportRow.of(iter.next());
        if (!group.isEmpty() && (group.get(0).nidx() != row.nidx() || !group.get(0).rank().equals(row.rank()))) {
          groupConsumer.accept(group);
          group = new ArrayList<>();
        }
        group.add(row);
      }
      if (!group.isEmpty()) {
        groupConsumer.accept(group);
      }
    }
  }

  static OutputStream create(File f) throws IOException {
    if (f.getParentFile() != null) {
      f.getParentFile().mkdirs();
    }
    OutputStream out = new BufferedOutputStream(new FileOutputStream(f), 1 << 16);
    // GZIPOutputStream writes no timestamp, so the same content gives the same bytes
    return f.getName().endsWith(".gz") ? new GZIPOutputStream(out, 1 << 16) : out;
  }

  /**
   * @return a writer that has written the given header already
   */
  static TabWriter writer(File f, List<String> header) throws IOException {
    TabWriter writer = TabWriter.fromStream(create(f));
    writer.setVerifyColumns(true);
    writer.write(header.toArray(new String[0]));
    return writer;
  }

  /**
   * Streams the rows below a verified header.
   */
  static void read(File f, List<String> header, Consumer<String[]> rowConsumer) throws IOException {
    try (TabReader reader = TabReader.tab(open(f), StandardCharsets.UTF_8, 0, 1)) {
      var iter = reader.iterator();
      verifyHeader(f, header, iter.hasNext() ? iter.next() : new String[0]);
      while (iter.hasNext()) {
        rowConsumer.accept(iter.next());
      }
    }
  }

  /**
   * The file next to a corpus file that names the parser its authors were parsed with. The re-parse writes it and the
   * miner passes it on, so a report can name the parser of its corpus - it parses nothing itself.
   */
  private static File parserFile(File data) {
    return new File(data.getAbsoluteFile().getParentFile(), data.getName() + ".parser");
  }

  public static void writeParser(File data, String parser) throws IOException {
    Files.writeString(parserFile(data).toPath(), parser + "\n", StandardCharsets.UTF_8);
  }

  /**
   * @return the parser a corpus file was parsed with, or null if it holds the parses of the imports
   */
  @Nullable
  public static String readParser(File data) throws IOException {
    File f = parserFile(data);
    return f.exists() ? Files.readString(f.toPath(), StandardCharsets.UTF_8).trim() : null;
  }

  public static TabWriter exportWriter(File export) throws IOException {
    return writer(export, ExportRow.COLUMNS);
  }

  public static TabWriter pairWriter(File pairs) throws IOException {
    return writer(pairs, AuthorPair.COLUMNS);
  }

  public static void readPairs(File pairs, Consumer<AuthorPair> pairConsumer) throws IOException {
    read(pairs, AuthorPair.COLUMNS, row -> pairConsumer.accept(AuthorPair.of(row)));
  }

  public static List<AuthorPair> readPairs(File pairs) throws IOException {
    List<AuthorPair> list = new ArrayList<>();
    readPairs(pairs, list::add);
    return list;
  }

  static void verifyHeader(File f, List<String> expected, String[] header) {
    if (!expected.equals(Arrays.asList(header))) {
      throw new IllegalArgumentException("Unexpected header in " + f
        + "\n  expected: " + expected + "\n  found:    " + Arrays.asList(header));
    }
  }
}
