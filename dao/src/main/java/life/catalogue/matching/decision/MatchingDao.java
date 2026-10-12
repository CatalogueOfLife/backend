package life.catalogue.matching.decision;

import com.google.common.annotations.VisibleForTesting;

import life.catalogue.api.model.*;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.parser.NameParser;

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
  private static final AuthorComparator AUTHOR_COMPARATOR = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
  /**
   * The number of classification levels a parent name is looked for, i.e. the direct parent plus 2 inserted levels.
   * Any ancestor would let a stray placed directly under an order match every homonym in that order.
   */
  @VisibleForTesting
  static final int PARENT_LEVELS = 3;
  
  private final SqlSession session;
  private final NameUsageMapper uMapper;

  
  public MatchingDao(SqlSession sqlSession) {
    this.session = sqlSession;
    uMapper = session.getMapper(NameUsageMapper.class);
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
   * Strictly matches a simple name to name usages from a given dataset.
   * Name and rank must be equal, authorship, status, code and parent are optional filters which only apply if the
   * simple name has them. Authorship and parent are compared leniently, see {@link #authorshipDiffers} and {@link #parentDiffers}.
   * @param name
   * @param datasetKey
   */
  public MatchingResult matchDataset(SimpleName name, int datasetKey) {
    var result = new MatchingResult(name);
    // https://github.com/Sp2000/colplus-backend/issues/283
    for (NameUsageBase t : uMapper.listByName(datasetKey, name.getName(), name.getRank(), new Page(0,1000))) {
      if (authorshipDiffers(name.getAuthorship(), t.getName())) {
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
      if (StringUtils.isNotBlank(name.getParent()) && parentDiffers(name.getParent(), t, datasetKey)) {
        result.ignore(t, "Parent differs");
        continue;
      }
      result.addMatch(t);
    }
    return result;
  }

  /**
   * Only a contradicting authorship rules a usage out, a missing one on either side never does.
   * An authorship that cannot be parsed has to be equal once normalised.
   */
  private static boolean authorshipDiffers(@Nullable String authorship, Name n) {
    if (StringUtils.isBlank(authorship) || StringUtils.isBlank(n.getAuthorship())) {
      return false;
    }
    var parsed = NameParser.PARSER.parseAuthorship(authorship);
    if (parsed.isPresent() && n.hasAuthorship()) {
      Name n2 = new Name();
      n2.setCombinationAuthorship(parsed.get().getCombinationAuthorship());
      n2.setBasionymAuthorship(parsed.get().getBasionymAuthorship());
      if (n2.hasAuthorship()) {
        return AUTHOR_COMPARATOR.compare(n2, n) == Equality.DIFFERENT;
      }
    }
    return !norm(authorship).equals(norm(n.getAuthorship()));
  }

  /**
   * The parent of a usage is given either as the id of the parent usage or as its scientific name, the latter being
   * what decisions persist as it survives a re-import with new ids. For synonyms the accepted name is the parent.
   * A name is compared leniently, so a decision survives a source inserting or removing a rank level:
   * the parent name may be found up to {@link #PARENT_LEVELS} levels up the classification,
   * and a parent that does not exist at all anymore in the dataset does not contradict either.
   * A homonym placed elsewhere in the tree does, which is what keeps a block on a stray from moving onto the real taxon:
   * https://github.com/CatalogueOfLife/backend/issues/1639
   */
  private boolean parentDiffers(String parent, NameUsageBase u, int datasetKey) {
    if (parent.equalsIgnoreCase(u.getParentId())) {
      return false;
    }
    final DSID<String> key = DSID.of(datasetKey, null);
    String pid = u.getParentId();
    for (int level = 0; level < PARENT_LEVELS && pid != null; level++) {
      SimpleName p = uMapper.getSimple(key.id(pid));
      if (p == null) {
        break;
      }
      if (parent.equalsIgnoreCase(p.getName())) {
        return false;
      }
      pid = p.getParent();
    }
    // the parent exists, but elsewhere?
    return uMapper.exists(key.id(parent))
      || !uMapper.listByName(datasetKey, parent, null, new Page(0, 1)).isEmpty();
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
