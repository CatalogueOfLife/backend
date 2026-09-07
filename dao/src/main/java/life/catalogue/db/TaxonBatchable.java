package life.catalogue.db;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Fetches the entities of many taxa at once.
 *
 * A filtered export knows exactly which taxa it wants. Asking for them one at a time is a round trip per
 * taxon per entity type, and streaming the whole dataset instead reads far more than it keeps - a subtree
 * export of the COL XRelease discarded 98% of the distribution rows it scanned. Batches do neither.
 *
 * Deliberately kept separate from {@link TaxonProcessable} rather than added to it: that interface's
 * MAPPERS list drags in mappers the export never touches, and TaxonProcessableTest calls every method
 * on all of them reflectively.
 *
 * @param <T> entity type
 */
public interface TaxonBatchable<T> {

  /**
   * @param ids taxon ids to fetch the entities of. Must not be empty.
   */
  List<T> listByTaxa(@Param("datasetKey") int datasetKey, @Param("ids") List<String> ids);
}
