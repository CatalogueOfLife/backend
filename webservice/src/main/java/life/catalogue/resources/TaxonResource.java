package life.catalogue.resources;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.TaxonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/taxon")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TaxonResource extends AbstractDatasetScopedResource<String, Taxon, TaxonResource.TaxonSearchRequest> {
  private static String ROOT_PARAM = "root";
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TaxonResource.class);
  private final TaxonDao dao;
  
  public TaxonResource(TaxonDao dao) {
    super(Taxon.class, dao);
    this.dao = dao;
  }

  public static class TaxonSearchRequest {
    @QueryParam("root")
    boolean root;
  }

  @Override
  ResultPage<Taxon> searchImpl(int datasetKey, TaxonSearchRequest req, Page page) {
    return req.root ? dao.listRoot(datasetKey, page) : dao.list(datasetKey, page);
  }

  @GET
  @Path("{id}/children")
  public ResultPage<Taxon> children(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Valid @BeanParam Page page) {
    return dao.getChildren(DSID.key(datasetKey, id), page);
  }
  
  @GET
  @Path("{id}/synonyms")
  public Synonymy synonyms(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id) {
    return dao.getSynonymy(datasetKey, id);
  }
  
  @GET
  @Path("{id}/classification")
  public List<Taxon> classification(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TaxonMapper.class).classification(DSID.key(datasetKey, id));
  }
  
  @GET
  @Timed
  @Path("{id}/info")
  public TaxonInfo info(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id) {
    TaxonInfo info = dao.getTaxonInfo(datasetKey, id);
    if (info == null) {
      throw NotFoundException.idNotFound(Taxon.class, datasetKey, id);
    }
    return info;
  }
  
}
