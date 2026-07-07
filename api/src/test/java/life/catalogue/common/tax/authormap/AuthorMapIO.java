package life.catalogue.common.tax.authormap;

import life.catalogue.common.io.Resources;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class AuthorMapIO {

  public static List<AuthorEntry> read(Path tsv) throws IOException {
    try (var lines = Files.lines(tsv, StandardCharsets.UTF_8)) {
      return parse(lines.collect(Collectors.toList()));
    }
  }

  public static List<AuthorEntry> readResource(String name) {
    List<String> lines = Resources.lines(name).collect(Collectors.toList());
    return parse(lines);
  }

  private static List<AuthorEntry> parse(List<String> lines) {
    List<AuthorEntry> out = new ArrayList<>();
    for (String line : lines) {
      if (line.isBlank()) continue;
      String[] c = line.split("\t");
      if (c.length < 3) continue;
      AuthorCode code = AuthorCode.valueOf(c[1].trim().toUpperCase());
      List<String> aliases = new ArrayList<>(Arrays.asList(c).subList(2, c.length));
      out.add(new AuthorEntry(c[0], code, aliases));
    }
    return out;
  }

  public static void write(Path tsv, List<AuthorEntry> entries) throws IOException {
    List<AuthorEntry> sorted = new ArrayList<>(entries);
    sorted.sort(Comparator.comparing(AuthorEntry::canonical, String.CASE_INSENSITIVE_ORDER)
                          .thenComparing(e -> e.code().name()));
    try (BufferedWriter w = Files.newBufferedWriter(tsv, StandardCharsets.UTF_8)) {
      for (AuthorEntry e : sorted) {
        w.write(e.canonical());
        w.write('\t');
        w.write(e.code().name());
        for (String a : e.aliases()) { w.write('\t'); w.write(a); }
        w.write('\n');
      }
    }
  }
}
