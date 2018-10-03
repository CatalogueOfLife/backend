package org.col.es;

import com.google.common.base.Strings;

import org.col.api.search.NameSearchRequest;
import org.col.es.query.BoolQuery;
import org.col.es.query.CaseInsensitiveQuery;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;

public class NameSearchRequestTranslator {

  private final NameSearchRequest request;

  public NameSearchRequestTranslator(NameSearchRequest request) {
    this.request = request;
  }

  public String getMainQuery() {
    EsSearchRequest esr = new EsSearchRequest();
    BoolQuery mainQuery = new BoolQuery();
    if (request.getSortBy() == NameSearchRequest.SortBy.RELEVANCE) {
      esr.setQuery(mainQuery);
    } else {
      esr.setQuery(new ConstantScoreQuery(mainQuery));
    }
    if (!Strings.isNullOrEmpty(request.getQ())) {
      BoolQuery qQuery = new BoolQuery();
      mainQuery.must(qQuery);
      for (NameSearchRequest.SearchContent searchIn : request.getContent()) {
        switch (searchIn) {
          case AUTHORSHIP:
            addAuthorshipFieldsToQuery(qQuery);
            break;
          case SCIENTIFIC_NAME:
            addScientificNameFieldsToQuery(qQuery);
            break;
          case VERNACULAR_NAME:
            addVernacularNameFieldsToQuery(qQuery);
            break;
        }
      }
    }
    return null;
  }

  private void addScientificNameFieldsToQuery(BoolQuery qQuery) {
    qQuery.should(new CaseInsensitiveQuery("name.basionymAuthorship.authors", request.getQ()));
    qQuery.should(new CaseInsensitiveQuery("name.combinationAuthorship.authors", request.getQ()));
  }

  private void addAuthorshipFieldsToQuery(BoolQuery qQuery) {
    qQuery.should(new CaseInsensitiveQuery("name.scientificName", request.getQ()));
  }

  private void addVernacularNameFieldsToQuery(BoolQuery qQuery) {
    
  }

}
