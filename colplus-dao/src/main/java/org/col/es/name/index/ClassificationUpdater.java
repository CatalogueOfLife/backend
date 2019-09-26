package org.col.es.name.index;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.NameUsageService;
import org.col.es.name.NameUsageWrapperConverter;
import org.col.es.query.BoolQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortField;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassificationUpdater implements Closeable, ResultHandler<NameUsageWrapper> {

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
  public void close() {
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
    LOG.debug("Received {} records from Postgres", collected.size());
    Map<String, NameUsageWrapper> lookups = collected.stream().collect(Collectors.toMap(this::getKey, Function.identity()));
    List<NameUsageDocument> documents = loadNameUsages(lookups.keySet());
    LOG.debug("Found {} matching documents", documents.size());
    documents.forEach(enu -> {
      NameUsageWrapper nuw = lookups.get(enu.getUsageId());
      enu.setUsageId(null); // Won't need to update that one
      NameUsageWrapperConverter.saveClassification(nuw, enu);
    });
    indexer.update(documents);
    LOG.debug("Updated {} documents", documents.size());
    collected.clear();
  }

  private List<NameUsageDocument> loadNameUsages(Set<String> ids) {
    List<NameUsageDocument> usages = new ArrayList<>(collected.size());
    List<String> terms = new ArrayList<>(1024);
    for (String id : ids) {
      terms.add(id);
      if (terms.size() == 1024) { // Max number of terms in TermsQuery is 1024
        usages.addAll(loadChunk(terms));
        terms.clear();
      }
    }
    if (terms.size() != 0) {
      usages.addAll(loadChunk(terms));
    }
    return usages;
  }

  /*
   * Returns bare bones name usage documents containing only the internal document ID (needed for the update later on) and
   * the usage ID (so they can be matched to the Postgres records).
   */
  private List<NameUsageDocument> loadChunk(List<String> terms) {
    EsSearchRequest query = EsSearchRequest.emptyRequest();
    BoolQuery constraints = new BoolQuery()
        .filter(new TermsQuery("usageId", terms))
        .filter(new TermQuery("datasetKey", datasetKey));
    query.select("usageId");
    query.setQuery(constraints);
    query.setSort(Arrays.asList(SortField.DOC));
    query.setSize(terms.size());
    NameUsageService svc = new NameUsageService(indexer.getIndexName(), indexer.getEsClient());
    return svc.getDocumentsWithDocId(query);
  }

  private String getKey(NameUsageWrapper nuw) {
    return nuw.getClassification().get(nuw.getClassification().size() - 1).getId();
  }

}
