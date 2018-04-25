package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Name;
import org.col.api.model.NameSearch;
import org.col.api.model.NameUsage;
import org.col.api.model.Page;

import java.util.List;

/**
 * Mapper dealing with methods returning teh NameUsage interface,
 * i.e. a name in the context of either a Taxon, Synonym or BareName.
 *
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible
 * avoiding duplication.
 */
public interface NameUsageMapper {

	List<NameUsage> listByName(@Param("nameKey") int nameKey);

	int searchCount(@Param("q") NameSearch query);

	List<NameUsage> search(@Param("q") NameSearch query,
												 @Param("page") Page page);

}
