package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.collection.CountMap;
import life.catalogue.common.collection.MapUtils;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.tax.MisappliedNameMatcher;
import life.catalogue.csv.MappingInfos;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.importer.acef.AcefInserter;
import life.catalogue.importer.coldp.ColdpInserter;
import life.catalogue.importer.dwca.DwcaInserter;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.NotUniqueRuntimeException;
import life.catalogue.importer.store.model.NameData;
import life.catalogue.importer.store.model.UsageData;
import life.catalogue.importer.txttree.TxtTreeInserter;
import life.catalogue.interpreter.ExtinctName;
import life.catalogue.interpreter.RanKnName;
import life.catalogue.matching.NameValidator;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.metadata.DoiResolver;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import jakarta.validation.Validator;

/**
 *
 */
public class Normalizer implements Callable<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(Normalizer.class);
  private final DataFormat format;
  private final Path sourceDir;
  private final int datasetKey;
  private final ImportStore store;
  private final ReferenceFactory refFactory;
  private final ImageService imgService;
  private final NameIndex index;
  private final DatasetWithSettings dataset;
  private final Validator validator;
  private MappingInfos meta;
  // parsing is expensive so we cache the higher taxa names that we need to parse a lot
  private final LoadingCache<RanKnName, ExtinctName> parseCache = Caffeine.newBuilder()
    .maximumSize(10000)
    .build(this::parse);


  public Normalizer(DatasetWithSettings dataset, ImportStore store, Path sourceDir, NameIndex index, ImageService imgService, Validator validator, @Nullable DoiResolver resolver) {
    this.format = Preconditions.checkNotNull(dataset.getDataFormat(), "Data format not given");
    this.dataset = dataset;
    this.sourceDir = sourceDir;
    this.store = store;
    this.datasetKey = dataset.getKey();
    refFactory = new ReferenceFactory(datasetKey, store.references(), resolver);
    if (dataset.getSettings().has(Setting.DOI_RESOLUTION)) {
      refFactory.setResolveDOIs(dataset.getSettings().getEnum(Setting.DOI_RESOLUTION));
    }
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
    // batch import verbatim records
    insertData();
    checkIfCancelled();
    // make id generator aware of existing ids we inserted up to now
    store.updateIdGenerators();
    checkIfCancelled();
    // insert normalizer db relations, create implicit nodes if needed and parse names
    normalize();
    checkIfCancelled();
    // apply missing dataset defaults, verify, derive issues and fail before we do expensive matching or even db imports
    validateAndDefaults();
    checkIfCancelled();
    // matches names and taxon concepts and builds metrics per name/taxon
    matchAndCount();
    LOG.info("Normalization succeeded");

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

  private Boolean isExtinctBySetting(Rank rank) {
    return dataset.getExtinct() != null && rank != null && !rank.isUncomparable() && !rank.higherThan(dataset.getExtinct());
  }

  /**
   * Mostly checks for required attributes so that subsequent postgres imports do not fail,
   * but also does further issue flagging and applying of missing dataset defaults.
   */
  private void validateAndDefaults() throws InterruptedException {
    final NomCode defaultCode = dataset.getCode();
    final Set<Environment> defaultEnvironment = dataset.getEnvironment() == null ? null : Set.of(dataset.getEnvironment());

    LOG.info("Start name validation");
    store.names().all().forEach(nn -> {
      Name n = nn.getName();

      // dataset defaults
      if (defaultCode != null && n.getCode() == null) {
        n.setCode(defaultCode);
        store.names().update(nn);
      }
      require(n, n.getId(), "name id");

      // is it a source with verbatim data?
      require(n, n.getOrigin(), "name origin");

      // check for required fields to avoid pg exceptions
      require(n, n.getScientificName(), "scientific name");
      require(n, n.getRank(), "rank");
      require(n, n.getType(), "name type");

      // all names should have a verbatim record by now - even implicit ones!
      VerbatimRecord v = NameValidator.flagIssues(n, store.verbatimSupplier(n.getVerbatimKey()));
      if (v != null) {
        store.put(v);
      }
    });

    LOG.info("Apply dataset defaults");
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

          // dataset defaults
          boolean updateNeeded = false;
          if (t.isExtinct() == null && isExtinctBySetting(t.getRank())) {
            t.setExtinct(true);
            updateNeeded = true;
          }
          if (defaultEnvironment != null && (t.getEnvironments() == null || t.getEnvironments().isEmpty())) {
            t.setEnvironments(defaultEnvironment);
            updateNeeded = true;
          }
          if (updateNeeded) {
            store.usages().update(u);
          }

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

    // TODO: flag PARENT_NAME_MISMATCH, PUBLISHED_BEFORE_GENUS, PARENT_SPECIES_MISSING & CLASSIFICATION_RANK_ORDER_INVALID for accepted names
    LOG.info("Flag classification issues");

    // verify reference truncation
    LOG.info("Validate references");
    for (Reference r : store.references()) {
      if (NameValidator.hasUnmatchedBrackets(r.getCitation())) {
        store.addIssues(r, Issue.UNMATCHED_REFERENCE_BRACKETS);
      }
    }
    LOG.info("All validations completed");
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
      nn.getName().setNamesIndexType(m.getType());
      if (m.hasMatch()) {
        int nKey = m.getName().getKey();
        nn.getName().setNamesIndexId(nKey);
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
      store.names().update(nn);
      counts.get(m.getType()).incrementAndGet();
      Exceptions.runtimeInterruptIfCancelled();
    });
    LOG.info("Matched all {} names: {}", MapUtils.sumValues(counts), Joiner.on(',').withKeyValueSeparator("=").join(counts));
  }

  private void normalize() throws InterruptedException {
    // validate relation constraints
    validateRelations();

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

  private void validateRelations() {
    LOG.info("Validate parent relations");
    LOG.info("Validate basionym relations");
  }

  static class UniqueNameRel {
    final NomRelType type;
    final String from;
    final String to;

    public UniqueNameRel(NomRelType type, String from, String to) {
      this.type = type;
      this.from = from;
      this.to = to;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof UniqueNameRel that)) return false;

      return type == that.type &&
        Objects.equals(from, that.from) &&
        Objects.equals(to, that.to);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, from, to);
    }
  }

  private void reduceRedundantNameRels() {
    CountMap<NomRelType> counter = new CountMap<>();
    store.names().all().forEach(n -> {
      Set<UniqueNameRel> rels = new HashSet<>();
      var iter = n.relations.iterator();
      while (iter.hasNext()) {
        var r = iter.next();
        if (!rels.add(new UniqueNameRel(r.getType(), r.getFromID(), r.getToID()))) {
          // redundant - remove it
          iter.remove();
          counter.inc(r.getType());
        }
      }
    });
    for (var entry : counter.entrySet()) {
      if (entry.getValue() > 0) {
        LOG.info("{} redundant {} relations removed", entry.getValue(), entry.getKey());
      }
    }
  }

  private void removeOrphanSynonyms() {
    final AtomicInteger counter = new AtomicInteger(0);
    store.usages().all().forEach(syn -> {
      if (syn.isSynonym()) {
        addUsageIssue(syn, Issue.ACCEPTED_NAME_MISSING);
        store.usages().remove(syn.getId());
        counter.incrementAndGet();
      }
    });
    LOG.info("{} orphan synonyms removed", counter);
  }

  /**
   * Updates the taxonomic status according to rules defined in
   * https://github.com/Sp2000/colplus-backend/issues/93
   * <p>
   * Currently only ambigous synonyms and misapplied names are derived
   * from names data and flagged by an issue.
   */
  private void rectifyTaxonomicStatus() {
    store.usages().all().forEach(u -> {
      if (u.isSynonym()) {
        Synonym syn = u.asSynonym();
        var n = store.names().objByID(u.nameID);
        boolean ambiguous = !n.usageIDs.isEmpty();
        boolean misapplied = MisappliedNameMatcher.isMisappliedName(new ParsedNameUsage(n.getName(), false, syn.getAccordingToId(), null));
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
          } else if (!ambiguous) {
            store.addIssues(syn, Issue.TAXONOMIC_STATUS_DOUBTFUL);
          }

        } else if (status == TaxonomicStatus.SYNONYM) {
          if (misapplied) {
            syn.setStatus(TaxonomicStatus.MISAPPLIED);
            store.addIssues(syn, Issue.DERIVED_TAXONOMIC_STATUS);
            store.usages().update(u);
          } else if (ambiguous) {
            syn.setStatus(TaxonomicStatus.AMBIGUOUS_SYNONYM);
            store.addIssues(syn, Issue.DERIVED_TAXONOMIC_STATUS);
            store.usages().update(u);
          }
        }
      }
    });
  }

  /**
   * Moves synonym data (descriptions, distributions, media, vernacular, bibliography) to its accepted taxon.
   * https://github.com/Sp2000/colplus-backend/issues/108
   */
  private void moveSynonymData() {
    AtomicInteger moved = new AtomicInteger(0);
    AtomicInteger removed = new AtomicInteger(0);
    AtomicBoolean hasAccepted = new AtomicBoolean(false);
    store.usages().all().forEach(u -> {
      if (u.isSynonym()) {
        if (u.hasSupplementaryInfos()) {
          hasAccepted.set(false);
          for (var acc : store.usages().accepted(u)) {
            acc.distributions.addAll(u.distributions);
            acc.media.addAll(u.media);
            acc.vernacularNames.addAll(u.vernacularNames);
            acc.estimates.addAll(u.estimates);
            acc.properties.addAll(u.properties);
            store.usages().update(acc);
            hasAccepted.set(true);
          }

          u.distributions.clear();
          u.media.clear();
          u.vernacularNames.clear();
          u.estimates.clear();
          u.properties.clear();
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
    //TODO: impl see old code
    int counter = 0;
    LongSet visited = new LongOpenHashSet();
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
  private void applyDenormedClassification() throws InterruptedException {
    if (!meta.isDenormedClassificationMapped()) {
      LOG.info("No higher classification mapped");
      return;
    }

    LOG.info("Start processing higher denormalized classification ...");
    store.usages().allTaxa().forEach(u -> {
      // the highest current parent of n
      var highest = findHighestParent(u);
      // only need to apply classification if highest exists and is not already a kingdom, the denormed classification cannot add to it anymore!
      if (highest != null && highest.usage.getRank() != Rank.KINGDOM) {
        if (u.classification != null) {
          applyClassification(highest, u.classification);
        }
      }
    });
  }

  private UsageData findHighestParent(UsageData n) {
    // the highest current parent of n
    UsageData highest = null;
    if (meta.isParentNameMapped()) {
      // verify if we already have a classification, that it ends with a known rank
      highest = CollectionUtils.lastOrNull(store.usages().parents(n));
      if (highest != null
          && !highest.getId().equals(n.getId())
          && !highest.usage.getRank().notOtherOrUnranked()
      ) {
        LOG.debug("Usage {} already has a classification which ends in an uncomparable rank.", n.getId());
        addUsageIssue(n, Issue.CLASSIFICATION_NOT_APPLIED);
        return null;
      }
    }
    if (highest == null) {
      // otherwise use this node
      highest = n;
    }
    return highest;
  }

  /**
   * Applies the classification lc to the given RankedUsage taxon
   * @param taxon
   * @param cl
   */
  private void applyClassification(UsageData taxon, Classification cl) {
    // first modify classification to only keep those ranks we want to apply!
    // exclude lowest rank from classification to be applied if this taxon is rankless and has the same name
    var nn = store.loadName(taxon);
    if (nn.getName().getRank() == null || nn.getName().getRank().isUncomparable()) {
      Rank lowest = cl.getLowestExistingRank();
      if (lowest != null && cl.getByRank(lowest).equalsIgnoreCase(nn.getName().getScientificName())) {
        cl.setByRank(lowest, null);
        // apply the classification rank to unranked taxon and reload immutable taxon instance
        updateRank(nn, lowest);
      }
    }
    // ignore same rank from classification if accepted
    if (!taxon.isSynonym() && nn.getName().getRank() != null) {
      cl.setByRank(nn.getName().getRank(), null);
    }
    // ignore genus and below for synonyms
    // http://dev.gbif.org/issues/browse/POR-2992
    if (taxon.isSynonym()) {
      cl.setGenus(null);
      cl.setSubgenus(null);
      cl.setSection(null);
      cl.setSpecies(null);
    }

    // now reconstruct the given classification with the parentID field
    // reusing existing taxa if possible, otherwise creating new ones
    // and at the very end apply that classification to the taxon
    UsageData parent = null;
    Rank parentRank = null;
    // from kingdom to subgenus
    for (final Rank hr : Classification.RANKS) {
      if ((nn.getName().getRank() == null || !nn.getName().getRank().higherThan(hr)) && cl.getByRank(hr) != null) {
        // test for existing usage with that name & rank (allowing also unranked names)
        boolean found = false;
        // we need to lookup the name by its normed form as we create them via createHigherTaxon
        // to be safe we query for both versions
        var rnn = new RanKnName(hr, cl.getByRankCleaned(hr));
        final ExtinctName normedName = parseCache.get(rnn);
        for (String uid : store.usageIDsByNames(hr, true, cl.getByRankCleaned(hr), normedName.pname == null ? null : normedName.pname.getScientificName())) {
          // ignore synonyms
          var u = store.usages().objByID(uid);
          if (u.isSynonym()) continue;
          if (parent == null) {
            // make sure node does also not have a higher linnean rank parent
            if (u.usage.getParentId() == null) {
              // aligns!
              found = true;
            }

          } else {
            // verify the parents for the next higher rank are the same
            // we dont want to apply a contradicting classification with the same name
            var p = store.usages().objByID(u.usage.getParentId());
            var p2 = store.usages().parent(u, parentRank);
            if ((p != null && p.getId().equals(parent.getId())) || (p2 != null && p2.getId().equals(parent.getId()) && mappedRanksInBetween(p, p2).isEmpty())) {
              found = true;
            } else if (p == null) {
              // if the matched node has not yet been denormalized we need to compare the classification props
              if (u.classification != null && u.classification.equalsAboveRank(cl, hr)) {
                found = true;
              }
            }
          }

          if (found) {
            parent = u;
            parentRank = hr;
            // did we match against an unranked name? Then use the queried rank
            var un = store.names().objByID(u.nameID);
            if (Rank.UNRANKED == un.getRank()) {
              updateRank(un, hr);
            }
            break;
          }
        }
        if (!found) {
          // persistent new higher taxon if not found
          var lowerParent = createHigherTaxon(normedName, hr);
          // insert parent relationship?
          store.usages().assignParent(lowerParent, parent.getId());
          parent = lowerParent;
          parentRank = hr;
        }
      }
    }
    // finally apply lowest parent to initial node
    store.usages().assignParent(taxon, parent.getId());
  }

  private Set<Rank> mappedRanksInBetween(UsageData u1, UsageData u2){
    return store.usages().parentsUntil(u1, u2.getId()).stream()
        .map(ru -> {
          var n = store.names().objByID(ru.nameID);
          return n.getRank();
        })
        .filter(r -> meta.getDenormedRanksMapped().contains(r))
        .collect(Collectors.toSet());
  }

  private void updateRank(NameData name, Rank r) {
    name.getName().setRank(r);
    store.names().update(name);
  }

  /**
   * Sanitizes synonym relations and cuts cycles at lowest rank
   */
  private void cutSynonymCycles() {
    //TODO: impl see old code Issue.CHAINED_SYNONYM
    LOG.info("Cleanup synonym cycles");
    final String query = "MATCH (s)-[sr:SYNONYM_OF]->(x)-[:SYNONYM_OF*]->(s) RETURN sr LIMIT 1";

    int counter = 0;
    LOG.info("{} synonym cycles resolved", counter);
  }

  private ExtinctName parse(RanKnName rnn) throws InterruptedException {
    var ename = new ExtinctName(rnn.name);
    ename.pname = new Name();
    ename.pname.setRank(rnn.rank);
    ename.pname.setScientificName(rnn.name);
    ename.pname.setCode(dataset.getCode());
    // parses the instance and determines the type - can e.g. be placeholders
    NameParser.PARSER.parse(ename.pname, IssueContainer.VOID);
    // reset rank as parser might have infered ranks from the name!
    ename.pname.setRank(rnn.rank);
    return ename;
  }

  /**
   * Creates a new denormalised higher taxon usage.
   * The given uninomial is allowed to contain a dagger to indicate extinct taxa.
   */
  private UsageData createHigherTaxon(ExtinctName eName, Rank rank) {
    UsageData t = UsageData.createTaxon(Origin.DENORMED_CLASSIFICATION, TaxonomicStatus.ACCEPTED);

    eName.pname.setId(null); // we don't want to reuse the name id
    t.usage.setName(eName.pname);

    if (eName.extinct || isExtinctBySetting(rank)) {
      t.asTaxon().setExtinct(true);
    }
    if (dataset.getEnvironment() != null) {
      t.asTaxon().setEnvironments(Set.of(dataset.getEnvironment()));
    }
    // store both, which creates a single new neo node
    store.createNameAndUsage(t);
    return t;
  }

  /**
   * Sanitizes synonym relations relinking synonym of synonyms to make sure synonyms always point to a direct accepted taxon.
   */
  private void relinkSynonymChains() {
    //TODO: impl see old code Issue.CHAINED_SYNONYM
    LOG.debug("Relink synonym chains to single accepted");
    final String query = "MATCH (s)-[srs:SYNONYM_OF*]->(x)-[:SYNONYM_OF]->(t:TAXON) " +
        "WITH srs, t UNWIND srs AS sr " +
        "RETURN DISTINCT sr, t";
    int counter = 0;
    LOG.info("{} synonym chains to a taxon resolved", counter);


    LOG.debug("Remove synonym chains missing any accepted");
    final String query2 = "MATCH (s)-[srs:SYNONYM_OF*]->(s2:SYNONYM) WHERE NOT (s2)-[:SYNONYM_OF]->() " +
        "WITH srs UNWIND srs AS sr " +
        "RETURN DISTINCT sr";
    AtomicInteger cnt = new AtomicInteger(0);
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
    //TODO: impl see old code Issue.SYNONYM_PARENT
    LOG.info("Cleanup relations, preferring synonym over parent relations");
    int parentOfRelDeleted = 0;
    int parentOfRelRelinked = 0;
    int childOfRelDeleted = 0;
    LOG.info("Synonym relations cleaned up. "
            + "{} hasParent relations deleted,"
            + "{} isParentOf relations deleted, {} isParentOf rels moved from synonym to accepted",
        childOfRelDeleted, parentOfRelDeleted, parentOfRelRelinked);
  }

  private void addNameIssue(NameData data, Issue issue) {
    store.addIssues(data.getName(), issue);
  }

  private void addUsageIssue(UsageData data, Issue issue) {
    store.addIssues(data.usage, issue);
  }

  private void insertData() throws NormalizationFailedException, InterruptedException {
    // closing the batch inserter opens the normalizer db again for regular access via the store
    try {
      DataInserter inserter;
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
          inserter = new TxtTreeInserter(store, sourceDir, dataset.getSettings(), refFactory);
          break;
        default:
          throw new NormalizationFailedException("Unsupported data format " + format);
      }
      // first metadata, the key will be preserved by the store
      inserter.readMetadata().ifPresent(d -> DatasetDao.patchMetadata(dataset, d.getDataset(), validator));
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