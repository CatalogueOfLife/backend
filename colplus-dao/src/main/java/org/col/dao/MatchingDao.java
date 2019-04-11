package org.col.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.vocab.Datasets;
import org.col.db.mapper.NameUsageMapper;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingDao {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingDao.class);
  
  private final SqlSession session;
  private final NameUsageMapper uMapper;
  
  
  public MatchingDao(SqlSession sqlSession) {
    this.session = sqlSession;
    uMapper = session.getMapper(NameUsageMapper.class);
  }
  
  public List<NameUsage> matchDataset(SimpleName name, int datasetKey) {
    List<NameUsage> matches = new ArrayList<>();
    // https://github.com/Sp2000/colplus-backend/issues/283
    // TODO: blocks decisions on synonyms
    for (NameUsage t : uMapper.listByName(datasetKey, name.getName(), name.getRank())) {
      if (Objects.equals(StringUtils.trimToNull(name.getAuthorship()), StringUtils.trimToNull(t.getName().authorshipComplete()))) {
        matches.add(t);
      }
    }
    return matches;
  }

  public List<Taxon> matchSector(SimpleName name, int sector) {
    return matchSector(name.getName(), name.getAuthorship(), name.getRank(), sector);
  }
  
  public List<Taxon> matchSector(Name name, int sector) {
    return matchSector(name.getScientificName(), name.getAuthorship(), name.getRank(), sector);
  }

  private List<Taxon> matchSector(String name, @Nullable String authorship, @Nullable Rank rank, int sector) {
    List<Taxon> matches = new ArrayList<>();
    for (NameUsage u : uMapper.listByName(Datasets.DRAFT_COL, name, rank)) {
      if (u.isTaxon()) {
        Taxon t = (Taxon) u;
        if (t.getSectorKey() != null && t.getSectorKey().equals(sector)
            && Objects.equals(StringUtils.trimToNull(authorship), StringUtils.trimToNull(u.getName().authorshipComplete()))) {
          matches.add(t);
        }
      }
    }
    return matches;
  }
  
}
