package org.col.resources;

import java.util.List;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.PagedReference;
import org.col.api.Name;
import org.col.api.NameAct;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.VerbatimRecord;
import org.col.dao.NameDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

@Path("/dataset/{datasetKey}/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

	@GET
	public PagingResultSet<Name> list(@PathParam("datasetKey") Integer datasetKey,
	    @Nullable @Context Page page,
	    @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.list(datasetKey, page);
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
	@Path("{id}/synonyms")
	public List<Name> getSynonyms(@PathParam("datasetKey") Integer datasetKey,
	    @PathParam("id") String id,
	    @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.synonyms(datasetKey, id);
	}

	@GET
	@Timed
	@Path("{id}/publishedIn")
	public PagedReference getPublishedIn(@PathParam("datasetKey") Integer datasetKey,
	    @PathParam("id") String id,
	    @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.getPublishedIn(datasetKey, id);
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
	    @PathParam("id") String id,
	    @QueryParam("homotypic") Boolean homotypic,
	    @Context SqlSession session) {
		throw new UnsupportedOperationException();
		// NameActMapper mapper = session.getMapper(NameActMapper.class);
		// return homotypic ? mapper.listByHomotypicGroup(datasetKey, id) :
		// mapper.listByName(datasetKey, id);
	}

}
