
package life.catalogue.matching.nidx;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameIndexEntry;
import life.catalogue.api.model.NameMatch;
import life.catalogue.common.tax.AuthorshipNormalizer;

import java.io.IOException;
import java.time.LocalDateTime;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameIndexFactory {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexFactory.class);

  /**
   * @return NameIndex that returns no match for any query
   */
  public static NameIndex passThru() {
    return new NameIndex() {
      @Override
      public void start() throws Exception {
      }

      @Override
      public void stop() throws Exception {
      }

      @Override
      public LocalDateTime created() {
        return LocalDateTime.now();
      }

      @Override
      public NameMatch match(Name name, boolean allowInserts, boolean verbose) {
        return NameMatch.noMatch();
      }

      @Override
      public NameIndexEntry get(Integer key) {
        return null;
      }

      @Override
      public int size() {
        return 0;
      }

      @Override
      public void reset() {
      }

      @Override
      public NameIndexStore store() {
        return null;
      }

      @Override
      public boolean hasStarted() {
        return true;
      }

    };
  }

  /**
   * @return NameIndex that returns the same fixed nidx match for any query
   */
  public static NameIndex fixed(final int nidx) {
    return new NameIndex() {
      @Override
      public void start() throws Exception { }

      @Override
      public void stop() throws Exception { }

      @Override
      public LocalDateTime created() {
        return LocalDateTime.now();
      }

      @Override
      public NameMatch match(Name query, boolean allowInserts, boolean verbose) {
        return NameMatch.match(nidx);
      }

      @Override
      public NameIndexEntry get(Integer key) {
        return null;
      }

      @Override
      public int size() {
        return 1;
      }

      @Override
      public void reset() {
      }

      @Override
      public NameIndexStore store() {
        return null;
      }

      @Override
      public boolean hasStarted() {
        return true;
      }

    };
  }

  /**
   * Returns a persistent index if location is given, otherwise an in memory one
   */
  public static NameIndexImpl build(NamesIndexConfig cfg, SqlSessionFactory sqlFactory, AuthorshipNormalizer aNormalizer) {
    NameIndexStore store = buildStore(cfg);
    return new NameIndexImpl(store, aNormalizer, sqlFactory);
  }

  private static NameIndexStore buildStore(NamesIndexConfig cfg) {
    try {
      if (cfg.file == null) {
        LOG.info("Create memory names index");
        return new NameIndexMapStore();

      } else {
        if (!cfg.file.exists()) {
          FileUtils.forceMkdirParent(cfg.file);
          LOG.info("Create new persistent chronicle names index at {}", cfg.file.getAbsolutePath());
        } else {
          LOG.info("Use persistent chronicle names index at {}", cfg.file.getAbsolutePath());
        }
        return new NameIndexChronicleStore(cfg);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
