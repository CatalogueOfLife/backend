package life.catalogue.common.tax.authormap;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class TsvDumpSource implements AuthorSource {
  private final String name;
  private final Path file;
  private final int canonicalCol;
  private final int codeCol;          // -1 = use defaultCode
  private final AuthorCode defaultCode;
  private final int[] aliasCols;      // empty = all columns except canonical/code

  private TsvDumpSource(String name, Path file, int canonicalCol, int codeCol, AuthorCode defaultCode, int[] aliasCols) {
    this.name = name; this.file = file; this.canonicalCol = canonicalCol;
    this.codeCol = codeCol; this.defaultCode = defaultCode; this.aliasCols = aliasCols;
  }

  /** The committed manual curation file, format: canonical <tab> code <tab> aliases… */
  public static TsvDumpSource manual(Path file) {
    return new TsvDumpSource("manual", file, 0, 1, AuthorCode.ANY, new int[0]);
  }

  /** The current authormap.txt (our IPNI base), format: canonical <tab> code <tab> aliases… */
  public static TsvDumpSource existingMap(Path file) {
    return new TsvDumpSource("existing", file, 0, 1, AuthorCode.BOT, new int[0]);
  }

  /** A downloaded IPNI/HUH TSV dump with fixed columns and no code column. */
  public static TsvDumpSource dump(String name, Path file, int canonicalCol, int codeCol, AuthorCode defaultCode, int... aliasCols) {
    return new TsvDumpSource(name, file, canonicalCol, codeCol, defaultCode, aliasCols);
  }

  @Override public String name() { return name; }

  @Override public List<AuthorEntry> read() throws Exception {
    List<AuthorEntry> out = new ArrayList<>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (line.isBlank()) continue;
      String[] c = line.split("\t");
      if (c.length <= canonicalCol) continue;
      AuthorCode code = codeCol >= 0 && codeCol < c.length
        ? AuthorCode.valueOf(c[codeCol].trim().toUpperCase()) : defaultCode;
      List<String> aliases = new ArrayList<>();
      if (aliasCols.length == 0) {
        for (int i = 0; i < c.length; i++) {
          if (i == canonicalCol || i == codeCol) continue;
          if (!c[i].isBlank()) aliases.add(c[i]);
        }
      } else {
        for (int i : aliasCols) if (i < c.length && !c[i].isBlank()) aliases.add(c[i]);
      }
      if (!aliases.isEmpty()) out.add(new AuthorEntry(c[canonicalCol], code, aliases));
    }
    return out;
  }
}
