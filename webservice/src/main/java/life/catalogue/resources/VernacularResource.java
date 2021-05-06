package life.catalogue.resources;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.VernacularNameUsage;
import life.catalogue.api.search.VernacularSearchRequest;
import life.catalogue.db.mapper.VernacularNameMapper;

import java.util.List;
import java.util.function.Supplier;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset/{key}/vernacular")
public class VernacularResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(VernacularResource.class);

  @GET
  public ResultPage<VernacularNameUsage> search(@PathParam("key") int datasetKey,
                                          @BeanParam VernacularSearchRequest query,
                                          @Valid @BeanParam Page page,
                                          @Context SqlSession session) {
    Page p = page == null ? new Page() : page;
    VernacularNameMapper vm = session.getMapper(VernacularNameMapper.class);
    List<VernacularNameUsage> result = vm.search(datasetKey, query, page);
    Supplier<Integer> count = () -> vm.count(datasetKey, query);
    return new ResultPage<>(p, result, count);
  }

}
