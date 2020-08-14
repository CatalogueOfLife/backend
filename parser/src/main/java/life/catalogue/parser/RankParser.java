package life.catalogue.parser;

import com.google.common.collect.ImmutableMap;
import life.catalogue.api.util.VocabularyUtils;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 *
 */
public class RankParser extends EnumParser<Rank> {
  public static final RankParser PARSER = new RankParser();

  private static final Map<Rank, Rank> BOTANY_MAP = ImmutableMap.of(
    Rank.SUPERDIVISION, Rank.SUPERPHYLUM,
    Rank.DIVISION, Rank.PHYLUM,
    Rank.SUBDIVISION, Rank.SUBPHYLUM,
    Rank.INFRADIVISION, Rank.INFRAPHYLUM
  );

  public RankParser() {
    super("rank.csv", Rank.class);
    for (Rank r : Rank.values()) {
      add(r.getMarker(), r);
    }
  }

  /**
   * Better parsing method that takes into account the nomenclatural code.
   * Botanical Division ranks are converted into phyla.
   *
   * @param code
   * @param value
   */
  public Optional<Rank> parse(@Nullable NomCode code, String value) throws UnparsableException {
    Optional<Rank> rank = super.parse(value);
    if (code != NomCode.ZOOLOGICAL) {
      Optional<Rank> mapped = rank.map(BOTANY_MAP::get);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    return rank;
  }

  public static org.gbif.api.vocabulary.Rank convertToGbif(Rank rank) {
    return VocabularyUtils.convertEnum(org.gbif.api.vocabulary.Rank.class, rank);
  }

}
