package org.col.es;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.col.api.model.SimpleName;
import org.col.api.model.Synonym;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.col.es.query.BoolQuery;
import org.col.es.query.CollapsibleList;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortField;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;
import org.elasticsearch.client.RestClient;

public class SynonymIndexer {

  /*
   * 655536 is the absolute maximum number of terms in a terms query. We might want to use a smaller number.
   */
  private static final int LOOKUP_BATCH_SIZE = 4096;

  private final String datasetKey;
  private final RestClient client;
  private final NameUsageSearchService searchService;

  private final List<String> taxonIds = new ArrayList<>(LOOKUP_BATCH_SIZE);
  // Assume 3 synonyms per accepted name
  private final List<NameUsageWrapper> collected = new ArrayList<>(LOOKUP_BATCH_SIZE * 3);
  private final HashMap<String, List<SimpleName>> classifications = new HashMap<>();

  private String prevTaxonId = "";

  public SynonymIndexer(RestClient client, String datasetKey) {
    this.datasetKey = datasetKey;
    this.client = client;
    this.searchService = new NameUsageSearchService(client);
  }

  public void addBatch(List<NameUsageWrapper> batch) {
    for (NameUsageWrapper nuw : batch) {
      collected.add(nuw);
      String taxonId = ((Synonym) nuw.getUsage()).getAccepted().getId();
      if (taxonId.equals(prevTaxonId)) {
        continue;
      }
      taxonIds.add(taxonId);
      if (taxonIds.size() == LOOKUP_BATCH_SIZE) {
        flush();
      }
    }
  }

  private void flush() {
    EsSearchRequest query = createQuery();
    List<EsNameUsage> docs = searchService.getDocuments(query);
    Map<String, List<SimpleName>> classifications = docs
        .stream()
        .collect(Collectors.toMap(EsNameUsage::getUsageId, NameUsageTransfer::extractClassifiction));
    collected.forEach(nuw -> nuw.setClassification(classifications.get(((Synonym) nuw.getUsage()).getAccepted().getId())));
  }

  private EsSearchRequest createQuery() {
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery("datasetKey", datasetKey))
        .filter(new TermsQuery("taxonId", taxonIds));
    EsSearchRequest esr = EsSearchRequest.emptyRequest();
    esr.setQuery(new ConstantScoreQuery(query));
    esr.setSort(CollapsibleList.of(SortField.DOC));
    esr.setSize(LOOKUP_BATCH_SIZE);
    return esr;
  }

}
