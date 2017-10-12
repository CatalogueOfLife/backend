package org.col.commands.importer.neo;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Throwables;
import org.apache.commons.io.FileUtils;
import org.col.api.Reference;
import org.col.commands.importer.neo.model.Labels;
import org.col.commands.importer.neo.model.NeoProperties;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.kernel.api.exceptions.index.IndexEntryConflictException;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A persistence mechanism for storing core taxonomy & names properties and relations in an embedded
 * Neo4j database, while keeping a large BLOB of information in a separate MapDB storage.
 * <p>
 * Neo4j does not perform well storing large properties in its node and it is recommended to keep
 * large BLOBs or strings externally: https://neo4j.com/blog/dark-side-neo4j-worst-practices/
 * <p>
 * We use the Kryo library for a very performant binary
 * serialisation with the data keyed under the neo4j node id.
 */
public class NeoDb implements NormalizerStore {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDb.class);
  private final GraphDatabaseBuilder neoFactory;
  private final DB mapDb;
  private final Map<Long, NeoTaxon> taxa;
  private final Map<Integer, Reference> references;
  private final AtomicInteger referenceSequence = new AtomicInteger(0);
  private final File neoDir;
  private final KryoPool pool;
  private BatchInserter inserter;
  private final int batchSize;

  private GraphDatabaseService neo;

  /**
   * @param mapDb
   * @param neoDir
   * @param neoFactory
   */
  NeoDb(DB mapDb, File neoDir, GraphDatabaseBuilder neoFactory, int batchSize) throws Exception {
    this.neoFactory = neoFactory;
    this.neoDir = neoDir;
    this.mapDb = mapDb;
    this.batchSize = batchSize;

    try {
      pool = new KryoPool.Builder(new org.col.commands.importer.neo.kryo.NeoKryoFactory())
          .softReferences()
          .build();

      //TODO: reorganize file layout; allow to reopen/append kryo file!
      // dataset1/
      //   normalizer(dir)
      //   mapdb
      taxa = mapDb.hashMap("taxa")
          .keySerializer(Serializer.LONG)
          .valueSerializer(new MapDbObjectSerializer(NeoTaxon.class, pool, 256))
          .createOrOpen();
      references = mapDb.hashMap("references")
          .keySerializer(Serializer.INTEGER)
          .valueSerializer(new MapDbObjectSerializer(Reference.class, pool, 128))
          .createOrOpen();
      openNeo();
    } catch (Exception e) {
      LOG.error("Failed to initialize a new NeoDB", e);
      close();
      throw e;
    }
  };

  /**
   * Fully closes the dao leaving any potentially existing persistence files untouched.
   */
  @Override
  public void close() {
    try {
      if (mapDb != null && !mapDb.isClosed()) {
        mapDb.close();
      }
    } catch (Exception e) {
      LOG.error("Failed to close mapDb for directory {}", neoDir.getAbsolutePath(), e);
    }
    closeNeo();
    LOG.debug("Closed NormalizerStore for directory {}", neoDir.getAbsolutePath());
  }

  public void closeAndDelete() {
    close();
    if (neoDir != null && neoDir.exists()) {
      LOG.debug("Deleting neo4j & mapDB directory {}", neoDir.getAbsolutePath());
      FileUtils.deleteQuietly(neoDir);
    }
  }

  private void openNeo() {
    LOG.debug("Starting embedded neo4j database from {}", neoDir.getAbsolutePath());
    neo = neoFactory.newGraphDatabase();
  }

  private void closeNeo() {
    try {
      if (neo != null) {
        neo.shutdown();
      }
    } catch (Exception e) {
      LOG.error("Failed to close neo4j {}", neoDir.getAbsolutePath(), e);
    }
  }

  public GraphDatabaseService getNeo() {
    return neo;
  }

  @Override
  public int getDatasetKey() {
    return -1;
  }

  /**
   * Shuts down the regular neo4j db and opens up neo4j in batch mode.
   * While batch mode is active only writes will be accepted and reads from the store
   * will throw exceptions.
   */
  @Override
  public void startBatchMode() {
    try {
      closeNeo();
      inserter = BatchInserters.inserter(neoDir);
    } catch (IOException e) {
      Throwables.propagate(e);
    }
  }

  public boolean isBatchMode() {
    return inserter != null;
  }

  @Override
  public void endBatchMode() throws NotUniqueRuntimeException {
    try {
      try {
        // define indices
        LOG.info("Building lucene index taxonID ...");
        //TODO: neo4j batchinserter does not seem to evaluate the unique constraint. Duplicates pass thru (see tests) !!!
        inserter.createDeferredConstraint(Labels.TAXON).assertPropertyIsUnique(NeoProperties.TAXON_ID).create();
        LOG.info("Building lucene index scientificName ...");
        inserter.createDeferredSchemaIndex(Labels.TAXON).on(NeoProperties.SCIENTIFIC_NAME).create();
        LOG.info("Building lucene index scientificNameID ...");
        inserter.createDeferredSchemaIndex(Labels.TAXON).on(NeoProperties.NAME_ID).create();
      } finally {
        // this is when lucene indices are build and thus throws RuntimeExceptions when unique constraints are broken
        // we catch these exceptions below
        inserter.shutdown();
      }
    } catch (RuntimeException e) {
      Throwable t = e.getCause();
      // check if the cause was a broken unique constraint which can only be taxonID in our case
      if (t != null && t instanceof IndexEntryConflictException) {
        IndexEntryConflictException pe = (IndexEntryConflictException) t;
        LOG.error("TaxonID not unique. Value {} used for both node {} and {}", pe.getSinglePropertyValue(), pe.getExistingNodeId(), pe.getAddedNodeId());
        throw new NotUniqueRuntimeException("TaxonID", pe.getSinglePropertyValue());
      } else {
        throw e;
      }
    }
    openNeo();
    inserter = null;
  }

  @Override
  public void put(NeoTaxon tax) {
    // extract references into ref store before store them
    for (Reference r : tax.listReferences()) {
      //TODO: add reference to map if new, replace all props with just the key
      put(r);
    }

    // update neo4j properties either via batch mode or classic
    //TODO: set ROOT or other labels
    long nodeId;
    Map<String, Object> props = NeoDbUtils.neo4jProps(tax);
    if (isBatchMode()) {
      // batch insert normalizer properties used during normalization
      nodeId = inserter.createNode(props, tax.getNeoLabel());

    } else {
      // create neo4j node if needed
      if (tax.node == null) {
        tax.node = neo.createNode(tax.getNeoLabel());
      }
      nodeId = tax.node.getId();
      // update neo4j props
      NeoDbUtils.setProperties(tax.node, props);
    }
    taxa.put(nodeId, tax);
  }

  @Override
  public void put(Reference r) {
    if (r.getKey() == null) {
      r.setKey(referenceSequence.incrementAndGet());
    }
    references.put(r.getKey(), r);
  }
}

