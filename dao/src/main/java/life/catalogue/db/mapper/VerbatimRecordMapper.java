package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;

import org.gbif.dwc.terms.Term;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 *
 */
public interface VerbatimRecordMapper extends Create<VerbatimRecord>, DatasetProcessable<VerbatimRecord>, CopyDataset {
  
  /**
   * @param datasetKey the required dataset key
   * @param types rowTypes to restrict to
   * @param terms optional list of terms and their values to filter by
   * @param termOp logical operator to combine multiple term filters
   * @param terms optional filter to return only records that have values for the given term
   * @param issues optional issues to filter by
   * @param q full text search query on term values
   */
  int count(@Param("datasetKey") int datasetKey,
            @Nullable @Param("types") Collection<Term> types,
            @Nullable @Param("termValues") Map<Term, String> termValues,
            @Param("termOp") LogicalOperator termOp,
            @Nullable @Param("terms") List<Term> terms,
            @Nullable @Param("issues") Collection<Issue> issues,
            @Nullable @Param("q") String q
  );
  
  /**
   * List verbatim records for a given dataset
   * @param datasetKey the required dataset key
   * @param types rowTypes to restrict to
   * @param terms optional list of terms and their values to filter by
   * @param termOp logical operator to combine multiple term filters
   * @param terms optional filter to return only records that have values for the given term
   * @param issues optional issues to filter by
   * @param q full text search query on term values
   * @param page
   * @return
   */
  List<VerbatimRecord> list(@Param("datasetKey") int datasetKey,
                            @Nullable @Param("types") Collection<Term> types,
                            @Nullable @Param("termValues") Map<Term, String> termValues,
                            @Param("termOp") LogicalOperator termOp,
                            @Nullable @Param("terms") List<Term> terms,
                            @Nullable @Param("issues") Collection<Issue> issues,
                            @Nullable @Param("q") String q,
                            @Param("page") Page page
  );
  
  VerbatimRecord get(@Param("key") DSID<Integer> key);
  
  IssueContainer getIssues(@Param("key") DSID<Integer> key);

  void update(@Param("key") DSID<Integer> key, @Param("issues") Set<Issue> issues);

  /**
   * Adds a single issue to an existing verbatim record.
   * @param key
   * @param issue to add
   */
  void addIssue(@Param("key") DSID<Integer> key, @Param("issue") Issue issue);
  
}

