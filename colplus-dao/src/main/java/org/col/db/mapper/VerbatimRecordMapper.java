package org.col.db.mapper;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.Term;

/**
 *
 */
public interface VerbatimRecordMapper {

  int count(@Param("datasetKey") int datasetKey, @Param("type") Term type);

  List<VerbatimRecord> list(@Param("datasetKey") int datasetKey,
                            @Param("type") Term type,
                            @Nullable @Param("issue") Issue issue,
                            @Param("page") Page page
  );

  VerbatimRecord get(@Param("datasetKey") int datasetKey, @Param("key") int key);

  void create(VerbatimRecord record);

}

