package org.col.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DatasetType;
import org.col.api.vocab.Frequency;
import org.col.api.vocab.License;
import org.gbif.nameparser.api.NomCode;

import javax.validation.constraints.NotEmpty;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 *
 */
public class Dataset {
	private Integer key;
	private DatasetType type;
	@NotEmpty
	private String title;
	private UUID gbifKey;
  private UUID gbifPublisherKey;
	private String description;
  private NomCode code;
	private String organisation;
	private String contactPerson;
	private List<String> authorsAndEditors = Lists.newArrayList();
	private License license;
	private String version;
	private LocalDate releaseDate;
	private URI homepage;
	private DataFormat dataFormat;
	private URI dataAccess;
	private Frequency importFrequency;
	private String notes;
	private LocalDateTime created;
	private LocalDateTime modified;
	private LocalDateTime deleted;

	public Dataset() {
	}

	/**
	 * A key only dataset often used to represent just a foreign key to a dataset in
	 * other classes.
	 */
	public Dataset(Integer key) {
		this.key = key;
	}

	public Integer getKey() {
		return key;
	}

  public DatasetType getType() {
    return type;
  }

  public void setType(DatasetType type) {
    this.type = type;
  }

  public void setKey(Integer key) {
		this.key = key;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public UUID getGbifKey() {
		return gbifKey;
	}

	public void setGbifKey(UUID gbifKey) {
		this.gbifKey = gbifKey;
	}

  public UUID getGbifPublisherKey() {
    return gbifPublisherKey;
  }

  public void setGbifPublisherKey(UUID gbifPublisherKey) {
    this.gbifPublisherKey = gbifPublisherKey;
  }

  public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

  /**
   * The nomenclatural code followed in this dataset.
   * It will be used mostly as a hint to format names accordingly.
   * If the dataset contains mixed data from multiple codes keep this field null.
   * @return the nomenclatural code applying to all data in this dataset or null
   */
  public NomCode getCode() {
    return code;
  }

  public void setCode(NomCode code) {
    this.code = code;
  }

  public List<String> getAuthorsAndEditors() {
    return authorsAndEditors;
  }

  public void setAuthorsAndEditors(List<String> authorsAndEditors) {
    this.authorsAndEditors = authorsAndEditors;
  }

  public String getOrganisation() {
		return organisation;
	}

	public void setOrganisation(String organisation) {
		this.organisation = organisation;
	}

	public String getContactPerson() {
		return contactPerson;
	}

	public void setContactPerson(String contactPerson) {
		this.contactPerson = contactPerson;
	}

  public License getLicense() {
    return license;
  }

  public void setLicense(License license) {
    this.license = license;
  }

  public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public LocalDate getReleaseDate() {
		return releaseDate;
	}

	public void setReleaseDate(LocalDate releaseDate) {
		this.releaseDate = releaseDate;
	}

	public URI getHomepage() {
		return homepage;
	}

	public void setHomepage(URI homepage) {
		this.homepage = homepage;
	}

	public DataFormat getDataFormat() {
		return dataFormat;
	}

	public void setDataFormat(DataFormat dataFormat) {
		this.dataFormat = dataFormat;
	}

	public URI getDataAccess() {
		return dataAccess;
	}

	public void setDataAccess(URI dataAccess) {
		this.dataAccess = dataAccess;
	}

  public Frequency getImportFrequency() {
    return importFrequency;
  }

  public void setImportFrequency(Frequency importFrequency) {
    this.importFrequency = importFrequency;
  }

  public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public LocalDateTime getCreated() {
		return created;
	}

	public void setCreated(LocalDateTime created) {
		this.created = created;
	}

	public LocalDateTime getModified() {
		return modified;
	}

	public void setModified(LocalDateTime modified) {
		this.modified = modified;
	}

	public LocalDateTime getDeleted() {
		return deleted;
	}

	@JsonIgnore
  public boolean isDeleted() {
    return deleted != null;
  }

	public void setDeleted(LocalDateTime deleted) {
		this.deleted = deleted;
	}

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Dataset dataset = (Dataset) o;
    return Objects.equals(key, dataset.key) &&
        type == dataset.type &&
        Objects.equals(title, dataset.title) &&
        Objects.equals(gbifKey, dataset.gbifKey) &&
        Objects.equals(gbifPublisherKey, dataset.gbifPublisherKey) &&
        Objects.equals(description, dataset.description) &&
        code == dataset.code &&
        Objects.equals(organisation, dataset.organisation) &&
        Objects.equals(contactPerson, dataset.contactPerson) &&
        Objects.equals(authorsAndEditors, dataset.authorsAndEditors) &&
        license == dataset.license &&
        Objects.equals(version, dataset.version) &&
        Objects.equals(releaseDate, dataset.releaseDate) &&
        Objects.equals(homepage, dataset.homepage) &&
        dataFormat == dataset.dataFormat &&
        Objects.equals(dataAccess, dataset.dataAccess) &&
        importFrequency == dataset.importFrequency &&
        Objects.equals(notes, dataset.notes) &&
        Objects.equals(created, dataset.created) &&
        Objects.equals(modified, dataset.modified) &&
        Objects.equals(deleted, dataset.deleted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, type, title, gbifKey, gbifPublisherKey, description, code, organisation, contactPerson, authorsAndEditors, license, version, releaseDate, homepage, dataFormat, dataAccess, importFrequency, notes, created, modified, deleted);
  }

  @Override
	public String toString() {
		return "Dataset " + key + ": " + title;
	}
}
