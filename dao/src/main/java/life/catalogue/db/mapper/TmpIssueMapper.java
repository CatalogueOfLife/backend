package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.SecondarySource;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Issue;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.SectorProcessable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * Mapper that manages the verbatim_source and verbatim_source_secondary tables.
 * Not that the CREATE method only inserts the main verbatim_source record and no secondary source.
 * Please use the dedicated insertSources method instead!
 */
public interface TmpIssueMapper extends DatasetProcessable<VerbatimSource>, SectorProcessable<VerbatimSource> {

  default void createTmpIssuesTable(@Param("datasetKey") int datasetKey, @Nullable @Param("sectorKey") Integer sectorKey) {
    // Issues are stored differently in external and project / release datasets
    final var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin.isProjectOrRelease()) {
      createTmpIssuesTableFromVerbatimSource(datasetKey, sectorKey);
      createTmpSourcesTableFromVerbatimSource(datasetKey, sectorKey);
    } else {
      createTmpIssuesTableFromVerbatim(datasetKey, sectorKey);
      createTmpSourcesTableFromVerbatim(datasetKey, sectorKey);
    }
  }

  /**
   * Creates a temporary table tmp_usage_issues that combines all issues from various name usage related tables
   * into a single pair of usage id and non empty issues.
   *
   * Warning: This does not include verbatim_source records for projects and releases, use the VerbatimSourceMapper instead !
   * @param datasetKey
   * @param sectorKey optional sector to restrict the issues to
   */
  void createTmpIssuesTableFromVerbatim(@Param("datasetKey") int datasetKey, @Nullable @Param("sectorKey") Integer sectorKey);

  void createTmpIssuesTableFromVerbatimSource(@Param("datasetKey") int datasetKey, @Nullable @Param("sectorKey") Integer sectorKey);

  /**
   * Creates a temporary table tmp_usage_sources that aggregates all secondary source records
   * into a set of infogroups and source dataset keys.
   * @param datasetKey
   * @param sectorKey optional sector to restrict the covered usages to
   */
  void createTmpSourcesTableFromVerbatimSource(@Param("datasetKey") int datasetKey, @Nullable @Param("sectorKey") Integer sectorKey);

  /**
   * creates empty table...
   */
  void createTmpSourcesTableFromVerbatim(@Param("datasetKey") int datasetKey, @Nullable @Param("sectorKey") Integer sectorKey);

  /**
   * Process all issues of the temporary table tmp_usage_issues.
   * Make sure you created such a table for the given session beforehand!
   */
  Cursor<IssueContainer.SimpleWithID> processIssues();

}

