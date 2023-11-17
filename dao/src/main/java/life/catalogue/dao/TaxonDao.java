package life.catalogue.dao;

import life.catalogue.api.exception.ArchivedException;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.SynonymException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.*;
import life.catalogue.db.NameProcessable;
import life.catalogue.db.PgUtils;
import life.catalogue.db.TaxonProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NameType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.Validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.LoadingCache;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class TaxonDao extends DatasetEntityDao<String, Taxon, TaxonMapper> {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonDao.class);
  private final NameUsageIndexService indexService;
  private final NameDao nameDao;
  private SectorDao sectorDao;

  /**
   * Warn: you must set a sector dao manually before using the TaxonDao.
   * We have circular dependency that cannot be satisfied with final properties through constructors
   */
  public TaxonDao(SqlSessionFactory factory, NameDao nameDao, NameUsageIndexService indexService, Validator validator) {
    super(true, factory, Taxon.class, TaxonMapper.class, validator);
    this.indexService = indexService;
    this.nameDao = nameDao;
  }

  public static void copyTaxon(SqlSession session, final Taxon t, final DSID<String> target, int user) {
    copyTaxon(session, t, target, user, Collections.emptySet());
  }

  public void setSectorDao(SectorDao sectorDao) {
    this.sectorDao = sectorDao;
  }

  /**
   * Copies the given source taxon into the dataset and under the given target parent.
   * The taxon and name source instance will be modified to represent the newly generated and finally persisted taxon.
   * The original id is retained and finally returned.
   * An optional set of associated entity types can be indicated to be copied too.
   *
   * The sectorKey found on the main taxon will also be applied to associated name, reference and other copied entities.
   * See {@link CatCopy#copyUsage} for details.
   *
   * @return the original source taxon id
   */
  public static void copyTaxon(SqlSession session, final Taxon t, final DSID<String> target, int user, Set<EntityType> include) {
    CatCopy.copyUsage(session, t, target, user, include, TaxonDao::devNull, TaxonDao::devNull);
  }

  /**
   * Returns a taxon with the specified key or throws:
   *  - a SynonymException in case the id belongs to a synonym
   *  - a NotFoundException if the id is no name usage at all
   */
  @Override
  public Taxon getOr404(DSID<String> key) {
    try {
      return super.getOr404(key);
    } catch (NotFoundException e) {
      // is it a synonym?
      try (SqlSession session = factory.openSession()) {
        SimpleName syn = session.getMapper(NameUsageMapper.class).getSimple(key);
        if (syn != null) {
          throw new SynonymException(key, syn.getParent());
        }
        // if it is a release, try if we have an archived version in the project
        var info = DatasetInfoCache.CACHE.info(key.getDatasetKey());
        if (info.origin.isRelease()) {
          var projectKey = DSID.of(info.sourceKey, key.getId());
          var anu= session.getMapper(ArchivedNameUsageMapper.class).get(projectKey);
          if (anu != null) {
            throw new ArchivedException(projectKey, anu);
          }
        }
        // rethrow the original 404
        throw e;
      }
    }
  }

  public ResultPage<Taxon> listRoot(Integer datasetKey, Page page) {
    try (SqlSession session = factory.openSession(false)) {
      Page p = page == null ? new Page() : page;
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      List<Taxon> result = tm.listRoot(datasetKey, p);
      return new ResultPage<>(p, result, () -> tm.countRoot(datasetKey));
    }
  }
  
  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(Taxon taxon) {
    return getSynonymy(taxon.getDatasetKey(), taxon.getId(), taxon.getName().getId());
  }

  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(int datasetKey, String taxonId, @Nullable String nameId) {
    try (SqlSession session = factory.openSession(false)) {
      NameRelationMapper nrm = session.getMapper(NameRelationMapper.class);
      SynonymMapper sm = session.getMapper(SynonymMapper.class);
      // load accepted name id if unknown
      if (nameId == null) {
        NameMapper nm = session.getMapper(NameMapper.class);
        nameId = nm.getNameIdByUsage(datasetKey, taxonId);
      }

      Synonymy syn = new Synonymy();
      var homotypicNamesIds = nrm.listRelatedNameIDs(DSID.of(datasetKey, nameId), NomRelType.HOMOTYPIC_RELATIONS);

      // now go through synonym usages and add to misapplied, heterotypic or skip if seen before
      List<HomGroup> heterotypics = new ArrayList<>();
      for (Synonym s : sm.listByTaxon(DSID.of(datasetKey, taxonId))) {
        if (TaxonomicStatus.MISAPPLIED == s.getStatus()) {
          syn.getMisapplied().add(s);
        } else if (homotypicNamesIds.contains(s.getName().getId())) {
          syn.getHomotypic().add(s);
        } else {
          syn.getHeterotypic().add(s);
          boolean found = false;
          for (var hg : heterotypics) {
            if (hg.nameIds.contains(s.getName().getId())) {
              found = true;
              hg.homotypic.add(s);
              break;
            }
          }
          if (!found) {
            var hg = new HomGroup(s, nrm.listRelatedNameIDs(DSID.of(datasetKey, s.getName().getId()), NomRelType.HOMOTYPIC_RELATIONS));
            heterotypics.add(hg);
            syn.getHeterotypicGroups().add(hg.homotypic);
          }
        }
      }
      // sort homotypic groups by year, then name
      syn.getHeterotypicGroups().forEach(Collections::sort);
      // finally sort the groups themselves by their first entry
      syn.getHeterotypicGroups().sort(Comparator.comparing(hg -> hg.get(0)));
      return syn;
    }
  }

  static class HomGroup {
    final List<Synonym> homotypic = new ArrayList<>();
    final Set<String> nameIds;

    HomGroup(Synonym synonym, Collection<String> nameIds) {
      this.nameIds = new HashSet<>(nameIds);
      homotypic.add(synonym);
    }
  }

  public UsageInfo getUsageInfo(DSID<String> key) {
    try (SqlSession session = factory.openSession(false)) {
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      return getUsageInfo(session, um.get(key));
    }
  }
  
  public UsageInfo getUsageInfo(final NameUsageBase usage) {
    try (SqlSession session = factory.openSession(false)) {
      return getUsageInfo(session, usage);
    }
  }

  public UsageInfo getUsageInfo(final SqlSession session, final NameUsageBase usage) {
    // main taxon object
    if (usage == null) {
      return null;
    }
    UsageInfo info = new UsageInfo(usage);
    fillUsageInfo(session, info, null, true, true, true, true, true, true,
      true, true, true, true, true);
    return info;
  }

  public VerbatimSource getSource(final DSID<String> key) {
    try (SqlSession session = factory.openSession(false)) {
      return session.getMapper(VerbatimSourceMapper.class).getWithSources(key);
    }
  }

  /**
   * @param info existing usage info with at least the usage property given so we can load the rest.
   */
  public void fillUsageInfo(final SqlSession session, final UsageInfo info,
                            LoadingCache<String, Reference> refCache,
                            boolean loadSource,
                            boolean loadSynonyms,
                            boolean loadDistributions,
                            boolean loadVernacular,
                            boolean loadMedia,
                            boolean loadTypeMaterial,
                            boolean loadTreatments,
                            boolean loadNameRelations,
                            boolean loadProperties,
                            boolean loadConceptRelations,
                            boolean loadSpeciesInteractions) {
    var usage = info.getUsage();
    final boolean isTaxon = usage.isTaxon();

    // all reference, name and taxon keys so we can select their details at the end
    Set<String> taxonIds = new HashSet<>();
    Set<String> nameIds = new HashSet<>();
    Set<String> refIds = new HashSet<>(usage.getReferenceIds());
    refIds.add(usage.getName().getPublishedInId());

    // synonyms
    if (isTaxon && loadSynonyms) {
      var syns = getSynonymy((Taxon)usage);
      info.setSynonyms(syns);
      info.getSynonyms().forEach(s -> {
        refIds.add(s.getName().getPublishedInId());
        refIds.addAll(s.getReferenceIds());
      });
    }

    // source
    if (loadSource) {
      var d = DatasetInfoCache.CACHE.info(usage.getDatasetKey());
      if (d.origin.isProjectOrRelease()) {
        // only managed and releases have this table - we'll yield an exception for external datasets!
        info.setSource(session.getMapper(VerbatimSourceMapper.class).getWithSources(usage));
      }
    }

    // treatment
    if (loadTreatments) {
      TreatmentMapper trm = session.getMapper(TreatmentMapper.class);
      info.setTreatment(trm.get(usage));
    }

    // usage name relations
    if (loadNameRelations) {
      NameRelationMapper mapper = session.getMapper(NameRelationMapper.class);
      final var rels = new ArrayList<NameUsageRelation>();
      var urels = mapper.listUsageRelByName(usage.getName());
      if (urels != null) {
        urels.forEach(urel -> urel.setUsageId(usage.getId()));
        rels.addAll(urels);
      }
      urels = mapper.listUsageRelByRelatedName(usage.getName());
      if (urels != null) {
        urels.forEach(urel -> urel.setRelatedUsageId(usage.getId()));
        rels.addAll(urels);
      }
      if (!rels.isEmpty()) {
        info.setNameRelations(rels);
        rels.forEach(r -> {
          refIds.add(r.getReferenceId());
          nameIds.add(r.getNameId());
          nameIds.add(r.getRelatedNameId());
        });
      }
    }

    // add all type material
    if (loadTypeMaterial) {
      TypeMaterialMapper tmm = session.getMapper(TypeMaterialMapper.class);
      info.getTypeMaterial().put(usage.getName().getId(), tmm.listByName(usage.getName()));
      if (info.getSynonyms() != null) {
        info.getSynonyms().forEach(s -> info.getTypeMaterial().put(s.getName().getId(), tmm.listByName(s.getName())));
      }
      info.getTypeMaterial().values().forEach(
        types -> types.forEach(
          t -> refIds.add(t.getReferenceId())
        )
      );
    }

    // add all supplementary taxon infos
    if (isTaxon) {
      if (loadDistributions) {
        DistributionMapper dim = session.getMapper(DistributionMapper.class);
        info.setDistributions(
          dim.listByTaxon(usage).stream()
             // replace will enums so we also get titles and other props - this is too hard to do in mybatis
             .map(d -> {
               if (d.getArea().getGazetteer() == Gazetteer.ISO) {
                 Country.fromIsoCode(d.getArea().getId()).ifPresent(c ->
                   d.setArea(new AreaImpl(c))
                 );

               } else if (d.getArea().getGazetteer() == Gazetteer.TDWG) {
                 d.setArea(TdwgArea.of(d.getArea().getId()));

               } else if (d.getArea().getGazetteer() == Gazetteer.LONGHURST) {
                 d.setArea(LonghurstArea.of(d.getArea().getId()));
               }
               return d;
             })
             .filter(d -> d.getArea() != null)
             .collect(Collectors.toList())
        );
        info.getDistributions().forEach(d -> refIds.add(d.getReferenceId()));
      }

      if (loadMedia) {
        MediaMapper mm = session.getMapper(MediaMapper.class);
        info.setMedia(mm.listByTaxon(usage));
        info.getMedia().forEach(m -> refIds.add(m.getReferenceId()));
      }

      if (loadVernacular) {
        VernacularNameMapper vm = session.getMapper(VernacularNameMapper.class);
        info.setVernacularNames(vm.listByTaxon(usage));
        info.getVernacularNames().forEach(d -> refIds.add(d.getReferenceId()));
      }

      if (loadProperties) {
        var mapper = session.getMapper(TaxonPropertyMapper.class);
        info.setProperties(mapper.listByTaxon(usage));
        info.getProperties().forEach(p -> refIds.add(p.getReferenceId()));
      }

      if (loadConceptRelations) {
        TaxonConceptRelationMapper mapper = session.getMapper(TaxonConceptRelationMapper.class);
        info.setConceptRelations(mapper.listByTaxon(usage));
        info.getConceptRelations().addAll(mapper.listByRelatedTaxon(usage));
        info.getConceptRelations().forEach(r -> {
          refIds.add(r.getReferenceId());
          taxonIds.add(r.getTaxonId());
          taxonIds.add(r.getRelatedTaxonId());
        });
      }

      if (loadSpeciesInteractions) {
        SpeciesInteractionMapper mapper = session.getMapper(SpeciesInteractionMapper.class);
        info.setSpeciesInteractions(mapper.listByTaxon(usage));
        info.getSpeciesInteractions().addAll(mapper.listByRelatedTaxon(usage));
        info.getSpeciesInteractions().forEach(r -> {
          refIds.add(r.getReferenceId());
          taxonIds.add(r.getTaxonId());
          taxonIds.add(r.getRelatedTaxonId());
        });
      }
    }

    // make sure we did not add null by accident
    refIds.remove(null);
    nameIds.remove(null);
    taxonIds.remove(null);

    if (!refIds.isEmpty()) {
      if (refCache == null) {
        ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
        List<Reference> refs = rm.listByIds(usage.getDatasetKey(), refIds);
        info.addReferences(refs);
      } else {
        info.setReferences(refCache.getAll(refIds));
      }
    }

    if (!nameIds.isEmpty()) {
      NameMapper mapper = session.getMapper(NameMapper.class);
      List<Name> names = mapper.listByIds(usage.getDatasetKey(), nameIds);
      info.addNames(names);
    }

    if (!taxonIds.isEmpty()) {
      TaxonMapper mapper = session.getMapper(TaxonMapper.class);
      List<Taxon> taxa = mapper.listByIds(usage.getDatasetKey(), taxonIds);
      info.addTaxa(taxa);
    }
  }

  /**
   * Creates a new Taxon including a name instance if no name id is already given.
   *
   * @param t
   * @param user
   * @return newly created taxon id
   */
  @Override
  public DSID<String> create(Taxon t, int user) {
    return create(t, user, true);
  }

  /**
   * Creates a new Taxon including a name instance if no name id is already given.
   * If desired the search index is updated too.
   * @param t
   * @param user
   * @param indexImmediately if true the search index is also updated
   * @return newly created taxon id
   */
  public DSID<String> create(Taxon t, int user, boolean indexImmediately) {
    t.setStatusIfNull(TaxonomicStatus.ACCEPTED);
    if (t.getStatus().isSynonym()) {
      throw new IllegalArgumentException("Taxa cannot have a synonym status");
    }

    try (SqlSession session = factory.openSession(false)) {
      final int datasetKey = t.getDatasetKey();
      Name n = t.getName();
      if (n.getId() == null) {
        if (!n.isParsed() && StringUtils.isBlank(n.getScientificName())) {
          throw new IllegalArgumentException("Existing nameId, scientificName or atomized name field required");
        }
        newKey(n);
        n.setOrigin(Origin.USER);
        n.applyUser(user);
        // make sure we use the same dataset
        n.setDatasetKey(datasetKey);
        // does the name need parsing?
        parseName(n);
        nameDao.create(n, user);
      } else {
        Name nExisting = nameDao.get(DSID.of(datasetKey, n.getId()));
        if (nExisting == null) {
          throw new IllegalArgumentException("No name exists with ID " + n.getId() + " in dataset " + datasetKey);
        }
      }
      
      newKey(t);
      t.setOrigin(Origin.USER);
      t.applyUser(user);
      session.getMapper(TaxonMapper.class).create(t);
      
      session.commit();

      // create taxon in ES
      if (indexImmediately) {
        indexService.update(t.getDatasetKey(), List.of(t.getId()));
      }
      return t;

    }
  }
  
  static void parseName(Name n) {
    if (!n.isParsed()) {
      try {
        NameParser.PARSER.parse(n, VerbatimRecord.VOID);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // reset flag
      }

    } else {
      if (n.getType() == null) {
        n.setType(NameType.SCIENTIFIC);
      }
      n.rebuildScientificName();
      if (n.getAuthorship() == null) {
        n.rebuildAuthorship();
      }
    }
  }

  @Override
  protected void updateBefore(Taxon obj, Taxon old, int user, TaxonMapper mapper, SqlSession session) {
    // only allow parent changes if they are not part of a sector
    if (!Objects.equals(old.getParentId(), obj.getParentId()) && old.getSectorKey() != null) {
      throw new IllegalArgumentException("You cannot move a taxon which is part of sector " + obj.getSectorKey());
    }
  }

  @Override
  protected boolean updateAfter(Taxon t, Taxon old, int user, TaxonMapper tm, SqlSession session, boolean keepSessionOpen) {
    // has parent, i.e. classification been changed ?
    if (!Objects.equals(old.getParentId(), t.getParentId())) {
      updatedParentCacheUpdate(tm, t, t.getParentId(), old.getParentId());
    }
    session.commit();
    if (!keepSessionOpen) {
      session.close();
    }
    // update single taxon in ES
    indexService.update(t.getDatasetKey(), List.of(t.getId()));
    return keepSessionOpen;
  }

  /**
   * Updates cached information in both postgres and elastic when a parent id of a taxon has changed.
   * The actual parentID change is expected to have happened already in the database!
   * @param t the taxon that has been assigned a new parentID
   * @param newParentId the newly assigned parentID (already set as the taxons parent_id)
   * @param oldParentId the former parentID
   */
  private void updatedParentCacheUpdate(TaxonMapper tm, DSID<String> t, String newParentId, String oldParentId){
    // migrate entire DatasetSectors from old to new
    Int2IntOpenHashMap delta = tm.getCounts(t).getCount();
    if (delta != null && !delta.isEmpty()) {
      DSID<String> parentKey =  DSID.of(t.getDatasetKey(), oldParentId);
      // reusable catalogue key instance
      final DSIDValue<String> catKey = DSID.of(t.getDatasetKey(), "");
      // remove delta
      for (TaxonSectorCountMap tc : tm.classificationCounts(parentKey)) {
        tm.updateDatasetSectorCount(catKey.id(tc.getId()), mergeMapCounts(tc.getCount(), delta, -1));
      }
      // add counts
      parentKey.setId(newParentId);
      for (TaxonSectorCountMap tc : tm.classificationCounts(parentKey)) {
        tm.updateDatasetSectorCount(catKey.id(tc.getId()), mergeMapCounts(tc.getCount(), delta, 1));
      }
    }
    // async update classification of all descendants.
    CompletableFuture.runAsync(() -> indexService.updateClassification(t.getDatasetKey(), t.getId()))
      .exceptionally(ex -> {
        LOG.error("Failed to update classification for descendants of {}", t, ex);
        return null;
      });
  }

  /**
   * Moves a taxon to a different parent, updating all caches and search index under the hood.
   *
   * @param t the taxon to change
   * @param newParentId the new parentId to move the taxon to
   * @param oldParentId the current parentId of the taxon to be modified
   */
  public void updateParent(SqlSession session, DSID<String> t, String newParentId, String oldParentId, int userKey){
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    num.updateParentId(t, newParentId, userKey);
    session.commit();
    updatedParentCacheUpdate(session.getMapper(TaxonMapper.class), t, newParentId, oldParentId);
    // update single taxon in ES
    indexService.update(t.getDatasetKey(), List.of(t.getId()));
  }

  private static Int2IntOpenHashMap mergeMapCounts(Int2IntOpenHashMap m1, Int2IntOpenHashMap m2, int factor) {
    for (Int2IntMap.Entry e : m2.int2IntEntrySet()) {
      if (m1.containsKey(e.getIntKey())) {
        m1.put(e.getIntKey(), m1.get(e.getIntKey()) + factor * e.getIntValue());
      } else {
        m1.put(e.getIntKey(), factor * e.getIntValue());
      }
    }
    return m1;
  }
  
  @Override
  protected void deleteBefore(DSID<String> did, Taxon old, int user, TaxonMapper tMapper, SqlSession session) {
    Taxon t = tMapper.get(did);

    int cnt = session.getMapper(NameUsageMapper.class).updateParentIds(did.getDatasetKey(), did.getId(), t.getParentId(), null, user);
    LOG.debug("Moved {} children of {} to {}", cnt, t.getId(), t.getParentId());
    
    // if this taxon had a sector we need to adjust parental counts
    // we keep the sector as a broken sector around
    SectorMapper sm = session.getMapper(SectorMapper.class);
    for (Sector s : sm.listByTarget(did)) {
      tMapper.incDatasetSectorCount(s.getTargetAsDSID(), s.getSubjectDatasetKey(), -1);
    }
    // delete all associated infos (vernaculars, etc)
    // but keep the name record!
    for (Class<? extends TaxonProcessable<?>> m : TaxonProcessable.MAPPERS) {
      int count = session.getMapper(m).deleteByTaxon(did);
      LOG.info("Deleted {} associated {}s for taxon {}", count, m.getSimpleName().replaceAll("Mapper", ""), did);
    }
  }
  
  @Override
  protected boolean deleteAfter(DSID<String> did, Taxon old, int user, TaxonMapper mapper, SqlSession session) {
    NameUsageWrapper bare = old == null ? null : session.getMapper(NameUsageWrapperMapper.class).getBareName(did.getDatasetKey(), old.getName().getId());
    session.close();
    // update ES. there is probably a bare name now to be indexed!
    indexService.delete(did);
    if (bare != null) {
      indexService.add(List.of(bare));
    }
    return false;
  }
  
  /**
   * Does a recursive delete to remove an entire subtree.
   * Name usage, name and all associated infos are removed.
   * It also deletes all sectors targeting any taxon in the subtree.
   * @param keepRoot if true only deletes all descendants but keeps the root taxon
   */
  public void deleteRecursively(DSID<String> id, boolean keepRoot, User user) {
    try (SqlSession session = factory.openSession(false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      List<TaxonProcessable<?>> taxProcMappers = List.of(
        session.getMapper(DistributionMapper.class),
        session.getMapper(MediaMapper.class),
        session.getMapper(VernacularNameMapper.class),
        session.getMapper(SpeciesInteractionMapper.class),
        session.getMapper(TaxonConceptRelationMapper.class)
      );
      TreatmentMapper trm = session.getMapper(TreatmentMapper.class);

      NameMapper nm = session.getMapper(NameMapper.class);
      List<NameProcessable<?>> nameProcMappers = List.of(
        session.getMapper(TypeMaterialMapper.class),
        session.getMapper(NameRelationMapper.class)
      );
      SectorMapper sm = session.getMapper(SectorMapper.class);
      VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);

      // remember sector count map so we can update parents at the end
      TaxonSectorCountMap delta = tm.getCounts(id);
      if (delta == null) {
        throw NotFoundException.notFound(Taxon.class, id);
      }
      LOG.info("Recursively delete {}taxon {} and its {} nested sectors from dataset {} by user {}", keepRoot ? "descendants of " : "", id, delta.size(), id.getDatasetKey(), user);

      List<Integer> sectorKeys = sm.listDescendantSectorKeys(id);
      if (sectorKeys.size() != delta.size()) {
        LOG.info("Recursive delete of {} detected {} included sectors, but {} are declared in the taxons sector count map", id, sectorKeys.size(), delta.size());
      }
      List<TaxonSectorCountMap> parents = tm.classificationCounts(id);

      // we remove usages, names, verbatim sources and associated infos.
      // but NOT name_rels or refs
      PgUtils.consume(
        () -> num.processTreeIds(id),
        unid -> {
          // should we keep the root taxon?
          if (!keepRoot || !unid.usageId.equals(id.getId())) {
            final var nuKey = DSID.of(id.getDatasetKey(), unid.usageId);
            // deletes no longer cascade, remove vernacular, distributions, media and treatments manually
            taxProcMappers.forEach(m -> m.deleteByTaxon(nuKey));
            trm.deleteByTaxon(nuKey);
            // remove usage
            num.delete(nuKey);
            vsm.delete(nuKey);
            // remove name relations and name
            final var nnKey = nuKey.id(unid.nameId);
            nameProcMappers.forEach(m -> m.deleteByName(nnKey));
            nm.delete(nnKey);
          }
        }
      );
      session.commit();

      // remove delta from parents
      var key = DSID.copy(id);
      if (!delta.isEmpty()) {
        for (TaxonSectorCountMap tc : parents) {
          if (!tc.getId().equals(id.getId())) {
            tm.updateDatasetSectorCount(key.id(tc.getId()), mergeMapCounts(tc.getCount(), delta.getCount(), -1));
          }
        }
      }
      session.commit();

      // remove included sectors
      for (int skey : sectorKeys) {
        LOG.info("Delete sector {} from project {} and its imports by user {}", skey, id.getDatasetKey(), user);
        DSID<Integer> sectorKey = DSID.of(id.getDatasetKey(), skey);
        sectorDao.deleteSector(sectorKey, false);
      }
    }
    
    // update ES
    indexService.deleteSubtree(id, keepRoot);
  }
  
  /**
   * Resets all dataset sector counts for an entire catalogue
   * and rebuilds the counts from the currently mapped sectors
   *
   * @param datasetKey
   */
  public void updateAllSectorCounts(int datasetKey) {
    try (SqlSession readSession = factory.openSession(true);
        SqlSession writeSession = factory.openSession(false)
    ) {
      TaxonMapper tm = writeSession.getMapper(TaxonMapper.class);
      tm.resetDatasetSectorCount(datasetKey);
      SectorCountUpdHandler scConsumer = new SectorCountUpdHandler(tm);
      PgUtils.consume(() -> readSession.getMapper(SectorMapper.class).processDataset(datasetKey), scConsumer);
      writeSession.commit();
      LOG.info("Updated dataset sector counts from {} sectors", scConsumer.counter);
    }
  }

  public Treatment getTreatment(DSIDValue<String> key) {
    try (SqlSession session = factory.openSession()) {
      TreatmentMapper tm = session.getMapper(TreatmentMapper.class);
      return tm.get(key);
    }
  }

  static class SectorCountUpdHandler implements Consumer<Sector> {
    private final TaxonMapper tm;
    int counter = 0;
  
    SectorCountUpdHandler(TaxonMapper tm) {
      this.tm = tm;
    }
  
    @Override
    public void accept(Sector s) {
      if (s.getTarget() != null) {
        counter++;
        tm.incDatasetSectorCount(s.getTargetAsDSID(), s.getSubjectDatasetKey(), 1);
      }
    }
  }
  
  private static String devNull(Reference r) {
    return null;
  }
  
  private static String devNull(String r) {
    return null;
  }
  
}
