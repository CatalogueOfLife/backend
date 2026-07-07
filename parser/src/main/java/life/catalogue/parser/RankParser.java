package life.catalogue.parser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

/**
 *
 */
public class RankParser extends EnumParser<Rank> {
  public static final RankParser PARSER = new RankParser();

  private static final Map<Rank, Rank> BOTANY_MAP = ImmutableMap.of(
    Rank.SUPERDIVISION, Rank.SUPERPHYLUM,
    Rank.DIVISION_ZOOLOGY, Rank.PHYLUM,
    Rank.SUBDIVISION, Rank.SUBPHYLUM,
    Rank.INFRADIVISION, Rank.INFRAPHYLUM,

    Rank.SUPERSECTION_ZOOLOGY, Rank.SUPERSECTION_BOTANY,
    Rank.SECTION_ZOOLOGY, Rank.SECTION_BOTANY,
    Rank.SUBSECTION_ZOOLOGY, Rank.SUBSECTION_BOTANY,

    Rank.SUPERSERIES_ZOOLOGY, Rank.SUPERSERIES_BOTANY,
    Rank.SERIES_ZOOLOGY, Rank.SERIES_BOTANY,
    Rank.SUBSERIES_ZOOLOGY, Rank.SUBSERIES_BOTANY
  );
  private static final Map<Rank, Rank> ZOOLOGY_MAP = ImmutableMap.of(
    Rank.SUPERSECTION_BOTANY, Rank.SUPERSECTION_ZOOLOGY,
    Rank.SECTION_BOTANY, Rank.SECTION_ZOOLOGY,
    Rank.SUBSECTION_BOTANY, Rank.SUBSECTION_ZOOLOGY,

    Rank.SUPERSERIES_BOTANY, Rank.SUPERSERIES_ZOOLOGY,
    Rank.SERIES_BOTANY, Rank.SERIES_ZOOLOGY,
    Rank.SUBSERIES_BOTANY, Rank.SUBSERIES_ZOOLOGY
  );

  /**
   * Values that already spell out their nomenclatural code (e.g. "zoodivisio", "section (botany)").
   * These are authoritative and must be returned as parsed, never re-mapped to the other code by
   * {@link #parse(NomCode, String)} — unlike the ambiguous bare markers ("div.", "section") which
   * follow the dataset code. Stored in their normalized form.
   */
  private final Set<String> codeExplicit = new HashSet<>();

  public RankParser() {
    super("rank.csv", Rank.class);
    for (Rank r : Rank.values()) {
      add(r.getMarker(), r);
      add(r.getPlural(), r);
    }
    // The "div." marker and "divisions" plural are shared by the suprageneric zoological
    // DIVISION_ZOOLOGY and the infrageneric botanical DIVISION_BOTANY. As DIVISION_BOTANY is
    // declared later it would win the loop above, but a standalone rank string carries no
    // positional context to justify the infrageneric reading. Default to the suprageneric
    // division (mapped to PHYLUM for botanical names via BOTANY_MAP, kept as DIVISION_ZOOLOGY
    // for zoological ones); the infrageneric botanical divisio is recognised positionally by
    // the name-parser, not here.
    add(Rank.DIVISION_ZOOLOGY.getMarker(), Rank.DIVISION_ZOOLOGY);
    add(Rank.DIVISION_BOTANY.getPlural(), Rank.DIVISION_ZOOLOGY);
    // keep in sync with the explicitly code-qualified entries in rank.csv
    for (String v : List.of("zoodivisio", "zoosectio", "zoosection", "section (zoology)", "section (botany)")) {
      codeExplicit.add(normalize(v));
    }
  }

  /**
   * Better parsing method that takes into account the nomenclatural code.
   * Ambiguous division and section/series ranks are resolved to their botanical or zoological
   * variant based on the code (botanical divisions become phyla). Values that already name their
   * code explicitly are returned verbatim.
   *
   * @param code
   * @param value
   */
  public Optional<Rank> parse(@Nullable NomCode code, String value) throws UnparsableException {
    var rank = super.parse(value);
    if (rank.isPresent() && codeExplicit.contains(normalize(value))) {
      return Optional.of(rank.get());
    }
    if (code == NomCode.ZOOLOGICAL) {
      Optional<Rank> mapped = rank.map(ZOOLOGY_MAP::get);
      if (mapped.isPresent()) {
        return mapped;
      }
    } else {
      Optional<Rank> mapped = rank.map(BOTANY_MAP::get);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    Rank r = rank.orElse(null);
    return Optional.ofNullable(r);
  }

}
