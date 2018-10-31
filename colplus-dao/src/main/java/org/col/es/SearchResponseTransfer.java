package org.col.es;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectReader;

import org.col.api.model.NameUsage;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;

public class SearchResponseTransfer {

  private final ObjectReader payloadReader;

  public SearchResponseTransfer(ObjectReader payloadReader) {
    this.payloadReader = payloadReader;
  }

  @SuppressWarnings("unchecked")
  List<NameUsageWrapper<? extends NameUsage>> transfer(SearchResponse<EsNameUsage> response) {
    if (response.getHits().getTotal() == 0) {
      return Collections.emptyList();
    }
    return response.getHits().getHits().stream().map(hit -> {
      try {
        return (NameUsageWrapper<? extends NameUsage>) payloadReader
            .readValue(hit.getSource().getPayload());
      } catch (IOException e) {
        throw new EsException(e);
      }
    }).collect(Collectors.toList());
  }

}
