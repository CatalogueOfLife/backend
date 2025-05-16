package life.catalogue.resources;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.VernacularNameUsage;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.db.mapper.VernacularNameMapper;

import java.util.List;

import jakarta.validation.Valid;
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
                                                @QueryParam("lang") String lang, // lang is used in other API methods as the standard way to filter by language
                                                @QueryParam("language") String language, // keeping the legacy one here
                                                @Valid @BeanParam Page page,
                                                @Context SqlSession session) {
    Page p = page == null ? new Page() : page;
    String langCode = ObjectUtils.coalesce(lang, language);
    VernacularNameMapper vm = session.getMapper(VernacularNameMapper.class);
    List<VernacularNameUsage> result = vm.searchAll(q, langCode, page);
    return new ResultPage<>(p, -1, result);
  }

}
