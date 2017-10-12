package org.col.resources;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.VernacularName;
import org.col.db.mapper.VernacularNameMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

@Path("{datasetKey}/vernacular")
@Produces(MediaType.APPLICATION_JSON)
public class VernacularNameResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(VernacularNameResource.class);

	@GET
	@Timed
	@Path("{taxonId}/forTaxon")
	public List<VernacularName> getVernacularNames(
	    @PathParam("datasetKey") int datasetKey,
	    @PathParam("taxonId") String taxonId,
	    @Context SqlSession session) {
		VernacularNameMapper mapper = session.getMapper(VernacularNameMapper.class);
		return mapper.getVernacularNames(datasetKey, taxonId);
	}

}
