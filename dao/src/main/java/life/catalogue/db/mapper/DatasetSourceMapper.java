package life.catalogue.db.mapper;

import kotlin.collections.EmptySet;

import life.catalogue.api.model.Dataset;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import life.catalogue.api.model.Sector;
import life.catalogue.dao.DatasetSourceDao;

import org.apache.ibatis.annotations.Param;

/**
 * DatasetProcessable refers to archived dataset metadata for projects only!
 * The table dataset_source only stores sources for releases, not the project itself.
 * The live project sources are determined based on the sector mappings alone and are the reason for the name of the mapper.
 */
public interface DatasetSourceMapper extends DatasetAgentMapper {

  class SourceDataset extends Dataset {
    private Set<Sector.Mode> sectorModes = EnumSet.noneOf(Sector.Mode.class);

    public SourceDataset(Dataset other) {
      super(other);
    }

    public SourceDataset() {
    }

    public Set<Sector.Mode> getSectorModes() {
      return sectorModes;
    }

    public void setSectorModes(Set<Sector.Mode> sectorModes) {
      this.sectorModes = sectorModes;
    }

    public boolean isMerged() {
      return sectorModes != null && sectorModes.contains(Sector.Mode.MERGE);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof SourceDataset)) return false;
      if (!super.equals(o)) return false;

      SourceDataset that = (SourceDataset) o;
      return Objects.equals(sectorModes, that.sectorModes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), sectorModes);
    }
  }

  /**
   * Copies a given dataset into the release source archive.
   * Source citations are NOT copied, this must be done using the CitationMapper!
   * The archive requires the source datasets key and release datasetKey combination to be unique.
   *
   * @param datasetKey the release the source dataset belongs to
   * @param d dataset to store as the projects source
   */
  void create(@Param("datasetKey") int datasetKey, @Param("obj") Dataset d);


  /**
   * Retrieves a single released source dataset from the archive
   * @param key the source dataset key
   * @param datasetKey the release dataset key. No project keys allowed!
   */
  SourceDataset getReleaseSource(@Param("key") int key, @Param("datasetKey") int datasetKey);

  /**
   * Retrieves a single source dataset for a project, reading either from the latest version
   * or the dataset archive if the last successful sync attempt was older.
   * @param key the source dataset key
   * @param projectKey the project dataset key. No release keys allowed!
   */
  SourceDataset getProjectSource(@Param("key") int key, @Param("datasetKey") int projectKey);

  /**
   * Lists all sources of a release as full datasets.
   * If possible use the less demanding listReleaseSourcesSimple()
   * @param datasetKey the release dataset key
   * @param inclPublisherSources if true includes all sources, if false excludes the sources which have a publisher configured
   */
  List<SourceDataset> listReleaseSources(@Param("datasetKey") int datasetKey,
                                   @Param("inclPublisherSources") boolean inclPublisherSources);

  /**
   * Same as listReleaseSources, but returns a stripped down Dataset instances.
   * See listProjectSourcesSimple for details
   * @param datasetKey the release dataset key
   * @param inclPublisherSources if true includes all sources, if false excludes the sources which have a publisher configured
   */
  List<SourceDataset> listReleaseSourcesSimple(@Param("datasetKey") int datasetKey,
                                                                @Param("inclPublisherSources") boolean inclPublisherSources);

  /**
   * Lists all project or release sources based on the sectors in the dataset,
   * retrieving metadata either from the latest version
   * or an archived copy depending on the import attempt of the last sync stored in the sectors.
   * This does not return datasets of sectors created by a sector publisher.
   * It does NOT rely on dataset_source records for releases and can be used to create them.
   * @param datasetKey the project/release key
   * @param inclPublisherSources if true includes all sources, if false excludes the sources which have a publisher configured
   */
  List<SourceDataset> listProjectSources(@Param("datasetKey") int datasetKey,
                                                          @Param("inclPublisherSources") boolean inclPublisherSources);

  /**
   * Same as listProjectSources above, but with stripped down Dataset instances without the following properties:
   *  - description
   *  - conversion
   *  - containerXXX properties
   *  - source bibliography
   *  - contributor
   *  - identifier
   *  - url_formatter
   * @param datasetKey the project/release key
   * @param inclPublisherSources if true includes all sources, if false excludes the sources which have a publisher configured
   */
  List<SourceDataset> listProjectSourcesSimple(@Param("datasetKey") int datasetKey,
                                         @Param("inclPublisherSources") boolean inclPublisherSources);

  /**
   * Deletes a single source dataset for the given release
   * @param key the source dataset key
   * @param datasetKey the release dataset key. No project keys allowed!
   */
  int delete(@Param("key") int key, @Param("datasetKey") int datasetKey);

  /**
   * Deletes all source datasets for the given release
   * @param datasetKey the release dataset key. No project keys allowed!
   */
  int deleteByRelease(@Param("datasetKey") int datasetKey);

}
