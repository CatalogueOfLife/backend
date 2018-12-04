package org.col.es.translate;

import java.util.EnumSet;
import java.util.Set;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.es.query.AutoCompleteQuery;
import org.col.es.query.BoolQuery;
import org.col.es.query.CaseInsensitivePrefixQuery;
import org.col.es.query.PrefixQuery;
import org.col.es.query.Query;

import static org.col.api.search.NameSearchRequest.SearchContent.AUTHORSHIP;
import static org.col.api.search.NameSearchRequest.SearchContent.VERNACULAR_NAME;
import static org.col.common.util.CollectionUtils.isEmpty;
import static org.col.es.NameUsageTransfer.normalizeStrongly;
import static org.col.es.NameUsageTransfer.normalizeWeakly;

/**
 * Translates the "q" request parameter into an Elasticsearch query.
 */
class QTranslator {

  // TODO: Maybe one day pick this up dynamically from es-settings.json
  private static int MAX_NGRAM_SIZE = 12;

  private static float DFAULT_SCI_NAME_BOOST = 1.2F;
  private static float DFAULT_AUTHORSHIP_BOOST = 1.0F;
  private static float DFAULT_VERNACULAR_BOOST = 1.0F;

  private final NameSearchRequest request;

  QTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Query translate() {
    Set<SearchContent> content = request.getContent();
    if (isEmpty(content)) {
      content = EnumSet.allOf(SearchContent.class);
    }
    if (content.size() == 1) {
      return content.stream().map(this::translate).findFirst().orElse(null);
    }
    return content.stream().map(this::translate).collect(BoolQuery::new, BoolQuery::should, BoolQuery::should);
  }

  private Query translate(SearchContent sc) {
    // Either an AutoCompleteQuery or a CaseInsensitivePrefixQuery is going to be executed. Both use lowercasing tokenizers.
    String q = request.getQ().toLowerCase();
    if (sc == AUTHORSHIP) {
      if (q.length() <= MAX_NGRAM_SIZE) {
        return new AutoCompleteQuery("authorship", q, DFAULT_AUTHORSHIP_BOOST);
      }
      return new CaseInsensitivePrefixQuery("authorship", q, DFAULT_AUTHORSHIP_BOOST);
    }
    if (sc == VERNACULAR_NAME) {
      if (q.length() <= MAX_NGRAM_SIZE) {
        return new AutoCompleteQuery("vernacularNames", q, DFAULT_VERNACULAR_BOOST);
      }
      return new CaseInsensitivePrefixQuery("vernacularNames", q, DFAULT_VERNACULAR_BOOST);
    }
    return getSciNameQuery(q);
  }

  /*
   * A match between a weakly normalized q and the weakly normalized scientific name in a name usage document must always rate at least as
   * high as a match between a strongly normalized q and the strongly normalized scientific name. The former implies the latter, but not
   * vice versa. Multiple weakly normalized strings lead to the same strongly normalized string, but not vice versa. However, as the user
   * types more letters, the difference in the ratings should decrease: to assume the user means "ra" when he/she types "rus" is an
   * aggressive interpretation. But to assume the user means "lara fusca" (strongly normalized version of Larus fuscus) when he types
   * "Larus fusca" is not. Thus, keeping the boost for the weakly normalized match at the default boost, the boost for the strongly
   * normalized match should start out lower and approach it as the length of the search string increases.
   */
  private static Query getSciNameQuery(String q) {
    String weaklyNormed = normalizeWeakly(q);
    String stronglyNormed = normalizeStrongly(q);
    int len = q.length();
    if (weaklyNormed.equals(stronglyNormed)) {
      if (len <= MAX_NGRAM_SIZE) {
        return new AutoCompleteQuery("scientificNameWN", weaklyNormed, DFAULT_SCI_NAME_BOOST);
      }
      return new PrefixQuery("scientificNameWN", weaklyNormed, DFAULT_SCI_NAME_BOOST);
    }
    if (len < 4) {
      float strongBoost = 0.4F;
      return new BoolQuery()
          .should(new AutoCompleteQuery("scientificNameWN", weaklyNormed, DFAULT_SCI_NAME_BOOST))
          .should(new AutoCompleteQuery("scientificNameSN", stronglyNormed, strongBoost));
    }
    if (len >= 4 && len < 9) {
      float strongBoost = 0.8F;
      return new BoolQuery()
          .should(new AutoCompleteQuery("scientificNameWN", weaklyNormed, DFAULT_SCI_NAME_BOOST))
          .should(new AutoCompleteQuery("scientificNameSN", stronglyNormed, strongBoost));
    }
    if (len >= 8 && len < MAX_NGRAM_SIZE) {
      float strongBoost = DFAULT_SCI_NAME_BOOST;
      return new BoolQuery()
          .should(new AutoCompleteQuery("scientificNameWN", weaklyNormed, DFAULT_SCI_NAME_BOOST))
          .should(new AutoCompleteQuery("scientificNameSN", stronglyNormed, strongBoost));
    }
    // Do we even need both beyond this point?
    float strongBoost = DFAULT_SCI_NAME_BOOST;
    return new BoolQuery()
        .should(new CaseInsensitivePrefixQuery("scientificNameWN", weaklyNormed, DFAULT_SCI_NAME_BOOST))
        .should(new CaseInsensitivePrefixQuery("scientificNameSN", stronglyNormed, strongBoost));
  }

}
