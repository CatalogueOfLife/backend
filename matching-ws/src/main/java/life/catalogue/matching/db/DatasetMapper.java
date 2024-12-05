package life.catalogue.matching.db;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.matching.model.Dataset;

import org.apache.ibatis.annotations.Param;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis mapper for dataset related queries
 */
public interface DatasetMapper {

  Optional<Dataset> getDataset(@Param("key") int key);

  /**
   * Looks up the dataset key of the latest release for a given project
   * @param key the project key
   * @param publicOnly if true only include public releases
   * @param origin the kind of release, can be null to allow any
   * @param ignore list of dataset key to ignore as the latest release
   * @return dataset key of the latest release or null if no release exists
   */
  Integer latestRelease(@Param("key") int key,
                        @Param("public") boolean publicOnly,
                        @Nullable @Param("ignore") List<Integer> ignore,
                        @Nullable @Param("origin") DatasetOrigin origin
  );

  default Integer latestRelease(int key, boolean publicOnly, DatasetOrigin origin) {
    return latestRelease(key, publicOnly, null, origin);
  }

  /**
   * Looks up the dataset key of a specific release attempt
   * @param key the project key
   * @param attempt the release attempt
   * @return dataset key of the requested release attempt or null if no release exists that matches
   */
  Integer releaseAttempt(@Param("key") int key, @Param("attempt") int attempt);
}
