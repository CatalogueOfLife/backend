package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.Taxon;
import org.col.api.TaxonInfo;
import org.col.dao.TaxonDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("{datasetKey}/taxon")
@Produces(MediaType.APPLICATION_JSON)
public class TaxonResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(TaxonResource.class);

	@GET
	public PagingResultSet<Taxon> list(@PathParam("datasetKey") Integer datasetKey,
                                     @Valid @BeanParam Page page,
                                    @Context SqlSession session) {
		TaxonDao dao = new TaxonDao(session);
		return dao.list(datasetKey, page);
	}

	@GET
	@Timed
	@Path("{id}")
	public Taxon get(@PathParam("datasetKey") int datasetKey,
                  @PathParam("id") String id,
                  @Context SqlSession session) {
		TaxonDao dao = new TaxonDao(session);
		return dao.get(datasetKey, id);
	}

	@GET
	@Timed
	@Path("{taxonId}/info")
	public TaxonInfo info(@PathParam("datasetKey") int datasetKey,
                        @PathParam("taxonId") String taxonId,
                        @Context SqlSession session) {
		TaxonDao dao = new TaxonDao(session);
		return dao.getTaxonInfo(datasetKey, taxonId);
	}

}
