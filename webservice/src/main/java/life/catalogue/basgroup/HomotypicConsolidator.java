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
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.BasionymGroup;
import life.catalogue.matching.authorship.BasionymSorter;

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
import com.google.common.collect.Sets;

public class HomotypicConsolidator {
  private static final Logger LOG = LoggerFactory.getLogger(HomotypicConsolidator.class);
  private final SqlSessionFactory factory;
  private final int datasetKey;
  private final DSID<String> dsid;
  private final List<SimpleName> families;
  private Map<String, Set<String>> basionymExclusions = new HashMap<>();
  private final AuthorComparator authorComparator;
  private final BasionymSorter basSorter;
  private final Function<LinneanNameUsage, Integer> priorityFunc;

  public static HomotypicConsolidator forAllFamilies(SqlSessionFactory factory, int datasetKey) {
    SectorPriority prio = new SectorPriority(datasetKey, factory);
    return HomotypicConsolidator.forAllFamilies(factory, datasetKey, prio::priority);
  }

  public static HomotypicConsolidator forAllFamilies(SqlSessionFactory factory, int datasetKey, Function<LinneanNameUsage, Integer> priorityFunc) {
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

  public static HomotypicConsolidator forFamilies(SqlSessionFactory factory, int datasetKey, List<SimpleName> families) {
    SectorPriority prio = new SectorPriority(datasetKey, factory);
    return HomotypicConsolidator.forFamilies(factory, datasetKey, families, prio::priority);
  }

  public static HomotypicConsolidator forFamilies(SqlSessionFactory factory, int datasetKey, List<SimpleName> families, Function<LinneanNameUsage, Integer> priorityFunc) {
    return new HomotypicConsolidator(factory, datasetKey, families, priorityFunc);
  }

  private HomotypicConsolidator(SqlSessionFactory factory, int datasetKey, List<SimpleName> families, Function<LinneanNameUsage, Integer> priorityFunc) {
    this.factory = factory;
    this.datasetKey = datasetKey;
    this.dsid = DSID.root(datasetKey);
    this.priorityFunc = priorityFunc;
    this.families = families;
    authorComparator = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
    basSorter = new BasionymSorter(authorComparator);
  }

  public void setBasionymExclusions(Map<String, Set<String>> basionymExclusions) {
    this.basionymExclusions = basionymExclusions;
  }

  public void consolidate() {
    LOG.info("Discover homotypic relations in {} accepted families", families.size());
    for (var fam : families) {
      consolidate(fam);
    }
  }

  /**
   * Goes through all usages of a given family and tries to discover basionyms by comparing the specific or infraspecific epithet and the authorships.
   * As we often see missing brackets from author names we must code defensively and allow several original names in the data for a single epithet.
   *
   * Each homotypic group is then consolidated so that only one accepted name remains.
   */
  private void consolidate(SimpleName family) {
    int newBasionyms = 0;
    int newRelations = 0;
    LOG.info("Detect homotypic relations within family {}", family.getLabel());
    final Map<String, List<LinneanNameUsage>> epithets = Maps.newHashMap();
    final Map<String, Set<String>> epithetBridges = Maps.newHashMap();
    final Set<String> ignore = basionymExclusions.get(family.getName());
    // key all names by their terminal epithet
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      NameRelationMapper nrm = session.getMapper(NameRelationMapper.class);
      NameMapper nm = session.getMapper(NameMapper.class);

      TreeTraversalParameter traversal = TreeTraversalParameter.dataset(datasetKey, family.getId());
      traversal.setSynonyms(true);
      PgUtils.consume(()->num.processTree(traversal, false), nuBIG -> {
        // configured to be ignored?
        if (ignore != null && ignore.contains(nuBIG.getName().getTerminalEpithet())) {
          LOG.info("Ignore epithet {} in family {} because of configs", nuBIG.getName().getTerminalEpithet(), family);
        } else if (nuBIG.getName().getType() == NameType.OTU || nuBIG.getName().getRank().isSupraspecific() || nuBIG.getName().isAutonym()){
          // ignore all supra specific names, autonyms and unparsed OTUs
        } else {
          // we transform it into a smaller object as we keep quite a few of those in memory
          // consider to implelemt a native mapper method to preocess the tree
          final LinneanNameUsage nu = new LinneanNameUsage(nuBIG);
          String epithet = SciNameNormalizer.stemEpithet(nu.getTerminalEpithet());
          if (!epithets.containsKey(epithet)) {
            epithets.put(epithet, Lists.newArrayList(nu));
          } else {
            epithets.get(epithet).add(nu);
          }
          // now check if a homotypic relation exists already that reaches out to some other epithet, e.g. due to gender changes
          var nids = nrm.listRelatedNameIDs(dsid.id(nu.getNameId()), NomRelType.HOMOTYPIC_RELATIONS);
          nids.remove(nu.getNameId());
          if (!nids.isEmpty()) {
            for (Name bas : nm.listByIds(datasetKey, Set.copyOf(nids))) {
              String epithet2 = SciNameNormalizer.stemEpithet(bas.getTerminalEpithet());
              if (epithet2 != null && !epithet2.equals(epithet)) {
                if (!epithetBridges.containsKey(epithet)) {
                  epithetBridges.put(epithet, Sets.newHashSet(epithet2));
                } else {
                  epithetBridges.get(epithet).add(epithet2);
                }
              }
            }
          }
        }
      });
      LOG.debug("{} distinct epithets found in family {}", epithets.size(), family.getLabel());
    }

    // merge epithet groups based on existing basionym relations, catching some gender changes
    LOG.debug("{} epithets are connected with explicit basionym relations", epithetBridges.size());
    for (Map.Entry<String, Set<String>> bridge : epithetBridges.entrySet()) {
      if (epithets.containsKey(bridge.getKey())) {
        var usages = epithets.get(bridge.getKey());
        for (String epi2 : bridge.getValue()) {
          if (epithets.containsKey(epi2)) {
            LOG.debug("Merging {} usages of epithet {} into epithet group {}", epithets.get(epi2).size(), epi2, bridge.getKey());
            usages.addAll(epithets.remove(epi2));
          }
        }
      }
    }

    // now compare authorships for each epithet group
    for (var epithetGroup : epithets.entrySet()) {
      var groups = basSorter.groupBasionyms(epithetGroup.getValue(), a -> a);
      // go through groups and persistent basionym relations where needed
      try (SqlSession session = factory.openSession(true)) {
        NameRelationMapper nrm = session.getMapper(NameRelationMapper.class);
        for (var group : groups) {
          // we only need to process groups that contain recombinations
          if (group.hasRecombinations()) {
            // if we have a basionym creating relations is straight forward
            LinneanNameUsage basionym = null;
            if (group.hasBasionym()) {
              basionym = group.getBasionym();
              for (var u : group.getRecombinations()) {
                if (createRelationIfNotExisting(u, basionym, NomRelType.BASIONYM, nrm)) {
                  newRelations++;
                }
              }
            } else {
              // pick any name and create homotypic relations instead of basionym ones
              var hom = group.getRecombinations().get(0);
              for (var u : group.getRecombinations()) {
                if (u != hom && createRelationIfNotExisting(u, hom, NomRelType.HOMOTYPIC, nrm)) {
                  newRelations++;
                }
              }
            }
          }
          // finally make sure we only have one accepted name!
          consolidate(family, group);
        }
      }
    }
    LOG.info("Discovered {} new basionym relations and created {} basionym placeholders in family {}", newRelations, newBasionyms, family);
  }

  private boolean createRelationIfNotExisting(LinneanNameUsage from, LinneanNameUsage to, NomRelType relType, NameRelationMapper mapper) {
    if (!mapper.exists(datasetKey, from.getNameId(), to.getNameId(), relType)) {
      var nr = new NameRelation();
      nr.setDatasetKey(datasetKey);
      nr.setType(relType);
      nr.setNameId(from.getNameId());
      nr.setRelatedNameId(to.getNameId());
      nr.setCreatedBy(Users.RELEASER);
      nr.setModifiedBy(Users.RELEASER);
      mapper.create(nr);
      return true;
    }
    return false;
  }

  /**
   * Make sure we only have at most one accepted name for each homotypical basionym group!
   * An entire group can consist of synonyms without a problem, but they must all refer to the same accepted name.
   * If a previously accepted name needs to be turned into a synonym it will be made a homotypic synonym.
   * <p>
   * As we merge names from different taxonomies it is possible there are multiple accepted names (maybe via a synonym relation) in such a group.
   * We always stick to the first combination with the highest priority and make all others
   * a) synonyms of this if it is accepted
   * b) synonyms of the primary's accepted name if it was a synonym itself
   * <p>
   * In case of conflicting accepted names we also flag these names with CONFLICTING_BASIONYM_COMBINATION
   */
  private void consolidate(SimpleName family, BasionymGroup<LinneanNameUsage> group) {
    if (group.size() > 1) {
      LOG.info("Consolidate homotypic group {} in family {}", group.getEpithet(), family);
      // we stick to the first combination with the highest priority and make all others
      //  a) synonyms of this if it is accepted
      //  b) synonyms of the primary's accepted name if it was a synonym itself
      // if there are several usages with the same priority select one according to some defined rules
      var all = group.getAll();
      final LinneanNameUsage primary = findPrimaryUsage(all);
      if (primary==null) {
        // we did not find a usage to trust. skip, but mark names with issues
        try (SqlSession session = factory.openSession(false)) {
          VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
          for (var u : all) {
            vsm.addIssue(dsid.id(u.getId()), Issue.HOMOTYPIC_MULTI_ACCEPTED);
          }
          session.commit();
        }
        return;
      }

      // get the accepted usage in case of synonyms
      final var accepted = primary.getStatus().isSynonym() ? load(primary.getParentId()) : primary;
      try (SqlSession session = factory.openSession(false)) {
        TaxonMapper tm = session.getMapper(TaxonMapper.class);

        if (LOG.isDebugEnabled()) {
          LOG.debug("Consolidating homotypic group with {} primary usage {}: {}", primary.getStatus(), primary.getLabel(), names(group.getAll()));
        }
        Set<String> parents = tm.classificationSimple(dsid.id(accepted.getParentId())).stream().map(SimpleName::getId).collect(Collectors.toSet());
        for (LinneanNameUsage u : group.getAll()) {
          if (u.equals(primary)) continue;
          if (parents.contains(u.getId())) {
            LOG.debug("Exclude parent {} from basionym consolidation of {}", u.getLabel(), primary.getLabel());

          } else {
            SimpleName previousParent = loadSN(u.getParentId());
            if (previousParent != null) {
              //TODO: add to usage remarks
              LOG.debug("Originally was treated as {} {} {}", u.getStatus(), u.getStatus().isSynonym() ? "of" : "taxon within", previousParent.getLabel());
            }
            convertToSynonym(u, accepted, Issue.CONFLICTING_BASIONYM_COMBINATION, session);
          }
        }
        session.commit();
      }
    }
  }

  /**
   * Converts the given taxon to a synonym of the given accepted usage.
   * All included descendants, both synonyms and accepted children, are also changed to become synonyms of the accepted.
   *  @param u
   * @param accepted
   * @param issue
   * @param session
   */
  public void convertToSynonym(LinneanNameUsage u, LinneanNameUsage accepted, @Nullable Issue issue, SqlSession session) {
    VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);

    if (u.getRank().isGenusOrSuprageneric()) {
      // pretty high ranks, warn!
      LOG.warn("Converting {} into a synonym of {}", u, accepted);
    } else {
      LOG.info("Convert {} into a synonym of {}", u, accepted);
    }
    // convert to synonym, removing old parent relation
    // change status
    u.setStatus(TaxonomicStatus.SYNONYM);
    if (issue != null) {
      vsm.addIssue(dsid.id(u.getId()), issue);
    }

    // synonymize all descendants!
    TreeTraversalParameter treeParams = TreeTraversalParameter.dataset(datasetKey, u.getId());
    treeParams.setSynonyms(true);
    try (var cursor = num.processTreeSimple(treeParams)) {
      for (var sn : cursor) {
        LOG.info("Also convert descendant {} into a synonym of {}", sn, accepted);
        num.updateParentAndStatus(dsid.id(sn.getId()), accepted.getId(), TaxonomicStatus.SYNONYM, Users.HOMOTYPIC_GROUPER);
      }
      // persist usage instance changes
      num.updateParentId(dsid.id(u.getId()), accepted.getId(), Users.HOMOTYPIC_GROUPER);
    } catch (IOException e) {
      LOG.error("Failed to traverse descendants of "+u.getLabel(), e);
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
  private LinneanNameUsage findPrimaryUsage(List<LinneanNameUsage> group) {
    if (group == null || group.isEmpty()) {
      return null;
    }
    // a single usage only
    if (group.size() == 1) {
      return group.get(0);
    }
    // keep shrinking this list until we get one!
    List<LinneanNameUsage> candidates = new ArrayList<>();

    // 1. by sector priority
    Integer highestPriority = null;
    for (LinneanNameUsage u : group) {
      Integer priority = priorityFunc.apply(u);
      if (Objects.equals(priority, highestPriority)) {
        candidates.add(u);
      } else if (priority != null && highestPriority == null || priority < highestPriority) {
        highestPriority = priority;
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
          LinneanNameUsage nu = CollectionUtils.find(group, u -> u.getId().equals(id));
          if (nu != null) {
            accepted.add(nu);
          } else {
            // load from db - might be the accepted name of a synonym
            accepted.add(load(id));
          }
        }
        LinneanNameUsage primary = CollectionUtils.findSingle(accepted, a -> a.getStatus()==TaxonomicStatus.ACCEPTED);
        if (primary != null) {
          LOG.debug("Prefer single accepted {} in basionym group with {} additional doubtful names out of {} usages from the most trusted sector {}",
            primary.getLabel(), accCounts.size()-1, candidates.size(), sectorKey);
          return primary;
        }
        LOG.info("Skip basionym group {} with {} accepted names out of {} usages from the most trusted sector {}",
          candidates.get(0).getLabel(), accCounts.size(), candidates.size(), sectorKey);
        return null;
      }
    }

    return candidates.get(0);
  }

  private LinneanNameUsage load(String id) {
    try (SqlSession session = factory.openSession()) {
      var nub = session.getMapper(NameUsageMapper.class).get(DSID.of(datasetKey, id));
      return new LinneanNameUsage(nub);
    }
  }

  private SimpleName loadSN(String id) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(NameUsageMapper.class).getSimple(DSID.of(datasetKey, id));
    }
  }
}
