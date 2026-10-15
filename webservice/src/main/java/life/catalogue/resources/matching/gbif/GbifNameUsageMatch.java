package life.catalogue.resources.matching.gbif;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The response of the GBIF v2 species match service (https://github.com/gbif/matching-ws),
 * kept as a plain DTO so the CLB API takes no dependency on GBIF's models.
 * Enums are plain upper case strings, as GBIF renders them, not the lower case CLB enums.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GbifNameUsageMatch {
  public Usage usage;
  public Usage acceptedUsage;
  public List<RankedName> classification;
  public Diagnostics diagnostics = new Diagnostics();
  public boolean synonym;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Usage {
    public String key;
    public String name;
    public String canonicalName;
    public String authorship;
    public String rank;
    public String code;
    public String status;
    public String type = "SCIENTIFIC";
    public String formattedName;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class RankedName {
    public String key;
    public String name;
    public String rank;

    public RankedName() {
    }

    public RankedName(String key, String name, String rank) {
      this.key = key;
      this.name = name;
      this.rank = rank;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Diagnostics {
    public String matchType;
    public Integer confidence;
    public String note;
    public Long timeTaken;
    public List<GbifNameUsageMatch> alternatives;
  }

  /**
   * GBIF's metadata about the checklist a match service instance serves, see /match/gbif/metadata.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Metadata {
    public String created;
    public MainIndex mainIndex = new MainIndex();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class MainIndex {
    public String clbDatasetKey;
    public String datasetKey;
    public String datasetAlias;
    public String datasetTitle;
    public Integer nameUsageCount;
  }
}
