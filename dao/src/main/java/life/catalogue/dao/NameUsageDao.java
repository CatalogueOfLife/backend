package life.catalogue.dao;

import jakarta.ws.rs.PathParam;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class NameUsageDao {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageDao.class);
  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;

  public NameUsageDao(SqlSessionFactory factory, NameUsageIndexService indexService) {
    this.factory = factory;
    this.indexService = indexService;
  }

  /**
   * Returns a taxon with the specified key or throws:
   *  - a SynonymException in case the id belongs to a synonym
   *  - a NotFoundException if the id is no name usage at all
   */
  public NameUsageBase get(DSID<String> key) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(NameUsageMapper.class).get(key);
    }
  }

  public ResultPage<NameUsageBase> list(int datasetKey, @Nullable String q, Rank rank,
                                        @Nullable String nameID,
                                        @Nullable Integer namesIndexID,
                                        Page page)
  {
    try (SqlSession session = factory.openSession()) {
      Page p = page == null ? new Page() : page;
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      List<NameUsageBase> result;
      Supplier<Integer> count;
      if (namesIndexID != null) {
        result = mapper.listByNamesIndexOrCanonicalID(datasetKey, namesIndexID, p);
        count = () -> mapper.countByNamesIndexID(namesIndexID, datasetKey);
      } else if (nameID != null) {
        result = mapper.listByNameID(datasetKey, nameID, p);
        count = () -> mapper.countByNameID(nameID, datasetKey);
      } else if (q != null) {
        result = mapper.listByName(datasetKey, q, rank, p);
        count = () -> result.size();
      } else {
        result = mapper.list(datasetKey, p);
        count = () -> mapper.count(datasetKey);
      }
      return new ResultPage<>(p, result, count);
    }
  }

  /**
   *
   * Lists related usages from other datasets which are linked via names index matches.
   * Various options to restrict the related datasets to be considered.
   *
   * @param datasetKey original dataset
   * @param id original usageOD in the above dataset
   * @param gbifOnly if true only datasets with a GBIF key are considered
   * @param nonGbifDatasetKeys optional setting when gbifOnly=true. Set of dataset keys to always consider even if they do not have a gbif key
   * @param datasetTypes optional set of dataset types to consider, ignoring all others
   * @param datasetKeys optional set of dataset keys to consider, ignoring all others
   * @param publisherKeys optional set of dataset GBIF publisher keys to consider, ignoring all others
   * @return
   */
  public List<SimpleNameInDataset> related(int datasetKey, String id,
                                     boolean gbifOnly,
                                     @Nullable Collection<Integer> nonGbifDatasetKeys,
                                     @Nullable Collection<DatasetOrigin> datasetOrigins,
                                     @Nullable Collection<DatasetType> datasetTypes,
                                     @Nullable Collection<Integer> datasetKeys,
                                     @Nullable Collection<UUID> publisherKeys) {
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      var key = DSID.of(datasetKey, id);
      num.existsOrThrow(key);
      return num.listRelated(key, gbifOnly, nonGbifDatasetKeys, datasetOrigins, datasetTypes, datasetKeys, publisherKeys);
    }
  }

  public SimpleName reindex(int datasetKey, String id) {
    SimpleName sn;
    try (var session = factory.openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      sn = num.getSimple(DSID.of(datasetKey, id));
    }
    indexService.update(datasetKey, id);
    return sn;
  }
}
