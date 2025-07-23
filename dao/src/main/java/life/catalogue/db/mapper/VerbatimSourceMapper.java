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

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper that manages the verbatim_source and verbatim_source_secondary tables.
 * Not that the CREATE method only inserts the main verbatim_source record and no secondary source.
 * Please use the dedicated insertSources method instead!
 */
public interface VerbatimSourceMapper extends Create<VerbatimSource>, CopyDataset, DatasetProcessable<VerbatimSource>,
  SectorProcessable<VerbatimSource> {

  int getMaxID(@Param("datasetKey") int datasetKey);

  VerbatimSource get(@Param("key") DSID<Integer> key);

  List<VerbatimSource> list(@Param("datasetKey") int datasetKey,
                            @Param("sourceDatasetKey") Integer sourceDatasetKey,
                            @Param("sectorKey") Integer sectorKey,
                            @Param("sourceEntity") EntityType sourceEntity,
                            @Param("secondarySourceKey") Integer secondarySourceKey,
                            @Param("secondarySourceGroup") InfoGroup secondarySourceGroup
  );

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

  default Integer getVSKeyByUsage(@Param("key") DSID<String> key) {
    return getVSKey(key, "name_usage");
  }

  default Integer getVSKeyByName(@Param("key") DSID<String> key) {
    return getVSKey(key, "name");
  }

  default Integer getVSKeyByReference(@Param("key") DSID<String> key) {
    return getVSKey(key, "reference");
  }

  Integer getVSKey(@Param("key") DSID<String> key, @Param("table") String table);

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
    v.setSecondarySources(snd);
    return v;
  }

  VerbatimSource getIssues(@Param("key") DSID<Integer> key);

  int updateIssues(@Param("key") DSID<Integer> key, @Param("issues") Set<Issue> issues);

  /**
   * Add an issue to an existing verbatim source record.
   */
  default void addIssue(@Param("key") DSID<Integer> key, Issue issue) {
    if (issue != null) {
      Set<Issue> issues = new HashSet<>();
      issues.add(issue);
      addIssues(key, issues);
    }
  }

  /**
   * Add some issues to an existing verbatim source record.
   */
  void addIssues(@Param("key") DSID<Integer> key, @Param("issues") Set<Issue> issues);

  boolean exists(@Param("key") DSID<Integer> key);

  /**
   * @param key the verbatim source that has secondary sources
   * @param secondarySource the if of the secondary source record to add - must be an identifier for the secondarySourceEntity given
   * @param groups the set of information groups this secondary source is responsible for
   */
  default void insertSources(DSID<Integer> key, DSID<String> secondarySource, Set<InfoGroup> groups) {
    deleteSourceGroups(key, groups);
    insertSource(key, secondarySource, groups);
  }

  /**
   * List all secondary sources for a given verbatim source
   */
  @MapKey("type")
  Map<InfoGroup, SecondarySource> getSources(@Param("key") DSID<Integer> key);

  void insertSource(@Param("key") DSID<Integer> key, @Param("source") DSID<String> secondarySource, @Param("groups") Set<InfoGroup> groups);

  void deleteSourceGroups(@Param("key") DSID<Integer> key, @Param("groups") Set<InfoGroup> groups);

  void deleteSources(@Param("key") DSID<Integer> key);

  void delete(@Param("key") DSID<Integer> key);

  /**
   * Removes all issues from all verbatim source records of the given project
   * @param projectKey
   */
  void removeAllIssues(@Param("projectKey") int projectKey);

  int deleteOrphans(@Param("datasetKey") int datasetKey);
}

