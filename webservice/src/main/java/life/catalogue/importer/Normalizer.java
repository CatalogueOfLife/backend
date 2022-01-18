package life.catalogue.importer;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.collection.IterUtils;
import life.catalogue.common.collection.MapUtils;
import life.catalogue.common.tax.MisappliedNameMatcher;
import life.catalogue.common.tax.RankUtils;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.importer.acef.AcefInserter;
import life.catalogue.importer.coldp.ColdpInserter;
import life.catalogue.importer.dwca.DwcaInserter;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.neo.NotUniqueRuntimeException;
import life.catalogue.importer.neo.model.*;
import life.catalogue.importer.neo.traverse.Traversals;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.importer.txttree.TxtTreeInserter;
import life.catalogue.matching.NameIndex;
import life.catalogue.parser.NameParser;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 *
 */
public class Normalizer implements Callable<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(Normalizer.class);
  private final DataFormat format;
  private final Path sourceDir;
  private final int datasetKey;
  private final NeoDb store;
  private final ReferenceFactory refFactory;
  private final ImageService imgService;
  private final NameIndex index;
  private final DatasetWithSettings dataset;
  private final Validator validator;
  private MappingFlags meta;


  public Normalizer(DatasetWithSettings dataset, NeoDb store, Path sourceDir, NameIndex index, ImageService imgService, Validator validator) {
    this.format = Preconditions.checkNotNull(dataset.getDataFormat(), "Data format not given");
    this.dataset = dataset;
    this.sourceDir = sourceDir;
    this.store = store;
    this.datasetKey = dataset.getKey();
    refFactory = new ReferenceFactory(datasetKey, store.references());
    this.index = index;
    this.imgService = imgService;
    this.validator = validator;
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
      // create new id generator being aware of existing ids we inserted up to now
      store.updateIdGenerators();
      // insert normalizer db relations, create implicit nodes if needed and parse names
      checkIfCancelled();
      normalize();
      // sync taxon KVP store with neo4j relations, setting correct neo4j labels, homotypic keys etc
      checkIfCancelled();
      store.sync();
      // verify, derive issues and fail before we do expensive matching or even db imports
      checkIfCancelled();
      validate();
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
   * Mostly checks for required attributes so that subsequent postgres imports do not fail,
   * but also does further issue flagging.
   */
  private void validate() {
    store.names().all().forEach(nn -> {
      Name n = nn.getName();
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
          Synonym s = u.asSynonym();
          require(s, s.getId(), "id");
          require(s, s.getOrigin(), "origin");

          // no vernaculars, distribution etc
          check(s, u.treatment == null, "no treatments");
          check(s, u.distributions.isEmpty(), "no distributions");
          check(s, u.media.isEmpty(), "no media");
          check(s, u.vernacularNames.isEmpty(), "no vernacular names");

        } else {
          Taxon t = u.asTaxon();
          require(t, t.getId(), "id");
          require(t, t.getOrigin(), "origin");
          require(t, t.getStatus(), "status");

          // vernacular
          for (VernacularName v : u.vernacularNames) {
            require(v, v.getName(), "vernacular name");
          }

          // distribution
          for (Distribution d : u.distributions) {
            require(d, d.getArea(), "area");
          }

        }
      } catch (NormalizationFailedException e) {
        LOG.error("{}: {}", e.getMessage(), u.getId());
        throw e;
      }
    });

    // flag PARENT_NAME_MISMATCH, PUBLISHED_BEFORE_GENUS, PARENT_SPECIES_MISSING & CLASSIFICATION_RANK_ORDER_INVALID for accepted names
    store.process(Labels.TAXON, store.batchSize, new NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        RankedUsage ru = NeoProperties.getRankedUsage(n);
        // compare with parent rank
        Node pNode = Traversals.parentWithConcreteRank(n);
        if (pNode != null && !ru.rank.isUncomparable()) {
          Node pNameNode = NeoProperties.getNameNode(pNode);
          Rank pRank = NeoProperties.getRank(pNameNode, Rank.UNRANKED);
          if (ru.rank == pRank || RankUtils.higherThanCodeAgnostic(ru.rank, pRank)) {
            store.addUsageIssues(n, Issue.CLASSIFICATION_RANK_ORDER_INVALID);
          }
        }

        if (ru.rank.isSpeciesOrBelow()) {
          NeoName nn = store.names().objByNode(ru.nameNode);
          Node gn = Traversals.parentWithRankOf(ru.usageNode, Rank.GENUS);
          if (gn != null) {
            NeoName g = store.nameByUsage(gn);
            // does the genus name match up?
            if (nn.getName().isParsed() && g.getName().isParsed() && !Objects.equals(nn.getName().getGenus(), g.getName().getUninomial())) {
              store.addIssues(nn.getName(), Issue.PARENT_NAME_MISMATCH);
            }
            // compare combination authorship years if existing
            if (nn.getName().getCombinationAuthorship().getYear() != null && g.getName().getCombinationAuthorship().getYear() != null) {
              if (isBefore(nn.getName().getCombinationAuthorship().getYear(), g.getName().getCombinationAuthorship().getYear())) {
                store.addIssues(nn.getName(), Issue.PUBLISHED_BEFORE_GENUS);
              }

            } else if (nn.getName().getPublishedInId() != null && g.getName().getPublishedInId() != null) {
              // compare publication years if existing
              Reference spr = store.references().get(nn.getName().getPublishedInId());
              Reference gr = store.references().get(g.getName().getPublishedInId());
              if (spr.getYear() != null && gr.getYear() != null && spr.getYear() < gr.getYear()) {
                store.addIssues(nn.getName(), Issue.PUBLISHED_BEFORE_GENUS);
              }
            }
          }

          // further checks for infraspecifics
          if (ru.rank.isInfraspecific()) {
            Node sp = Traversals.parentWithRankOf(ru.usageNode, Rank.SPECIES);
            if (sp == null) {
              store.addIssues(nn.getName(), Issue.PARENT_SPECIES_MISSING);
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
    for (Reference r : store.references()) {
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
    final Int2IntMap nameIds = new Int2IntOpenHashMap();
    store.names().all().forEach(nn -> {
      NameMatch m = index.match(nn.getName(), true, false);
      nn.namesIndexMatchType = m.getType();
      if (m.hasMatch()) {
        int nKey = m.getName().getKey();
        nn.namesIndexId = nKey;
        store.names().update(nn);
        // track duplicates regardless of status - but only for verbatim records!
        if (nn.getName().getVerbatimKey() != null) {
          if (nameIds.containsKey(nKey)) {
            store.addIssues(nameIds.get(nKey), Issue.DUPLICATE_NAME);
            store.addIssues(nn.getName(), Issue.DUPLICATE_NAME);
          } else {
            nameIds.put(nKey, (int) nn.getName().getVerbatimKey());
          }
        }
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

    // deduplicate name relations
    reduceRedundantNameRels();

    // cleanup basionym rels
    cutBasionymChains();

    // rectify taxonomic status
    rectifyTaxonomicStatus();

    // process the denormalized classifications of accepted taxa
    applyDenormedClassification();

    // move synonym data to accepted
    moveSynonymData();

    // remove orphan synonyms
    removeOrphanSynonyms();

    LOG.info("Normalization completed.");
  }

  private void reduceRedundantNameRels() {
    for (NomRelType type : NomRelType.values()) {
      reduceRedundantNameRels(type);
    }
  }

  private void reduceRedundantNameRels(NomRelType type) {
    LOG.info("Remove redundant {} relations", type);
    RelType rt = RelType.from(type);
    final String query = String.format("MATCH (n:NAME)-[r1:%s]->(b:NAME)<-[r2:%s]-(n:NAME)", rt, rt) +
      "RETURN r1, r2 " +
      "LIMIT 1";

    int counter = 0;
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query);
      while (result.hasNext()) {
        Map<String, Object> row = result.next();
        Relationship r1 = (Relationship) row.get("r1");
        Relationship r2 = (Relationship) row.get("r2");

        NameRelation nr1 = store.toNameRelation(r1);

        // delete the relation with the least info
        Relationship del = nr1.isRich() ? r2 : r1;
        del.delete();
        counter++;
        LOG.debug("Deleted redundant {} relation {}", type, del);

        result = store.getNeo().execute(query);
      }
      tx.success();
    }
    LOG.info("{} redundant {} relations removed", counter, type);
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
          Synonym syn = u.asSynonym();
          // getUsage a real neo4j node (store.allUsages() only populates a dummy with an id)
          Node n = store.getNeo().getNodeById(u.node.getId());
          Name name = store.names().objByNode(NeoProperties.getNameNode(n)).getName();
          boolean ambigous = n.getDegree(RelType.SYNONYM_OF, Direction.OUTGOING) > 1;
          boolean misapplied = MisappliedNameMatcher.isMisappliedName(new ParsedNameUsage(name, false, syn.getAccordingToId(), null));
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
    AtomicInteger moved = new AtomicInteger(0);
    AtomicInteger removed = new AtomicInteger(0);
    AtomicBoolean hasAccepted = new AtomicBoolean(false);
    try (Transaction tx = store.getNeo().beginTx()) {
      store.usages().all().forEach(u -> {
        if (u.isSynonym()) {
          if (!u.distributions.isEmpty() ||
              !u.media.isEmpty() ||
              !u.vernacularNames.isEmpty()
          ) {
            // getUsage a real neo4j node (store.allUsages() only populates a dummy with an id)
            Node n = store.getNeo().getNodeById(u.node.getId());
            hasAccepted.set(false);
            Traversals.ACCEPTED.traverse(n).nodes().forEach( accNode -> {
              NeoUsage acc = store.usages().objByNode(accNode);
              acc.distributions.addAll(u.distributions);
              acc.media.addAll(u.media);
              acc.vernacularNames.addAll(u.vernacularNames);
              store.usages().update(acc);
              hasAccepted.set(true);
            });

            u.distributions.clear();
            u.media.clear();
            u.vernacularNames.clear();
            store.addIssues(u.usage, Issue.SYNONYM_DATA_MOVED);
            store.usages().update(u);
            if (hasAccepted.get()) {
              moved.incrementAndGet();
            } else {
              removed.incrementAndGet();
            }
          }
        }
      });
    }
    LOG.info("Moved associated data from {} synonyms to their accepted taxon", moved);
    LOG.info("Removed associated data from {} synonyms without accepted taxon", removed);
  }

  /**
   * Sanitizes basionym relations, cutting chains of basionym relations
   * by preferring basionyms referred to more often.
   */
  private void cutBasionymChains() {
    LOG.info("Cut basionym chains");
    int counter = 0;
    while (true) {
      int cut = cutNonOverlappingBasionymChains();
      if (cut == 0) {
        break;
      }
      counter += cut;
    }
    LOG.info("{} basionym chains resolved", counter);
  }

  private int cutNonOverlappingBasionymChains() {
    final String query = "MATCH (x)-[r1:HAS_BASIONYM]->(b1)-[r2:HAS_BASIONYM]->(b2:NAME) " +
      "RETURN x, b1, b2, r1, r2";
    int counter = 0;
    LongSet visited = new LongOpenHashSet();
    try (Transaction tx = store.getNeo().beginTx()) {
      Result result = store.getNeo().execute(query);
      while (result.hasNext()) {
        Map<String, Object> row = result.next();
        Node x = (Node) row.get("x");
        Node b1 = (Node) row.get("b1");
        Node b2 = (Node) row.get("b2");

        // make sure any of the 3 nodes in play have not been visited before
        // otherwise skip this relationship and wait for the next round
        if (visited.contains(x.getId()) || visited.contains(b1.getId()) || visited.contains(b2.getId())) {
          LOG.debug("Skip overlapping relation {}->{}->{}", x.getId(), b1.getId(), b2.getId());
          continue;
        }
        visited.add(x.getId());
        visited.add(b1.getId());
        visited.add(b2.getId());

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
      }
      tx.success();
    }
    return counter;
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
      cl.setSection(null);
      cl.setSpecies(null);
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
            if ((p != null && p.equals(parent)) || (p2 != null && p2.equals(parent) && mappedRanksInBetween(n, p2).isEmpty())) {
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

  private Set<Rank> mappedRanksInBetween(Node n, Node n2){
    return Traversals.parentsUntil(n, n2).stream()
        .map(NeoProperties::getRankedUsage)
        .map(ru -> ru.rank)
        .filter(r -> meta.getDenormedRanksMapped().contains(r))
        .collect(Collectors.toSet());
  }

  /**
   * @param n a Name node (not usage!)
   */
  private void updateRank(Node n, Rank r) {
    NeoName name = store.names().objByNode(n);
    name.getName().setRank(r);
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
    NeoUsage t = NeoUsage.createTaxon(Origin.DENORMED_CLASSIFICATION, TaxonomicStatus.ACCEPTED);

    Name n = new Name();
    n.setUninomial(uninomial);
    n.setRank(rank);
    n.rebuildScientificName();
    // determine type - can e.g. be placeholders
    n.setType(NameParser.PARSER.determineType(n).orElse(NameType.SCIENTIFIC));
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
    store.addIssues(store.names().objByNode(node).getName(), issue);
  }

  private void addUsageIssue(Node node, Issue issue) {
    store.addIssues(store.usages().objByNode(node).usage, issue);
  }

  private void insertData() throws NormalizationFailedException {
    // closing the batch inserter opens the normalizer db again for regular access via the store
    try {
      NeoInserter inserter;
      switch (format) {
        case COLDP:
          inserter = new ColdpInserter(store, sourceDir, dataset.getSettings(), refFactory);
          break;
        case DWCA:
          inserter = new DwcaInserter(store, sourceDir, dataset.getSettings(), refFactory);
          break;
        case ACEF:
          inserter = new AcefInserter(store, sourceDir, dataset.getSettings(), refFactory);
          break;
        case TEXT_TREE:
          inserter = new TxtTreeInserter(store, sourceDir);
          break;
        default:
          throw new NormalizationFailedException("Unsupported data format " + format);
      }
      // first metadata, the key will be preserved by the store
      inserter.readMetadata().ifPresent(d -> PgImport.updateMetadata(dataset.getDataset(), d.getDataset(), validator));
      // data
      inserter.insertAll();
      meta = inserter.getMappingFlags();
      store.reportDuplicates();
      inserter.reportBadFks();
      // lookout for local logo file
      inserter.logo().ifPresent(l -> {
        try {
          imgService.putDatasetLogo(datasetKey, ImageServiceFS.read(Files.newInputStream(l)));
        } catch (IOException e) {
          LOG.warn("Failed to read local logo file {}", l);
        }
      });

    } catch (NotUniqueRuntimeException e) {
      throw new NormalizationFailedException(e.getProperty() + " values not unique: " + e.getKey(), e);

    } catch (IOException e) {
      throw new NormalizationFailedException("IO error: " + e.getMessage(), e);
    }
  }
}