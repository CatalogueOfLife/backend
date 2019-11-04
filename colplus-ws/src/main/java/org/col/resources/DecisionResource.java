package org.col.resources;

import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.EditorialDecision;
import org.col.api.vocab.Datasets;
import org.col.dao.DecisionDao;
import org.col.db.mapper.DecisionMapper;
import org.col.es.name.index.NameUsageIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/decision")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DecisionResource extends AbstractDecisionResource<EditorialDecision> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionResource.class);
  
  public DecisionResource(SqlSessionFactory factory, NameUsageIndexService indexService) {
    super(EditorialDecision.class, new DecisionDao(factory, indexService), factory);
  }
  
  @GET
  public List<EditorialDecision> list(@Context SqlSession session,
                                      @QueryParam("catalogueKey") @DefaultValue(""+Datasets.DRAFT_COL) int catalogueKey,
                                      @QueryParam("datasetKey") Integer datasetKey,
                                      @QueryParam("id") String id) {
    return session.getMapper(DecisionMapper.class).listBySubjectDataset(catalogueKey, datasetKey, id);
  }
  
  @GET
  @Path("/broken")
  public List<EditorialDecision> broken(@Context SqlSession session,
                                        @QueryParam("catalogueKey") @DefaultValue(""+Datasets.DRAFT_COL) int catalogueKey,
                                        @QueryParam("datasetKey") Integer datasetKey) {
    DecisionMapper mapper = session.getMapper(DecisionMapper.class);
    return mapper.subjectBroken(catalogueKey, datasetKey);
  }
  
}
