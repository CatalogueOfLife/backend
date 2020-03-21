package life.catalogue.es.nu;

import java.util.List;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.UpwardConverter;
import life.catalogue.es.response.EsResponse;
import life.catalogue.es.response.SearchHit;
import static java.util.stream.Collectors.toList;

/**
 * Converts the raw Elasticsearch response into a list of {@link EsNameUsage} instances, which typically will converted again into API-level
 * {@link NameUsageWrapper} instances.
 */
public class EsNameUsageConverter implements UpwardConverter<EsResponse<EsNameUsage>, List<EsNameUsage>> {

  protected final EsResponse<EsNameUsage> esResponse;

  public EsNameUsageConverter(EsResponse<EsNameUsage> response) {
    this.esResponse = response;
  }

  /**
   * Returns the raw Elasticsearch documents with the payload still zipped (if zipping is enabled). Useful and fast if you're only
   * interested in the indexed fields.
   * 
   * @return
   */
  public List<EsNameUsage> getDocuments() {
    return esResponse.getHits().getHits().stream().map(SearchHit::getSource).collect(toList());
  }

  /**
   * Returns the raw Elasticsearch documents with their internal document IDs set on the NameUsageDocument instances.
   * 
   * @return
   */
  public List<EsNameUsage> getDocumentsWithDocId() {
    return esResponse.getHits().getHits().stream().map(this::extractDocument).collect(toList());
  }

  private EsNameUsage extractDocument(SearchHit<EsNameUsage> hit) {
    EsNameUsage enu = hit.getSource();
    enu.setDocumentId(hit.getId());
    return enu;
  }

}
