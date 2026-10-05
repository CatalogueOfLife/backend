package life.catalogue.api.model;

import life.catalogue.api.vocab.DatasetType;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Which sectors of a project a {@link SectorProfile} applies to.
 * Fields are ANDed, the values within one field ORed. An empty field places no restriction,
 * so an empty selector matches every sector of the project.
 * Publisher and type are those of the subject dataset as it is now, not as it was when the sector was created.
 */
public class SectorSelector {
  private Set<Sector.Mode> modes = EnumSet.noneOf(Sector.Mode.class);
  private Set<DatasetType> datasetTypes = EnumSet.noneOf(DatasetType.class);
  private Set<UUID> publisherKeys = new HashSet<>();
  // matches the subject datasets published by any of the projects sector publishers, so it follows new ones
  private boolean anySectorPublisher;
  private Set<Integer> subjectDatasetKeys = new HashSet<>();
  private Set<Integer> sectorKeys = new HashSet<>();

  public Set<Sector.Mode> getModes() {
    return modes;
  }

  public void setModes(Set<Sector.Mode> modes) {
    this.modes = modes == null ? EnumSet.noneOf(Sector.Mode.class) : modes;
  }

  public Set<DatasetType> getDatasetTypes() {
    return datasetTypes;
  }

  public void setDatasetTypes(Set<DatasetType> datasetTypes) {
    this.datasetTypes = datasetTypes == null ? EnumSet.noneOf(DatasetType.class) : datasetTypes;
  }

  public Set<UUID> getPublisherKeys() {
    return publisherKeys;
  }

  public void setPublisherKeys(Set<UUID> publisherKeys) {
    this.publisherKeys = publisherKeys == null ? new HashSet<>() : publisherKeys;
  }

  public boolean isAnySectorPublisher() {
    return anySectorPublisher;
  }

  public void setAnySectorPublisher(boolean anySectorPublisher) {
    this.anySectorPublisher = anySectorPublisher;
  }

  public Set<Integer> getSubjectDatasetKeys() {
    return subjectDatasetKeys;
  }

  public void setSubjectDatasetKeys(Set<Integer> subjectDatasetKeys) {
    this.subjectDatasetKeys = subjectDatasetKeys == null ? new HashSet<>() : subjectDatasetKeys;
  }

  public Set<Integer> getSectorKeys() {
    return sectorKeys;
  }

  public void setSectorKeys(Set<Integer> sectorKeys) {
    this.sectorKeys = sectorKeys == null ? new HashSet<>() : sectorKeys;
  }

  /**
   * @return true if the selector matches every sector of the project
   */
  @JsonIgnore
  public boolean isEmpty() {
    return modes.isEmpty() && datasetTypes.isEmpty() && publisherKeys.isEmpty() && !anySectorPublisher
           && subjectDatasetKeys.isEmpty() && sectorKeys.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorSelector)) return false;
    SectorSelector that = (SectorSelector) o;
    return anySectorPublisher == that.anySectorPublisher
           && Objects.equals(modes, that.modes)
           && Objects.equals(datasetTypes, that.datasetTypes)
           && Objects.equals(publisherKeys, that.publisherKeys)
           && Objects.equals(subjectDatasetKeys, that.subjectDatasetKeys)
           && Objects.equals(sectorKeys, that.sectorKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(modes, datasetTypes, publisherKeys, anySectorPublisher, subjectDatasetKeys, sectorKeys);
  }
}
