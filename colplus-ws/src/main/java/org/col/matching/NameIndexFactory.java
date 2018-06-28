package org.col.matching;

import java.io.File;
import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.matching.authorship.AuthorComparator;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameIndexFactory {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexFactory.class);
  private static final int DATASET_KEY = 1;

  public static NameIndex memory(@Nullable SqlSessionFactory sqlFactory) {
    return new NameIndex(DBMaker.memoryDB(), AuthorComparator.createWithAuthormap(), DATASET_KEY, sqlFactory);
  }

  /**
   * Creates or opens a persistent names index.
   */
  public static NameIndex persistent(File location, @Nullable SqlSessionFactory sqlFactory) {
    DBMaker.Maker maker = DBMaker
        .fileDB(location)
        .fileMmapEnableIfSupported();
    return new NameIndex(maker, AuthorComparator.createWithAuthormap(), DATASET_KEY, sqlFactory);
  }

}
