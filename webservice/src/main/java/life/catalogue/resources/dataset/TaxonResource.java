package life.catalogue.resources.dataset;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Language;
import life.catalogue.api.vocab.TreatmentFormat;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dao.TaxonDao;
import life.catalogue.dao.TxtTreeDao;
import life.catalogue.db.mapper.*;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;
import life.catalogue.dw.jersey.filter.VaryAccept;
import life.catalogue.printer.JsonTreePrinter;

import org.gbif.nameparser.api.Rank;

import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

@Path("/dataset/{key}/taxon")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TaxonResource extends AbstractDatasetScopedResource<String, Taxon, TaxonResource.TaxonSearchRequest> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TaxonResource.class);
  private final SqlSessionFactory factory;
  private final TaxonDao dao;
  private final TxtTreeDao txtTreeDao;

  public TaxonResource(SqlSessionFactory factory, TaxonDao dao, TxtTreeDao txtTreeDao) {
    super(Taxon.class, dao);
    this.factory = factory;
    this.txtTreeDao = txtTreeDao;
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
  @Hidden // for building COL portal sitemaps only
  @Path("ids")
  @Produces(MediaType.TEXT_PLAIN)
  public Cursor<String> sitemap(@PathParam("key") int datasetKey, @Context SqlSession session) {
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    return num.processIds(datasetKey, false, null);
  }

  @GET
  @Override
  @VaryAccept
  @Path("{id}")
  public Taxon get(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    LoggingUtils.setDatasetMDC(datasetKey, getClass());
    LoggingUtils.setJobMDC(UUID.randomUUID(), getClass());
    var key = new DSIDValue<>(datasetKey, id);
    return dao.getOr404(key);
  }

  @GET
  @Path("{id}/synonyms")
  public Synonymy synonyms(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return dao.getSynonymy(datasetKey, id, null);
  }

  @GET
  @Path("{id}/metrics")
  public TaxonMetrics metrics(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return dao.getMetrics(datasetKey, id);
  }
  @GET
  @Path("{id}/classification")
  public List<SimpleName> classification(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    return dao.classificationSimple(DSID.of(datasetKey, id));
  }

  @GET
  @Hidden
  @Deprecated // use NameUsageResource instead
  @Path("{id}/info")
  public UsageInfo info(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    UsageInfo info = dao.getUsageInfo(DSID.of(datasetKey, id));
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
  public List<VernacularName> vernacular(@PathParam("key") int datasetKey, @PathParam("id") String id,
                                         @QueryParam("lang") String langCode,
                                         @Context SqlSession session) {
    // normalize lang codes
    var lang = Language.byCode(langCode);
    return session.getMapper(VernacularNameMapper.class).listByTaxonFiltered(DSID.of(datasetKey, id), lang == null ? null : lang.getCode());
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
  @Path("{id}/property")
  public List<TaxonProperty> property(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TaxonPropertyMapper.class).listByTaxon(DSID.of(datasetKey, id));
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

  @GET
  @Path("{id}/tree")
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR, Roles.REVIEWER})
  public Response txtree(@PathParam("key") int datasetKey, @PathParam("id") String id, @QueryParam("extended") boolean extended, @QueryParam("rank") Set<Rank> ranks) {
    StreamingOutput stream = os -> txtTreeDao.readTxtree(datasetKey, id, extended, ranks, os);
    return Response.ok(stream).build();
  }

  @POST
  @Path("{id}/tree")
  @Consumes(MediaType.TEXT_PLAIN)
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public int insertTxtree(@PathParam("key") int datasetKey,
                          @PathParam("id") String id,
                          @QueryParam("replace") boolean replace,
                          @Auth User user,
                          InputStream txtree) throws Exception {
    return txtTreeDao.insertTxtree(datasetKey, id, user, txtree, replace);
  }

  @GET
  @Path("{id}/breakdown")
  public Response breakdown(@PathParam("key") int datasetKey, @PathParam("id") String id) {
    StreamingOutput stream = os -> {
      try (Writer writer = UTF8IoUtils.writerFromStream(os);
           JsonTreePrinter printer = dao.childrenBreakdownPrinter(datasetKey, id, writer)
      ) {
        printer.print();
        writer.flush();
      }
    };
    return Response.ok(stream).build();
  }
}
