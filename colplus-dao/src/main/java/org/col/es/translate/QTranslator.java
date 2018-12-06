package org.col.es.translate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.es.query.AutoCompleteQuery;
import org.col.es.query.BoolQuery;
import org.col.es.query.CaseInsensitivePrefixQuery;
import org.col.es.query.Query;

import static java.util.Arrays.asList;

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

  private static float SCI_NAME_BOOST = 1.2F;
  private static float AUTHORSHIP_BOOST = 1.0F;
  private static float VERNACULAR_BOOST = 1.0F;

  private final NameSearchRequest request;

  QTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Query translate() {
    Set<SearchContent> content = request.getContent();
    if (isEmpty(content)) {
      content = EnumSet.allOf(SearchContent.class);
    }
    List<Query> subqueries = content.stream()
        .map(this::translate)
        .collect(ArrayList::new, ArrayList::addAll, ArrayList::addAll);
    if (subqueries.size() == 1) {
      return subqueries.get(0);
    }
    return subqueries.stream().collect(BoolQuery::new, BoolQuery::should, BoolQuery::should);
  }

  private List<Query> translate(SearchContent sc) {
    // Either an AutoCompleteQuery or a CaseInsensitivePrefixQuery is going to be executed. Both use lowercasing tokenizers.
    String q = request.getQ().toLowerCase();
    if (sc == AUTHORSHIP) {
      if (q.length() <= MAX_NGRAM_SIZE) {
        return asList(new AutoCompleteQuery("authorship", q, AUTHORSHIP_BOOST));
      }
      return asList(new CaseInsensitivePrefixQuery("authorship", q, AUTHORSHIP_BOOST));
    }
    if (sc == VERNACULAR_NAME) {
      if (q.length() <= MAX_NGRAM_SIZE) {
        return asList(new AutoCompleteQuery("vernacularNames", q, VERNACULAR_BOOST));
      }
      return asList(new CaseInsensitivePrefixQuery("vernacularNames", q, VERNACULAR_BOOST));
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
  private static List<Query> getSciNameQuery(String q) {
    String weaklyNormed = normalizeWeakly(q);
    String stronglyNormed = normalizeStrongly(q);
    int len = q.length();
    if (weaklyNormed.equals(stronglyNormed)) {
      if (len <= MAX_NGRAM_SIZE) {
        return asList(new AutoCompleteQuery("scientificNameWN", weaklyNormed, SCI_NAME_BOOST));
      }
      return asList(new CaseInsensitivePrefixQuery("scientificNameWN", weaklyNormed, SCI_NAME_BOOST));
    }
    // Determines how fast boost for strongly normalized matches creaps towards boost for weakly normalized matches
    int accelerator = 2;
    // Determines how far boost for strongly normalized matches may exceed boost for weakly normalized matches (possibly negative)
    float overshoot = 0;
    float strongBoost = Math.min(SCI_NAME_BOOST + overshoot, ((len + accelerator) / SCI_NAME_BOOST));
    if (len <= MAX_NGRAM_SIZE) {
      return asList(
          new AutoCompleteQuery("scientificNameWN", weaklyNormed, SCI_NAME_BOOST),
          new AutoCompleteQuery("scientificNameSN", stronglyNormed, strongBoost));
    }
    return asList(
        new CaseInsensitivePrefixQuery("scientificNameWN", weaklyNormed, SCI_NAME_BOOST),
        new CaseInsensitivePrefixQuery("scientificNameSN", stronglyNormed, strongBoost));
  }

}
