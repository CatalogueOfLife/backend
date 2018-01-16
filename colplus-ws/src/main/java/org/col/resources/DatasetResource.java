package org.col.resources;

import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.api.*;
import org.col.api.Dataset;
import org.col.api.DatasetImport;
import org.col.api.Page;
import org.col.api.ResultPage;
import org.col.dao.DatasetDao;
import org.col.db.mapper.DatasetImportMapper;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/dataset")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DatasetResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);

  @GET
  public ResultPage<Dataset> list(@Valid @BeanParam Page page, @QueryParam("q") String q,
      @Context SqlSession session) {
    return new DatasetDao(session).search(q, page);
  }

  @POST
  public Integer create(Dataset dataset, @Context SqlSession session) {
    session.getMapper(DatasetMapper.class).create(dataset);
    session.commit();
    return dataset.getKey();
  }

  @GET
  @Path("{key}")
  public Dataset get(@PathParam("key") Integer key, @Context SqlSession session) {
    return session.getMapper(DatasetMapper.class).get(key);
  }

  @PUT
  @Path("{key}")
  public Response update(Dataset dataset, @Context SqlSession session) {
    int i = session.getMapper(DatasetMapper.class).update(dataset);
    session.commit();
    if (i == 0) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @DELETE
  @Path("{key}")
  public Response delete(@PathParam("key") Integer key, @Context SqlSession session) {
    int i = session.getMapper(DatasetMapper.class).delete(key);
    session.commit();
    if (i == 0) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @GET
  @Path("{key}/import")
  public List<DatasetImport> getImports(@PathParam("key") Integer key,
                                        @QueryParam("all") Boolean all,
      @Context SqlSession session) {
    DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
    if (all == null || !all) {
      return Lists.newArrayList(mapper.lastSuccessful(key));
    } else {
      return mapper.listByDataset(key);
    }
  }

  @GET
  @Path("{key}/verbatim")
  public ResultPage<VerbatimRecord> list(@PathParam("key") Integer datasetKey,
                                              @Valid @BeanParam Page page,
                                              @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return new ResultPage<VerbatimRecord>(page, mapper.count(datasetKey), mapper.list(datasetKey, page));
  }

  @GET
  @Path("{key}/verbatim/{id}")
  public VerbatimRecord get(@PathParam("key") Integer datasetKey,
                            @PathParam("id") String id,
                            @Context SqlSession session) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.get(datasetKey, id);
  }
}
