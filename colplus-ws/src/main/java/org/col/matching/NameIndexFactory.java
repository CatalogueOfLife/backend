package org.col.matching;

import java.io.File;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.vocab.Datasets;
import org.col.matching.authorship.AuthorComparator;
import org.col.api.model.Name;
import org.col.api.model.NameMatch;
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
  
  public static NameIndex memory(SqlSessionFactory sqlFactory) {
    return memory(Datasets.DRAFT_COL, sqlFactory);
  }
  
  public static NameIndex memory(int datasetKey, SqlSessionFactory sqlFactory) {
    return new NameIndexMapDB(DBMaker.memoryDB(), AuthorComparator.createWithAuthormap(), datasetKey, sqlFactory);
  }
  
  public static NameIndex persistent(File location, SqlSessionFactory sqlFactory) {
    return persistent(Datasets.DRAFT_COL, location, sqlFactory);
  }
  
  /**
   * Creates or opens a persistent mapdb names index.
   */
  public static NameIndex persistent(int datasetKey, File location, SqlSessionFactory sqlFactory) {
    DBMaker.Maker maker = DBMaker
        .fileDB(location)
        .fileMmapEnableIfSupported();
    return new NameIndexMapDB(maker, AuthorComparator.createWithAuthormap(), datasetKey, sqlFactory);
  }
  
}
