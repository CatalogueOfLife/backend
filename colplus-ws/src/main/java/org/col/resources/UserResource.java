package org.col.resources;

import java.util.Map;
import javax.annotation.security.PermitAll;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.ColUser;
import org.col.db.mapper.UserMapper;
import org.col.dw.auth.JwtCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class UserResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);
  
  private final JwtCodec jwt;
  
  public UserResource(JwtCodec jwt) {
    this.jwt = jwt;
  }

  @GET
  @Path("/{key}")
  public ColUser get(@PathParam("key") Integer key, @Context SqlSession session) {
    ColUser u = session.getMapper(UserMapper.class).get(key);
    // obfuscate email and personal things
    if (u != null) {
      u.setEmail(null);
      u.setSettings(null);
      u.setOrcid(null);
      u.setLastLogin(null);
      u.setCreated(null);
      u.setDeleted(null);
      u.setRoles(null);
    }
    return u;
  }

  @GET
  @Path("/me")
  @PermitAll
  public ColUser me(@Auth ColUser user) {
    return user;
  }
  
  /**
   * Makes surer a user has authenticated with BasicAuth and then returns a new JWT token if successful.
   */
  @GET
  @Path("/login")
  public String login(@Context SecurityContext secCtxt, @Auth ColUser user) {
    // the user shall be authenticated using basic auth scheme only.
    if (secCtxt == null || !SecurityContext.BASIC_AUTH.equalsIgnoreCase(secCtxt.getAuthenticationScheme())) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }
    if (user == null) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }
    return jwt.generate(user);
  }
  
  @PUT
  @Path("/settings")
  @PermitAll
  public void updateSettings(Map<String, String> settings, @Auth ColUser user, @Context SqlSession session) {
    if (user != null && settings != null) {
      user.setSettings(settings);
      session.getMapper(UserMapper.class).update(user);
      session.commit();
    }
  }
}
