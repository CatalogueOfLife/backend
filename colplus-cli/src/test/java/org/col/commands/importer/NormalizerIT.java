package org.col.commands.importer;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.commands.config.NormalizerConfig;
import org.col.commands.importer.neo.NeoDbFactory;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.commands.importer.neo.model.Labels;
import org.col.commands.importer.neo.model.NeoProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 *
 */
public class NormalizerIT {
  private NormalizerStore store;
  private NormalizerConfig cfg;

  private Normalizer normalizer(int datasetKey) throws Exception {
    URL dwcaUrl = getClass().getResource("/dwca/"+datasetKey);
    Path dwca = Paths.get(dwcaUrl.toURI());

    store = NeoDbFactory.create(cfg,datasetKey);
    return new Normalizer(store, dwca.toFile());
  }

  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.directory = Files.createTempDir();
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      // store is close by Normalizer.run method already
      FileUtils.deleteQuietly(cfg.directory);
    }
  }

  void reopen(int datasetKey) throws IOException {
    store = NeoDbFactory.open(cfg,datasetKey);
  }

  @Test
  @Ignore("Need to fix name parsing first")
  public void testNeoIndices() throws Exception {
    final int datasetKey = 1;

    Normalizer norm = normalizer(datasetKey);
    norm.run();

    reopen(datasetKey);
    //compareStats(norm.getStats());

    Set<String> taxonIndices = Sets.newHashSet();
    taxonIndices.add(NeoProperties.TAXON_ID);
    taxonIndices.add(NeoProperties.SCIENTIFIC_NAME);
    taxonIndices.add(NeoProperties.NAME_ID);
    try (Transaction tx = store.getNeo().beginTx()) {
      Schema schema = store.getNeo().schema();
      for (IndexDefinition idf : schema.getIndexes(Labels.TAXON)) {
        List<String> idxProps = Iterables.asList(idf.getPropertyKeys());
        assertTrue(idxProps.size() == 1);
        assertTrue(taxonIndices.remove(idxProps.get(0)));
      }

      // 1001, Crepis bakeri Greene
      assertNotNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.TAXON_ID, "1001")));
      assertNotNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, "Crepis bakeri")));

      assertNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.TAXON_ID, "x1001")));
      assertNull(Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, "xCrepis bakeri")));
    }
  }

  @Test
  public void run() throws Exception {
    Normalizer n = normalizer(1);
    n.run();
  }

}