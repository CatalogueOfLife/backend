package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.NameUsageMapper;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.ibatis.session.SqlSession;

import io.swagger.v3.oas.annotations.Hidden;

@Path("/colseo")
@SuppressWarnings("static-method")
public class ColSeoResource {
  private final DatasetSourceDao sourceDao;
  private final TaxonDao tdao;
  private final LatestDatasetKeyCache cache;

  public ColSeoResource(TaxonDao tdao, DatasetSourceDao sourceDao, LatestDatasetKeyCache cache) {
    this.tdao = tdao;
    this.sourceDao = sourceDao;
    this.cache = cache;
  }

  private Integer releaseKey(boolean preview) {
    return preview ? cache.getLatestReleaseCandidate(Datasets.COL) : cache.getLatestRelease(Datasets.COL);
  }

  @GET
  @Hidden
  @Path("source/{id}")
  @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML})
  public Response getHtmlHeader(@PathParam("id") int id, @QueryParam("preview") boolean preview) {
    Integer datasetKey = releaseKey(preview);
    if (datasetKey != null) {
      var d = sourceDao.get(datasetKey, id, false);
      if (d != null) {
        return ResourceUtils.streamFreemarker(d, "seo/dataset-seo.ftl", MediaType.TEXT_PLAIN_TYPE);
      }
    }
    return Response.noContent().build();
  }

  @GET
  @Hidden
  @Path("taxon/{id}")
  @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML})
  public Response getHtmlHeader(@PathParam("id") String id, @QueryParam("preview") boolean preview) {
    Integer datasetKey = releaseKey(preview);
    if (datasetKey != null) {
      final var key = new DSIDValue<>(datasetKey, id);
      Taxon t = tdao.get(key);
      if (t != null) {
        final Map<String, Object> data = new HashMap<>();
        TaxonInfo info = new TaxonInfo();
        info.setTaxon(t);
        data.put("info", info);
        try (SqlSession session = tdao.getFactory().openSession()) {
          tdao.fillTaxonInfo(session, info, null,
            true,
            true,
            false,
            true,
            false,
            false,
            false,
            true,
            false,
            false
          );
          // put source dataset title into the ID field so we can show a real title
          if (info.getSource()!=null) {
            var source = sourceDao.get(datasetKey, info.getSource().getSourceDatasetKey(), false);
            data.put("source", source);
          }
          if (info.getTaxon().getParentId()!=null) {
            SimpleName parent = session.getMapper(NameUsageMapper.class).getSimple(DSID.of(datasetKey, info.getTaxon().getParentId()));
            data.put("parent", parent);
          }
        }
        return ResourceUtils.streamFreemarker(data, "seo/taxon-seo.ftl", MediaType.TEXT_PLAIN_TYPE);
      }
    }
    return Response.noContent().build();
  }
}
