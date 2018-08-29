package org.col.db.mapper;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Dataset;
import org.col.api.model.Page;

public interface DatasetMapper {

	int count(@Nullable @Param("q") String q);

	List<Dataset> list(@Param("page") Page page);

	List<Dataset> search(@Nullable @Param("q") String q, @Param("page") Page page);

	/**
	 * list datasets which have not been imported before, ordered by date created.
   * @param limit maximum of datasets to return
	 */
	List<Dataset> listNeverImported(int limit);

  /**
   * list datasets which have already been imported before, but need a refresh.
   * @param limit maximum of datasets to return
   */
  List<Dataset> listToBeImported(int limit);

  Dataset get(@Param("key") int key);

	Dataset getByGBIF(@Param("key") UUID key);

	void create(Dataset dataset);

	int update(Dataset dataset);

	/**
	 * Marks a dataset as deleted
	 * 
	 * @param key
	 */
	int delete(@Param("key") int key);

}
