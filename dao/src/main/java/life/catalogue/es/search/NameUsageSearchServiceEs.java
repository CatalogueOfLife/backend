package life.catalogue.es.search;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsException;
import life.catalogue.es.EsQueryService;
import life.catalogue.es.query.SortByTranslator;

import java.io.IOException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;

public class NameUsageSearchServiceEs extends EsQueryService implements NameUsageSearchService {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceEs.class);
  // keep the scroll context alive between batches
  private static final Time SCROLL_KEEP_ALIVE = Time.of(t -> t.time("5m"));

  public NameUsageSearchServiceEs(String indexName, ElasticsearchClient client) {
    super(indexName, client);
  }

  public NameUsageSearchResponse search(NameUsageSearchRequest request, Page page) {
    try {
      new SearchRequestValidator(request).validateRequest();
      SearchRequestTranslator translator = new SearchRequestTranslator(request, page);
      SearchRequest esSearchRequest = translator.translateRequest(index);
      SearchResponse<NameUsageWrapper> esResponse = client.search(esSearchRequest, NameUsageWrapper.class);
      SearchResponseConverter converter = new SearchResponseConverter(esResponse, request);
      return converter.convertEsResponse(page);

    } catch (ElasticsearchException e) {
      LOG.error("Elasticsearch error: {} => {}", e.getMessage(), e.response().error());
      throw new EsException(e);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @Override
  public void scroll(NameUsageSearchRequest request, int batchSize, Consumer<NameUsageWrapper> handler) {
    new SearchRequestValidator(request).validateRequest();
    final var query = SearchRequestTranslator.generateQuery(request);
    final var sort = new SortByTranslator(request).translate();
    try {
      // open the scroll with the first batch
      ResponseBody<NameUsageWrapper> resp = client.search(s -> s
          .index(index)
          .scroll(SCROLL_KEEP_ALIVE)
          .size(batchSize)
          .query(query)
          .sort(sort),
        NameUsageWrapper.class);
      String scrollId = resp.scrollId();
      try {
        while (!resp.hits().hits().isEmpty()) {
          for (Hit<NameUsageWrapper> hit : resp.hits().hits()) {
            if (hit.source() != null) {
              handler.accept(hit.source());
            }
          }
          final String sid = scrollId;
          resp = client.scroll(sc -> sc.scrollId(sid).scroll(SCROLL_KEEP_ALIVE), NameUsageWrapper.class);
          scrollId = resp.scrollId();
        }
      } finally {
        // always release the scroll context on the ES server
        final String sid = scrollId;
        if (sid != null) {
          try {
            client.clearScroll(c -> c.scrollId(sid));
          } catch (Exception e) {
            LOG.warn("Failed to clear scroll {}", sid, e);
          }
        }
      }
    } catch (ElasticsearchException e) {
      LOG.error("Elasticsearch error: {} => {}", e.getMessage(), e.response().error());
      throw new EsException(e);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

}
