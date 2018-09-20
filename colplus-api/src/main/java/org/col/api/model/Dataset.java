package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.col.api.vocab.*;
import org.gbif.nameparser.api.NomCode;

/**
 * Metadata about a dataset or a subset of it if parentKey is given.
 */
public class Dataset {
	private Integer key;
	private DatasetType type = DatasetType.OTHER;
	@NotEmpty
	private String title;
	private UUID gbifKey;
  private UUID gbifPublisherKey;
	private String description;
	private String organisation;
	private String contactPerson;
	private List<String> authorsAndEditors = Lists.newArrayList();
	private License license;
	private String version;
	private LocalDate released;
	private URI homepage;
	private DataFormat dataFormat;
	private URI dataAccess;
	private Frequency importFrequency;
  private NomCode code;
	private Integer size;
	private String notes;
	private Catalogue catalogue;
  private LocalDateTime created;
	private LocalDateTime modified;
	private LocalDateTime deleted;

	public Integer getKey() {
		return key;
	}

  public DatasetType getType() {
    return type;
  }

  public void setType(DatasetType type) {
    this.type = Preconditions.checkNotNull(type);
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

	/**
	 * Release date of the source data.
	 * The date can usually only be taken from metadata explicitly given by the source.
	 */
	public LocalDate getReleased() {
		return released;
	}

	public void setReleased(LocalDate released) {
		this.released = released;
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

	public Integer getSize() {
		return size;
	}

	public void setSize(Integer size) {
		this.size = size;
	}

	/**
	 * If the dataset participates in any of the 2 catalouge assemblies
	 * this is indicated here. All scrutinized sources will also be included as provisional ones.
	 *
	 * Dataset used to build the provisional catalogue will be trusted and insert their names into the names index.
	 */
	public Catalogue getCatalogue() {
		return catalogue;
	}

	public void setCatalogue(Catalogue catalogue) {
		this.catalogue = catalogue;
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
  public boolean hasDeletedDate() {
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
				Objects.equals(organisation, dataset.organisation) &&
				Objects.equals(contactPerson, dataset.contactPerson) &&
				Objects.equals(authorsAndEditors, dataset.authorsAndEditors) &&
				license == dataset.license &&
				Objects.equals(version, dataset.version) &&
				Objects.equals(released, dataset.released) &&
				Objects.equals(homepage, dataset.homepage) &&
				dataFormat == dataset.dataFormat &&
				Objects.equals(dataAccess, dataset.dataAccess) &&
				importFrequency == dataset.importFrequency &&
				code == dataset.code &&
				Objects.equals(notes, dataset.notes) &&
				catalogue == dataset.catalogue &&
				Objects.equals(created, dataset.created) &&
				Objects.equals(modified, dataset.modified) &&
				Objects.equals(deleted, dataset.deleted);
	}

	@Override
	public int hashCode() {

		return Objects.hash(key, type, title, gbifKey, gbifPublisherKey, description, organisation, contactPerson, authorsAndEditors, license, version, released, homepage, dataFormat, dataAccess, importFrequency, code, notes, catalogue, created, modified, deleted);
	}

	@Override
	public String toString() {
		return "Dataset " + key + ": " + title;
	}
}
