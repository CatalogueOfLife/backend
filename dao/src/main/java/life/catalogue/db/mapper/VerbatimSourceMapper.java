package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.*;

import java.util.*;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;

import javax.validation.constraints.NotNull;

/**
 * Mapper that manages the verbatim_source and verbatim_source_secondary tables.
 * Not that the CREATE method only inserts the main verbatim_source record and no secondary source.
 * Please use the dedicated insertSources method instead!
 */
public interface VerbatimSourceMapper extends Create<VerbatimSource>, CopyDataset, DatasetProcessable<VerbatimSource>,
  SectorProcessable<VerbatimSource>, TaxonProcessable<VerbatimSource> {

  VerbatimSource get(@Param("key") DSID<String> key);

  default VerbatimSource getWithSources(@Param("key") DSID<String> key) {
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

  VerbatimSource getIssues(@Param("key") DSID<String> key);

  int updateIssues(@Param("key") DSID<String> key, @Param("issues") Set<Issue> issues);

  int _addIssueInternal(@Param("key") DSID<String> key, @Param("issues") @NotNull Set<Issue> issues);

  /**
   * Add an issue to an existing verbatim source record or create a new one.
   */
  default void addIssue(@Param("key") DSID<String> key, Issue issue) {
    if (issue != null) {
      Set<Issue> issues = new HashSet<>();
      issues.add(issue);
      addIssues(key, issues);
    }
  }

  default void addIssues(@Param("key") DSID<String> key, Set<Issue> issues) {
    if (issues != null && !issues.isEmpty()) {
      int mod = _addIssueInternal(key, issues);
      if (mod < 1) {
        VerbatimSource v = new VerbatimSource(key.getDatasetKey(), key.getId(), null, null);
        v.getIssues().addAll(issues);
        create(v);
      }
    }
  }

  default void insertSources(@Param("key") DSID<String> key, @Param("source") DSID<String> secondarySource, @Param("groups") Set<InfoGroup> groups) {
    for (var group : groups) {
      insertSource(key, secondarySource, group);
    }
  }

  class SecondarySource implements DSID<String> {
    private String id;
    private Integer datasetKey;
    private InfoGroup type;

    @Override
    public String getId() {
      return id;
    }

    @Override
    public void setId(String id) {
      this.id = id;
    }

    @Override
    public Integer getDatasetKey() {
      return datasetKey;
    }

    @Override
    public void setDatasetKey(Integer datasetKey) {
      this.datasetKey = datasetKey;
    }

    public InfoGroup getType() {
      return type;
    }

    public void setType(InfoGroup type) {
      this.type = type;
    }
  }

  @MapKey("type")
  Map<InfoGroup, SecondarySource> getSources(@Param("key") DSID<String> key);

  void insertSource(@Param("key") DSID<String> key, @Param("source") DSID<String> secondarySource, @Param("group") InfoGroup group);

  void deleteSources(@Param("key") DSID<String> key);

  void delete(@Param("key") DSID<String> key);

}

