package life.catalogue.es;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.config.IndexConfig;
import life.catalogue.es.query.FieldLookup;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.transport.ElasticsearchTransport;

/**
 * Utility class for interacting with Elasticsearch using the typed Java client.
 */
public class EsUtil {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);

  /**
   * Creates an index using the static schema JSON file (settings + mappings).
   */
  public static int createIndex(ElasticsearchClient client, IndexConfig config) throws IOException {
    LOG.warn("Creating Elasticsearch index {}", config.name);
    try (InputStream schemaStream = EsUtil.class.getResourceAsStream("schema.json")) {
      CreateIndexResponse response = client.indices().create(c -> c
        .index(config.name)
        .withJson(schemaStream)
      );
      // Override shard/replica settings if non-default
      if (config.numShards != 1 || config.numReplicas != 0) {
        client.indices().putSettings(s -> s
          .index(config.name)
          .settings(st -> st
            .numberOfReplicas(String.valueOf(config.numReplicas))
          )
        );
      }
      return response.acknowledged() ? 200 : 400;
    }
  }

  /**
   * Creates the provided alias for the provided index.
   */
  public static void createAlias(ElasticsearchClient client, String index, String alias) {
    try {
      client.indices().putAlias(a -> a.index(index).name(alias));
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  /**
   * Deletes the index with the provided name. Will silently do nothing if the index did not exist.
   */
  public static int deleteIndex(ElasticsearchClient client, IndexConfig index) throws IOException {
    LOG.warn("Deleting Elasticsearch Index {}", index.name);
    try {
      DeleteIndexResponse response = client.indices().delete(d -> d.index(index.name));
      return response.acknowledged() ? 200 : 400;
    } catch (ElasticsearchException e) {
      if (e.status() == 404) {
        return 404;
      }
      throw new EsException(e.getMessage());
    }
  }

  /**
   * Deletes the provided alias.
   */
  public static void deleteAlias(ElasticsearchClient client, String index, String alias) throws IOException {
    client.indices().deleteAlias(a -> a.index(index).name(alias));
  }

  /**
   * Whether or not an index with the provided name exists.
   */
  public static boolean indexExists(ElasticsearchClient client, String index) throws IOException {
    return client.indices().exists(e -> e.index(index)).value();
  }

  /**
   * Removes the dataset corresponding to the provided key.
   */
  public static int deleteDataset(ElasticsearchClient client, String index, int datasetKey) {
    return deleteByQuery(client, index,
      Query.of(q -> q.term(t -> t.field("usage.datasetKey").value(datasetKey))));
  }

  /**
   * Removes bare names for the dataset.
   */
  public static int deleteBareNames(ElasticsearchClient client, String index, int datasetKey) {
    String statusField = FieldLookup.INSTANCE.lookupSingle(NameUsageSearchParameter.STATUS);
    return deleteByQuery(client, index,
      Query.of(q -> q.bool(b -> b
        .filter(f -> f.term(t -> t.field("usage.datasetKey").value(datasetKey)))
        .filter(f -> f.term(t -> t.field(statusField).value(TaxonomicStatus.BARE_NAME.name())))
      )));
  }

  /**
   * Removes the sector corresponding to the provided key.
   */
  public static int deleteSector(ElasticsearchClient client, String index, DSID<Integer> sectorKey) {
    return deleteByQuery(client, index,
      Query.of(q -> q.bool(b -> b
        .filter(f -> f.term(t -> t.field("usage.datasetKey").value(sectorKey.getDatasetKey())))
        .filter(f -> f.term(t -> t.field("usage.sectorKey").value(sectorKey.getId())))
      )));
  }

  /**
   * Deletes a taxonomic subtree from a single dataset.
   */
  public static int deleteSubtree(ElasticsearchClient client, String index, DSID<String> root, boolean keepRoot) {
    String taxonIdField = FieldLookup.INSTANCE.lookupSingle(NameUsageSearchParameter.TAXON_ID);
    return deleteByQuery(client, index,
      Query.of(q -> q.bool(b -> {
        b.filter(f -> f.term(t -> t.field("usage.datasetKey").value(root.getDatasetKey())))
         .filter(f -> f.term(t -> t.field(taxonIdField).value(root.getId())));
        if (keepRoot) {
          b.mustNot(mn -> mn.term(t -> t.field("id").value(root.getId())));
        }
        return b;
      })));
  }

  /**
   * Delete the documents corresponding to the provided dataset key and usage IDs.
   */
  public static int deleteNameUsages(ElasticsearchClient client, String index, int datasetKey, Collection<String> usageIds) {
    if (usageIds.isEmpty()) {
      return 0;
    }
    List<String> ids = (usageIds instanceof List) ? (List<String>) usageIds : new ArrayList<>(usageIds);
    int from = 0;
    int deleted = 0;
    while (from < ids.size()) {
      int to = Math.min(ids.size(), from + 1024);
      final List<String> batch = ids.subList(from, to);
      deleted += deleteByQuery(client, index,
        Query.of(q -> q.bool(b -> b
          .filter(f -> f.term(t -> t.field("usage.datasetKey").value(datasetKey)))
          .filter(f -> f.terms(ts -> ts.field("id").terms(tv -> tv.value(batch.stream().map(co.elastic.clients.elasticsearch._types.FieldValue::of).toList()))))
        )));
      from = to;
    }
    return deleted;
  }

  /**
   * Deletes all documents from the index, but leaves the index itself intact.
   */
  public static void truncate(ElasticsearchClient client, String index) {
    deleteByQuery(client, index, Query.of(q -> q.matchAll(m -> m)));
  }

  /**
   * Deletes all documents satisfying the provided query constraint(s).
   */
  public static int deleteByQuery(ElasticsearchClient client, String index, Query query) {
    int attempts = 20;
    for (int i = 0; i < attempts; i++) {
      try {
        DeleteByQueryResponse response = client.deleteByQuery(d -> d
          .index(index)
          .query(query)
          .conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
          .waitForCompletion(true)
        );
        Long del = response.deleted();
        return del != null ? del.intValue() : 0;
      } catch (ElasticsearchException e) {
        if (e.status() == 429) {
          LOG.warn("_delete_by_query request rejected by Elasticsearch. Waiting before retry");
          sleep(TooManyRequestsException.WAIT_INTERVAL_MILLIS);
        } else {
          throw new EsRequestException(e.getMessage());
        }
      } catch (IOException e) {
        throw new EsException(e);
      }
    }
    throw new EsRequestException("_delete_by_query request failed to complete after retries");
  }

  /**
   * Makes all index documents become visible to clients.
   */
  public static void refreshIndex(ElasticsearchClient client, String name) {
    try {
      client.indices().refresh(r -> r.index(name));
    } catch (IOException | ElasticsearchException e) {
      throw new EsException(e);
    }
  }

  /**
   * Simple document count.
   */
  public static int count(ElasticsearchClient client, String indexName) throws IOException {
    CountResponse response = client.count(c -> c.index(indexName));
    return (int) response.count();
  }

  /**
   * Inserts the provided object into the provided index and returns the generated document ID.
   * Copies sectorMode from the usage to the wrapper so it survives the JSON round-trip
   * (usage.sectorMode is @JsonIgnore, but NameUsageWrapper.sectorMode is not).
   */
  public static String insert(ElasticsearchClient client, String index, NameUsageWrapper obj) throws IOException {
    if (obj.getUsage() instanceof NameUsageBase nub && nub.getSectorMode() != null && obj.getSectorMode() == null) {
      obj.setSectorMode(nub.getSectorMode());
    }
    IndexResponse response = client.index(i -> i.index(index).document(obj));
    return response.id();
  }

  /**
   * Closes the underlying transport and REST client.
   */
  public static void close(ElasticsearchClient client) throws IOException {
    if (client != null) {
      ElasticsearchTransport transport = client._transport();
      if (transport instanceof AutoCloseable ac) {
        try {
          ac.close();
        } catch (IOException e) {
          throw e;
        } catch (Exception e) {
          throw new IOException(e);
        }
      }
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
    }
  }

}
