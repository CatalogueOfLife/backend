package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.model.User;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NamesIndexMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RematchJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(RematchJob.class);
  private final SqlSessionFactory factory;
  private final NameIndex ni;

  @JsonProperty
  private final int[] datasetKeys;

  public static RematchJob all(User user, SqlSessionFactory factory, NameIndex ni){
    return new RematchJob(user, factory, ni);
  }

  public static RematchJob one(User user, SqlSessionFactory factory, NameIndex ni, int datasetKey){
    return new RematchJob(user, factory, ni, datasetKey);
  }

  public static RematchJob some(User user, SqlSessionFactory factory, NameIndex ni, int... datasetKeys){
    return new RematchJob(user, factory, ni, datasetKeys);
  }

  private RematchJob(User user, SqlSessionFactory factory, NameIndex ni, int... datasetKeys) {
    super(user.getKey());
    this.datasetKeys = datasetKeys;
    this.factory = factory;
    this.ni = ni;
  }

  @Override
  public void execute() {
    final List<Integer> keys;
    if (datasetKeys != null && datasetKeys.length > 0) {
      keys = new ArrayList<>();
      for (int key : datasetKeys){
        keys.add(key);
      }
    } else {
      LOG.warn("Rebuilt names index and rematch all datasets with data");
      try (SqlSession session = factory.openSession(true)) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);
        keys = dm.keys();
        keys.removeIf(key -> !dpm.exists(key));
        // kill names index
        NamesIndexMapper nim = session.getMapper(NamesIndexMapper.class);
        nim.truncate();
        nim.resetSequence();
      }
    }

    LOG.info("Rematching {} datasets with data. Triggered by {}", keys.size(), getUserKey());
    DatasetMatcher matcher = new DatasetMatcher(factory, ni, true);
    for (int key : keys) {
      matcher.match(key, true);
    }

    LOG.info("Rematched {} datasets ({} failed), updating {} names from {} in total",
      matcher.getDatasets(),
      keys.size() - matcher.getDatasets(),
      matcher.getUpdated(),
      matcher.getTotal()
    );
  }
}