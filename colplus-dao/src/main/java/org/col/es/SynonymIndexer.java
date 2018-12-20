package org.col.es;

import java.util.HashMap;
import java.util.List;

import org.col.api.model.SimpleName;
import org.col.api.model.Synonym;
import org.col.api.search.NameUsageWrapper;
import org.col.es.query.BoolQuery;
import org.col.es.query.CollapsibleList;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortField;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;

public class SynonymIndexer {

  private final String datasetKey;

  public SynonymIndexer(String datasetKey) {
    this.datasetKey = datasetKey;
  }

  public void addBatch(List<NameUsageWrapper> batch) {
    HashMap<String, List<SimpleName>> map = new HashMap<>();
    for (NameUsageWrapper nuw : batch) {
      String taxonId = ((Synonym) nuw.getUsage()).getAccepted().getId();
      map.put(taxonId, null);
    }
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery("datasetKey", datasetKey))
        .filter(new TermsQuery("taxonId", map.keySet()));
    EsSearchRequest esr = EsSearchRequest.emptyRequest();
    esr.setQuery(new ConstantScoreQuery(query));
    esr.setSort(CollapsibleList.of(SortField.DOC));
    esr.setSize(map.keySet().size());
  }

}
