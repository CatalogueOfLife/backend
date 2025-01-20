package life.catalogue.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Issue;

import org.gbif.nameparser.api.Rank;

import javax.annotation.Nullable;

import java.util.*;

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
