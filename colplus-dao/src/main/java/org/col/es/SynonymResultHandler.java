package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.SimpleName;
import org.col.api.model.Synonym;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.col.es.query.BoolQuery;
import org.col.es.query.CollapsibleList;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortField;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.NameUsageTransfer.extractClassifiction;

/**
 * Collects synonyms retrieved from Postgres until a treshold is reached and then adds their classification before inserting them into
 * Elasticsearch.
 */
class SynonymResultHandler implements ResultHandler<NameUsageWrapper>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SynonymResultHandler.class);

  /*
   * The maximum number of taxa we are going to retrieve to build an id-to-classification lookup table. NB 655536 is the absolute maximum
   * number of terms in a terms query (used to retrieve the taxa by their IDs), but that might blow up the JVM. Note that the lookup table
   * size really doesn't matter all that much, because simply indexing the documents takes up way more time than enriching them with
   * classifications.
   */
  private static final int LOOKUP_TABLE_SIZE = 8192;

  private final NameUsageIndexer indexer;
  private final int datasetKey;

  private final List<String> taxonIds = new ArrayList<>(LOOKUP_TABLE_SIZE);
  private final List<NameUsageWrapper> collected = new ArrayList<>(LOOKUP_TABLE_SIZE * 2);

  private String prevTaxonId = "";

  SynonymResultHandler(NameUsageIndexer indexer, int datasetKey) {
    this.indexer = indexer;
    this.datasetKey = datasetKey;
  }

  @Override
  public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
    NameUsageWrapper nuw = ctx.getResultObject();
    collected.add(nuw);
    String taxonId = getTaxonId(nuw);
    if (!taxonId.equals(prevTaxonId)) {
      // NB synonyms expected to be ordered by taxon ID in NameUsageMapper.xml
      taxonIds.add(prevTaxonId = taxonId);
      if (taxonIds.size() == LOOKUP_TABLE_SIZE) {
        try {
          flush();
        } catch (IOException e) {
          throw new EsException(e);
        }
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (collected.size() != 0) {
      flush();
    }
  }

  private void flush() throws IOException {
    LOG.debug("Loading taxa");
    List<EsNameUsage> taxa = loadTaxa();
    LOG.debug("Building lookup table for {} taxa", taxa.size());
    HashMap<String, List<SimpleName>> lookups = createLookupTable(taxa);
    LOG.debug("Copying classifications");
    collected.forEach(nuw -> nuw.setClassification(lookups.get(getTaxonId(nuw))));
    LOG.debug("Submitting {} synonyms to indexer", collected.size());
    indexer.accept(collected);
    taxonIds.clear();
    collected.clear();
    prevTaxonId = "";
  }

  private List<EsNameUsage> loadTaxa() throws IOException {
    NameUsageSearchService svc = new NameUsageSearchService(indexer.getEsClient());
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery("datasetKey", datasetKey))
        .filter(new TermsQuery("usageId", taxonIds));
    EsSearchRequest esr = EsSearchRequest.emptyRequest();
    // Keep memory footprint of lookup table as small as possible:
    esr.select("usageId", "classificationIds", "classification");
    esr.setQuery(query);
    esr.setSort(CollapsibleList.of(SortField.DOC));
    esr.setSize(taxonIds.size());
    return svc.getDocuments(indexer.getIndexName(), esr);
  }

  // Collectors.toMap inefficient for large maps in Java 8
  private static HashMap<String, List<SimpleName>> createLookupTable(List<EsNameUsage> taxa) {
    // Expect hardly any hash collisions for taxon IDs
    HashMap<String, List<SimpleName>> map = new HashMap<>(taxa.size(), 1F);
    taxa.forEach(enu -> map.put(enu.getUsageId(), extractClassifiction(enu)));
    return map;
  }

  private static String getTaxonId(NameUsageWrapper nuw) {
    return ((Synonym) nuw.getUsage()).getAccepted().getId();
  }

}
