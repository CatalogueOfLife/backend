package life.catalogue.matching.decision;

import life.catalogue.api.model.*;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
  
  /**
   * Strictly matches a simple name to name usages from a given dataset
   * @param name
   * @param datasetKey
   */
  List<? extends NameUsage> matchDataset(SimpleName name, int datasetKey) {
    List<NameUsageBase> matches = new ArrayList<>();
    // https://github.com/Sp2000/colplus-backend/issues/283
    for (NameUsageBase t : uMapper.listByName(datasetKey, name.getName(), name.getRank())) {
      // take authorship, code, status and parent as optional filters, i.e. if null accept any value
      if (StringUtils.trimToNull(name.getAuthorship()) != null && !name.getAuthorship().equalsIgnoreCase(t.getName().getAuthorship())) {
        continue;
      }
      if (name.getStatus() != null && !Objects.equals(name.getStatus(), t.getStatus())) {
        continue;
      }
      if (name.getCode() != null && t.getName().getCode() != null && !name.getCode().equals(t.getName().getCode())) {
        continue;
      }
      if (name.getParent() != null) {
        // synonyms already have their parent name. For taxa we need to look that up
        // https://github.com/Sp2000/colplus-backend/issues/349
        Name parent;
        if (t.isSynonym()) {
          Synonym s = (Synonym) t;
          parent = s.getAccepted().getName();
        } else {
          parent = nMapper.getByUsage(datasetKey, t.getParentId());
        }
        if (parent == null || !name.getParent().equalsIgnoreCase(parent.getScientificName())) {
          continue;
        }
      }
      matches.add(t);
    }
    return matches;
  }

  /**
   * Strictly matches a simple name to name usages from a given sector
   */
  public List<Taxon> matchSector(SimpleName name, Sector sector) {
    return matchSector(name.getName(), name.getAuthorship(), name.getRank(), sector);
  }

  /**
   * Strictly matches a name to name usages from a given sector
   */
  public List<Taxon> matchSector(Name name, Sector sector) {
    return matchSector(name.getScientificName(), name.getAuthorship(), name.getRank(), sector);
  }

  private List<Taxon> matchSector(String name, @Nullable String authorship, @Nullable Rank rank, Sector sector) {
    List<Taxon> matches = new ArrayList<>();
    for (NameUsage u : uMapper.listByName(sector.getDatasetKey(), name, rank)) {
      if (u.isTaxon()) {
        Taxon t = (Taxon) u;
        if (t.getSectorKey() != null && t.getSectorKey().equals(sector.getId())
            && Objects.equals(StringUtils.trimToNull(authorship), StringUtils.trimToNull(u.getName().getAuthorship()))) {
          matches.add(t);
        }
      }
    }
    return matches;
  }
  
}
