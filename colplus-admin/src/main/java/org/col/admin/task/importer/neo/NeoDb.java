package org.col.admin.task.importer.neo;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import org.apache.commons.io.FileUtils;
import org.col.admin.task.importer.ImportJob;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.kryo.NeoKryoFactory;
import org.col.admin.task.importer.neo.mapdb.MapDbObjectSerializer;
import org.col.admin.task.importer.neo.model.*;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.util.concurrent.ThrottledThreadPoolExecutor;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.kernel.impl.core.NodeProxy;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A persistence mechanism for storing core taxonomy & names properties and relations in an embedded
 * Neo4j database, while keeping a large BLOB of information in a separate MapDB storage.
 * <p>
 * Neo4j does not perform well storing large properties in its node and it is recommended to keep
 * large BLOBs or strings externally: https://neo4j.com/blog/dark-side-neo4j-worst-practices/
 * <p>
 * We use the Kryo library for a very performant binary
 * serialisation with the data keyed under the neo4j node value.
 *
 * TODO: separate out the reference store in its own class and use a lucene index
 * with a suitable analyzer to lookup refs by id or title
 */
public class NeoDb implements ReferenceStore {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDb.class);
  private static final Labels[] TAX_LABELS = new Labels[]{Labels.ALL, Labels.TAXON};
  private static final Labels[] SYN_LABELS = new Labels[]{Labels.ALL, Labels.SYNONYM};
  public static final Name PLACEHOLDER = new Name();
  static {
    PLACEHOLDER.setScientificName("Incertae sedis");
    PLACEHOLDER.setRank(Rank.UNRANKED);
    PLACEHOLDER.setType(NameType.PLACEHOLDER);
    PLACEHOLDER.setOrigin(Origin.OTHER);
  }

  private final int datasetKey;
  private final GraphDatabaseBuilder neoFactory;
  private final DB mapDb;
  private final Atomic.Var<Dataset> dataset;
  private final Map<Long, NeoTaxon> taxa;
  private final Map<Integer, Reference> references;
  private final Map<String, Reference> referenceIndex;
  private final AtomicInteger referenceSequence = new AtomicInteger(0);
  private final File neoDir;
  private final KryoPool pool;
  private BatchInserter inserter;
  public final int batchSize;

  private GraphDatabaseService neo;

  /**
   * @param mapDb
   * @param neoDir
   * @param neoFactory
   */
  NeoDb(int datasetKey, DB mapDb, File neoDir, GraphDatabaseBuilder neoFactory, int batchSize) throws Exception {
    this.datasetKey = datasetKey;
    this.neoFactory = neoFactory;
    this.neoDir = neoDir;
    this.mapDb = mapDb;
    this.batchSize = batchSize;

    try {
      pool = new KryoPool.Builder(new NeoKryoFactory())
          .softReferences()
          .build();

      dataset = (Atomic.Var<Dataset>) mapDb.atomicVar("dataset", new MapDbObjectSerializer(Dataset.class, pool, 256))
          .createOrOpen();
      taxa = mapDb.hashMap("taxa")
          .keySerializer(Serializer.LONG)
          .valueSerializer(new MapDbObjectSerializer(NeoTaxon.class, pool, 256))
          .createOrOpen();
      references = mapDb.hashMap("references")
          .keySerializer(Serializer.INTEGER)
          .valueSerializer(new MapDbObjectSerializer(Reference.class, pool, 128))
          .createOrOpen();
      // TODO: replace with lucene index stored on disk
      referenceIndex = Maps.newHashMap();
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

  public NeoTaxon get(Node n) {
    NeoTaxon t = taxa.get(n.getId());
    if (t != null) {
      t.node = n;
    }
    return t;
  }

  public NeoTaxon getByID(String id) {
    Node n = byID(id);
    if (n != null) {
      return get(n);
    }
    return null;
  }

  /**
   * @return the single matching node with the taxonID or null
   */
  public Node byID(String id) {
    try {
      return Iterators.singleOrNull(neo.findNodes(Labels.ALL, NeoProperties.ID, id));
    } catch (NoSuchElementException e) {
      throw new NotUniqueRuntimeException(NeoProperties.ID, id);
    }
  }

  /**
   * get taxon by its unique taxonID
   */
  public NeoTaxon getByTaxonID(String taxonID) {
    Node n = byID(taxonID);
    if (n != null) {
      return get(n);
    }
    return null;
  }

  /**
   * @return the single matching node with the taxonID or null
   */
  public Node byTaxonID(String taxonID) {
    try {
      return Iterators.singleOrNull(neo.findNodes(Labels.ALL, NeoProperties.TAXON_ID, taxonID));

    } catch (NoSuchElementException e) {
      throw new NotUniqueRuntimeException(NeoProperties.ID, taxonID);
    }
  }

  /**
   * @return the matching nodes with the scientificName
   */
  public List<Node> byScientificName(String scientificName) {
    return Iterators.asList(neo.findNodes(Labels.ALL, NeoProperties.SCIENTIFIC_NAME, scientificName));
  }

  public List<Node> byScientificName(String scientificName, Rank rank) {
    List<Node> names = byScientificName(scientificName);
    names.removeIf(n -> !NeoProperties.getRank(n, Rank.UNRANKED).equals(rank));
    return names;
  }

  public Dataset getDataset() {
    return dataset.get();
  }

  /**
   * Process all nodes in batches with the given callback handler.
   * Every batch is processed in a single transaction which is committed at the end of the batch.
   *
   * If new nodes are created within a batch transaction this will be also be returned to the callback handler at the very end.
   *
   * Iteration is by node value starting from node value 1 to highest.
   *
   * @param label neo4j node label to select nodes by. Use Labels.ALL for all nodes
   * @param batchSize
   * @param callback
   * @return total number of processed nodes.
   */
  public int process(Labels label, final int batchSize, NodeBatchProcessor callback) {
    ExecutorService service =  ThrottledThreadPoolExecutor.newFixedThreadPool(1, 3);
    final AtomicInteger counter = new AtomicInteger();
    try (Transaction tx = neo.beginTx()){
      int batchNum = 0;
      UnmodifiableIterator<List<Node>> batchIter = com.google.common.collect.Iterators.partition(neo.findNodes(label), batchSize);
      while (batchIter.hasNext()) {
        List<Node> batch = batchIter.next();
        // execute batch processing in separate thread to not create nested flat transactions
        service.submit(new BatchCallback(datasetKey, neo, callback, batch, counter));
        batchNum++;
      }
      // await termination
      service.shutdown();
      service.awaitTermination(10, TimeUnit.DAYS);
      LOG.info("Neo processing of {} finished in {} batches", label, batchNum);

    } catch (InterruptedException e) {
      LOG.error("Neo processing interrupted", e);

    } catch (Exception e){
      LOG.error("Neo processing with {} failed", callback.getClass(), e);
      throw e;
    }
    return counter.get();
  }

  static class BatchCallback implements Runnable {
    private final int datasetKey;
    private final AtomicInteger counter;
    private final GraphDatabaseService neo;
    private final NodeBatchProcessor callback;
    private final List<Node> batch;

    BatchCallback(int datasetKey, GraphDatabaseService neo, NodeBatchProcessor callback, List<Node> batch, AtomicInteger counter) {
      this.datasetKey = datasetKey;
      this.counter = counter;
      this.neo = neo;
      this.callback = callback;
      this.batch = batch;
    }

    @Override
    public void run() {
      ImportJob.setMDC(datasetKey);
      try (Transaction tx = neo.beginTx()) {
        LOG.debug("Start new neo processing batch with {}", batch.get(0));
        for (Node n : batch) {
          callback.process(n);
          counter.incrementAndGet();
        }
        tx.success();
        callback.commitBatch(counter.get());

      } finally {
        ImportJob.removeMDC();
      }
    }
  }

  public interface NodeBatchProcessor {
    void process(Node n);

    /**
     * Indicates whether the batch should be committed or not
     * @param counter the total record counter of processed records at this point
     * @return true if the batch should be committed.
     */
    void commitBatch(int counter);
  }

  /**
   * Shuts down the regular neo4j db and opens up neo4j in batch mode.
   * While batch mode is active only writes will be accepted and reads from the store
   * will throw exceptions.
   */
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

  public void endBatchMode() throws NotUniqueRuntimeException {
    try {
      // define indices
      LOG.info("Building lucene index scientificName ...");
      inserter.createDeferredSchemaIndex(Labels.ALL).on(NeoProperties.SCIENTIFIC_NAME).create();
      LOG.info("Building lucene index taxonID ...");
      inserter.createDeferredSchemaIndex(Labels.ALL).on(NeoProperties.TAXON_ID).create();
    } finally {
      // this is when lucene indices are build and thus throws RuntimeExceptions when unique constraints are broken
      // we catch these exceptions below
      inserter.shutdown();
    }

    openNeo();
    // now try to add a taxonID unique constraint. If it fails we will remove offending records
    try {
      buildPrimaryKeyIndex();
    } catch (ConstraintViolationException e) {
      LOG.warn("The inserted dataset contains duplicate ID! Only the first record will be used");
      removeDuplicateKeys();
      buildPrimaryKeyIndex();
    }

    inserter = null;
  }

  private void buildPrimaryKeyIndex() {
    try (Transaction tx = neo.beginTx()){
      LOG.info("Building lucene index ID ...");
      neo.schema().constraintFor(Labels.ALL).assertPropertyIsUnique(NeoProperties.ID).create();
      tx.success();
    }
    try (Transaction tx = neo.beginTx()){
      neo.schema().awaitIndexesOnline(1, TimeUnit.HOURS);
      LOG.debug("Done building lucene index ID");
    }
  }

  private void removeDuplicateKeys() {
    try (Transaction tx = neo.beginTx()){
      Result res = neo.execute("MATCH (n:ALL) WITH n."+NeoProperties.ID +" as id, collect(n) AS nodes WHERE size(nodes) >  1 RETURN nodes");
      res.accept(new Result.ResultVisitor<Exception>() {
        @Override
        public boolean visit(Result.ResultRow row) throws Exception {
          List<Node> nodes = (List<Node>) row.get("nodes");
          Node first = nodes.get(0);
          LOG.info("keep {} {}", first, NeoProperties.getScientificNameWithAuthor(first));
          NeoTaxon t = get(first);
          t.addIssue(Issue.ID_NOT_UNIQUE);
          update(t);

          for (Node n : nodes) {
            if (n.getId() != first.getId()) {
              LOG.info("remove {} with duplicate ID {}", NeoProperties.getID(n), n);
              n.delete();
              taxa.remove(n.getId());
            }
          }
          // continue to visit other nodes
          return true;
        }
      });
      tx.success();

    } catch (Exception e) {
      LOG.error("Failed to remove duplicate records with the same ID ", e);
      throw new NotUniqueRuntimeException("ID");
    }
  }

  public NeoTaxon put(NeoTaxon t) {
    // extract references into ref store before store them
    for (Reference r : t.listReferences()) {
      //TODO: add reference to map if new, replace all props with just the key
      put(r);
    }

    // update neo4j properties either via batch mode or classic
    long nodeId;
    Map<String, Object> props = NeoDbUtils.neo4jProps(t);
    if (isBatchMode()) {
      // batch insert normalizer properties used during normalization
      nodeId = inserter.createNode(props, getNeoLabels(t));

    } else {
      // create neo4j node if needed
      if (t.node == null) {
        t.node = neo.createNode(getNeoLabels(t));
      }
      nodeId = t.node.getId();
      // update neo4j props
      NeoDbUtils.setProperties(t.node, props);
    }
    taxa.put(nodeId, t);

    // use neo4j node ids as keys for both name and taxon
    t.taxon.setKey((int)nodeId);
    if (t.name != null) {
      t.name.setKey(t.taxon.getKey());
    }

    return t;
  }

  /**
   * Updates the taxon object only in the  KVP store, keeping neo4j as it is.
   * throws is taxon did not exist before.
   */
  public void update(NeoTaxon t) {
    Preconditions.checkNotNull(t.node);
    taxa.put(t.node.getId(), t);
  }

  private Labels[] getNeoLabels(NeoTaxon t) {
    return t.isSynonym() ? SYN_LABELS : TAX_LABELS;
  }

  @Override
  public Reference put(Reference r) {
    if (r.getKey() == null) {
      r.setKey(referenceSequence.incrementAndGet());
    }
    references.put(r.getKey(), r);
    // update lookup index
    if (!Strings.isNullOrEmpty(r.getId())) {
      referenceIndex.put(normRef(r.getId()), r);
    }
    if (!Strings.isNullOrEmpty(r.getTitle())) {
      referenceIndex.put(normRef(r.getTitle()), r);
    }
    return r;
  }

  private static String normRef(String idOrTitle) {
    return idOrTitle.replaceAll("[^\\w]+", "").toLowerCase();
  }

  /**
   * Return all NeoTaxa incl a node property to work with the nodeId.
   * Note though that it is not a real neo4j node but just a dummy that contains the id!!!
   * No other neo operations can be done on this node - it would need to be retrieved from the store
   * individually.
   */
  public Stream<NeoTaxon> all() {
    return taxa.entrySet().stream().map(e -> {
      NeoTaxon t = e.getValue();
      t.node = new NodeProxy(null, e.getKey());
      return t;
    });
  }

  @Override
  public Iterable<Reference> refList() {
    return references.values();
  }

  public Reference refByKey(Integer key) {
    return references.getOrDefault(key, null);
  }

  @Override
  public Reference refById(String id) {
    return refByAny(id);
  }

  @Override
  public Reference refByTitle(String title) {
    return refByAny(title);
  }

  private Reference refByAny(String x) {
    return x == null ? null : referenceIndex.getOrDefault(normRef(x), null);
  }

  public Dataset put(Dataset d) {
    // keep existing dataset key & settings
    Dataset old = dataset.get();
    if (old != null) {
      d.setKey(old.getKey());
      d.setCode(old.getCode());
    }
    dataset.set(d);
    return d;
  }

  /**
   * overlay neo4j relations to NeoTaxon instances
   */
  public void updateTaxonStoreWithRelations() {
    try (Transaction tx = getNeo().beginTx()) {
      for (Node n : getNeo().getAllNodes()) {
        NeoTaxon t = get(n);
        t.taxon.setKey((int)n.getId());
        // basionym
        Node bn = getSingleRelated(t.node, RelType.BASIONYM_OF, Direction.INCOMING);
        if (bn != null) {
          NeoTaxon bas = get(bn);
          bas.name.setKey((int)bn.getId());
          t.name.setBasionymKey(bas.name.getKey());
        }

        if (t.node.hasLabel(Labels.SYNONYM)) {
          // accepted, can be multiple
          for (Relationship synRel : t.node.getRelationships(RelType.SYNONYM_OF, Direction.OUTGOING)) {
            if (t.synonym == null) {
              t.synonym = new NeoTaxon.Synonym();
            }
            t.synonym.accepted.add(extractTaxon(synRel.getOtherNode(t.node)));
          }

        } else if (!t.node.hasLabel(Labels.ROOT)){
          // parent
          Node p = getSingleRelated(t.node, RelType.PARENT_OF, Direction.INCOMING);
          t.taxon.setParentKey(extractTaxon(p).getKey());
        }
        // store the updated object directly in MapDB, avoiding unecessary updates to Neo
        taxa.put(t.node.getId(), t);
      }
    }
  }

  private Taxon extractTaxon(Node n) {
    NeoTaxon t = get(n);
    t.taxon.setName(t.name);
    // use neo4j node as key
    t.taxon.setKey((int)n.getId());
    return t.taxon;
  }

  private Node getSingleRelated(Node n, RelType type, Direction dir) {
    try {
      Relationship rel = n.getSingleRelationship(type, dir);
      if (rel != null) {
        return rel.getOtherNode(n);
      }

    } catch (NotFoundException e) {
      // thrown in case of multiple relations, debug
      LOG.debug("Multiple {} {} relations found for {}: {} - {}", dir, type, n, NeoProperties.getID(n), NeoProperties.getScientificNameWithAuthor(n));
      for (Relationship rel : n.getRelationships(type, dir)) {
        Node other = rel.getOtherNode(n);
        LOG.debug("  {} {}/{} - {}",
            dir == Direction.INCOMING ? "<-- "+type.abbrev+" --" : "-- "+type.abbrev+" -->",
            other,
            NeoProperties.getID(other), NeoProperties.getScientificNameWithAuthor(other));
      }
      throw new NormalizationFailedException("Multiple "+dir+" "+type+" relations found for "+NeoProperties.getScientificNameWithAuthor(n), e);
    }
    return null;
  }

  /**
   * Set correct ROOT, PROPARTE and BASIONYM labels for easier access
   */
  public void updateLabels() {
    // set ROOT
    LOG.info("Labelling root nodes");
    String query =  "MATCH (r:TAXON) " +
        "WHERE not ( ()-[:PARENT_OF]->(r) ) " +
        "SET r :ROOT " +
        "RETURN count(r)";
    long count = updateLabel(query);
    LOG.info("Labelled {} root nodes", count);

    // set BASIONYM
    LOG.info("Labelling basionym nodes");
    query = "MATCH (b:ALL)-[:BASIONYM_OF]->() " +
        "SET b :BASIONYM " +
        "RETURN count(b)";
    count = updateLabel(query);
    LOG.info("Labelled {} basionym nodes", count);


    // set PROPARTE_SYNONYM
    LOG.info("Labelling proparte synonym nodes");
    query = "MATCH (s:SYNONYM)-[sr:SYNONYM_OF]->() " +
        "WITH s, count(sr) AS count " +
        "WHERE count > 1 " +
        "SET s :PROPARTE_SYNONYM " +
        "RETURN count";
    count = updateLabel(query);
    LOG.info("Labelled {} pro parte synonym nodes", count);
  }

  private long updateLabel(String query) {
    try (Transaction tx = neo.beginTx()) {
      Result result = neo.execute(query);
      tx.success();
      if (result.hasNext()) {
        return (Long) result.next().values().iterator().next();
      } else {
        return 0;
      }
    }
  }







  public void assignParent(Node parent, Node child) {
    if (parent != null) {
      if (child.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
        // override existing parent!
        Node oldParent=null;
        for (Relationship r : child.getRelationships(RelType.PARENT_OF, Direction.INCOMING)){
          oldParent = r.getOtherNode(child);
          r.delete();
        }
        LOG.error("{} has already a parent {}, override with new parent {}",
            NeoProperties.getScientificNameWithAuthor(child),
            NeoProperties.getScientificNameWithAuthor(oldParent),
            NeoProperties.getScientificNameWithAuthor(parent));

      } else {
        parent.createRelationshipTo(child, RelType.PARENT_OF);
      }

    }
  }

  /**
   * Creates a synonym relationship between the given synonym and the accepted node, updating labels accordingly
   * and also moving potentially existing parent_of relations.
   */
  public void createSynonymRel(Node synonym, Node accepted) {
    synonym.createRelationshipTo(accepted, RelType.SYNONYM_OF);
    synonym.addLabel(Labels.SYNONYM);
    synonym.removeLabel(Labels.TAXON);
    // potentially move the parent relationship of the synonym
    if (synonym.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
      try {
        Relationship rel = synonym.getSingleRelationship(RelType.PARENT_OF, Direction.INCOMING);
        if (rel != null) {
          // check if accepted has a parent relation already
          if (!accepted.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
            assignParent(rel.getStartNode(), accepted);
          }
        }
      } catch (RuntimeException e) {
        // more than one parent relationship exists, should never be the case, sth wrong!
        LOG.error("Synonym {} has multiple parent relationships!", synonym.getId());
        //for (Relationship r : synonym.getRelationships(RelType.PARENT_OF)) {
        //  r.delete();
        //}
      }
    }
  }

  public RankedName createPlaceholder(Origin origin, @Nullable Issue issue) {
    PLACEHOLDER.setRank(Rank.UNRANKED);
    return createDoubtfulFromSource(origin, PLACEHOLDER, null, Rank.GENUS,null, issue);
  }



  /**
   * Creates a new taxon in neo and the name usage kvp using the source usages as a template for the classification properties.
   * Only copies the classification above genus and ignores genus and below!
   * A verbatim usage is created with just the parentNameUsage(ID) values so they can get resolved into proper neo relations later.
   *
   * @param name the new name to be used
   * @param source the taxon source to copy from
   * @param taxonID the optional taxonID to apply to the new node
   * @param excludeRankAndBelow the rank (and all ranks below) to exclude from the source classification
   */
  public RankedName createDoubtfulFromSource(Origin origin, Name name,
                                              @Nullable NeoTaxon source, Rank excludeRankAndBelow, @Nullable String taxonID,
                                              @Nullable Issue issue) {
    NeoTaxon t = NeoTaxon.createTaxon(origin, name, TaxonomicStatus.DOUBTFUL);
    t.taxon.setId(taxonID);
    // copy verbatim classification from source
    if (source != null) {
      if (source.classification != null) {
        t.classification = Classification.copy(source.classification);
        // remove lower ranks
        t.classification.clearRankAndBelow(excludeRankAndBelow);
      }
      // copy parent props from source
      t.verbatim = UnescapedVerbatimRecord.create();
      t.verbatim.setTerm(DwcTerm.parentNameUsageID, source.verbatim.getTerm(DwcTerm.parentNameUsageID));
      t.verbatim.setTerm(DwcTerm.parentNameUsage, source.verbatim.getTerm(DwcTerm.parentNameUsage));
    }
    // add potential issue
    if (issue != null) {
      t.addIssue(issue);
    }

    // store, which creates a new neo node
    put(t);

    return new RankedName(t.node, t.name.getScientificName(), t.name.authorshipComplete(), t.name.getRank());
  }


}

