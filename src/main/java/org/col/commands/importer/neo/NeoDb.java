package org.col.commands.importer.neo;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonomicStatus;
import org.col.commands.importer.neo.kryo.CliKryoFactory;
import org.col.commands.importer.neo.mapdb.MapDbObjectSerializer;
import org.col.commands.importer.neo.model.*;
import org.col.commands.importer.neo.traverse.Traversals;
import org.col.util.SciNameNormalizer;
import org.gbif.api.model.checklistbank.ParsedName;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

/**
 * A persistence mechanism for storing core taxonomy & names properties and relations in an embedded
 * Neo4j database, while keeping a large BLOB of information in a separate MapDB storage.
 * <p>
 * Neo4j does not perform well storing large properties in its node and it is recommended to keep
 * large BLOBs or strings externally: https://neo4j.com/blog/dark-side-neo4j-worst-practices/
 * <p>
 * We use the Krypto library for a very performant binary
 * serialisation with the data keyed under the neo4j node id.
 *
 * @param <T> the persisted object class stored in mapdb
 */
public class NeoDb<T extends NeoTaxon> {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDb.class);

  private final Class<T> dataClass;
  private final GraphDatabaseBuilder neoFactory;
  private final DB mapDb;
  private final Map<Long, T> data;
  private final File neoDir;
  private final File kvpStore;
  private final KryoPool pool;
  private final Joiner remarkJoiner = Joiner.on("\n").skipNulls();

  private GraphDatabaseService neo;


  /**
   * @param mapDb
   * @param neoDir
   * @param neoFactory
   */
  NeoDb(Class<T> dataClass, DB mapDb, File neoDir, @Nullable File kvpStore, GraphDatabaseBuilder neoFactory) {
    this.dataClass = dataClass;
    this.neoFactory = neoFactory;
    this.neoDir = neoDir;
    this.kvpStore = kvpStore;
    this.mapDb = mapDb;

    try {
      pool = new KryoPool.Builder(new CliKryoFactory())
          .softReferences()
          .build();
      data = mapDb.hashMap("data")
          .keySerializer(Serializer.LONG)
          .valueSerializer(new MapDbObjectSerializer(dataClass, pool, 256))
          .createOrOpen();
      openNeo();
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
      LOG.error("Failed to close mapDb store {}", kvpStore.getAbsolutePath(), e);
    }
    closeNeo();
    LOG.debug("Closed DAO for directory {}", neoDir.getAbsolutePath());
  }

  public void closeAndDelete() {
    close();
    if (kvpStore != null && kvpStore.exists()) {
      LOG.debug("Deleting kvp storage file {}", kvpStore.getAbsolutePath());
      FileUtils.deleteQuietly(kvpStore);
    }
    if (neoDir != null && neoDir.exists()) {
      LOG.debug("Deleting neo4j directory {}", neoDir.getAbsolutePath());
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

  /**
   * Sets a node property and removes it in case the property value is null.
   */
  private static void setProperty(Node n, String property, Object value) {
    if (value == null) {
      n.removeProperty(property);
    } else {
      n.setProperty(property, value);
    }
  }

  private static <T> T readEnum(Node n, String property, Class<T> vocab, T defaultValue) {
    Object val = n.getProperty(property, null);
    if (val != null) {
      int idx = (Integer) val;
      return (T) vocab.getEnumConstants()[idx];
    }
    return defaultValue;
  }

  private static void storeEnum(Node n, String property, Enum value) {
    if (value == null) {
      n.removeProperty(property);
    } else {
      n.setProperty(property, value.ordinal());
    }
  }

  private Rank readRank(Node n) {
    return readEnum(n, NeoProperties.RANK, Rank.class, Rank.UNRANKED);
  }

  private void updateNeo(Node n, T t) {
    if (n != null) {
      setProperty(n, NeoProperties.TAXON_ID, t.getTaxonID());
      setProperty(n, NeoProperties.SCIENTIFIC_NAME, t.getScientificName());
      setProperty(n, NeoProperties.CANONICAL_NAME, t.getCanonicalName());
      storeEnum(n, NeoProperties.RANK, t.getRank());
    }
  }

  public Transaction beginTx() {
    return neo.beginTx();
  }

  /**
   * Finds nodes by their canonical name property.
   * Be careful when using this method on large graphs without a schema indexing the canonical name property!
   */
  public Collection<Node> findByName(String canonicalName) {
    return Iterators.asCollection(neo.findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, canonicalName));
  }

  /**
   * @param canonicalName
   * @return th matching node, null or NoSuchElementException
   */
  public Node findByNameSingle(String canonicalName) {
    return Iterators.single(neo.findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, canonicalName));
  }

  /**
   * @param scientificName
   * @return th matching node, null or NoSuchElementException
   */
  public Node findByScientificName(String scientificName) {
    return Iterators.single(neo.findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, scientificName));
  }

  /**
   * @return the canonical name of a parsed name or the entire scientific name in case the canonical cannot be created (e.g. virus or hybrid names)
   */
  public static String canonicalOrScientificName(ParsedName pn, boolean withAuthors) {
    String name = withAuthors ? pn.canonicalNameComplete() : SciNameNormalizer.normalize(pn.canonicalName());
    if (StringUtils.isBlank(name)) {
      // this should only ever happen for virus names, log otherwise
      if (pn.isParsableType()) {
        LOG.warn("Parsable name found with an empty canonical name string: {}", pn.getScientificName());
      }
      return pn.getScientificName();
    }
    return name;
  }


  /**
   * Creates a new neo node labeld as a taxon.
   *
   * @return the new & empty neo node
   */
  public Node createTaxon() {
    return neo.createNode(Labels.TAXON);
  }

  /**
   * Creates a new neo4j node, updates it with information from the data instance and stores it in the kvp store.
   */
  public Node create(T obj) {
    Node n = createTaxon();
    obj.setNode(n);
    // store usage in kvp store
    this.data.put(n.getId(), obj);
    // update neo with indexed properties
    updateNeo(n, obj);
    return n;
  }

  private Node getRelatedTaxon(Node n, RelType type, Direction dir) {
    Relationship rel = n.getSingleRelationship(type, dir);
    if (rel != null) {
      return rel.getOtherNode(n);
    }
    return null;
  }

  /**
   * Reads a node into a name usage instance with keys being the node ids long values based on the neo relations.
   * The bulk of the usage data comes from the KVP store and neo properties are overlayed.
   */
  public T read(Node n, boolean readRelations) {
    if (data.containsKey(n.getId())) {
      T obj = data.get(n.getId());
      obj.setNode(n);
      if (n.hasLabel(Labels.SYNONYM) && (obj.getStatus() == null || !obj.getStatus().isSynonym())) {
        obj.setStatus(TaxonomicStatus.SYNONYM);
      }
      if (readRelations) {
        readRelations(n, obj);
      }
      return obj;
    }
    return null;
  }

  /**
   * Retrieves data just from the kvp store without overlaying neo4j information.
   */
  public T readData(long key) {
    return data.get(key);
  }

  /**
   * Updates a given data instance with the neo4j relational information, i.e. the classification
   * or synonymy.
   *
   * @param n
   * @param obj
   */
  private void readRelations(Node n, T obj) {
    try {
      Node bas = getRelatedTaxon(n, RelType.BASIONYM_OF, Direction.INCOMING);
      if (bas != null) {
        obj.setBasionymKey((int) bas.getId());
        obj.setBasionym(NeoProperties.getScientificName(bas));
      }
    } catch (RuntimeException e) {
      LOG.error("Unable to read basionym relation for {} with node {}", obj.getScientificName(), n.getId());
      obj.addIssue(Issue.RELATIONSHIP_MISSING);
      obj.addRemark("Multiple original name relations");
    }

    Node acc = null;
    try {
      // pro parte synonym relations must have been flattened already...
      acc = getRelatedTaxon(n, RelType.SYNONYM_OF, Direction.OUTGOING);
      if (acc != null) {
        obj.setAcceptedKey((int) acc.getId());
        obj.setAccepted(NeoProperties.getScientificName(acc));
        // update synonym flag based on relations
        if (obj.getStatus() == null) {
          obj.setStatus(TaxonomicStatus.SYNONYM);
        }
      }
    } catch (RuntimeException e) {
      LOG.error("Unable to read accepted name relation for {} with node {}", obj.getScientificName(), n.getId(), e);
      obj.addIssue(Issue.RELATIONSHIP_MISSING);
      obj.addRemark("Multiple accepted name relations");
    }

    try {
      // prefer the parent relationship of the accepted node if it exists
      Node p = getRelatedTaxon(acc == null ? n : acc, RelType.PARENT_OF, Direction.INCOMING);
      if (p != null) {
        obj.setParentKey((int) p.getId());
        //u.setParent(NeoProperties.getCanonicalName(p));
      }
    } catch (RuntimeException e) {
      LOG.error("Unable to read parent relation for {} with node {}", obj.getScientificName(), n.getId());
      obj.addIssue(Issue.RELATIONSHIP_MISSING);
      obj.addRemark("Multiple parent relations");
    }
  }

  /**
   * Reads a simple RankedName instance based purely on neo4j properties.
   */
  public RankedName readRankedName(Node n) {
    RankedName rn = null;
    if (n != null) {
      rn = new RankedName();
      rn.node = n;
      rn.name = NeoProperties.getScientificName(n);
      rn.rank = readRank(n);
    }
    return rn;
  }

  /**
   * Stores a data instance in both the kvp store and updates the neo node properties.
   */
  public void update(T nn) {
    update(nn, true);
  }

  /**
   * Stores a data instance in the kvp store and optionally also updates the neo node properties if requested.
   */
  public void update(T nn, boolean updateNeo) {
    data.put(nn.getNode().getId(), nn);
    if (updateNeo) {
      // update neo with indexed properties
      updateNeo(nn.getNode(), nn);
    }
  }

  /**
   * Deletes kvp entry and neo node together with all its relations.
   */
  public void delete(Node node) {
    LOG.debug("Deleting node {} {}", node.getId(), NeoProperties.getScientificName(node));
    data.remove(node.getId());
    // remove all relations
    for (Relationship rel : node.getRelationships()) {
      rel.delete();
    }
    node.delete();
  }

  /**
   * @return the last parent or the node itself if no parent exists
   */
  protected RankedName getDirectParent(Node n) {
    Node p = Iterables.lastOrNull(Traversals.PARENTS.traverse(n).nodes());
    return readRankedName(p != null ? p : n);
  }

  protected Node getLinneanRankParent(Node n) {
    return Iterables.firstOrNull(Traversals.LINNEAN_PARENTS.traverse(n).nodes());
  }

  /**
   * @return the single matching node with the taxonID or null
   */
  protected Node nodeByTaxonId(String taxonID) {
    return Iterators.singleOrNull(getNeo().findNodes(Labels.TAXON, NeoProperties.TAXON_ID, taxonID));
  }

  /**
   * @return the single matching node with the canonical name or null
   */
  protected Node nodeByCanonical(String canonical) throws NotUniqueException {
    try {
      return Iterators.singleOrNull(
          getNeo().findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, canonical)
      );
    } catch (NoSuchElementException e) {
      throw new NotUniqueException(canonical, "Canonical name not unique: " + canonical);
    }
  }

  protected Collection<Node> nodesByCanonical(String canonical) {
    return Iterators.asCollection(
        getNeo().findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, canonical)
    );
  }

  protected List<Node> nodesByCanonicalAndRank(String canonical, org.col.api.vocab.Rank rank) {
    List<Node> matching = filterByRank(getNeo().findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, canonical), rank);
    if (matching.size() > 10) {
      LOG.warn("There are {} matches for the {} {}. This might indicate we are not dealing with a proper checklist", matching.size(), rank, canonical);
    }
    return matching;
  }

  private List<Node> filterByRank(ResourceIterator<Node> nodes, org.col.api.vocab.Rank rank) {
    List<Node> matchingRanks = Lists.newArrayList();
    while (nodes.hasNext()) {
      Node n = nodes.next();
      if (rank == null || n.getProperty(NeoProperties.RANK, rank.ordinal()).equals(rank.ordinal())) {
        matchingRanks.add(n);
      }
    }
    return matchingRanks;
  }

  /**
   * @return the single matching node with the scientific name or null
   */
  protected Node nodeBySciname(String sciname) throws NotUniqueException {
    try {
      return Iterators.singleOrNull(
          getNeo().findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, sciname)
      );
    } catch (NoSuchElementException e) {
      throw new NotUniqueException(sciname, "Scientific name not unique: " + sciname);
    }
  }

  protected boolean matchesClassification(Node n, List<RankedName> classification) {
    Iterator<RankedName> clIter = classification.listIterator();
    Iterator<Node> nodeIter = Traversals.PARENTS.traverse(n).nodes().iterator();

    while (clIter.hasNext()) {
      if (!nodeIter.hasNext()) {
        return false;
      }
      RankedName rn1 = clIter.next();
      RankedName rn2 = readRankedName(nodeIter.next());
      if (rn1.rank != rn2.rank || !rn1.name.equals(rn2.name)) {
        return false;
      }
    }
    return !nodeIter.hasNext();
  }

  /**
   * Logs all neo4j nodes with their properties, mainly for debugging.
   * This avoids (potentially erroneous) tree traversals missing some nodes.
   */
  public void logAll() throws Exception {
    for (Node n : neo.getAllNodes()) {
      LOG.info("{} {} [{} {}]", n.getId(), NeoProperties.getScientificName(n), n.hasLabel(Labels.SYNONYM) ? "syn." : "acc.", NeoProperties.getRank(n, null));
    }
  }

}

