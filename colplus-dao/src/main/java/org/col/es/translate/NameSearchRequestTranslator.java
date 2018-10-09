package org.col.es.translate;

import com.google.common.base.Strings;

import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.InvalidQueryException;
import org.col.es.query.BoolQuery;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;

public class NameSearchRequestTranslator {

  private final NameSearchRequest request;

  public NameSearchRequestTranslator(NameSearchRequest request) {
    this.request = request;
  }

  public EsSearchRequest translate() throws InvalidQueryException {
    EsSearchRequest esr = new EsSearchRequest();
    BoolQuery mainQuery = new BoolQuery();
    if (request.getSortBy() == NameSearchRequest.SortBy.RELEVANCE) {
      esr.setQuery(mainQuery);
    } else {
      esr.setQuery(new ConstantScoreQuery(mainQuery));
    }
    for (NameSearchParameter param : NameSearchParameter.values()) {
      if (request.get(param) != null) {

      }
    }
    if (!Strings.isNullOrEmpty(request.getQ())) {
      mainQuery.must(new QTranslator(request).translate());
    }
    return esr;
  }

}
