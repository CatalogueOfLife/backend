package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import life.catalogue.db.CRUD;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

public interface PublisherMapper extends CRUD<UUID, Publisher> {

  List<Publisher> search(@Param("q") String q, @Param("page") Page page);

  /**
   * Lists publishers with the number of their datasets, optionally matching a full text query.
   * Datasets are counted like the dataset search does: deleted and temporary ones are left out,
   * private ones unless the user can read them.
   *
   * @param userKey the user reading, null for anonymous and the admin magic key for admins
   */
  List<PublisherInfo> list(@Nullable @Param("q") String q,
                           @Param("sortBy") PublisherInfo.SortBy sortBy,
                           @Nullable @Param("userKey") Integer userKey,
                           @Param("page") Page page);

  int count(@Nullable @Param("q") String q);

  /**
   * The datasets of a publisher grouped by origin, type, license and the private flag,
   * counted like the dataset search does.
   */
  List<DatasetGroup> datasetGroups(@Param("key") UUID key, @Nullable @Param("userKey") Integer userKey);

  class DatasetGroup {
    public DatasetOrigin origin;
    public DatasetType type;
    public License license;
    public boolean privat;
    public int count;
    public long usages;
  }
}
