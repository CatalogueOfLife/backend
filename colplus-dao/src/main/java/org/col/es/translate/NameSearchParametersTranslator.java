package org.col.es.translate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.vocab.VocabularyUtils;
import org.col.es.InvalidQueryException;
import org.col.es.query.BoolQuery;
import org.col.es.query.IsNullQuery;
import org.col.es.query.Query;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;

/*
 * Translates all query parameters except the "q" parameter into an Elasticsearch query. Unless
 * there is just one query parameter, this will be an AND query. For example:
 * ?rank=genus&nom_status=available is translated into (rank=genus AND nom_status=available). If a
 * query parameter maps to multiple Elasticsearch fields (not currently the case), these fields will
 * then be combined within a nested OR query. Also, the search request may contain multiple values
 * per request parameter (since it extends MultiValuedMap). For example:
 * ?rank=order&rank=family&rank=genus. This will be translated into (rank=order OR rank=family OR
 * rank=genus). Finally, a query parameter may be present but have no value, for example:
 * ?nom_status=&rank=family. This will be translated into (nom_status IS NULL AND rank=family).
 *
 */
class NameSearchParametersTranslator {

  private final NameSearchRequest request;

  NameSearchParametersTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Optional<Query> translate() throws InvalidQueryException {
    NameSearchParameter[] params = getRequestParams();
    if (params.length == 0) {
      return Optional.empty();
    }
    if (params.length == 1) {
      return Optional.of(translate(params[0]));
    }
    BoolQuery bq = new BoolQuery();
    for (NameSearchParameter param : params) {
      bq.must(translate(param));
    }
    return Optional.of(bq);
  }

  private Query translate(NameSearchParameter param) throws InvalidQueryException {
    List<Query> queries = new ArrayList<>();
    // Get the ES field(s) that this parameter maps to
    String[] fields = EsFieldLookup.INSTANCE.get(param);
    for (String field : fields) {
      if (containsEmptyValue(param)) {
        queries.add(new IsNullQuery(field));
      }
      List<?> paramValues = getNonEmptyValues(param);
      if (paramValues.size() == 1) {
        queries.add(new TermQuery(field, paramValues.get(0)));
      } else if (paramValues.size() > 1) {
        queries.add(new TermsQuery(field, paramValues));
      }
    }
    if (queries.size() == 1) {
      return queries.get(0);
    }
    return queries.stream().collect(BoolQuery::new, BoolQuery::should, BoolQuery::should);
  }

  private NameSearchParameter[] getRequestParams() {
    return Arrays.stream(NameSearchParameter.values()).filter(request::containsKey).toArray(
        NameSearchParameter[]::new);
  }

  private boolean containsEmptyValue(NameSearchParameter param) {
    return request.get(param).stream().anyMatch(StringUtils::isEmpty);
  }

  private List<?> getNonEmptyValues(NameSearchParameter param) throws InvalidQueryException {
    if (param.type() == String.class)
      return request.get(param).stream().filter(StringUtils::isNotEmpty).collect(
          Collectors.toList());
    if (param.type() == Integer.class) {
      try {
        return request.get(param).stream().map(Integer::valueOf).collect(Collectors.toList());
      } catch (NumberFormatException e) {
        throw new InvalidQueryException("Non-integer value for parameter " + param);
      }
    }
    if (Enum.class.isAssignableFrom(param.type())) {
      try {
        return request.get(param)
            .stream()
            .map(t -> VocabularyUtils.lookupEnum(t, param.getClass()).ordinal())
            .collect(Collectors.toList());
      } catch (IllegalArgumentException e) {
        throw new InvalidQueryException(e.getMessage());
      }
    }
    throw new AssertionError("Unexpected parameter type: " + param.type());
  }

}
