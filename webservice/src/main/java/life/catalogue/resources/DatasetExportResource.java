package life.catalogue.resources;

import io.swagger.v3.oas.annotations.Hidden;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.model.User;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dao.MetricsDao;
import life.catalogue.dw.jersey.Redirect;
import life.catalogue.dw.jersey.filter.VaryAccept;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.printer.*;

import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import java.io.Writer;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private final SqlSessionFactory factory;
  private final MetricsDao metricsDao;
  private final ExportManager exportManager;
  private final WsServerConfig cfg;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetExportResource.class);

  public DatasetExportResource(SqlSessionFactory factory, MetricsDao metricsDao, ExportManager exportManager, WsServerConfig cfg) {
    this.factory = factory;
    this.metricsDao = metricsDao;
    this.exportManager = exportManager;
    this.cfg = cfg;
  }

  @POST
  public UUID export(@PathParam("key") int key, @Valid ExportRequest req, @Auth User user) {
    if (req == null) req = new ExportRequest();
    req.setDatasetKey(key);
    if (user == null || !user.isAdmin()) {
      req.setForce(false); // we don't allow to force exports from the public API for non admin users
    }
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
  public Response download(@PathParam("key") int key,
                           @QueryParam("format") DataFormat format,
                           @QueryParam("extended") boolean extended) {
    if (format == null) {
      throw new IllegalArgumentException("Format parameter is required");
    }

    // an already existing export in the given format
    ExportRequest req = new ExportRequest(key, format);
    req.setExtended(extended);
    UUID exportKey = exportManager.exists(req);
    if (exportKey != null) {
      return Redirect.temporary(cfg.job.downloadURI(exportKey));
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
    @QueryParam("issues") boolean issues;

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
      if (ranks != null && !ranks.isEmpty()) {
        ttp.setLowestRank(RankUtils.lowestRank(ranks));
      } else if (minRank != null) {
        ttp.setLowestRank(minRank);
      }
      return ttp;
    }
  }

  <T extends AbstractPrinter> Response printerExport(Class<T> printerClass, int key, ExportQueryParams params, Consumer<T> modifier) {
    params.init();
    StreamingOutput stream = os -> {
      try (Writer writer = UTF8IoUtils.writerFromStream(os);
           T printer = PrinterFactory.dataset(printerClass, params.toTreeTraversalParameter(key), params.ranks, params.extinct, params.countBy, metricsDao, factory, writer)
      ) {
        modifier.accept(printer);
        printer.print();
        writer.flush();
      }
    };
    return Response.ok(stream).build();
  }

  @GET
  @Hidden
  @VaryAccept
  @Produces(MediaType.TEXT_PLAIN)
  public Response textTree(@PathParam("key") int key,
                           @BeanParam ExportQueryParams params,
                           @QueryParam("showID") boolean showID) {
    return printerExport(TextTreePrinter.class, key, params, printer -> {
      if (showID) printer.showIDs();
    });
  }

  @GET
  @Hidden
  @VaryAccept
  @Produces(MediaType.APPLICATION_JSON)
  public Response simpleName(@PathParam("key") int key,
                             @QueryParam("flat") boolean flat,
                             @BeanParam ExportQueryParams params) {
    Class<? extends AbstractPrinter> printerClass = flat ? JsonFlatPrinter.class : JsonTreePrinter.class;
    return printerExport(printerClass, key, params, p->{});
  }

  @GET
  @Hidden
  @VaryAccept
  @Produces(MoreMediaTypes.TEXT_TSV)
  public Response exportTsv(@PathParam("key") int key,
                                    @BeanParam ExportQueryParams params,
                                    @Context SqlSession session) {
    return printerExport(ColdpPrinter.TSV.class, key, params, p->{});
  }

  @GET
  @Hidden
  @VaryAccept
  @Produces({MoreMediaTypes.TEXT_CSV})
  public Response exportCsv(@PathParam("key") int key,
                                    @BeanParam ExportQueryParams params,
                                    @Context SqlSession session) {
    return printerExport(ColdpPrinter.CSV.class, key, params, p->{});
  }

}
