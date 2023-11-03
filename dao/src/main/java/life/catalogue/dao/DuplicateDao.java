package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.NameCategory;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.DuplicateMapper;
import life.catalogue.db.mapper.SectorMapper;

import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Preconditions;

public class DuplicateDao {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DuplicateDao.class);
  private static final Object[][] EXPORT_HEADERS = new Object[1][];
  static {
    EXPORT_HEADERS[0] = new Object[]{"sourceDatasetKey", "ID", "decision", "status", "rank", "label", "scientificName", "authorship", "genus", "specificEpithet", "infraspecificEpithet", "accepted", "classification"};
  }


  private final SqlSessionFactory factory;
  private final LoadingCache<DSID<Integer>, Integer> sectorCache = Caffeine.newBuilder()
                                            .maximumSize(10000)
                                            .build(this::getSectorDatasetKey);

  private Integer getSectorDatasetKey(@NonNull DSID<Integer> sectorKey) {
    try (SqlSession session = factory.openSession(true)) {
      var s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s != null) {
        return s.getSubjectDatasetKey();
      }
    }
    return null;
  }

  public DuplicateDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public static class DuplicateRequest {
    private static final int WRONG_ENTITY = -999999;
    // the dataset to be analyzed
    int datasetKey;
    String query;
    DatasetInfoCache.DatasetInfo info;
    // if true compare names only, if false usages
    boolean compareNames;
    // the matching mode to detect duplicates - strict or fuzzy
    MatchingMode mode;
    // minimum number of duplicate names to exist. Defaults to 2
    Integer minSize;
    // the source dataset within a project to analyze. Requires datasetKey to be a project or release
    Integer sourceDatasetKey;
    // optional sector to restrict the duplicates to a single sector. Requires datasetKey to be a project or release
    Integer sectorKey;
    // optional restriction to uni/bi/trinomials only
    NameCategory category;
    // optional restriction on ranks
    Set<Rank> ranks;
    // optional restriction on usage status
    Set<TaxonomicStatus> status;
    Boolean acceptedDifferent;
    Boolean authorshipDifferent;
    Boolean rankDifferent;
    Boolean codeDifferent;
    // optionally filter duplicates to have or do not already have a decision
    Boolean withDecision;
    // the project key decisions and sectors are for. Required if withDecision is given
    Integer projectKey;

    /**
     * @param entity the entity to look out for. Must be NAME or NAME_USAGE nothing else
     * @param mode the matching mode to detect duplicates - strict or fuzzy
     * @param query optional prefix query string
     * @param minSize minimum number of duplicate names to exist. Defaults to 2
     * @param datasetKey the dataset to be analyzed
     * @param sourceDatasetKey the source dataset within a project to analyze. Requires datasetKey to be a project or release
     * @param sectorKey optional sector to restrict the duplicates to a single sector. Requires datasetKey to be a project or release
     * @param category optional restriction to uni/bi/trinomials only
     * @param ranks optional restriction on ranks
     * @param status optional restriction on usage status
     * @param authorshipDifferent
     * @param acceptedDifferent
     * @param rankDifferent
     * @param codeDifferent
     * @param withDecision optionally filter duplicates to have or do not already have a decision
     * @param projectKey the project key decisions and sectors are for. Required if withDecision is given
     */
    @JsonCreator
    public DuplicateRequest(@QueryParam("entity") EntityType entity,
                            @QueryParam("mode") MatchingMode mode,
                            @QueryParam("q") String query,
                            @QueryParam("minSize") @Min(2) Integer minSize,
                            @PathParam("key") int datasetKey,
                            @QueryParam("sourceDatasetKey") Integer sourceDatasetKey,
                            @QueryParam("sectorKey") Integer sectorKey,
                            @QueryParam("category") NameCategory category,
                            @QueryParam("rank") Set<Rank> ranks,
                            @QueryParam("status") Set<TaxonomicStatus> status,
                            @QueryParam("authorshipDifferent") Boolean authorshipDifferent,
                            @QueryParam("acceptedDifferent") Boolean acceptedDifferent,
                            @QueryParam("rankDifferent") Boolean rankDifferent,
                            @QueryParam("codeDifferent") Boolean codeDifferent,
                            @QueryParam("withDecision") Boolean withDecision,
                            @QueryParam("catalogueKey") Integer projectKey) {
      this.mode = ObjectUtils.defaultIfNull(mode, MatchingMode.STRICT);
      this.minSize = ObjectUtils.defaultIfNull(minSize, 2);
      this.query = query;
      this.datasetKey = datasetKey;
      this.sourceDatasetKey = sourceDatasetKey;
      this.sectorKey = sectorKey;
      this.category = category;
      this.ranks = ranks;
      this.status = status;
      this.authorshipDifferent = authorshipDifferent;
      this.acceptedDifferent = acceptedDifferent;
      this.rankDifferent = rankDifferent;
      this.codeDifferent = codeDifferent;
      this.withDecision = withDecision;
      this.projectKey = projectKey;

      // entity specific checks & defaults
      if (entity == null || entity == EntityType.NAME_USAGE) {
        compareNames = false;
      } else if (entity == EntityType.NAME) {
        compareNames = true;
        this.sourceDatasetKey = null;
        this.sectorKey = null;
        this.status = null;
        this.acceptedDifferent = null;
        this.withDecision = null;
        this.projectKey = null;
      } else {
        this.sectorKey = WRONG_ENTITY;
        this.sourceDatasetKey = WRONG_ENTITY;
      }
    }
  }

  private void validate(DuplicateRequest req) {
    // wrong entity!
    if (req.sectorKey != null && req.sectorKey == DuplicateRequest.WRONG_ENTITY
        && req.sourceDatasetKey != null && req.sourceDatasetKey == DuplicateRequest.WRONG_ENTITY) {
      throw new IllegalArgumentException("Duplicates only supported for NAME or NAME_USAGE entity");
    }
    var info = DatasetInfoCache.CACHE.info(req.datasetKey);
    Preconditions.checkArgument(req.minSize > 1, "minimum group size must at least be 2");
    if (req.sourceDatasetKey != null){
      Preconditions.checkArgument(info.origin.isProjectOrRelease(), "datasetKey must be a project or release if parameter sourceDatasetKey is used");
    }
    if (req.sectorKey != null){
      Preconditions.checkArgument(info.origin.isProjectOrRelease(), "datasetKey must be a project or release if parameter sectorKey is used");
    }
    if (req.withDecision != null) {
      Preconditions.checkArgument(req.projectKey != null, "catalogueKey is required if parameter withDecision is used");
    }
  }

  public int count(DuplicateRequest req) {
    validate(req);
    try (SqlSession session = factory.openSession()) {
      DuplicateMapper mapper = session.getMapper(DuplicateMapper.class);
      if (req.compareNames) {
        return mapper.countNames(req.mode, req.query, req.minSize, req.datasetKey, req.category, req.ranks, req.authorshipDifferent, req.rankDifferent,
          req.codeDifferent);
      } else {
        return mapper.count(req.mode, req.query, req.minSize, req.datasetKey, req.sourceDatasetKey, req.sectorKey, req.category, req.ranks, req.status,
          req.authorshipDifferent, req.acceptedDifferent, req.rankDifferent, req.codeDifferent, req.withDecision, req.projectKey);
      }
    }
  }

  public ResultPage<Duplicate> page(DuplicateRequest req, Page page) {
    var result = findOrList(req, ObjectUtils.defaultIfNull(page, new Page()));
    return new ResultPage<>(page, result, () -> count(req));
  }

  /**
   * @return ID	Decision	Status	scientificName	Authorship	Genus	specificEpithet	infraspecificEpithet	Accepted	Rank	Classification
   */
  public Stream<Object[]> stream(DuplicateRequest req) {
    var all = findOrList(req, null);
    if (all == null || all.isEmpty()) {
      return Stream.of(EXPORT_HEADERS);
    }
    return Stream.concat(
      Stream.of(EXPORT_HEADERS),
      all.stream().flatMap(this::mapCSV)
    );
  }

  private Stream<Object[]> mapCSV(Duplicate d) {
    // "sourceDatasetKey", "ID", "decision", "status", "rank", "label", "scientificName", "authorship", "genus", "specificEpithet", "infraspecificEpithet", "accepted", "classification"};
    return d.getUsages().stream().map(u -> new Object[]{
      u.getUsage().getSectorKey() == null ? null : sectorCache.get(u.getUsage().getSectorDSID()),
      u.getUsage().getId(),
      u.getDecision() == null ? null : u.getDecision().getMode(),
      u.getUsage().getStatus(),
      u.getUsage().getRank(),
      u.getUsage().getLabel(),
      u.getUsage().getName().getScientificName(),
      u.getUsage().getName().getAuthorship(),
      u.getUsage().getName().getGenus(),
      u.getUsage().getName().getSpecificEpithet(),
      u.getUsage().getName().getInfraspecificEpithet(),
      u.getUsage().isSynonym() ? ((Synonym) u.getUsage()).getAccepted().getLabel() : null,
      concat(u.getClassification())
    });
  }

  private static String concat(List<SimpleName> classification) {
    if (classification != null) {
      Collections.reverse(classification);
      return classification.stream()
                           .map(SimpleName::getName)
                           .collect(Collectors.joining(" > "));
    }
    return null;
  }

  private List<Duplicate> findOrList(DuplicateRequest req, @Nullable Page page) {
    validate(req);
    try (SqlSession session = factory.openSession()) {
      DuplicateMapper mapper = session.getMapper(DuplicateMapper.class);
      // load all duplicate usages or names
      List<Duplicate.Mybatis> dupsTmp;
      if (req.compareNames) {
        dupsTmp = mapper.duplicateNames(req.mode, req.query, req.minSize, req.datasetKey, req.category, req.ranks, req.authorshipDifferent, req.rankDifferent,
          req.codeDifferent, page);
      } else {
        dupsTmp = mapper.duplicates(req.mode, req.query, req.minSize, req.datasetKey, req.sourceDatasetKey, req.sectorKey, req.category, req.ranks, req.status,
          req.authorshipDifferent, req.acceptedDifferent, req.rankDifferent, req.codeDifferent, req.withDecision, req.projectKey, page);
      }
      if (dupsTmp.isEmpty()) {
        return Collections.EMPTY_LIST;
      }

      Set<String> ids = dupsTmp.stream()
          .map(Duplicate.Mybatis::getUsages)
          .flatMap(List::stream)
          .collect(Collectors.toSet());

      Map<String, Duplicate.UsageDecision> usages;
      if (req.compareNames) {
        usages = mapper.namesByIds(req.datasetKey, ids).stream()
            .collect(Collectors.toMap(d -> d.getUsage().getName().getId(), Function.identity()));
      } else {
        usages = mapper.usagesByIds(req.datasetKey, req.projectKey, ids).stream()
            .collect(Collectors.toMap(d -> d.getUsage().getId(), Function.identity()));
      }

      List<Duplicate> dups = new ArrayList<>(dupsTmp.size());
      for (Duplicate.Mybatis dm : dupsTmp) {
        Duplicate d = new Duplicate();
        d.setKey(dm.getKey());
        d.setUsages(dm.getUsages().stream()
            .map(usages::get)
            .collect(Collectors.toList())
        );
        dups.add(d);
      }
      return dups;
    }
  }
}
