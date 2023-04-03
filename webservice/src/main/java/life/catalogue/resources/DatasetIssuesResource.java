package life.catalogue.resources;

import com.google.common.collect.Streams;

import io.dropwizard.auth.Auth;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.text.StringUtils;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.db.tree.*;
import life.catalogue.dw.jersey.Redirect;
import life.catalogue.dw.jersey.filter.VaryAccept;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.exporter.ExportManager;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Streams all issues of an entire dataset
 */
@Path("/dataset/{key}/issues")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetIssuesResource {
  private final SqlSessionFactory factory;
  private static final Object[][] EXPORT_HEADERS = new Object[1][];
  static {
    EXPORT_HEADERS[0] = new Object[]{"ID", "status", "rank", "label", "issues"};
  }

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetIssuesResource.class);

  public DatasetIssuesResource(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @GET
  @VaryAccept
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> exportCsv(@PathParam("key") int datasetKey,
                                    @Context SqlSession session) {
    // export entire dataset with issues
    VerbatimRecordMapper vm = session.getMapper(VerbatimRecordMapper.class);
    vm.createTmpIssuesTable(datasetKey, null);
    return Stream.concat(
      Stream.of(EXPORT_HEADERS),
      Streams.stream(vm.processIssues(datasetKey))
             .map(this::map)
    );
  }

  private Object[] map(IssueContainer.SimpleWithID v){
    return new Object[]{
      v.getId(),
      StringUtils.concat(";", v.getIssues())
    };
  }

}
