package org.col.commands.importer.dwca;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.col.api.*;
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
import org.col.parser.NameParserGNA;
import org.col.parser.UnparsableException;
import org.gbif.dwc.terms.DwcTerm;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class Normalizer implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(Normalizer.class);
  public static final Name PLACEHOLDER = new Name();
  static {
    PLACEHOLDER.setScientificName("Incertae sedis");
    PLACEHOLDER.setRank(Rank.UNRANKED);
  }
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
   * Run the normalizer and close the store at the end.
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

    store.process(Labels.ALL,10000, new NeoDb.NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        NeoTaxon t = store.get(n);
        insertAcceptedRel(t);
        insertParentRel(t);
        insertBasionymRel(t);
        store.put(t);
      }

      @Override
      public boolean commitBatch(int counter) {
        checkInterrupted();
        LOG.debug("Processed relations for {} nodes", counter);
        return true;
      }

      @Override
      public boolean finalBatch(int counter) {
        LOG.info("Processed relations for all {} nodes", counter);
        return true;
      }
    });

    // cleanup synonym & parent relations
    cutSynonymCycles();
    relinkSynonymChains();
    preferSynonymOverParentRel();

    // process the denormalized classifications of accepted taxa
    applyDenormedClassification();

    // set correct ROOT and PROPARTE labels for easier access
    store.updateLabels();

    // updates the taxon instances with infos derived from neo4j relations
    store.updateTaxonStoreWithRelations();

    LOG.info("Relation setup completed.");
  }

  /**
   * Creates synonym_of relationship based on the verbatim dwc:acceptedNameUsageID and dwc:acceptedNameUsage term values.
   * Assumes pro parte basionymGroup are dealt with before and the remaining accepted identifier refers to a single taxon only.
   *
   * @param t the full neotaxon to process
   */
  private void insertAcceptedRel(NeoTaxon t) {
    List<RankedName> accepted = null;
    if (t.verbatim != null && meta.isAcceptedNameMapped()) {
      accepted = lookupByIdOrName(t, DwcTerm.acceptedNameUsageID, Issue.ACCEPTED_NAME_USAGE_ID_INVALID, DwcTerm.acceptedNameUsage, Origin.VERBATIM_ACCEPTED);
      for (RankedName acc : accepted) {
        createSynonymRel(t.node, acc.node);
      }
    }

    // if status is synonym but we aint got no idea of the accepted insert an incertae sedis record of same rank
    if ((accepted==null || accepted.isEmpty())
        && (t.isSynonym() || t.issues.containsKey(Issue.ACCEPTED_NAME_USAGE_ID_INVALID))
    ) {
      t.addIssue(Issue.ACCEPTED_NAME_MISSING);
      PLACEHOLDER.setRank(t.name.getRank());
      RankedName acc = createDoubtfulFromSource(Origin.MISSING_ACCEPTED, PLACEHOLDER, t, t.name.getRank(), null, Issue.ACCEPTED_NAME_MISSING, t.name.getScientificName());
      // now remove any denormed classification from this synonym as we have copied it already to the accepted placeholder
      t.classification = null;
      createSynonymRel(t.node, acc.node);
    }
  }

  /**
   * Sets up the parent relations using the parentNameUsage(ID) term values.
   * The denormed, flat classification is used in a next step later.
   */
  private void insertParentRel(NeoTaxon t) {
    if (t.verbatim != null && meta.isParentNameMapped()) {
      RankedName parent = lookupSingleByIdOrName(t, DwcTerm.parentNameUsageID, Issue.PARENT_NAME_USAGE_ID_INVALID, DwcTerm.parentNameUsage, Origin.VERBATIM_PARENT);
      if (parent != null) {
        assignParent(parent.node, t.node);
      }
    }
  }

  private void insertBasionymRel(NeoTaxon t) {
    if (t.verbatim != null && meta.isOriginalNameMapped()) {
      RankedName bas = lookupSingleByIdOrName(t, DwcTerm.originalNameUsageID, Issue.ORIGINAL_NAME_USAGE_ID_INVALID, DwcTerm.originalNameUsage, Origin.VERBATIM_BASIONYM);
      if (bas != null) {
        bas.node.createRelationshipTo(t.node, RelType.BASIONYM_OF);
      }
    }
  }

  private RankedName lookupSingleByIdOrName(NeoTaxon t, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<RankedName> names = lookupByIdOrName(t, false, idTerm, invlidIdIssue, nameTerm, createdNameOrigin);
    return names.isEmpty() ? null : names.get(0);
  }

  private List<RankedName> lookupByIdOrName(NeoTaxon t, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    return lookupByIdOrName(t, true, idTerm, invlidIdIssue, nameTerm, createdNameOrigin);
  }
  private List<RankedName> lookupByIdOrName(NeoTaxon t, boolean allowMultiple, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<RankedName> names = lookupByTaxonID(idTerm, t, invlidIdIssue, allowMultiple);
    if (names.isEmpty()) {
      // try to setup rel via the name
      RankedName n = lookupByName(nameTerm, t, createdNameOrigin);
      if (n != null) {
        names.add(n);
      }
    }
    return names;
  }

  /**
   * Applies the classification given as denormalized higher taxa terms to accepted taxa
   * after the parent / accepted relations have been applied.
   * We need to be careful as the classification coming in first via the parentNameUsage(ID) terms
   * is variable and must not always include a rank.
   *
   * The classification is not applied to basionymGroup!
   */
  private void applyDenormedClassification() {
    LOG.info("Start processing higher denormalized classification ...");
    if (!meta.isDenormedClassificationMapped()) {
      LOG.info("No higher classification mapped");
      return;
    }

    store.process(Labels.ALL,10000, new NeoDb.NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        if (n.hasLabel(Labels.TAXON)) {
          RankedName rn = NeoProperties.getRankedName(n);
          // the highest current parent of n
          RankedName highest = findHighestParent(n);
          // only need to apply classification if highest exists and is not already a kingdom, the denormed classification cannot add to it anymore!
          if (highest != null && highest.rank != Rank.KINGDOM) {
            NeoTaxon t = store.get(n);
            if (t.classification != null) {
              applyClassification(highest, t.classification);
            }
          }
        }
      }

      @Override
      public boolean commitBatch(int counter) {
        LOG.info("Higher classifications processed for {} taxa", counter);
        return true;
      }

      @Override
      public boolean finalBatch(int counter) {
        LOG.info("Higher classifications processed for all {} taxa", counter);
        return true;
      }
    });
  }

  private RankedName findHighestParent(Node n) {
    // the highest current parent of n
    RankedName highest = null;
    if (meta.isParentNameMapped()) {
      // verify if we already have a classification, that it ends with a known rank
      Node p = Iterables.lastOrNull(Traversals.PARENTS.traverse(n).nodes());
      highest = p == null ? null : NeoProperties.getRankedName(p);
      if (highest != null
          && !highest.node.equals(n)
          && !highest.rank.notOtherOrUnranked()
      ) {
        LOG.debug("Node {} already has a classification which ends in an uncomparable rank.", n.getId());
        addIssueRemark(n, null, Issue.CLASSIFICATION_NOT_APPLIED);
        return null;
      }
    }
    if (highest == null) {
      // otherwise use this node
      highest = NeoProperties.getRankedName(n);
    }
    return highest;
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
    // ignore genus and below for basionymGroup
    // http://dev.gbif.org/issues/browse/POR-2992
    if (taxon.node.hasLabel(Labels.SYNONYM)) {
      cl.setGenus(null);
      cl.setSubgenus(null);
    }

    // now reconstruct the given classification as linked neo4j nodes
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

        RankedName created = createPlaceholder(Origin.MISSING_ACCEPTED, Issue.CHAINED_SYNOYM, taxonID);
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
   * Sanitizes synonym relations relinking synonym of basionymGroup to make sure basionymGroup always point to a direct accepted taxon.
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
   * (Re)move parent relationship for basionymGroup.
   * If basionymGroup are parents of other taxa relinks relationship to the accepted
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
        // remove parent rel for basionymGroup
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

  /**
   * Reads a verbatim given term that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to split the ids into multiple keys and lookup the matching nodes.
   *
   * @return list of potentially split ids with their matching neo node if found, otherwise null
   */
  private List<RankedName> lookupByTaxonID(DwcTerm term, NeoTaxon t, Issue invalidIdIssue, boolean allowMultiple) {
    List<RankedName> ids = Lists.newArrayList();
    final String unsplitIds = t.verbatim.getCoreTerm(term);
    if (unsplitIds != null && !unsplitIds.equals(t.getTaxonID())) {
      if (allowMultiple && meta.getMultiValueDelimiters().containsKey(term)) {
        ids.addAll(lookupRankedNames(
            meta.getMultiValueDelimiters().get(term).splitToList(unsplitIds), t)
        );
      } else {
        // match by taxonID to see if this is an existing identifier or if we should try to split it
        Node a = store.byTaxonID(unsplitIds);
        if (a != null) {
          ids.add(NeoProperties.getRankedName(a));

        } else if (allowMultiple){
          for (Splitter splitter : COMMON_SPLITTER) {
            List<String> vals = splitter.splitToList(unsplitIds);
            if (vals.size() > 1) {
              ids.addAll(lookupRankedNames(vals, t));
              break;
            }
          }
        }
      }
      // could not find anything?
      if (ids.isEmpty()) {
        t.addIssue(invalidIdIssue, unsplitIds);
        LOG.warn("{} {} not existing", term.simpleName(), unsplitIds);
      }
    }
    return ids;
  }

  private List<RankedName> lookupRankedNames(Iterable<String> taxonIDs, NeoTaxon t) {
    List<RankedName> rankedNames = Lists.newArrayList();
    for (String id : taxonIDs) {
      if (!id.equals(t.getTaxonID())) {
        Node n = store.byTaxonID(id);
        if (n != null) {
          rankedNames.add(NeoProperties.getRankedName(n));
        }
      }
    }
    return rankedNames;
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
  private RankedName lookupByName(DwcTerm term, NeoTaxon t, Origin createdOrigin) {
    if (t.verbatim.hasCoreTerm(term)) {
      Name nameTmp;
      try {
        nameTmp = NameParserGNA.PARSER.parse(t.verbatim.getCoreTerm(term)).get();
      } catch (UnparsableException e) {
        LOG.warn("Unable to parse");
        nameTmp = new Name();
        nameTmp.addIssue(Issue.UNPARSABLE_NAME);
        nameTmp.setScientificName(t.verbatim.getCoreTerm(term));
      }

      final Name name = nameTmp;
      if (!name.getScientificName().equalsIgnoreCase(t.name.getScientificName())) {
        List<Node> matches = store.byScientificName(name.getScientificName());
        // remove other authors, but allow names without authors
        if (name.hasAuthorship()) {
          matches.removeIf(n -> !Strings.isNullOrEmpty(NeoProperties.getAuthorship(n)) && !NeoProperties.getAuthorship(n).equalsIgnoreCase(name.fullAuthorship()));
        }

        // if multiple matches remove basionymGroup
        if (matches.size() > 1) {
          matches.removeIf(n -> n.hasLabel(Labels.SYNONYM));
        }

        // if we got one match, use it!
        if (matches.isEmpty()) {
          // create
          LOG.debug("{} {} not existing, materialize it", term.simpleName(), name);
          return createDoubtfulFromSource(createdOrigin, name, t, t.name.getRank(), null, null, null);

        } else {
          if (matches.size() > 1) {
            // still multiple matches, pick first and log critical issue!
            t.addIssue(Issue.NAME_NOT_UNIQUE, name);
          }
          return NeoProperties.getRankedName(matches.get(0));
        }
      }
    }
    return null;
  }

  private NeoTaxon createAccepted(Origin origin, String sciname, Rank rank) {
    Name n = new Name();
    n.setScientificName(sciname);
    n.setRank(rank);
    NeoTaxon t = NeoTaxon.createTaxon(origin, n, TaxonomicStatus.ACCEPTED);

    // store, which creates a new neo node
    store.put(t);
    return t;
  }

  private RankedName createPlaceholder(Origin origin, @Nullable Issue issue, @Nullable String issueValue) {
    PLACEHOLDER.setRank(Rank.UNRANKED);
    return createDoubtfulFromSource(origin, PLACEHOLDER, null, Rank.GENUS,null, issue, issueValue);
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
  private RankedName createDoubtfulFromSource(Origin origin, Name name,
                                              @Nullable NeoTaxon source, Rank excludeRankAndBelow, @Nullable String taxonID,
                                              @Nullable Issue issue, @Nullable String issueValue) {
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

    return new RankedName(t.node, t.name.getScientificName(), t.name.getAuthorship().toString(), t.name.getRank());
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