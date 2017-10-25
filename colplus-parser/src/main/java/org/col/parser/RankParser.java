package org.col.parser;

import org.col.api.vocab.Rank;
import org.col.api.vocab.VocabularyUtils;

/**
 *
 */
public class RankParser extends EnumParser<Rank> {
  public static final RankParser PARSER = new RankParser();

  public RankParser() {
    super("rank.csv", Rank.class);
    for (Rank r : Rank.values()) {
      add(r.getMarker(), r);
    }
  }

  public static org.gbif.api.vocabulary.Rank convertToGbif(Rank rank) {
    return VocabularyUtils.convertEnum(org.gbif.api.vocabulary.Rank.class, rank);
  }

}
