package life.catalogue.es.nu;

import life.catalogue.api.model.SimpleNameClassification;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.query.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public class ClassificationUpdater implements Consumer<List<? extends SimpleNameClassification>> {

  private static final Logger LOG = LoggerFactory.getLogger(ClassificationUpdater.class);

  private final NameUsageIndexer indexer;
  private final int datasetKey;

  public ClassificationUpdater(NameUsageIndexer indexer, int datasetKey) {
    this.indexer = indexer;
    this.datasetKey = datasetKey;
  }

  @Override
  public void accept(List<? extends SimpleNameClassification> batch) {
    LOG.debug("Received {} records from Postgres", batch.size());
    Map<String, SimpleNameClassification> lookups = batch.stream().collect(toMap(SimpleNameClassification::getId, Function.identity()));
    List<EsNameUsage> documents = loadNameUsages(lookups.keySet());
    LOG.debug("Found {} matching documents", documents.size());
    documents.forEach(doc -> {
      SimpleNameClassification classification = lookups.get(doc.getUsageId());
      doc.setUsageId(null); // Won't need to update that one
      NameUsageWrapperConverter.saveClassification(doc, classification);
    });
    indexer.update(documents);
    LOG.debug("Updated {} documents", documents.size());
  }

  private List<EsNameUsage> loadNameUsages(Set<String> ids) {
    List<EsNameUsage> usages = new ArrayList<>(ids.size());
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
    EsSearchRequest query = EsSearchRequest.emptyRequest()
        .select("usageId")
        .where(BoolQuery.withFilters(
            new TermQuery("datasetKey", datasetKey),
            new TermsQuery("usageId", terms)))
        .sortBy(SortField.DOC)
        .size(terms.size());
    NameUsageQueryService svc = new NameUsageQueryService(indexer.getIndexName(), indexer.getEsClient());
    return svc.getDocumentsWithDocId(query);
  }

}
