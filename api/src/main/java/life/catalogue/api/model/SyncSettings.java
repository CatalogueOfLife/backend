package life.catalogue.api.model;

import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * The settings that steer how a sector is synced into its project.
 * Implemented by {@link Sector} itself and by {@link SectorSettings}, the bundle a {@link SectorProfile} carries.
 * Null - and for sets an empty one - means "not set at this level", so whatever a lower level says shows through.
 * SectorSettingsResolver in the dao module layers built-in defaults, the matching profiles and the sector.
 */
public interface SyncSettings {

  Set<Rank> getRanks();
  void setRanks(Set<Rank> ranks);

  Set<EntityType> getEntities();
  void setEntities(Set<EntityType> entities);

  Set<NameType> getNameTypes();
  void setNameTypes(Set<NameType> nameTypes);

  /** A blocklist: unioned over all levels. */
  Set<NomStatus> getNameStatusExclusion();
  void setNameStatusExclusion(Set<NomStatus> nameStatusExclusion);

  /**
   * An optional regex. If given, only usages whose scientific name fully matches are synced.
   * Useful to sync only a subset of OTU/OTHER type names such as BOLD or UNITE SH names that can no longer be
   * isolated by name type alone.
   */
  String getNameFilter();
  void setNameFilter(String nameFilter);

  /** True only syncs extinct, false only extant taxa. */
  Boolean getExtinctFilter();
  void setExtinctFilter(Boolean extinctFilter);

  Boolean getCopyAccordingTo();
  void setCopyAccordingTo(Boolean copyAccordingTo);

  Boolean getRemoveOrdinals();
  void setRemoveOrdinals(Boolean removeOrdinals);

  Boolean getCreateImplicitNames();
  void setCreateImplicitNames(Boolean createImplicitNames);

  /** The nomenclatural code forced onto every synced name. */
  NomCode getCode();
  void setCode(NomCode code);

  /** HIERARCHY sectors only: whether to copy the source authorship onto matched existing names. */
  Sector.AuthorshipUpdate getAuthorshipUpdate();
  void setAuthorshipUpdate(Sector.AuthorshipUpdate authorshipUpdate);

  /** MERGE sectors only. A blocklist: unioned over all levels and with the XRelease config. */
  Set<Issue> getIssueExclusion();
  void setIssueExclusion(Set<Issue> issueExclusion);

  /** MERGE sectors only. A blocklist: unioned over all levels and with the XRelease config. */
  Set<String> getBlockedNames();
  void setBlockedNames(Set<String> blockedNames);

  /** MERGE sectors only. A blocklist: unioned over all levels and with the XRelease config. */
  Set<String> getBlockedNamePatterns();
  void setBlockedNamePatterns(Set<String> blockedNamePatterns);

  /**
   * Copies every setting from one holder onto another. Sets are copied, never shared.
   */
  static void copy(SyncSettings from, SyncSettings to) {
    to.setRanks(copy(from.getRanks(), Rank.class));
    to.setEntities(copy(from.getEntities(), EntityType.class));
    to.setNameTypes(copy(from.getNameTypes(), NameType.class));
    to.setNameStatusExclusion(copy(from.getNameStatusExclusion(), NomStatus.class));
    to.setNameFilter(from.getNameFilter());
    to.setExtinctFilter(from.getExtinctFilter());
    to.setCopyAccordingTo(from.getCopyAccordingTo());
    to.setRemoveOrdinals(from.getRemoveOrdinals());
    to.setCreateImplicitNames(from.getCreateImplicitNames());
    to.setCode(from.getCode());
    to.setAuthorshipUpdate(from.getAuthorshipUpdate());
    to.setIssueExclusion(copy(from.getIssueExclusion(), Issue.class));
    to.setBlockedNames(from.getBlockedNames() == null ? null : new HashSet<>(from.getBlockedNames()));
    to.setBlockedNamePatterns(from.getBlockedNamePatterns() == null ? null : new HashSet<>(from.getBlockedNamePatterns()));
  }

  // EnumSet.copyOf fails on an empty collection that is not an EnumSet itself
  private static <E extends Enum<E>> Set<E> copy(Set<E> set, Class<E> clazz) {
    if (set == null) return null;
    var copy = EnumSet.noneOf(clazz);
    copy.addAll(set);
    return copy;
  }
}
