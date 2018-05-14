package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.VerbatimRecord;

/**
 *
 */
public interface VerbatimRecordMapper {

  int count(@Param("datasetKey") int datasetKey);

  List<VerbatimRecord> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  VerbatimRecord get(@Param("datasetKey") int datasetKey, @Param("id") String id);

  VerbatimRecord getByName(@Param("nameKey") int nameKey);

  VerbatimRecord getByTaxon(@Param("taxonKey") int taxonKey);

  VerbatimRecord getByReference(@Param("referenceKey") int referenceKey);

  void create(@Param("rec") VerbatimRecord record,
              @Param("taxonKey") Integer taxonKey,
              @Param("nameKey") Integer nameKey,
              @Param("referenceKey") Integer referenceKey
  );

}

