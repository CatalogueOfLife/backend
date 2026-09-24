package life.catalogue.matching.authorship.corpus;

import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;
import life.catalogue.matching.authorship.corpus.LabelRules.Source;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * A labelled pair of author keys with one pair of names from the corpus that shows it.
 * Side A is the key that sorts first.
 *
 * @param weight number of names backing the label
 * @param code   the nomenclatural code of the names, taken from the whole group if these two do not have one
 * @param group  the taxonomic group of the two names, see {@link #commonGroup}; null if unknown or disparate
 */
public record AuthorPair(
  Label label, Source source, int weight, PairStat stat,
  @Nullable NomCode code, String rank, int nidx, String scientificName,
  String keyA, String keyB, Side a, Side b, @Nullable TaxGroup group
) {
  private static final List<String> SIDE_COLUMNS = List.of(
    "datasetKey", "nameId", "authorship", "combAuthors", "combEx", "combYear", "basAuthors", "basEx", "basYear", "sanctioning"
  );
  public static final List<String> COLUMNS = Stream.of(
    Stream.of("label", "source", "weight", "support", "yearAgree", "yearConflict", "freqA", "freqB",
      "intraYearDiff", "intraNoYear", "intraYearAgree", "code", "rank", "nidx", "scientificName", "keyA", "keyB"),
    SIDE_COLUMNS.stream().map(c -> c + "A"),
    SIDE_COLUMNS.stream().map(c -> c + "B"),
    Stream.of("group")
  ).flatMap(s -> s).toList();
  /** the columns of a pairs file from before the group, which still reads */
  public static final List<String> COLUMNS_WITHOUT_GROUP = COLUMNS.subList(0, COLUMNS.size() - 1);

  /**
   * @return the group both names belong to: the broader of two nested groups, the one known if the other is not, null
   *         for disparate ones
   */
  @Nullable
  public static TaxGroup commonGroup(@Nullable TaxGroup a, @Nullable TaxGroup b) {
    if (a == null) return b;
    if (b == null) return a;
    if (a.contains(b)) return a;
    if (b.contains(a)) return b;
    return null;
  }

  /**
   * One name of a pair with everything needed to rebuild its parsed authorship without a name parser.
   */
  public record Side(int datasetKey, String nameId, String authorship,
                     List<String> combAuthors, List<String> combExAuthors, @Nullable String combYear,
                     List<String> basAuthors, List<String> basExAuthors, @Nullable String basYear,
                     @Nullable String sanctioningAuthor) {

    static Side of(ExportRow r) {
      return new Side(r.datasetKey(), r.nameId(), r.authorship(), r.combAuthors(), r.combExAuthors(), r.combYear(),
        r.basAuthors(), r.basExAuthors(), r.basYear(), r.sanctioningAuthor());
    }

    /**
     * @param withYears false to leave the years out, which isolates the comparison of the authors
     */
    public Name toName(@Nullable NomCode code, boolean withYears) {
      Name n = new Name();
      n.setCode(code);
      n.setCombinationAuthorship(authorship(combAuthors, combExAuthors, withYears ? combYear : null));
      n.setBasionymAuthorship(authorship(basAuthors, basExAuthors, withYears ? basYear : null));
      n.setSanctioningAuthor(sanctioningAuthor);
      return n;
    }

    private static Authorship authorship(List<String> authors, List<String> exAuthors, @Nullable String year) {
      Authorship a = new Authorship();
      a.setAuthors(new ArrayList<>(authors));
      a.setExAuthors(new ArrayList<>(exAuthors));
      a.setYear(year);
      return a;
    }

    private void addTo(List<String> row) {
      row.add(String.valueOf(datasetKey));
      row.add(nameId);
      row.add(authorship);
      row.add(ExportRow.team(combAuthors));
      row.add(ExportRow.team(combExAuthors));
      row.add(combYear);
      row.add(ExportRow.team(basAuthors));
      row.add(ExportRow.team(basExAuthors));
      row.add(basYear);
      row.add(sanctioningAuthor);
    }

    private static Side of(String[] row, int offset) {
      return new Side(Integer.parseInt(ExportRow.col(row, offset)), ExportRow.col(row, offset + 1), ExportRow.col(row, offset + 2),
        ExportRow.team(ExportRow.col(row, offset + 3)), ExportRow.team(ExportRow.col(row, offset + 4)), ExportRow.col(row, offset + 5),
        ExportRow.team(ExportRow.col(row, offset + 6)), ExportRow.team(ExportRow.col(row, offset + 7)), ExportRow.col(row, offset + 8),
        ExportRow.col(row, offset + 9));
    }
  }

  /**
   * @return the key of a pair across files: code and both author keys
   */
  public String id() {
    return (code == null ? "" : code.name()) + " " + keyA + " " + keyB;
  }

  String[] toRow() {
    List<String> row = new ArrayList<>(COLUMNS.size());
    row.add(label.name());
    row.add(source.name());
    row.add(String.valueOf(weight));
    row.add(String.valueOf(stat.support()));
    row.add(String.valueOf(stat.yearAgree()));
    row.add(String.valueOf(stat.yearConflict()));
    row.add(String.valueOf(stat.freqA()));
    row.add(String.valueOf(stat.freqB()));
    row.add(String.valueOf(stat.intraYearDiff()));
    row.add(String.valueOf(stat.intraNoYear()));
    row.add(String.valueOf(stat.intraYearAgree()));
    row.add(code == null ? null : code.name());
    row.add(rank);
    row.add(String.valueOf(nidx));
    row.add(scientificName);
    row.add(keyA);
    row.add(keyB);
    a.addTo(row);
    b.addTo(row);
    row.add(group == null ? null : group.name());
    return row.toArray(new String[0]);
  }

  static AuthorPair of(String[] row) {
    PairStat stat = new PairStat(num(row, 3), num(row, 4), num(row, 5), num(row, 6), num(row, 7), num(row, 8), num(row, 9), num(row, 10));
    String code = ExportRow.col(row, 11);
    return new AuthorPair(
      Label.valueOf(ExportRow.col(row, 0)), Source.valueOf(ExportRow.col(row, 1)), num(row, 2), stat,
      code == null ? null : NomCode.valueOf(code), ExportRow.col(row, 12), num(row, 13), ExportRow.col(row, 14),
      // a key starts with its slot separators, which must not be trimmed away
      row[15], row[16],
      Side.of(row, 17), Side.of(row, 17 + SIDE_COLUMNS.size()), group(ExportRow.col(row, COLUMNS.size() - 1))
    );
  }

  @Nullable
  private static TaxGroup group(@Nullable String name) {
    return name == null ? null : TaxGroup.valueOf(name);
  }

  private static int num(String[] row, int idx) {
    return Integer.parseInt(ExportRow.col(row, idx));
  }
}
