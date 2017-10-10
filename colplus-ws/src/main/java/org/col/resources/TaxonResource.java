package org.col.resources;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Taxon;
import org.col.db.mapper.TaxonMapper;
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
  @Path("{key}")
  public Taxon get(@PathParam("datasetKey") Integer datasetKey, @PathParam("key") String key, @Context SqlSession session) {
    TaxonMapper mapper = session.getMapper(TaxonMapper.class);
    return mapper.get(datasetKey, key);
  }

}
