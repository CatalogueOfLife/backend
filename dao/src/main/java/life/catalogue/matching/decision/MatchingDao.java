package life.catalogue.matching.decision;

import com.google.common.annotations.VisibleForTesting;

import life.catalogue.api.model.*;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.text.StringUtils.removePunctWS;

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

  @VisibleForTesting
  protected static String norm(String x) {
    x = removePunctWS(x);
    if (x != null) {
      x = x.toLowerCase();
    }
    return StringUtils.trimToEmpty(x);
  }

  /**
   * Strictly matches a simple name to name usages from a given dataset
   * @param name
   * @param datasetKey
   */
  public MatchingResult matchDataset(SimpleName name, int datasetKey) {
    var result = new MatchingResult(name);
    // https://github.com/Sp2000/colplus-backend/issues/283
    for (NameUsageBase t : uMapper.listByName(datasetKey, name.getName(), name.getRank(), new Page(0,1000))) {
      // take authorship, code, status and parent as optional filters, i.e. if null accept any value
      if (StringUtils.isNotBlank(name.getAuthorship()) && !norm(name.getAuthorship()).equals(norm(t.getName().getAuthorship()))) {
        result.ignore(t, "Authorship differs");
        continue;
      }
      if (name.getStatus() != null && !Objects.equals(name.getStatus(), t.getStatus())) {
        result.ignore(t, "Status differs");
        continue;
      }
      if (name.getCode() != null && t.getName().getCode() != null && !name.getCode().equals(t.getName().getCode())) {
        result.ignore(t, "Code differs");
        continue;
      }
      if (StringUtils.isNotBlank(name.getParent())) {
        // synonyms already have their parent name. For taxa we need to look that up
        // https://github.com/Sp2000/colplus-backend/issues/349
        Name parent;
        if (t.isSynonym()) {
          Synonym s = (Synonym) t;
          parent = s.getAccepted().getName();
        } else {
          parent = nMapper.getByUsage(datasetKey, t.getParentId());
        }
        if (parent == null || (
          // we sometimes have the parent ID or the name in the request - this should probably fixed elsewhere,
          // but we need to be graceful to allow both
          !name.getParent().equalsIgnoreCase(parent.getScientificName()) && !name.getParent().equalsIgnoreCase(t.getParentId())
        )) {
          result.ignore(t, "Parent differs");
          continue;
        }
      }
      result.addMatch(t);
    }
    return result;
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
    for (NameUsageBase u : uMapper.listByName(sector.getDatasetKey(), name, rank, new Page(0, 1000))) {
      if (u.isTaxon()) {
        Taxon t = (Taxon) u;
        if (t.getSectorKey() != null && t.getSectorKey().equals(sector.getId()) && authorshipMatches(authorship, u.getName().getAuthorship())) {
          matches.add(t);
        }
      }
    }
    return matches;
  }

  private boolean authorshipMatches(String a1, String a2) {
    if (StringUtils.trimToNull(a1) == null || StringUtils.trimToNull(a2) == null) {
      return true;
    }
    return Objects.equals(AuthorshipNormalizer.normalize(a1), AuthorshipNormalizer.normalize(a2));
  }
  
}
