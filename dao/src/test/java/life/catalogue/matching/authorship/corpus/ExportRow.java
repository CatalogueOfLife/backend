package life.catalogue.matching.authorship.corpus;

import org.gbif.nameparser.api.NomCode;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * One name of the export written by <code>author-corpus-export.sql</code>.
 * {@link #COLUMNS} is the contract between that script and everything that reads its output.
 */
public record ExportRow(
  int nidx,
  int datasetKey,
  String nameId,
  String rank,
  @Nullable NomCode code,
  @Nullable String nomStatus,
  String scientificName,
  String authorship,
  List<String> combAuthors,
  List<String> combExAuthors,
  @Nullable String combYear,
  List<String> basAuthors,
  List<String> basExAuthors,
  @Nullable String basYear,
  @Nullable String sanctioningAuthor
) {
  public static final List<String> COLUMNS = List.of(
    "index_id", "dataset_key", "name_id", "rank", "code", "nom_status", "scientific_name", "authorship",
    "combination_authors", "combination_ex_authors", "combination_year",
    "basionym_authors", "basionym_ex_authors", "basionym_year", "sanctioning_author"
  );
  /** separates the authors of a team, the export script drops every name that holds one */
  public static final char TEAM_SEPARATOR = '|';

  /**
   * @param row the columns of one line, trailing empty ones may be missing
   */
  public static ExportRow of(String[] row) {
    return new ExportRow(
      Integer.parseInt(col(row, 0)),
      Integer.parseInt(col(row, 1)),
      col(row, 2),
      col(row, 3),
      col(row, 4) == null ? null : NomCode.valueOf(col(row, 4)),
      col(row, 5),
      col(row, 6),
      col(row, 7),
      team(col(row, 8)),
      team(col(row, 9)),
      col(row, 10),
      team(col(row, 11)),
      team(col(row, 12)),
      col(row, 13),
      col(row, 14)
    );
  }

  /**
   * The inverse of {@link #of(String[])}.
   */
  public String[] toRow() {
    return new String[]{
      String.valueOf(nidx), String.valueOf(datasetKey), nameId, rank, code == null ? null : code.name(), nomStatus,
      scientificName, authorship,
      team(combAuthors), team(combExAuthors), combYear,
      team(basAuthors), team(basExAuthors), basYear, sanctioningAuthor
    };
  }

  static String col(String[] row, int idx) {
    return idx < row.length ? StringUtils.trimToNull(row[idx]) : null;
  }

  static List<String> team(@Nullable String authors) {
    return authors == null ? List.of() : List.of(StringUtils.split(authors, TEAM_SEPARATOR));
  }

  static String team(List<String> authors) {
    return authors.isEmpty() ? null : String.join(String.valueOf(TEAM_SEPARATOR), authors);
  }
}
