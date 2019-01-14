package org.col.admin.importer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.col.admin.importer.acef.AcefInserter;
import org.col.admin.importer.coldp.ColdpInserter;
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
  private MappingFlags meta;
  
  
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
   * @throws InterruptedException         in case the thread got interrupted, e.g. the import got cancelled
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
      return y1 + 1 < y2;
      
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
  
  /**
   * Mostly checks for required attributes so that subsequent postgres imports do not fail.
   */
  private void verify() {
    store.names().all().forEach(nn -> {
      Name n = nn.name;
      require(n, n.getId(), "name id");
  
      // is it a source with verbatim data?
      require(n, n.getOrigin(), "name origin");
  
      // check for required fields to avoid pg exceptions
      require(n, n.getScientificName(), "scientific name");
      require(n, n.getRank(), "rank");
      require(n, n.getType(), "name type");
  
  
      if (n.getVerbatimKey() != null){
        VerbatimRecord v = NameValidator.flagIssues(n, store.verbatimSupplier(n.getVerbatimKey()));
        if (v != null) {
          store.put(v);
        }
      }
  
    });
    
    store.usages().all().forEach(u -> {
      try {
        // taxon or synonym
        if (u.isSynonym()) {
          Synonym s = u.getSynonym();
          require(s, s.getOrigin(), "origin");
    
          // no vernaculars, distribution etc
          check(s, u.descriptions.isEmpty(), "no descriptions");
          check(s, u.distributions.isEmpty(), "no distributions");
          check(s, u.media.isEmpty(), "no media");
          check(s, u.vernacularNames.isEmpty(), "no vernacular names");
          
        } else {
          Taxon t = u.getTaxon();
          require(t, t.getId(), "id");
          require(t, t.getOrigin(), "origin");
          require(t, t.getStatus(), "status");
    
          // vernacular
          for (VernacularName v : u.vernacularNames) {
            require(v, v.getName(), "vernacular name");
          }
    
          // distribution
          for (Distribution d : u.distributions) {
            require(d, d.getGazetteer(), "area standard");
            require(d, d.getArea(), "area");
          }
    
        }
      } catch (NormalizationFailedException e) {
        LOG.error("{}: {}", e.getMessage(), u.getId());
        throw e;
      }
    });
    
    // flag PARENT_NAME_MISMATCH & PUBLISHED_BEFORE_GENUS for accepted names
    store.process(Labels.TAXON, store.batchSize, new NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        RankedUsage ru = NeoProperties.getRankedUsage(n);
        if (ru.rank.isSpeciesOrBelow()) {
          NeoName sp = store.names().objByNode(ru.nameNode);
          Node gn = Traversals.parentWithRankOf(ru.usageNode, Rank.GENUS);
          if (gn != null) {
            NeoName g = store.nameByUsage(gn);
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
  
  private void check(VerbatimEntity ent, boolean assertion, String msg) {
    if (!assertion) {
      // failed assertion
      String errMsg = String.format("%s check failed for %s", msg, ent.getClass().getSimpleName());
      throw new NormalizationFailedException.AssertionException(errMsg);
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
    store.names().all().forEach(t -> {
      NameMatch m = index.match(t.name, dataset.getContributesTo()!=null, false);
      if (m.hasMatch()) {
        t.name.setIndexNameId(m.getName().getId());
        store.names().update(t);
        // track duplicates regardless of status - but only for verbatim records!
        if (t.name.getVerbatimKey() != null) {
          if (nameIds.containsKey(m.getName().getId())) {
            store.addIssues(nameIds.get(m.getName().getId()), Issue.DUPLICATE_NAME);
            store.addIssues(t.name, Issue.DUPLICATE_NAME);
          } else {
            nameIds.put(m.getName().getId(), t.name.getVerbatimKey());
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
  
    // process the denormalized classifications of accepted taxa
    applyDenormedClassification();
  
    // remove orphan synonyms
    removeOrphanSynonyms();
  
    // move synonym data to accepted
    moveSynonymData();
  
    LOG.info("Normalization completed.");
  }
  
  private void removeOrphanSynonyms() {
    final String query = "MATCH (s:SYNONYM) WHERE NOT (s)-[:SYNONYM_OF]->() RETURN s";
    try (Transaction tx = store.getNeo().beginTx()) {
      int counter = 0;
      Result result = store.getNeo().execute(query);
      try (ResourceIterator<Node> nodes = result.columnAs("s")) {
        while (nodes.hasNext()) {
          Node syn = nodes.next();
          addUsageIssue(syn, Issue.ACCEPTED_NAME_MISSING);
          store.remove(syn);
          counter++;
        }
      }
      tx.success();
      LOG.info("{} orphan synonyms removed", counter);
    }
  }
  
  /**
   * Updates the taxonomic status according to rules defined in
   * https://github.com/Sp2000/colplus-backend/issues/93
   * <p>
   * Currently only ambigous synonyms and misapplied names are derived
   * from names data and flagged by an issue.
   */
  private void rectifyTaxonomicStatus() {
    try (Transaction tx = store.getNeo().beginTx()) {
      store.usages().all().forEach(u -> {
        if (u.isSynonym()) {
          Synonym syn = u.getSynonym();
          // getUsage a real neo4j node (store.allUsages() only populates a dummy with an id)
          Node n = store.getNeo().getNodeById(u.node.getId());
          Name name = store.names().objByNode(NeoProperties.getNameNode(n)).name;
          boolean ambigous = n.getDegree(RelType.SYNONYM_OF, Direction.OUTGOING) > 1;
          boolean misapplied = MisappliedNameMatcher.isMisappliedName(new NameAccordingTo(name, syn.getAccordingTo()));
          TaxonomicStatus status = syn.getStatus();

          if (status == TaxonomicStatus.MISAPPLIED) {
            if (!misapplied) {
              store.addIssues(syn, Issue.TAXONOMIC_STATUS_DOUBTFUL);
            }
            
          } else if (status == TaxonomicStatus.AMBIGUOUS_SYNONYM) {
            if (misapplied) {
              syn.setStatus(TaxonomicStatus.MISAPPLIED);
              store.addIssues(syn, Issue.DERIVED_TAXONOMIC_STATUS);
              store.usages().update(u);
            } else if (!ambigous) {
              store.addIssues(syn, Issue.TAXONOMIC_STATUS_DOUBTFUL);
            }
            
          } else if (status == TaxonomicStatus.SYNONYM) {
            if (misapplied) {
              syn.setStatus(TaxonomicStatus.MISAPPLIED);
              store.addIssues(syn, Issue.DERIVED_TAXONOMIC_STATUS);
              store.usages().update(u);
            } else if (ambigous) {
              syn.setStatus(TaxonomicStatus.AMBIGUOUS_SYNONYM);
              store.addIssues(syn, Issue.DERIVED_TAXONOMIC_STATUS);
              store.usages().update(u);
            }
          }
        }
      });
    }
  }
  
  /**
   * Moves synonym data (descriptions, distributions, media, vernacular, bibliography) to its accepted taxon.
   * https://github.com/Sp2000/colplus-backend/issues/108
   */
  private void moveSynonymData() {
    try (Transaction tx = store.getNeo().beginTx()) {
      store.usages().all().forEach(u -> {
        if (u.isSynonym()) {
          if (!u.distributions.isEmpty() ||
              !u.descriptions.isEmpty() ||
              !u.media.isEmpty() ||
              !u.vernacularNames.isEmpty() ||
              !u.bibliography.isEmpty()
          ) {
            // getUsage a real neo4j node (store.allUsages() only populates a dummy with an id)
            Node n = store.getNeo().getNodeById(u.node.getId());
            Traversals.ACCEPTED.traverse(n).nodes().forEach( accNode -> {
              NeoUsage acc = store.usages().objByNode(accNode);
              acc.distributions.addAll(u.distributions);
              acc.descriptions.addAll(u.descriptions);
              acc.media.addAll(u.media);
              acc.vernacularNames.addAll(u.vernacularNames);
              acc.bibliography.addAll(u.bibliography);
              store.usages().update(acc);
            });
  
            u.distributions.clear();
            u.descriptions.clear();
            u.media.clear();
            u.vernacularNames.clear();
            u.bibliography.clear();
            store.addIssues(u.usage, Issue.SYNONYM_DATA_MOVED);
            store.usages().update(u);
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
    final String query = "MATCH (x)-[r1:HAS_BASIONYM]->(b1)-[r2:HAS_BASIONYM]->(b2:NAME) " +
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

        // default to remove b2 with the "higher" sorting id property to getUsage a determine result
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
   * <p>
   * The classification is not applied to synonyms!
   */
  private void applyDenormedClassification() {
    if (!meta.isDenormedClassificationMapped()) {
      LOG.info("No higher classification mapped");
      return;
    }
    
    LOG.info("Start processing higher denormalized classification ...");
    store.process(Labels.TAXON, store.batchSize, new NodeBatchProcessor() {
      @Override
      public void process(Node u) {
        // the highest current parent of n
        RankedUsage highest = findHighestParent(u);
        // only need to apply classification if highest exists and is not already a kingdom, the denormed classification cannot add to it anymore!
        if (highest != null && highest.rank != Rank.KINGDOM) {
          NeoUsage t = store.usages().objByNode(u);
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

  private RankedUsage findHighestParent(Node n) {
    // the highest current parent of n
    RankedUsage highest = null;
    if (meta.isParentNameMapped()) {
      // verify if we already have a classification, that it ends with a known rank
      Node p = Iterables.lastOrNull(Traversals.PARENTS.traverse(n).nodes());
      highest = p == null ? null : NeoProperties.getRankedUsage(p);
      if (highest != null
          && !highest.usageNode.equals(n)
          && !highest.rank.notOtherOrUnranked()
          ) {
        LOG.debug("Node {} already has a classification which ends in an uncomparable rank.", n.getId());
        addUsageIssue(n, Issue.CLASSIFICATION_NOT_APPLIED);
        return null;
      }
    }
    if (highest == null) {
      // otherwise use this node
      highest = NeoProperties.getRankedUsage(n);
    }
    return highest;
  }
  
  /**
   * Applies the classification lc to the given RankedUsage taxon
   * @param taxon
   * @param cl
   */
  private void applyClassification(RankedUsage taxon, Classification cl) {
    // first modify classification to only keep those ranks we want to apply!
    // exclude lowest rank from classification to be applied if this taxon is rankless and has the same name
    if (taxon.rank == null || taxon.rank.isUncomparable()) {
      Rank lowest = cl.getLowestExistingRank();
      if (lowest != null && cl.getByRank(lowest).equalsIgnoreCase(taxon.name)) {
        cl.setByRank(lowest, null);
        // apply the classification rank to unranked taxon and reload immutable taxon instance
        updateRank(taxon.nameNode, lowest);
        taxon = NeoProperties.getRankedUsage(taxon.usageNode);
      }
    }
    // ignore same rank from classification if accepted
    if (!taxon.isSynonym() && taxon.rank != null) {
      cl.setByRank(taxon.rank, null);
    }
    // ignore genus and below for synonyms
    // http://dev.gbif.org/issues/browse/POR-2992
    if (taxon.isSynonym()) {
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
        for (Node n : store.usagesByName(cl.getByRank(hr), null, hr, true)) {
          // ignore synonyms
          if (n.hasLabel(Labels.SYNONYM)) continue;
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
              // if the matched node has not yet been denormalized we need to compare the classification props
              NeoUsage u = store.usages().objByNode(n);
              if (u.classification != null && u.classification.equalsAboveRank(cl, hr)) {
                found = true;
              }
            }
          }
          
          if (found) {
            parent = n;
            parentRank = hr;
            // did we match against an unranked name? Then use the queried rank
            RankedUsage ru = NeoProperties.getRankedUsage(n);
            if (Rank.UNRANKED == ru.rank) {
              updateRank(ru.nameNode, hr);
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
    store.assignParent(parent, taxon.usageNode);
  }
  
  /**
   * @param n a Name node (not usage!)
   */
  private void updateRank(Node n, Rank r) {
    NeoName name = store.names().objByNode(n);
    name.name.setRank(r);
    store.names().update(name);
  }
  
  /**
   * Sanitizes synonym relations and cuts cycles at lowest rank
   */
  private void cutSynonymCycles() {
    LOG.info("Cleanup synonym cycles");
    final String query = "MATCH (s)-[sr:SYNONYM_OF]->(x)-[:SYNONYM_OF*]->(s) RETURN sr LIMIT 1";

    int counter = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query);
      ;
      while (result.hasNext()) {
        Relationship sr = (Relationship) result.next().get("sr");
        
        Node n = sr.getStartNode();
        addUsageIssue(n, Issue.CHAINED_SYNONYM);
        sr.delete();
        
        if (counter++ % 100 == 0) {
          LOG.debug("Synonym cycles cut so far: {}", counter);
        }
        result = store.getNeo().execute(query);
        ;
      }
      tx.success();
    }
    LOG.info("{} synonym cycles resolved", counter);
  }

  private NeoUsage createHigherTaxon(String uninomial, Rank rank) {
    NeoUsage t = NeoUsage.createTaxon(Origin.DENORMED_CLASSIFICATION, false);

    Name n = new Name();
    n.setUninomial(uninomial);
    n.setRank(rank);
    n.setType(NameType.SCIENTIFIC);
    n.updateScientificName();
    t.usage.setName(n);
    // store both, which creates a single new neo node
    store.createNameAndUsage(t);
    return t;
  }
  
  /**
   * Sanitizes synonym relations relinking synonym of synonyms to make sure synonyms always point to a direct accepted taxon.
   */
  private void relinkSynonymChains() {
    LOG.debug("Relink synonym chains to single accepted");
    final String query = "MATCH (s)-[srs:SYNONYM_OF*]->(x)-[:SYNONYM_OF]->(t:TAXON) " +
        "WITH srs, t UNWIND srs AS sr " +
        "RETURN DISTINCT sr, t";
    int counter = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query);
      while (result.hasNext()) {
        Map<String, Object> row = result.next();
        Node acc = (Node) row.get("t");
        Relationship sr = (Relationship) row.get("sr");
        Node syn = sr.getStartNode();
        addUsageIssue(syn, Issue.CHAINED_SYNONYM);
        store.createSynonymRel(syn, acc);
        sr.delete();
        counter++;
      }
      tx.success();
    }
    LOG.info("{} synonym chains to a taxon resolved", counter);
  
    
    LOG.debug("Remove synonym chains missing any accepted");
    final String query2 = "MATCH (s)-[srs:SYNONYM_OF*]->(s2:SYNONYM) WHERE NOT (s2)-[:SYNONYM_OF]->() " +
        "WITH srs UNWIND srs AS sr " +
        "RETURN DISTINCT sr";
    AtomicInteger cnt = new AtomicInteger(0);
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query2);
      result.<Relationship>columnAs("sr").forEachRemaining( sr -> {
        Node syn = sr.getStartNode();
        addUsageIssue(syn, Issue.CHAINED_SYNONYM);
        sr.delete();
        cnt.incrementAndGet();
      });
      tx.success();
    }
    LOG.info("{} synonym chains to a taxon resolved", counter);
  }
  
  
  /**
   * Sanitizes relations by preferring synonym relations over parent rels.
   * (Re)move parent relationship for synonyms, even if no synonym relation exists
   * but the node is just flagged to be a synonym. This happens for example when a synonym indicates
   * a non existing accepted name.
   * <p>
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
          addUsageIssue(syn, Issue.SYNONYM_PARENT);
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
    store.addIssues(store.names().objByNode(node).name, issue);
  }
  
  private void addUsageIssue(Node node, Issue issue) {
    store.addIssues(store.usages().objByNode(node).usage, issue);
  }
  
  private void insertData() throws NormalizationFailedException {
    // closing the batch inserter opens the normalizer db again for regular access via the store
    try {
      NeoInserter inserter;
      switch (dataset.getDataFormat()) {
        case COLDP:
          inserter = new ColdpInserter(store, sourceDir, refFactory);
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
      inserter.insertAll();
      meta = inserter.reader.getMappingFlags();
      
      store.reportDuplicates();
      inserter.reportBadFks();
  
    } catch (NotUniqueRuntimeException e) {
      throw new NormalizationFailedException(e.getProperty() + " values not unique: " + e.getKey(), e);
      
    } catch (IOException e) {
      throw new NormalizationFailedException("IO error: " + e.getMessage(), e);
    }
  }
}