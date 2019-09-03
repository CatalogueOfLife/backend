package org.col.es.response;

import java.util.List;

/**
 * The reponse coming back from an _msearch (multisearch) request.
 *
 * @param <T> The class modelling the documents in the index
 * @param <U> The type of the aggregations specific to the index
 * @param <V> The type of the response within the multi response
 */
public class EsMultiResponse<T, U extends Aggregation, V extends EsResponse<T, U>> {

  private List<V> responses;

  public List<V> getResponses() {
    return responses;
  }

}
