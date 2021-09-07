package life.catalogue.es;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;

import java.util.Collections;

import life.catalogue.api.vocab.TaxonomicStatus;

import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameUsageSearchService extends TaxonCounter {

  Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  /**
   * Converts the Elasticsearh response coming back from the query into an API object (NameSearchResponse).
   *
   * @param nameSearchRequest
   * @param page
   * @return
   */
  NameUsageSearchResponse search(NameUsageSearchRequest nameSearchRequest, Page page);


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
    };
  }

}
