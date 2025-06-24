package life.catalogue.resources.dataset;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.dw.jersey.filter.VaryAccept;

import java.util.stream.Stream;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Streams;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

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
