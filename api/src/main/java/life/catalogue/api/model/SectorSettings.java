package life.catalogue.api.model;

import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Objects;
import java.util.Set;

/**
 * A bundle of sector sync settings as held by a {@link SectorProfile}.
 * Every value is optional, see {@link SyncSettings}.
 */
public class SectorSettings implements SyncSettings {
  private Set<Rank> ranks;
  private Set<EntityType> entities;
  private Set<NameType> nameTypes;
  private Set<NomStatus> nameStatusExclusion;
  private String nameFilter;
  private Boolean extinctFilter;
  private Boolean copyAccordingTo;
  private Boolean removeOrdinals;
  private Boolean createImplicitNames;
  private NomCode code;
  private Sector.AuthorshipUpdate authorshipUpdate;
  private Set<Issue> issueExclusion;
  private Set<String> blockedNames;
  private Set<String> blockedNamePatterns;

  public SectorSettings() {
  }

  public static SectorSettings of(SyncSettings src) {
    var s = new SectorSettings();
    SyncSettings.copy(src, s);
    return s;
  }

  @Override
  public Set<Rank> getRanks() {
    return ranks;
  }

  @Override
  public void setRanks(Set<Rank> ranks) {
    this.ranks = ranks;
  }

  @Override
  public Set<EntityType> getEntities() {
    return entities;
  }

  @Override
  public void setEntities(Set<EntityType> entities) {
    this.entities = entities;
  }

  @Override
  public Set<NameType> getNameTypes() {
    return nameTypes;
  }

  @Override
  public void setNameTypes(Set<NameType> nameTypes) {
    this.nameTypes = nameTypes;
  }

  @Override
  public Set<NomStatus> getNameStatusExclusion() {
    return nameStatusExclusion;
  }

  @Override
  public void setNameStatusExclusion(Set<NomStatus> nameStatusExclusion) {
    this.nameStatusExclusion = nameStatusExclusion;
  }

  @Override
  public String getNameFilter() {
    return nameFilter;
  }

  @Override
  public void setNameFilter(String nameFilter) {
    this.nameFilter = nameFilter;
  }

  @Override
  public Boolean getExtinctFilter() {
    return extinctFilter;
  }

  @Override
  public void setExtinctFilter(Boolean extinctFilter) {
    this.extinctFilter = extinctFilter;
  }

  @Override
  public Boolean getCopyAccordingTo() {
    return copyAccordingTo;
  }

  @Override
  public void setCopyAccordingTo(Boolean copyAccordingTo) {
    this.copyAccordingTo = copyAccordingTo;
  }

  @Override
  public Boolean getRemoveOrdinals() {
    return removeOrdinals;
  }

  @Override
  public void setRemoveOrdinals(Boolean removeOrdinals) {
    this.removeOrdinals = removeOrdinals;
  }

  @Override
  public Boolean getCreateImplicitNames() {
    return createImplicitNames;
  }

  @Override
  public void setCreateImplicitNames(Boolean createImplicitNames) {
    this.createImplicitNames = createImplicitNames;
  }

  @Override
  public NomCode getCode() {
    return code;
  }

  @Override
  public void setCode(NomCode code) {
    this.code = code;
  }

  @Override
  public Sector.AuthorshipUpdate getAuthorshipUpdate() {
    return authorshipUpdate;
  }

  @Override
  public void setAuthorshipUpdate(Sector.AuthorshipUpdate authorshipUpdate) {
    this.authorshipUpdate = authorshipUpdate;
  }

  @Override
  public Set<Issue> getIssueExclusion() {
    return issueExclusion;
  }

  @Override
  public void setIssueExclusion(Set<Issue> issueExclusion) {
    this.issueExclusion = issueExclusion;
  }

  @Override
  public Set<String> getBlockedNames() {
    return blockedNames;
  }

  @Override
  public void setBlockedNames(Set<String> blockedNames) {
    this.blockedNames = blockedNames;
  }

  @Override
  public Set<String> getBlockedNamePatterns() {
    return blockedNamePatterns;
  }

  @Override
  public void setBlockedNamePatterns(Set<String> blockedNamePatterns) {
    this.blockedNamePatterns = blockedNamePatterns;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorSettings)) return false;
    SectorSettings that = (SectorSettings) o;
    return Objects.equals(ranks, that.ranks)
           && Objects.equals(entities, that.entities)
           && Objects.equals(nameTypes, that.nameTypes)
           && Objects.equals(nameStatusExclusion, that.nameStatusExclusion)
           && Objects.equals(nameFilter, that.nameFilter)
           && Objects.equals(extinctFilter, that.extinctFilter)
           && Objects.equals(copyAccordingTo, that.copyAccordingTo)
           && Objects.equals(removeOrdinals, that.removeOrdinals)
           && Objects.equals(createImplicitNames, that.createImplicitNames)
           && code == that.code
           && authorshipUpdate == that.authorshipUpdate
           && Objects.equals(issueExclusion, that.issueExclusion)
           && Objects.equals(blockedNames, that.blockedNames)
           && Objects.equals(blockedNamePatterns, that.blockedNamePatterns);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ranks, entities, nameTypes, nameStatusExclusion, nameFilter, extinctFilter, copyAccordingTo,
      removeOrdinals, createImplicitNames, code, authorshipUpdate, issueExclusion, blockedNames, blockedNamePatterns);
  }
}
