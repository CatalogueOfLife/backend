package life.catalogue.basgroup;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import it.unimi.dsi.fastutil.Pair;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.collection.CountMap;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.BasionymGroup;
import life.catalogue.matching.authorship.BasionymSorter;

import org.apache.ibatis.annotations.Param;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class HomotypicConsolidator {
  private static final Logger LOG = LoggerFactory.getLogger(HomotypicConsolidator.class);
  private static final List<TaxonomicStatus> STATUS_ORDER = List.of(TaxonomicStatus.ACCEPTED, TaxonomicStatus.PROVISIONALLY_ACCEPTED, TaxonomicStatus.SYNONYM, TaxonomicStatus.AMBIGUOUS_SYNONYM);
  private static final Comparator<LinneanNameUsage> PREFERRED_STATUS_ORDER = Comparator.comparing(u -> STATUS_ORDER.indexOf(u.getStatus()));

  private final SqlSessionFactory factory;
  private final int datasetKey;
  private final DSID<String> dsid;
  private final List<SimpleName> taxa;
  private Map<String, Set<String>> basionymExclusions = new HashMap<>();
  private final AuthorComparator authorComparator;
  private final BasionymSorter basSorter;
  private final Function<LinneanNameUsage, Integer> priorityFunc;
  private int synCounter;
  private Map<String, LinneanNameUsage> usages; // lookup by id for each taxon group being consolidated

  /**
   * @return a consolidator that will group an entire dataset family by family
   */
  public static HomotypicConsolidator entireDataset(SqlSessionFactory factory, int datasetKey) {
    SectorPriority prio = new SectorPriority(datasetKey, factory);
    return HomotypicConsolidator.entireDataset(factory, datasetKey, prio::priority);
  }

  public static HomotypicConsolidator entireDataset(SqlSessionFactory factory, int datasetKey, Function<LinneanNameUsage, Integer> priorityFunc) {
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

  public static HomotypicConsolidator forTaxa(SqlSessionFactory factory, int datasetKey, List<SimpleName> taxa, Function<LinneanNameUsage, Integer> priorityFunc) {
    return new HomotypicConsolidator(factory, datasetKey, taxa, priorityFunc);
  }

  private HomotypicConsolidator(SqlSessionFactory factory, int datasetKey, List<SimpleName> taxa, Function<LinneanNameUsage, Integer> priorityFunc) {
    this.factory = factory;
    this.datasetKey = datasetKey;
    this.dsid = DSID.root(datasetKey);
    this.priorityFunc = priorityFunc;
    this.taxa = taxa;
    authorComparator = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
    basSorter = new BasionymSorter(authorComparator);
  }

  public void setBasionymExclusions(Map<String, Set<String>> basionymExclusions) {
    this.basionymExclusions = basionymExclusions;
  }

  public void consolidate() {
    LOG.info("Discover homotypic relations in {} accepted taxa of dataset {}", taxa.size(), datasetKey);
    for (var tax : taxa) {
      consolidate(tax);
    }
  }

  /**
   * Goes through all usages of a given parent taxon and tries to discover basionyms by comparing the specific or infraspecific epithet and the authorships.
   * As we often see missing brackets from author names we must code defensively and allow several original names in the data for a single epithet.
   *
   * Each homotypic group is then consolidated so that only one accepted name remains.
   */
  private void consolidate(SimpleName tax) {
    synCounter = 0;
    int newBasionyms = 0;
    int newBasionymRelations = 0;
    int newHomotypicRelations = 0;
    int newSpellingRelations = 0;
    LOG.info("Detect homotypic relations within {}", tax);
    final Map<String, List<LinneanNameUsage>> epithets = Maps.newHashMap();
    final Set<String> ignore = basionymExclusions.get(tax.getName());
    // key all names by their terminal epithet
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);

      TreeTraversalParameter traversal = TreeTraversalParameter.dataset(datasetKey, tax.getId());
      traversal.setSynonyms(true);
      PgUtils.consume(()->num.processTree(traversal, false), nuBIG -> {
        // configured to be ignored?
        if (ignore != null && ignore.contains(nuBIG.getName().getTerminalEpithet())) {
          LOG.info("Ignore epithet {} in {} because of configs", nuBIG.getName().getTerminalEpithet(), tax);
        } else if (nuBIG.getName().getType() == NameType.OTU || nuBIG.getName().getRank().isSupraspecific() || nuBIG.getName().isAutonym()){
          // ignore all supra specific names, autonyms and unparsed OTUs
        } else {
          // we transform it into a smaller object as we keep quite a few of those in memory
          // consider to implelemt a native mapper method to preocess the tree
          final LinneanNameUsage nu = new LinneanNameUsage(nuBIG);
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

    // now compare authorships for each epithet group
    for (var epithetGroup : epithets.entrySet()) {
      var groups = basSorter.groupBasionyms(epithetGroup.getValue(), a -> a, this::flagMultipleBasionyms);
      // go through groups and persistent basionym relations where needed
      for (var group : groups) {
        try (SqlSession session = factory.openSession(false)) {
          NameRelationMapper nrm = session.getMapper(NameRelationMapper.class);
          // we only need to process groups that contain recombinations or duplicates
          if (group.hasRecombinations() || group.hasBasionymDuplicates()) {
            // if we have a basionym creating relations is straight forward
            LinneanNameUsage basionym = null;
            if (group.hasBasionym()) {
              basionym = group.getBasionym();
              for (var u : group.getRecombinations()) {
                if (createRelationIfNotExisting(u, basionym, NomRelType.BASIONYM, nrm)) {
                  newBasionymRelations++;
                }
              }
              for (var u : group.getBasionymDuplicates()) {
                if (createRelationIfNotExisting(basionym, u, NomRelType.SPELLING_CORRECTION, nrm)) {
                  newSpellingRelations++;
                }
              }
            } else {
              // pick any name and create homotypic relations instead of basionym ones
              var all = group.getAll();
              var hom = all.remove(0);
              for (var u : all) {
                if (createRelationIfNotExisting(u, hom, NomRelType.HOMOTYPIC, nrm)) {
                  newHomotypicRelations++;
                }
              }
            }
          }
          // finally make sure we only have one accepted name!
          session.commit();
        }
        // now make sure we only have a single accepted name
        consolidate(tax, group);
      }
    }
    LOG.info("Discovered {} new basionym, {} homotypic and {} spelling relations. Created {} basionym placeholders and converted {} taxa into synonyms in {}", newBasionymRelations, newHomotypicRelations, newSpellingRelations, newBasionyms, synCounter, tax);
    usages = null;
  }

  /**
   *
   * @param group first=originals, second=recombinations
   */
  private void flagMultipleBasionyms(Pair<List<LinneanNameUsage>, List<LinneanNameUsage>> group) {
    try (SqlSession session = factory.openSession(false)) {
      VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
      for (var u : group.first()) {
        vsm.addIssue(dsid.id(u.getId()), Issue.MULTIPLE_BASIONYMS);
        if (u.getStatus().isTaxon()) {
          vsm.addIssue(dsid.id(u.getId()), Issue.HOMOTYPIC_CONSOLIDATION_UNRESOLVED);
        }
      }
      for (var u : group.second()) {
        if (u.getStatus().isTaxon()) {
          vsm.addIssue(dsid.id(u.getId()), Issue.HOMOTYPIC_CONSOLIDATION_UNRESOLVED);
        }
      }
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
   *  1) prefer accepted over synonym, e.g. s.str vs s.l.
   *  2) if multiple synonyms (senso lato) with different accepted names exist, pick the synonym with the homotypic accepted name that has the same epithet.
   * <p>
   * In case we have duplicates of the basionym treat them just as recombinations that need to be consolidated and synonymised to the primary accepted name.
   *
   * @param tax taxon context of all groups
   * @param group homotypic group to consolidate
   */
  private void consolidate(SimpleName tax, BasionymGroup<LinneanNameUsage> group) {
    if (group.size() > 1) {
      LOG.info("Consolidate homotypic group {} with {} recombinations and basionym={} with {} duplicates in {}", group.getEpithet(), group.getRecombinations().size(), group.getBasionym(), group.getBasionymDuplicates().size(), tax);
      final LinneanNameUsage primary = findPrimaryUsage(group);
      if (primary==null) {
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

      // get the accepted usage in case of synonyms
      final var primaryAcc = primary.getStatus().isSynonym() ? load(primary.getParentId()) : primary;
      try (SqlSession session = factory.openSession(false)) {
        TaxonMapper tm = session.getMapper(TaxonMapper.class);

        if (LOG.isDebugEnabled()) {
          LOG.debug("Consolidating homotypic group with {} primary usage {}: {}", primary.getStatus(), primary.getLabel(), names(group.getAll()));
        }
        Set<String> parents = tm.classificationSimple(dsid.id(primaryAcc.getParentId())).stream().map(SimpleName::getId).collect(Collectors.toSet());
        for (LinneanNameUsage u : group.getAll()) {
          if (u.equals(primary)) continue;
          if (parents.contains(u.getId())) {
            LOG.debug("Exclude parent {} from basionym consolidation of {}", u.getLabel(), primary.getLabel());

          } else {
            convertToSynonym(u, primaryAcc, Issue.HOMOTYPIC_CONSOLIDATION, session);
          }
        }
        session.commit();
      }
    }
  }

  /**
   * Converts the given taxon to a synonym of the given accepted usage.
   * All included descendants, both synonyms and accepted children, are also changed to become synonyms of the accepted.
   *
   * The method also updates the already loaded group instances to reflect the status & parent changes.
   * @param u taxon to convert to synonym
   * @param accepted newly accepted parent of the new synonym
   * @param issue optional issue to flag
   */
  public void convertToSynonym(LinneanNameUsage u, LinneanNameUsage accepted, @Nullable Issue issue, SqlSession session) {
    VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);

    //TODO: enable the strict check below once it never fails!
    // Preconditions.checkArgument(accepted.getStatus().isTaxon(), String.format("Fail to convert usage %s into a synonym of the non accepted name %s [%s]", u.getLabel(), accepted.getLabel(), accepted.getStatus()));
    if (!accepted.getStatus().isTaxon()) {
      LOG.warn("Cannot convert usage {} into a synonym of the {} {}", u.getLabel(), accepted.getStatus(), accepted.getLabel());
      return;
    }
    if (u.getId().equals(accepted.getId())) {
      LOG.warn("Trying to convert {} into a synonym of itself is suspicious. Abort", u);
      return;
    }

    SimpleName previousParent = loadSN(u.getParentId());
    if(u.getStatus().isSynonym()) {
      LOG.info("Move synonym {} from {} to {}", u, previousParent, accepted);
    } else if (u.getRank().isGenusOrSuprageneric()) {
      // pretty high ranks, warn!
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

    // synonymize all descendants!
    TreeTraversalParameter treeParams = TreeTraversalParameter.dataset(datasetKey, u.getId());
    treeParams.setSynonyms(true);
    try (var cursor = num.processTreeSimple(treeParams)) {
      for (var sn : cursor) {
        if (sn.getId().equals(u.getId())) continue; // exclude root
        var newStatus = sn.getStatus().isSynonym() ? sn.getStatus() : TaxonomicStatus.SYNONYM;
        if(sn.getStatus().isSynonym()) {
          var prev = loadSN(sn.getParent());
          LOG.info("Also move descendant synonym {} from {} to {}", sn, prev, accepted);
        } else {
          LOG.info("Also convert descendant {} into a {} of {}", sn, newStatus, accepted);
        }
        updateParentAndStatus(sn.getId(), accepted.getId(), newStatus, num);
      }
      // persist usage instance changes
      updateParentAndStatus(u.getId(), accepted.getId(), TaxonomicStatus.SYNONYM, num);
    } catch (IOException e) {
      LOG.error("Failed to traverse descendants of "+u.getLabel(), e);
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

  /**
   * From a list of usages believed to be homotypic select the most trusted usage.
   * If there are multiple usages from the most trusted source:
   *  a) selected a random first one if they all point to the same accepted usage
   *  b) return NULL if the source contains multiple accepted usages - this is either a bad taxonomy in the source
   *  or we did a bad basionym detection and we would wrongly lump names.
   */
  @VisibleForTesting
  protected LinneanNameUsage findPrimaryUsage(BasionymGroup<LinneanNameUsage> group) {
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
    Integer currMinPriority = null;
    for (LinneanNameUsage u : group.getAll()) {
      Integer priority = priorityFunc.apply(u);
      if (Objects.equals(priority, currMinPriority)) {
        candidates.add(u);
      } else if (priority != null && (currMinPriority == null || priority < currMinPriority)) {
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
      if (accCounts.size()>1) {
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

        List<LinneanNameUsage> acceptedStrict = CollectionUtils.find(accepted, a -> a.getStatus()==TaxonomicStatus.ACCEPTED);
        if (acceptedStrict.size() == 1) {
          var primary = acceptedStrict.get(0);
          LOG.debug("Prefer single accepted {} in basionym group with {} additional doubtful names out of {} usages from the most trusted sector {}",
            primary.getLabel(), accCounts.size()-1, candidates.size(), sectorKey);
          return primary;
        } else if (acceptedStrict.size() > 1) {
          // try to find a single homotypic accepted name with the same epithet
          var primary = CollectionUtils.findSingle(acceptedStrict, u -> isMatching(group.getEpithet(), u));
          if (primary != null) {
            return primary;
          }
        }
        LOG.info("Skip basionym group {} with {} accepted names out of {} usages from the most trusted sector {}",
          candidates.get(0).getLabel(), accCounts.size(), candidates.size(), sectorKey);
        return null;

      } else if (candidates.size() > 1){
        // multiple candidates? Prefer accepted ones
        candidates.sort(PREFERRED_STATUS_ORDER);
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

  private SimpleName loadSN(String id) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(NameUsageMapper.class).getSimple(DSID.of(datasetKey, id));
    }
  }
}
