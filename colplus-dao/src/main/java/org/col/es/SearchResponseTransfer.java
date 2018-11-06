package org.col.es;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.col.api.model.NameUsage;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;

/**
 * Converts the payload field within the EsNameUsage document back to a NameUsageWrapper.
 */
public class SearchResponseTransfer {

  @SuppressWarnings("unchecked")
  List<NameUsageWrapper<? extends NameUsage>> transfer(SearchResponse<EsNameUsage> response) {
    if (response.getHits().getTotal() == 0) {
      return Collections.emptyList();
    }
    return response.getHits().getHits().stream().map(hit -> {
      try {
        return (NameUsageWrapper<? extends NameUsage>) EsModule.NAME_USAGE_READER
            .readValue(hit.getSource().getPayload());
      } catch (IOException e) {
        throw new EsException(e);
      }
    }).collect(Collectors.toList());
  }

}
