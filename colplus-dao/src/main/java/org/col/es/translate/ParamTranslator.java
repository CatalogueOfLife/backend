package org.col.es.translate;

import java.util.List;
import java.util.stream.Collectors;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.vocab.VocabularyUtils;
import org.col.es.InvalidQueryException;
import org.col.es.query.BoolQuery;
import org.col.es.query.IsNotNullQuery;
import org.col.es.query.Query;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;

/**
 * Translates a (non-Q) parameter in the NameSearchRequest to an Elasticsearch query.
 * 
 * @author Ayco Holleman
 *
 */
class ParamTranslator {

  private final NameSearchRequest request;
  private final NameSearchParameter param;

  ParamTranslator(NameSearchRequest request, NameSearchParameter param) {
    this.request = request;
    this.param = param;
  }

  Query translate() throws InvalidQueryException {
    List<String> values = request.get(param);
    if (values.size() == 0) {
      String field = EsFieldLookup.INSTANCE.get(param);
      return new BoolQuery().mustNot(new IsNotNullQuery(field));
    }
    return translateMultiple();
  }

  private Query translateMultiple() {
    String field = EsFieldLookup.INSTANCE.get(param);
    if (param.type() == String.class) {
      return new TermsQuery(field, request.get(param));
    }
    if (param.type() == Integer.class) {
      return new TermsQuery(field, getInts());
    }
    if (Enum.class.isAssignableFrom(param.type())) {
      return new TermsQuery(field, getOrdinals());
    }
    throw new AssertionError("Unexpected search parameter type");
  }

  private Query translateSingle() throws InvalidQueryException {
    String field = EsFieldLookup.INSTANCE.get(param);
    if (param.type() == String.class) {
      return new TermQuery(field, request.get(param).get(0));
    }
    if (param.type() == Integer.class) {
    }
    throw new AssertionError("Unexpected search parameter type");
  }

  private Integer getOrdinal() {
    String val = request.get(param).get(0);
    return VocabularyUtils.lookupEnum(val, param.getClass()).ordinal();
  }

  private Integer getInt() throws InvalidQueryException {
    try {
      return Integer.valueOf(request.get(param).get(0));
    } catch (NumberFormatException e) {
      String fmt = "Invalid value for parameter %s: %s";
      String msg = String.format(fmt, param,request.get(param).get(0));
      throw new InvalidQueryException(msg);
    }
  }

  private List<Integer> getOrdinals() {
    return request.get(param)
        .stream()
        .map(t -> VocabularyUtils.lookupEnum(t, param.getClass()).ordinal())
        .collect(Collectors.toList());
  }

  private List<Integer> getInts() {
    return request.get(param).stream().map(Integer::valueOf).collect(Collectors.toList());
  }

}
