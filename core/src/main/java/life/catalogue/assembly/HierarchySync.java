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
 * Delegates the higher classification of a project to a source taxonomy.
 *
 * <p>Unlike {@link SectorSync} which pulls a sub-tree from a source dataset down into a project, a
 * HierarchySync walks <em>upwards</em> from project name usages that already carry source-dataset
 * identifiers and copies the missing higher classification into the project. It runs in three
 * sequential phases inside the standard {@link SectorRunnable} lifecycle:
 *
 * <ol>
 *   <li><b>Phase 1 — upward classification copy.</b>
 *       Streams every project usage; for those carrying an identifier whose scope (resolved via
 *       {@link IdentifierScopeResolver}) maps to the source dataset, the source's parent chain is
 *       walked and every ancestor whose rank is strictly higher than {@link Rank#GENUS} is collected.
 *       The unioned ancestor set is inserted into the project parents-before-children using a
 *       topological sort over {@code parent_id} (rank ordinals are unreliable — UNRANKED / OTHER
 *       sit at the bottom regardless of tree position). Every inserted record is tagged with the
 *       sector's id and {@link Sector.Mode#HIERARCHY}, plus a source-dataset identifier so future
 *       runs can match by id. Finally each accepted matched project usage is rewired to its
 *       newly-imported immediate above-genus ancestor; synonyms are intentionally not rewired
 *       (their {@code parent_id} must keep pointing at an accepted taxon, not a higher-rank
 *       ancestor).</li>
 *   <li><b>Phase 2 — taxonomic-status realignment.</b>
 *       For every match the source's status is loaded and compared to the project usage. Cases:
 *       both accepted is a no-op (handled by phase 1); accepted→synonym demotes via
 *       {@link NameUsageMapper#updateParentAndStatus} with parent set to the project's equivalent
 *       of the source's accepted parent; synonym→accepted promotes the same way; both-synonym
 *       retargets the parent (and aligns the synonym subtype) to match the source. Unresolvable
 *       cases (source's parent has no project equivalent) are logged and left untouched.</li>
 *   <li><b>Phase 3 — synonymy copy.</b>
 *       For every project taxon that ends up accepted and has a source match — the freshly
 *       imported above-genus ancestors plus matched project usages whose post-phase-2 status is
 *       accepted — the source's synonyms are listed via {@link SynonymMapper#listByTaxon} and
 *       copied into the project under the project's accepted taxon, tagged with the sector's key
 *       and identified back to the source. Synonyms whose source id is already represented in the
 *       project (an existing matched project usage or an imported ancestor) are skipped to avoid
 *       duplicates. Pre-existing project synonyms aren't touched and survive re-runs.</li>
 * </ol>
 *
 * <p>Records produced by the sync are tagged with the sector's {@code sectorKey} and
 * {@link Sector.Mode#HIERARCHY}, so a previous run is wiped via
 * {@link SectorProcessable#deleteBySector(DSID)} before a new one starts — making the job
 * repeatable.
 *
 * <p>The source dataset is held in {@link Sector#getSubjectDatasetKey()}: if that dataset is a
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
 *   <li><b>Name-match fallback</b> — project usages without a source identifier are skipped today.
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

  /** Source dataset to read the higher classification from. Resolved during {@link #init()}. */
  private int sourceDatasetKey;

  // Shared state between phases — populated in phase 1, read by phase 2 + 3
  /** project usage id -> matched source usage id (all matches, regardless of status). */
  private final Map<String, String> projectMatches = new LinkedHashMap<>();
  /** project usage id -> its current TaxonomicStatus at sync start. */
  private final Map<String, TaxonomicStatus> projectStatuses = new HashMap<>();
  /** project usage id -> its current parent_id (captured for matched usages; populated lazily for non-matched
   *  parents we touch via the cycle guard / synonym walk-up). */
  private final Map<String, String> projectParents = new HashMap<>();
  /** source usage id -> newly created project usage id (above-genus ancestors imported in phase 1). */
  private final Map<String, String> sourceToProject = new HashMap<>();
  /** source usage id -> matched project usage id (reverse of projectMatches; built eagerly at start of phase 1). */
  private Map<String, String> matchReverse = null;

  /** Cap on how many hops we walk to reach an accepted from a matched synonym, or to detect a cycle. */
  private static final int MAX_WALK_DEPTH = 20;

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
    sourceDatasetKey = resolveSourceDatasetKey();
    LOG.info("HierarchySync sector {}: resolved source dataset {} (configured subject dataset {}, useXRelease={})",
      sectorKey, sourceDatasetKey, subjectDatasetKey, sector.useXRelease());
  }

  /**
   * Resolves the effective source dataset to read the higher classification from.
   * If the configured subject dataset is a PROJECT, the latest public (X)Release is picked
   * according to {@link Sector#useXRelease()}. Other origins are used as-is.
   */
  private int resolveSourceDatasetKey() {
    DatasetInfoCache.DatasetInfo info = DatasetInfoCache.CACHE.info(subjectDatasetKey);
    DatasetOrigin origin = info.origin;
    if (origin == DatasetOrigin.PROJECT) {
      Integer key = latestKeyCache.getLatestRelease(subjectDatasetKey, sector.useXRelease());
      if (key == null) {
        throw new NotFoundException(String.format(
          "No public %s found for project %d configured as hierarchy source of sector %s",
          sector.useXRelease() ? "XRelease" : "Release", subjectDatasetKey, sectorKey));
      }
      return key;
    }
    if (origin == DatasetOrigin.RELEASE || origin == DatasetOrigin.XRELEASE || origin == DatasetOrigin.EXTERNAL) {
      return subjectDatasetKey;
    }
    throw new IllegalArgumentException("Unsupported source dataset origin " + origin + " for hierarchy sync of sector " + sectorKey);
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
   * Phase 1: walk upwards from each project name usage that carries a source-dataset identifier
   * and copy the missing higher classification (above genus) into the project. Newly inserted
   * ancestors are tagged with this sector's key. Project usages are then rewired to point at
   * their imported immediate ancestor.
   *
   * <p>Name-match fallback for usages without a source identifier is not yet implemented (see
   * {@link UsageMatcher}).
   */
  private void syncHigherClassification() throws Exception {
    LOG.info("HierarchySync phase 1 (upward classification copy) for sector {} from source dataset {}", sectorKey, sourceDatasetKey);
    final int projectKey = sectorKey.getDatasetKey();
    final String sourceScope = scopeResolver != null ? scopeResolver.resolve(sourceDatasetKey) : null;
    if (sourceScope == null) {
      LOG.warn("Hierarchy sector {}: no identifier scope mapping configured for source dataset {}; identifier-based matching disabled",
        sectorKey, sourceDatasetKey);
    } else {
      LOG.info("Hierarchy sector {}: matching identifiers in scope '{}' against source dataset {}", sectorKey, sourceScope, sourceDatasetKey);
    }

    // Pass 1: discover project usages that match a source id (also captures their current status
    // and parent_id for use in phases 2 + 3 and the synonym walk-up).
    discoverIdentifierMatches(projectKey, sourceScope);
    if (projectMatches.isEmpty()) {
      LOG.info("Hierarchy sector {}: no project usages matched to source dataset {} - phase 1 has nothing to do", sectorKey, sourceDatasetKey);
      return;
    }
    LOG.info("Hierarchy sector {}: matched {} project usages to source dataset {}", sectorKey, projectMatches.size(), sourceDatasetKey);
    // Build matchReverse eagerly so phase 1 rewiring (and phase 2) can resolve source ids through it.
    buildMatchReverse();

    // Pass 2: walk parent chains. For each matched project usage, collect every above-genus source
    // ancestor for import. For each matched ACCEPTED project usage, also record the full source
    // classification chain (immediate parent first) — phase 1's rewire walks that chain looking for
    // the closest ancestor that already has a project equivalent (an imported above-genus ancestor
    // or another matched accepted project usage), so an existing matched genus is preferred over
    // jumping all the way to family. Synonyms are intentionally skipped for the rewire mapping —
    // their parent_id must keep pointing at an accepted taxon and is handled by phase 2.
    Map<String, List<String>> sourceChainForAccepted = new LinkedHashMap<>();
    Map<String, Taxon> ancestorsToInsert = collectAncestors(sourceChainForAccepted);
    if (ancestorsToInsert.isEmpty() && sourceChainForAccepted.isEmpty()) {
      LOG.info("Hierarchy sector {}: no above-genus ancestors required and no accepted matches - phase 1 done", sectorKey);
      return;
    }
    LOG.info("Hierarchy sector {}: {} unique above-genus ancestors will be imported", sectorKey, ancestorsToInsert.size());

    // Pass 3: insert ancestors top-down (parent_id topological order); populates sourceToProject
    insertAncestorsTopDown(projectKey, ancestorsToInsert, sourceScope);

    // Pass 4: rewire ACCEPTED project usages to point at their closest project ancestor.
    rewireProjectParents(projectKey, sourceChainForAccepted);
  }

  /**
   * Builds the {@code source id -> project id} reverse lookup once, eagerly, so it is available to
   * both phase 1 rewiring and phase 2. Last write wins on duplicates — if two project usages map
   * to the same source id we keep the most-recently-seen one (deterministic via the LinkedHashMap
   * iteration order of {@link #projectMatches}).
   */
  private void buildMatchReverse() {
    matchReverse = new HashMap<>(projectMatches.size());
    for (Map.Entry<String, String> e : projectMatches.entrySet()) {
      matchReverse.put(e.getValue(), e.getKey());
    }
  }

  /**
   * Streams every project name usage and populates {@link #projectMatches} +
   * {@link #projectStatuses} for every usage that carries an identifier whose scope maps to the
   * source dataset.
   */
  private void discoverIdentifierMatches(int projectKey, @Nullable String sourceScope) {
    if (sourceScope == null) {
      return;
    }
    try (SqlSession session = factory.openSession(true);
         Cursor<NameUsageBase> cursor = session.getMapper(NameUsageMapper.class).processDataset(projectKey, null, null)) {
      for (NameUsageBase u : cursor) {
        if (Objects.equals(sectorKey.getId(), u.getSectorKey())) {
          continue; // defensive: should already be wiped by deleteOld()
        }
        String tid = findSourceIdByIdentifier(u, sourceScope);
        // TODO(hierarchy-sync): name-match fallback. If tid is null, run the project usage through
        //   UsageMatcher (against sourceDatasetKey) using the usage's canonical name + classification
        //   context, and use the match's id when one is found. Fields nameIndex + matcherSupplier
        //   are already injected for this. See plan: phase 1, "name-match fallback".
        if (tid != null) {
          projectMatches.put(u.getId(), tid);
          if (u.getStatus() != null) {
            projectStatuses.put(u.getId(), u.getStatus());
          }
          projectParents.put(u.getId(), u.getParentId());
        }
      }
    } catch (java.io.IOException e) {
      throw new RuntimeException("Failed to stream project usages of dataset " + projectKey, e);
    }
  }

  private static @Nullable String findSourceIdByIdentifier(NameUsageBase u, String sourceScope) {
    List<Identifier> ids = u.getIdentifier();
    if (ids == null) return null;
    for (Identifier id : ids) {
      if (sourceScope.equalsIgnoreCase(id.getScope())) {
        return id.getId();
      }
    }
    return null;
  }

  /**
   * For each matched (project usage, source id) pair, walks the source's parent chain and collects
   * every ancestor whose rank is strictly higher than {@link Rank#GENUS} for the import pass. For
   * each matched ACCEPTED project usage, also records the full source classification chain
   * (immediate parent first, root last) into {@code sourceChainForAccepted} so phase 1's rewire
   * can resolve it to the closest existing project ancestor.
   */
  private Map<String, Taxon> collectAncestors(Map<String, List<String>> sourceChainForAccepted) {
    Map<String, Taxon> ancestors = new LinkedHashMap<>();
    try (SqlSession session = factory.openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      for (Map.Entry<String, String> e : projectMatches.entrySet()) {
        // classification yields the parent chain ordered from immediate parent up to root, excluding the start node
        List<Taxon> chain = tm.classification(DSID.of(sourceDatasetKey, e.getValue()));
        List<String> chainIds = new ArrayList<>(chain.size());
        for (Taxon t : chain) {
          chainIds.add(t.getId());
          Rank r = t.getRank();
          if (r != null && r.higherThan(Rank.GENUS)) {
            ancestors.putIfAbsent(t.getId(), t);
          }
        }
        TaxonomicStatus ps = projectStatuses.get(e.getKey());
        if (ps != null && ps.isTaxon() && !chainIds.isEmpty()) {
          sourceChainForAccepted.put(e.getKey(), chainIds);
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
   * <p>Each inserted usage carries this sector's key and mode, plus a source-dataset identifier so
   * future runs can match by id.
   *
   * Populates {@link #sourceToProject} as it inserts.
   */
  private void insertAncestorsTopDown(int projectKey, Map<String, Taxon> ancestors, @Nullable String sourceScope) {
    // remaining = pending ancestors keyed by source id. We pull out batches whose parent has already
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
          LOG.warn("Hierarchy sector {}: cannot resolve insertion order for {} remaining ancestors (parent_id cycle in source dataset {}); skipping them",
            sectorKey, remaining.size(), sourceDatasetKey);
          break;
        }
        for (Taxon t : ready) {
          final String origSourceId = t.getId();
          final String origSourceParentId = t.getParentId();
          // resolve project parent: if our parent is in the inserted set, use the new project id; otherwise null (root)
          String projectParentId = origSourceParentId == null ? null : sourceToProject.get(origSourceParentId);

          // TODO(hierarchy-sync): project-side dedup. Before inserting, match the ancestor
          //   (canonical name + rank) against existing project usages. If an equivalent already
          //   exists (e.g. a manually-curated family), reuse it: record sourceToProject.put(origSourceId, existingProjectId)
          //   and skip the copy + addIdentifier — but DO NOT tag the existing record with this sector
          //   (we don't want deleteBySector to wipe user data on re-runs).

          // tag with sector before copying (CopyUtil propagates Taxon.sectorKey to the Name)
          t.setSectorKey(sector.getId());
          t.setSectorMode(Sector.Mode.HIERARCHY);
          // null verbatim_source_key — the loaded value points at the source dataset's verbatim_source
          // and would violate the project's FK. CopyUtil nulls verbatim_key but not this one.
          t.setVerbatimSourceKey(null);
          if (t.getName() != null) {
            t.getName().setSectorKey(sector.getId());
            t.getName().setSectorMode(Sector.Mode.HIERARCHY);
            t.getName().setVerbatimSourceKey(null);
          }
          // copy. No extension entities; reference linkage is dropped in this phase.
          CopyUtil.copyUsage(batch, t, DSID.of(projectKey, projectParentId), user, Set.of(),
            ref -> null, refId -> null);

          // CopyUtil mutated t to its new project id - record mapping
          sourceToProject.put(origSourceId, t.getId());

          // attach the source identifier so future syncs can match by id
          if (sourceScope != null) {
            num.addIdentifier(DSID.of(projectKey, t.getId()), List.of(new Identifier(sourceScope, origSourceId)));
          }

          remaining.remove(origSourceId);
          if (++inserted % 1000 == 0) {
            batch.commit();
          }
        }
      }
      batch.commit();
    }
    LOG.info("Hierarchy sector {}: inserted {} above-genus taxa from source dataset {}", sectorKey, inserted, sourceDatasetKey);
  }

  /**
   * For every matched <em>accepted</em> project usage, walks the source classification chain
   * (immediate parent first) and rewires its {@code parent_id} to the closest ancestor that has a
   * project equivalent — either an above-genus ancestor that we just imported, or another matched
   * accepted project usage (so the existing matched genus is preferred over the imported family).
   * Updates are skipped when the new parent matches the current one. Usages whose entire chain has
   * no project equivalent are left untouched.
   */
  private void rewireProjectParents(int projectKey, Map<String, List<String>> sourceChainForAccepted) {
    if (sourceChainForAccepted.isEmpty()) return;
    int rewired = 0;
    int unchanged = 0;
    int unresolved = 0;
    try (SqlSession batch = factory.openSession(ExecutorType.BATCH, false)) {
      NameUsageMapper num = batch.getMapper(NameUsageMapper.class);
      for (Map.Entry<String, List<String>> e : sourceChainForAccepted.entrySet()) {
        final String projectId = e.getKey();
        String newParent = null;
        for (String ancestorSourceId : e.getValue()) {
          String resolved = resolveProjectIdForSource(ancestorSourceId);
          if (resolved != null) {
            newParent = resolved;
            break;
          }
        }
        if (newParent == null) {
          unresolved++;
          continue;
        }
        if (newParent.equals(projectId)) {
          // would create a self-loop (e.g. cached projectParents already had us here); skip
          unchanged++;
          continue;
        }
        String currentParent = projectParents.get(projectId);
        if (newParent.equals(currentParent)) {
          unchanged++;
          continue;
        }
        num.updateParentId(DSID.of(projectKey, projectId), newParent, user);
        projectParents.put(projectId, newParent);
        if (++rewired % 1000 == 0) {
          batch.commit();
        }
      }
      batch.commit();
    }
    LOG.info("Hierarchy sector {}: rewired {} accepted project usages to closest project ancestor (unchanged {}, unresolvable {})",
      sectorKey, rewired, unchanged, unresolved);
  }


  /**
   * Phase 2: align the {@link TaxonomicStatus} of each matched project usage with the source.
   *
   * <p>Cases handled:
   * <ul>
   *   <li>both accepted: nothing to do (parent rewire happened in phase 1)</li>
   *   <li>both synonym: status copied if it differs (e.g. SYNONYM ↔ AMBIGUOUS_SYNONYM ↔ MISAPPLIED);
   *       parent_id adjusted to point at the project's representation of the source's accepted
   *       taxon when resolvable</li>
   *   <li>project accepted, source synonym: project usage demoted to the source's synonym status,
   *       parent_id rewritten to the project's representation of the source's accepted taxon</li>
   *   <li>project synonym, source accepted: project usage promoted to the source's accepted status,
   *       parent_id rewritten to the project's representation of the source's parent (the
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
    int demoted = 0, promoted = 0, synonymRetargeted = 0, unchanged = 0, unresolved = 0, cycleBlocked = 0;

    try (SqlSession session = factory.openSession(true);
         SqlSession batch = factory.openSession(ExecutorType.BATCH, false)) {
      NameUsageMapper sourceNum = session.getMapper(NameUsageMapper.class);
      NameUsageMapper readNum = session.getMapper(NameUsageMapper.class);
      NameUsageMapper writeNum = batch.getMapper(NameUsageMapper.class);
      int written = 0;

      for (Map.Entry<String, String> e : projectMatches.entrySet()) {
        final String projectId = e.getKey();
        final String sourceId = e.getValue();
        TaxonomicStatus pStatus = projectStatuses.get(projectId);
        NameUsageBase source = sourceNum.get(DSID.of(sourceDatasetKey, sourceId));
        if (source == null) {
          // source gone? skip — phase 1 would have warned earlier, no point spamming again
          continue;
        }
        TaxonomicStatus tStatus = source.getStatus();
        if (pStatus == null || tStatus == null) {
          unresolved++;
          continue;
        }

        // both accepted -> already handled by phase 1 rewire
        if (pStatus.isTaxon() && tStatus.isTaxon()) {
          unchanged++;
          continue;
        }

        // figure out the intended project parent given the source's parent
        String newProjectParent = resolveProjectIdForSource(source.getParentId());

        if (pStatus.isTaxon() && tStatus.isSynonym()) {
          // accepted -> synonym (demote)
          if (newProjectParent == null) {
            LOG.info("Hierarchy sector {}: cannot demote {} to synonym - source accepted parent {} not found in project", sectorKey, projectId, source.getParentId());
            unresolved++;
            continue;
          }
          if (wouldCreateCycle(projectKey, projectId, newProjectParent, readNum)) {
            LOG.warn("Hierarchy sector {}: skipping demote of {} - new parent {} would create a cycle", sectorKey, projectId, newProjectParent);
            cycleBlocked++;
            continue;
          }
          writeNum.updateParentAndStatus(DSID.of(projectKey, projectId), newProjectParent, tStatus, user);
          projectStatuses.put(projectId, tStatus);
          projectParents.put(projectId, newProjectParent);
          demoted++;
          if (++written % 1000 == 0) batch.commit();
          continue;
        }

        if (pStatus.isSynonym() && tStatus.isTaxon()) {
          // synonym -> accepted (promote)
          if (newProjectParent == null) {
            LOG.info("Hierarchy sector {}: cannot promote {} to accepted - source parent {} not found in project", sectorKey, projectId, source.getParentId());
            unresolved++;
            continue;
          }
          if (wouldCreateCycle(projectKey, projectId, newProjectParent, readNum)) {
            LOG.warn("Hierarchy sector {}: skipping promote of {} - new parent {} would create a cycle", sectorKey, projectId, newProjectParent);
            cycleBlocked++;
            continue;
          }
          writeNum.updateParentAndStatus(DSID.of(projectKey, projectId), newProjectParent, tStatus, user);
          projectStatuses.put(projectId, tStatus);
          projectParents.put(projectId, newProjectParent);
          promoted++;
          if (++written % 1000 == 0) batch.commit();
          continue;
        }

        // both synonym - retarget if the accepted differs, plus tweak status subtype if needed
        if (pStatus.isSynonym() && tStatus.isSynonym()) {
          if (newProjectParent != null) {
            if (wouldCreateCycle(projectKey, projectId, newProjectParent, readNum)) {
              LOG.warn("Hierarchy sector {}: skipping synonym retarget of {} - new parent {} would create a cycle", sectorKey, projectId, newProjectParent);
              cycleBlocked++;
              continue;
            }
            // even if status subtype matches, retarget the parent to the project's accepted equivalent
            writeNum.updateParentAndStatus(DSID.of(projectKey, projectId), newProjectParent, tStatus, user);
            projectStatuses.put(projectId, tStatus);
            projectParents.put(projectId, newProjectParent);
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
    LOG.info("Hierarchy sector {}: phase 2 done - demoted={}, promoted={}, synonyms retargeted/realigned={}, unchanged={}, unresolved={}, cycle-blocked={}",
      sectorKey, demoted, promoted, synonymRetargeted, unchanged, unresolved, cycleBlocked);
  }

  /**
   * Returns the project usage id corresponding to a source usage id, by checking (a) the ancestors
   * imported in phase 1 and (b) the existing matched project usages. When a matchReverse hit is a
   * project synonym we walk its in-project {@code parent_id} chain (bounded by
   * {@link #MAX_WALK_DEPTH}) to find the accepted taxon it belongs to — a {@code parent_id} must
   * point at an accepted, and a project synonym carrying a source accepted's identifier is the
   * common case in COL data. Returns {@code null} if no project equivalent (or no reachable
   * accepted) is known.
   */
  private @Nullable String resolveProjectIdForSource(@Nullable String sourceId) {
    if (sourceId == null) return null;
    String pid = sourceToProject.get(sourceId);
    if (pid != null) return pid;
    if (matchReverse == null) {
      buildMatchReverse();
    }
    String matched = matchReverse.get(sourceId);
    if (matched == null) return null;
    return resolveToAccepted(matched);
  }

  /**
   * Defensive cycle guard for phase 2 parent updates. Returns true if setting
   * {@code projectId.parent_id = newParent} would close a cycle on {@code projectId} — either a
   * self-loop or a chain that walks back to {@code projectId} within {@link #MAX_WALK_DEPTH} hops.
   * Walks via the {@link #projectParents} cache and falls back to {@code NameUsageMapper.get} for
   * intermediate ancestors not yet cached, populating the cache as it goes.
   */
  private boolean wouldCreateCycle(int projectKey, String projectId, @Nullable String newParent, NameUsageMapper readNum) {
    if (newParent == null) return false;
    if (newParent.equals(projectId)) return true;
    Set<String> seen = new HashSet<>();
    String current = newParent;
    for (int i = 0; i < MAX_WALK_DEPTH; i++) {
      if (current == null) return false;
      if (current.equals(projectId)) return true;
      if (!seen.add(current)) return false; // pre-existing cycle that doesn't include projectId
      String parent;
      if (projectParents.containsKey(current)) {
        parent = projectParents.get(current);
      } else {
        NameUsageBase u = readNum.get(DSID.of(projectKey, current));
        if (u == null) return false;
        parent = u.getParentId();
        projectParents.put(current, parent);
      }
      current = parent;
    }
    return false;
  }

  /**
   * If {@code projectId} is (cached as) an accepted taxon, returns it. Otherwise walks
   * {@link #projectParents} up to {@link #MAX_WALK_DEPTH} hops looking for an accepted ancestor.
   * Unmatched ancestors that aren't in {@link #projectStatuses} are trusted as accepted (the
   * {@code parent_id} of a synonym in the project should always point at an accepted taxon by the
   * data model). Returns {@code null} on cycles or when the walk runs out of cached parents.
   */
  private @Nullable String resolveToAccepted(String projectId) {
    TaxonomicStatus st = projectStatuses.get(projectId);
    if (st == null || st.isTaxon()) {
      // not a known synonym in our match cache — trust it's accepted (data model invariant)
      return projectId;
    }
    Set<String> seen = new HashSet<>();
    String current = projectId;
    for (int i = 0; i < MAX_WALK_DEPTH; i++) {
      if (!seen.add(current)) return null; // cycle in pre-existing data
      String parent = projectParents.get(current);
      if (parent == null) return null; // walked off the cached chain
      TaxonomicStatus parentStatus = projectStatuses.get(parent);
      if (parentStatus == null || parentStatus.isTaxon()) {
        // either an unmatched usage (trusted accepted by invariant) or an accepted match
        return parent;
      }
      current = parent;
    }
    return null;
  }

  /**
   * Phase 3: for every project taxon that ends up accepted and matched to the source — i.e. the
   * higher taxa imported in phase 1 plus any matched project usage that is currently accepted
   * after phase 2 — copies the source's synonyms into the project, attached to the project's
   * accepted taxon and tagged with this sector's key.
   *
   * <p>Synonyms whose source id is already mapped to a project usage (either via an existing
   * matched project usage or via an imported ancestor) are skipped to avoid creating duplicates.
   *
   * <p>Pre-existing project synonyms of the same accepted taxa are not touched: they don't carry
   * this sector's key, so {@code deleteBySector} will leave them alone on re-runs.
   */
  private void copySynonymies() throws Exception {
    LOG.info("HierarchySync phase 3 (synonymy copy) for sector {}", sectorKey);
    if (projectMatches.isEmpty() && sourceToProject.isEmpty()) {
      LOG.info("Hierarchy sector {}: phase 3 skipped — no matches/ancestors", sectorKey);
      return;
    }

    final int projectKey = sectorKey.getDatasetKey();
    final String sourceScope = scopeResolver != null ? scopeResolver.resolve(sourceDatasetKey) : null;

    // Build the universe of (projectAcceptedId -> sourceAcceptedId) pairs:
    //   (a) every ancestor imported in phase 1 — all accepted by construction
    //   (b) every matched project usage whose status (after phase 2) is accepted
    Map<String, String> acceptedPairs = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : sourceToProject.entrySet()) {
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

    // source ids that are already represented in the project — skip them when copying synonyms
    Set<String> alreadyMappedSourceIds = new HashSet<>(projectMatches.size() + sourceToProject.size());
    alreadyMappedSourceIds.addAll(projectMatches.values());
    alreadyMappedSourceIds.addAll(sourceToProject.keySet());

    int copied = 0;
    int skipped = 0;
    try (SqlSession readSession = factory.openSession(true);
         SqlSession batch = factory.openSession(ExecutorType.BATCH, false)) {
      SynonymMapper readSyn = readSession.getMapper(SynonymMapper.class);
      NameUsageMapper writeNum = batch.getMapper(NameUsageMapper.class);
      int written = 0;

      for (Map.Entry<String, String> pair : acceptedPairs.entrySet()) {
        final String projectAcceptedId = pair.getKey();
        final String sourceAcceptedId = pair.getValue();

        List<Synonym> synonyms = readSyn.listByTaxon(DSID.of(sourceDatasetKey, sourceAcceptedId));
        for (Synonym syn : synonyms) {
          if (alreadyMappedSourceIds.contains(syn.getId())) {
            skipped++;
            continue;
          }
          final String origSourceId = syn.getId();

          // tag with sector before copying (CopyUtil propagates Taxon/Synonym.sectorKey to the Name)
          syn.setSectorKey(sector.getId());
          syn.setSectorMode(Sector.Mode.HIERARCHY);
          // null verbatim_source_key — see phase 1 insertAncestorsTopDown for rationale
          syn.setVerbatimSourceKey(null);
          if (syn.getName() != null) {
            syn.getName().setSectorKey(sector.getId());
            syn.getName().setSectorMode(Sector.Mode.HIERARCHY);
            syn.getName().setVerbatimSourceKey(null);
          }
          // copy. parent = the project's accepted taxon. No extension entities; reference linkage dropped.
          CopyUtil.copyUsage(batch, syn, DSID.of(projectKey, projectAcceptedId), user, Set.of(),
            ref -> null, refId -> null);

          if (sourceScope != null) {
            writeNum.addIdentifier(DSID.of(projectKey, syn.getId()), List.of(new Identifier(sourceScope, origSourceId)));
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

  public int getSourceDatasetKey() {
    return sourceDatasetKey;
  }
}
