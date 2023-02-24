package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.tree.*;
import life.catalogue.dw.jersey.Redirect;
import life.catalogue.dw.jersey.filter.VaryAccept;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.exporter.ExportManager;

import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Streams;

import io.dropwizard.auth.Auth;

/**
 * Streams dataset or parts of it to the user.
 * Results are either compressed, existing archives (original ones or preprepared ones)
 * or rather lightweight streamed json or text responses.
 *
 * Query parameters are aligned with ExportRequest and ExportSearchRequest.
 */
@Path("/dataset/{key}/export")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetExportResource {
  private final DatasetImportDao diDao;
  private final SqlSessionFactory factory;
  private final NameUsageSearchService searchService;
  private final ExportManager exportManager;
  private final WsServerConfig cfg;
  private static final Object[][] EXPORT_HEADERS = new Object[1][];
  static {
    EXPORT_HEADERS[0] = new Object[]{"ID", "parentID", "status", "rank", "scientificName", "authorship", "label"};
  }

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetExportResource.class);

  public DatasetExportResource(SqlSessionFactory factory, NameUsageSearchService searchService, ExportManager exportManager, DatasetImportDao diDao, WsServerConfig cfg) {
    this.factory = factory;
    this.searchService = searchService;
    this.exportManager = exportManager;
    this.diDao = diDao;
    this.cfg = cfg;
  }

  @POST
  public UUID export(@PathParam("key") int key, @Valid ExportRequest req, @Auth User user) {
    if (req == null) req = new ExportRequest();
    req.setDatasetKey(key);
    req.setForce(false); // we don't allow to force exports from the public API
    return exportManager.submit(req, user.getKey());
  }

  /**
   * If no format is given the original source is returned.
   */
  @GET
  @VaryAccept
  // there are many unofficial mime types around for zip
  @Produces({
    MediaType.APPLICATION_OCTET_STREAM,
    MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
  })
  public Response download(@PathParam("key") int key, @QueryParam("format") DataFormat format) {
    if (format == null) {
      throw new IllegalArgumentException("Format parameter is required");
    }

    // an already existing export in the given format
    ExportRequest req = new ExportRequest(key, format);
    DatasetExport export = exportManager.exists(req);
    if (export != null) {
      return Redirect.temporary(cfg.job.downloadURI(export.getKey()));
    }

    throw new NotFoundException(key, format.getName() + " archive for dataset " + key + " not found");
  }

  public static class ExportQueryParams {
    @QueryParam("taxonID") String taxonID;
    @QueryParam("rank") Set<Rank> ranks;
    @QueryParam("minRank") Rank minRank;
    @QueryParam("maxRank") Rank maxRank;
    @QueryParam("synonyms") boolean synonyms;
    @QueryParam("extinct") Boolean extinct;
    @QueryParam("countBy") Rank countBy;

    void init() throws IllegalArgumentException {
      if (minRank != null || maxRank != null) {
        if (ranks == null || ranks.isEmpty()) {
          ranks = RankUtils.between(ObjectUtils.coalesce(minRank, Rank.UNRANKED), ObjectUtils.coalesce(maxRank, Rank.values()[0]), true);
        } else {
          throw new IllegalArgumentException("Parameters min/maxRank and rank are mutually exclusive");
        }
      }
    }

    public TreeTraversalParameter toTreeTraversalParameter(int datasetKey) {
      var ttp = TreeTraversalParameter.dataset(datasetKey);
      ttp.setTaxonID(taxonID);
      ttp.setSynonyms(synonyms);
      ttp.setExtinct(extinct);
      if (ranks != null && !ranks.isEmpty()) {
        ttp.setLowestRank(RankUtils.lowestRank(ranks));
      } else if (minRank != null) {
        ttp.setLowestRank(minRank);
      }
      return ttp;
    }
  }

  @GET
  @VaryAccept
  @Produces(MediaType.TEXT_PLAIN)
  public Response textTree(@PathParam("key") int key,
                           @BeanParam ExportQueryParams params,
                           @QueryParam("showID") boolean showID,
                           @Context SqlSession session) {
    params.init();
    StreamingOutput stream = os -> {
      Writer writer = new BufferedWriter(new OutputStreamWriter(os));
      TextTreePrinter printer = PrinterFactory.dataset(TextTreePrinter.class, params.toTreeTraversalParameter(key), params.ranks, params.countBy, searchService, factory, writer);
      if (showID) printer.showIDs();
      printer.print();
      writer.flush();
    };
    return Response.ok(stream).build();
  }

  @GET
  @VaryAccept
  @Produces(MediaType.APPLICATION_JSON)
  public Response simpleName(@PathParam("key") int key,
                             @QueryParam("flat") boolean flat,
                             @BeanParam ExportQueryParams params,
                             @Context SqlSession session) {
    params.init();
    StreamingOutput stream = os -> {
      Writer writer = new BufferedWriter(new OutputStreamWriter(os));
      AbstractTreePrinter printer;
      if (flat) {
        printer = PrinterFactory.dataset(JsonFlatPrinter.class, params.toTreeTraversalParameter(key), params.ranks, params.countBy, searchService, factory, writer);
      } else {
        printer = PrinterFactory.dataset(JsonTreePrinter.class, params.toTreeTraversalParameter(key), params.ranks, params.countBy, searchService, factory, writer);
      }
      printer.print();
      writer.flush();
    };
    return Response.ok(stream).build();
  }

  @GET
  @Deprecated
  @VaryAccept
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{id}")
  public Response simpleNameLegacy(@PathParam("key") int key,
                             @PathParam("id") String taxonID,
                             @QueryParam("flat") boolean flat,
                             @BeanParam ExportQueryParams params,
                             @Context SqlSession session) {
    params.init();
    params.taxonID = taxonID;
    return simpleName(key, flat, params, session);
  }

  @GET
  @VaryAccept
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> exportCsv(@PathParam("key") int datasetKey,
                                    @BeanParam ExportQueryParams params,
                                    @Context SqlSession session) {
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    params.init();
    return Stream.concat(
      Stream.of(EXPORT_HEADERS),
      Streams.stream(num.processTreeSimple(params.toTreeTraversalParameter(datasetKey)))
             .map(this::map)
    );
  }


  private Object[] map(SimpleName sn){
    return new Object[]{
      sn.getId(),
      sn.getParent(),
      sn.getStatus(),
      sn.getRank(),
      sn.getName(),
      sn.getAuthorship(),
      sn.getPhrase(),
      sn.getLabel()
    };
  }

}
