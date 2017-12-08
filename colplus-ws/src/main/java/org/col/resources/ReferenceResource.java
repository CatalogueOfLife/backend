package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.Reference;
import org.col.dao.ReferenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/dataset/{datasetKey}/reference")
@Produces(MediaType.APPLICATION_JSON)
public class ReferenceResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(ReferenceResource.class);

	@GET
	public PagingResultSet<Reference> list(@PathParam("datasetKey") Integer datasetKey,
                                         @Valid @BeanParam Page page,
                                         @Context SqlSession session) {
		ReferenceDao dao = new ReferenceDao(session);
		return dao.list(datasetKey, page);
	}

	@GET
	@Timed
	@Path("{id}")
	public Reference get(@PathParam("key") Integer key, @Context SqlSession session) {
		ReferenceDao dao = new ReferenceDao(session);
		return dao.get(key);
	}

}
