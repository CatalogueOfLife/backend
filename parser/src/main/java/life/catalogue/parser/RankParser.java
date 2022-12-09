package life.catalogue.parser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

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
      add(r.getPlural(), r);
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
    var rank = super.parse(value);
    if (code != NomCode.ZOOLOGICAL) {
      Optional<Rank> mapped = rank.map(BOTANY_MAP::get);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    Rank r = rank.orElse(null);
    return Optional.ofNullable(r);
  }

}
