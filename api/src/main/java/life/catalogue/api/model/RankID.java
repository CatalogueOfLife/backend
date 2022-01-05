package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RankID extends DSIDValue<String> {
  private static final Logger LOG = LoggerFactory.getLogger(RankID.class);
  private static final String INC_SEDIS = "--incertae-sedis--";
  private static final Pattern ID_PATTERN = Pattern.compile("^(.+)"+INC_SEDIS+"([A-Z_]+)$", Pattern.CASE_INSENSITIVE);

  public static RankID parseID(DSID<String> id){
    if (id != null) {
      return parseID(id.getDatasetKey(), id.getId());
    }
    return null;
  }

  public static RankID parseID(int datasetKey, String id){
    if (id != null) {
      Matcher m = ID_PATTERN.matcher(id);
      if (m.find()) {
        try {
          return new RankID(datasetKey, m.group(1), Rank.valueOf(m.group(2).toUpperCase()));
        } catch (IllegalArgumentException e) {
          LOG.warn("Bad incertae sedis ID " + id);
        }
      }
    }
    return new RankID(datasetKey, id, null);
  }

  public static String buildID(String parentID, Rank rank) {
    return parentID + INC_SEDIS + rank.name();
  }

  public Rank rank;

  public RankID(int datasetKey, String id, Rank rank) {
    super(datasetKey, id);
    this.rank = rank;
  }
}
