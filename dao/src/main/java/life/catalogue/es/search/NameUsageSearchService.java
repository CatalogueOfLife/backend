package life.catalogue.es.search;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.util.Collections;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameUsageSearchService extends TaxonCounter {

  Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  /**
   * Converts the Elasticsearch response coming back from the query into an API object (NameSearchResponse).
   *
   * @param nameSearchRequest
   * @param page
   * @return
   */
  NameUsageSearchResponse search(NameUsageSearchRequest nameSearchRequest, Page page);

  /**
   * Streams all hits matching the request in a single, scrolled Elasticsearch pass, invoking the handler for each.
   * Unlike {@link #search(NameUsageSearchRequest, Page)} this is not limited by the max result window and does not
   * use inefficient deep offset paging, so it can be used to retrieve very large, unbounded result sets in one go.
   *
   * @param batchSize number of documents to retrieve per scroll batch
   * @param handler consumer invoked for every matching name usage in result order
   */
  void scroll(NameUsageSearchRequest request, int batchSize, Consumer<NameUsageWrapper> handler);


  @Override
  default int count(DSID<String> taxonID, Rank countRank) {
    final Page page = new Page(0,0);
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, taxonID.getDatasetKey());
    req.addFilter(NameUsageSearchParameter.TAXON_ID, taxonID.getId());
    req.addFilter(NameUsageSearchParameter.RANK, countRank);
    req.addFilter(NameUsageSearchParameter.STATUS, TaxonomicStatus.ACCEPTED, TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    var resp = search(req, page);
    return resp.getTotal();
  }

  default int count(int datasetKey) {
    final Page page = new Page(0,0);
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, datasetKey);
    var resp = search(req, page);
    return resp.getTotal();
  }

  /**
   * @return a pass through search service that never returns any results. Good for tests
   */
  static NameUsageSearchService passThru() {
    return new NameUsageSearchService() {

      @Override
      public NameUsageSearchResponse search(NameUsageSearchRequest request, Page page) {
        LOG.info("No Elastic Search configured. Passing through search request {}", request);
        return new NameUsageSearchResponse(page, 0, Collections.EMPTY_LIST);
      }

      @Override
      public void scroll(NameUsageSearchRequest request, int batchSize, Consumer<NameUsageWrapper> handler) {
        LOG.info("No Elastic Search configured. Passing through scroll request {}", request);
      }
    };
  }

}
