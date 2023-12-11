package life.catalogue.matching;

import life.catalogue.api.model.DSID;
import life.catalogue.concurrent.BackgroundJob;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
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