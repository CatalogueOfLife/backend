package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotEmpty;

import com.google.common.collect.Lists;
import org.col.api.vocab.Coverage;
import org.col.api.vocab.License;

/**
 * A citable source for a CoL data provider
 */
public class ColSource implements SourceMetadata {
	private Integer key;
	private Integer datasetKey;
	@NotEmpty
	private String title;
	private String alias;
	private String description;
	private String organisation;
	private String contactPerson;
	private List<String> authorsAndEditors = Lists.newArrayList();
	private License license;
	private String version;
	private LocalDate released;
	private URI homepage;
	private URI logo;
	private String group;
	private Coverage coverage;
	private String citation;
	private Integer livingSpeciesCount;
	private Integer livingInfraspecificCount;
	private Integer extinctSpeciesCount;
	private Integer extinctInfraspecificCount;
	private Integer synonymsCount;
	private Integer vernacularsCount;
	private Integer namesCount;
  private LocalDateTime created;
	private LocalDateTime modified;

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

	public Integer getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(Integer datasetKey) {
		this.datasetKey = datasetKey;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public void setTitle(String title) {
		this.title = title;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String getOrganisation() {
		return organisation;
	}

	@Override
	public void setOrganisation(String organisation) {
		this.organisation = organisation;
	}

	@Override
	public String getContactPerson() {
		return contactPerson;
	}

	@Override
	public void setContactPerson(String contactPerson) {
		this.contactPerson = contactPerson;
	}

	@Override
	public List<String> getAuthorsAndEditors() {
		return authorsAndEditors;
	}

	@Override
	public void setAuthorsAndEditors(List<String> authorsAndEditors) {
		this.authorsAndEditors = authorsAndEditors;
	}

	public License getLicense() {
		return license;
	}

	public void setLicense(License license) {
		this.license = license;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public void setVersion(String version) {
		this.version = version;
	}

	@Override
	public LocalDate getReleased() {
		return released;
	}

	@Override
	public void setReleased(LocalDate released) {
		this.released = released;
	}

	@Override
	public URI getHomepage() {
		return homepage;
	}

	@Override
	public void setHomepage(URI homepage) {
		this.homepage = homepage;
	}

	public URI getLogo() {
		return logo;
	}

	public void setLogo(URI logo) {
		this.logo = logo;
	}

	/**
	 * English name for the taxonomic group dealt by this source
	 */
	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public Coverage getCoverage() {
		return coverage;
	}

	public void setCoverage(Coverage coverage) {
		this.coverage = coverage;
	}

	public String getCitation() {
		return citation;
	}

	public void setCitation(String citation) {
		this.citation = citation;
	}

	public Integer getLivingSpeciesCount() {
		return livingSpeciesCount;
	}

	public void setLivingSpeciesCount(Integer livingSpeciesCount) {
		this.livingSpeciesCount = livingSpeciesCount;
	}

	public Integer getLivingInfraspecificCount() {
		return livingInfraspecificCount;
	}

	public void setLivingInfraspecificCount(Integer livingInfraspecificCount) {
		this.livingInfraspecificCount = livingInfraspecificCount;
	}

	public Integer getExtinctSpeciesCount() {
		return extinctSpeciesCount;
	}

	public void setExtinctSpeciesCount(Integer extinctSpeciesCount) {
		this.extinctSpeciesCount = extinctSpeciesCount;
	}

	public Integer getExtinctInfraspecificCount() {
		return extinctInfraspecificCount;
	}

	public void setExtinctInfraspecificCount(Integer extinctInfraspecificCount) {
		this.extinctInfraspecificCount = extinctInfraspecificCount;
	}

	public Integer getSynonymsCount() {
		return synonymsCount;
	}

	public void setSynonymsCount(Integer synonymsCount) {
		this.synonymsCount = synonymsCount;
	}

	public Integer getVernacularsCount() {
		return vernacularsCount;
	}

	public void setVernacularsCount(Integer vernacularsCount) {
		this.vernacularsCount = vernacularsCount;
	}

	public Integer getNamesCount() {
		return namesCount;
	}

	public void setNamesCount(Integer namesCount) {
		this.namesCount = namesCount;
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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ColSource colSource = (ColSource) o;
		return Objects.equals(key, colSource.key) &&
				Objects.equals(datasetKey, colSource.datasetKey) &&
				Objects.equals(title, colSource.title) &&
				Objects.equals(alias, colSource.alias) &&
				Objects.equals(description, colSource.description) &&
				Objects.equals(organisation, colSource.organisation) &&
				Objects.equals(contactPerson, colSource.contactPerson) &&
				Objects.equals(authorsAndEditors, colSource.authorsAndEditors) &&
				license == colSource.license &&
				Objects.equals(version, colSource.version) &&
				Objects.equals(released, colSource.released) &&
				Objects.equals(homepage, colSource.homepage) &&
				Objects.equals(logo, colSource.logo) &&
				Objects.equals(group, colSource.group) &&
				coverage == colSource.coverage &&
				Objects.equals(citation, colSource.citation) &&
				Objects.equals(livingSpeciesCount, colSource.livingSpeciesCount) &&
				Objects.equals(livingInfraspecificCount, colSource.livingInfraspecificCount) &&
				Objects.equals(extinctSpeciesCount, colSource.extinctSpeciesCount) &&
				Objects.equals(extinctInfraspecificCount, colSource.extinctInfraspecificCount) &&
				Objects.equals(synonymsCount, colSource.synonymsCount) &&
				Objects.equals(vernacularsCount, colSource.vernacularsCount) &&
				Objects.equals(namesCount, colSource.namesCount) &&
				Objects.equals(created, colSource.created) &&
				Objects.equals(modified, colSource.modified);
	}

	@Override
	public int hashCode() {

		return Objects.hash(key, datasetKey, title, alias, description, organisation, contactPerson, authorsAndEditors, license, version, released, homepage, logo, group, coverage, citation, livingSpeciesCount, livingInfraspecificCount, extinctSpeciesCount, extinctInfraspecificCount, synonymsCount, vernacularsCount, namesCount, created, modified);
	}

	@Override
	public String toString() {
		return "ColSource " + key + ": " + alias;
	}
}
