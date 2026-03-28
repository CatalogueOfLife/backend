package life.catalogue.es.suggest;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.query.QTranslator;

import org.gbif.nameparser.api.Rank;

import co.elastic.clients.elasticsearch._types.query_dsl.*;

public class QSuggestTranslater extends QTranslator {
  private static final String FLD_WORD = "usage.name.scientificName.word";
  /** Factor for reciprocal rank boost: score contribution ≈ RANK_FACTOR / rank.ordinal().
   *  At SPECIES (81) this yields ~1.23; at SUBSPECIES (85) ~1.18; at GENUS (70) ~1.43. */
  private static final double RANK_FACTOR = 100.0;

  public QSuggestTranslater(NameUsageRequest request) {
    super(request);
  }

  /**
   * Phrase-prefix match on the search_as_you_type word field.
   * ES automatically routes match_phrase_prefix through the _index_prefix sub-field for
   * O(1) performance regardless of prefix length — no max_expansions needed.
   * Phrase semantics ensure all tokens are required in order, so "Phoca vit" cannot
   * match the genus "Phoca" alone.
   * Scoring ignores BM25 (boostMode=Replace) and instead sums:
   *   - accepted boost (weight 1.1 for accepted names)
   *   - rank boost   (reciprocal of rank ordinal × RANK_FACTOR — higher ranks score higher)
   */
  @Override
  public Query buildSciNameQuery() {
    return Query.of(q -> q.functionScore(fs -> fs
        .query(inner -> inner.matchPhrasePrefix(m -> m
        .field(FLD_WORD)
        .query(request.getQ())
      ))
      .functions(f -> f
        .filter(fq -> fq.term(t -> t
          .field("usage.status")
          .value(TaxonomicStatus.ACCEPTED.name())
        ))
        .weight(1.1)
      )
      .functions(f -> f
        .fieldValueFactor(fvf -> fvf
          .field("usage.name.rank")
          .factor(RANK_FACTOR)
          .modifier(FieldValueFactorModifier.Reciprocal)
          .missing((double) Rank.UNRANKED.ordinal())
        )
      )
      .scoreMode(FunctionScoreMode.Sum)
      .boostMode(FunctionBoostMode.Replace)
    ));
  }
}
