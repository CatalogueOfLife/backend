package org.col.es.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.es.query.AutoCompleteQuery;
import org.col.es.query.BoolQuery;
import org.col.es.query.CaseInsensitivePrefixQuery;
import org.col.es.query.Query;

import static org.col.api.search.NameSearchRequest.SearchContent.AUTHORSHIP;
import static org.col.api.search.NameSearchRequest.SearchContent.VERNACULAR_NAME;
import static org.col.es.NameUsageTransfer.normalizeStrongly;
import static org.col.es.NameUsageTransfer.normalizeWeakly;

/**
 * Translates the "q" request parameter into an Elasticsearch query.
 */
class QTranslator {

  private static int MAX_NGRAM_SIZE = 12; // see es-settings.json

  private static float SCI_NAME_BOOST = 1.2F;
  private static float AUTHORSHIP_BOOST = 1.0F;
  private static float VERNACULAR_BOOST = 1.0F;

  private final NameSearchRequest request;

  QTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Query translate() {
    Set<SearchContent> content = request.getContent();
    List<Query> subqueries = new ArrayList<>(4);
    content.forEach(sc -> translate(subqueries, sc));
    if (subqueries.size() == 1) {
      return subqueries.get(0);
    }
    BoolQuery query = new BoolQuery();
    subqueries.forEach(query::should);
    return query;
  }

  private void translate(List<Query> subqueries, SearchContent sc) {
    // Either an AutoCompleteQuery or a CaseInsensitivePrefixQuery is going to be executed. Both use lowercasing tokenizers.
    String q = request.getQ().toLowerCase();
    if (sc == AUTHORSHIP) {
      subqueries.add(subquery("authorship", q, AUTHORSHIP_BOOST));
    } else if (sc == VERNACULAR_NAME) {
      subqueries.add(subquery("vernacularNames", q, VERNACULAR_BOOST));
    } else {
      addSciNameQuery(subqueries, q);
    }
  }

  /*
   * A match between a weakly normalized q and the weakly normalized scientific name in a name usage document should probably always rate at
   * least as high as a match between a strongly normalized q and the strongly normalized scientific name. The former implies the latter,
   * but not vice versa. However the difference should decrease as the user types more letters. To assume the user means "ra" when he/she
   * types "rus" is an aggressive interpretation. But to assume the user means "lara fusca" (strongly normalized version of Larus fuscus)
   * when he types "Larus fusca" is not. Thus, keeping the boost for the weakly normalized match at the default boost, the boost for the
   * strongly normalized match should start out lower and approach it as the length of the search string increases.
   */
  private static void addSciNameQuery(List<Query> subqueries, String q) {
    String weaklyNormed = normalizeWeakly(q);
    String stronglyNormed = normalizeStrongly(q);
    if (weaklyNormed.equals(stronglyNormed)) {
      subqueries.add(subquery("scientificNameWN", weaklyNormed, SCI_NAME_BOOST));
    } else {
      int len = q.length();
      // Determines how fast boost for strongly normalized matches creaps towards boost for weakly normalized matches
      int accelerator = 2;
      // Determines how far boost for strongly normalized matches may exceed boost for weakly normalized matches (possibly negative)
      float overshoot = 0;
      float strongBoost = Math.min(SCI_NAME_BOOST + overshoot, ((len + accelerator) / SCI_NAME_BOOST));
      subqueries.add(subquery("scientificNameWN", weaklyNormed, SCI_NAME_BOOST));
      subqueries.add(subquery("scientificNameSN", stronglyNormed, strongBoost));
    }
  }

  private static Query subquery(String field, String value, float boost) {
    if (value.length() < MAX_NGRAM_SIZE) {
      return new AutoCompleteQuery(field, value, boost);
    }
    return new CaseInsensitivePrefixQuery(field, value, boost);
  }

}
