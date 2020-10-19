package life.catalogue.db.mapper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import org.apache.ibatis.annotations.Param;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DatasetProcessable refers to archived dataset metadata for projects only!
 */
public interface ProjectSourceMapper {

  /**
   * Copies a given dataset into the project archive.
   * The archive requires the source datasets key and project datasetKey combined to be unique.
   *
   * @param datasetKey the project the source dataset belongs to
   * @param d dataset to store as the projects source
   */
  default void create(int datasetKey, ArchivedDataset d){
    createInternal(new DatasetWithProjectKey(datasetKey, d));
  }

  void createInternal(DatasetWithProjectKey d);

  /**
   * Retrieves a projects source dataset from the archive by its key and the projects datasetKey.
   * @param key the source dataset key
   * @param datasetKey the project key
   */
  ArchivedDataset get(@Param("key") int key, @Param("datasetKey") int datasetKey);

  /**
   * @param datasetKey the release dataset key
   */
  List<ArchivedDataset> listReleaseSources(@Param("datasetKey") int datasetKey);

  /**
   * @param datasetKey the project dataset key
   */
  List<ArchivedDataset> listProjectSources(@Param("datasetKey") int datasetKey);

  int deleteByProject(@Param("datasetKey") int datasetKey);


  class DatasetWithProjectKey {
    public final int datasetKey;
    public final ArchivedDataset dataset;

    public DatasetWithProjectKey(int projectKey, ArchivedDataset dataset) {
      this.datasetKey = projectKey;
      this.dataset = dataset;
    }

    public int getDatasetKey() {
      return datasetKey;
    }

    public Integer getKey() {
      return dataset.getKey();
    }

    public DatasetType getType() {
      return dataset.getType();
    }

    public Integer getSourceKey() {
      return dataset.getSourceKey();
    }

    public Integer getImportAttempt() {
      return dataset.getImportAttempt();
    }

    public String getTitle() {
      return dataset.getTitle();
    }

    public String getDescription() {
      return dataset.getDescription();
    }

    public List<Person> getAuthors() {
      return dataset.getAuthors();
    }

    public List<Person> getEditors() {
      return dataset.getEditors();
    }

    public List<String> getOrganisations() {
      return dataset.getOrganisations();
    }

    public Person getContact() {
      return dataset.getContact();
    }

    public License getLicense() {
      return dataset.getLicense();
    }

    public String getVersion() {
      return dataset.getVersion();
    }

    public String getGeographicScope() {
      return dataset.getGeographicScope();
    }

    public LocalDate getReleased() {
      return dataset.getReleased();
    }

    public String getCitation() {
      return dataset.getCitation();
    }

    public URI getWebsite() {
      return dataset.getWebsite();
    }

    public URI getLogo() {
      return dataset.getLogo();
    }

    public DatasetOrigin getOrigin() {
      return dataset.getOrigin();
    }

    public String getNotes() {
      return dataset.getNotes();
    }

    @JsonIgnore
    public String getAliasOrTitle() {
      return dataset.getAliasOrTitle();
    }

    public String getAlias() {
      return dataset.getAlias();
    }

    public String getGroup() {
      return dataset.getGroup();
    }

    public Integer getConfidence() {
      return dataset.getConfidence();
    }

    public Integer getCompleteness() {
      return dataset.getCompleteness();
    }

    public LocalDateTime getCreated() {
      return dataset.getCreated();
    }

    public Integer getCreatedBy() {
      return dataset.getCreatedBy();
    }

    public LocalDateTime getModified() {
      return dataset.getModified();
    }

    public Integer getModifiedBy() {
      return dataset.getModifiedBy();
    }
  }

}
