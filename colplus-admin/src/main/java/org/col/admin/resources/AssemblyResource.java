package org.col.admin.resources;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.col.admin.assembly.ContinuousAssembly;
import org.col.api.vocab.Datasets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/assembly/{catKey}")
@Produces(MediaType.APPLICATION_JSON)
public class AssemblyResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AssemblyResource.class);
  private final ContinuousAssembly assembly;
  
  public AssemblyResource(ContinuousAssembly assembly) {
    this.assembly = assembly;
  }
  
  @GET
  @Path("/sync")
  public Object state(@PathParam("catKey") int catKey) {
    requireDraft(catKey);
    return assembly.getState();
  }
  
  @POST
  @Path("/sync/sector/{key}")
  public void sync(@PathParam("catKey") int catKey, @QueryParam("key") int sectorKey) {
    requireDraft(catKey);
    assembly.syncSector(sectorKey);
  }
  
  private static void requireDraft(int catKey) {
    if (catKey != Datasets.DRAFT_COL) {
      throw new IllegalArgumentException("Only the draft CoL can be assembled at this stage");
    }
  }
}
