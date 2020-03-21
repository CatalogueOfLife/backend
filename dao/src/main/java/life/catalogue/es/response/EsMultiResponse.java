package life.catalogue.es.response;

import java.util.List;

/**
 * Models the object coming back from an _msearch (multisearch) request. Not used any longer.
 *
 * @param <T> The class modelling the documents in the index
 * @param <V> The type of the response within the multi response
 */
public class EsMultiResponse<T, V extends EsResponse<T>> {

  private List<V> responses;

  public List<V> getResponses() {
    return responses;
  }

}
