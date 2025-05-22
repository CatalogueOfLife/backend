package life.catalogue.basgroup;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.collection.CountMap;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.similarity.ScientificNameSimilarity;
import life.catalogue.matching.similarity.StringSimilarity;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import it.unimi.dsi.fastutil.Pair;

public class HomotypicConsolidator {
  private static final Logger LOG = LoggerFactory.getLogger(HomotypicConsolidator.class);
  private static final List<TaxonomicStatus> STATUS_ORDER = List.of(TaxonomicStatus.ACCEPTED, TaxonomicStatus.PROVISIONALLY_ACCEPTED, TaxonomicStatus.SYNONYM, TaxonomicStatus.AMBIGUOUS_SYNONYM);
  private static final Comparator<LinneanNameUsage> PREFERRED_STATUS_ORDER = Comparator.comparing(u -> STATUS_ORDER.indexOf(u.getStatus()));
  private static final Comparator<LinneanNameUsage> PREFERRED_STATUS_RANK_ORDER = PREFERRED_STATUS_ORDER.thenComparing(LinneanNameUsage::getRank);

  private final SqlSessionFactory factory;
  private final int datasetKey;
  private final List<SimpleName> taxa;
  private Map<String, Set<String>> basionymExclusions = new HashMap<>();
  private final AuthorComparator authorComparator;
  private final BasionymSorter<LinneanNameUsage> basSorter;
  private final ToIntFunction<LinneanNameUsage> priorityFunc;

  /**
   * @return a consolidator that will group an entire dataset family by family
   */
  public static HomotypicConsolidator entireDataset(SqlSessionFactory factory, int datasetKey) {
    SectorPriority prio = new SectorPriority(datasetKey, factory);
    return HomotypicConsolidator.entireDataset(factory, datasetKey, prio::priority);
  }

  public static HomotypicConsolidator entireDataset(SqlSessionFactory factory, int datasetKey, ToIntFunction<LinneanNameUsage> priorityFunc) {
    final List<SimpleName> families = new ArrayList<>();
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      for (TaxonomicStatus status : TaxonomicStatus.values()) {
        if (status.isTaxon()) {
          families.addAll(num.findSimple(datasetKey, null, status, Rank.FAMILY, null));
        }
      }
    }
    return new HomotypicConsolidator(factory, datasetKey, families, priorityFunc);
  }

  /**
   * @return a consolidator that will group names within each of the given taxa separately.
   */
  public static HomotypicConsolidator forTaxa(SqlSessionFactory factory, int datasetKey, List<SimpleName> taxa) {
    SectorPriority prio = new SectorPriority(datasetKey, factory);
    return HomotypicConsolidator.forTaxa(factory, datasetKey, taxa, prio::priority);
  }

  public static HomotypicConsolidator forTaxa(SqlSessionFactory factory, int datasetKey, List<SimpleName> taxa, ToIntFunction<LinneanNameUsage> priorityFunc) {
    return new HomotypicConsolidator(factory, datasetKey, taxa, priorityFunc);
  }

  private HomotypicConsolidator(SqlSessionFactory factory, int datasetKey, List<SimpleName> taxa, ToIntFunction<LinneanNameUsage> priorityFunc) {
    this.factory = factory;
    this.datasetKey = datasetKey;
    this.priorityFunc = priorityFunc;
    this.taxa = taxa;
    authorComparator = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
    basSorter = new BasionymSorter<>(authorComparator, priorityFunc);
  }

  public void setBasionymExclusions(Map<String, Set<String>> basionymExclusions) {
    this.basionymExclusions = basionymExclusions;
  }

  public void consolidate() {
    consolidate(4);
  }
  public void consolidate(int threads) {
    LOG.info("Discover homotypic relations in {} accepted taxa of dataset {}, using {} threads", taxa.size(), datasetKey, threads);
    var exec = Executors.newFixedThreadPool(threads, new NamedThreadFactory("ht-consolidator-worker"));
    for (var tax : taxa) {
      var task = new ConsolidatorTask(tax);
      exec.submit(task);
    }
    ExecutorUtils.shutdown(exec);
  }

  /**
   * Goes through all usages of a given parent taxon and tries to discover basionyms by comparing the specific or infraspecific epithet and the authorships.
   * As we often see missing brackets from author names we must code defensively and allow several original names in the data for a single epithet.
   * <p>
   * Each homotypic group is then consolidated so that only one accepted name remains.
   */
  private class ConsolidatorTask implements Runnable {
    private final SimpleName tax;
    private final DSID<String> dsid;
    private int synCounter;
    private Map<String, LinneanNameUsage> usages; // lookup by id for each taxon group being consolidated

    private ConsolidatorTask(SimpleName tax) {
      this.tax = tax;
      this.dsid = DSID.root(datasetKey);
    }

    @Override
    public void run() {
      synCounter = 0;
      int newBasionyms = 0;
      int newBasionymRelations = 0;
      int newHomotypicRelations = 0;
      int newSpellingRelations = 0;
      int newBasedOnRelations = 0;
      LOG.info("Detect homotypic relations within {}", tax);
      final Map<String, List<LinneanNameUsage>> epithets = Maps.newHashMap();
      final Set<String> ignore = basionymExclusions.get(tax.getName());
      // key all names by their normalised, terminal epithet
      try (SqlSession session = factory.openSession(true)) {
        NameUsageMapper num = session.getMapper(NameUsageMapper.class);

        TreeTraversalParameter traversal = TreeTraversalParameter.dataset(datasetKey, tax.getId());
        traversal.setSynonyms(true);
        PgUtils.consume(() -> num.processTreeLinneanUsage(traversal, false, false), nu -> {
          if (nu.getType() == NameType.OTU || nu.getRank().isSupraspecific() || nu.isAutonym()) {
            // ignore all supra specific names, autonyms and unparsed OTUs
          } else if (ignore != null && ignore.contains(nu.getTerminalEpithet())) {
            LOG.info("Ignore epithet {} in {} because of configs", nu.getTerminalEpithet(), tax);
          } else {
            String epithet = SciNameNormalizer.normalizeEpithet(nu.getTerminalEpithet());
            if (!epithets.containsKey(epithet)) {
              epithets.put(epithet, Lists.newArrayList(nu));
            } else {
              epithets.get(epithet).add(nu);
            }
          }
        });
        LOG.debug("{} distinct epithets found in {}", epithets.size(), tax);
      }

      // keep identity map of all usages
      usages = new HashMap<>();
      for (var lnus : epithets.values()) {
        for (var lnu : lnus) {
          usages.put(lnu.getId(), lnu);
        }
      }

      detectOrthVars(epithets);

      // now compare authorships for each epithet group
      for (var epithetGroup : epithets.entrySet()) {
        var groups = basSorter.groupBasionyms(tax.getCode(), epithetGroup.getKey(), epithetGroup.getValue(), a -> a, this::flagConsolidationIssue);
        // go through groups and persistent basionym relations where needed
        for (var group : groups) {
          // we only need to work on groups with at least 2 names
          if (group.size() > 1) {
            try (SqlSession session = factory.openSession(false)) {
              NameRelationMapper nrm = session.getMapper(NameRelationMapper.class);
              // create relations for basionym & variations + recombinations
              if (group.hasRecombinations() || group.hasBasionymVariations()) {
                // if we have a basionym creating relations is straight forward
                if (group.hasBasionym()) {
                  LinneanNameUsage basionym = group.getBasionym();
                  for (var u : group.getRecombinations()) {
                    if (createRelationIfNotExisting(u, basionym, NomRelType.BASIONYM, nrm)) {
                      newBasionymRelations++;
                    }
                  }
                  for (var u : group.getBasionymVariations()) {
                    if (createRelationIfNotExisting(basionym, u, NomRelType.SPELLING_CORRECTION, nrm)) {
                      newSpellingRelations++;
                    }
                  }
                } else {
                  // if no basionym exists it cannot have basionym variations, hence just pick the first recombination which must exist
                  var iter = group.getRecombinations().listIterator();
                  var hom = iter.next();
                  while (iter.hasNext()) {
                    var u = iter.next();
                    if (createRelationIfNotExisting(u, hom, NomRelType.HOMOTYPIC, nrm)) {
                      newHomotypicRelations++;
                    }
                  }
                }
              }
              // basedOn & variations + basedOnNameIDs
              if (group.hasBasedOn()) {
                LinneanNameUsage basedOn = group.getBasedOn();
                // create main based on relation to the primary name
                if (createRelationIfNotExisting(group.getPrimary(), basedOn, NomRelType.BASED_ON, nrm)) {
                  newBasedOnRelations++;
                }
                for (var u : group.getBasedOnVariations()) {
                  if (createRelationIfNotExisting(basedOn, u, NomRelType.SPELLING_CORRECTION, nrm)) {
                    newSpellingRelations++;
                  }
                }
              }
              session.commit();
            }
            // finally make sure we only have one accepted name!
            consolidate(group);
          } else {
            LOG.debug("Skip single name group {}", group);
          }
        }
      }
      LOG.info("Discovered {} new basionym, {} homotypic, {} based on and {} spelling relations. Created {} basionym placeholders and converted {} taxa into synonyms in {}",
        newBasionymRelations, newHomotypicRelations, newBasedOnRelations, newSpellingRelations, newBasionyms, synCounter, tax);
      usages = null;
    }

    private class EpithetIndex {
      final char initial;
      final int length;

      private EpithetIndex(String epithet) {
        this.initial = epithet.charAt(0);
        this.length = epithet.length();
      }

      public EpithetIndex(EpithetIndex other, int lengthDiff) {
        this.initial = other.initial;
        this.length = other.length + lengthDiff;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EpithetIndex)) return false;
        EpithetIndex that = (EpithetIndex) o;
        return initial == that.initial && length == that.length;
      }

      @Override
      public int hashCode() {
        return Objects.hash(initial, length);
      }
    }
    private void detectOrthVars(Map<String, List<LinneanNameUsage>> epithets) {
      // group all epithets by their first char and length so we can filter out unlikely matches to speed up clustering
      Map<EpithetIndex, List<String>> index = new HashMap<>();
      for (var epi : epithets.keySet()) {
        var key = new EpithetIndex(epi);
        var epis = index.computeIfAbsent(key, k -> new ArrayList<>());
        epis.add(epi);
      }
      for (var groupEntry : index.entrySet()) {
        var key = groupEntry.getKey();
        Set<String> epithetWindow = new HashSet<>(groupEntry.getValue());

        var keyMin = key.length > 1 ? new EpithetIndex(key, -1) : null;
        var keyMax = new EpithetIndex(key, 1);
        for (var k : new EpithetIndex[]{keyMin, keyMax}) {
          if (k != null && index.containsKey(k)) {
            epithetWindow.addAll(index.get(k));
          }
        }
        // for each group epithets with the same first character and similar length, try to spot orthographic variations
        clusterNames(epithets, epithetWindow);
      }
    }

    private class EpiLNU {
      final String epithet;
      final LinneanNameUsage lnu;

      private EpiLNU(String epithet, LinneanNameUsage lnu) {
        this.epithet = epithet;
        this.lnu = lnu;
      }

      public String toString() {
        return lnu.getLabel();
      }
    }

    /**
     * Cluster epithets in the window to find orthographic variants.
     * Require the authorship to be strictly the same, but allow epithet itself to slightly differ.
     *
     * @param epithets
     * @param epithetWindow
     */
    private void clusterNames(Map<String, List<LinneanNameUsage>> epithets, Set<String> epithetWindow) {
      StringSimilarity sim = new ScientificNameSimilarity();
      // cluster all full names having the same author and move them into one of the epithet boxes
      var usages = new ArrayList<EpiLNU>();
      for (var epi : epithetWindow) {
        usages.addAll(epithets.get(epi).stream()
          .map(u -> new EpiLNU(epi, u))
          .collect(Collectors.toList())
        );
      }

      final double THRESHOLD = 92;
      // build upper triangular matrix with similarity between all names
      final int size = usages.size();
      var matrix = new double[size][size];
      double max = 0;
      for (int x = 0; x<size; x++) {
        EpiLNU ux = usages.get(x);
        for (int y = x+1; y<size; y++) {
          EpiLNU uy = usages.get(y);
          // only calculate full distance if authorship matches up strictly
          var authEq = authorComparator.compareStrict(ux.lnu, uy.lnu);
          var dist = authEq ? sim.getSimilarity(ux.lnu.getScientificName(), uy.lnu.getScientificName()) : 0;
          max = Math.max(max, dist);
          matrix[x][y] = dist;
        }
      }

      if (max >= THRESHOLD) {
        // we can have several variants for a name, not just a pair
        System.out.println(matrix);
        Set<String> visited = new HashSet<>();
        for (int x = 0; x<size; x++) {
          for (int y = x+1; y<size; y++) {
            if (matrix[x][y] >= THRESHOLD) {
              EpiLNU u = usages.get(x);
              if (!visited.contains(u.lnu.getId())) {
                // new cluster!
                List<EpiLNU> cluster = new ArrayList<>();
                cluster.add(u);
                cluster.add(usages.get(y));
                // check for futher matches
                for (int y2 = y+1; y2<size; y2++) {
                  if (matrix[x][y2] >= THRESHOLD) {
                    EpiLNU u2 = usages.get(y2);
                    if (!visited.contains(u2.lnu.getId())) {
                      cluster.add(u2);
                    }
                  }
                }
                consolidateOrthVars(epithets, cluster);
                // remember, so we dont report the same names again
                visited.addAll(cluster.stream().map(e -> e.lnu.getId()).collect(Collectors.toSet()));
              }
            }
          }
        }
      }
    }

    private void consolidateOrthVars(Map<String, List<LinneanNameUsage>> epithets, List<EpiLNU> names) {
      EpiLNU primary = names.remove(0);
      var list = epithets.get(primary.epithet);
      for (var n : names) {
        if (!n.epithet.equals(primary.epithet)) {
          var iter = epithets.get(n.epithet).listIterator();
          while (iter.hasNext()) {
            var en = iter.next();
            if (n.lnu.getId().equals(en.getId())) {
              LOG.info("Found orthographic variant of {}: {}", primary.lnu, en);
              list.add(n.lnu);
              iter.remove();
            }
          }
        }
      }
    }

    private void flagConsolidationIssue(Pair<LinneanNameUsage, Issue> obj) {
      try (SqlSession session = factory.openSession(false)) {
        VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
        vsm.addIssue(dsid.id(obj.key().getId()), obj.value());
        session.commit();
      }
    }

    private boolean createRelationIfNotExisting(LinneanNameUsage from, LinneanNameUsage to, NomRelType relType, NameRelationMapper mapper) {
      if (!mapper.exists(datasetKey, from.getNameId(), to.getNameId(), relType)) {
        var nr = new NameRelation();
        nr.setDatasetKey(datasetKey);
        nr.setType(relType);
        nr.setNameId(from.getNameId());
        nr.setRelatedNameId(to.getNameId());
        nr.setCreatedBy(Users.HOMOTYPIC_GROUPER);
        nr.setModifiedBy(Users.HOMOTYPIC_GROUPER);
        mapper.create(nr);
        return true;
      }
      return false;
    }

    /**
     * Make sure we only have at most one accepted name for each homotypical basionym group!
     * An entire group can consist of synonyms without a problem and can also refer to different accepted names, e.g. with pro parte synonyms.
     * If a previously accepted name needs to be turned into a synonym it will be made an ambiguous synonym
     * if there are multiple accepted names existing for all synonyms, otherwise a regular synonym.
     * <p>
     * As we merge names from different taxonomies it is possible there are multiple accepted names (maybe via a synonym relation) in such a group.
     * We always stick to the first combination with the highest priority and make all others
     * a) synonyms of this if it is accepted
     * b) synonyms of the primary's accepted name if it was a synonym itself.
     * <p>
     * If there are several usages with the same priority select one according to these rules:
     * 1) prefer accepted over synonym, e.g. s.str vs s.l.
     * 2) if multiple synonyms (senso lato) with different accepted names exist, pick the synonym with the homotypic accepted name that has the same epithet.
     * <p>
     * In case we have duplicates of the basionym treat them just as recombinations that need to be consolidated and synonymised to the primary accepted name.
     *
     * @param group homotypic group to consolidate
     */
    private void consolidate(HomotypicGroup<LinneanNameUsage> group) {
      if (group.size() > 1) {
        LOG.info("Consolidate homotypic group {} {} with {} names in {}. Basionym={} and BasedOn={}", group.getEpithet(), group.getAuthorship(), group.size(), tax, group.getBasionym(), group.getBasedOn());
        final LinneanNameUsage primary = findPrimaryUsage(group);
        if (primary == null) {
          // we did not find a usage to trust. skip, but mark accepted names with issues
          try (SqlSession session = factory.openSession(false)) {
            VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
            for (var u : group.getAll()) {
              if (u.getStatus().isTaxon()) {
                vsm.addIssue(dsid.id(u.getId()), Issue.HOMOTYPIC_CONSOLIDATION_UNRESOLVED);
              }
            }
            session.commit();
          }
          return;
        }

        // get the accepted usage in case of synonyms - caution, this can now be an autonym that is happy to live with its accepted species
        final var primaryAcc = primary.getStatus().isSynonym() ? load(primary.getParentId()) : primary;
        // use the highest priority from either primary or the accepted usage of it if its different
        final int primaryPrio = Math.min(priorityFunc.applyAsInt(primary), priorityFunc.applyAsInt(primaryAcc));
        try (SqlSession session = factory.openSession(false)) {
          TaxonMapper tm = session.getMapper(TaxonMapper.class);
          var num = session.getMapper(NameUsageMapper.class);

          if (LOG.isDebugEnabled()) {
            LOG.debug("Consolidating homotypic group with {} primary usage {}: {}", primary.getStatus(), primary.getLabel(), names(group.getAll()));
          }
          Set<String> parents = Set.copyOf(tm.classificationIds(dsid.id(primaryAcc.getId())));
          for (LinneanNameUsage u : group.getAll()) {
            if (u.equals(primary)) continue;
            if (parents.contains(u.getId())) { // this should catch autonym cases with an accepted species above
              LOG.debug("Exclude parent {} from homotypic consolidation of {}", u.getLabel(), primary.getLabel());

            } else {
              final int prio = priorityFunc.applyAsInt(u);
              if (prio > primaryPrio) {
                convertToSynonym(u, primaryAcc, Issue.HOMOTYPIC_CONSOLIDATION, session);
                // delete synonym with identical name? We have moved all children and changed the usage to a synonym, so there are no related records any longer
                if (u.getLabel().equalsIgnoreCase(primaryAcc.getLabel())) {
                  delete(u, session);
                } else {
                  // does the accepted already have the exact same synonym?
                  var syns = num.listSimpleSynonyms(dsid.id(primaryAcc.getId()));
                  if (syns.stream().anyMatch(s -> !u.getId().equals(s.getId()) && u.getLabel().equalsIgnoreCase(s.getLabel()))) {
                    delete(u, session);
                  }
                }
              } else if (prio == primaryPrio) {
                LOG.debug("Same priority, keep usage: {}", u);
              } else {
                LOG.warn("Unexpected priorities. Keep usage: {}", u);
                addIssue(u, Issue.HOMOTYPIC_CONSOLIDATION_UNRESOLVED, session);
              }
            }
          }
          session.commit();
        }
      }
    }

    private void addIssue(LinneanNameUsage u, Issue issue, SqlSession session) {
      VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
      vsm.addIssue(dsid.id(u.getId()), issue);
    }

    private void delete(LinneanNameUsage u, SqlSession session) {
      VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      vsm.delete(dsid.id(u.getId()));
      num.delete(dsid);
    }

    /**
     * Converts the given taxon to a synonym of the given accepted usage.
     * All included descendants, both synonyms and accepted children, are also changed to become synonyms of the accepted.
     * <p>
     * The method also updates the already loaded group instances to reflect the status & parent changes.
     *
     * @param u        taxon to convert to synonym
     * @param accepted newly accepted parent of the new synonym
     * @param issue    optional issue to flag
     */
    public void convertToSynonym(LinneanNameUsage u, LinneanNameUsage accepted, @Nullable Issue issue, SqlSession session) {
      VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);

      if (!accepted.getStatus().isTaxon()) {
        LOG.warn("Cannot convert usage {} into a synonym of the {} {}", u.getLabel(), accepted.getStatus(), accepted.getLabel());
        return;
      }
      if (u.getId().equals(accepted.getId())) {
        LOG.warn("Trying to convert {} into a synonym of itself is suspicious. Abort", u);
        return;
      } else if (u.getParentId().equals(accepted.getId()) && u.getStatus().isSynonym()) {
        LOG.info("Trying to convert {} into a synonym of it's accepted name. Nothing to be done.", u);
        return;
      }

      SimpleName previousParent = loadSN(u.getParentId());
      if (u.getStatus().isSynonym()) {
        LOG.info("Move {} from {} to {}", u, previousParent, accepted);
      } else if (u.getRank().isGenusOrSuprageneric()) {
        // pretty high ranks, dont do that!
        LOG.warn("Trying to convert {} into a synonym of {}, but rank {} is too high. Abort", u, accepted, u.getRank());
        return;

      } else {
        LOG.info("Convert {} into a synonym of {}", u, accepted);
      }
      if (previousParent != null) {
        LOG.debug("Originally was treated as {} {} {}", u.getStatus(), u.getStatus().isSynonym() ? "of" : "taxon within", previousParent.getLabel());
      }

      // convert to synonym, removing old parent relation
      if (issue != null) {
        vsm.addIssue(dsid.id(u.getId()), issue);
      }

      // move all descendants!
      TreeTraversalParameter treeParams = TreeTraversalParameter.dataset(datasetKey, u.getId());
      treeParams.setSynonyms(true);
      try (var cursor = num.processTreeSimple(treeParams)) {
        for (var sn : cursor) {
          if (sn.getId().equals(u.getId())) continue; // exclude root
          if (sn.getParent().equals(accepted.getId())) continue; // the name was placed correctly already - how can that be?
          if (sn.getId().equals(accepted.getId())) {
            // avoid moving the main accepted usage - how can we even end up here?
            LOG.warn("Trying to move the main accepted name {} to become a child of itself. Avoid!", accepted);
            continue;
          }
          LOG.info("Also move descendant {} from {} to {}", sn, sn.getParent(), accepted);
          updateParent(sn.getId(), accepted.getId(), num);
        }
        // persist usage instance changes
        updateParentAndStatus(u.getId(), accepted.getId(), TaxonomicStatus.SYNONYM, num);
      } catch (IOException | PersistenceException e) {
        LOG.error("Failed to traverse descendants of " + u.getLabel(), e);
      }
    }

    private void updateParent(String id, String parentId, NameUsageMapper num) {
      num.updateParentId(dsid.id(id), parentId, Users.HOMOTYPIC_GROUPER);
      // track change in our memory instances too
      if (usages.containsKey(id)) {
        var u = usages.get(id);
        u.setParentId(parentId);
      }
    }

    private void updateParentAndStatus(String id, String parentId, TaxonomicStatus status, NameUsageMapper num) {
      num.updateParentAndStatus(dsid.id(id), parentId, status, Users.HOMOTYPIC_GROUPER);
      synCounter++;
      // track change in our memory instances too
      if (usages.containsKey(id)) {
        var u = usages.get(id);
        u.setStatus(status);
        u.setParentId(parentId);
      }
    }

    private String names(Collection<LinneanNameUsage> usages) {
      return usages.stream().map(LinneanNameUsage::getLabel).collect(Collectors.joining("; "));
    }

    private SimpleName loadSN(String id) {
      try (SqlSession session = factory.openSession()) {
        return session.getMapper(NameUsageMapper.class).getSimple(DSID.of(datasetKey, id));
      }
    }

  }


  /**
   * From a list of usages believed to be homotypic select the most trusted usage.
   * If there are multiple usages from the most trusted source:
   * a) selected a random first one if they all point to the same accepted usage
   * b) return NULL if the source contains multiple accepted usages - this is either a bad taxonomy in the source
   * or we did a bad basionym detection and we would wrongly lump names.
   */
  @VisibleForTesting
  protected LinneanNameUsage findPrimaryUsage(HomotypicGroup<LinneanNameUsage> group) {
    if (group == null || group.isEmpty()) {
      return null;
    }
    // a single usage only
    if (group.size() == 1) {
      return group.getAll().get(0);
    }
    // keep shrinking this list until we get one!
    List<LinneanNameUsage> candidates = new ArrayList<>();

    // 1. by sector priority
    int currMinPriority = Integer.MAX_VALUE;
    for (LinneanNameUsage u : group.getAll()) {
      int priority = priorityFunc.applyAsInt(u);
      if (priority == currMinPriority) {
        candidates.add(u);
      } else if (priority < currMinPriority) {
        currMinPriority = priority;
        candidates.clear();
        candidates.add(u);
      }
    }

    // now all usages originate from the same sector!
    if (candidates.size() > 1) {
      final Integer sectorKey = candidates.get(0).getSectorKey();
      // if all remaining usages point to the same accepted taxon it does not matter which we pick
      // otherwise log warning and do not group names further - we risk to have badly detected basionyms
      CountMap<String> accCounts = new CountMap<>();
      for (var u : candidates) {
        final var accID = u.getStatus().isSynonym() ? u.getParentId() : u.getId();
        accCounts.inc(accID);
      }
      if (accCounts.size() > 1) {
        // the same dataset contains multiple accepted. It is either:
        // a) taxonomically inconsistent
        // b) has pro parte synonyms
        // c) we did some bad basionym detection - better back off
        // d) there are doubtfully accepted taxa which we should maybe ignore
        List<LinneanNameUsage> accepted = new ArrayList<>();
        for (var id : accCounts.keySet()) {
          LinneanNameUsage nu = CollectionUtils.first(group.getAll(), u -> u.getId().equals(id));
          if (nu != null) {
            accepted.add(nu);
          } else {
            // load from db - might be the accepted name of a synonym
            accepted.add(load(id));
          }
        }

        List<LinneanNameUsage> acceptedStrict = CollectionUtils.find(accepted, a -> a.getStatus() == TaxonomicStatus.ACCEPTED);
        if (acceptedStrict.size() == 1) {
          var primary = acceptedStrict.get(0);
          LOG.debug("Prefer single accepted {} in homotypic group with {} additional doubtful names out of {} usages from the most trusted sector {}",
            primary.getLabel(), accCounts.size() - 1, candidates.size(), sectorKey);
          return primary;

        } else if (acceptedStrict.size() > 1) {
          // as we resolved synonyms to their accepted name we can now have autonyms.
          // If there are both the species and its autonym accepted prefer the species!
          var autonym = CollectionUtils.findSingle(acceptedStrict, FormattableName::isAutonym);
          if (autonym != null) {
            var species = CollectionUtils.findSingle(acceptedStrict, u -> !u.isTrinomial() && Objects.equals(autonym.getGenus(), u.getGenus()));
            if (species != null) {
              LOG.debug("Prefer accepted species {} over its autonym {}", species, autonym);
              acceptedStrict.remove(autonym);
            }
          }

          // try to find a single homotypic accepted name with the same epithet
          var primary = CollectionUtils.findSingle(acceptedStrict, u -> isMatching(group.getEpithet(), u));
          if (primary != null) {
            return primary;
          }
        }
        LOG.info("Skip basionym group {} with {} accepted names out of {} usages from the most trusted sector {}",
          candidates.get(0).getLabel(), accCounts.size(), candidates.size(), sectorKey);
        return null;

      } else if (candidates.size() > 1) {
        // multiple candidates? Prefer accepted ones and species over infraspecifics
        candidates.sort(PREFERRED_STATUS_RANK_ORDER);
      }
    }

    return candidates.get(0);
  }

  /**
   * @return true if the given name belongs to the group according to the terminal epithet
   */
  private boolean isMatching(String epithet, FormattableName name) {
    return epithet != null && SciNameNormalizer.normalizeEpithet(epithet).equals(SciNameNormalizer.normalizeEpithet(name.getTerminalEpithet()));
  }

  private LinneanNameUsage load(String id) {
    final var dsid = DSID.of(datasetKey, id);
    try (SqlSession session = factory.openSession()) {
      var nub = session.getMapper(NameUsageMapper.class).get(dsid);
      return new LinneanNameUsage(nub);

    } catch (Exception e) {
      LOG.error("Failed to load usage {}", dsid, e);
      throw new RuntimeException(e);
    }
  }

}
