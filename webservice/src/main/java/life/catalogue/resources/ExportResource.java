package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.dw.auth.Roles;
import life.catalogue.release.AcExporter;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.util.Set;

/**
 * Stream dataset exports to the user.
 * If existing it uses preprepared files from the filesystem.
 * For yet non existing files we should generate and store them for later reuse.
 * If no format is given the original source is returned.
 *
 * Managed datasets can change data continously and we will need to:
 *  a) never store any version and dynamically recreate them each time
 *  b) keep a "dirty" flag that indicates the currently stored archive is not valid anymore because data has changed.
 *     Any edit would have to raise the dirty flag which therefore must be kept in memory and only persisted if it has changed.
 *     Creating an export would remove the flag - we will need a flag for each supported output format.
 *
 * Formats currently supported for the entire dataset and which are archived for reuse:
 *  - ColDP
 *  - ColDP simple (single TSV file)
 *  - DwCA
 *  - DwC simple (single TSV file)
 *  - TextTree
 *
 *  Single file formats for dynamic exports using some filter (e.g. rank, rootID, etc)
 *  - ColDP simple (single TSV file)
 *  - DwC simple (single TSV file)
 *  - TextTree
 */
@Path("/dataset/{datasetKey}/export")
@Produces(MediaType.APPLICATION_JSON)
public class ExportResource {
  private final DatasetImportDao diDao;
  private final SqlSessionFactory factory;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ExportResource.class);
  private final AcExporter exporter;

  public ExportResource(SqlSessionFactory factory, AcExporter exporter, DatasetImportDao diDao) {
    this.factory = factory;
    this.exporter = exporter;
    this.diDao = diDao;
  }

  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public boolean export(@PathParam("datasetKey") int datasetKey, @Auth User user) {
    return exportAC(datasetKey, user);
  }

  private boolean exportAC(int key, User user) {
    try {
      exporter.export(key);
      return true;

    } catch (Throwable e) {
      LOG.error("Error exporting dataset {}", key, e);
    }
    return false;
  }


  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public Response textTree(@PathParam("datasetKey") int key,
                           @QueryParam("root") String rootID,
                           @QueryParam("rank") Set<Rank> ranks,
                           @Context SqlSession session) {
    StreamingOutput stream;
    final Integer projectKey;
    Integer attempt;
    // a release?
    if (DatasetInfoCache.CACHE.origin(key) == DatasetOrigin.RELEASED) {
      attempt = DatasetInfoCache.CACHE.importAttempt(key);
      projectKey = DatasetInfoCache.CACHE.sourceProject(key);
    } else {
      attempt = session.getMapper(DatasetMapper.class).lastImportAttempt(key);
      projectKey = key;
    }

    if (attempt != null && rootID == null && (ranks == null || ranks.isEmpty())) {
      // stream from pre-generated file
      stream = os -> {
        InputStream in = new FileInputStream(diDao.getFileMetricsDao().treeFile(projectKey, attempt));
        IOUtils.copy(in, os);
        os.flush();
      };

    } else {
      stream = os -> {
        Writer writer = new BufferedWriter(new OutputStreamWriter(os));
        TextTreePrinter printer = TextTreePrinter.dataset(key, rootID, ranks, factory, writer);
        printer.print();
        if (printer.getCounter() == 0) {
          writer.write("--NONE--");
        }
        writer.flush();
      };
    }
    return Response.ok(stream).build();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Cursor<SimpleName> simpleName(@PathParam("datasetKey") int key,
                                        @QueryParam("root") String rootID,
                                        @QueryParam("rank") Rank lowestRank,
                                        @QueryParam("synonyms") boolean includeSynonyms,
                                        @Context SqlSession session) {
    if (rootID == null) {
      throw new IllegalArgumentException("root query parameter required");
    }
    return session.getMapper(NameUsageMapper.class).processTreeSimple(key, null, rootID, null, lowestRank, includeSynonyms);
  }
}
