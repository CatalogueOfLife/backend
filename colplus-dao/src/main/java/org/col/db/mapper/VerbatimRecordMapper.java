package org.col.db.mapper;

import java.util.List;
import java.util.Map;
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
  
  /**
   * See list method for parameters
   */
  int count(@Param("datasetKey") int datasetKey,
            @Param("type") Term type,
            @Nullable @Param("terms") Map<Term, String> terms,
            @Nullable @Param("issues") List<Issue> issues
  );
  
  /**
   * List verbatim records for a given dataset
   * @param datasetKey the required dataset key
   * @param type rowType to restrict to
   * @param terms optional list of terms and their values to filter by
   * @param issues optional issues to filter by
   * @param page
   * @return
   */
  List<VerbatimRecord> list(@Param("datasetKey") int datasetKey,
                            @Nullable @Param("type") Term type,
                            @Nullable @Param("terms") Map<Term, String> terms,
                            @Nullable @Param("issues") List<Issue> issues,
                            @Param("page") Page page
  );
  
  VerbatimRecord get(@Param("datasetKey") int datasetKey, @Param("key") int key);
  
  void create(VerbatimRecord record);
  
}

