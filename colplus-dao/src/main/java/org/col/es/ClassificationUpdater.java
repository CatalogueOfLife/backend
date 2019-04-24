package org.col.es;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.col.es.query.BoolQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortField;
import org.col.es.query.TermsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassificationUpdater implements Closeable, ResultHandler<NameUsageWrapper> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ClassificationUpdater.class);

  private static final int BATCH_SIZE = 4096;

  private final List<NameUsageWrapper> collected;
  private final NameUsageIndexer indexer;
  private final int datasetKey;

  public ClassificationUpdater(NameUsageIndexer indexer, int datasetKey) {
    this.collected = new ArrayList<>(BATCH_SIZE);
    this.indexer = indexer;
    this.datasetKey = datasetKey;
  }

  @Override
  public void handleResult(ResultContext<? extends NameUsageWrapper> resultContext) {
    handle(resultContext.getResultObject());
  }

  @Override
  public void close() throws IOException {
    if (collected.size() != 0) {
      flush();
    }
  }

  @VisibleForTesting
  void handle(NameUsageWrapper nuw) {
    collected.add(nuw);
    if (collected.size() == BATCH_SIZE) {
      flush();
    }
  }

  private void flush() {
    Map<String, NameUsageWrapper> lookups = collected.stream().collect(Collectors.toMap(this::getKey, Function.identity()));
    List<EsNameUsage> documents = loadNameUsages();
    documents.forEach(enu -> {
      NameUsageWrapper nuw = lookups.get(enu.getUsageId());
      NameUsageTransfer.saveClassification(nuw, enu);
    });
    indexer.indexRaw(documents);
  }

  private List<EsNameUsage> loadNameUsages() {
    List<EsNameUsage> usages = new ArrayList<>(collected.size());
    List<String> terms = new ArrayList<>(1024);
    for (NameUsageWrapper nuw : collected) {
      terms.add(getKey(nuw));
      if (terms.size() == 1024) { // Can't have more than this many terms in a TermsQuery
        usages.addAll(loadChunk(terms));
        terms.clear();
      }
    }
    if (terms.size() != 0) {
      usages.addAll(loadChunk(terms));
    }
    return usages;
  }

  private List<EsNameUsage> loadChunk(List<String> terms) {
    EsSearchRequest query = EsSearchRequest.emptyRequest();
    BoolQuery constraints = new BoolQuery()
        .filter(new TermsQuery("usageId", terms))
        .filter(new TermsQuery("datasetKey", datasetKey));
    query.setQuery(constraints);
    query.setSort(Arrays.asList(SortField.DOC));
    NameUsageSearchService svc = new NameUsageSearchService(indexer.getIndexName(), indexer.getEsClient());
    return svc.getDocuments(query);
  }

  private String getKey(NameUsageWrapper nuw) {
    return nuw.getClassification().get(nuw.getClassification().size() - 1).getId();
  }

}
