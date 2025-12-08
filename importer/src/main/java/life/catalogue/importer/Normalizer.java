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
import life.catalogue.dao.ParentStack;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.importer.acef.AcefInserter;
import life.catalogue.importer.coldp.ColdpInserter;
import life.catalogue.importer.dwca.DwcaInserter;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.NotUniqueRuntimeException;
import life.catalogue.importer.store.TreeWalker;
import life.catalogue.importer.store.model.NameData;
import life.catalogue.importer.store.model.NameUsageData;
import life.catalogue.importer.store.model.RelationData;
import life.catalogue.importer.store.model.UsageData;
import life.catalogue.importer.txttree.TxtTreeInserter;
import life.catalogue.interpreter.ExtinctName;
import life.catalogue.interpreter.RanKnName;
import life.catalogue.matching.NameValidator;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.metadata.DoiResolver;
import life.catalogue.parser.NameParser;

import life.catalogue.release.TreeCleanerAndValidator;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
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
   * but also applies missing dataset defaults.
   *
   * Further name and taxonomy validation is done in PgImport when walking the tree for inserts,
   * reusing the TreeCleanerAndValidator logic.
   */
  private void validateAndDefaults() throws InterruptedException {
    final NomCode defaultCode = dataset.getCode();
    final Set<Environment> defaultEnvironment = dataset.getEnvironment() == null ? null : Set.of(dataset.getEnvironment());

    LOG.info("Require mandatory name fields");
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

    LOG.info("Validate name usages");
    TreeWalker.walkTree(store, new TreeWalker.StartEndHandler() {
      final ParentStack<TreeCleanerAndValidator.XLinneanNameUsage> parents = new ParentStack();

      @Override
      public void start(NameUsageData nu, TreeWalker.WalkerContext ctxt) {
        TreeCleanerAndValidator.XLinneanNameUsage lnu = new TreeCleanerAndValidator.XLinneanNameUsage(nu.toNameUsageBase());
        var issues = IssueContainer.simple();
        var basionyms = nu.nd.getRelations(NomRelType.BASIONYM).stream()
          .map(nr -> store.names().objByID(nr.getToID()).getName())
          .collect(Collectors.toList());
        TreeCleanerAndValidator.validateAndPush(lnu, parents, basionyms, issues);
        if (issues.hasIssues()) {
          // all usages should have a verbatim record by now - even implicit ones!
          VerbatimRecord v = store.getVerbatim(nu.ud.getVerbatimKey());
          v.add(issues);
          store.put(v);
        }
      }

      @Override
      public void end(NameUsageData data, TreeWalker.WalkerContext ctxt) {}
    });

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
    insertBasionymRelations();
    // deduplicate name relations
    reduceRedundantNameRels();

    // cleanup synonym & parent relations
    resolveSynonymChains();
    resolveSynonymParents();
    cutParentCycles();

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


  /**
   * Use the basionymID from the name instances to setup valid name relations or flag invalid ids
   */
  private void insertBasionymRelations() {
    store.names().all()
      .filter(n->n.basionymID != null)
      .forEach(n -> {
        if (store.names().exists(n.basionymID)) {
          var rel = new RelationData<NomRelType>();
          rel.setType(NomRelType.BASIONYM);
          rel.setVerbatimKey(n.getVerbatimKey());
          rel.setFromID(n.getId());
          rel.setToID(n.basionymID);
          n.relations.add(rel);
          n.basionymID = null;
          store.names().update(n);
        } else {
          store.addIssues(n, Issue.BASIONYM_ID_INVALID);
        }
      });
  }

  private void validateRelations() {
    LOG.info("Validate parent relations");
    store.usages().all().forEach(u -> {
      if (u.usage.getParentId() != null) {
        var p = store.usages().objByID(u.usage.getParentId());
        if (p == null) {
          LOG.debug("ParentID {} of usage {} not existing", u.usage.getParentId(), u.usage.getId());
          u.usage.asUsageBase().setParentId(null);
          store.usages().update(u);
          if (u.isSynonym()) {
            store.addIssues(u.usage, Issue.ACCEPTED_ID_INVALID);
          } else {
            store.addIssues(u.usage, Issue.PARENT_ID_INVALID);
          }
        }
      }
    });
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
      var changed = false;
      while (iter.hasNext()) {
        var r = iter.next();
        if (!rels.add(new UniqueNameRel(r.getType(), r.getFromID(), r.getToID()))) {
          // redundant - remove it
          iter.remove();
          changed = true;
          counter.inc(r.getType());
        }
      }
      if (changed) {
        store.names().update(n);
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
    store.usages().allSynonyms().forEach(syn -> {
      if (syn.usage.getParentId() == null || !store.usages().exists(syn.usage.getParentId())) {
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
        boolean ambiguous = n.usageIDs.size()>1;
        boolean misapplied = MisappliedNameMatcher.isMisappliedName(n.pnu);
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
    store.usages().all().forEach(u -> {
      if (u.isSynonym()) {
        if (u.hasSupplementaryInfos()) {
          boolean hasAccepted = false;
          for (var acc : store.usages().accepted(u)) {
            acc.distributions.addAll(u.distributions);
            acc.media.addAll(u.media);
            acc.vernacularNames.addAll(u.vernacularNames);
            acc.estimates.addAll(u.estimates);
            acc.properties.addAll(u.properties);
            store.usages().update(acc);
            hasAccepted=true;
          }

          u.distributions.clear();
          u.media.clear();
          u.vernacularNames.clear();
          u.estimates.clear();
          u.properties.clear();
          store.addIssues(u.usage, Issue.SYNONYM_DATA_MOVED);
          store.usages().update(u);
          if (hasAccepted) {
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
   * This effectively also prevents any cycles that could be fatal.
   */
  private void cutBasionymChains() {
    LOG.info("Cut basionym chains");
    AtomicInteger counter = new AtomicInteger();
    // we iterate over the keys as we update objects in the map which would corrupt the stream and result in duplicates being offered
    Set<String> visited = new HashSet<>();
    store.names().allKeys().sorted().forEach(key -> {
      var n = store.names().objByID(key);
      var rels = n.getRelations(NomRelType.BASIONYM);
      if (!rels.isEmpty()) {
        visited.add(key);
        if (rels.size()>1) {
          store.addIssues(n, Issue.MULTIPLE_BASIONYMS);
        }
        for (var br1 : rels) {
          var b = store.names().objByID(br1.getToID());
          var rels2 = b.getRelations(NomRelType.BASIONYM);
          if (!rels2.isEmpty()) {
            if (rels2.size()>1) {
              store.addIssues(b, Issue.CHAINED_BASIONYM, Issue.MULTIPLE_BASIONYMS);
            } else {
              store.addIssues(b, Issue.CHAINED_BASIONYM);
            }
            // chain, remove all and relink original relation to the first outgoing name
            boolean updated = false;
            var iter = b.relations.iterator();
            while (iter.hasNext()) {
              var br2 = iter.next();
              if (br2.getType() == NomRelType.BASIONYM) {
                // if we've been to an id before we would start a new chain
                if (!updated && !visited.contains(br2.getToID())) {
                  br1.setToID(br2.getToID());
                  store.names().update(n);
                  updated = true;
                }
                iter.remove();
              }
            }
            store.names().update(b);
            counter.incrementAndGet();
          }
        }
      }
    });
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
  private void applyDenormedClassification() throws InterruptedException {
    if (!meta.isDenormedClassificationMapped()) {
      LOG.info("No higher classification mapped");
      return;
    }

    LOG.info("Start processing higher denormalized classification ...");
    store.usages().allKeys().forEach(key -> {
      var u = store.nameUsage(key);
      if (u.ud.isTaxon()) {
        // the highest current parent of n
        var highest = findHighestParent(u);
        // only need to apply classification if highest exists and is not already a superdomain, the denormed classification cannot add to it anymore!
        if (highest != null && highest.nd.getRank() != Rank.SUPERDOMAIN) {
          if (u.ud.classification != null) {
            applyClassification(highest, u.ud.classification);
          }
        }
      }
    });
  }

  private NameUsageData findHighestParent(NameUsageData nu) {
    // the highest current parent of n
    NameUsageData highest = null;
    if (meta.isParentNameMapped()) {
      // verify if we already have a classification, that it ends with a known rank
      var hu = CollectionUtils.lastOrNull(store.usages().parents(nu.ud));
      if (hu != null) {
        highest = store.nameUsage(hu);
        if(!hu.getId().equals(nu.ud.getId()) && !highest.nd.getRank().notOtherOrUnranked()) {
          LOG.debug("Usage {} already has a classification which ends in an uncomparable rank.", nu.ud.getId());
          addUsageIssue(nu.ud, Issue.CLASSIFICATION_NOT_APPLIED);
          return null;
        }
      }
    }
    if (highest == null) {
      // otherwise use this node
      highest = nu;
    }
    return highest;
  }

  /**
   * Applies the classification lc to the given RankedUsage taxon
   * @param taxon
   * @param clOrig
   */
  private void applyClassification(NameUsageData taxon, Classification clOrig) {
    // first modify classification to only keep those ranks we want to apply!
    // exclude lowest rank from classification to be applied if this taxon is rankless and has the same name
    var n = taxon.nd.getName();
    var cl = new Classification(clOrig); // we modify the instance, so work on a copy to not persist these changes
    if (n.getRank() == null || n.getRank().isUncomparable()) {
      Rank lowest = cl.getLowestExistingRank();
      if (lowest != null && cl.getByRank(lowest).equalsIgnoreCase(n.getScientificName())) {
        cl.setByRank(lowest, null);
        // apply the classification rank to unranked taxon and reload immutable taxon instance
        updateRank(taxon.nd, lowest);
      }
    }
    // ignore same rank from classification if accepted
    if (!taxon.ud.isSynonym() && n.getRank() != null) {
      cl.setByRank(n.getRank(), null);
    }
    // ignore genus and below for synonyms
    // http://dev.gbif.org/issues/browse/POR-2992
    if (taxon.ud.isSynonym()) {
      cl.setGenus(null);
      cl.setSubgenus(null);
      cl.setSection(null);
      cl.setSpecies(null);
    }

    // now reconstruct the given classification with the parentID field
    // reusing existing taxa if possible, otherwise creating new ones
    // and at the very end apply that classification to the taxon
    String parentID = null;
    Rank parentRank = parentID == null ? null : store.name(store.usages().objByID(parentID)).getRank();
    // from kingdom to subgenus
    for (final Rank hr : Classification.RANKS) {
      if ((n.getRank() == null || !n.getRank().higherThan(hr)) && cl.getByRank(hr) != null) {
        // test for existing usage with that name & rank (allowing also unranked names)
        boolean found = false;
        // we need to lookup the name by its normed form as we create them via createHigherTaxon
        // to be safe we query for both versions
        var rnn = new RanKnName(hr, cl.getByRankCleaned(hr));
        final ExtinctName normedName = parseCache.get(rnn);
        for (String uid : store.usageIDsByName(normedName.pname == null ? cl.getByRankCleaned(hr) : normedName.pname.getScientificName(), null, hr, true)) {
          var u = store.usages().objByID(uid);
          // ignore synonyms
          if (u.isSynonym()) continue;
          if (parentID == null) {
            // make sure found usage does also not have any linnean rank as parent
            if (nextLinneanRankOfParents(u) == null) {
              found = true;
            }

          } else {
            // verify the parents for the next higher rank are the same
            // we dont want to apply a contradicting classification with the same name
            var up = store.usages().objByID(u.usage.getParentId());
            var upAtRnk = store.usages().parent(u, parentRank);
            if ((up != null && up.getId().equals(parentID)) || (upAtRnk != null && upAtRnk.getId().equals(parentID) && mappedRanksInBetween(up, upAtRnk).isEmpty())) {
              found = true;
            } else if (up == null) {
              // if the matched usage has not yet been denormalized we need to compare the classification props
              if (u.classification != null && u.classification.equalsAboveRank(cl, hr)) {
                found = true;
              }
            }
          }

          if (found) {
            parentID = u.getId();
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
          var lowerParent = createHigherTaxon(normedName, hr, parentID);
          parentID = lowerParent.getId();
          parentRank = hr;
        }
      }
    }
    // finally apply lowest parent to initial node
    if (parentID != null) {
      store.usages().assignParent(taxon.ud, parentID);
    }
  }

  private Rank nextLinneanRankOfParents(UsageData u) {
    u = store.usages().parent(u);
    while (u != null && u.usage.getParentId() != null) {
      var nd = store.names().objByID(u.nameID);
      if (nd.getRank().isLinnean()) {
        return nd.getRank();
      }
      u = store.usages().parent(u);
    }
    return null;
  }

  private Set<Rank> mappedRanksInBetween(UsageData u1, UsageData u2){
    return store.usages().parentsUntil(u1, u2.getId()).stream()
        .map(ru -> store.name(ru).getRank())
        .filter(r -> meta.getDenormedRanksMapped().contains(r))
        .collect(Collectors.toSet());
  }

  private void updateRank(NameData name, Rank r) {
    name.getName().setRank(r);
    store.names().update(name);
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
  private UsageData createHigherTaxon(ExtinctName eName, Rank rank, String parentID) {
    UsageData ud = UsageData.buildTaxon(Origin.DENORMED_CLASSIFICATION, TaxonomicStatus.ACCEPTED);
    Taxon t = ud.asTaxon();
    eName.pname.setId(null); // we don't want to reuse the name id
    t.setParentId(parentID);
    if (eName.extinct || isExtinctBySetting(rank)) {
      t.setExtinct(true);
    }
    if (dataset.getEnvironment() != null) {
      t.setEnvironments(Set.of(dataset.getEnvironment()));
    }
    var nu = new NameUsageData(new NameData(eName.pname), ud);
    store.createNameAndUsage(nu);
    return ud;
  }

  /**
   * Sanitizes synonym relations by relinking synonym of synonyms to make sure synonyms always point to a direct accepted taxon.
   * Synonyms without an accepted parent will be flagged and removed at the very end by the removeOrphanSynonyms routine.
   */
  private void resolveSynonymChains() {
    final AtomicInteger synChains = new AtomicInteger();
    store.usages().allSynonyms().forEach(syn -> {
      if (syn.usage.getParentId() != null) {
        var p = store.usages().parent(syn);
        if (p.isSynonym()) {
          synChains.incrementAndGet();
          var chain = new ArrayList<UsageData>();
          chain.add(syn);
          while (p != null && p.isSynonym() && listContainsID(chain, p.getId())) {
            chain.add(p);
            p = store.usages().parent(p);
          }
          var accID = p == null || p.isSynonym() ? null : p.getId();
          for (var s : chain) {
            addUsageIssue(s, Issue.CHAINED_SYNONYM);
            store.usages().assignParent(syn, accID);
          }
        } else if (Objects.equals(p.nameID, syn.nameID)){
          // the accepted name of the synonym is identical
          // in this case remove the superfluous synonym: https://github.com/CatalogueOfLife/backend/issues/307
          addUsageIssue(syn, Issue.DUPLICATE_NAME); //TODO: create specific new issue?
          store.usages().remove(syn.getId());
        }
      }
    });
    LOG.info("Resolved {} chained synonyms", synChains.get());
  }

  private boolean listContainsID(List<UsageData> list, final String id) {
    return list.stream().noneMatch(u -> Objects.equals(u.getId(), id));
  }

  /**
   * Find accepted names that point to synonyms are their hierarchy
   * and resolves them by assigning the synonyms accepted name as the parent instead.
   */
  private void resolveSynonymParents() {
    LOG.info("Cleanup taxa with synonym parents");
    AtomicInteger cntSynParent = new AtomicInteger();
    store.usages().allTaxa().forEach(ud -> {
      if (ud.usage.getParentId() != null) {
        var p = store.usages().objByID(ud.usage.getParentId());
        if (p.isSynonym()) {
          addUsageIssue(ud, Issue.SYNONYM_PARENT);
          String newParentID = p.usage.getParentId(); // synonyms are clean by now, this must be an accepted name usage
          if (ud.getId().equals(newParentID)) { // but it can be itself, avoid selfloops
            newParentID = null;
            LOG.debug("No new parent found for taxon {} with synonym parent {}", ud.getId(), p.getId());
          }
          store.usages().assignParent(ud, newParentID);
          cntSynParent.incrementAndGet();
        }
      }
    });
    LOG.info("Resolved {} taxa with synonym parents", cntSynParent);
  }

  /**
   * Finds parent cycles and cuts them at the lowest possible rank.
   */
  private void cutParentCycles() {
    // brute force for taxa - all synonyms should point to some accepted now
    LOG.info("Cleanup parent cycles");
    AtomicInteger counter = new AtomicInteger();
    Set<String> visited = new HashSet<>();
    store.usages().allKeys().forEach(key -> {
      var ud = store.usages().objByID(key);
      if (ud.isTaxon()) {
        if (ud.usage.getParentId() != null) {
          if (ud.usage.getParentId().equals(ud.getId())) {
            store.usages().assignParent(ud, null);
            store.addIssues(ud, Issue.PARENT_CYCLE);
            counter.incrementAndGet();
          } else {
            List<UsageData> cycle = findParentCycle(ud, visited);
            if (cycle != null) {
              // find highest rank to cut
              NameUsageData max = null;
              for (var u : cycle) {
                NameUsageData nu = store.nameUsage(u);
                if (max == null || nu.nd.getRank().higherThan(max.nd.getRank())) {
                  max = nu;
                }
              }
              counter.incrementAndGet();
              store.usages().assignParent(max.ud, null);
              store.addIssues(max.ud, Issue.PARENT_CYCLE);
            }
          }
        }
        visited.add(ud.getId());
      }
    });
    LOG.info("{} parent cycles resolved", counter);
  }

  private List<UsageData> findParentCycle(UsageData ud, Set<String> checked) {
    if (checked.contains(ud.getId()) || (ud.usage.getParentId() != null && checked.contains(ud.usage.getParentId()))) {
      return null;
    }
    // self loop?
    if (ud.usage.getId().equals(ud.usage.getParentId())) {
      return List.of(ud);
    }
    Set<String> visited = new HashSet<>();
    visited.add(ud.getId());

    List<UsageData> cycle = new ArrayList<>();
    cycle.add(ud);
    var p = store.usages().objByID(ud.usage.getParentId());
    while (p != null) {
      cycle.add(p);
      // did we check that usage already before? No need to do it again
      if (checked.contains(p.getId())) {
        break;
      }
      checked.add(p.getId());
      // did we see the id in this cycle detection before?
      if (visited.contains(p.getId())) {
        // figure out the smallest cycle to return
        int idx = 0;
        while (idx < cycle.size()) {
          if (cycle.get(idx).getId().equals(p.getId())) {
            return cycle.subList(idx, cycle.size());
          }
          idx++;
        }
        throw new IllegalStateException("We must have seen the id in the cycle list!");
      }
      visited.add(p.getId());
      p = store.usages().objByID(p.usage.getParentId());
    }
    return null;
  }

  private void addNameIssue(NameData data, Issue... issue) {
    store.addIssues(data.getName(), issue);
  }

  private void addUsageIssue(UsageData data, Issue... issue) {
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