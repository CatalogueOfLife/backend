package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.*;
import org.col.dao.NameDao;
import org.col.db.mapper.NameActMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/dataset/{datasetKey}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

	@GET
	public PagingResultSet<Name> list(@PathParam("datasetKey") Integer datasetKey,
                                    @Valid @BeanParam Page page,
	                                  @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.list(datasetKey, page);
	}

	@GET
	@Timed
	@Path("/search")
	public PagingResultSet<NameSearchResult> search(@BeanParam NameSearch query,
                                      @Valid @BeanParam Page page,
                                      @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.search(query, page);
	}

	@GET
	@Timed
	@Path("{id}")
	public Name get(@PathParam("datasetKey") Integer datasetKey,
                  @PathParam("id") String id,
                  @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.get(datasetKey, id);
	}

	@GET
	@Timed
	@Path("{id}/basionymGroup")
	public List<Name> getSynonyms(@PathParam("datasetKey") Integer datasetKey,
                                @PathParam("id") String id,
                                @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.basionymGroup(datasetKey, id);
	}

	@GET
	@Timed
	@Path("{id}/publishedIn")
	public PagedReference getPublishedIn(@PathParam("datasetKey") Integer datasetKey,
                                       @PathParam("id") int nameKey,
	                                     @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.getPublishedIn(datasetKey, nameKey);
	}

	@GET
	@Timed
	@Path("{id}/verbatim")
	public VerbatimRecord getVerbatim(@PathParam("datasetKey") Integer datasetKey,
                                    @PathParam("id") String id,
                                    @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.getVerbatim(datasetKey, id);
	}

	@GET
	@Timed
	@Path("{id}/acts")
	@SuppressWarnings("unused")
	public List<NameAct> getActs(@PathParam("datasetKey") Integer datasetKey,
                               @PathParam("id") int nameKey,
                               @QueryParam("homotypic") Boolean homotypic,
                               @Context SqlSession session) {
		NameActMapper mapper = session.getMapper(NameActMapper.class);
		return homotypic ? mapper.listByHomotypicGroup(datasetKey, nameKey) : mapper.listByName(datasetKey, nameKey);
	}

}
