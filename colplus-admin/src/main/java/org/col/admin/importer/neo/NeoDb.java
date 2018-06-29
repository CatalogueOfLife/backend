package org.col.admin.importer.neo;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import org.apache.commons.io.FileUtils;
import org.col.admin.AdminServer;
import org.col.admin.importer.ImportJob;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.model.*;
import org.col.admin.importer.neo.traverse.StartEndHandler;
import org.col.admin.importer.neo.traverse.Traversals;
import org.col.admin.importer.neo.traverse.TreeWalker;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.common.concurrent.ExecutorUtils;
import org.col.common.concurrent.ThrottledThreadPoolExecutor;
import org.col.common.mapdb.MapDbObjectSerializer;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;
import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphalgo.LabelPropagationProc;
import org.neo4j.graphalgo.UnionFindProc;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.core.NodeProxy;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A persistence mechanism for storing core taxonomy & names properties and relations in an embedded
 * Neo4j database, while keeping a large BLOB of information in a separate MapDB storage.
 * <p>
 * Neo4j does not perform well storing large properties in its node and it is recommended to keep
 * large BLOBs or strings externally: https://neo4j.com/blog/dark-side-neo4j-worst-practices/
 * <p>
 * We use the Kryo library for a very performant binary
 * serialisation with the data keyed under the neo4j node value.
 */
public class NeoDb implements ReferenceStore {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDb.class);
  private static final Labels[] TAX_LABELS = new Labels[]{Labels.ALL, Labels.TAXON};
  private static final Labels[] SYN_LABELS = new Labels[]{Labels.ALL, Labels.SYNONYM};

  private final int datasetKey;
  private final GraphDatabaseBuilder neoFactory;
  private final DB mapDb;
  private final Atomic.Var<Dataset> dataset;
  private final Map<Long, NeoTaxon> taxa;
  private final Map<Integer, Reference> references;
  private final Map<Integer, TermRecord> verbatim;
  private final AtomicInteger verbatimSequence = new AtomicInteger(0);
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
          .valueSerializer(new MapDbObjectSerializer<>(NeoTaxon.class, pool, 256))
          .createOrOpen();
      references = mapDb.hashMap("references")
          .keySerializer(Serializer.INTEGER)
          .valueSerializer(new MapDbObjectSerializer(Reference.class, pool, 128))
          .createOrOpen();
      verbatim = mapDb.hashMap("verbatim")
          .keySerializer(Serializer.INTEGER)
          .valueSerializer(new MapDbObjectSerializer(TermRecord.class, pool, 128))
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

    try {
      GraphDatabaseAPI gdb = (GraphDatabaseAPI) neo;
      gdb.getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(UnionFindProc.class);
      gdb.getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(LabelPropagationProc.class);

    } catch (KernelException e) {
      LOG.warn("Unable to register neo4j algorithms", e);
    }
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
    if (id != null) {
      Node n = byID(id);
      if (n != null) {
        return get(n);
      }
    }
    return null;
  }

  /**
   * @return a collection of all name relations with name key using node ids.
   */
  public List<NameRelation> relations(Node n) {
    return Iterables.stream(n.getRelationships())
        .filter(r -> RelType.valueOf(r.getType().name()).nomRelType != null)
        .map(r -> {
          NameRelation nr = new NameRelation();
          nr.setType(RelType.valueOf(r.getType().name()).nomRelType);
          nr.setNameKey( (int) r.getStartNodeId());
          nr.setRelatedNameKey( (int) r.getEndNodeId());
          nr.setNote( (String) r.getProperty(NeoProperties.NOTE, null));
          nr.setPublishedInKey( (Integer) r.getProperty(NeoProperties.REF_KEY, null));
          if (r.hasProperty(NeoProperties.VERBATIM_KEY)) {
            nr.setVerbatimKey((Integer)r.getProperty(NeoProperties.VERBATIM_KEY));
          }
          return nr;
        })
        .collect(Collectors.toList());
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
   * @return the matching nodes with the scientificName
   */
  public List<Node> byScientificName(String scientificName) {
    return Iterators.asList(neo.findNodes(Labels.ALL, NeoProperties.SCIENTIFIC_NAME, scientificName));
  }

  /**
   * @return the matching nodes with the scientificName and given label
   */
  public List<Node> byScientificName(Labels label, String scientificName) {
    return Iterators.asList(neo.findNodes(label, NeoProperties.SCIENTIFIC_NAME, scientificName));
  }

  public List<Node> byScientificName(Labels label, String scientificName, Rank rank, boolean inclUnranked) {
    List<Node> names = byScientificName(label, scientificName);
    names.removeIf(n -> {
      Rank r = NeoProperties.getRank(n, Rank.UNRANKED);
      if (inclUnranked) {
        return !r.equals(rank) && r != Rank.UNRANKED;
      } else {
        return !r.equals(rank);
      }
    });
    return names;
  }

  public Dataset getDataset() {
    return dataset.get();
  }

  /**
   * Process all nodes in batches with the given callback handler.
   * Every batch is processed in a single transaction which is committed at the end of the batch.
   * To avoid nested flat transactions we execute all batches in a separate executor thread.
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
      Future<Integer> curr = null;
      while (batchIter.hasNext()) {
        checkIfInterrupted();
        List<Node> batch = batchIter.next();
        // execute batch processing in separate thread to not create nested flat transactions
        Future<Integer> f = service.submit(new BatchCallback(datasetKey, neo, callback, batch, counter));
        if (curr != null) {
          // wait til the last job finishes - this might throw an exception
          curr.get();
        }
        curr = f;
        batchNum++;
      }
      // very last batch
      if (curr != null) {
        curr.get();
      }

      // mark good for commit
      tx.success();
      LOG.info("Neo processing of {} finished in {} batches with {} records", label, batchNum, counter.get());

    } catch (InterruptedException e) {
      LOG.error("Neo processing interrupted", e);
      service.shutdownNow();

    } catch (ExecutionException e){
      LOG.error("Neo processing with {} failed", callback.getClass(), e);
      throw new RuntimeException(e.getCause());

    } finally {
      ExecutorUtils.shutdown(service, AdminServer.MILLIS_TO_DIE/2, TimeUnit.MILLISECONDS);
    }
    return counter.get();
  }

  private void checkIfInterrupted() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Neo thread was cancelled/interrupted");
    }
  }

  /**
   * Removes the neo4j node with all its relations and all entities stored under this node like NeoTaxon.
   */
  public void remove(Node n) {
    taxa.remove(n.getId());
    int counter = 0;
    for (Relationship rel : n.getRelationships()) {
      rel.delete();
      counter++;
    }
    n.delete();
    LOG.debug("Deleted {} from store with {} relations", n, counter);
  }

  private static class BatchCallback implements Callable<Integer> {
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
    public Integer call() throws Exception {
      ImportJob.setMDC(datasetKey);
      try (Transaction tx = neo.beginTx()) {
        LOG.debug("Start new neo processing batch with {} nodes, first={}", batch.size(), batch.get(0));
        for (Node n : batch) {
          callback.process(n);
          counter.incrementAndGet();
        }
        tx.success();
        callback.commitBatch(counter.get());

      } catch (Throwable e) {
        LOG.error("Failed to process batch with {} nodes, first={}", batch.size(), batch.get(0), e);
        throw e;

      } finally {
        ImportJob.removeMDC();
      }
      return counter.get();
    }
  }

  public interface NodeBatchProcessor {
    void process(Node n);

    /**
     * Indicates whether the batch should be committed or not
     * @param counter the total record counter of processed records at this point
     * @return true if the batch should be committed.
     */
    void commitBatch(int counter) throws InterruptedException;
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
      throw new RuntimeException(e);
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
      LOG.warn("The inserted dataset contains duplicate IDs! Only the first record will be used");
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
          addIssues(t.name, Issue.ID_NOT_UNIQUE);
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

    // use neo4j node ids as keys for both name and taxon
    t.taxon.setKey((int)nodeId);
    if (t.name != null) {
      t.name.setKey(t.taxon.getKey());
    }

    taxa.put(nodeId, t);

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

  public void assignKey(TermRecord v) {
    if (v.getKey() == null) {
      v.setKey(verbatimSequence.incrementAndGet());
    }
  }
  public void put(TermRecord v) {
    if (v.hasChanged()) {
      assignKey(v);
      verbatim.put(v.getKey(), v);
    }
  }

  /**
   * Creates a new name relation linking the 2 given nodes.
   * The note and publishedInKey values are stored as relation properties
   */
  public void createNameRel(Node n1, Node n2, NeoNameRel rel) {
    Relationship r = n1.createRelationshipTo(n2, rel.getType());
    if (rel.getVerbatimKey() != null) {
      r.setProperty(NeoProperties.VERBATIM_KEY, rel.getVerbatimKey());
    }
    if (rel.getRefKey() != null) {
      r.setProperty(NeoProperties.REF_KEY, rel.getRefKey());
    }
    if (rel.getNote() != null) {
      r.setProperty(NeoProperties.NOTE, rel.getNote());
    }
  }

  @Override
  public Reference put(Reference r) {
    if (r.getKey() == null) {
      r.setKey(referenceSequence.incrementAndGet());
    }
    references.put(r.getKey(), r);
    // update lookup index for id and title
    if (!Strings.isNullOrEmpty(r.getId())) {
      referenceIndex.put(normRef(r.getId()), r);
    }
    return r;
  }

  /**
   * @return the verbatim key as assigned from verbatimSequence
   */
  public TermRecord getVerbatim(int key) {
    TermRecord rec = verbatim.get(key);
    if (rec != null) {
      rec.setHashCode();
    }
    return rec;
  }

  public void addIssues(VerbatimEntity ent, Issue... issue) {
    if (issue != null && ent.getVerbatimKey() != null) {
      TermRecord v = getVerbatim(ent.getVerbatimKey());
      if (v == null) {
        LOG.warn("No verbatim exists for {} with verbatim key {}", ent.getClass().getSimpleName(), ent.getVerbatimKey());
      } else {
        for (Issue is : issue) {
          v.addIssue(is);
        }
        put(v);
      }
    }
  }

  private static String normRef(String idOrTitle) {
    return idOrTitle.trim();
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

  public Iterable<TermRecord> verbatimList() {
    return verbatim.values();
  }

  @Override
  public Reference refByKey(int key) {
    return references.getOrDefault(key, null);
  }

  @Override
  public Reference refById(String id) {
    return id == null ? null : referenceIndex.getOrDefault(normRef(id), null);
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

  public Set<Long> nodeIdsOutsideTree() throws InterruptedException {
    final Set<Long> treeNodes = new HashSet<>();
    TreeWalker.walkTree(neo, new StartEndHandler() {
      @Override
      public void start(Node n) {
        treeNodes.add(n.getId());
      }

      @Override
      public void end(Node n) { }
    });

    final Set<Long> nodes = new HashSet<>();
    try (Transaction tx = getNeo().beginTx()) {
      for (Node n : neo.getAllNodes()) {
        if (!treeNodes.contains(n.getId())) {
          nodes.add(n.getId());
        }
      }
    }
    return nodes;
  }

  /**
   * overlay neo4j relations to NeoTaxon instances
   */
  private void updateTaxonStoreWithRelations() {
    try (Transaction tx = getNeo().beginTx()) {
      for (Node n : getNeo().getAllNodes()) {
        NeoTaxon t = get(n);
        t.taxon.setKey((int)n.getId());
        if (t.node.hasLabel(Labels.SYNONYM)) {
          if (t.synonym == null) {
            t.synonym = new Synonym();
            t.synonym.setStatus(TaxonomicStatus.SYNONYM);
            t.synonym.setAccordingTo(t.taxon.getAccordingTo());
          }

        } else if (!t.node.hasLabel(Labels.ROOT)){
          // parent
          Node p = getSingleRelated(t.node, RelType.PARENT_OF, Direction.INCOMING);
          t.taxon.setParentKey(extractTaxon(p).getKey());
        }
        // store the updated object directly in MapDB, avoiding unecessary updates to Neo
        taxa.put(t.node.getId(), t);
      }
      tx.success();
    }
  }

  /**
   * Sets the same neo node id for a given cluster of homotypic names derived from name relations and synonym[homotpic=true] relations.
   * We first go thru all synonyms with the homotypic flag to determine the keys and then add all missing basionym nodes.
   */
  private void updateHomotypicNameKeys() {
    int counter = 0;
    LOG.debug("Setting shared homotypic name keys");
    try (Transaction tx = neo.beginTx()) {
      // first homotypic synonym rels
      for (Node syn : Iterators.loop(getNeo().findNodes(Labels.SYNONYM))) {
        NeoTaxon tsyn = get(syn);
        if (tsyn.homotypic) {
          NeoTaxon acc = get(syn.getSingleRelationship(RelType.SYNONYM_OF, Direction.OUTGOING).getEndNode());
          int homoKey;
          if (acc.name.getHomotypicNameKey() == null ) {
            homoKey = (int) acc.node.getId();
            acc.name.setHomotypicNameKey(homoKey);
            update(acc);
            counter++;
          } else {
            homoKey = acc.name.getHomotypicNameKey();
          }
          tsyn.name.setHomotypicNameKey(homoKey);
          update(tsyn);
        }
      }
      LOG.info("{} homotypic groups found via homotypic synonym relations", counter);

      // now name relations, reuse keys if existing
      counter = 0;
      for (Node n : getNeo().getAllNodes()) {
        // check if this node has a homotypic group already in which case we can skip it
        NeoTaxon start = get(n);
        if (start.name.getHomotypicNameKey() != null) {
          continue;
        }
        // query homotypic group excluding start node
        List<NeoTaxon> group = Traversals.HOMOTYPIC_GROUP
            .traverse(n)
            .nodes()
            .stream()
            .map(this::get)
            .collect(Collectors.toList());
        if (!group.isEmpty()) {
          // we have more than the starting node so we do process, add starting node too
          group.add(start);
          // determine existing or new key to be shared
          Integer homoKey = null;
          for (NeoTaxon t : group) {
            if (t.name.getHomotypicNameKey() != null) {
              if (homoKey == null) {
                homoKey = t.name.getHomotypicNameKey();
              } else if (!homoKey.equals(t.name.getHomotypicNameKey())){
                LOG.warn("Several homotypic name keys found in the same homotypic name group for {}", NeoProperties.getScientificNameWithAuthor(n));
              }
            }
          }
          if (homoKey == null) {
            homoKey = (int) n.getId();
            counter++;
          }
          // update entire group with key
          for (NeoTaxon t : group) {
            if (t.name.getHomotypicNameKey() == null) {
              t.name.setHomotypicNameKey(homoKey);
              update(t);
            }
          }
        }
      }
      LOG.info("{} additional homotypic groups found via name relations", counter);
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
   * Sync taxon KVP storee with neo4j relations, setting correct neo4j labels, homotypic keys etc
   * Set correct ROOT, PROPARTE and BASIONYM labels for easier access
   */
  public void sync() {
    updateLabels();
    updateTaxonStoreWithRelations();
    updateHomotypicNameKeys();
  }

  private void updateLabels() {
    // set ROOT
    LOG.debug("Labelling root nodes");
    String query =  "MATCH (r:TAXON) " +
        "WHERE not ( ()-[:PARENT_OF]->(r) ) " +
        "SET r :ROOT " +
        "RETURN count(r)";
    long count = updateLabel(query);
    LOG.info("Labelled {} root nodes", count);

    // set BASIONYM
    LOG.debug("Labelling basionym nodes");
    query = "MATCH (b:ALL)<-[:HAS_BASIONYM]-() " +
        "SET b :BASIONYM " +
        "RETURN count(b)";
    count = updateLabel(query);
    LOG.info("Labelled {} basionym nodes", count);
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

  /**
   * Returns an iterator over all relations of a given type.
   * Requires a valid neo transaction to exist outside of this method call.
   */
  public ResourceIterator<Relationship> iterRelations(RelType type) {
    String query = "MATCH ()-[rel:" + type.name() + "]->() RETURN rel";
    Result result = neo.execute(query);
    return result.columnAs("rel");
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
   * Homotypic relation flag is not set and expected to be added if known to be homotypic.
   *
   * @return newly created synonym relation
   */
  public Relationship createSynonymRel(Node synonym, Node accepted) {
    Relationship synRel = synonym.createRelationshipTo(accepted, RelType.SYNONYM_OF);
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
    return synRel;
  }

  /**
   * List all accepted taxa of a potentially prop parte synonym
   */
  public List<RankedName> accepted(Node synonym) {
    return Traversals.ACCEPTED.traverse(synonym).nodes().stream()
        .map(NeoProperties::getRankedName)
        .collect(Collectors.toList());
  }

  /**
   * Creates a new taxon in neo and the name usage kvp using the source usages as a template for the classification properties.
   * Only copies the classification above genus and ignores genus and below!
   * A verbatim usage is created with just the parentNameUsage(ID) values so they can get resolved into proper neo relations later.
   *
   * @param name the new name to be used
   * @param source the taxon source to copy from
   * @param excludeRankAndBelow the rank (and all ranks below) to exclude from the source classification
   */
  public RankedName createDoubtfulFromSource(Origin origin, Name name, @Nullable NeoTaxon source, Rank excludeRankAndBelow) {
    NeoTaxon t = NeoTaxon.createTaxon(origin, name, true);
    // copy verbatim classification from source
    if (source != null) {
      if (source.classification != null) {
        t.classification = Classification.copy(source.classification);
        // remove lower ranks
        t.classification.clearRankAndBelow(excludeRankAndBelow);
      }
      // copy parent props from source
      if (t.taxon.getVerbatimKey() != null) {
        TermRecord sourceTerms = getVerbatim(t.taxon.getVerbatimKey());
        TermRecord copyTerms = new TermRecord();
        copyTerms.put(DwcTerm.parentNameUsageID, sourceTerms.get(DwcTerm.parentNameUsageID));
        copyTerms.put(DwcTerm.parentNameUsage, sourceTerms.get(DwcTerm.parentNameUsage));
        put(copyTerms);
        t.taxon.setVerbatimKey(copyTerms.getKey());
      }
    }
    // store, which creates a new neo node
    put(t);

    return new RankedName(t.node, t.name.getScientificName(), t.name.authorshipComplete(), t.name.getRank());
  }


}

