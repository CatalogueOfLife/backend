package life.catalogue.importer.neo;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.importer.IdGenerator;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.neo.NodeBatchProcessor.BatchConsumer;
import life.catalogue.importer.neo.model.*;
import life.catalogue.importer.neo.printer.PrinterUtils;
import life.catalogue.importer.neo.traverse.Traversals;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphalgo.LabelPropagationProc;
import org.neo4j.graphalgo.UnionFindProc;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Preconditions;
import com.google.common.collect.UnmodifiableIterator;

import org.slf4j.MDC;

import static life.catalogue.common.tax.NameFormatter.HYBRID_MARKER;

/**
 * A persistence mechanism for storing core taxonomy & names propLabel and relations in an embedded
 * Neo4j database, while keeping a large BLOB of information in a separate MapDB storage.
 * <p>
 * Neo4j does not perform well storing large propLabel in its node and it is recommended to keep
 * large BLOBs or strings externally: https://neo4j.com/blog/dark-side-neo4j-worst-practices/
 * <p>
 * We use the Kryo library for a very performant binary
 * serialisation with the data keyed under the neo4j node value.
 */
public class NeoDb {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDb.class);

  private final int datasetKey;
  private final int attempt;
  private final GraphDatabaseBuilder neoFactory;
  private final DB mapDb;
  private final File neoDir;
  private final Pool<Kryo> pool;
  private BatchInserter inserter;
  public final int batchSize;
  public final int batchTimeout;

  // verbatimKey sequence and lookup
  private final AtomicInteger verbatimSequence = new AtomicInteger(0);
  private final Map<Integer, VerbatimRecord> verbatim;
  private final ReferenceMapStore references;
  private final MapStore<TypeMaterial> typeMaterial;
  private final NeoNameStore names;
  private final NeoUsageStore usages;

  private final IdGenerator idGen = new IdGenerator("~");

  private GraphDatabaseService neo;
  private final AtomicInteger neoCounter = new AtomicInteger(0);
  private Node devNullNode;


  /**
   * @param mapDb
   * @param neoDir
   * @param neoFactory
   * @param batchTimeout in minutes
   */
  NeoDb(int datasetKey, int attempt, DB mapDb, File neoDir, GraphDatabaseBuilder neoFactory, int batchSize, int batchTimeout) {
    this.datasetKey = datasetKey;
    this.attempt = attempt;
    this.neoFactory = neoFactory;
    this.neoDir = neoDir;
    this.mapDb = mapDb;
    this.batchSize = batchSize;
    this.batchTimeout = batchTimeout;
    
    try {
      pool = new NeoKryoPool(8);
      verbatim = mapDb.hashMap("verbatim")
          .keySerializer(Serializer.INTEGER)
          .valueSerializer(new MapDbObjectSerializer(VerbatimRecord.class, pool, 128))
          .createOrOpen();
      references = new ReferenceMapStore(mapDb, pool, this::addIssues);
      typeMaterial = new MapStore<>(TypeMaterial.class, "tm", mapDb, pool, this::addIssues);

      openNeo();

      usages = new NeoUsageStore(mapDb, "usages", pool, idGen, this);
      
      names = new NeoNameStore(mapDb, "names", pool, idGen, this);

    } catch (Exception e) {
      LOG.error("Failed to initialize a new NeoDB", e);
      close();
      throw e;
    }
  }
  
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
    closeNeoQuietly();
    // make sure we don't leave any batch inserter alive.
    // Should have been closed, but we don't want to take the risk-
    if (inserter != null) {
      try {
        inserter.shutdown();
        LOG.warn("Batch inserter was not closed properly at {}. Closed now.", neoDir.getAbsolutePath());
      } catch (IllegalStateException e) {
        // it was closed already but somehow the instance was not removed;)
      } finally {
        inserter = null;
      }
    }
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

    // make sure we have a working devNull node
    try (Transaction tx = neo.beginTx()) {
      ResourceIterator<Node> iter = neo.findNodes(Labels.DEV_NULL);
      if (iter.hasNext()) {
        devNullNode = iter.next();
      } else {
        devNullNode = neo.createNode(Labels.DEV_NULL);
      }
      tx.success();
    }
  }

  private void closeNeoQuietly() {
    try {
      if (neo != null) {
        neo.shutdown();
        LOG.debug("Closed NormalizerStore for directory {}", neoDir.getAbsolutePath());
        neo = null;
      }
    } catch (Exception e) {
      LOG.error("Failed to close neo4j {}", neoDir.getAbsolutePath(), e);
    }
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public GraphDatabaseService getNeo() {
    return neo;
  }
  
  public NeoNameStore names() {
    return names;
  }
  
  public NeoCRUDStore<NeoUsage> usages() {
    return usages;
  }

  public ReferenceMapStore references() {
    return references;
  }

  public MapStore<TypeMaterial> typeMaterial() {
    return typeMaterial;
  }

  public NeoUsage usageWithName(String usageID) {
    return usageWithName(usages().nodeByID(usageID));
  }

  public Node getDevNullNode() {
    return devNullNode;
  }

  public NeoUsage usageWithName(Node usageNode) {
    NeoUsage u = usages().objByNode(usageNode);
    if (u != null) {
      NeoName nn = nameByUsage(usageNode);
      u.usage.setName(nn.getName());
      u.nameNode = nn.node;
    }
    return u;
  }

  /**
   * @return a collection of all name relations with name key using node ids.
   */
  public NameRelation toNameRelation(Relationship r) {
    NameRelation nr = new NameRelation();
    nr.setType(RelType.valueOf(r.getType().name()).nomRelType);
    nr.setNameId(names.objByNode(r.getStartNode()).getId());
    nr.setRelatedNameId(names.objByNode(r.getEndNode()).getId());
    nr.setRemarks((String) r.getProperty(NeoProperties.NOTE, null));
    return update(r, nr);
  }

  public TaxonConceptRelation toConceptRelation(Relationship r) {
    TaxonConceptRelation tr = new TaxonConceptRelation();
    tr.setType(RelType.valueOf(r.getType().name()).taxRelType);
    tr.setTaxonId(usages.objByNode(r.getStartNode()).getId());
    tr.setRelatedTaxonId(usages.objByNode(r.getEndNode()).getId());
    tr.setRemarks((String) r.getProperty(NeoProperties.NOTE, null));
    return update(r, tr);
  }

  public SpeciesInteraction toSpeciesInteraction(Relationship r) {
    SpeciesInteraction tr = new SpeciesInteraction();
    tr.setType(RelType.valueOf(r.getType().name()).specInterType);
    tr.setTaxonId(usages.objByNode(r.getStartNode()).getId());
    // related id could point to the dummy node - keep those null
    if (!r.getEndNode().hasLabel(Labels.DEV_NULL)) {
      tr.setRelatedTaxonId(usages.objByNode(r.getEndNode()).getId());
    }
    tr.setRelatedTaxonScientificName((String) r.getProperty(NeoProperties.SCINAME, null));
    tr.setRemarks((String) r.getProperty(NeoProperties.NOTE, null));
    return update(r, tr);
  }

  private <T extends DatasetScopedEntity<Integer> & VerbatimEntity & Referenced> T update(Relationship r, T obj) {
    obj.setDatasetKey(datasetKey);
    obj.setReferenceId((String) r.getProperty(NeoProperties.REF_ID, null));
    if (r.hasProperty(NeoProperties.VERBATIM_KEY)) {
      obj.setVerbatimKey((Integer) r.getProperty(NeoProperties.VERBATIM_KEY));
    }
    return obj;
  }
  
  /**
   * @return a collection of all name relations for the given name node with NameRelation.key using node ids.
   */
  public List<NameRelation> nameRelations(Node nameNode) {
    return Iterables.stream(nameNode.getRelationships())
        .filter(r -> RelType.valueOf(r.getType().name()).isNameRel())
        .map(this::toNameRelation)
        .collect(Collectors.toList());
  }


  /**
   * Returns an iterator over all bare name nodes.
   * As we return a resource iterator make sure that there is an open neo transaction when you call this method.
   */
  public ResourceIterator<Node> bareNames() {
    final String query = "MATCH (n:NAME) WHERE NOT (n)<-[:HAS_NAME]-() RETURN DISTINCT n";
    Result result = neo.execute(query);
    return result.columnAs("n");
  }

  /**
   * Retuns a list of usage nodes that have a matching scientific name, rank & authorship.
   * A prefixed hybrid symbol will be ignored in both the query name and stored names.
   */
  public Set<Node> usagesByName(String scientificName, @Nullable String authorship, @Nullable Rank rank, boolean inclUnranked) {
    Set<Node> names = names().nodesByName(scientificName);
    if (scientificName.charAt(0) != HYBRID_MARKER) {
      // try also to find the hybrid version of any monomial
      names.addAll( names().nodesByName(HYBRID_MARKER + " " + scientificName));
    }
    // filter ranks
    if (rank != null) {
      names.removeIf(n -> {
        Rank r = NeoProperties.getRank(n, Rank.UNRANKED);
        if (inclUnranked) {
          return !r.equals(rank) && r != Rank.UNRANKED;
        } else {
          return !r.equals(rank);
        }
      });
    }
    // filter authorship
    if (authorship != null) {
      names.removeIf(n -> !authorship.equalsIgnoreCase(NeoProperties.getAuthorship(n)));
    }

    Set<Node> taxa = new HashSet<>();
    for (Node n : names) {
      taxa.addAll(usageNodesByName(n));
    }
    return taxa;
  }
  
  /**
   * Process all nodes in batches with the given callback handler.
   * Every batch is processed in a single transaction which is committed at the end of the batch.
   * To avoid nested flat transactions we execute all batches in a separate consumer thread.
   * <p>
   * If new nodes are created within a batch transaction this will be also be returned to the callback handler at the very end.
   * <p>
   * Iteration is by node value starting from node value 1 to highest.
   *
   * @param label neo4j node label to select nodes by. Use NULL for all nodes
   * @param batchSize
   * @param callback
   * @return total number of processed nodes.
   */
  public int process(@Nullable Labels label, final int batchSize, NodeBatchProcessor callback) throws InterruptedException {
    final BlockingQueue<List<Node>> queue = new LinkedBlockingQueue<>(3);
    BatchConsumer consumer = new BatchConsumer(datasetKey, attempt, neo, callback, queue, Thread.currentThread());
    Thread consThread = new Thread(consumer, "neodb-processor-" + datasetKey);

    try (Transaction tx = neo.beginTx()){
      consThread.start();
      final ResourceIterator<Node> iter = label == null ? neo.getAllNodes().iterator() : neo.findNodes(label);
      UnmodifiableIterator<List<Node>> batchIter = com.google.common.collect.Iterators.partition(iter, batchSize);

      while (batchIter.hasNext() && consThread.isAlive()) {
        checkIfInterrupted();
        List<Node> batch = batchIter.next();
        if (!queue.offer(batch, batchTimeout, TimeUnit.MINUTES)) {
          LOG.error("Failed to offer new batch {} of size {} within {} minutes for neodb processing by {}", consumer.getBatchCounter(), batch.size(), batchTimeout, callback);
          LOG.info("Nodes: {}", batch.stream()
              .map(NeoProperties::getScientificNameWithAuthor)
              .collect(Collectors.joining("; "))
          );
          throw new RuntimeException("Failed to offer new batch for neodb processing by " + callback);
        }
      }
      if (consThread.isAlive()) {
        queue.put(BatchConsumer.POISON_PILL);
      }
      consThread.join();
      
      // mark good for commit
      tx.success();
      LOG.info("Neo processing of {} finished in {} batches with {} records", label, consumer.getBatchCounter(), consumer.getRecordCounter());
      
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();  // set interrupt flag back
      LOG.error("Neo processing interrupted", e);
      if (consThread.isAlive()) {
        consThread.interrupt();
      }
    }
    
    if (consumer.hasError()) {
      throw consumer.getError();
    }
    
    return consumer.getRecordCounter();
  }
  
  private void checkIfInterrupted() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Neo thread was cancelled/interrupted");
    }
  }
  
  /**
   * Shuts down the regular neo4j db and opens up neo4j in batch mode.
   * While batch mode is active only writes will be accepted and reads from the store
   * will throw exceptions.
   */
  public void startBatchMode() {
    try {
      closeNeoQuietly();
      LOG.info("Open batch inserter for {}", neoDir);
      inserter = BatchInserters.inserter(neoDir);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public boolean isBatchMode() {
    return inserter != null;
  }
  
  public void endBatchMode() throws NotUniqueRuntimeException, InterruptedException {
    LOG.info("Shutting down batch inserter for {} ...", neoDir);
    try {
      inserter.shutdown();
    } catch (Exception e) {
      // the BatchInserter shutdown flushes data to disk, so this involves blocking IO operations which can
      // a) throw an interrupted exception during shutdown and
      // b) throw some IOException, e.g. UnderlyingStorageException "No space left on device"
      // We'll have to make sure the inserter is properly closed and all resources are free'd up.
      // see https://github.com/CatalogueOfLife/backend/issues/1147 and https://github.com/CatalogueOfLife/backend/issues/1132
      if (Exceptions.containsInstanceOf( e, InterruptedException.class)) {
        LOG.warn("Shutdown of batch inserter was interrupted. Trying again", e);
        try {
          inserter.shutdown();
        } catch (IllegalStateException ex) {
          // Batch inserter already has shutdown - we are good, ignore
        }
        throw new InterruptedException("Shutdown of batch inserter was interrupted");
      }
      LOG.error("Shutdown of batch inserter failed", e);
      throw e;

    } finally {
      inserter = null;
    }
    LOG.info("Neo batch inserter closed, data flushed to disk");
    openNeo();
  }
  
  /**
   * Creates both a name and a usage neo4j node.
   * The name node is returned while the usage node is set on the NeoUsage object.
   * The name instance is taken from the usage object which is removed from the usage.
   *
   * If the usage ID is not unique a name will be created, but not a usage. In this case the usage id is reset to null.
   *
   * @return the created name node or null if it could not be created
   */
  public Node createNameAndUsage(NeoUsage u) {
    Preconditions.checkArgument(u.getNode() == null, "NeoUsage already has a neo4j node");
    Preconditions.checkArgument(u.nameNode == null, "NeoUsage already has a neo4j name node");
    Preconditions.checkNotNull(u.usage.getName(), "NeoUsage with name required");

    // is no true verbatim record existed create a new one to hold issues for validation etc.
    if (u.usage.getVerbatimKey() == null) {
      VerbatimRecord v = new VerbatimRecord();
      put(v);
      u.usage.setVerbatimKey(v.getId());
    }
    // first create the name in a new node
    NeoName nn = new NeoName(u.usage.getName());
    if (nn.getId() == null) {
      nn.setId(u.getId());
    }
    if (nn.getVerbatimKey() == null) {
      nn.setVerbatimKey(u.getVerbatimKey());
    }
    if (nn.getName().getOrigin() == null) {
      nn.getName().setOrigin(u.asNameUsageBase().getOrigin());
    }
    nn.homotypic = u.homotypic;
    u.nameNode = names.create(nn);
  
    if (u.nameNode != null) {
      // remove name from usage & create it which results in a new node on the usage
      u.usage.setName(null);
      if (!u.usage.isBareName()) {
        var unode = usages.create(u);
        if (unode == null) {
          u.setId(null); // non unique id
        }
      }
    } else {
      LOG.debug("Skip usage {} as no name node was created for {}", u.getId(), nn.getName().getLabel());
    }
    return u.nameNode;
  }
  
  /**
   * Removes the neo4j node with all its relations and all entities stored under this node
   * i.e. NeoUsage and NeoName.
   */
  public void remove(Node n) {
    // first remove mapdb entries
    if (n.hasLabel(Labels.NAME)) {
      names().remove(n);
    } else if (n.hasLabel(Labels.USAGE)) {
      usages().remove(n);
    }
    // now remove all neo relations
    int counter = 0;
    for (Relationship rel : n.getRelationships()) {
      rel.delete();
      counter++;
    }
    // and finally the node
    String labels = NeoDbUtils.labelsToString(n);
    n.delete();
    LOG.debug("Deleted {}{} from store with {} relations", labels, n, counter);
  }

  Node createNode(PropLabel data) {
    Node n;
    if (isBatchMode()) {
      // batch insert normalizer propLabel used during normalization
      long nodeId = inserter.createNode(data, data.getLabels());
      n = new NodeMock(nodeId);
    } else {
      // create neo4j node and update its propLabel
      n = neo.createNode(data.getLabels());
      NeoDbUtils.addProperties(n, data);
    }
    neoCounter.incrementAndGet();
    return n;
  }
  
  /**
   * Updates a node by adding properties and/or labels
   */
  void updateNode(long nodeId, PropLabel data) {
    if (!data.isEmpty()) {
      if (isBatchMode()) {
        if (data.getLabels() != null) {
          Label[] all = ArrayUtils.addAll(data.getLabels(),
              com.google.common.collect.Iterables.toArray(inserter.getNodeLabels(nodeId), Label.class)
          );
          inserter.setNodeLabels(nodeId, all);
        }
        if (data.size()>0) {
          data.putAll(inserter.getNodeProperties(nodeId));
          inserter.setNodeProperties(nodeId, data);
        }
      } else {
        Node n = neo.getNodeById(nodeId);
        NeoDbUtils.addProperties(n, data);
        NeoDbUtils.addLabels(n, data.getLabels());
      }
    }
  }
  
  /**
   * Updates a node by adding properties and/or labels
   */
  void createRel(Node node1, Node node2, RelationshipType type) {
      if (isBatchMode()) {
        inserter.createRelationship(node1.getId(), node2.getId(), type, null);
      } else {
        node1.createRelationshipTo(node2, type);
      }
  }
  
  /**
   * @return a node which is a dummy proxy only with just an id while we are in batch mode.
   */
  Node nodeById(long nodeId) {
    return isBatchMode() ? new NodeMock(nodeId) : neo.getNodeById(nodeId);
  }
  
  /**
   * Creates or updates a verbatim record.
   * If created a new key is issued.
   */
  public void put(VerbatimRecord v) {
    if (v.hasChanged()) {
      if (v.getId() == null) {
        v.setId(verbatimSequence.incrementAndGet());
      }
      verbatim.put(v.getId(), v);
      v.setHashCode();
    }
  }

  /**
   * Creates a new name or taxon relation linking the 2 given nodes.
   * Additional NeoRel properties are stored as relation properties.
   */
  public void createNeoRel(Node n1, Node n2, NeoRel rel) {
    Map<String, Object> props = NeoDbUtils.neo4jProps(rel);
    if (isBatchMode()) {
      inserter.createRelationship(n1.getId(), n2.getId(), rel.getType(), props);
    } else {
      Relationship r = n1.createRelationshipTo(n2, rel.getType());
      NeoDbUtils.addProperties(r, props);
    }
  }

  /**
   * @return the verbatim record belonging to the requested key as assigned from verbatimSequence
   */
  public VerbatimRecord getVerbatim(int key) {
    VerbatimRecord rec = verbatim.get(key);
    if (rec != null) {
      rec.setHashCode();
    }
    return rec;
  }
  
  /**
   * @return a lazy supplier for the verbatim record belonging to the requested key as assigned from verbatimSequence
   */
  public Supplier<VerbatimRecord> verbatimSupplier(int key) {
    return new Supplier<VerbatimRecord>() {
      @Override
      public VerbatimRecord get() {
        return getVerbatim(key);
      }
    };
  }

  public NeoName addNameIssues(Node node, Issue... issue) {
    NeoName nn = names.objByNode(node);
    addIssues(nn, issue);
    return nn;
  }

  public NeoUsage addUsageIssues(Node node, Issue... issue) {
    NeoUsage nu = usages.objByNode(node);
    addIssues(nu, issue);
    return nu;
  }

  public void addIssues(VerbatimEntity ent, Issue... issue) {
    addIssues(ent.getVerbatimKey(), issue);
  }
  
  public void addIssues(Integer verbatimKey, Issue... issue) {
    if (verbatimKey != null) {
      VerbatimRecord v = getVerbatim(verbatimKey);
      if (v == null) {
        LOG.warn("No verbatim exists for verbatim key {}", verbatimKey);
      } else {
        for (Issue is : issue) {
          if (is != null) {
            v.addIssue(is);
          }
        }
        put(v);
      }
    }
  }

  public Iterable<VerbatimRecord> verbatimList() {
    return verbatim.values();
  }

  public Stream<VerbatimRecord> verbatimList(Term rowType) {
    return verbatim.values().stream().filter(v -> v.getType().equals(rowType));
  }

  /**
   * @return a stream of name nodes which have no has_name relation to any usage
   */
  public Stream<Node> bareNameNodes() {
    final String query = "MATCH (n:NAME) WHERE NOT (n)<-[:HAS_NAME]-() RETURN n";
    Result result = neo.execute(query);
    return result.<Node>columnAs("n").stream();
  }
  
  /**
   * overlay neo4j relations to NeoTaxon instances
   */
  private void updateTaxonStoreWithRelations() {
    try (Transaction tx = getNeo().beginTx()) {
      for (Node n : Iterators.loop(getNeo().findNodes(Labels.TAXON))) {
        NeoUsage u = usages().objByNode(n);
        if (!u.node.hasLabel(Labels.ROOT)){
          // parent
          Node p = getSingleRelated(u.node, RelType.PARENT_OF, Direction.INCOMING);
          NeoUsage pt = usages().objByNode(p);
          u.asTaxon().setParentId(pt.getId());
        }
        // store the updated object
        usages().update(u);
      }
      tx.success();
  
      for (Node n : Iterators.loop(getNeo().findNodes(Labels.SYNONYM))) {
        NeoUsage u = usages().objByNode(n);
        if (u.node.hasLabel(Labels.SYNONYM) && !u.isSynonym()) {
          LOG.error("Taxon to Synonym conversion in neo4j needed for {}", u.usage);
          u.convertToSynonym(TaxonomicStatus.SYNONYM);
        }
        // store the updated object
        usages().update(u);
      }
      tx.success();
    }
  }
  
  private Node getSingleRelated(Node n, RelType type, Direction dir) {
    try {
      Relationship rel = n.getSingleRelationship(type, dir);
      if (rel != null) {
        return rel.getOtherNode(n);
      }
      
    } catch (NotFoundException e) {
      // thrown in case of multiple relations, debug
      LOG.debug("Multiple {} {} relations found for {}: {}", dir, type, n, NeoProperties.getScientificNameWithAuthor(n));
      for (Relationship rel : n.getRelationships(type, dir)) {
        Node other = rel.getOtherNode(n);
        LOG.debug("  {} {}/{}",
            dir == Direction.INCOMING ? "<-- "+type.abbrev+" --" : "-- "+type.abbrev+" -->",
            other, NeoProperties.getScientificNameWithAuthor(other));
      }
      throw new NormalizationFailedException("Multiple " + dir + " " + type + " relations found for " + NeoProperties.getScientificNameWithAuthor(n), e);
    }
    return null;
  }
  
  /**
   * Sync taxon KVP store with neo4j relations, setting correct neo4j labels, homotypic keys etc
   * Set correct ROOT, PROPARTE and BASIONYM labels for easier access
   */
  public void sync() {
    updateLabels();
    updateTaxonStoreWithRelations();
  }
  
  /**
   * dump treetext on console
   */
  public void debug() {
    try {
      System.out.println("TextTree:\n" + PrinterUtils.textTree(getNeo()) + "\n");

      System.out.println("\n\nNames:");
      names().all().forEach(n -> System.out.println(n.getName()));
      System.out.println("\n");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public void dump() {
    dump(new File("graphs/neodb.dot"));
  }
  
  /**
   * Dump entire graph with all labels as DOT file for debugging
   */
  public void dump(File file) {
    try {
      Writer writer = UTF8IoUtils.writerFromFile(file);
      PrinterUtils.dumpDotFile(neo, writer);
      writer.close();
      System.out.println("Wrote graph to " + file.getAbsolutePath());
    
    } catch (Exception e) {
      LOG.error("Failed to dump neo to {}", file, e);
    }
  }
  
  /**
   * @return the number of neo4j nodes
   */
  public int size() {
    return neoCounter.get();
  }
  
  private void updateLabels() {
    // set ROOT
    LOG.debug("Labelling root nodes");
    String query = "MATCH (r:TAXON) " +
        "WHERE not ( ()-[:PARENT_OF]->(r) ) " +
        "SET r:ROOT " +
        "RETURN count(r)";
    long count = updateLabel(query);
    LOG.info("Labelled {} root nodes", count);
    
    // set USAGE
    LOG.debug("Labelling usage nodes");
    query =  "MATCH (u) " +
        "WHERE u:TAXON OR u:SYNONYM " +
        "SET u:USAGE " +
        "RETURN count(u)";
    count = updateLabel(query);
    LOG.info("Labelled {} usage nodes", count);
    
    // set BASIONYM
    LOG.debug("Labelling basionym nodes");
    query = "MATCH (b)<-[:HAS_BASIONYM]-() " +
        "SET b:BASIONYM " +
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
    // avoid self referencing loops
    if (parent != null && !parent.equals(child)) {
      if (child.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
        // override existing parent!
        Node oldParent = null;
        for (Relationship r : child.getRelationships(RelType.PARENT_OF, Direction.INCOMING)) {
          Node p = r.getOtherNode(child);
          if (p.getId() != parent.getId()) {
            if (oldParent != null) {
              throw new IllegalStateException(NeoProperties.getScientificNameWithAuthorFromUsage(child) +
                  " has multiple parents including " +
                  NeoProperties.getScientificNameWithAuthorFromUsage(oldParent)
              );
            }
            oldParent = p;
            r.delete();
          }
        }
        if (oldParent != null) {
          parent.createRelationshipTo(child, RelType.PARENT_OF);
          LOG.warn("{} has already a parent {}, override with new parent {}",
              NeoProperties.getScientificNameWithAuthorFromUsage(child),
              NeoProperties.getScientificNameWithAuthorFromUsage(oldParent),
              NeoProperties.getScientificNameWithAuthorFromUsage(parent)
          );
        }
        
      } else {
        parent.createRelationshipTo(child, RelType.PARENT_OF);
      }
      
    }
  }
  
  /**
   * Creates a synonym relationship between the given synonym and the accepted node, updating labels accordingly
   * and also moving potentially existing parent_of relations.
   * If the name of the synonym and accepted usage is the same node, do not create the relation and return null.
   * Homotypic relation flag is not set and expected to be added if known to be homotypic.
   *
   * @return true if a synonym relation ws created
   */
  public boolean createSynonymRel(Node synonym, Node accepted) {
    // make sure the synonyms name is not the same as the accepted name
  
    Node sn = getUsageNameNode(synonym);
    Node an = getUsageNameNode(accepted);
    if (sn != null && sn.equals(an)) {
      return false;
    }
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
    return true;
  }
  
  public Node getUsageNameNode(Node usage) {
    return usage.getSingleRelationship(RelType.HAS_NAME, Direction.OUTGOING).getOtherNode(usage);
  }
  
  /**
   * Get the name object for a usage via its HasName relation.
   */
  public NeoName nameByUsage(final Node usage) {
    return names().objByNode(getUsageNameNode(usage));
  }
  
  /**
   * Get the name object for a usage via its HasName relation.
   */
  public List<NeoUsage> usagesByName(final Node nameNode) {
    return Iterables.stream(nameNode.getRelationships(RelType.HAS_NAME, Direction.INCOMING))
        .map(rel -> usages().objByNode(rel.getOtherNode(nameNode)))
        .collect(Collectors.toList());
  }
  
  /**
   * Get the name object for a usage via its HasName relation.
   */
  public List<Node> usageNodesByName(final Node nameNode) {
    List<Node> usages = new ArrayList<>();
    nameNode.getRelationships(RelType.HAS_NAME, Direction.INCOMING).forEach(
        un -> usages.add(un.getOtherNode(nameNode))
    );
    return usages;
  }
  
  /**
   * List all accepted taxa of a potentially prop parte synonym
   */
  public List<RankedUsage> accepted(Node synonym) {
    return Traversals.ACCEPTED.traverse(synonym).nodes().stream()
        .map(NeoProperties::getRankedUsage)
        .collect(Collectors.toList());
  }
  
  /**
   * List all accepted taxa of a potentially prop parte synonym
   */
  public List<RankedUsage> parents(Node child) {
    return Traversals.PARENTS.traverse(child).nodes().stream()
        .map(NeoProperties::getRankedUsage)
        .collect(Collectors.toList());
  }
  
  /**
   * Creates a new taxon in neo and the name usage kvp using the source usages as a template for the classification propLabel.
   * Only copies the classification above genus and ignores genus and below!
   * A verbatim usage is created with just the parentNameUsage(ID) values so they can getUsage resolved into proper neo relations later.
   * Name and taxon ids are generated de novo.
   *
   * @param name                the new name to be used
   * @param source              the taxon source to copyTaxon from
   * @param excludeRankAndBelow the rank (and all ranks below) to exclude from the source classification
   */
  public RankedUsage createProvisionalUsageFromSource(Origin origin,
                                                      Name name,
                                                      @Nullable NeoUsage source,
                                                      Rank excludeRankAndBelow) {
    NeoUsage u = NeoUsage.createTaxon(origin, name, TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    // copyTaxon verbatim classification from source
    if (source != null) {
      if (source.classification != null) {
        u.classification = new Classification(source.classification);
        // remove lower ranks
        u.classification.clearRankAndBelow(excludeRankAndBelow);
      }
      // copyTaxon parent props from source
      if (u.getVerbatimKey() != null) {
        VerbatimRecord sourceTerms = getVerbatim(u.getVerbatimKey());
        VerbatimRecord copyTerms = new VerbatimRecord();
        copyTerms.put(DwcTerm.parentNameUsageID, sourceTerms.get(DwcTerm.parentNameUsageID));
        copyTerms.put(DwcTerm.parentNameUsage, sourceTerms.get(DwcTerm.parentNameUsage));
        put(copyTerms);
        u.setVerbatimKey(copyTerms.getId());
      }
    }
    
    // store, which creates a new neo node
    Node nameNode = createNameAndUsage(u);

    return new RankedUsage(u.node, nameNode, name.getScientificName(), name.getAuthorship(), name.getRank());
  }
  
  public void updateIdGenerators() {
    // just update prefix so new ids for implicit usages are good
    idGen.setPrefix("x",
        Stream.concat(
            usages().allIds(),
            names().allIds()
        )
    );
    LOG.info("Name/Usage ID generator updated with unique prefix {}", idGen.getPrefix());
  }
  
  public void reportDuplicates() {
    if (names().getDuplicateCounter() > 0) {
      LOG.warn("The inserted dataset contains {} duplicate nameIds! Only the first record will be used", names().getDuplicateCounter());
    }
    if (usages().getDuplicateCounter() > 0) {
      LOG.warn("The inserted dataset contains {} duplicate taxonIds! Only the first record will be used", usages().getDuplicateCounter());
    }
  }
  
}

