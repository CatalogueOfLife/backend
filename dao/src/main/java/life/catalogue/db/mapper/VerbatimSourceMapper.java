package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SecondarySource;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper that manages the verbatim_source and verbatim_source_secondary tables.
 * Not that the CREATE method only inserts the main verbatim_source record and no secondary source.
 * Please use the dedicated insertSources method instead!
 */
public interface VerbatimSourceMapper extends Create<VerbatimSource>, CopyDataset, DatasetProcessable<VerbatimSource>,
  SectorProcessable<VerbatimSource> {

  VerbatimSource get(@Param("key") DSID<Integer> key);

  default VerbatimSource getByUsage(@Param("key") DSID<String> key) {
    return getByEntity(key, "name_usage");
  }

  default VerbatimSource getByName(@Param("key") DSID<String> key) {
    return getByEntity(key, "name");
  }

  default VerbatimSource getByReference(@Param("key") DSID<String> key) {
    return getByEntity(key, "reference");
  }

  VerbatimSource getByEntity(@Param("key") DSID<String> key, @Param("table") String table);

  default VerbatimSource addSources(VerbatimSource v) {
    if (v != null) {
      var snd = getSources(v);
      v.setSecondarySources(snd);
    }
    return v;
  }

  default VerbatimSource getWithSources(@Param("key") DSID<Integer> key) {
    VerbatimSource v = get(key);
    var snd = getSources(key);
    // it can happen that we only have secondary sources!
    if (v == null && snd != null && !snd.isEmpty())  {
      v = new VerbatimSource();
      v.setKey(key);
    }
    if (v != null) {
      v.setSecondarySources(snd);
    }
    return v;
  }

  VerbatimSource getIssues(@Param("key") DSID<Integer> key);

  int updateIssues(@Param("key") DSID<Integer> key, @Param("issues") Set<Issue> issues);

  int _addIssueInternal(@Param("key") DSID<Integer> key, @Param("issues") @NotNull Set<Issue> issues);

  /**
   * Add an issue to an existing verbatim source record or create a new one.
   */
  default void addIssue(@Param("key") DSID<Integer> key, Issue issue) {
    if (issue != null) {
      Set<Issue> issues = new HashSet<>();
      issues.add(issue);
      addIssues(key, issues);
    }
  }

  default void addIssues(@Param("key") DSID<Integer> key, Set<Issue> issues) {
    if (issues != null && !issues.isEmpty()) {
      int mod = _addIssueInternal(key, issues);
      if (mod < 1) {
        VerbatimSource v = new VerbatimSource(key.getDatasetKey(), null, key.getId(), null, null);
        v.getIssues().addAll(issues);
        create(v);
      }
    }
  }

  boolean exists(@Param("key") DSID<String> key);

  /**
   * @param key the key of the usage that has secondary sources
   * @param secondarySource the if of the secondary source record to add - must be an identifier for the secondarySourceEntity given
   * @param secondarySourceEntity the entity of the secondary source that is added, e.g. usage, name or reference
   * @param groups the set of information groups this secondary source is responsible for
   */
  default void insertSources(DSID<String> key, EntityType secondarySourceEntity, DSID<String> secondarySource, Set<InfoGroup> groups) {
    deleteSourceGroups(key, groups);
    if (!exists(key)) {
      VerbatimSource v = new VerbatimSource(key.getDatasetKey(), null, key.getId(), null, null);
      create(v);
    }
    insertSource(key, secondarySourceEntity, secondarySource, groups);
  }

  @MapKey("type")
  Map<InfoGroup, SecondarySource> getSources(@Param("key") DSID<Integer> key);

  List<SecondarySource> list(@Param("key") DSID<String> key);

  void insertSource(@Param("key") DSID<String> key, @Param("entity") EntityType sourceEntity, @Param("source") DSID<String> secondarySource, @Param("groups") Set<InfoGroup> groups);

  void deleteSourceGroups(@Param("key") DSID<String> key, @Param("groups") Set<InfoGroup> groups);

  void deleteSources(@Param("key") DSID<Integer> key);

  void delete(@Param("key") DSID<Integer> key);

  /**
   * Removes all issues from all verbatim source records of the given project
   * @param projectKey
   */
  void removeAllIssues(@Param("projectKey") int projectKey);
}

