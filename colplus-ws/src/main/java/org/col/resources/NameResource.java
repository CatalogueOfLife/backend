package org.col.resources;

import java.util.List;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Name;
import org.col.api.NameAct;
import org.col.api.NameSearch;
import org.col.api.NameSearchResult;
import org.col.api.Page;
import org.col.api.PagedReference;
import org.col.api.ResultPage;
import org.col.api.VerbatimRecord;
import org.col.dao.NameDao;
import org.col.db.mapper.NameActMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

@Path("/name")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class NameResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

  @GET
	public ResultPage<Name> list(@QueryParam("datasetKey") Integer datasetKey,
	    @Valid @BeanParam Page page,
	    @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.list(datasetKey, page);
	}

	@GET
	@Timed
	@Path("search")
	public ResultPage<NameSearchResult> search(@BeanParam NameSearch query,
	    @Valid @BeanParam Page page,
	    @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.search(query, page);
	}

	@GET
	@Timed
	@Path("{id}/{datasetKey}")
	public Integer lookupKey(@PathParam("id") String id,
	    @PathParam("datasetKey") int datasetKey,
	    @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.lookupKey(id, datasetKey);
	}

	@GET
	@Timed
	@Path("{key}")
	public Name get(@PathParam("key") int key, @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.get(key);
	}

	@GET
	@Timed
	@Path("{key}/synonyms")
	public List<Name> getSynonyms(@PathParam("key") int key, @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.basionymGroup(key);
	}

	@GET
	@Timed
	@Path("{key}/publishedIn")
	public PagedReference getPublishedIn(@PathParam("key") int key, @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.getPublishedIn(key);
	}

	@GET
	@Timed
	@Path("{key}/verbatim")
	public VerbatimRecord getVerbatim(@PathParam("key") int key, @Context SqlSession session) {
		NameDao dao = new NameDao(session);
		return dao.getVerbatim(key);
	}

	@GET
	@Timed
	@Path("{key}/acts")
	public List<NameAct> getActs(@PathParam("key") int key,
	    @QueryParam("homotypic") Boolean homotypic,
	    @Context SqlSession session) {
		NameActMapper mapper = session.getMapper(NameActMapper.class);
		return homotypic ? mapper.listByHomotypicGroup(key) : mapper.listByName(key);
	}

}
