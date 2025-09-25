package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.dao.CopyUtil;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TypeMaterialMapper;
import life.catalogue.db.mapper.VernacularNameMapper;
import life.catalogue.interpreter.InterpreterUtils;
import life.catalogue.matching.*;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.printer.PrinterUtils;
import life.catalogue.release.UsageIdGen;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;
import static life.catalogue.common.text.StringUtils.lc;
import static life.catalogue.common.text.StringUtils.rmWS;

/**
 * Expects depth first traversal!
 */
public class TreeMergeHandler extends TreeBaseHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TreeMergeHandler.class);
  public static final char ID_PREFIX = '~';
  private static final Set<Rank> LOW_RANKS = Set.of(Rank.FAMILY, Rank.SUBFAMILY, Rank.TRIBE, Rank.GENUS);
  private final MatchedParentStack parents;
  private final UsageMatcher matcher;
  private final MatchingUtils utils;
  private final TaxGroupAnalyzer groupAnalyzer;
  private int counter = 0;  // all source usages
  private int ignored = 0;
  private int thrown = 0;
  private int created = 0;
  private int updated = 0; // updates
  private Throwable exception;
  private final @Nullable TreeMergeHandlerConfig cfg;
  private final DSID<Integer> vKey;
  private final Identifier.Scope nameIdScope;
  private final Identifier.Scope usageIdScope;
  SqlSessionFactory factory;

  TreeMergeHandler(int targetDatasetKey, int sourceDatasetKey, Map<String, EditorialDecision> decisions, SqlSessionFactory factory,
                   Function<SqlSession, UsageMatcher> matcherSupplier, NameIndex nameIndex,
                   int user, Sector sector, SectorImport state, @Nullable TreeMergeHandlerConfig cfg,
                   Supplier<String> nameIdGen, Supplier<String> typeMaterialIdGen, UsageIdGen usageIdGen) {
    // we use much smaller ids than UUID which are terribly long to iterate over the entire tree - which requires to build a path from all parent IDs
    // this causes postgres to use a lot of memory and creates very large temporary files
    super(targetDatasetKey, decisions, factory, nameIndex, user, sector, state, nameIdGen, typeMaterialIdGen, usageIdGen);
    this.factory=factory;
    this.cfg = cfg;
    this.vKey = DSID.root(sourceDatasetKey);
    this.matcher = matcherSupplier.apply(batchSession);
    groupAnalyzer = new TaxGroupAnalyzer();
    utils = new MatchingUtils(nameIndex);

    // figure out the lowest insertion point in the project/release
    // a) a target is given
    // b) a subject is given. Match it and see if it is lower and inside the target
    // c) nothing, but there maybe is an incertae sedis taxon configured to collect all unplaced
    SimpleNameClassified<SimpleNameCached> trgt = null;
    if (target != null) {
      var cl = target.getParentKey() == null ? null : num.getClassificationSN(target.getParentKey());
      trgt = utils.toSimpleNameClassified(target, cl);
    } else if (cfg != null && cfg.incertae != null) {
      trgt = utils.toSimpleNameClassified(cfg.incertae, null);
    }
    if (trgt != null) {
      // match target to tmp project identifiers, which usually differ from the project
      UsageMatch match = matcher.match(trgt);
      if (match.isMatch()) {
        trgt = match.usage;
      } else {
        LOG.warn("Sector target not found in tmp release project: {}", trgt);
        trgt = null;
      }
    }
    parents = new MatchedParentStack(trgt);
    if (sector.getSubject() != null) {
      // match subject and its classification
      try (SqlSession session = factory.openSession()) {
        var num = session.getMapper(NameUsageMapper.class);
        // loop over classification incl the subject itself as the last usage
        for (var p : num.getClassification(sector.getSubjectAsDSID())) {
          var nusn = utils.toSimpleNameCached(p);
          parents.push(nusn, null);
          var snc = utils.toSimpleNameClassified(p, parents.classificationSN());
          UsageMatch match = matcher.match(snc);
          if (match.isMatch()) {
            parents.setMatch(match.usage);
          }
        }
        var lowest = parents.lowestParentMatch();
        if (lowest != null && (trgt == null || !lowest.getId().equals(trgt.getId()))) {
          // found a lower target than we had before!
          LOG.info("The sector subject {} resulted in a lower target match to use for merging: {}", sector.getSubject(), lowest);
          parents.setRoot(lowest);
        }
      }
    }

    // check if requested entities are supported in the source at all
    try (SqlSession session = factory.openSession()) {
      if (entities.contains(EntityType.VERNACULAR)) {
        var mapper = session.getMapper(VernacularNameMapper.class);
        if (!mapper.entityExists(sourceDatasetKey)) {
          entities.remove(EntityType.VERNACULAR);
          LOG.info("No vernacular names in sector {}", sector);
        }
      }
      if (entities.contains(EntityType.TYPE_MATERIAL)) {
        var mapper = session.getMapper(TypeMaterialMapper.class);
        if (!mapper.entityExists(sourceDatasetKey)) {
          entities.remove(EntityType.TYPE_MATERIAL);
          LOG.info("No type material in sector {}", sector);
        }
      }
    }

    // add known identifiers?
    if (source.getType() == DatasetType.NOMENCLATURAL && source.getAlias() != null) {
      switch (source.getAlias().toUpperCase()) {
        case "ZOOBANK":
          nameIdScope = Identifier.Scope.ZOOBANK;
          break;
        case "IF":
        case "Index Fungorum":
          nameIdScope = Identifier.Scope.IF;
          break;
        case "INA":
          nameIdScope = Identifier.Scope.INA;
          break;
        case "IPNI":
          nameIdScope = Identifier.Scope.IPNI;
          break;
        default:
          nameIdScope = null;
      }
    } else {
      nameIdScope = null;
    }
    // known usage id scope?
    if (source.getType() == DatasetType.TAXONOMIC && source.getAlias() != null) {
      switch (source.getAlias().toUpperCase()) {
        case "WFO":
          usageIdScope = Identifier.Scope.WFO;
          break;
        case "ITIS":
          usageIdScope = Identifier.Scope.TSN;
          break;
        case "INAT":
          usageIdScope = Identifier.Scope.INAT;
          break;
        case "GBIF":
          usageIdScope = Identifier.Scope.GBIF;
          break;
        default:
          usageIdScope = null;
      }
    } else {
      usageIdScope = null;
    }
  }

  @Override
  protected List<EditorialDecision> findParentDecisions(String taxonID) {
    var ll = new LinkedList<>(parents.classification());
    var iter = ll.descendingIterator();
    while (iter.hasNext()) {
      var u = iter.next();
      if (u.usage.getId().equals(taxonID)) {
        break;
      }
      iter.remove();
    }
    return ll.stream()
      .filter(mu -> mu.decision != null)
      .map(mu -> mu.decision)
      .collect(Collectors.toList());
  }

  @Override
  public void reset() {
    // only needed for UNION sectors which do several iterations
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasThrown() {
    return thrown > 0;
  }

  @Override
  public Throwable lastException() {
    return exception;
  }

  @Override
  public Map<IgnoreReason, Integer> getIgnoredCounter() {
    return ignoredCounter;
  }

  @Override
  public int getDecisionCounter() {
    return decisionCounter;
  }

  @Override
  public void acceptThrows(NameUsageBase nu) throws InterruptedException {
    try {
      acceptThrowsNoCatch(nu);
    } catch (InterruptedException e) {
      throw e; // rethrow real interruptions

    } catch (RuntimeException e) {
      exception = e;
      LOG.error("Unable to process {} with parent {}. {}:{}", nu, nu.getParentId(), e.getClass().getSimpleName(), e.getMessage(), e);
      thrown++;

    } catch (Exception e) {
      exception = e;
      LOG.error("Fatal. Unable to process {} with parent {}. {}:{}", nu, nu.getParentId(), e.getClass().getSimpleName(), e.getMessage(), e);
      thrown++;
      throw new RuntimeException(e);
    }
  }

  public void acceptThrowsNoCatch(NameUsageBase nu) throws Exception {
    counter++;
    LOG.debug("process {} {} {} -> {}", nu.getStatus(), nu.getName().getRank(), nu.getLabel(), parents.classificationToString());

    // apply common changes to the usage
    var mod = processCommon(nu);

    // track parent classification and match to existing usages. Create new ones if they dont yet exist
    var nusn = utils.toSimpleNameCached(nu);
    // this also removes lower entries from the parents list from the last usage
    parents.push(nusn, decisions.get(nu.getId()));

    final boolean qualifiedName = nu.getName().hasAuthorship();

    // ignore doubtfully marked usages in classification, e-g- wrong rank ordering
    if (parents.isDoubtful()) {
      ignored++;
      LOG.info("Ignore {} {} [{}] because it has a bad parent classification {}", nu.getName().getRank(), nu.getName().getLabel(), nu.getId(), parents.getDoubtful().usage);
      return;
    }

    boolean unique = nu.getName().getRank().isSupraspecific() && cfg != null && cfg.xCfg.enforceUnique(nu.getName());
    boolean markMatch = false;

    // find out matching - even if we ignore the name in the merge we want the parents matched for classification comparisons
    // we have a custom usage loader registered that knows about the open batch session
    // that writes new usages to the release which might not be flushed to the database
    var snc = new SimpleNameClassified<SimpleNameCached>(nusn);
    snc.setClassification(parents.classificationSN(1));
    UsageMatch match = matcher.match(snc, true, unique);
    // remove matches to genera for unranked source names as genera often are homonyms with higher names and can cause serious trouble
    if (match.isMatch() && nu.getRank().otherOrUnranked() && match.usage.getRank().isGenusGroup()) {
      LOG.info("Ignore {} [{}] because it is unranked and matches a genus which can be a bad homonym match: {}", nu.getName().getLabel(), nu.getId(), match.usage.getLabel());
      match = UsageMatch.empty(targetDatasetKey);
    } else {
      LOG.debug("{} matches {}", nu.getLabel(), match);
    }
    if (!match.isMatch() && unique) {
      for (var alt : match.alternatives) {
        if (alt.getRank() == nu.getName().getRank() && alt.getName().equalsIgnoreCase(nu.getName().getScientificName())) {
          markMatch = true;
          match = UsageMatch.snap(alt, targetDatasetKey, null);
          LOG.debug("Enforce unique name and match {} to {}", nu.getLabel(), match);
          //TODO: enforce the use of this genus for all child species
        }
      }
    }
    if (!match.isMatch() && qualifiedName && nu.isTaxon() && nu.getRank().isSpeciesOrBelow()) {
      var snCanon = SimpleNameClassified.canonicalCopy(snc);
      match = matcher.match(snCanon, false, false);
      if (match.isMatch() && sameLowClassification(match.usage.getClassification(), parents.classification())) {
        // make sure the species is in the same genus or family
        LOG.debug("Accepted {} {} has canonical match within the same family subtree: {}", nu.getRank(), nu.getLabel(), match.usage);
        match = UsageMatch.ignore(match);
      }
    }

    // figure out closest matched parent that we can use to attach to
    Usage parent;
    if (nu.isSynonym()) {
      // make sure synonyms have a matched direct parent (second last, cause the last is the synonym itself)
      // parent can be null here, but we will skip synonyms that have no matched parent in ignoreUsage() below
      parent = usage(parents.secondLast().match);
    } else {
      // otherwise use the parent stacks lowest taxon or root, e.g. incertae sedis
      parent = usage(parents.lowestParentMatch());
    }

    // some sources contain the same name multiple times with different status. Good pro parte ones or bad ones...
    // we allow any number of synonyms as long as they have different parents
    // but only allow a single accepted name
    if (match.isMatch() && Objects.equals(sector.getId(), match.sectorKey) &&
        (match.usage.getStatus().isSynonym() || nu.getStatus().isSynonym())
    ) {
      // verify parents are different
      if (parent == null ||
          // different parents, but not the same as the match, we dont want synonyms that point to themselves as accepted
          (!parent.id.equals(match.usage.getParent()) && !parent.id.equals(match.usage.getId()))
      ) {
        LOG.debug("Ignore match to potential pro parte synonym complex from the same source: {}", match.usage.getLabel());
        match = UsageMatch.empty(targetDatasetKey);
        //TODO: reuse existing name instance for pro parte usages when they are created below
      }
    }

    // remember the match
    parents.setMatch(match.usage);
    if (markMatch) {
      parents.mark();
    }

    // check if usage should be ignored AFTER matching as we need the parents matched to attach child taxa correctly
    if (match.ignore || ignoreUsage(nu, decisions.get(nu.getId()), mod, true)) {
      // skip this taxon, but include children
      ignored++;
      return;
    }

    // finally create or update records
    SimpleNameWithNidx sn = null;
    if (match.isMatch()) {
      // *** UPDATE ***
      update(nu, match);
      sn = match.usage;
      mod.createOrthVarRel = false; // dont create new name relations for spelling corrections

    } else if (match.type == MatchType.AMBIGUOUS) {
      LOG.debug("Do not create new name as we had {} ambiguous matches for {}", match.alternatives.size(), nu.getLabel());

    } else {
      // *** CREATE ***
      if ( nu.isTaxon() && syncTaxa && !isAmbiguousGenus(nu) ||  nu.isSynonym() && syncSynonyms) {
        sn = create(nu, parent);
      }
    }

    processEnd(sn, mod);
  }

  /**
   * Detects unqualified genus usages without authorship
   * which are placed under no parents or Biota and similar parents which have no taxonomic group at all.
   *
   * These ambiguous genera often cause trouble as they match later on to pretty much anything alike
   * and also adapt (wrong) authorships.
   */
  private boolean isAmbiguousGenus(NameUsageBase nu) {
    if (nu.getRank() == Rank.GENUS) {
      var psn = parents.matchedParentsOnlySN();
      var group = groupAnalyzer.analyze(nu.toSimpleNameLink(), psn);
      if (group == null || group.equals(TaxGroup.Eukaryotes)) {
        LOG.info("Ignore canonical genus {} with vague parents: {}", nu.getLabel(), psn);
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean allowImplicitName(Usage parent, Taxon u) {
    return !isAmbiguousGenus(u);
  }

  public void acceptName(Name n) throws InterruptedException {
    try {
      acceptNameThrowsNoCatch(n);
    } catch (InterruptedException e) {
      throw e; // rethrow real interruptions

    } catch (RuntimeException e) {
      exception = e;
      LOG.error("Unable to process name {}. {}:{}", n, e.getClass().getSimpleName(), e.getMessage(), e);
      thrown++;

    } catch (Exception e) {
      exception = e;
      LOG.error("Unable to process name {}. {}:{}", n, e.getClass().getSimpleName(), e.getMessage(), e);
      thrown++;
      throw new RuntimeException(e);
    }
  }

  private void acceptNameThrowsNoCatch(Name n) throws InterruptedException {
    Set<InfoGroup> upd = EnumSet.noneOf(InfoGroup.class);

    if (n.getNamesIndexType() == null) {
      matchName(n);
    }
    if (n.getNamesIndexType() == MatchType.EXACT) {
      var candidates = nm.listByNidx(targetDatasetKey, n.getNamesIndexId());
      if (candidates.size() == 1) {
        Name existing = candidates.get(0);
        VerbatimSource vs = new VerbatimSource(targetDatasetKey, null, sector.getId(), sector.getSubjectDatasetKey(), n.getId(), EntityType.NAME);
        var pn = updateName(existing, n, vs, upd, null);

        if (!upd.isEmpty()) {
          updated++;
          // make sure name has a vs key
          var vskey = vsKey(pn);
          vsm.insertSources(vskey, n, upd);
          // update name
          nm.update(pn);
          // commit in batches
          if (updated % 1000 == 0) {
            interruptIfCancelled();
            session.commit();
            batchSession.commit();
          }
        }
      } else {
        LOG.debug("Cannot merge {} into {} matching names", n, candidates.size());
      }
    }
  }

  private SimpleNameWithNidx create(NameUsageBase nu, Usage parent) {
    // replace accepted taxa with doubtful ones for genus parents which are synonyms
    // provisionally accepted species & infraspecies will not create an implicit genus or species !!!
    if (nu.getStatus() == TaxonomicStatus.ACCEPTED && parent != null && parent.status.isSynonym() && parent.rank == Rank.GENUS) {
      nu.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    }
    if (parent != null && parent.status.isSynonym()) {
      // use accepted instead
      var p = numRO.getSimpleParent(targetKey.id(parent.id));
      // make sure rank hierarchy makes sense - can be distorted by synonyms
      if (p == null || (nu.isTaxon() && nu.getRank().notOtherOrUnranked() && p.getRank().lowerOrEqualsTo(nu.getRank()))) {
        while (p != null && p.getRank().lowerOrEqualsTo(nu.getRank())) {
          p = numRO.getSimpleParent(targetKey.id(p.getId()));
        }
        if (p == null) {
          // nothing to attach to. Better skip this taxon, but include children
          LOG.info("Ignore name which links to a synonym and for which we cannot find a suitable parent: {}", nu.getLabel());
          ignored++;
          return null;
        }
      }
      parent = usage(p);

    } else if (nu.isSynonym() && parent == null) {
      LOG.warn("Ignore synonym without a parent: {}", nu.getLabel());
      ignored++;
      return null;

    } else if (nu.isTaxon() && parent != null && parent.rank.notOtherOrUnranked() && nu.getRank().higherOrEqualsTo(parent.rank)) {
      LOG.info("Avoid bad rank ordering. Do not create {} {} with parent: {}", nu.getRank(), nu.getLabel(), parent.rank);
      ignored++;
      return null;
    }

    // add well known identifiers
    if (usageIdScope != null) {
      nu.addIdentifier(new Identifier(usageIdScope, nu.getId()));
    }
    if (nameIdScope != null) {
      nu.getName().addIdentifier(new Identifier(nameIdScope, nu.getName().getId()));
    }

    // only add a new name if we do not have already multiple names that we cannot clearly match
    // track if we are outside of the sector target
    Issue[] issues;
    if (target != null && parent != null
      && !Objects.equals(parent.id, target.getId())
      && !containsID(matcher.store().getClassification(parent.id), target.getId())
    ) {
      issues = new Issue[]{Issue.SYNC_OUTSIDE_TARGET};
    } else {
      issues = new Issue[0];
    }
    // *** CREATE ***
    var sn = super.create(nu, parent, issues);
    created++;
    parents.setMatch(sn);
    matcher.store().add(sn);
    return sn;
  }

  private static boolean sameLowClassification(List<SimpleNameCached> cl1, List<MatchedParentStack.MatchedUsage> cl2) {
    Set<String> names1 = cl1.stream()
      .filter(n -> LOW_RANKS.contains(n.getRank()))
      .map(n -> n.getName().toLowerCase())
      .collect(Collectors.toSet());
    return !names1.isEmpty() && cl2.stream()
      .filter(n -> LOW_RANKS.contains(n.usage.getRank()))
      .anyMatch(n -> names1.contains(n.usage.getName().toLowerCase()));
  }

  private static boolean containsID(List<SimpleNameCached> usages,  String id){
    return usages != null && usages.stream().anyMatch(u -> u.getId().equals(id));
  }

  @Override
  protected boolean ignoreUsage(NameUsageBase u, @Nullable EditorialDecision decision, IssueContainer issues, boolean filterSynonymsByRank) {
    var ignore =  super.ignoreUsage(u, decision, issues, true);
    if (!ignore) {
      // additional checks - we dont want any unranked unless they are OTU names
      ignore = u.getRank() == Rank.UNRANKED && u.getName().getType() != NameType.OTU
        || (cfg != null && cfg.isBlocked(u.getName()));
      // check the dynamically generated name validation issues without loading
      if (issues.contains(Issue.INCONSISTENT_NAME)) {
        LOG.debug("Ignore {} because it is an inconsistent name", u.getLabel());
        return true;
      }
      // if custom issues are to be excluded we need to load the verbatim records
      if (cfg != null && !cfg.xCfg.issueExclusion.isEmpty() && u.getName().getVerbatimKey() != null) {
        var issues2 = vrmRO.getIssues(vKey.id(u.getName().getVerbatimKey()));
        issues.add(issues2);
        if (issues != null && CollectionUtils.overlaps(issues.getIssues(), cfg.xCfg.issueExclusion)) {
          LOG.debug("Ignore {} because of excluded issues: {}", u.getLabel(), StringUtils.join(issues, ","));
          return true;
        }
      }
    }
    return ignore;
  }

  /**
   * Use the same usage matching to find existing taxa
   */
  @Override
  protected Usage findExisting(Name n, Usage parent) {
    var snc = utils.toSimpleNameClassified(new Taxon(n), parents.classificationSN());
    var m = matcher.match(snc, true, false);
    // make sure rank is correct - canonical matches are across ranks
    if (m.usage != null && m.usage.getRank() == n.getRank()) {
      return usage(m.usage);
    }
    return null;
  }

  @Override
  protected void cacheImplicit(Taxon t) {
    var snc = utils.toSimpleNameCached(t);
    matcher.store().add(snc);
  }

  private Name loadFromDB(String usageID) {
    batchSession.commit();
    return nm.getByUsage(targetDatasetKey, usageID);
  }

  private boolean proposedParentDoesNotConflict(SimpleName existing, SimpleName existingParent, SimpleName proposedParent) {
    // shortcut for special case of incertae sedis parent
    // regardless of its rank we should always update the classification!
    if (cfg != null && cfg.incertae != null && cfg.incertae.getId().equals(existingParent.getId())) {
      return true;
    }
    boolean existingParentFound = false;
    if (existingParent.getRank().higherThan(proposedParent.getRank())
        && proposedParent.getRank().higherThan(existing.getRank())
        && !existingParent.getId().equals(proposedParent.getId())
    ) {
      // now check the newly proposed classification does also contain the current parent to avoid changes - we only want to patch missing ranks
      // but also make sure the existing name is not part of the proposed classification as this will result in a fatal circular loop!
      var proposedClassification = matcher.store().getClassification(proposedParent.getId());
      for (var propHigherTaxon : proposedClassification) {
        if (propHigherTaxon.getId().equals(existing.getId())) {
          LOG.debug("Avoid circular classifications by updating the parent of {} {} to {} {}", existing.getRank(), existing.getLabel(), proposedParent.getRank(), proposedParent.getLabel());
          return false;
        }
        if (propHigherTaxon.getId().equals(existingParent.getId())) {
          existingParentFound = true;
        }
      }
    }
    return existingParentFound;
  }

  /**
   * Lazily persist a new verbatim source if the key is not existing yet
   */
  private int verbatimSourceKey(VerbatimSource v) {
    if (v.getId() == null) {
      // first persist to create the key
      v.setId(vsIdGen++);
      vsm.create(v);
    }
    return v.getId();
  }
  private void updateParent(NameUsageBase nu, DSID<String> existingUsageKey, UsageMatch existing, SimpleNameWithNidx existingParent, SimpleNameWithNidx parent, Set<InfoGroup> upd) {
    LOG.debug("Update {} with closer parent {} {} than {} from {}", existing.usage, parent.getRank(), parent.getId(), existingParent, nu);
    numRO.updateParentId(existingUsageKey, parent.getId(), user);
    matcher.store().updateParentId(existingUsageKey.getId(), parent.getId());
    upd.add(InfoGroup.PARENT);
  }

  private void update(NameUsageBase nu, UsageMatch existing) {
    if (nu.getStatus().getMajorStatus() == existing.usage.getStatus().getMajorStatus()) {
      LOG.debug("Update {} {} {} from source {}:{} with status {}", existing.usage.getStatus(), existing.usage.getRank(), existing.usage.getLabel(), sector.getSubjectDatasetKey(), nu.getId(), nu.getStatus());

      // we need to
      // 1) load the primary source and add secondary sources if we update anything
      // 2) create a new verbatim source for all newly added supplementary records like vernaculars, distributions, etc
      //    we do only create the VS record when it is actually needed, so we start with just the instance without persisted key:
      VerbatimSource vs = new VerbatimSource(targetDatasetKey, null, sector.getId(), sector.getSubjectDatasetKey(), nu.getId(), EntityType.NAME_USAGE);
      Set<InfoGroup> upd = EnumSet.noneOf(InfoGroup.class);

      // set targetKey to the existing usage
      final var existingUsageKey = DSID.of(targetDatasetKey, existing.usage.getId());
      if (existing.usage.getStatus().isTaxon()) {
        // patch classification of accepted names if direct parent adds to it
        if (syncTaxa) {
          var matchedParents = parents.matchedParentsOnly(existing.usage.getId());
          if (!matchedParents.isEmpty()) {
            var parent = matchedParents.getLast().match;
            if (parent != null) {
              if (parent.getStatus().isSynonym()) {
                LOG.info("Do not update {} with a closer synonym parent {} {} from {}", existing.usage, parent.getRank(), parent.getId(), nu);

              } else {
                var existingParent = existing.usage.getClassification() == null || existing.usage.getClassification().isEmpty() ? null : existing.usage.getClassification().get(0);
                var parent2 = matchedParents.size() < 2 ? null : matchedParents.get(matchedParents.size()-2).match;
                batchSession.commit(); // we need to flush the write session to avoid broken foreign key constraints
                if (existingParent == null || proposedParentDoesNotConflict(existing.usage, existingParent, parent)) {
                  updateParent(nu, existingUsageKey, existing, existingParent, parent, upd);
                } else if (parent.getRank() == Rank.SERIES && parent2 != null && proposedParentDoesNotConflict(existing.usage, existingParent, parent2)) {
                  // series in zoology are placed differently than in botany which we consider as series
                  // See https://github.com/CatalogueOfLife/data/issues/1023
                  updateParent(nu, existingUsageKey, existing, existingParent, parent2, upd);
                }
              }
            }
          }
        }
        // vernacular names
        if (entities.contains(EntityType.VERNACULAR)) {
          final var mapper = batchSession.getMapper(VernacularNameMapper.class);
          List<VernacularName> existingVNames = null;
          vnloop:
          for (var vn : mapper.listByTaxon(nu)) {
            // we only want to add vernaculars with a name & language
            if (vn.getName() == null || vn.getLanguage() == null) continue;

            // ignore if they have pipes, semicolon or commas as these are nearly always badly concatenated values
            if (InterpreterUtils.unlikelyVernacular(vn.getName())) continue;

            // does it exist already?
            if (existingVNames == null) {
              // lazily query existing vnames
              existingVNames = mapper.listByTaxon(existing);
            }
            for (var evn : existingVNames) {
              if (sameVName(vn, evn)) {
                continue vnloop;
              }
            }
            // a new vernacular
            vn.setId(null);
            vn.setVerbatimSourceKey(verbatimSourceKey(vs));
            vn.setSectorKey(sector.getId());
            vn.setDatasetKey(targetDatasetKey);
            vn.applyUser(user);
            // check if the entity refers to a reference which we need to lookup / copy
            String ridCopy = lookupOrCreateReference(vn.getReferenceId());
            vn.setReferenceId(ridCopy);
            CopyUtil.transliterateVernacularName(vn, IssueContainer.VOID);
            mapper.create(vn, existingUsageKey.getId());
            existingVNames.add(vn);
          }
        }
      }

      // try to also update the name - conditional checks within the subroutine
      // this can change the existing usage ID if the authorship changes !!!
      Name pn = updateName(null, nu.getName(), vs, upd, existing);

      if (!upd.isEmpty()) {
        this.updated++;
        // update name & usage vsKey
        // both name and usage can have a key to a verbatim source. Ideally they are the same
        Integer uvsKey = vsm.getVSKeyByUsage(existing);
        DSID<Integer> vsKey;
        if (uvsKey != null) {
          vsKey = DSID.of(targetDatasetKey, uvsKey);
        } else if (pn.getVerbatimSourceKey() != null) {
          vsKey = DSID.of(targetDatasetKey, pn.getVerbatimSourceKey());
        } else {
          vsKey = createSecondaryVS();
        }
        vsm.insertSources(vsKey, nu, upd);
        if (pn.getVerbatimSourceKey() == null) {
          pn.setVerbatimSourceKey(vsKey.getId());
        }
        nm.update(pn);
        if (uvsKey == null) {
          num.updateVerbatimSourceKey(existingUsageKey, vsKey.getId());
        }
      }

    } else {
      LOG.debug("Ignore update of {} {} {} from source {}:{} with different status {}", existing.usage.getStatus(), existing.usage.getRank(), existing.usage.getLabel(), sector.getSubjectDatasetKey(), nu.getId(), nu.getStatus());
    }

    // add well know name and usage ids
    if (usageIdScope != null) {
      num.addIdentifier(existing, List.of(new Identifier(usageIdScope, nu.getId())));
    }
  }

  /**
   * Creates a new verbatim source record to hold issues and secondary sources.
   * But do not link to a primary source, i.e. not populate sector, sourceId & source dataset
   */
  private VerbatimSource createSecondaryVS() {
    var vs = new VerbatimSource(targetDatasetKey, vsIdGen++, null, null, null, null);
    vsm.create(vs);
    return vs;
  }

  /**
   * Use the verbatim source key from a name instance or create a new emtpy VS record with no source link.
   * The name.verbatimSourceKey property is set, but not persisted yet!
   * @return the VS key
   */
  private DSID<Integer> vsKey(Name n) {
    // we can see usages and names that were manually created and have not been synced, this do not have a verbatim source!
    // So we need to create missing ones in such cases...
    if (n.getVerbatimSourceKey() != null) {
      return DSID.of(targetDatasetKey, n.getVerbatimSourceKey());
    }
    var vs = createSecondaryVS();
    n.setVerbatimSourceKey(vs.getId());
    return vs;
  }

  /**
   * Either the Name n or the existing usage must be given!
   */
  private Name lazilyLoad(@Nullable Name n, @Nullable UsageMatch existingUsage) {
    return n != null ? n : loadFromDB(existingUsage.usage.getId());
  }

  /**
   * Updates an existing name.
   * Either the Name n or the existing usage must be given!
   * If a new authorship is added the key of the existingUsage will change to a new temp id !
   *
   * @param n name to be updated
   * @param src source for updates
   * @param vs verbatim source for the source record, but might not have been persisted yet with a key. Will only be used for newly created records like type material!
   * @param upd set of info groups that have been updated from this verbatim source. Will be persisted in the calling method.
   * @param existingUsage usage match instance corresponding to Name n - only used to update the matcher cache to be in sync with the name.
   *                 Not needed for bare name merging
   * @return
   */
  private Name updateName(@Nullable Name n, Name src, VerbatimSource vs, Set<InfoGroup> upd, @Nullable UsageMatch existingUsage) {
    if (n == null && existingUsage == null) return null;

    if (syncNames) {
      n = lazilyLoad(n, existingUsage);
      final int updSizeStart = upd.size();
      if (src.hasParsedAuthorship() && !n.hasAuthorship()) {
        upd.add(InfoGroup.AUTHORSHIP);
        n.setCombinationAuthorship(src.getCombinationAuthorship());
        n.setSanctioningAuthor(src.getSanctioningAuthor());
        n.setBasionymAuthorship(src.getBasionymAuthorship());
        n.rebuildAuthorship();
        if (existingUsage != null) {
          existingUsage.usage.setAuthorship(n.getAuthorship());
        }
        LOG.debug("Updated {} with authorship {}", n.getScientificName(), n.getAuthorship());
      }
      if (!src.getRank().isUncomparable() && n.getRank().isUncomparable()
        && RankComparator.compareVagueRanks(n.getRank(), src.getRank()) != Equality.DIFFERENT
      ) {
        upd.add(InfoGroup.RANK);
        n.setRank(src.getRank());
        if (existingUsage != null) {
          existingUsage.usage.setRank(n.getRank());
        }
        LOG.debug("Updated {} with rank {}", n.getScientificName(), n.getRank());
      }
      // also update the original match as we cache and reuse that
      if (upd.size() != updSizeStart) {
        if (!Objects.equals(src.getNamesIndexId(), n.getNamesIndexId())) {
          final var canonicalNidx = nameIndex.getCanonical(src.getNamesIndexId());
          n.setNamesIndexId(src.getNamesIndexId());
          if (existingUsage != null) {
            existingUsage.usage.setNamesIndexId(src.getNamesIndexId());
            existingUsage.usage.setNamesIndexMatchType(src.getNamesIndexType());
            if (!Objects.equals(existingUsage.usage.getCanonicalId(), canonicalNidx)) {
              LOG.warn("Updated name {} changed it's canonical nidx: {} -> {}", n.getLabel(), existingUsage.usage.getCanonicalId(), canonicalNidx);
              existingUsage.usage.setCanonicalId(canonicalNidx);
            }
            // also update the usage identifier for changes in authorship !!!
            // https://github.com/CatalogueOfLife/backend/issues/1407
            // assign new id based on the new nidx
            final var oldID = existingUsage.usage.getId();
            final var newID = usageIdGen.issue(existingUsage.usage);
            existingUsage.usage.setId(newID);
            TaxonDao.changeUsageID(DSID.of(targetDatasetKey, oldID), newID, existingUsage.usage.isSynonym(), user, batchSession);
            matcher.store().updateUsageID(oldID, newID);
            // update name match in db
            nmm.update(n, src.getNamesIndexId(), src.getNamesIndexType());
          }
        }
        // keep matcher storage in sync
        if (existingUsage != null) {
          matcher.store().add(existingUsage.usage);
        }
      }
      if (n.getPublishedInId() == null && src.getPublishedInId() != null) {
        setPubInRef(n, src, upd);
      }
    }

    // now try to update the reference itself if it existed already
    if (syncReferences && !upd.contains(InfoGroup.PUBLISHED_IN) && src.getPublishedInId() != null) {
      n = lazilyLoad(n, existingUsage);
      if (n.getPublishedInId() == null) {
        // just add a reference
        setPubInRef(n, src, upd);
      } else {
        // TODO: merge reference. Update reference links & DOI

      }
    }

    // type material
    if (entities.contains(EntityType.TYPE_MATERIAL)) {
      n = lazilyLoad(n, existingUsage);
      final var mapper = batchSession.getMapper(TypeMaterialMapper.class);
      List<TypeMaterial> existingTMs = null;
      tmloop:
      for (var tm : mapper.listByName(src)) {
        // we only want to add type material with important states
        if (tm.getStatus() == null || !tm.getStatus().isPrimary()) {
          continue;
        }
        // does it exist already?
        // lazily query existing material
        if (existingTMs == null) {
          existingTMs = mapper.listByName(n);
        }
        for (var etm : existingTMs) {
          if (sameType(tm, etm)) {
            continue tmloop;
          }
        }
        // a new type
        tm.setNameId(n.getId());
        tm.setVerbatimSourceKey(verbatimSourceKey(vs));
        tm.setSectorKey(sector.getId());
        tm.setDatasetKey(targetDatasetKey);
        tm.applyUser(user);
        // check if the entity refers to a reference which we need to lookup / copy
        String ridCopy = lookupOrCreateReference(tm.getReferenceId());
        tm.setReferenceId(ridCopy);
        try {
          mapper.create(tm);
        } catch (Exception e) {
          // ID might be used already - skip or try with no id instead?
          tm.setId(null);
          mapper.create(tm);
        }
        existingTMs.add(tm);
      }
    }

    // basionym / name relations
    if (entities.contains(EntityType.NAME_RELATION)) {
      // TODO: implement basionym/name rel updates
    }
    // well known name identifier
    if (existingUsage != null && nameIdScope != null) {
      var nid = DSID.of(existingUsage.getDatasetKey(), nm.getNameIdByUsage(existingUsage.getDatasetKey(), existingUsage.getId()));
      nm.addIdentifier(nid, List.of(new Identifier(nameIdScope, src.getId())));
    }
    return n;
  }

  private void setPubInRef(Name n, Name src, Set<InfoGroup> upd) {
    Reference ref = rm.get(DSID.of(src.getDatasetKey(), src.getPublishedInId()));
    n.setPublishedInId(lookupOrCreateReference(ref));
    n.setPublishedInPage(src.getPublishedInPage());
    n.setPublishedInPageLink(src.getPublishedInPageLink());
    upd.add(InfoGroup.PUBLISHED_IN);
    LOG.debug("Updated {} with publishedIn", n);
  }

  /**
   * @param vn1 required to have a name & language!
   */
  private static boolean sameVName(VernacularName vn1, VernacularName vn2) {
    return Objects.equals( lc(rmWS(vn1.getName())), lc(rmWS(vn2.getName())) ) ||
      ( vn1.getLatin() != null && lc(rmWS(vn1.getLatin())).equalsIgnoreCase(lc(rmWS(vn2.getLatin()))) );
  }

  private static boolean sameType(TypeMaterial mt1, TypeMaterial tm2) {
    return (
        rmWS(mt1.getId()).equalsIgnoreCase(rmWS(tm2.getId()))
      ) || (
        // only keep one holo
        mt1.getStatus() == TypeStatus.HOLOTYPE && tm2.getStatus() == TypeStatus.HOLOTYPE
      ) || (
        // only keep one lectotype
        mt1.getStatus() == TypeStatus.LECTOTYPE && tm2.getStatus() == TypeStatus.LECTOTYPE
      ) || (
        Objects.equals( rmWS(mt1.getInstitutionCode()), rmWS(tm2.getInstitutionCode()) ) &&
        Objects.equals( rmWS(mt1.getCatalogNumber()), rmWS(tm2.getCatalogNumber()) )
      );
  }

  /**
   * Copies all name and taxon relations based on ids collected during the accept calls by the tree traversal.
   */
  @Override
  public void copyRelations() {
    // TODO: copy name & taxon relations
    // implicit relations last, so we can check if we have duplicates
    super.copyRelations();
  }

  @Override
  public void close() {
    session.commit();
    session.close();
    batchSession.commit();
    batchSession.close();
    LOG.info("Sector {}: Total processed={}, thrown={}, ignored={}, created={}, updated={}", sector, counter, thrown, ignored, created, updated);
  }

  public int getUpdated() {
    return updated;
  }
}
