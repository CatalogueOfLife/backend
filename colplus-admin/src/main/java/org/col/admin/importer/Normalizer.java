package org.col.admin.importer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.col.admin.importer.acef.AcefInserter;
import org.col.admin.importer.coldp.ColDPInserter;
import org.col.admin.importer.dwca.DwcaInserter;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.neo.NotUniqueRuntimeException;
import org.col.admin.importer.neo.model.*;
import org.col.admin.importer.neo.traverse.Traversals;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.admin.matching.NameIndex;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.MatchType;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.common.collection.IterUtils;
import org.col.common.collection.MapUtils;
import org.col.common.tax.MisappliedNameMatcher;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Normalizer implements Callable<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(Normalizer.class);
  private static final Map<MatchType, Issue> MATCH_ISSUES = ImmutableMap.of(
      MatchType.VARIANT, Issue.NAME_MATCH_VARIANT,
      MatchType.INSERTED, Issue.NAME_MATCH_INSERTED,
      MatchType.AMBIGUOUS, Issue.NAME_MATCH_AMBIGUOUS,
      MatchType.NONE, Issue.NAME_MATCH_NONE
  );
  private final Dataset dataset;
  private final Path sourceDir;
  private final NeoDb store;
  private final ReferenceFactory refFactory;
  private final NameIndex index;
  private InsertMetadata meta;


  public Normalizer(NeoDb store, Path sourceDir, NameIndex index) {
    this.sourceDir = sourceDir;
    this.store = store;
    this.dataset = store.getDataset();
    refFactory = new ReferenceFactory(dataset.getKey(), store);
    this.index = index;
  }

  /**
   * Run the normalizer and close the store at the end.
   *
   * @throws NormalizationFailedException
   * @throws InterruptedException in case the thread got interrupted, e.g. the import got cancelled
   */
  @Override
  public Boolean call() throws NormalizationFailedException, InterruptedException {
    LOG.info("Start normalization of {}", store);
    try {
      // batch import verbatim records
      insertData();
      // create new id generator being aware of existing ids
      store.updateIdGeneratorPrefix();
      // insert normalizer db relations, create implicit nodes if needed and parse names
      checkIfCancelled();
      normalize();
      // sync taxon KVP store with neo4j relations, setting correct neo4j labels, homotypic keys etc
      checkIfCancelled();
      store.sync();
      // verify, derive issues and fail before we do expensive matching or even db imports
      checkIfCancelled();
      verify();
      // matches names and taxon concepts and builds metrics per name/taxon
      checkIfCancelled();
      matchAndCount();
      LOG.info("Normalization succeeded");

    } finally {
      store.close();
      LOG.info("Normalizer store shut down");
    }
    return true;
  }

  private void checkIfCancelled() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Normalizer was interrupted");
    }
  }

  /**
   * @return true if year1 is considered to be before year2 with at least 1 year difference
   */
  private static boolean isBefore(String year1, String year2) {
    try {
      int y1 = Integer.parseInt(year1.trim());
      int y2 = Integer.parseInt(year2.trim());
      return y1+1 < y2;

    } catch (IllegalArgumentException e) {
      return false;
    }
  }
  /**
   * Mostly checks for required attributes so that subsequent postgres imports do not fail.
   */
  private void verify() {
    // verify uniqueness of name ids which are not covered by neo4j indices
    final Set<String> nameIds = new HashSet<>(store.size());
    store.all().forEach(t -> {
      require(t.name, t.name.getId(), "name id");
      require(t.name, t.taxon.getId(), "taxon id");
      if (!nameIds.add(t.name.getId())) {
        String msg = "Duplicate nameID "+ t.name.getId();
        LOG.error(msg);
        throw new NormalizationFailedException(msg);
      }

      // is it a source with verbatim data?
      require(t.name, t.name.getOrigin(), "name origin");

      // check for required fields to avoid pg exceptions
      require(t.name, t.name.getScientificName(), "scientific name");
      require(t.name, t.name.getRank(), "rank");
      require(t.name, t.name.getType(), "name type");

      // taxon or synonym
      if (!t.isSynonym()) {
        require(t.taxon, t.taxon.getOrigin(), "taxon origin");
        require(t.taxon, t.taxon.getStatus(), "taxon status");
      }

      // vernacular
      for (VernacularName v : t.vernacularNames) {
        require(v, v.getName(), "vernacular name");
      }

      // distribution
      for (Distribution d : t.distributions) {
        require(d, d.getGazetteer(), "distribution area standard");
        require(d, d.getArea(), "distribution area");
      }

      // verify source name and flag issues
      if (t.name.getVerbatimKey() != null) {
        VerbatimRecord v = store.getVerbatim(t.name.getVerbatimKey());
        if (NameValidator.flagIssues(t.name, v)) {
          store.put(v);
        }
      }
    });

    // flag PARENT_NAME_MISMATCH & PUBLISHED_BEFORE_GENUS for accepted names
    store.process(Labels.TAXON, store.batchSize, new NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        RankedName rn = NeoProperties.getRankedName(n);
        if (rn.rank.isSpeciesOrBelow()) {
          NeoTaxon sp = store.get(n);
          Node gn = Traversals.parentWithRankOf(sp.node, Rank.GENUS);
          if (gn != null) {
            NeoTaxon g = store.get(gn);
            // does the genus name match up?
            if (sp.name.isParsed() && g.name.isParsed() && !Objects.equals(sp.name.getGenus(), g.name.getUninomial())) {
              store.addIssues(sp.name, Issue.PARENT_NAME_MISMATCH);
            }
            // compare combination authorship years if existing
            if (sp.name.getCombinationAuthorship().getYear() != null && g.name.getCombinationAuthorship().getYear() != null) {
              if (isBefore(sp.name.getCombinationAuthorship().getYear(), g.name.getCombinationAuthorship().getYear())) {
                store.addIssues(sp.name, Issue.PUBLISHED_BEFORE_GENUS);
              }

            } else if (sp.name.getPublishedInId() != null && g.name.getPublishedInId() != null) {
              // compare publication years if existing
              Reference spr = store.refById(sp.name.getPublishedInId());
              Reference gr = store.refById(g.name.getPublishedInId());
              if (spr.getYear() != null && gr.getYear() != null && spr.getYear() < gr.getYear()) {
                store.addIssues(sp.name, Issue.PUBLISHED_BEFORE_GENUS);
              }
            }
          }
        }
      }

      @Override
      public void commitBatch(int counter) {
        LOG.debug("{} taxa verified", counter);
      }
    });

    // TODO: https://github.com/Sp2000/colplus-backend/issues/117
    // Issue.POTENTIAL_CHRESONYM;

    // TODO: https://github.com/Sp2000/colplus-backend/issues/114
    // Issue.POTENTIAL_VARIANT;

    // verify reference truncation
    for (Reference r : store.refList()) {
      if (NameValidator.hasUnmatchedBrackets(r.getCitation())) {
        store.addIssues(r, Issue.UNMATCHED_REFERENCE_BRACKETS);
      }
    }
  }

  private <T> T require(VerbatimEntity ent, T obj, String fieldName) {
    if (obj == null) {
      // in such fatal cases log the verbatim record in question for later debugging
      if (ent.getVerbatimKey() != null) {
        VerbatimRecord rec = store.getVerbatim(ent.getVerbatimKey());
        LOG.error("Missing {} of {}: {}", fieldName, ent.getClass().getSimpleName(), rec.toStringComplete());
      } else {
        LOG.error("Missing {} of {}. No verbatim for {}", fieldName, ent.getClass().getSimpleName(), ent);
      }
      String msg = String.format("%s missing for %s", fieldName, ent.getClass().getSimpleName());
      throw new NormalizationFailedException.MissingDataException(msg);
    }
    return obj;
  }

  private void matchAndCount() {
    final Map<MatchType, AtomicInteger> counts = Maps.newHashMap();
    for (MatchType mt : MatchType.values()) {
      counts.put(mt, new AtomicInteger(0));
    }
    // track duplicates, map index name ids to first verbatim key
    // if synonym negate the verbatim key to track status without needing more memory
    final Map<String, Integer> nameIds = new HashMap<>();
    store.all().forEach(t -> {
      NameMatch m = index.match(t.name, dataset.getContributesTo()!=null, false);
      if (m.hasMatch()) {
        t.name.setIndexNameId(m.getName().getId());
        store.update(t);
        // track duplicates regardless of status - but only for verbatim records!
        if (t.name.getVerbatimKey() != null) {
          if (nameIds.containsKey(m.getName().getId())) {
            Issue variant = null;
            Integer vKey = nameIds.get(m.getName().getId());
            if (!t.isSynonym()) {
              if (vKey < 0) {
                // first name was a synonym, this one is accepted. track accepted from now on instead
                nameIds.put(m.getName().getId(), t.name.getVerbatimKey());
              } else {
                // first name was also accepted, flag another issue on both
                variant = Issue.POTENTIAL_VARIANT;
              }
            }
            store.addIssues(Math.abs(vKey), Issue.DUPLICATE_NAME, variant);
            store.addIssues(t.name, Issue.DUPLICATE_NAME, variant);
          } else {
            Integer vKey = t.isSynonym() ? -1 * t.name.getVerbatimKey() : t.name.getVerbatimKey();
            nameIds.put(m.getName().getId(), vKey);
          }
        }
      }
      if (MATCH_ISSUES.containsKey(m.getType())) {
        store.addIssues(t.name, MATCH_ISSUES.get(m.getType()));
      }
      counts.get(m.getType()).incrementAndGet();
    });
    LOG.info("Matched all {} names: {}", MapUtils.sumValues(counts), Joiner.on(',').withKeyValueSeparator("=").join(counts));
  }

  private void normalize() {
    // cleanup synonym & parent relations
    cutSynonymCycles();
    relinkSynonymChains();
    preferSynonymOverParentRel();

    // cleanup basionym rels
    cutBasionymChains();

    // rectify taxonomic status
    rectifyTaxonomicStatus();

    // move synonym data to accepted
    moveSynonymData();

    // process the denormalized classifications of accepted taxa
    applyDenormedClassification();

    LOG.info("Relation setup completed.");
  }

  /**
   * Updates the taxonomic status according to rules defined in
   * https://github.com/Sp2000/colplus-backend/issues/93
   *
   * Currently only ambigous synonyms and misapplied names are derived
   * from names data and flagged by an issue.
   */
  private void rectifyTaxonomicStatus() {
    try (Transaction tx = store.getNeo().beginTx()) {
      store.all().forEach(t -> {
        if (t.isSynonym()) {
          // get a real neo4j node (store.all() only populates a dummy with an id)
          Node n = store.getNeo().getNodeById(t.node.getId());
          boolean ambigous = n.getDegree(RelType.SYNONYM_OF, Direction.OUTGOING) > 1;
          boolean misapplied = MisappliedNameMatcher.isMisappliedName(new NameAccordingTo(t.name, t.synonym.getAccordingTo()));
          TaxonomicStatus status = t.synonym.getStatus();

          if (status == TaxonomicStatus.MISAPPLIED) {
            if (!misapplied) {
              store.addIssues(t.taxon, Issue.TAXONOMIC_STATUS_DOUBTFUL);
            }

          } else if (status == TaxonomicStatus.AMBIGUOUS_SYNONYM) {
            if (misapplied) {
              t.synonym.setStatus(TaxonomicStatus.MISAPPLIED);
              store.addIssues(t.taxon, Issue.DERIVED_TAXONOMIC_STATUS);
              store.update(t);
            } else if (!ambigous) {
              store.addIssues(t.taxon, Issue.TAXONOMIC_STATUS_DOUBTFUL);
            }

          } else if (status == TaxonomicStatus.SYNONYM) {
            if (misapplied) {
              t.synonym.setStatus(TaxonomicStatus.MISAPPLIED);
              store.addIssues(t.taxon, Issue.DERIVED_TAXONOMIC_STATUS);
              store.update(t);
            } else if (ambigous) {
              t.synonym.setStatus(TaxonomicStatus.AMBIGUOUS_SYNONYM);
              store.addIssues(t.taxon, Issue.DERIVED_TAXONOMIC_STATUS);
              store.update(t);
            }
          }
        }
      });
    }
  }

  /**
   * Moves synonym data (distributions, vernacular, bibliography) to its accepted taxon.
   * https://github.com/Sp2000/colplus-backend/issues/108
   */
  private void moveSynonymData() {
    try (Transaction tx = store.getNeo().beginTx()) {
      store.all().forEach(t -> {
        if (t.isSynonym()) {
          if (!t.distributions.isEmpty() ||
              !t.vernacularNames.isEmpty() ||
              !t.bibliography.isEmpty()
          ) {
            // get a real neo4j node (store.all() only populates a dummy with an id)
            Node n = store.getNeo().getNodeById(t.node.getId());
            for (RankedName rn : store.accepted(n)) {
              NeoTaxon acc = store.get(rn.node);
              acc.distributions.addAll(t.distributions);
              acc.vernacularNames.addAll(t.vernacularNames);
              acc.bibliography.addAll(t.bibliography);
              store.update(acc);
            }

            t.distributions.clear();
            t.vernacularNames.clear();
            t.bibliography.clear();
            store.addIssues(t.synonym, Issue.SYNONYM_DATA_MOVED);
            store.update(t);
          }
        }
      });
    }
  }

  /**
   * Sanitizes basionym relations, cutting chains of basionym relations
   * by preferring basionyms referred to more often.
   */
  private void cutBasionymChains() {
    LOG.info("Cut basionym chains");
    final String query = "MATCH (x)-[r1:HAS_BASIONYM]->(b1)-[r2:HAS_BASIONYM]->(b2:ALL) " +
        "RETURN b1, b2, r1, r2 " +
        "ORDER BY x.id " +
        "LIMIT 1";

    int counter = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query);
      while (result.hasNext()) {
        Map<String, Object> row = result.next();
        Node b1 = (Node) row.get("b1");
        Node b2 = (Node) row.get("b2");

        // pick the bad relation to delete.
        // count number of incoming basionym relations = combinations
        int d1 = b1.getDegree(RelType.HAS_BASIONYM, Direction.INCOMING);
        int d2 = b2.getDegree(RelType.HAS_BASIONYM, Direction.INCOMING);

        // default to remove b2 with the "higher" sorting id property to get a determine result
        String badRelAlias;
        // but prefer r1 in case it links to a more used basionym
        if (d1 < d2) {
          badRelAlias = "r1";
        } else {
          badRelAlias = "r2";
        }
        Relationship bad = (Relationship) row.get(badRelAlias);

        // remove basionym relations
        addNameIssue(bad.getStartNode(), Issue.CHAINED_BASIONYM);
        addNameIssue(bad.getEndNode(), Issue.CHAINED_BASIONYM);
        LOG.debug("Delete rel {}-{}>{}", bad.getStartNodeId(), bad.getType().name(), bad.getEndNodeId());
        bad.delete();
        counter++;

        result = store.getNeo().execute(query);
      }
      tx.success();
    }
    LOG.info("{} basionym chains resolved", counter);
  }

  /**
   * Applies the classification given as denormalized higher taxa terms to accepted taxa
   * after the parent / accepted relations have been applied.
   * We need to be careful as the classification coming in first via the parentNameUsage(ID) terms
   * is variable and must not always include a rank.
   *
   * The classification is not applied to synonyms!
   */
  private void applyDenormedClassification() {
    LOG.info("Start processing higher denormalized classification ...");
    if (!meta.isDenormedClassificationMapped()) {
      LOG.info("No higher classification mapped");
      return;
    }

    store.process(Labels.TAXON, store.batchSize, new NodeBatchProcessor() {
      @Override
      public void process(Node n) {
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

      @Override
      public void commitBatch(int counter) {
        LOG.debug("Higher classifications processed for {} taxa", counter);
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
        addTaxonIssue(n, Issue.CLASSIFICATION_NOT_APPLIED);
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
        // apply the classification rank to unranked taxon
        updateRank(taxon.node, lowest);
        taxon = NeoProperties.getRankedName(taxon.node);
      }
    }
    // ignore same rank from classification if accepted
    if (!taxon.node.hasLabel(Labels.SYNONYM) && taxon.rank != null) {
      cl.setByRank(taxon.rank, null);
    }
    // ignore genus and below for synonyms
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
    // from kingdom to subgenus
    for (Rank hr : Classification.RANKS) {
      if ((taxon.rank == null || !taxon.rank.higherThan(hr)) && cl.getByRank(hr) != null) {
        // test for existing usage with that name & rank (allowing also unranked names)
        boolean found = false;
        for (Node n : store.byScientificName(Labels.TAXON, cl.getByRank(hr), hr, true)) {
          if (parent == null) {
            // make sure node does also not have a higher linnean rank parent
            Node p = Iterables.firstOrNull(Traversals.CLASSIFICATION.traverse(n).nodes());
            if (p == null) {
              // aligns!
              found = true;
            }

          } else {
            // verify the parents for the next higher rank are the same
            // we dont want to apply a contradicting classification with the same name
            Node p = Traversals.parentOf(n);
            Node p2 = Traversals.parentWithRankOf(n, parentRank);
            if ((p != null && p.equals(parent)) || (p2 != null && p2.equals(parent))) {
              found = true;
            } else if (p == null) {
              // if the matched node has not yet been denormalized we need to compare the classification prop
              NeoTaxon nt = store.get(n);
              if (nt.classification != null && nt.classification.equalsAboveRank(cl, hr)) {
                found = true;
              }
            }
          }

          if (found) {
            parent = n;
            parentRank = hr;
            // did we match against an unranked name? Then use the queried rank
            if (Rank.UNRANKED == NeoProperties.getRank(n, Rank.UNRANKED)) {
              updateRank(n, hr);
            }
            break;
          }
        }
        if (!found) {
          // persistent new higher taxon if not found
          Node lowerParent = createHigherTaxon(cl.getByRank(hr), hr).node;
          // insert parent relationship?
          store.assignParent(parent, lowerParent);
          parent = lowerParent;
          parentRank = hr;
        }
      }
    }
    // finally apply lowest parent to initial node
    store.assignParent(parent, taxon.node);
  }

  private void updateRank(Node n, Rank r) {
    NeoTaxon t = store.get(n);
    t.name.setRank(r);
    store.put(t);
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

        Node n = sr.getStartNode();
        addTaxonIssue(n, Issue.CHAINED_SYNONYM);
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

  private NeoTaxon createHigherTaxon(String uninomial, Rank rank) {
    Name n = new Name();
    n.setUninomial(uninomial);
    n.setRank(rank);
    n.setType(NameType.SCIENTIFIC);
    n.updateScientificName();
    NeoTaxon t = NeoTaxon.createTaxon(Origin.DENORMED_CLASSIFICATION, n, false);
    // store, which creates a new neo node
    store.put(t);
    return t;
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
          addTaxonIssue(syn, Issue.CHAINED_SYNONYM);
          store.createSynonymRel(syn, acc);
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
   * (Re)move parent relationship for synonyms, even if no synonym relation exists
   * but the node is just flagged to be a synonym. This happens for example when a synonym indicates
   * a non existing accepted name.
   *
   * If synonyms are parents of other taxa relinks relationship to the accepted
   * presence of both confuses subsequent imports, see http://dev.gbif.org/issues/browse/POR-2755
   */
  private void preferSynonymOverParentRel() {
    LOG.info("Cleanup relations, preferring synonym over parent relations");
    int parentOfRelDeleted = 0;
    int parentOfRelRelinked = 0;
    int childOfRelDeleted = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      for (Node syn : Iterators.loop(store.getNeo().findNodes(Labels.SYNONYM))) {
        // if the synonym is a parent of another child taxon - relink accepted as parent of child
        Set<Node> accepted = Traversals.acceptedOf(syn);
        for (Relationship pRel : syn.getRelationships(RelType.PARENT_OF, Direction.OUTGOING)) {
          Node child = pRel.getOtherNode(syn);
          pRel.delete();
          addTaxonIssue(syn, Issue.SYNONYM_PARENT);
          if (accepted.contains(child)) {
            // accepted is also the parent. Simply delete the parent rel in this case
            parentOfRelDeleted++;
          } else {
            String synonymName = NeoProperties.getScientificNameWithAuthor(syn);
            if (accepted.isEmpty()) {
              LOG.info("No accepted taxon for synonym {} with a child {}. Child becomes root taxon!", synonymName, NeoProperties.getScientificNameWithAuthor(child));
            } else {
              if (accepted.size() > 1) {
                // multiple accepted taxa. We will take the first, but log an issue!
                LOG.info("{} accepted taxa for synonym {} with a child {}. Relink child to first accepted only!", accepted.size(), synonymName, NeoProperties.getScientificNameWithAuthor(child));
              }
              store.assignParent(IterUtils.firstOrNull(accepted), child);
              parentOfRelRelinked++;
            }
          }
        }
        // remove parent rel for synonyms
        for (Relationship pRel : syn.getRelationships(RelType.PARENT_OF, Direction.INCOMING)) {
          pRel.delete();
          childOfRelDeleted++;
        }
      }
      tx.success();
    }
    LOG.info("Synonym relations cleaned up. "
            + "{} hasParent relations deleted,"
            + "{} isParentOf relations deleted, {} isParentOf rels moved from synonym to accepted",
        childOfRelDeleted, parentOfRelDeleted, parentOfRelRelinked);
  }

  private void addNameIssue(Node node, Issue issue) {
    NeoTaxon t = store.get(node);
    store.addIssues(t.name, issue);
  }
  private void addTaxonIssue(Node node, Issue issue) {
    NeoTaxon t = store.get(node);
    store.addIssues(t.taxon, issue);
  }

  private void insertData() throws NormalizationFailedException {
    // closing the batch inserter opens the normalizer db again for regular access via the store
    try {
      NeoInserter inserter;
      switch (dataset.getDataFormat()) {
        case COLDP:
          inserter = new ColDPInserter(store, sourceDir, refFactory);
          break;
        case DWCA:
          inserter = new DwcaInserter(store, sourceDir, refFactory);
          break;
        case ACEF:
          inserter = new AcefInserter(store, sourceDir, refFactory);
          break;
        default:
          throw new NormalizationFailedException("Unsupported data format " + dataset.getDataFormat());
      }
      meta = inserter.insertAll();

    } catch (NotUniqueRuntimeException e) {
      throw new NormalizationFailedException(e.getProperty() + " values not unique: " + e.getKey(), e);

    } catch (IOException e) {
      throw new NormalizationFailedException("IO error: " + e.getMessage(), e);
    }
  }
}