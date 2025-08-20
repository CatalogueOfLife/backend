package life.catalogue.matching;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.matching.nidx.NameIndex;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RematchArchiveJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(RematchArchiveJob.class);
  private final SqlSessionFactory factory;
  private final NameIndex ni;

  public RematchArchiveJob(int userKey, SqlSessionFactory factory, NameIndex ni) {
    super(userKey);
    this.factory = factory;
    this.ni = ni.assertOnline();
    this.logToFile = true;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof RematchArchiveJob;
  }

  @Override
  public void execute() {
    var matcher = new ArchiveMatcher(factory, ni);
    matcher.match();
  }
}