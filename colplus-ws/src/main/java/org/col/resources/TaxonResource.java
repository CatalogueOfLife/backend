package org.col.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Taxon;
import org.col.api.TaxonInfo;
import org.col.dao.TaxonDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

@Path("{datasetKey}/taxon")
@Produces(MediaType.APPLICATION_JSON)
public class TaxonResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(TaxonResource.class);

	@GET
	@Timed
	@Path("{id}")
	public Taxon get(@PathParam("datasetKey") int datasetKey,
	    @PathParam("id") String id,
	    @Context SqlSession session) {
		return new TaxonDao(session).get(datasetKey, id);
	}

	@GET
	@Timed
	@Path("{taxonId}/info")
	public TaxonInfo info(@PathParam("datasetKey") int datasetKey,
	    @PathParam("taxonId") String taxonId,
	    @Context SqlSession session) {
		return new TaxonDao(session).getTaxonInfo(datasetKey, taxonId);
	}

}
