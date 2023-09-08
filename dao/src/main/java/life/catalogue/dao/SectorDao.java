package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.Setting;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.Validator;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectorDao extends DatasetEntityDao<Integer, Sector, SectorMapper> {
  private final static Set<Rank> PUBLISHER_SECTOR_RANKS = Set.of(Rank.GENUS, Rank.SPECIES, Rank.SUBSPECIES, Rank.VARIETY, Rank.FORM);
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

  /**
   * Lists all projects that have at least one decision on the given subject dataset key.
   */
  public List<Integer> listProjects(Integer subjectDatasetKey) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(DecisionMapper.class).listProjectKeys(subjectDatasetKey);
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
      TaxonMapper tm = session.getMapper(TaxonMapper.class);

      // make sure we have a managed dataset - otherwise sectors cannot be created and we lack an id sequence to generate a key!
      DatasetOrigin origin = DatasetInfoCache.CACHE.info(s.getDatasetKey()).origin;
      if (origin == null) {
        throw new IllegalArgumentException("dataset " + s.getDatasetKey() + " does not exist");
      } else if (origin != DatasetOrigin.PROJECT) {
        throw new IllegalArgumentException("dataset " + s.getDatasetKey() + " is not managed but of origin " + origin);
      }

      // check if source is a placeholder node
      parsePlaceholderRank(s);

      // reload full source and target
      var subject = reloadTaxon(s, "subject", s::getSubjectAsDSID, s::setSubject, tm);
      var target = reloadTaxon(s, "target", s::getTargetAsDSID, s::setTarget, tm);

      // make sure the priority is not take, otherwise make room
      updatePriorities(s, mapper);

      // creates sector key
      mapper.create(s);

      // for the UI to quickly render something we create a few direct children in the target !!!
      List<Taxon> toCopy = new ArrayList<>();
      // create direct children in catalogue
      if (Sector.Mode.ATTACH == s.getMode()) {
        // one taxon in ATTACH mode
        toCopy.add(subject);
      } else if (Sector.Mode.UNION == s.getMode()){
        // several taxa in UNION mode
        toCopy = tm.children(s.getSubjectAsDSID(), s.getPlaceholderRank(), new Page(0, 5));
      } else {
        // none in MERGE mode
      }

      if (!toCopy.isEmpty()) {
        for (Taxon t : toCopy) {
          t.setSectorKey(s.getId());
          TaxonDao.copyTaxon(session, t, s.getTargetAsDSID(), user);
        }
        indexService.add(toCopy.stream()
          .map(t -> {
            NameUsageWrapper w = new NameUsageWrapper(t);
            w.setSectorDatasetKey(s.getSubjectDatasetKey());
            return w;
          })
          .collect(Collectors.toList()))
        ;
      }

      incSectorCounts(session, s, 1);
  
      session.commit();
      return s.getKey();
    }
  }

  public static Taxon verifyTaxon(Sector s, String kind, Supplier<DSID<String>> getter, TaxonMapper tm) {
    DSID<String> did = getter.get();
    Taxon tax = null;
    if (did != null) {
      tax = tm.get(did);
      if (tax == null) {
        throw new IllegalArgumentException(kind + " ID " + did.getId() + " not existing in dataset " + did.getDatasetKey());
      }
    } else if (s.getMode() != Sector.Mode.MERGE){
      throw new IllegalArgumentException(kind + " required for " + s.getMode() + " sector");
    }
    return tax;
  }

  private static Taxon reloadTaxon(Sector s, String kind, Supplier<DSID<String>> getter, Consumer<SimpleNameLink> setter, TaxonMapper tm) {
    Taxon tax = verifyTaxon(s, kind, getter, tm);
    if (tax != null) {
      setter.accept(tax.toSimpleNameLink());
    } else {
      setter.accept(null);
    }
    return tax;
  }

  @Override
  protected void updateBefore(Sector s, Sector old, int user, SectorMapper mapper, SqlSession session) {
    parsePlaceholderRank(s);
    if (s.getMode() != Sector.Mode.MERGE && s.getTarget() == null) {
      throw new IllegalArgumentException(String.format("%s sector %s must have a target", s.getMode(), s.getKey()));
    }
    requireTaxonIdExists(s.getTargetAsDSID(), session);
    if (s.getPriority() != null && !Objects.equals(s.getPriority(), old.getPriority())) {
      updatePriorities(s, mapper);
    }
  }
  private static void requireTaxonIdExists(DSID<String> key, SqlSession session){
    if (key != null && key.getId() != null) {
      if (!session.getMapper(NameUsageMapper.class).exists(key)) {
        throw new IllegalArgumentException("ID " + key.getId() + " not existing in dataset " + key.getDatasetKey());
      }
    }
  }

  private static void updatePriorities(Sector s, SectorMapper mapper){
    if (s.getPriority() != null) {
      // does that priority already exist? If so, make room
      var pk = mapper.getByPriority(s.getDatasetKey(), s.getPriority());
      if (pk != null) {
        // to avoid constraint problems we need to shift this and lower prios
        int num = mapper.incLowerPriorities(s.getDatasetKey(), s.getPriority());
        LOG.debug("Shifted {} lower priority sectors for dataset {}", num, s.getDatasetKey());
      }
    }
  }

  public static boolean parsePlaceholderRank(Sector s){
    if (s.getSubject() != null) {
      RankID subjId = RankID.parseID(s.getSubjectDatasetKey(), s.getSubject().getId());
      if (subjId.rank != null) {
        s.setPlaceholderRank(subjId.rank);
        s.getSubject().setId(subjId.getId());
        return true;
      }
    }
    return false;
  }

  /**
   * Move also root target taxa of the sector in case the target was changed.
   * We already verified the target taxon exists in the before update...
   */
  @Override
  protected boolean updateAfter(Sector obj, Sector old, int user, SectorMapper mapper, SqlSession session, boolean keepSessionOpen) {
    if (!Objects.equals(old.getTargetID(), obj.getTargetID())) {
      incSectorCounts(session, obj, 1);
      incSectorCounts(session, old, -1);
    }
    // update usages in case the target has changed and it wasn't a MERGE!
    if (obj.getMode() != Sector.Mode.MERGE && simpleNameID(obj.getTarget()) != null && !Objects.equals(simpleNameID(old.getTarget()), simpleNameID(obj.getTarget()))) {
      // loop over sector root taxa as the old target id might be missing or even wrong. Only trust real usage data!
      final DSID<String> key = DSID.of(obj.getDatasetKey(), null);
      for (SimpleName sn : session.getMapper(NameUsageMapper.class).sectorRoot(obj)) {
        // obj.getTargetID() must exist if not null as we validated this in the before update method
        tDao.updateParent(session, key.id(sn.getId()), obj.getTargetID(), sn.getParent(), user);
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

      final String sectorType = subSector ? "subsector" : "sector";

      // order matters!
      for (Class<? extends SectorProcessable<?>> m : SectorProcessable.MAPPERS) {
        int count = session.getMapper(m).deleteBySector(sectorKey);
        LOG.info("Deleted {} existing {}s from {} {}", count, m.getSimpleName().replaceAll("Mapper", ""), sectorType, sectorKey);
      }

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

  public int createMissingMergeSectorsFromPublisher(int projectKey, int userKey, UUID publisherKey, @Nullable Set<Integer> datasetExclusion) {
    return createMissingMergeSectorsFromPublisher(projectKey, userKey, PUBLISHER_SECTOR_RANKS, publisherKey, datasetExclusion);
  }

  /**
   * Creates new merge sectors for source datasets published by the given GBIF publisher key unless there is an existing one already.
   * @param projectKey the project to create sectors in
   * @param userKey the creator
   * @param ranks optional set of ranks as sector setting to use
   * @param publisherKey GBIF publisher key to scan for published datasets
   * @param datasetExclusion optional set of dataset keys to exclude. No sectors will be created for these
   * @return number of newly created sectors
   */
  public int createMissingMergeSectorsFromPublisher(int projectKey, int userKey, @Nullable Set<Rank> ranks, UUID publisherKey, @Nullable Set<Integer> datasetExclusion) {
    LOG.info("Retrieve newly published sectors from GBIF publisher {}", publisherKey);
    List<Integer> datasetKeys;
    try (SqlSession session = factory.openSession(true)) {
      datasetKeys = session.getMapper(DatasetMapper.class).keysByPublisher(publisherKey);
    }
    // exclude some of these datasets?
    if (datasetExclusion != null) {
      datasetExclusion.forEach(datasetKeys::remove);
    }
    int counter = 0;
    if (datasetKeys != null) {
      try (SqlSession session = factory.openSession(true)) {
        var dm = session.getMapper(DatasetMapper.class);
        var sm = session.getMapper(SectorMapper.class);
        final License projectLicense = dm.get(projectKey).getLicense();
        for (int sourceDatasetKey : datasetKeys) {
          var existing = sm.listByDataset(projectKey, sourceDatasetKey);
          if ((existing == null || existing.isEmpty())) {
            // not yet existing - create a new merge sector!
            // first check if licenses are compatible
            Dataset src = dm.get(sourceDatasetKey);
            if (!License.isCompatible(src.getLicense(), projectLicense)) {
              LOG.warn("License {} of source {} from publisher {} is not compatible with license {} of project {}. Do not create any sector for: {}", src.getLicense(), src.getKey(), publisherKey, projectLicense, projectKey, src.getTitle());
              continue;
            }
            Sector s = new Sector();
            s.setDatasetKey(projectKey);
            s.setSubjectDatasetKey(sourceDatasetKey);
            s.setMode(Sector.Mode.MERGE);
            s.setRanks(ranks);
            s.applyUser(userKey);
            sm.create(s);
            counter++;
          }
        }
      }
    }
    return counter;
  }
}
