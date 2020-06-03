
package life.catalogue.matching;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.tax.AuthorshipNormalizer;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mapdb.DBException;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

public class NameIndexFactory {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexFactory.class);
  
  /**
   * @return NameIndex that returns no match for any query
   */
  public static NameIndex passThru() {
    return new NameIndex() {
      @Override
      public NameMatch match(Name name, boolean allowInserts, boolean verbose) {
        return NameMatch.noMatch();
      }
      
      @Override
      public int size() {
        return 0;
      }
      
      @Override
      public void add(Name name) {
      }
      
    };
  }
  
  /**
   * Returns a persistent index if location is given, otherwise an in memory one
   */
  public static NameIndex persistentOrMemory(@Nullable File location, SqlSessionFactory sqlFactory, AuthorshipNormalizer aNormalizer) throws IOException {
    NameIndex ni;
    if (location == null) {
      ni = memory(sqlFactory, aNormalizer);
    } else {
      ni = persistent(location, sqlFactory, aNormalizer);
    }
    return ni;
  }
  
  public static NameIndex memory(SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer) {
    LOG.info("Use volatile in memory names index");
    NameIndexStore store = new NameIndexMapDBStore(DBMaker.memoryDB());
    return new NameIndexImpl(store, authorshipNormalizer, Datasets.NAME_INDEX, sqlFactory);
  }

  /**
   * Creates or opens a persistent mapdb names index.
   */
  public static NameIndex persistent(File location, SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer) throws IOException {
    if (!location.exists()) {
      FileUtils.forceMkdirParent(location);
      LOG.info("Create persistent names index at {}", location.getAbsolutePath());
    } else {
      LOG.info("Open persistent names index at {}", location.getAbsolutePath());
    }
    DBMaker.Maker maker = DBMaker
        .fileDB(location)
        .fileMmapEnableIfSupported();
    NameIndexStore store;
    try {
      store = new NameIndexMapDBStore(maker);
    } catch (DBException.DataCorruption e) {
      LOG.warn("NamesIndex mapdb was corrupt. Remove and rebuild index from scratch. {}", e.getMessage());
      location.delete();
      store = new NameIndexMapDBStore(maker);
    }
    LOG.info("Opened names index");
    return new NameIndexImpl(store, authorshipNormalizer, Datasets.NAME_INDEX, sqlFactory);
  }
  
}
