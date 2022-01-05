package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.NameProcessable;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.TaxonProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Validator;

public class SectorDao extends DatasetEntityDao<Integer, Sector, SectorMapper> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorDao.class);
  private final NameUsageIndexService indexService;
  private final TaxonDao tDao;

  public SectorDao(SqlSessionFactory factory, NameUsageIndexService indexService, TaxonDao tDao, Validator validator) {
    super(true, factory, Sector.class, SectorMapper.class, validator);
    this.indexService = indexService;
    this.tDao = tDao;
  }
  
  public ResultPage<Sector> search(SectorSearchRequest request, Page page) {
    validate(request);
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      SectorMapper mapper = session.getMapper(SectorMapper.class);
      List<Sector> result = mapper.search(request, p);
      return new ResultPage<>(p, result, () -> mapper.countSearch(request));
    }
  }

  static void validate(SectorSearchRequest req) {
    if (req.isWithoutData() && req.getDatasetKey() == null) {
      throw new IllegalArgumentException("DatasetKey must be given if withoutData filter is requested");
    }
  }

  @Override
  public DSID<Integer> create(Sector s, int user) {
    s.applyUser(user);
    try (SqlSession session = factory.openSession(ExecutorType.SIMPLE, false)) {
      SectorMapper mapper = session.getMapper(SectorMapper.class);

      // make sure we have a managed dataset - otherwise sectors cannot be created and we lack a id sequence to generate a key!
      DatasetOrigin origin = DatasetInfoCache.CACHE.info(s.getDatasetKey()).origin;
      if (origin == null) {
        throw new IllegalArgumentException("dataset " + s.getDatasetKey() + " does not exist");
      } else if (origin != DatasetOrigin.MANAGED) {
        throw new IllegalArgumentException("dataset " + s.getDatasetKey() + " is not managed but of origin " + origin);
      }

      final DSID<String> did = s.getTargetAsDSID();
      TaxonMapper tm = session.getMapper(TaxonMapper.class);

      // check if source is a placeholder node
      parsePlaceholderRank(s);
      // reload full source and target
      Taxon subject = tm.get(s.getSubjectAsDSID());
      if (subject == null) {
        throw new IllegalArgumentException("subject ID " + s.getSubject().getId() + " not existing in dataset " + s.getSubjectDatasetKey());
      }
      s.setSubject(subject.toSimpleNameLink());

      Taxon target  = tm.get(s.getTargetAsDSID());
      if (target == null) {
        throw new IllegalArgumentException("target ID " + s.getTarget().getId() + " not existing in catalogue " + s.getDatasetKey());
      }
      s.setTarget(target.toSimpleNameLink());
      
      
      // creates sector key
      mapper.create(s);

      // for the UI to quickly render something we create a few direct children in the target !!!
      List<Taxon> toCopy = new ArrayList<>();
      // create direct children in catalogue
      if (Sector.Mode.ATTACH == s.getMode()) {
        // one taxon in ATTACH mode
        toCopy.add(subject);
      } else {
        // several taxa in UNION/MERGE mode
        toCopy = tm.children(s.getSubjectAsDSID(), s.getPlaceholderRank(), new Page(0, 5));
      }
  
      for (Taxon t : toCopy) {
        t.setSectorKey(s.getId());
        TaxonDao.copyTaxon(session, t, did, user);
      }
      indexService.add(toCopy.stream()
        .map(t -> {
          NameUsageWrapper w = new NameUsageWrapper(t);
          w.setSectorDatasetKey(s.getSubjectDatasetKey());
          return w;
        })
        .collect(Collectors.toList()))
      ;

      incSectorCounts(session, s, 1);
  
      session.commit();
      return s.getKey();
    }
  }

  @Override
  protected void updateBefore(Sector s, Sector old, int user, SectorMapper mapper, SqlSession session) {
    parsePlaceholderRank(s);
    requireTaxonIdExists(s.getTargetAsDSID(), session);
    super.updateBefore(s, old, user, mapper, session);
  }

  private static void requireTaxonIdExists(DSID<String> key, SqlSession session){
    if (key != null && key.getId() != null) {
      if (!session.getMapper(NameUsageMapper.class).exists(key)) {
        throw new IllegalArgumentException("ID " + key.getId() + " not existing in dataset " + key.getDatasetKey());
      }
    }
  }

  public static boolean parsePlaceholderRank(Sector s){
    RankID subjId = RankID.parseID(s.getSubjectDatasetKey(), s.getSubject().getId());
    if (subjId.rank != null) {
      s.setPlaceholderRank(subjId.rank);
      s.getSubject().setId(subjId.getId());
      return true;
    }
    return false;
  }

  /**
   * We already verified the target taxon exists in the before update...
   */
  @Override
  protected boolean updateAfter(Sector obj, Sector old, int user, SectorMapper mapper, SqlSession session, boolean keepSessionOpen) {
    if (old.getTarget() == null || obj.getTarget() == null || !Objects.equals(old.getTarget().getId(), obj.getTarget().getId())) {
      incSectorCounts(session, obj, 1);
      incSectorCounts(session, old, -1);
    }
    // update usages in case the target has changed!
    if (!Objects.equals(simpleNameID(old.getTarget()), simpleNameID(obj.getTarget())) && obj.getTarget().getId()!=null) {
      // loop over sector root taxa as the old target id might be missing or even wrong. Only trust real usage data!
      final DSID<String> key = DSID.of(obj.getDatasetKey(), null);
      for (SimpleName sn : session.getMapper(NameUsageMapper.class).sectorRoot(obj)) {
        // obj.getTarget().getId() must exist as we validated this in the before update method
        tDao.updateParent(session, key.id(sn.getId()), obj.getTarget().getId(), sn.getParent(), user);
      }
    }
    return false;
  }

  private static String simpleNameID(SimpleName sn) {
    return sn == null ? null : sn.getId();
  }

  @Override
  public int delete(DSID<Integer> key, int user) {
    throw new UnsupportedOperationException("Sectors have to be deleted asynchronously through a SectorDelete job");
  }

  /**
   * A full sector deletion method that removes also associated usages.
   * This can be used in the SectorDelete jobs and elsewhere, but should not be used blindly as a replacement for a sector dao delete
   * which we disabled for good reasons.
   *
   * Note that this method does NOT deal with nested sectors and their recursive deletion!
   *
   * @param subSector flag to indicate in logs that we deleted a subsector in case of recursive deletions
   */
  public void deleteSector(DSID<Integer> sectorKey, boolean subSector) {
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
      }
      // order matters!
      List<SectorProcessable<?>> secProcMappers = List.of(
        // usage related
        session.getMapper(DistributionMapper.class),
        session.getMapper(MediaMapper.class),
        session.getMapper(VernacularNameMapper.class),
        session.getMapper(SpeciesInteractionMapper.class),
        session.getMapper(TaxonConceptRelationMapper.class),
        session.getMapper(TreatmentMapper.class),
        // usage
        session.getMapper(NameUsageMapper.class),
        // name related
        session.getMapper(NameMatchMapper.class),
        session.getMapper(TypeMaterialMapper.class),
        session.getMapper(NameRelationMapper.class),
        // name
        session.getMapper(NameMapper.class),
        // refs
        session.getMapper(ReferenceMapper.class)
      );

      final String sectorType = subSector ? "subsector" : "sector";

      secProcMappers.forEach(m -> {
        int count = m.deleteBySector(sectorKey);
        String type = m.getClass().getSimpleName().replace("Mapper", "");
        LOG.info("Deleted {} {} records from {} {}", count, type, sectorType, sectorKey);
      });

      // remove verbatim sources from remaining usages
      VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);
      vsm.deleteBySector(s);

      // update datasetSectors counts
      SectorDao.incSectorCounts(session, s, -1);

      // we don't remove any sector metric anymore to avoid previous releases to be broken
      // see https://github.com/CatalogueOfLife/backend/issues/986
      //session.getMapper(SectorImportMapper.class).delete(sectorKey);
      session.getMapper(SectorMapper.class).delete(sectorKey);
      LOG.info("Deleted {} {}", sectorType, sectorKey);
    }
  }

  /**
   * Recursively updates the sector count for a given sectors target taxon and all its parents.
   */
  public static void incSectorCounts(SqlSession session, Sector s, int delta) {
    if (s != null && s.getTarget() != null) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      tm.incDatasetSectorCount(s.getTargetAsDSID(), s.getSubjectDatasetKey(), delta);
    }
  }

}
