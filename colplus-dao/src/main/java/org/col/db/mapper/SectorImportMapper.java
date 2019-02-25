package org.col.db.mapper;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.SectorImport;

/**
 * The MyBatis mapper interface for SectorImport.
 */
public interface SectorImportMapper {
  
  SectorImport get(@Param("key") int sectorKey, @Param("attempt") int attempt);
  
  /**
   * Count all imports by their state
   */
  int count(@Param("key") @Nullable Integer sectorKey, @Param("states") Collection<SectorImport.State> states);
  
  /**
   * List all imports optionally filtered by their datasetKey and state(s).
   * Ordered by starting date from latest to historical.
   */
  List<SectorImport> list(@Param("key") @Nullable Integer sectorKey,
                           @Param("states") @Nullable Collection<SectorImport.State> states,
                           @Param("page") Page page);
  
  void create(@Param("imp") SectorImport sectorImport);
  
  
}
