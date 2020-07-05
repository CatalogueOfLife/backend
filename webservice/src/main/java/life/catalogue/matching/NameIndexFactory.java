
package life.catalogue.matching;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.tax.AuthorshipNormalizer;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
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
      public void start() throws Exception {
      }

      @Override
      public void stop() throws Exception {
      }

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
  public static NameIndexImpl persistentOrMemory(@Nullable File location, SqlSessionFactory sqlFactory, AuthorshipNormalizer aNormalizer) throws IOException {
    NameIndexImpl ni;
    if (location == null) {
      ni = memory(sqlFactory, aNormalizer);
    } else {
      ni = persistent(location, sqlFactory, aNormalizer);
    }
    return ni;
  }

  public static NameIndexImpl memory(int datasetKey, SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer) {
    LOG.info("Use volatile in memory names index for dataset {}", datasetKey);
    NameIndexStore store = new NameIndexMapDBStore(DBMaker.memoryDB());
    return new NameIndexImpl(store, authorshipNormalizer, datasetKey, sqlFactory);
  }

  public static NameIndexImpl memory(SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer) {
    return memory(Datasets.NAME_INDEX, sqlFactory, authorshipNormalizer);
  }

  /**
   * Creates or opens a persistent mapdb names index for a given names index dataset.
   * @param datasetKey the dataset key to the names index in postgres
   */
  public static NameIndexImpl persistent(File location, int datasetKey, SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer) throws IOException {
    if (!location.exists()) {
      FileUtils.forceMkdirParent(location);
      LOG.info("Create persistent names index at {} for dataset {}", location.getAbsolutePath(), datasetKey);
    } else {
      LOG.info("Use persistent names index at {} for dataset {}", location.getAbsolutePath(), datasetKey);
    }
    DBMaker.Maker maker = DBMaker
        .fileDB(location)
        .fileMmapEnableIfSupported();
    NameIndexStore store = new NameIndexMapDBStore(maker, location);
    return new NameIndexImpl(store, authorshipNormalizer, datasetKey, sqlFactory);
  }

  /**
   * Creates or opens a persistent mapdb names index.
   */
  public static NameIndexImpl persistent(File location, SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer) throws IOException {
    return persistent(location, Datasets.NAME_INDEX, sqlFactory, authorshipNormalizer);
  }
  
}
