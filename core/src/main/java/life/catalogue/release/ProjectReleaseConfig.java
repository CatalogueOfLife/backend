package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetType;

import java.util.ArrayList;
import java.util.List;

public class ProjectReleaseConfig {


  public static class MetadataConfig {
    /**
     * Template used to build a new release alias.
     */
    public String alias;

    /**
     * Release title with project variables allowed to be applied to a release
     */
    public String title;

    /**
     * Template used to build a new release version.
     */
    public String version;

    /**
     * Template used to build a new release description.
     */
    public String description;
    public List<String> keyword;
    public Dataset.UrlDescription conversion = new Dataset.UrlDescription();
    public Integer confidence;
    public Integer completeness;
    public String geographicScope;
    public String taxonomicScope;
    public String temporalScope;

    /**
     * If true a release will include as its authors all authors of all it's sources.
     */
    public boolean addSourceAuthors;

    /**
     * Optional list of dataset types to exclude from sources to generate the release authors from.
     * E.g. ARTICLE to exclude all authors from Plazi and BDJ sources.
     */
    public List<DatasetType> authorSourceExclusion;

    /**
     * If true a release will include as its authors all contributors of the project (not source contributors).
     */
    public boolean addContributors;
  }

  public MetadataConfig metadata = new MetadataConfig();

  /**
   * If true a release will issue new DOIs to changed sources.
   */
  public boolean issueSourceDOIs = false;

  /**
   * If true a release will first delete all bare names from the project before it copies data.
   */
  public boolean removeBareNames = true;

  /**
   * If true a release will prepare exports for the entire release in all common formats.
   */
  public boolean prepareDownloads = false;

}
