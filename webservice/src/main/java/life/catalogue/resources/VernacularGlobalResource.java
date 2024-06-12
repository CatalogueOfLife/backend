package life.catalogue.resources;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.VernacularNameUsage;
import life.catalogue.db.mapper.VernacularNameMapper;

import java.util.List;

import javax.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Path("/vernacular")
public class VernacularGlobalResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(VernacularGlobalResource.class);

  @GET
  public ResultPage<VernacularNameUsage> search(@QueryParam("q") String q,
                                                @QueryParam("language") String language,
                                                @Valid @BeanParam Page page,
                                                @Context SqlSession session) {
    Page p = page == null ? new Page() : page;
    VernacularNameMapper vm = session.getMapper(VernacularNameMapper.class);
    List<VernacularNameUsage> result = vm.searchAll(q, language, page);
    return new ResultPage<>(p, -1, result);
  }

}
