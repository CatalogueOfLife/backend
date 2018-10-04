package org.col.resources;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic resource that forwards a name match to the admin API.
 * Uses the native java URL connection which is rather dumn, but should work for this case
 */
@Path("/name/matching")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class MatchingForwardingResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(MatchingForwardingResource.class);
  private final String adminHost;
  private final int adminPort = 80;
  
  public MatchingForwardingResource(String adminHost) {
    this.adminHost = adminHost;
  }
  
  @GET
  public Response match(@Context UriInfo uriInfo) throws Exception {
    return forward(uriInfo);
  }
  
  private Response forward(UriInfo uriInfo) throws Exception {
    URI forwardUri = uriInfo.getRequestUriBuilder()
        .host(adminHost)
        .port(adminPort)
        .build();
    URL forwardUrl = forwardUri.toURL();
  
    HttpURLConnection con = (HttpURLConnection) forwardUrl.openConnection();
    con.setRequestMethod("GET");
    con.setRequestProperty(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
    try {
      con.connect();
      StreamingOutput stream = new StreamingOutput() {
        @Override
        public void write(OutputStream os) throws IOException, WebApplicationException {
          try {
            IOUtils.copy(con.getInputStream(), os);
            os.flush();
          } catch (FileNotFoundException e) {
            throw new NotFoundException(e);
          }
        }
      };
      return Response.ok(stream)
          .type(MediaType.APPLICATION_JSON)
          .build();
      
    } finally {
      con.disconnect();
    }
  }

}
