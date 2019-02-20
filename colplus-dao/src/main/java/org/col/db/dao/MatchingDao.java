package org.col.db.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Decision;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.vocab.Datasets;
import org.col.db.mapper.TaxonMapper;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingDao {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingDao.class);
  
  private final SqlSession session;
  private final TaxonMapper tMapper;
  
  
  public MatchingDao(SqlSession sqlSession) {
    this.session = sqlSession;
    tMapper = session.getMapper(TaxonMapper.class);
  }
  
  public List<Taxon> match(Decision decision, int sector) {
    //TODO
    return null;
  }
  public List<Taxon> match(SimpleName name, int sector) {
    return match(name.getName(), name.getAuthorship(), name.getRank(), sector);
  }
  
  public List<Taxon> match(String name, @Nullable String authorship, @Nullable Rank rank, int sector) {
    List<Taxon> matches = new ArrayList<>();
    for (Taxon t : tMapper.listByNameAndSector(Datasets.DRAFT_COL, sector, name, rank)) {
      if (Objects.equals(authorship, t.getName().authorshipComplete())) {
        matches.add(t);
      }
    }
    return matches;
  }
  
}
