package life.catalogue.release;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.Setting;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.function.IntFunction;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * The release keys a project's release and extended release configs tell id mapping to ignore, see
 * ProjectReleaseConfig#ignoredReleases. The name usage archive never takes a version from them.
 */
public class IgnoredReleases implements IntFunction<IntSet> {
  private final SqlSessionFactory factory;

  public IgnoredReleases(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @Override
  public IntSet apply(int projectKey) {
    DatasetSettings settings;
    try (SqlSession session = factory.openSession(true)) {
      settings = session.getMapper(DatasetMapper.class).getSettings(projectKey);
    }
    IntSet ignored = new IntOpenHashSet();
    if (settings != null) {
      var rCfg = ProjectRelease.loadConfig(ProjectReleaseConfig.class, settings.getURI(Setting.RELEASE_CONFIG), false);
      var xCfg = ProjectRelease.loadConfig(XReleaseConfig.class, settings.getURI(Setting.XRELEASE_CONFIG), false);
      if (rCfg.ignoredReleases != null) {
        ignored.addAll(rCfg.ignoredReleases);
      }
      if (xCfg.ignoredReleases != null) {
        ignored.addAll(xCfg.ignoredReleases);
      }
    }
    return ignored;
  }
}
