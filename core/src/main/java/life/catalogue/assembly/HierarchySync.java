package life.catalogue.assembly;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Identifier;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.CopyUtil;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SynonymMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.IdentifierScopeResolver;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegates the higher classification of a project to a target taxonomy.
 *
 * <p>Unlike {@link SectorSync} which pulls a sub-tree from a source dataset down into a project, a
 * HierarchySync walks <em>upwards</em> from project name usages that already carry target-dataset
 * identifiers and copies the missing higher classification into the project. It runs in three
 * sequential phases inside the standard {@link SectorRunnable} lifecycle:
 *
 * <ol>
 *   <li><b>Phase 1 — upward classification copy.</b>
 *       Streams every project usage; for those carrying an identifier whose scope (resolved via
 *       {@link IdentifierScopeResolver}) maps to the target dataset, the target's parent chain is
 *       walked and every ancestor whose rank is strictly higher than {@link Rank#GENUS} is collected.
 *       The unioned ancestor set is inserted into the project parents-before-children using a
 *       topological sort over {@code parent_id} (rank ordinals are unreliable — UNRANKED / OTHER
 *       sit at the bottom regardless of tree position). Every inserted record is tagged with the
 *       sector's id and {@link Sector.Mode#HIERARCHY}, plus a target-dataset identifier so future
 *       runs can match by id. Finally each accepted matched project usage is rewired to its
 *       newly-imported immediate above-genus ancestor; synonyms are intentionally not rewired
 *       (their {@code parent_id} must keep pointing at an accepted taxon, not a higher-rank
 *       ancestor).</li>
 *   <li><b>Phase 2 — taxonomic-status realignment.</b>
 *       For every match the target's status is loaded and compared to the project usage. Cases:
 *       both accepted is a no-op (handled by phase 1); accepted→synonym demotes via
 *       {@link NameUsageMapper#updateParentAndStatus} with parent set to the project's equivalent
 *       of the target's accepted parent; synonym→accepted promotes the same way; both-synonym
 *       retargets the parent (and aligns the synonym subtype) to match the target. Unresolvable
 *       cases (target's parent has no project equivalent) are logged and left untouched.</li>
 *   <li><b>Phase 3 — synonymy copy.</b>
 *       For every project taxon that ends up accepted and has a target match — the freshly
 *       imported above-genus ancestors plus matched project usages whose post-phase-2 status is
 *       accepted — the target's synonyms are listed via {@link SynonymMapper#listByTaxon} and
 *       copied into the project under the project's accepted taxon, tagged with the sector's key
 *       and identified back to the source. Synonyms whose target id is already represented in the
 *       project (an existing matched project usage or an imported ancestor) are skipped to avoid
 *       duplicates. Pre-existing project synonyms aren't touched and survive re-runs.</li>
 * </ol>
 *
 * <p>Records produced by the sync are tagged with the sector's {@code sectorKey} and
 * {@link Sector.Mode#HIERARCHY}, so a previous run is wiped via
 * {@link SectorProcessable#deleteBySector(DSID)} before a new one starts — making the job
 * repeatable.
 *
 * <p>The target dataset is held in {@link Sector#getSubjectDatasetKey()}: if that dataset is a
 * {@link DatasetOrigin#PROJECT}, the latest public release is resolved at run time
 * (X-Release vs plain Release controlled by {@link Sector#useXRelease()}). Concrete RELEASE,
 * XRELEASE or EXTERNAL dataset keys are used as-is. See {@code HIERARCHY-SYNC.md} at the project
 * root for the full pipeline reference.
 *
 * <h2>Limitations / Future work</h2>
 *
 * <p>The current implementation is identifier-only and intentionally conservative. Search the
 * source for {@code TODO(hierarchy-sync)} for the exact code sites where each item slots in:
 *
 * <ul>
 *   <li><b>Name-match fallback</b> — project usages without a target identifier are skipped today.
 *       The infrastructure ({@code nameIndex}, {@code matcherSupplier}) is already injected, ready
 *       to run unmatched usages through {@link UsageMatcher}.</li>
 *   <li><b>Project-side dedup of imported ancestors</b> — if the project already has an
 *       equivalent family/order, phase 1 currently inserts a new copy. A canonical-name + rank
 *       lookup against the project before copying would let us reuse existing nodes (without
 *       tagging them with this sector, so user data isn't wiped on re-run).</li>
 *   <li><b>Performance batching</b> — phase 2 / 3 do per-match {@code NameUsageMapper#get} and
 *       {@code SynonymMapper#listByTaxon} queries. For very large projects these can be batched
 *       via {@code listByIds} or a streaming join.</li>
 * </ul>
 */
public class HierarchySync extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(HierarchySync.class);

  private final SectorImportDao sid;
  // Reserved for the upcoming name-match fallback (see syncHigherClassification).
  @SuppressWarnings("unused") private final NameIndex nameIndex;
  @SuppressWarnings("unused") private final Function<SqlSession, UsageMatcher> matcherSupplier;
  private final LatestDatasetKeyCache latestKeyCache;
  private final @Nullable IdentifierScopeResolver scopeResolver;

  /** Target dataset to read the higher classification from. Resolved during {@link #init()}. */
  private int targetDatasetKey;

  // Shared state between phases — populated in phase 1, read by phase 2 + 3
  /** project usage id -> matched target usage id (all matches, regardless of status). */
  private final Map<String, String> projectMatches = new LinkedHashMap<>();
  /** project usage id -> its current TaxonomicStatus at sync start. */
  private final Map<String, TaxonomicStatus> projectStatuses = new HashMap<>();
  /** target usage id -> newly created project usage id (above-genus ancestors imported in phase 1). */
  private final Map<String, String> targetToProject = new HashMap<>();
  /** target usage id -> matched project usage id (reverse of projectMatches; built lazily for lookups). */
  private Map<String, String> matchReverse = null;

  HierarchySync(DSID<Integer> sectorKey,
                SqlSessionFactory factory,
                NameIndex nameIndex,
                Function<SqlSession, UsageMatcher> matcherSupplier,
                LatestDatasetKeyCache latestKeyCache,
                EventBroker bus,
                NameUsageIndexService indexService,
                SectorDao sdao,
                SectorImportDao sid,
                Consumer<SectorRunnable> successCallback,
                BiConsumer<SectorRunnable, Exception> errorCallback,
                @Nullable IdentifierScopeResolver scopeResolver,
                int user) throws IllegalArgumentException {
    super(sectorKey, true, factory, indexService, sdao, sid, bus, successCallback, errorCallback, true, user);
    if (sector.getMode() != Sector.Mode.HIERARCHY) {
      throw new IllegalArgumentException("HierarchySync requires a sector with mode HIERARCHY, got " + sector.getMode());
    }
    this.sid = sid;
    this.nameIndex = nameIndex;
    this.matcherSupplier = matcherSupplier;
    this.latestKeyCache = latestKeyCache;
    this.scopeResolver = scopeResolver;
  }

  @Override
  void init() throws Exception {
    super.init(false);
    targetDatasetKey = resolveTargetDatasetKey();
    LOG.info("HierarchySync sector {}: resolved target dataset {} (configured subject dataset {}, useXRelease={})",
      sectorKey, targetDatasetKey, subjectDatasetKey, sector.useXRelease());
  }

  /**
   * Resolves the effective source dataset to read the higher classification from.
   * If the configured subject dataset is a PROJECT, the latest public (X)Release is picked
   * according to {@link Sector#useXRelease()}. Other origins are used as-is.
   */
  private int resolveTargetDatasetKey() {
    DatasetInfoCache.DatasetInfo info = DatasetInfoCache.CACHE.info(subjectDatasetKey);
    DatasetOrigin origin = info.origin;
    if (origin == DatasetOrigin.PROJECT) {
      Integer key = latestKeyCache.getLatestRelease(subjectDatasetKey, sector.useXRelease());
      if (key == null) {
        throw new NotFoundException(String.format(
          "No public %s found for project %d configured as hierarchy target of sector %s",
          sector.useXRelease() ? "XRelease" : "Release", subjectDatasetKey, sectorKey));
      }
      return key;
    }
    if (origin == DatasetOrigin.RELEASE || origin == DatasetOrigin.XRELEASE || origin == DatasetOrigin.EXTERNAL) {
      return subjectDatasetKey;
    }
    throw new IllegalArgumentException("Unsupported target dataset origin " + origin + " for hierarchy sync of sector " + sectorKey);
  }

  @Override
  void doWork() throws Exception {
    state.setState(ImportState.DELETING);
    deleteOld();
    checkIfCancelled();

    state.setState(ImportState.INSERTING);
    syncHigherClassification();
    checkIfCancelled();

    state.setState(ImportState.MATCHING);
    realignStatus();
    checkIfCancelled();

    state.setState(ImportState.INSERTING);
    copySynonymies();
    checkIfCancelled();
  }

  @Override
  void doMetrics() throws Exception {
    sid.updateMetrics(state, sectorKey.getDatasetKey());
  }

  @Override
  void updateSearchIndex() throws Exception {
    indexService.indexSector(sector);
    LOG.info("Reindexed hierarchy sector {} from search index", sectorKey);
  }

  /**
   * Phase 1: walk upwards from each project name usage that carries a target-dataset identifier
   * and copy the missing higher classification (above genus) into the project. Newly inserted
   * ancestors are tagged with this sector's key. Project usages are then rewired to point at
   * their imported immediate ancestor.
   *
   * <p>Name-match fallback for usages without a target identifier is not yet implemented (see
   * {@link UsageMatcher}).
   */
  private void syncHigherClassification() throws Exception {
    LOG.info("HierarchySync phase 1 (upward classification copy) for sector {} from target dataset {}", sectorKey, targetDatasetKey);
    final int projectKey = sectorKey.getDatasetKey();
    final String targetScope = scopeResolver != null ? scopeResolver.resolve(targetDatasetKey) : null;
    if (targetScope == null) {
      LOG.warn("Hierarchy sector {}: no identifier scope mapping configured for target dataset {}; identifier-based matching disabled",
        sectorKey, targetDatasetKey);
    } else {
      LOG.info("Hierarchy sector {}: matching identifiers in scope '{}' against target dataset {}", sectorKey, targetScope, targetDatasetKey);
    }

    // Pass 1: discover project usages that match a target id (also captures their current status for phase 2)
    discoverIdentifierMatches(projectKey, targetScope);
    if (projectMatches.isEmpty()) {
      LOG.info("Hierarchy sector {}: no project usages matched to target dataset {} - phase 1 has nothing to do", sectorKey, targetDatasetKey);
      return;
    }
    LOG.info("Hierarchy sector {}: matched {} project usages to target dataset {}", sectorKey, projectMatches.size(), targetDatasetKey);

    // Pass 2: walk parent chains, collect above-genus ancestor Taxon objects.
    // Synonyms are skipped here for the rewire mapping — their parent_id should keep pointing at the
    // accepted taxon in the project, not at a high-rank ancestor. (Walking from a synonym would
    // surface the accepted taxon's chain, but it's the *accepted* project usage that needs rewiring.)
    Map<String, String> immediateAncestorForAccepted = new HashMap<>();
    Map<String, Taxon> ancestorsToInsert = collectAncestors(immediateAncestorForAccepted);
    if (ancestorsToInsert.isEmpty()) {
      LOG.info("Hierarchy sector {}: no above-genus ancestors required - phase 1 done", sectorKey);
      return;
    }
    LOG.info("Hierarchy sector {}: {} unique above-genus ancestors will be imported", sectorKey, ancestorsToInsert.size());

    // Pass 3: insert ancestors top-down (parent_id topological order); populates targetToProject
    insertAncestorsTopDown(projectKey, ancestorsToInsert, targetScope);

    // Pass 4: rewire ACCEPTED project usages to point at their newly imported immediate ancestor.
    rewireProjectParents(projectKey, immediateAncestorForAccepted);
  }

  /**
   * Streams every project name usage and populates {@link #projectMatches} +
   * {@link #projectStatuses} for every usage that carries an identifier whose scope maps to the
   * target dataset.
   */
  private void discoverIdentifierMatches(int projectKey, @Nullable String targetScope) {
    if (targetScope == null) {
      return;
    }
    try (SqlSession session = factory.openSession(true);
         Cursor<NameUsageBase> cursor = session.getMapper(NameUsageMapper.class).processDataset(projectKey, null, null)) {
      for (NameUsageBase u : cursor) {
        if (Objects.equals(sectorKey.getId(), u.getSectorKey())) {
          continue; // defensive: should already be wiped by deleteOld()
        }
        String tid = findTargetIdByIdentifier(u, targetScope);
        // TODO(hierarchy-sync): name-match fallback. If tid is null, run the project usage through
        //   UsageMatcher (against targetDatasetKey) using the usage's canonical name + classification
        //   context, and use the match's id when one is found. Fields nameIndex + matcherSupplier
        //   are already injected for this. See plan: phase 1, "name-match fallback".
        if (tid != null) {
          projectMatches.put(u.getId(), tid);
          if (u.getStatus() != null) {
            projectStatuses.put(u.getId(), u.getStatus());
          }
        }
      }
    } catch (java.io.IOException e) {
      throw new RuntimeException("Failed to stream project usages of dataset " + projectKey, e);
    }
  }

  private static @Nullable String findTargetIdByIdentifier(NameUsageBase u, String targetScope) {
    List<Identifier> ids = u.getIdentifier();
    if (ids == null) return null;
    for (Identifier id : ids) {
      if (targetScope.equalsIgnoreCase(id.getScope())) {
        return id.getId();
      }
    }
    return null;
  }

  /**
   * For each matched (project usage, target id) pair, walks the target's parent chain and collects
   * every ancestor whose rank is strictly higher than {@link Rank#GENUS}. The result map is keyed
   * by target ancestor id; {@code immediateAncestorForAccepted} is populated only for project
   * usages that are currently accepted — synonyms keep their parent (it should point at the
   * accepted taxon, not a higher-classification ancestor).
   */
  private Map<String, Taxon> collectAncestors(Map<String, String> immediateAncestorForAccepted) {
    Map<String, Taxon> ancestors = new LinkedHashMap<>();
    try (SqlSession session = factory.openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      for (Map.Entry<String, String> e : projectMatches.entrySet()) {
        // classification yields the parent chain ordered from immediate parent up to root, excluding the start node
        List<Taxon> chain = tm.classification(DSID.of(targetDatasetKey, e.getValue()));
        Taxon immediate = null;
        for (Taxon t : chain) {
          Rank r = t.getRank();
          if (r != null && r.higherThan(Rank.GENUS)) {
            ancestors.putIfAbsent(t.getId(), t);
            if (immediate == null) {
              immediate = t;
            }
          }
        }
        if (immediate != null) {
          TaxonomicStatus ps = projectStatuses.get(e.getKey());
          if (ps != null && ps.isTaxon()) {
            immediateAncestorForAccepted.put(e.getKey(), immediate.getId());
          }
        }
      }
    }
    return ancestors;
  }

  /**
   * Inserts the collected ancestor taxa into the project, parents before children. Insertion
   * order is a topological sort over {@link Taxon#getParentId()} — rank-based ordering is not
   * reliable because UNRANKED / OTHER ranks have the highest ordinals despite sitting anywhere in
   * the tree.
   *
   * <p>Each inserted usage carries this sector's key and mode, plus a target-dataset identifier so
   * future runs can match by id.
   *
   * Populates {@link #targetToProject} as it inserts.
   */
  private void insertAncestorsTopDown(int projectKey, Map<String, Taxon> ancestors, @Nullable String targetScope) {
    // remaining = pending ancestors keyed by target id. We pull out batches whose parent has already
    // been inserted (or is outside our required set / null), then loop until empty.
    Map<String, Taxon> remaining = new LinkedHashMap<>(ancestors);
    int inserted = 0;

    try (SqlSession batch = factory.openSession(ExecutorType.BATCH, false)) {
      NameUsageMapper num = batch.getMapper(NameUsageMapper.class);
      while (!remaining.isEmpty()) {
        // find every taxon whose parent is already resolved: parent_id is null, or parent is not in
        // our required set (so we'll attach it as a project root), or parent has already been inserted
        List<Taxon> ready = new ArrayList<>();
        for (Taxon t : remaining.values()) {
          String pid = t.getParentId();
          if (pid == null || !remaining.containsKey(pid)) {
            ready.add(t);
          }
        }
        if (ready.isEmpty()) {
          // no progress means a cycle among the remaining parent_id refs — bail loudly
          LOG.warn("Hierarchy sector {}: cannot resolve insertion order for {} remaining ancestors (parent_id cycle in target dataset {}); skipping them",
            sectorKey, remaining.size(), targetDatasetKey);
          break;
        }
        for (Taxon t : ready) {
          final String origTargetId = t.getId();
          final String origTargetParentId = t.getParentId();
          // resolve project parent: if our parent is in the inserted set, use the new project id; otherwise null (root)
          String projectParentId = origTargetParentId == null ? null : targetToProject.get(origTargetParentId);

          // TODO(hierarchy-sync): project-side dedup. Before inserting, match the ancestor
          //   (canonical name + rank) against existing project usages. If an equivalent already
          //   exists (e.g. a manually-curated family), reuse it: record targetToProject.put(origTargetId, existingProjectId)
          //   and skip the copy + addIdentifier — but DO NOT tag the existing record with this sector
          //   (we don't want deleteBySector to wipe user data on re-runs).

          // tag with sector before copying (CopyUtil propagates Taxon.sectorKey to the Name)
          t.setSectorKey(sector.getId());
          t.setSectorMode(Sector.Mode.HIERARCHY);
          if (t.getName() != null) {
            t.getName().setSectorKey(sector.getId());
            t.getName().setSectorMode(Sector.Mode.HIERARCHY);
          }
          // copy. No extension entities; reference linkage is dropped in this phase.
          CopyUtil.copyUsage(batch, t, DSID.of(projectKey, projectParentId), user, Set.of(),
            ref -> null, refId -> null);

          // CopyUtil mutated t to its new project id - record mapping
          targetToProject.put(origTargetId, t.getId());

          // attach the target identifier so future syncs can match by id
          if (targetScope != null) {
            num.addIdentifier(DSID.of(projectKey, t.getId()), List.of(new Identifier(targetScope, origTargetId)));
          }

          remaining.remove(origTargetId);
          if (++inserted % 1000 == 0) {
            batch.commit();
          }
        }
      }
      batch.commit();
    }
    LOG.info("Hierarchy sector {}: inserted {} above-genus taxa from target dataset {}", sectorKey, inserted, targetDatasetKey);
  }

  /**
   * For every matched <em>accepted</em> project usage, rewires its parent_id to the newly imported
   * immediate ancestor. Project usages whose immediate ancestor could not be inserted are left
   * untouched.
   */
  private void rewireProjectParents(int projectKey, Map<String, String> immediateAncestorForAccepted) {
    if (immediateAncestorForAccepted.isEmpty()) return;
    int rewired = 0;
    int skipped = 0;
    try (SqlSession batch = factory.openSession(ExecutorType.BATCH, false)) {
      NameUsageMapper num = batch.getMapper(NameUsageMapper.class);
      for (Map.Entry<String, String> e : immediateAncestorForAccepted.entrySet()) {
        String newParent = targetToProject.get(e.getValue());
        if (newParent == null) {
          skipped++;
          continue;
        }
        num.updateParentId(DSID.of(projectKey, e.getKey()), newParent, user);
        if (++rewired % 1000 == 0) {
          batch.commit();
        }
      }
      batch.commit();
    }
    LOG.info("Hierarchy sector {}: rewired {} accepted project usages to imported higher classification (skipped {} without resolvable ancestor)",
      sectorKey, rewired, skipped);
  }


  /**
   * Phase 2: align the {@link TaxonomicStatus} of each matched project usage with the target.
   *
   * <p>Cases handled:
   * <ul>
   *   <li>both accepted: nothing to do (parent rewire happened in phase 1)</li>
   *   <li>both synonym: status copied if it differs (e.g. SYNONYM ↔ AMBIGUOUS_SYNONYM ↔ MISAPPLIED);
   *       parent_id adjusted to point at the project's representation of the target's accepted
   *       taxon when resolvable</li>
   *   <li>project accepted, target synonym: project usage demoted to the target's synonym status,
   *       parent_id rewritten to the project's representation of the target's accepted taxon</li>
   *   <li>project synonym, target accepted: project usage promoted to the target's accepted status,
   *       parent_id rewritten to the project's representation of the target's parent (the
   *       above-genus ancestor imported in phase 1, or another matched project usage)</li>
   * </ul>
   *
   * <p>Cases where the corresponding project usage cannot be located are logged and skipped. We
   * keep this conservative — we'd rather leave a project record alone than orphan it.
   */
  private void realignStatus() throws Exception {
    LOG.info("HierarchySync phase 2 (status realignment) for sector {}", sectorKey);
    if (projectMatches.isEmpty()) {
      LOG.info("Hierarchy sector {}: phase 2 skipped — no matches", sectorKey);
      return;
    }
    final int projectKey = sectorKey.getDatasetKey();
    int demoted = 0, promoted = 0, synonymRetargeted = 0, unchanged = 0, unresolved = 0;

    try (SqlSession session = factory.openSession(true);
         SqlSession batch = factory.openSession(ExecutorType.BATCH, false)) {
      NameUsageMapper targetNum = session.getMapper(NameUsageMapper.class);
      NameUsageMapper writeNum = batch.getMapper(NameUsageMapper.class);
      int written = 0;

      for (Map.Entry<String, String> e : projectMatches.entrySet()) {
        final String projectId = e.getKey();
        final String targetId = e.getValue();
        TaxonomicStatus pStatus = projectStatuses.get(projectId);
        NameUsageBase target = targetNum.get(DSID.of(targetDatasetKey, targetId));
        if (target == null) {
          // target gone? skip — phase 1 would have warned earlier, no point spamming again
          continue;
        }
        TaxonomicStatus tStatus = target.getStatus();
        if (pStatus == null || tStatus == null) {
          unresolved++;
          continue;
        }

        // both accepted -> already handled by phase 1 rewire
        if (pStatus.isTaxon() && tStatus.isTaxon()) {
          unchanged++;
          continue;
        }

        // figure out the intended project parent given the target's parent
        String newProjectParent = resolveProjectIdForTarget(target.getParentId());

        if (pStatus.isTaxon() && tStatus.isSynonym()) {
          // accepted -> synonym (demote)
          if (newProjectParent == null) {
            LOG.info("Hierarchy sector {}: cannot demote {} to synonym - target accepted parent {} not found in project", sectorKey, projectId, target.getParentId());
            unresolved++;
            continue;
          }
          writeNum.updateParentAndStatus(DSID.of(projectKey, projectId), newProjectParent, tStatus, user);
          projectStatuses.put(projectId, tStatus);
          demoted++;
          if (++written % 1000 == 0) batch.commit();
          continue;
        }

        if (pStatus.isSynonym() && tStatus.isTaxon()) {
          // synonym -> accepted (promote)
          if (newProjectParent == null) {
            LOG.info("Hierarchy sector {}: cannot promote {} to accepted - target parent {} not found in project", sectorKey, projectId, target.getParentId());
            unresolved++;
            continue;
          }
          writeNum.updateParentAndStatus(DSID.of(projectKey, projectId), newProjectParent, tStatus, user);
          projectStatuses.put(projectId, tStatus);
          promoted++;
          if (++written % 1000 == 0) batch.commit();
          continue;
        }

        // both synonym - retarget if the accepted differs, plus tweak status subtype if needed
        if (pStatus.isSynonym() && tStatus.isSynonym()) {
          if (newProjectParent != null) {
            // even if status subtype matches, retarget the parent to the project's accepted equivalent
            writeNum.updateParentAndStatus(DSID.of(projectKey, projectId), newProjectParent, tStatus, user);
            projectStatuses.put(projectId, tStatus);
            synonymRetargeted++;
            if (++written % 1000 == 0) batch.commit();
          } else if (pStatus != tStatus) {
            // can't retarget but at least align the synonym subtype
            writeNum.updateStatus(DSID.of(projectKey, projectId), tStatus, user);
            projectStatuses.put(projectId, tStatus);
            synonymRetargeted++;
            if (++written % 1000 == 0) batch.commit();
          } else {
            unchanged++;
          }
        }
      }
      batch.commit();
    }
    LOG.info("Hierarchy sector {}: phase 2 done - demoted={}, promoted={}, synonyms retargeted/realigned={}, unchanged={}, unresolved={}",
      sectorKey, demoted, promoted, synonymRetargeted, unchanged, unresolved);
  }

  /**
   * Returns the project usage id corresponding to a target usage id, by checking (a) the ancestors
   * imported in phase 1 and (b) the existing matched project usages. Returns {@code null} if no
   * project equivalent is known.
   */
  private @Nullable String resolveProjectIdForTarget(@Nullable String targetId) {
    if (targetId == null) return null;
    String pid = targetToProject.get(targetId);
    if (pid != null) return pid;
    if (matchReverse == null) {
      matchReverse = new HashMap<>(projectMatches.size());
      for (Map.Entry<String, String> e : projectMatches.entrySet()) {
        matchReverse.put(e.getValue(), e.getKey());
      }
    }
    return matchReverse.get(targetId);
  }

  /**
   * Phase 3: for every project taxon that ends up accepted and matched to the target — i.e. the
   * higher taxa imported in phase 1 plus any matched project usage that is currently accepted
   * after phase 2 — copies the target's synonyms into the project, attached to the project's
   * accepted taxon and tagged with this sector's key.
   *
   * <p>Synonyms whose target id is already mapped to a project usage (either via an existing
   * matched project usage or via an imported ancestor) are skipped to avoid creating duplicates.
   *
   * <p>Pre-existing project synonyms of the same accepted taxa are not touched: they don't carry
   * this sector's key, so {@code deleteBySector} will leave them alone on re-runs.
   */
  private void copySynonymies() throws Exception {
    LOG.info("HierarchySync phase 3 (synonymy copy) for sector {}", sectorKey);
    if (projectMatches.isEmpty() && targetToProject.isEmpty()) {
      LOG.info("Hierarchy sector {}: phase 3 skipped — no matches/ancestors", sectorKey);
      return;
    }

    final int projectKey = sectorKey.getDatasetKey();
    final String targetScope = scopeResolver != null ? scopeResolver.resolve(targetDatasetKey) : null;

    // Build the universe of (projectAcceptedId -> targetAcceptedId) pairs:
    //   (a) every ancestor imported in phase 1 — all accepted by construction
    //   (b) every matched project usage whose status (after phase 2) is accepted
    Map<String, String> acceptedPairs = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : targetToProject.entrySet()) {
      acceptedPairs.put(e.getValue(), e.getKey());
    }
    for (Map.Entry<String, String> e : projectMatches.entrySet()) {
      TaxonomicStatus s = projectStatuses.get(e.getKey());
      if (s != null && s.isTaxon()) {
        acceptedPairs.putIfAbsent(e.getKey(), e.getValue());
      }
    }
    if (acceptedPairs.isEmpty()) {
      LOG.info("Hierarchy sector {}: phase 3 has no accepted matches to enrich", sectorKey);
      return;
    }

    // target ids that are already represented in the project — skip them when copying synonyms
    Set<String> alreadyMappedTargetIds = new HashSet<>(projectMatches.size() + targetToProject.size());
    alreadyMappedTargetIds.addAll(projectMatches.values());
    alreadyMappedTargetIds.addAll(targetToProject.keySet());

    int copied = 0;
    int skipped = 0;
    try (SqlSession readSession = factory.openSession(true);
         SqlSession batch = factory.openSession(ExecutorType.BATCH, false)) {
      SynonymMapper readSyn = readSession.getMapper(SynonymMapper.class);
      NameUsageMapper writeNum = batch.getMapper(NameUsageMapper.class);
      int written = 0;

      for (Map.Entry<String, String> pair : acceptedPairs.entrySet()) {
        final String projectAcceptedId = pair.getKey();
        final String targetAcceptedId = pair.getValue();

        List<Synonym> synonyms = readSyn.listByTaxon(DSID.of(targetDatasetKey, targetAcceptedId));
        for (Synonym syn : synonyms) {
          if (alreadyMappedTargetIds.contains(syn.getId())) {
            skipped++;
            continue;
          }
          final String origTargetId = syn.getId();

          // tag with sector before copying (CopyUtil propagates Taxon/Synonym.sectorKey to the Name)
          syn.setSectorKey(sector.getId());
          syn.setSectorMode(Sector.Mode.HIERARCHY);
          if (syn.getName() != null) {
            syn.getName().setSectorKey(sector.getId());
            syn.getName().setSectorMode(Sector.Mode.HIERARCHY);
          }
          // copy. parent = the project's accepted taxon. No extension entities; reference linkage dropped.
          CopyUtil.copyUsage(batch, syn, DSID.of(projectKey, projectAcceptedId), user, Set.of(),
            ref -> null, refId -> null);

          if (targetScope != null) {
            writeNum.addIdentifier(DSID.of(projectKey, syn.getId()), List.of(new Identifier(targetScope, origTargetId)));
          }
          copied++;
          if (++written % 1000 == 0) batch.commit();
        }
      }
      batch.commit();
    }
    LOG.info("Hierarchy sector {}: phase 3 done — copied {} synonyms across {} accepted taxa (skipped {} already represented in the project)",
      sectorKey, copied, acceptedPairs.size(), skipped);
  }

  /**
   * Wipes records produced by a previous run of this sector via the standard
   * {@link SectorProcessable} contract.
   */
  private void deleteOld() {
    try (SqlSession session = factory.openSession(true)) {
      for (Class<? extends SectorProcessable<?>> m : SectorProcessable.MAPPERS) {
        int count = session.getMapper(m).deleteBySector(sector);
        LOG.info("Deleted {} existing {}s from hierarchy sector {}", count, m.getSimpleName().replaceAll("Mapper", ""), sector);
      }
    }
  }

  public int getTargetDatasetKey() {
    return targetDatasetKey;
  }
}
