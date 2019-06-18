package org.col.dao;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameUsageMapper;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingDao {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingDao.class);
  
  private final SqlSession session;
  private final NameUsageMapper uMapper;
  private final NameMapper nMapper;
  
  
  public MatchingDao(SqlSession sqlSession) {
    this.session = sqlSession;
    uMapper = session.getMapper(NameUsageMapper.class);
    nMapper = session.getMapper(NameMapper.class);
  }
  
  public List<? extends NameUsage> matchDataset(SimpleName name, int datasetKey) {
    List<NameUsageBase> matches = new ArrayList<>();
    // https://github.com/Sp2000/colplus-backend/issues/283
    for (NameUsageBase t : uMapper.listByName(datasetKey, name.getName(), name.getRank())) {
      // take authorship, code, status and parent as optional filters, i.e. if null accept any value
      if (StringUtils.trimToNull(name.getAuthorship()) != null && !Objects.equals(name.getAuthorship(), t.getName().authorshipComplete())) {
        continue;
      }
      if (name.getStatus() != null && !Objects.equals(name.getStatus(), t.getStatus())) {
        continue;
      }
      if (name.getCode() != null && !Objects.equals(name.getCode(), t.getName().getCode())) {
        continue;
      }
      matches.add(t);
    }
    if (!matches.isEmpty() && name.getParent() != null) {
      Iterator<NameUsageBase> iter = matches.iterator();
      while(iter.hasNext()) {
        NameUsageBase u = iter.next();
        // https://github.com/Sp2000/colplus-backend/issues/349
        Name parent = nMapper.getByUsage(datasetKey, u.getParentId());
        if (parent == null || !name.getParent().equalsIgnoreCase(parent.getScientificName())) {
          iter.remove();
        }
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
