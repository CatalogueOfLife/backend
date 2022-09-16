package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.TreatmentFormat;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import life.catalogue.dw.jersey.MoreHttpHeaders;
import life.catalogue.common.ws.MoreMediaTypes;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.poi.ss.formula.functions.T;
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
  @Path("ids")
  @Produces(MediaType.TEXT_PLAIN)
  public Cursor<String> sitemap(@PathParam("key") int datasetKey, @Context SqlSession session) {
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    return num.processIds(datasetKey, false);
  }

  @GET
  @Override
  @Path("{id}")
  public Taxon get(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    var key = new DSIDValue<>(datasetKey, id);
    return dao.getOr404(key);
  }
  
  @GET
  @Path("{id}/synonyms")
  public Synonymy synonyms(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return dao.getSynonymy(datasetKey, id, null);
  }
  
  @GET
  @Path("{id}/classification")
  public List<SimpleName> classification(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TaxonMapper.class).classificationSimple(DSID.of(datasetKey, id));
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
  @Path("{id}/treatment")
  public Treatment treatment(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return dao.getTreatment(DSID.of(datasetKey, id));
  }

  @GET
  @Produces({MediaType.TEXT_HTML, MediaType.TEXT_XML, MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_MARKDOWN})
  @Path("{id}/treatment")
  public Response treatmentAsHtml(@PathParam("key") int datasetKey, @PathParam("id") String id, @QueryParam("format") TreatmentFormat format) {
    var t = dao.getTreatment(DSID.of(datasetKey, id));
    if (t != null) {
      return Response.ok()
                     .type(ObjectUtils.coalesce(format, TreatmentFormat.HTML).getMediaType().withCharset(StandardCharsets.UTF_8.name()))
                     .encoding(StandardCharsets.UTF_8.name())
                     .entity(t.getDocument())
                     .build();
    }
    return null;
  }

  @GET
  @Path("{id}/vernacular")
  public List<VernacularName> vernacular(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(VernacularNameMapper.class).listByTaxon(DSID.of(datasetKey, id));
  }

  @GET
  @Path("{id}/distribution")
  public List<Distribution> distribution(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(DistributionMapper.class).listByTaxon(DSID.of(datasetKey, id));
  }

  @GET
  @Path("{id}/media")
  public List<Media> media(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(MediaMapper.class).listByTaxon(DSID.of(datasetKey, id));
  }

  @GET
  @Path("{id}/interaction")
  public List<SpeciesInteraction> interaction(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(SpeciesInteractionMapper.class).listByTaxon(DSID.of(datasetKey, id));
  }

  @GET
  @Path("{id}/relation")
  public List<TaxonConceptRelation> relations(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TaxonConceptRelationMapper.class).listByTaxon(DSID.of(datasetKey, id));
  }

  @GET
  @Path("{id}/source")
  public VerbatimSource source(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(VerbatimSourceMapper.class).get(DSID.of(datasetKey, id));
  }
}
