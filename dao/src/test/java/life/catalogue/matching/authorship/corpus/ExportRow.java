package life.catalogue.matching.authorship.corpus;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.TaxGroupAnalyzer;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * One name of the export written by <code>author-corpus-export.sql</code>.
 * {@link #COLUMNS} is the contract between that script and everything that reads its output.
 *
 * @param classification the names of the higher taxa of the name's taxon, root first, none below a suprageneric name:
 *                       what {@link #group()} is derived from. Empty in an export from before the column.
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
  @Nullable String sanctioningAuthor,
  List<String> classification
) {
  public static final List<String> COLUMNS = List.of(
    "index_id", "dataset_key", "name_id", "rank", "code", "nom_status", "scientific_name", "authorship",
    "combination_authors", "combination_ex_authors", "combination_year",
    "basionym_authors", "basionym_ex_authors", "basionym_year", "sanctioning_author", "classification"
  );
  /** the columns of an export from before the classification column, which still reads */
  public static final List<String> COLUMNS_WITHOUT_CLASSIFICATION = COLUMNS.subList(0, COLUMNS.size() - 1);
  /** separates the authors of a team, the export script drops every name that holds one */
  public static final char TEAM_SEPARATOR = '|';
  private static final TaxGroupAnalyzer GROUP_ANALYZER = new TaxGroupAnalyzer();
  /**
   * The group of every name of a dataset of one group, for its names without a classification: nomenclators hold bare
   * names that have no taxon. ZooBank registers protists besides animals, so it only tells eukaryotes.
   */
  static final Map<Integer, TaxGroup> DATASET_GROUPS = Map.of(
    2003, TaxGroup.Algae,       // Index Nominum Algarum
    2037, TaxGroup.Eukaryotes,  // ZooBank
    2073, TaxGroup.Fungi        // Species Fungorum Plus
  );

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
      col(row, 14),
      team(col(row, 15))
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
      team(basAuthors), team(basExAuthors), basYear, sanctioningAuthor, team(classification)
    };
  }

  /**
   * @return the taxonomic group of the name as the matching derives it, from the classification, the name itself and
   *         its code, or for a name without a classification the group of its dataset if it has one; null if none can
   *         be told
   */
  @Nullable
  public TaxGroup group() {
    if (classification.isEmpty() && DATASET_GROUPS.containsKey(datasetKey)) {
      return DATASET_GROUPS.get(datasetKey);
    }
    Rank r;
    try {
      r = Rank.valueOf(rank);
    } catch (IllegalArgumentException | NullPointerException e) {
      r = null;
    }
    return GROUP_ANALYZER.analyzeNames(SimpleName.sn(r, code, scientificName, null), classification);
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
