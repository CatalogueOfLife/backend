package life.catalogue.es;

import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class EsUtilIT extends EsTestBase {

  /**
   * Elasticsearch refuses to delete an index through its alias, as init did with the configured index name
   */
  @Test
  public void deleteAliasedIndices() throws Exception {
    final String alias = indexName + "-alias";
    EsUtil.createAlias(client, indexName, alias);
    assertTrue(EsUtil.indexExists(client, alias));
    assertEquals(Set.of(indexName), EsUtil.aliasedIndices(client, alias));
    assertTrue(EsUtil.aliasedIndices(client, indexName).isEmpty());

    EsUtil.deleteIndexOrAliasedIndices(client, alias);
    assertFalse(EsUtil.indexExists(client, alias));
    assertFalse(EsUtil.indexExists(client, indexName));
    assertTrue(EsUtil.aliasedIndices(client, alias).isEmpty());

    // a concrete index and a missing one
    EsUtil.createIndex(client, cfg.index);
    EsUtil.deleteIndexOrAliasedIndices(client, indexName);
    assertFalse(EsUtil.indexExists(client, indexName));
    EsUtil.deleteIndexOrAliasedIndices(client, indexName);
  }

}
