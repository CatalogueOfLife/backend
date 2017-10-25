package org.col.commands.importer.dwca;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.col.api.Classification;
import org.col.api.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonomicStatus;
import org.col.commands.importer.neo.InsertMetadata;
import org.col.commands.importer.neo.NeoDb;
import org.col.commands.importer.neo.NormalizerStore;
import org.col.commands.importer.neo.NotUniqueRuntimeException;
import org.col.commands.importer.neo.model.*;
import org.col.commands.importer.neo.traverse.Traversals;
import org.gbif.dwc.terms.DwcTerm;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 *
 */
public class Normalizer implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(Normalizer.class);
  public static final String PLACEHOLDER_NAME = "Incertae sedis";
  private static final List<Splitter> COMMON_SPLITTER = Lists.newArrayList();
  static {
    for (char del : "[|;, ]".toCharArray()) {
      COMMON_SPLITTER.add(Splitter.on(del).trimResults().omitEmptyStrings());
    }
  }

  private final File dwca;
  private final NormalizerStore store;
  private InsertMetadata meta;

  public Normalizer(NormalizerStore store, File dwca) {
    this.dwca = dwca;
    this.store = store;
  }

  /**
   * Run the normalizer and closes the store at the end.
   *
   * @throws NormalizationFailedException
   */
  @Override
  public void run() throws NormalizationFailedException {
    run(true);
  }

  /**
   * Run the normalizer.
   *
   * @param closeStore Should the store be closed after running or on exception?
   * @throws NormalizationFailedException
   */
  public void run(boolean closeStore) throws NormalizationFailedException {
    LOG.info("Start normalization of {}", store);
    try {
      // batch import verbatim records its own batchdb
      insertData();
      // insert normalizer db relations, create implicit nodes if needed and parse names
      normalize();
      // matches names and taxon concepts and builds metrics per name/taxon
      matchAndCount();
      LOG.info("Normalization succeeded");
    } finally {
      if (closeStore) {
        store.close();
        LOG.info("Normalizer store shut down");
      }
    }
  }

  private void matchAndCount() {

  }

  private void normalize() {
    LOG.info("Start processing explicit relations ...");

    store.processAll(10000, new NeoDb.NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        NeoTaxon t = store.get(n);
        setupAcceptedRel(t);
        setupParentRel(t);
        setupBasionymRel(t);
        store.put(t);
      }

      @Override
      public boolean commitBatch(int counter) {
        checkInterrupted();
        LOG.debug("Processed relations for {} nodes", counter);
        return true;
      }
    });
    LOG.info("Relation processing completed.");

    // now process the denormalized classifications
    applyDenormedClassification();

    // finally cleanup synonym & parent relations
    cutSynonymCycles();
    relinkSynonymChains();
    preferSynonymOverParentRel();

    LOG.info("Relation setup completed.");
  }

  /**
   * Creates synonym_of relationship based on the verbatim dwc:acceptedNameUsageID and dwc:acceptedNameUsage term values.
   * Assumes pro parte synonyms are dealt with before and the remaining accepted identifier refers to a single taxon only.
   *
   * @param t the full neotaxon to process
   */
  private void setupAcceptedRel(NeoTaxon t) {
    List<ValueNode> accepted = null;
    if (t.verbatim != null && meta.isAcceptedNameMapped()) {
      accepted = lookupByIdOrName(t, DwcTerm.acceptedNameUsageID, Issue.ACCEPTED_NAME_USAGE_ID_INVALID, DwcTerm.acceptedNameUsage, Origin.VERBATIM_ACCEPTED);
      for (ValueNode acc : accepted) {
        createSynonymRel(t.node, acc.n);
      }
    }

    // if status is synonym but we aint got no idea of the accepted insert an incertae sedis record of same rank
    if (t.isSynonym() && (accepted==null || accepted.isEmpty())) {
      t.addIssue(Issue.ACCEPTED_NAME_MISSING);
      NeoTaxon acc = createDoubtfulFromSource(Origin.MISSING_ACCEPTED, PLACEHOLDER_NAME, t.name.getRank(), t, null, Issue.ACCEPTED_NAME_MISSING, t.name.getScientificName());
      createSynonymRel(t.node, acc.node);
    }
  }

  /**
   * Sets up the parent relations using the parentNameUsage(ID) term values.
   * The denormed, flat classification is used in a next step later.
   */
  private void setupParentRel(NeoTaxon t) {
    if (t.verbatim != null && meta.isParentNameMapped()) {
      ValueNode parent = lookupSingleByIdOrName(t, DwcTerm.parentNameUsageID, Issue.PARENT_NAME_USAGE_ID_INVALID, DwcTerm.parentNameUsage, Origin.VERBATIM_PARENT);
      if (parent != null) {
        assignParent(parent.n, t.node);
      }
    }
  }

  private void setupBasionymRel(NeoTaxon t) {
    if (t.verbatim != null && meta.isOriginalNameMapped()) {
      ValueNode bas = lookupSingleByIdOrName(t, DwcTerm.originalNameUsageID, Issue.ORIGINAL_NAME_USAGE_ID_INVALID, DwcTerm.originalNameUsage, Origin.VERBATIM_BASIONYM);
      if (bas != null) {
        bas.n.createRelationshipTo(t.node, RelType.BASIONYM_OF);
      }
    }
  }

  private ValueNode lookupSingleByIdOrName(NeoTaxon t, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<ValueNode> names = lookupByIdOrName(t, false, idTerm, invlidIdIssue, nameTerm, createdNameOrigin);
    return names.isEmpty() ? null : names.get(0);
  }

  private List<ValueNode> lookupByIdOrName(NeoTaxon t, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    return lookupByIdOrName(t, true, idTerm, invlidIdIssue, nameTerm, createdNameOrigin);
  }
  private List<ValueNode> lookupByIdOrName(NeoTaxon t, boolean allowMultiple, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<ValueNode> names = lookupByTaxonID(idTerm, t, invlidIdIssue, allowMultiple);
    if (names.isEmpty()) {
      // try to setup rel via the name
      ValueNode n = lookupByName(nameTerm, t, createdNameOrigin);
      if (n != null) {
        names.add(n);
      }
    }
    return names;
  }

  /**
   * Applies the classification given as denormalized higher taxa terms
   * after the parent / accepted relations have been applied.
   * It also removes the ROOT label if new parents are assigned.
   * We need to be careful as the classification coming in first via the parentNameUsage(ID) terms
   * is variable and must not always include a rank.
   */
  private void applyDenormedClassification() {
    LOG.info("Start processing higher denormalized classification ...");
    if (!meta.isDenormedClassificationMapped()) {
      LOG.info("No higher classification mapped");
      return;
    }

    store.processAll(10000, new NeoDb.NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        applyClassification(n);
      }

      @Override
      public boolean commitBatch(int counter) {
        LOG.info("Higher classifications processed for {} taxa", counter);
        return true;
      }
    });
    LOG.info("Classification processing completed.");
  }

  private void applyClassification(Node n) {
    // the highest current parent of n
    RankedName highest = null;
    if (meta.isParentNameMapped()) {
      // verify if we already have a classification, that it ends with a known rank
      Node p = Iterables.lastOrNull(Traversals.PARENTS.traverse(n).nodes());
      highest = p == null ? null : store.getRankedName(p);
      if (highest != null && highest.node != n && highest.rank.notOtherOrUnranked()) {
        LOG.debug("Node {} already has a classification which ends in an uncomparable rank.", n.getId());
        addIssueRemark(n, null, Issue.CLASSIFICATION_NOT_APPLIED);
        return;
      }
    }
    if (highest == null) {
      // otherwise use this node
      highest = store.getRankedName(n);
    }
    // shortcut: exit if highest is already a kingdom, the denormed classification cannot add to it anymore!
    if (highest != null && highest.rank == Rank.KINGDOM) {
      return;
    }
    NeoTaxon t = store.get(n);
    applyClassification(highest, t.classification);
  }

  private void removeGenusAndBelow(Classification lc) {
    lc.setGenus(null);
    lc.setSubgenus(null);
  }

  /**
   * Applies the classification lc to the given RankedName taxon
   * @param taxon
   * @param cl
   */
  private void applyClassification(RankedName taxon, Classification cl) {
    // first modify classification to only keep those ranks we want to apply!
    // exclude lowest rank from classification to be applied if this taxon is rankless and has the same name
    if (taxon.rank == null || taxon.rank.isUncomparable()) {
      Rank lowest = cl.getLowestExistingRank();
      if (lowest != null && cl.getByRank(lowest).equalsIgnoreCase(taxon.name)) {
        cl.setByRank(lowest, null);
      }
    }
    // ignore same rank from classification if accepted
    if (!taxon.node.hasLabel(Labels.SYNONYM) && taxon.rank != null) {
      cl.setByRank(taxon.rank, null);
    }
    // ignore genus and below for synonyms
    // http://dev.gbif.org/issues/browse/POR-2992
    if (taxon.node.hasLabel(Labels.SYNONYM)) {
      removeGenusAndBelow(cl);
    }

    // now reconstruct the given classification as linked nep4j nodes
    // reusing existing nodes if possible, otherwise creating new ones
    // and at the very end apply that classification to the taxon.node
    Node parent = null;
    Rank parentRank = null;
    // from kingdom to genus
    for (Rank hr : Rank.DWC_RANKS) {
      if ((taxon.rank == null || !taxon.rank.higherThan(hr)) && cl.getByRank(hr) != null) {
        // test for existing usage with that name & rank
        boolean found = false;
        for (Node n : store.byScientificName(cl.getByRank(hr), hr)) {
          if (parent == null) {
            // make sure node does also not have a higher linnean rank parent
            Node p = Iterables.firstOrNull(Traversals.LINNEAN_PARENTS.traverse(n).nodes());
            if (p == null) {
              // aligns!
              parent = n;
              parentRank = hr;
              found = true;
              break;
            }

          } else {
            // verify the parents for the next higher rank are the same
            // we dont want to apply a contradicting classification with the same name
            Node p = Traversals.parentOf(n);
            Node p2 = Traversals.parentWithRankOf(n, parentRank);
            if ((p != null && p.equals(parent)) || (p2 != null && p2.equals(parent))) {
              parent = n;
              parentRank = hr;
              found = true;
              break;
            }
          }
        }
        if (!found) {
          // persistent new higher taxon if not found
          Node lowerParent = createAccepted(Origin.DENORMED_CLASSIFICATION, cl.getByRank(hr), hr).node;
          // insert parent relationship?
          assignParent(parent, lowerParent);
          parent = lowerParent;
          parentRank = hr;
        }
      }
    }
    // finally apply lowest parent to initial node
    assignParent(parent, taxon.node);
  }

  private void assignParent(Node parent, Node child) {
    if (parent != null) {
      if (child == null) {
        LOG.error("child NULL");
      }
      parent.createRelationshipTo(child, RelType.PARENT_OF);
      child.removeLabel(Labels.ROOT);
    }
  }


  /**
   * Sanitizes synonym relations and cuts cycles at lowest rank
   */
  private void cutSynonymCycles() {
    LOG.info("Cleanup synonym cycles");
    final String query = "MATCH (s:ALL)-[sr:SYNONYM_OF]->(x)-[:SYNONYM_OF*]->(s) RETURN sr LIMIT 1";

    int counter = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query);;
      while (result.hasNext()) {
        Relationship sr = (Relationship) result.next().get("sr");

        Node syn = sr.getStartNode();

        NeoTaxon su = store.get(syn);
        su.addIssue(Issue.CHAINED_SYNOYM);
        su.addIssue(Issue.PARENT_CYCLE);
        store.put(su);
        // this is serious. Report id
        String taxonID = NeoProperties.getTaxonID(syn);

        NeoTaxon created = createDoubtfulFromSource(Origin.MISSING_ACCEPTED, PLACEHOLDER_NAME, null, null, null, Issue.CHAINED_SYNOYM, taxonID);
        createSynonymRel(syn, created.node);
        sr.delete();

        if (counter++ % 100 == 0) {
          LOG.debug("Synonym cycles cut so far: {}", counter);
        }
        result = store.getNeo().execute(query);;
      }
      tx.success();
    }
    LOG.info("{} synonym cycles resolved", counter);
  }

  /**
   * Sanitizes synonym relations relinking synonym of synonyms to make sure synonyms always point to a direct accepted taxon.
   */
  private void relinkSynonymChains() {
    LOG.info("Relink synonym chains to single accepted");
    final String query = "MATCH (s:ALL)-[sr:SYNONYM_OF*]->(x)-[:SYNONYM_OF]->(t:TAXON) " +
        "WHERE NOT (t)-[:SYNONYM_OF]->() " +
        "RETURN sr, t LIMIT 1";

    int counter = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query);
      while (result.hasNext()) {
        Map<String, Object> row = result.next();
        Node acc = (Node) row.get("t");
        for (Relationship sr : (Collection<Relationship>) row.get("sr")) {
          Node syn = sr.getStartNode();
          addIssue(syn, Issue.CHAINED_SYNOYM);
          createSynonymRel(syn, acc);
          sr.delete();
          counter++;
        }
        if (counter++ % 100 == 0) {
          LOG.debug("Synonym chains cut so far: {}", counter);
        }
        result = store.getNeo().execute(query);
      }
      tx.success();
    }
    LOG.info("{} synonym chains resolved", counter);
  }


  /**
   * Sanitizes relations by preferring synonym relations over parent rels.
   * (Re)move parent relationship for synonyms.
   * If synonyms are parents of other taxa relinks relationship to the accepted
   * presence of both confuses subsequent imports, see http://dev.gbif.org/issues/browse/POR-2755
   */
  private void preferSynonymOverParentRel() {
    LOG.info("Cleanup relations, preferring synonym over parent relations");
    int parentOfRelDeleted = 0;
    int parentOfRelRelinked = 0;
    int childOfRelDeleted = 0;
    int childOfRelRelinkedToAccepted = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      for (Node syn : Iterators.loop(store.getNeo().findNodes(Labels.SYNONYM))) {
        // if the synonym is a parent of another child taxon - relink accepted as parent of child
        Set<Node> accepted = Traversals.acceptedOf(syn);
        for (Relationship pRel : syn.getRelationships(RelType.PARENT_OF, Direction.OUTGOING)) {
          Node child = pRel.getOtherNode(syn);
          if (accepted.contains(child)) {
            // accepted is also the parent. Simply delete the parent rel in this case
            pRel.delete();
            parentOfRelDeleted++;
          } else {
            pRel.delete();
            String synonymName = NeoProperties.getScientificNameWithAuthor(syn);
            if (accepted.size() > 1) {
              // multiple accepted taxa. We will take the first, but log an issue!
              LOG.warn("{} accepted taxa for synonym {} with a child {}. Relink child to first accepted only!", accepted.size(), synonymName, NeoProperties.getScientificNameWithAuthor(child));
            }
            assignParent(accepted.iterator().next(), child);
            parentOfRelRelinked++;
            addRemark(child, "Parent relation taken from synonym " + synonymName);
          }
        }
        // remove parent rel for synonyms
        for (Relationship pRel : syn.getRelationships(RelType.PARENT_OF, Direction.INCOMING)) {
          // before we delete the relation make sure the accepted nodes have a parent rel or is ROOT
          for (Node acc : accepted) {
            if (acc.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
              // just delete
              childOfRelDeleted++;

            } else {
              Node parent = pRel.getOtherNode(syn);
              // relink if parent is not the accepted and parent rank is higher than accepted or null
              if (!parent.equals(acc)) {
                Rank parentRank = NeoProperties.getRank(parent, Rank.UNRANKED);
                Rank acceptedRank = NeoProperties.getRank(acc, Rank.UNRANKED);
                if (parentRank == Rank.UNRANKED || parentRank.higherThan(acceptedRank)) {
                  String synName = NeoProperties.getScientificNameWithAuthor(syn);
                  LOG.debug("Relink parent rel of synonym {}", synName);
                  childOfRelRelinkedToAccepted++;
                  assignParent(parent, acc);
                  addRemark(acc, "Parent relation taken from synonym " + synName);
                }
              }
            }
          }
          pRel.delete();
        }
      }
      tx.success();
    }
    LOG.info("Synonym relations cleaned up. "
            + "{} childOf relations deleted, {} childOf rels relinked to accepted,"
            + "{} parentOf relations deleted, {} parentOf rels moved from synonym to accepted",
        childOfRelDeleted, childOfRelRelinkedToAccepted, parentOfRelDeleted, parentOfRelRelinked);
  }

  private NeoTaxon addRemark(Node node, String remark) {
    return addIssueRemark(node, remark, null);
  }

  private NeoTaxon addIssue(Node node, Issue issue) {
    return addIssueRemark(node, null, issue);
  }

  /**
   * Reads a name usage from the kvp store, adds issues and or remarks and persists it again.
   * Only use this method if you just have a node a no usage instance yet at hand.
   */
  private NeoTaxon addIssueRemark(Node n, @Nullable String remark, @Nullable Issue issue) {
    //TODO: consider to store issues & remarks in neo4j so we do not have to load/store the full taxon instance
    NeoTaxon t = store.get(n);
    if (issue != null) {
      t.addIssue(issue);
    }
    if (remark != null) {
      t.addRemark(remark);
    }
    store.put(t);
    return t;
  }

  /**
   * Creates a synonym relationship between the given synonym and the accepted node, updating labels accordingly
   * and also moving potentially existing parent_of relations.
   */
  private void createSynonymRel(Node synonym, Node accepted) {
    synonym.createRelationshipTo(accepted, RelType.SYNONYM_OF);
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

  /**
   * Reads a verbatim given term that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to split the ids into multiple keys and lookup the matching nodes.
   *
   * @return list of potentially split ids with their matching neo node if found, otherwise null
   */
  private List<ValueNode> lookupByTaxonID(DwcTerm term, NeoTaxon t, Issue invalidIdIssue, boolean allowMultiple) {
    List<ValueNode> ids = Lists.newArrayList();
    final String unsplitIds = t.verbatim.getCoreTerm(term);
    if (unsplitIds != null && !unsplitIds.equals(t.getTaxonID())) {
      if (allowMultiple && meta.getMultiValueDelimiters().containsKey(term)) {
        for (String id : meta.getMultiValueDelimiters().get(term).splitToList(unsplitIds)) {
          if (!id.equals(t.getTaxonID())) {
            ids.add(new ValueNode(store.byTaxonID(id), id));
          }
        }
      } else {
        // matcher by taxon value to see if this is an existing identifier or if we should try to split it
        Node a = store.byTaxonID(unsplitIds);
        if (a != null) {
          ids.add(new ValueNode(a, unsplitIds));
        } else if (allowMultiple){
          for (Splitter splitter : COMMON_SPLITTER) {
            List<String> vals = splitter.splitToList(unsplitIds);
            if (vals.size() > 1) {
              for (String id : vals) {
                if (!id.equals(t.getTaxonID())) {
                  ids.add(new ValueNode(store.byTaxonID(id), id));
                }
              }
              break;
            }
          }
          // could not find anything
          ids.add(new ValueNode(null, unsplitIds));
        }
      }
    }
    // remove and log bad ids
    Iterator<ValueNode> iter = ids.iterator();
    while (iter.hasNext()) {
      ValueNode nid = iter.next();
      if (nid.n == null) {
        t.addIssue(invalidIdIssue, nid.value);
        LOG.warn("{} {} not existing", term.simpleName(), nid.value);
        iter.remove();
      }
    }
    return ids;
  }

  private void removeSynonyms(List<Node> nodes) {
    nodes.removeIf(n -> n.hasLabel(Labels.SYNONYM));
  }

  /**
   * Reads a verbatim given term that should represent a scientific name pointing to another record via the scientificName.
   * It first tries to lookup existing records by the canonical name with author, but falls back to authorless lookup if no matches.
   * If the name is the same as the original records scientificName it is ignored.
   *
   * If true names that cannot be found are created as explicit names
   *
   * @return the accepted node with its name. Null if no accepted name was mapped or equals the record itself
   */
  private ValueNode lookupByName(DwcTerm term, NeoTaxon t, Origin createdOrigin) {
    final String name = t.verbatim.getCoreTerm(term);
    if (name != null && !name.equalsIgnoreCase(t.name.getScientificName())) {
      List<Node> matches = store.byScientificName(name);

      // if multiple matches remove synonyms
      if (matches.size() > 1) {
        removeSynonyms(matches);
      }

      // if we got one match, use it!
      if (matches.isEmpty()) {
        // create
        LOG.debug("{} {} not existing, materialize it", term.simpleName(), name);
        NeoTaxon created = createDoubtfulFromSource(createdOrigin, name, null, t, null, null, null);
        return new ValueNode(created.node, name);

      } else {
        if (matches.size() > 1) {
          // still multiple matches, pick first and log critical issue!
          t.addIssue(Issue.NAME_NOT_UNIQUE, name);
        }
        return new ValueNode(matches.get(0), name);
      }
    }
    return null;
  }


  static class ValueNode {
    public final Node n;
    public final String value;

    public ValueNode(Node n, String value) {
      this.n = n;
      this.value = value;
    }
  }

  private NeoTaxon createAccepted(Origin origin, String sciname, Rank rank) {
    NeoTaxon t = NeoTaxon.createTaxon(origin, sciname, rank, TaxonomicStatus.ACCEPTED);

    // store, which creates a new neo node
    store.put(t);
    return t;
  }

  /**
   * Creates a new taxon in neo and the name usage kvp using the source usages as a template for the classification properties.
   * Only copies the classification above genus and ignores genus and below!
   * A verbatim usage is created with just the parentNameUsage(ID) values so they can get resolved into proper neo relations later.
   *
   * @param taxonID the optional taxonID to apply to the new node
   */
  private NeoTaxon createDoubtfulFromSource(Origin origin, String sciname, Rank rank, @Nullable NeoTaxon source,
                                            @Nullable String taxonID, @Nullable Issue issue, @Nullable String issueValue) {
    NeoTaxon t = NeoTaxon.createTaxon(origin, sciname, rank, TaxonomicStatus.DOUBTFUL);
    t.taxon.setId(taxonID);
    // copy verbatim classification from source
    if (source != null) {
      t.classification = Classification.copy(source.classification);
      // removeGenusAndBelow
      t.classification.setGenus(null);
      t.classification.setSubgenus(null);
      // copy parent props from source
      t.verbatim = new VerbatimRecord();
      t.verbatim.setCoreTerm(DwcTerm.parentNameUsageID, source.verbatim.getCoreTerm(DwcTerm.parentNameUsageID));
      t.verbatim.setCoreTerm(DwcTerm.parentNameUsage, source.verbatim.getCoreTerm(DwcTerm.parentNameUsage));
    }
    // add potential issue
    if (issue != null) {
      t.addIssue(issue, issueValue);
    }

    // store, which creates a new neo node
    store.put(t);
    return t;
  }

  private void checkInterrupted() throws NormalizationFailedException {
    if (Thread.interrupted()) {
      LOG.warn("Normalizer interrupted, exit {} early with incomplete parsing", store.getDataset());
      throw new NormalizationFailedException("Normalizer interrupted");
    }
  }

  private void insertData() throws NormalizationFailedException {
    // closing the batch inserter opens the normalizer db again for regular access via the store
    try {
      NormalizerInserter inserter = new NormalizerInserter(store);
      meta = inserter.insert(dwca);

    } catch (NotUniqueRuntimeException e) {
      throw new NormalizationFailedException(e.getProperty() + " values not unique: " + e.getKey(), e);

    } catch (IOException e) {
      throw new NormalizationFailedException("IO error: " + e.getMessage(), e);
    }
  }
}