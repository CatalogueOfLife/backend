package org.col.es;

import java.io.Closeable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.SimpleNameClassification;
import org.col.es.model.EsNameUsage;
import org.col.es.query.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassificationUpdater implements Closeable, ResultHandler<SimpleNameClassification> {

  private static final Logger LOG = LoggerFactory.getLogger(ClassificationUpdater.class);

  private static final int BATCH_SIZE = 4096;

  private final List<SimpleNameClassification> collected;
  private final NameUsageIndexer indexer;
  private final int datasetKey;

  public ClassificationUpdater(NameUsageIndexer indexer, int datasetKey) {
    this.collected = new ArrayList<>(BATCH_SIZE);
    this.indexer = indexer;
    this.datasetKey = datasetKey;
  }

  @Override
  public void handleResult(ResultContext<? extends SimpleNameClassification> resultContext) {
    handle(resultContext.getResultObject());
  }

  @Override
  public void close() {
    if (collected.size() != 0) {
      flush();
    }
  }

  @VisibleForTesting
  void handle(SimpleNameClassification nuw) {
    collected.add(nuw);
    if (collected.size() == BATCH_SIZE) {
      flush();
    }
  }

  private void flush() {
    LOG.debug("Received {} records from Postgres", collected.size());
    Map<String, SimpleNameClassification> lookups = collected.stream().collect(Collectors.toMap(SimpleNameClassification::getId, Function.identity()));
    List<EsNameUsage> documents = loadNameUsages(lookups.keySet());
    LOG.debug("Found {} matching documents", documents.size());
    documents.forEach(enu -> {
      SimpleNameClassification nuw = lookups.get(enu.getUsageId());
      enu.setUsageId(null); // Won't need to update that one
      NameUsageTransfer.saveClassification(nuw, enu);
    });
    indexer.update(documents);
    LOG.debug("Updated {} documents", documents.size());
    collected.clear();
  }

  private List<EsNameUsage> loadNameUsages(Set<String> ids) {
    List<EsNameUsage> usages = new ArrayList<>(collected.size());
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
   * Returns bare bones name usage documents containing only the internal document ID (needed for the update later on) and the usage ID (so
   * they can be matched to the Postgres records).
   */
  private List<EsNameUsage> loadChunk(List<String> terms) {
    EsSearchRequest query = EsSearchRequest.emptyRequest();
    BoolQuery constraints = new BoolQuery()
        .filter(new TermsQuery("usageId", terms))
        .filter(new TermQuery("datasetKey", datasetKey));
    query.select("usageId");
    query.setQuery(constraints);
    query.setSort(Arrays.asList(SortField.DOC));
    query.setSize(terms.size());
    NameUsageSearchServiceEs svc = new NameUsageSearchServiceEs(indexer.getIndexName(), indexer.getEsClient());
    return svc.getDocumentsWithDocId(query);
  }

}
