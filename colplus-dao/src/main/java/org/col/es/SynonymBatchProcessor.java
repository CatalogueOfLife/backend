package org.col.es;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

import static java.util.stream.Collectors.toMap;

public class SynonymBatchProcessor {

  /*
   * 655536 is the absolute maximum number of terms in a terms query, but that may take up too much memory.
   */
  private static final int LOOKUP_BATCH_SIZE = 4096;

  private final NameUsageIndexer indexer;
  private final String datasetKey;

  private final List<String> taxonIds = new ArrayList<>(LOOKUP_BATCH_SIZE);
  // Assume 3 synonyms per accepted name
  private final List<NameUsageWrapper> collected = new ArrayList<>(LOOKUP_BATCH_SIZE * 3);

  private String prevTaxonId = "";

  public SynonymBatchProcessor(NameUsageIndexer indexer, String datasetKey) {
    this.indexer = indexer;
    this.datasetKey = datasetKey;
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
    NameUsageSearchService svc = new NameUsageSearchService(indexer.getRestClient());
    List<EsNameUsage> docs = svc.getDocuments(query);
    Map<String, List<SimpleName>> lookupTable = docs
        .stream()
        .collect(toMap(EsNameUsage::getUsageId, NameUsageTransfer::extractClassifiction));
    collected.forEach(nuw -> {
      String taxonId = ((Synonym) nuw.getUsage()).getAccepted().getId();
      nuw.setClassification(lookupTable.get(taxonId));
    });
    indexer.indexBatch(collected);
    taxonIds.clear();
    collected.clear();
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
