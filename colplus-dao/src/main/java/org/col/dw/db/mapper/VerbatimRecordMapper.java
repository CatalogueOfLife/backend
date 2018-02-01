package org.col.dw.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.dw.api.Page;
import org.col.dw.api.VerbatimRecord;

import java.util.List;

/**
 *
 */
public interface VerbatimRecordMapper {

  int count(@Param("datasetKey") int datasetKey);

  List<VerbatimRecord> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  VerbatimRecord get(@Param("datasetKey") int datasetKey, @Param("id") String id);

  VerbatimRecord getByName(@Param("nameKey") int nameKey);

  VerbatimRecord getByTaxon(@Param("taxonKey") int taxonKey);

  void create(@Param("rec") VerbatimRecord record,
              @Param("taxonKey") Integer taxonKey,
              @Param("nameKey") int nameKey
  );

}

