package org.col.resources;

import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.EditorialDecision;
import org.col.db.mapper.DecisionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/decision")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DecisionResource extends CRUDResource<EditorialDecision> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionResource.class);

  public DecisionResource() {
    super(EditorialDecision.class, DecisionMapper.class);
  }
  
  @GET
  public List<EditorialDecision> list(@Context SqlSession session,
                                      @QueryParam("datasetKey") Integer datasetKey,
                                      @QueryParam("sectorKey") Integer sectorKey) {
    if (sectorKey != null) {
      return session.getMapper(DecisionMapper.class).listBySector(sectorKey);
      
    } else if (datasetKey != null) {
      return session.getMapper(DecisionMapper.class).listByDataset(datasetKey);
      
    } else {
      throw new IllegalArgumentException("Parameter datasetKey or sectorKey is required");
    }
  }

}
