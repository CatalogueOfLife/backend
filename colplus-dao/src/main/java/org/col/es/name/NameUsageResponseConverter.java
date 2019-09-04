package org.col.es.name;

import java.util.List;

import org.col.es.model.NameUsageDocument;
import org.col.es.response.SearchHit;

import static java.util.stream.Collectors.toList;

/**
 * Converts
 */
public class NameUsageResponseConverter {

  protected final NameUsageResponse esResponse;

  public NameUsageResponseConverter(NameUsageResponse response) {
    this.esResponse = response;
  }

  /**
   * Returns the raw Elasticsearch documents with the payload still zipped (if zipping is enabled). Useful and fast if
   * you're only interested in the indexed fields.
   * 
   * @return
   */
  public List<NameUsageDocument> getDocuments() {
    return esResponse.getHits().getHits().stream().map(SearchHit::getSource).collect(toList());
  }

  /**
   * Returns the raw Elasticsearch documents with their internal document IDs set on the NameUsageDocument instances.
   * 
   * @return
   */
  public List<NameUsageDocument> getDocumentsWithDocId() {
    return esResponse.getHits().getHits().stream().map(this::extractDocument).collect(toList());
  }

  private NameUsageDocument extractDocument(SearchHit<NameUsageDocument> hit) {
    NameUsageDocument enu = hit.getSource();
    enu.setDocumentId(hit.getId());
    return enu;
  }

}
