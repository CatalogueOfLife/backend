package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.NameUsage;

/**
 * Mapper dealing with methods returning teh NameUsage interface,
 * i.e. a name in the context of either a Taxon, Synonym or BareName.
 *
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible
 * avoiding duplication.
 */
public interface NameUsageMapper {

	List<NameUsage> listByName(@Param("datasetKey") int datasetKey, @Param("nameKey") int nameKey);

	/**
	 * Iterates over all name usages of a given dataset and processes them with the supplied handler.
	 * This allows a single query to efficiently stream all its values without keeping them in memory.
	 */
	void processDataset(@Param("datasetKey") int datasetKey, ResultHandler<NameUsage> handler);

}
