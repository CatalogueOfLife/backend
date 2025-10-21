package life.catalogue.release;

import jakarta.validation.constraints.NotNull;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.config.ReleaseAction;

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

    // the following cannot use template vars, just fixed configs
    public List<String> keyword;
    public Agent contact;
    public List<Agent> creator;
    public List<Agent> additionalCreators; // to be used with source authors and resorted in alphabetical order
    public List<Agent> editor;
    public Agent publisher;
    public List<Agent> contributor;

    public Dataset.UrlDescription conversion = new Dataset.UrlDescription();
    public Integer confidence; // 1-5 max
    public Integer completeness;
    public String geographicScope;
    public String taxonomicScope;
    public String temporalScope;

    /**
     * If true a release will append to its authors all authors (creators & editors) of all it's sources.
     */
    public boolean addSourceAuthors;

    /**
     * If true a release will append to its contributors all authors (creators & editors) of all it's sources.
     */
    public boolean addSourceContributors;

    /**
     * Optional list of dataset types to exclude from sources to generate the release authors from.
     * E.g. ARTICLE to exclude all authors from Plazi and BDJ sources.
     */
    public List<DatasetType> authorSourceExclusion;
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

  // list of dataset keys of releases to ignore (e.g. they contain bad ids)
  @NotNull
  public List<Integer> ignoredReleases = new ArrayList<>();

  // list of action hook URLs to be called after successful releases
  @NotNull
  public List<ReleaseAction> actions = new ArrayList<>();

  // list of action hook URLs to be called after successful publication of a release
  @NotNull
  public List<ReleaseAction> publishActions = new ArrayList<>();
}
