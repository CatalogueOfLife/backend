package org.col.resources;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Dataset;
import org.col.api.DatasetImport;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.dao.DatasetDao;
import org.col.db.mapper.DatasetImportMapper;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/dataset")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(DatasetResource.class);

	@GET
	public PagingResultSet<Dataset> list(@Valid @BeanParam Page page,
                                       @QueryParam("q") String q,
                                       @Context SqlSession session
  ) {
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
	public void update(Dataset dataset, @Context SqlSession session) {
    session.getMapper(DatasetMapper.class).update(dataset);
		session.commit();
	}

	@DELETE
	@Path("{key}")
	public void delete(@PathParam("key") Integer key, @Context SqlSession session) {
    session.getMapper(DatasetMapper.class).delete(key);
		session.commit();
	}

  @GET
  @Path("{key}/import")
  public List<DatasetImport> getImports(@PathParam("key") Integer key, @Context SqlSession session) {
    return session.getMapper(DatasetImportMapper.class).list(key);
  }

  @GET
  @Path("{key}/import/last")
  public DatasetImport getLastImport(@PathParam("key") Integer key, @Context SqlSession session) {
    return session.getMapper(DatasetImportMapper.class).lastSuccessful(key);
  }

}
