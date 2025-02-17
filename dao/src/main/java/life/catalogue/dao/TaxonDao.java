package life.catalogue.dao;

import com.google.common.annotations.VisibleForTesting;

import life.catalogue.api.exception.ArchivedException;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.SynonymException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.db.NameProcessable;
import life.catalogue.db.PgUtils;
import life.catalogue.db.TaxonProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.TaxGroupAnalyzer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import jakarta.validation.Validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.LoadingCache;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class TaxonDao extends NameUsageDao<Taxon, TaxonMapper> implements TaxonCounter {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonDao.class);
  private SectorDao sectorDao;
  private TaxGroupAnalyzer groupAnalyzer = new TaxGroupAnalyzer();

  /**
   * Warn: you must set a sector dao manually before using the TaxonDao.
   * We have circular dependency that cannot be satisfied with final properties through constructors
   */
  public TaxonDao(SqlSessionFactory factory, NameDao nameDao, NameUsageIndexService indexService, Validator validator) {
    super(Taxon.class, TaxonMapper.class, factory, nameDao, indexService, validator);
  }

  // dependency loop :( so cant populate this in the constructor
  public void setSectorDao(SectorDao sectorDao) {
    this.sectorDao = sectorDao;
  }

  public static void copyTaxon(SqlSession session, final Taxon t, final DSID<String> target, int user) {
    copyTaxon(session, t, target, user, Collections.emptySet());
  }

  /**
   * Copies the given source taxon into the dataset and under the given target parent.
   * The taxon and name source instance will be modified to represent the newly generated and finally persisted taxon.
   * The original id is retained and finally returned.
   * An optional set of associated entity types can be indicated to be copied too.
   *
   * The sectorKey found on the main taxon will also be applied to associated name, reference and other copied entities.
   * See {@link CopyUtil#copyUsage} for details.
   *
   * @return the original source taxon id
   */
  public static void copyTaxon(SqlSession session, final Taxon t, final DSID<String> target, int user, Set<EntityType> include) {
    CopyUtil.copyUsage(session, t, target, user, include, TaxonDao::devNull, TaxonDao::devNull);
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

  public TaxonMetrics getMetrics(int datasetKey, String id) {
    return getMetrics(DSID.of(datasetKey, id));
  }
  public TaxonMetrics getMetrics(DSID<String> key) {
      try (SqlSession session = factory.openSession()) {
        return session.getMapper(TaxonMetricsMapper.class).get(key);
      }
    }

  public List<SimpleName> classificationSimple(DSID<String> key) {
    try (SqlSession session = factory.openSession(true)) {
      return classificationSimple(key, session);
    }
  }
  private List<SimpleName> classificationSimple(DSID<String> key, SqlSession session) {
    if (DatasetInfoCache.CACHE.info(key.getDatasetKey()).isMutable()) {
      return session.getMapper(TaxonMapper.class).classificationSimple(key);
    } else {
      return session.getMapper(TaxonMetricsMapper.class).get(key).getClassification();
    }
  }

  @Override
  public int count(DSID<String> key, Rank countRank) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(TaxonMetricsMapper.class).get(key).getTaxaByRankCount().get(countRank);
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
    fillUsageInfo(session, info, null, true, true, true, true, true, true, true,
      true, true, true, true, true, true, true);
    return info;
  }

  private static void addSectorMode(SectorScoped obj, Map<Integer, Sector.Mode> cache, SectorMapper sm) {
    if (sm != null && obj.getSectorKey() != null) {
      obj.setSectorMode(
        cache.computeIfAbsent(obj.getSectorKey(), sk -> sm.getMode(obj.getDatasetKey(), sk))
      );
    }
  }

  /**
   * @param info existing usage info with at least the usage property given so we can load the rest.
   */
  public void fillUsageInfo(final SqlSession session, final UsageInfo info,
                            LoadingCache<String, Reference> refCache,
                            boolean loadClassification,
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
                            boolean loadSpeciesInteractions,
                            boolean loadDecisions,
                            boolean loadSectorModes
                            ) {
    var usage = info.getUsage();
    final boolean isTaxon = usage.isTaxon();
    // we don't expect too many different sectors to show up, so lets fetch them as we go and reuse them
    // sectors only exist in projects and releases anyways
    final Map<Integer, Sector.Mode> sectorModes = loadSectorModes ? new HashMap<>() : null;
    SectorMapper sm = loadSectorModes ? session.getMapper(SectorMapper.class) : null;
    addSectorMode(usage, sectorModes, sm);
    addSectorMode(usage.getName(), sectorModes, sm);

    // all reference, name and taxon keys so we can select their details at the end
    Set<String> taxonIds = new HashSet<>();
    Set<String> nameIds = new HashSet<>();
    Set<String> refIds = new HashSet<>(usage.getReferenceIds());
    refIds.add(usage.getName().getPublishedInId());

    // classification & taxgroup
    if (loadClassification) {
      info.setClassification(classificationSimple(usage, session));
      var g = groupAnalyzer.analyze(usage.toSimpleNameLink(), info.getClassification());
      info.setGroup(g);
    }

    // synonyms
    if (isTaxon && loadSynonyms) {
      var syns = getSynonymy((Taxon)usage);
      info.setSynonyms(syns); // never NULL, but only set for accepted Taxon usages!!!
      info.getSynonyms().forEach(s -> {
        refIds.add(s.getName().getPublishedInId());
        refIds.addAll(s.getReferenceIds());
        addSectorMode(s, sectorModes, sm);
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
          addSectorMode(r, sectorModes, sm);
        });
      }
    }

    // add all type material
    if (loadTypeMaterial) {
      TypeMaterialMapper tmm = session.getMapper(TypeMaterialMapper.class);
      info.getTypeMaterial().put(usage.getName().getId(), tmm.listByName(usage.getName()));
      if (info.getSynonyms() != null) { // can be null for synonym usages
        info.getSynonyms().forEach(s -> info.getTypeMaterial().put(s.getName().getId(), tmm.listByName(s.getName())));
        // aggregate types for all homotypic names
        if (!info.getTypeMaterial().isEmpty()) {
          final List<String> nids = new ArrayList<>();
          nids.add(usage.getName().getId());
          info.getSynonyms().getHomotypic().forEach(s -> nids.add(s.getName().getId()));
          aggregateTypes(info, nids); // homotypic group of accepted name
          // now add homotypic groups from heterotypic synonyms
          for (var hg : info.getSynonyms().getHeterotypicGroups()) {
            var nids2 = hg.stream().map(s -> s.getName().getId()).collect(Collectors.toList());
            aggregateTypes(info, nids2);
          }
        }
      }
      // extract all refs
      info.getTypeMaterial().values().forEach(
        types -> types.forEach(t -> {
          refIds.add(t.getReferenceId());
          addSectorMode(t, sectorModes, sm);
        })
      );
    }

    if (loadDecisions) {
      var dm = session.getMapper(DecisionMapper.class);
      var ed = dm.getByReleasedUsage(usage);
      if (ed != null) {
        info.getDecisions().put(usage.getId(), ed);
      }
      if (info.getSynonyms() != null) { // can be null for synonym usages
        info.getSynonyms().forEach(s -> {
          var eds = dm.getByReleasedUsage(s);
          if (eds != null) {
            info.getDecisions().put(s.getId(), eds);
          }
        });
      }
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
        info.getDistributions().forEach(d -> {
          refIds.add(d.getReferenceId());
          addSectorMode(d, sectorModes, sm);
        });
      }

      if (loadMedia) {
        MediaMapper mm = session.getMapper(MediaMapper.class);
        info.setMedia(mm.listByTaxon(usage));
        info.getMedia().forEach(m -> {
          refIds.add(m.getReferenceId());
          addSectorMode(m, sectorModes, sm);
        });
      }

      if (loadVernacular) {
        VernacularNameMapper vm = session.getMapper(VernacularNameMapper.class);
        info.setVernacularNames(vm.listByTaxon(usage));
        info.getVernacularNames().forEach(v -> {
          refIds.add(v.getReferenceId());
          addSectorMode(v, sectorModes, sm);
        });
      }

      if (loadProperties) {
        var mapper = session.getMapper(TaxonPropertyMapper.class);
        info.setProperties(mapper.listByTaxon(usage));
        info.getProperties().forEach(p -> {
          refIds.add(p.getReferenceId());
          addSectorMode(p, sectorModes, sm);
        });
      }

      if (loadConceptRelations) {
        TaxonConceptRelationMapper mapper = session.getMapper(TaxonConceptRelationMapper.class);
        info.setConceptRelations(mapper.listByTaxon(usage));
        info.getConceptRelations().addAll(mapper.listByRelatedTaxon(usage));
        info.getConceptRelations().forEach(r -> {
          refIds.add(r.getReferenceId());
          taxonIds.add(r.getTaxonId());
          taxonIds.add(r.getRelatedTaxonId());
          addSectorMode(r, sectorModes, sm);
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
          addSectorMode(r, sectorModes, sm);
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
      info.getReferences().values().forEach(r -> addSectorMode(r, sectorModes, sm));
    }

    if (!nameIds.isEmpty()) {
      NameMapper mapper = session.getMapper(NameMapper.class);
      List<Name> names = mapper.listByIds(usage.getDatasetKey(), nameIds);
      names.forEach(n -> addSectorMode(n, sectorModes, sm));
      info.addNames(names);
    }

    if (!taxonIds.isEmpty()) {
      TaxonMapper mapper = session.getMapper(TaxonMapper.class);
      List<Taxon> taxa = mapper.listByIds(usage.getDatasetKey(), taxonIds);
      taxa.forEach(t -> addSectorMode(t, sectorModes, sm));
      info.addTaxa(taxa);
    }
  }

  @VisibleForTesting
  protected static String typeContent(TypeMaterial tm) {
    return Arrays.stream(new Object[]{tm.getCitation(), tm.getStatus(), tm.getLink(), tm.getInstitutionCode(), tm.getCatalogNumber(),
        tm.getLocality(), tm.getCountry(), tm.getSex(), tm.getAssociatedSequences(), tm.getHost(), tm.getDate(), tm.getCollector(),
        tm.getLatitude(), tm.getLongitude(), tm.getCoordinate(), tm.getAltitude(), tm.getRemarks()}
      )
      .filter(Objects::nonNull)
      .map(Object::toString)
      .map(String::toLowerCase)
      .map(StringUtils::trimToEmpty)
      .collect(Collectors.joining("|"));
  }
  private void aggregateTypes(UsageInfo info, List<String> homotypicNameIds) {
    if (homotypicNameIds != null && !homotypicNameIds.isEmpty()) {
      List<TypeMaterial> agg = homotypicNameIds.stream()
        .map(info::getTypeMaterial)
        .flatMap(List::stream)
        .collect(Collectors.groupingBy(TaxonDao::typeContent))
        .values()
        .stream()
        .map(l -> l.get(0))
        .collect(Collectors.toList());
      for (var nid : homotypicNameIds) {
        info.getTypeMaterial().put(nid, agg);
      }
    }
  }

  /**
   * Creates a new Taxon including a name instance if no name id is already given.
   * If desired the search index is updated too.
   * @param t
   * @param user
   * @param indexImmediately if true the search index is also updated
   * @return newly created taxon id
   */
  @Override
  public DSID<String> create(Taxon t, int user, boolean indexImmediately) {
    t.setStatusIfNull(TaxonomicStatus.ACCEPTED);
    if (t.getStatus().isSynonym()) {
      throw new IllegalArgumentException("Taxa cannot have a synonym status");
    }
    return super.create(t, user, indexImmediately);
  }

  @Override
  protected boolean updateAfter(Taxon t, Taxon old, int user, TaxonMapper tm, SqlSession session, boolean keepSessionOpen) {
    // has parent, i.e. classification been changed ?
    if (!Objects.equals(old.getParentId(), t.getParentId())) {
      updatedParentCacheUpdate(t);
    }
    return super.updateAfter(t, old, user, tm, session, keepSessionOpen);
  }

  /**
   * Updates cached information in both postgres and elastic when a parent id of a taxon has changed.
   * The actual parentID change is expected to have happened already in the database!
   * @param t the taxon that has been assigned a new parentID
   */
  private void updatedParentCacheUpdate(DSID<String> t){
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
   */
  public void updateParent(SqlSession session, DSID<String> t, String newParentId, int userKey){
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    num.updateParentId(t, newParentId, userKey);
    session.commit();
    updatedParentCacheUpdate(t);
    // update single taxon in ES
    indexService.update(t.getDatasetKey(), List.of(t.getId()));
  }
  
  @Override
  protected void deleteBefore(DSID<String> did, Taxon old, int user, TaxonMapper tMapper, SqlSession session) {
    Taxon t = tMapper.get(did);

    int cnt = session.getMapper(NameUsageMapper.class).updateParentIds(did.getDatasetKey(), did.getId(), t.getParentId(), null, user);
    LOG.debug("Moved {} children of {} to {}", cnt, t.getId(), t.getParentId());
    
    // delete all associated infos (vernaculars, etc)
    // but keep the name record!
    for (Class<? extends TaxonProcessable<?>> m : TaxonProcessable.MAPPERS) {
      int count = session.getMapper(m).deleteByTaxon(did);
      LOG.info("Deleted {} associated {}s for usage {}", count, m.getSimpleName().replaceAll("Mapper", ""), did);
    }
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
      SectorMapper sm = session.getMapper(SectorMapper.class);
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      NameMapper nm = session.getMapper(NameMapper.class);

      List<Integer> sectorKeys = sm.listDescendantSectorKeys(id);
      LOG.info("Recursively delete {}taxon {} and its {} nested sectors from dataset {} by user {}", keepRoot ? "descendants of " : "", id, sectorKeys.size(), id.getDatasetKey(), user);

      List<TaxonProcessable<?>> taxProcMappers = TaxonProcessable.MAPPERS.stream()
        .map(session::getMapper)
        .collect(Collectors.toList());

      List<NameProcessable<?>> nameProcMappers = NameProcessable.MAPPERS.stream()
        .map(session::getMapper)
        .collect(Collectors.toList());

      // we remove usages & associated infos, names & relations and verbatim sources,
      // but NOT references!
      PgUtils.consume(
        () -> num.processTreeIds(id),
        unid -> {
          // should we keep the root taxon?
          if (!keepRoot || !unid.usageId.equals(id.getId())) {
            final var nuKey = DSID.of(id.getDatasetKey(), unid.usageId);
            // deletes no longer cascade, remove vernacular, distributions, media and treatments manually
            taxProcMappers.forEach(m -> m.deleteByTaxon(nuKey));
            // remove usage
            num.delete(nuKey);
            // remove name relations and name
            final var nnKey = nuKey.id(unid.nameId);
            nameProcMappers.forEach(m -> m.deleteByName(nnKey));
            nm.delete(nnKey);
          }
        }
      );
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

  public Treatment getTreatment(DSIDValue<String> key) {
    try (SqlSession session = factory.openSession()) {
      TreatmentMapper tm = session.getMapper(TreatmentMapper.class);
      return tm.get(key);
    }
  }

  private static String devNull(Reference r) {
    return null;
  }
  
  private static String devNull(String r) {
    return null;
  }
  
}
