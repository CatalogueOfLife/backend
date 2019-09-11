package org.col.es;

import java.util.Collections;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameUsageSearchService {
  Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);
  
  /**
   * Converts the Elasticsearh response coming back from the query into an API object (NameSearchResponse).
   *
   * @param nameSearchRequest
   * @param page
   * @return
   */
  NameSearchResponse search(NameSearchRequest nameSearchRequest, Page page);
  
  /**
   * @return a pass through search service that never returns any results. Good for tests
   */
  static NameUsageSearchService passThru() {
    return new NameUsageSearchService() {
  
      @Override
      public NameSearchResponse search(NameSearchRequest request, Page page) {
        LOG.info("No Elastic Search configured. Passing through search request {}", request);
        return new NameSearchResponse(page, 0, Collections.EMPTY_LIST);
      }
    };
  }
  
}
