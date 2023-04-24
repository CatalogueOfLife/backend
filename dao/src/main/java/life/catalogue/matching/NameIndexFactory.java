
package life.catalogue.matching;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.common.tax.AuthorshipNormalizer;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

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
      public void add(IndexName name) {
      }

      @Override
      public void reset() {
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
      public void add(IndexName name) { }

      @Override
      public void reset() {
      }

      @Override
      public boolean hasStarted() {
        return true;
      }

    };
  }

  /**
   * Returns a persistent index if location is given, otherwise an in memory one
   * @param verifyIndex if true the index is verified and rebuild if not consistent with the database
   */
  public static NameIndexImpl persistentOrMemory(@Nullable File location, SqlSessionFactory sqlFactory, AuthorshipNormalizer aNormalizer, boolean verifyIndex) throws IOException {
    NameIndexImpl ni;
    if (location == null) {
      ni = memory(sqlFactory, aNormalizer);
    } else {
      ni = persistent(location, sqlFactory, aNormalizer, verifyIndex);
    }
    return ni;
  }

  public static NameIndexImpl memory(SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer) {
    LOG.info("Use volatile in memory names index");
    NameIndexStore store = new NameIndexMapDBStore(DBMaker.memoryDB());
    return new NameIndexImpl(store, authorshipNormalizer, sqlFactory, true);
  }

  /**
   * Creates or opens a persistent mapdb names index for the names index.
   */
  public static NameIndexImpl persistent(File location, SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer, boolean verifyIndex) throws IOException {
    if (!location.exists()) {
      FileUtils.forceMkdirParent(location);
      LOG.info("Create persistent names index at {}", location.getAbsolutePath());
    } else {
      LOG.info("Use persistent names index at {}", location.getAbsolutePath());
    }
    DBMaker.Maker maker = DBMaker
        .fileDB(location)
        .fileMmapEnableIfSupported();
    NameIndexStore store = new NameIndexMapDBStore(maker, location);
    return new NameIndexImpl(store, authorshipNormalizer, sqlFactory, verifyIndex);
  }
  
}
