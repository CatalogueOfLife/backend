package org.col.matching;

import java.util.List;
import java.util.Map;

import org.col.api.model.Name;
import org.col.authorship.AuthorComparator;
import org.mapdb.DB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameIndex {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndex.class);

  private final DB db;
  private final Map<String, List<Name>> names;
  private final AuthorComparator authComp;

  public NameIndex(DB db, Map<String, List<Name>> names, AuthorComparator authComp) {
    this.db = db;
    this.names = names;
    this.authComp = authComp;
  }
}
