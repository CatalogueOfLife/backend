package org.col.resources;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Taxon;
import org.col.api.VernacularName;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.VernacularNameMapper;
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
	public Taxon get(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id,
	    @Context SqlSession session) {
		TaxonMapper mapper = session.getMapper(TaxonMapper.class);
		return mapper.get(datasetKey, id);
	}

	@GET
	@Timed
	@Path("{key}/getVernacularNames")
	public List<VernacularName> getVernacularNames(
	    @PathParam("datasetKey") Integer datasetKey, @PathParam("taxonKey") Integer taxonKey,
	    @Context SqlSession session) {
		VernacularNameMapper mapper = session.getMapper(VernacularNameMapper.class);
		return mapper.getVernacularNamesForTaxon(datasetKey, taxonKey);
	}

}
