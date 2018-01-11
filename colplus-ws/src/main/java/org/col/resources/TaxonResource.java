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
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.Taxon;
import org.col.api.TaxonInfo;
import org.col.dao.TaxonDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

@Path("/taxon")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TaxonResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(TaxonResource.class);

  @GET
	public PagingResultSet<Taxon> list(@QueryParam("datasetKey") Integer datasetKey,
	    @Valid @BeanParam Page page,
	    @Context SqlSession session) {
		TaxonDao dao = new TaxonDao(session);
		return dao.list(datasetKey, page);
	}

	@GET
	@Timed
	@Path("{id}/{datasetKey}")
	public Integer lookupKey(@PathParam("id") String id,
	    @PathParam("datasetKey") int datasetKey,
	    @Context SqlSession session) {
		TaxonDao dao = new TaxonDao(session);
		return dao.lookupKey(id, datasetKey);
	}

	@GET
	@Timed
	@Path("{key}")
	public Taxon get(@PathParam("key") int key, @Context SqlSession session) {
		TaxonDao dao = new TaxonDao(session);
		return dao.get(key);
	}

	@GET
	@Timed
	@Path("{key}/children")
	public List<Taxon> children(@PathParam("key") int key,
	    @Valid @BeanParam Page page,
	    @Context SqlSession session) {
		TaxonDao dao = new TaxonDao(session);
		return dao.getChildren(key, page);
	}

	@GET
	@Timed
	@Path("{key}/classification")
	public List<Taxon> classification(@PathParam("key") int key, @Context SqlSession session) {
		TaxonDao dao = new TaxonDao(session);
		return dao.getClassification(key);
	}

	@GET
	@Timed
	@Path("{key}/info")
	public TaxonInfo info(@PathParam("key") int key, @Context SqlSession session) {
		TaxonDao dao = new TaxonDao(session);
		return dao.getTaxonInfo(key);
	}

}
