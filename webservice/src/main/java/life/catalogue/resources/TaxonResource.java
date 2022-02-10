package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.SynonymException;
import life.catalogue.api.model.*;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;

import java.net.URI;
import java.util.List;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{key}/taxon")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TaxonResource extends AbstractDatasetScopedResource<String, Taxon, TaxonResource.TaxonSearchRequest> {
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
  @Override
  @Path("{id}")
  public Taxon get(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    var key = new DSIDValue<>(datasetKey, id);
    try {
      return dao.getOr404(key);
    } catch (SynonymException e) {
      URI location = URI.create("/dataset/"+e.acceptedKey.getDatasetKey()+"/taxon/" + e.acceptedKey.getId());
      throw ResourceUtils.redirect(location);
    }
  }

  @GET
  @Path("{id}/children")
  public ResultPage<Taxon> children(@PathParam("key") int datasetKey, @PathParam("id") String id, @Valid @BeanParam Page page) {
    return dao.getChildren(DSID.of(datasetKey, id), page);
  }
  
  @GET
  @Path("{id}/synonyms")
  public Synonymy synonyms(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return dao.getSynonymy(datasetKey, id);
  }
  
  @GET
  @Path("{id}/classification")
  public List<SimpleName> classification(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TaxonMapper.class).classificationSimple(DSID.of(datasetKey, id));
  }

  @GET
  @Path("{id}/classification2")
  public List<SimpleName> classification2(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return classification(datasetKey, id, session);
  }

  @GET
  @Path("{id}/info")
  public TaxonInfo info(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    TaxonInfo info = dao.getTaxonInfo(DSID.of(datasetKey, id));
    if (info == null) {
      throw NotFoundException.notFound(Taxon.class, datasetKey, id);
    }
    return info;
  }

  @GET
  @Path("{id}/source")
  public VerbatimSource source(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(VerbatimSourceMapper.class).get(DSID.of(datasetKey, id));
  }
}
