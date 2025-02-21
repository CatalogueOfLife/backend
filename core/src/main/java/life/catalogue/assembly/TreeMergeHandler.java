package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.cache.CacheLoader;
import life.catalogue.cache.UsageCache;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.dao.CopyUtil;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TypeMaterialMapper;
import life.catalogue.db.mapper.VernacularNameMapper;
import life.catalogue.matching.*;
import life.catalogue.matching.nidx.NameIndex;

import life.catalogue.release.UsageIdGen;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;
import static life.catalogue.common.text.StringUtils.rmWS;
/**
 * Expects depth first traversal!
 */
public class TreeMergeHandler extends TreeBaseHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TreeMergeHandler.class);
  public static final char ID_PREFIX = '~';
  private static final Set<Rank> LOW_RANKS = Set.of(Rank.FAMILY, Rank.SUBFAMILY, Rank.TRIBE, Rank.GENUS);
  private final MatchedParentStack parents;
  private final UsageMatcherGlobal matcher;
  private final TaxGroupAnalyzer groupAnalyzer;
  private final UsageCache uCache;
  private final CacheLoader loader;
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

  TreeMergeHandler(int targetDatasetKey, int sourceDatasetKey, Map<String, EditorialDecision> decisions, SqlSessionFactory factory, NameIndex nameIndex, UsageMatcherGlobal matcher,
                   int user, Sector sector, SectorImport state, @Nullable TreeMergeHandlerConfig cfg,
                   Supplier<String> nameIdGen, Supplier<String> typeMaterialIdGen, UsageIdGen usageIdGen) {
    // we use much smaller ids than UUID which are terribly long to iterate over the entire tree - which requires to build a path from all parent IDs
    // this causes postgres to use a lot of memory and creates very large temporary files
    super(targetDatasetKey, decisions, factory, nameIndex, user, sector, state, nameIdGen, typeMaterialIdGen, usageIdGen);
    this.cfg = cfg;
    this.vKey = DSID.root(sourceDatasetKey);
    this.matcher = matcher;
    uCache = matcher.getUCache();
    groupAnalyzer = new TaxGroupAnalyzer();

    // figure out the lowest insertion point in the project/release
    // a) a target is given
    // b) a subject is given. Match it and see if it is lower and inside the target
    // c) nothing, but there maybe is an incertae sedis taxon configured to collect all unplaced
    SimpleNameWithNidx trgt = null;
    if (target != null) {
      trgt = matcher.toMatchedSimpleName(target);
    } else if (cfg != null && cfg.incertae != null) {
      trgt = matcher.toMatchedSimpleName(cfg.incertae);
    }
    parents = new MatchedParentStack(trgt);
    if (sector.getSubject() != null) {
      // match subject and its classification
      try (SqlSession session = factory.openSession()) {
        var num = session.getMapper(NameUsageMapper.class);
        // loop over classification incl the subject itself as the last usage
        for (var p : num.getClassification(sector.getSubjectAsDSID())) {
          var nusn = matcher.toMatchedSimpleName(p);
          parents.push(nusn, null);
          UsageMatch match = matcher.matchWithParents(targetDatasetKey, p, parents.classificationSN(), false, false);
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
    this.loader = new CacheLoader.Mybatis(batchSession, true);
    matcher.registerLoader(targetDatasetKey, loader); // we need to make sure we remove it at the end no matter what!

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
    var nusn = matcher.toMatchedSimpleName(nu);
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
    UsageMatch match = matcher.matchWithParents(targetDatasetKey, nu, parents.classificationSN(), true, unique);
    LOG.debug("{} matches {}", nu.getLabel(), match);
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
      var nCanon = Name.copyCanonical(nu.getName());
      var tCanon = new Taxon(nu);
      tCanon.setName(nCanon);
      match = matcher.matchWithParents(targetDatasetKey, tCanon, parents.classificationSN(), false, false);
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
    if (match.ignore || ignoreUsage(nu, decisions.get(nu.getId()), true)) {
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
        var pn = updateName(existing, n, upd, null);

        if (!upd.isEmpty()) {
          updated++;
          // update name
          nm.update(pn);
          // TODO: track source - we require a usage currently, not just a bare name
          //vsm.insertSources(existingUsageKey, n, upd);

          // commit in batches
          if (updated % 1000 == 0) {
            interruptIfCancelled();
            session.commit();
            batchSession.commit();
          }
        }
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
      if (p == null || (nu.getRank().notOtherOrUnranked() && p.getRank().lowerOrEqualsTo(nu.getRank()))) {
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
      && !containsID(uCache.getClassification(targetKey.id(parent.id), loader), target.getId())
    ) {
      issues = new Issue[]{Issue.SYNC_OUTSIDE_TARGET};
    } else {
      issues = new Issue[0];
    }
    // *** CREATE ***
    var sn = super.create(nu, parent, issues);
    parents.setMatch(sn);
    matcher.add(nu);
    created++;
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
  protected boolean ignoreUsage(NameUsageBase u, @Nullable EditorialDecision decision, boolean filterSynonymsByRank) {
    var ignore =  super.ignoreUsage(u, decision, true);
    if (!ignore) {
      // additional checks - we dont want any unranked unless they are OTU names
      ignore = u.getRank() == Rank.UNRANKED && u.getName().getType() != NameType.OTU
        || (cfg != null && cfg.isBlocked(u.getName()));
      // if issues are to be excluded we need to load the verbatim records
      if (cfg != null && !cfg.xCfg.issueExclusion.isEmpty() && u.getName().getVerbatimKey() != null) {
        var issues = vrmRO.getIssues(vKey.id(u.getName().getVerbatimKey()));
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
    Taxon t = new Taxon(n);
    var m = matcher.matchWithParents(targetDatasetKey, t, parents.classificationSN(), true, false);
    // make sure rank is correct - canonical matches are across ranks
    if (m.usage != null && m.usage.getRank() == n.getRank()) {
      return usage(m.usage);
    }
    return null;
  }

  @Override
  protected void cacheImplicit(Taxon t, Usage parent) {
    matcher.add(t);
  }

  private Name loadFromDB(String usageID) {
    return nm.getByUsage(targetDatasetKey, usageID);
  }

  private boolean proposedParentDoesNotConflict(SimpleName existing, SimpleName existingParent, SimpleName proposedParent) {
    boolean existingParentFound = false;
    if (existingParent.getRank().higherThan(proposedParent.getRank())
        && proposedParent.getRank().higherThan(existing.getRank())
        && !existingParent.getId().equals(proposedParent.getId())
    ) {
      // now check the newly proposed classification does also contain the current parent to avoid changes - we only want to patch missing ranks
      // but also make sure the existing name is not part of the proposed classification as this will result in a fatal circular loop!
      var proposedClassification = uCache.getClassification(proposedParent.toDSID(targetDatasetKey), loader);
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

  private void update(NameUsageBase nu, UsageMatch existing) {
    if (nu.getStatus().getMajorStatus() == existing.usage.getStatus().getMajorStatus()) {
      LOG.debug("Update {} {} {} from source {}:{} with status {}", existing.usage.getStatus(), existing.usage.getRank(), existing.usage.getLabel(), sector.getSubjectDatasetKey(), nu.getId(), nu.getStatus());

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
                batchSession.commit(); // we need to flush the write session to avoid broken foreign key constraints
                if (existingParent == null || proposedParentDoesNotConflict(existing.usage, existingParent, parent)) {
                  LOG.debug("Update {} with closer parent {} {} than {} from {}", existing.usage, parent.getRank(), parent.getId(), existingParent, nu);
                  numRO.updateParentId(existingUsageKey, parent.getId(), user);
                  upd.add(InfoGroup.PARENT);
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
            vn.setVerbatimKey(null);
            vn.setSectorKey(sector.getId());
            vn.setDatasetKey(targetDatasetKey);
            vn.applyUser(user);
            // check if the entity refers to a reference which we need to lookup / copy
            String ridCopy = lookupReference(vn.getReferenceId());
            vn.setReferenceId(ridCopy);
            CopyUtil.transliterateVernacularName(vn, IssueContainer.VOID);
            mapper.create(vn, existingUsageKey.getId());
            existingVNames.add(vn);
          }
        }
      }

      // try to also update the name - conditional checks within the subroutine
      Name pn = updateName(null, nu.getName(), upd, existing);

      if (!upd.isEmpty()) {
        this.updated++;
        // update name
        nm.update(pn);
        // track source
        vsm.insertSources(existingUsageKey, nu, upd);
        batchSession.commit(); // we need the parsed names to be up to date all the time! cache loaders...
        matcher.invalidate(targetDatasetKey, existing.usage.getCanonicalId());
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
   * Either the Name n or the existing usage must be given!
   */
  private Name lazilyLoad(@Nullable Name n, @Nullable UsageMatch existingUsage) {
    return n != null ? n : loadFromDB(existingUsage.usage.getId());
  }
  /**
   * Either the Name n or the existing usage must be given!
   *
   * @param n name to be updated
   * @param src source for updates
   * @param upd
   * @param existingUsage usage match instance corresponding to Name n - only used to update cache fields to be in sync with the name.
   *                 Not needed for bare name merging
   * @return
   */
  private Name updateName(@Nullable Name n, Name src, Set<InfoGroup> upd, @Nullable UsageMatch existingUsage) {
    if (n == null && existingUsage == null) return null;

    if (syncNames) {
      n = lazilyLoad(n, existingUsage);
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
      if (!upd.isEmpty() && !Objects.equals(src.getNamesIndexId(), n.getNamesIndexId())) {
        n.setNamesIndexId(src.getNamesIndexId());
        if (existingUsage != null) {
          existingUsage.usage.setNamesIndexId(src.getNamesIndexId());
        }
        // update name match in db
        nmm.update(n, src.getNamesIndexId(), src.getNamesIndexType());
        batchSession.commit(); // we need the matches to be up to date all the time! cache loaders...
      }
      if (n.getPublishedInId() == null && src.getPublishedInId() != null) {
        upd.add(InfoGroup.PUBLISHED_IN);
        Reference ref = rm.get(DSID.of(src.getDatasetKey(), src.getPublishedInId()));
        n.setPublishedInId(lookupReference(ref));
        n.setPublishedInPage(src.getPublishedInPage());
        n.setPublishedInPageLink(src.getPublishedInPageLink());
        LOG.debug("Updated {} with publishedIn", n);
      }
    }

    // now try to update the reference itself if it existed already
    if (syncReferences && !upd.contains(InfoGroup.PUBLISHED_IN) && n.getPublishedInId() != null && src.getPublishedInId() != null) {
      // TODO: Update reference links & DOI
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
        tm.setVerbatimKey(null);
        tm.setSectorKey(sector.getId());
        tm.setDatasetKey(targetDatasetKey);
        tm.applyUser(user);
        // check if the entity refers to a reference which we need to lookup / copy
        String ridCopy = lookupReference(tm.getReferenceId());
        tm.setReferenceId(ridCopy);
        try {
          mapper.create(tm);
        } catch (Exception e) {
          // ID might be used already - skip or try with no id instead?
          tm.setId(null);
          mapper.create(tm);
        }
        existingTMs.add(tm);
        if (tm.getStatus().getRoot() == TypeStatus.HOLOTYPE) {
          upd.add(InfoGroup.HOLOTYPE);
        }
      }
    }

    // basionym / name relations
    if (entities.contains(EntityType.NAME_RELATION)) {
      // TODO: implement basionym/name rel updates
    }
    // well known name identifier
    if (nameIdScope != null) {
      var nid = DSID.of(existingUsage.getDatasetKey(), nm.getNameIdByUsage(existingUsage.getDatasetKey(), existingUsage.getId()));
      nm.addIdentifier(nid, List.of(new Identifier(nameIdScope, src.getId())));
    }
    return n;
  }

  /**
   * @param vn1 required to have a name & language!
   */
  private static boolean sameVName(VernacularName vn1, VernacularName vn2) {
    return Objects.equals( rmWS(vn1.getName()), rmWS(vn2.getName()) ) ||
      ( vn1.getLatin() != null && rmWS(vn1.getLatin()).equalsIgnoreCase(rmWS(vn2.getLatin())) );
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
    matcher.removeLoader(targetDatasetKey);
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
