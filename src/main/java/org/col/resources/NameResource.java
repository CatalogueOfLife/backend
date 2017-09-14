package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Name;
import org.col.api.vocab.Rank;
import org.col.db.mapper.NameMapper;
import org.col.parser.NameParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Optional;


@Path("/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameResource {
    private static final Logger LOG = LoggerFactory.getLogger(NameResource.class);

    final NameParser parser = new NameParser();

    @GET
    @Timed
    @Path("{key}")
    public Name get(@PathParam("key") int key, @Context SqlSession session) {
        NameMapper mapper = session.getMapper(NameMapper.class);
        return mapper.get(key);
    }

    @GET
    @Timed
    @Path("create")
    public int create(@QueryParam("name") String sciname, @QueryParam("ref") Integer ref, @QueryParam("rank") Optional<Rank> rank, @Context SqlSession session) {
        Name n = parser.parse(sciname, rank);
        n.setPublishedInKey(ref);

        NameMapper mapper = session.getMapper(NameMapper.class);
        mapper.insert(n);
        session.commit();

        return n.getKey();
    }

}
