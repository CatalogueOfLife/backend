package life.catalogue.matching;

import life.catalogue.matching.nidx.NameIndex;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

public class RematchMissing implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(RematchMissing.class);
  private final DatasetMatcher matcher;

  private final int datasetKey;

  public RematchMissing(SqlSessionFactory factory, NameIndex ni, EventBus bus, int datasetKey) {
    this(new DatasetMatcher(factory, ni.assertOnline(), bus), datasetKey);
  }
  public RematchMissing(DatasetMatcher matcher, int datasetKey) {
    this.datasetKey = datasetKey;
    this.matcher = matcher;
  }

  @Override
  public void run() {
    matcher.match(datasetKey, true, true);
  }
}