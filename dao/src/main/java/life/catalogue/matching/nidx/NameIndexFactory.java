
package life.catalogue.matching.nidx;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.common.tax.AuthorshipNormalizer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mapdb.DBMaker;
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
      public IndexName get(Integer key) {
        return null;
      }

      @Override
      public Collection<IndexName> byCanonical(Integer key) {
        return Collections.emptyList();
      }

      @Override
      public Iterable<IndexName> all() {
        return Collections.emptyList();
      }

      @Override
      public int size() {
        return 0;
      }

      @Override
      public List<IndexName> delete(int key, boolean rematch) {
        return Collections.emptyList();
      }

      @Override
      public void add(IndexName name) {
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
   * @return NameIndex that returns the same fixed match for any query
   */
  public static NameIndex fixed(final IndexName n) {
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
        NameMatch m = new NameMatch();
        m.setName(n);
        if (query.getLabel().equalsIgnoreCase(n.getLabel())) {
          m.setType(MatchType.EXACT);
        } else {
          m.setType(MatchType.VARIANT);
        }
        return m;
      }

      @Override
      public Collection<IndexName> byCanonical(Integer key) {
        return Objects.equals(n.getCanonicalId(), key) ? List.of(n) : Collections.emptyList();
      }

      @Override
      public IndexName get(Integer key) {
        return Objects.equals(n.getKey(), key) ? n : null;
      }

      @Override
      public Iterable<IndexName> all() {
        return List.of(n);
      }

      @Override
      public int size() {
        return 1;
      }

      @Override
      public List<IndexName> delete(int key, boolean rematch) {
        return Collections.emptyList();
      }

      @Override
      public void add(IndexName name) { }

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
    return new NameIndexImpl(store, aNormalizer, sqlFactory, cfg.verification);
  }

  private static NameIndexStore buildStore(NamesIndexConfig cfg) {
    try {
      if (cfg.file == null) {
        LOG.info("Create {} memory names index", cfg.type);
        if (cfg.type == NamesIndexConfig.Store.CHRONICLE) {
          return new NameIndexChronicleStore(cfg);
        }
        return new NameIndexMapDBStore(DBMaker.memoryDB(), cfg.kryoPoolSize);

      } else {
        if(!cfg.file.exists()) {
          FileUtils.forceMkdirParent(cfg.file);
          LOG.info("Create new persistent {} names index at {}", cfg.type, cfg.file.getAbsolutePath());
        } else {
          LOG.info("Use persistent {} names index at {}", cfg.type, cfg.file.getAbsolutePath());
        }
        if (cfg.type == NamesIndexConfig.Store.CHRONICLE) {
          return new NameIndexChronicleStore(cfg);
        } else {
          DBMaker.Maker maker = DBMaker
            .fileDB(cfg.file)
            .fileMmapEnableIfSupported();
          return new NameIndexMapDBStore(maker, cfg.file, cfg.kryoPoolSize);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
