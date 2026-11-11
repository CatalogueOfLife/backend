package life.catalogue.resources.dataset;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.GbifRegistration;
import life.catalogue.api.model.User;
import life.catalogue.dw.auth.Roles;
import life.catalogue.gbifsync.GbifRegistrar;

import java.util.UUID;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Registers an external dataset in the GBIF registry from ChecklistBank, see docs/GBIF-REGISTRATION.md.
 * Registering needs write access to the dataset and the organisation publisher role for the chosen GBIF organisation.
 * Moving a registered dataset to another organisation and deregistering it are done by admins.
 */
@Path("/dataset/{key}/gbif")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetGbifResource {
  public static final String PUBLISHER_AGREEMENT = "https://www.gbif.org/terms/data-publisher";
  private final GbifRegistrar registrar;

  public static class RegistrationRequest {
    // the GBIF organisation to publish the dataset
    public UUID publisherKey;
    // the GBIF data publisher agreement must be accepted
    public boolean acceptTerms;
  }

  public DatasetGbifResource(GbifRegistrar registrar) {
    this.registrar = registrar;
  }

  /**
   * @return the GBIF registration of the dataset with any outbox command still to update the GBIF registry.
   *   The command carries GBIF's raw error responses, so this is for editors of the dataset only.
   */
  @GET
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public GbifRegistrar.Status status(@PathParam("key") int key) {
    var status = registrar.status(key);
    if (status == null) {
      throw new NotFoundException(key, "Dataset " + key + " is not registered in GBIF from ChecklistBank");
    }
    return status;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public GbifRegistration register(@PathParam("key") int key, RegistrationRequest req, @Auth User user) {
    if (req == null || req.publisherKey == null) {
      throw new IllegalArgumentException("The GBIF organisation to publish the dataset is required as publisherKey");
    }
    if (!req.acceptTerms) {
      throw new IllegalArgumentException("Registering a dataset in GBIF requires to accept the GBIF data publisher agreement: " + PUBLISHER_AGREEMENT);
    }
    if (!user.isAdmin() && !user.isOrgPublisher(req.publisherKey)) {
      throw new ForbiddenException("You are not allowed to publish datasets in GBIF for organisation " + req.publisherKey);
    }
    return registrar.register(key, req.publisherKey, user.getKey());
  }

  /**
   * Moves a registered dataset to another GBIF organisation.
   */
  @PUT
  @Path("publisher/{publisherKey}")
  @RolesAllowed({Roles.ADMIN})
  public void changePublisher(@PathParam("key") int key, @PathParam("publisherKey") UUID publisherKey, @Auth User user) {
    registrar.changePublisher(key, publisherKey, user.getKey());
  }

  /**
   * Deregisters the dataset, which deletes it in GBIF.
   */
  @DELETE
  @RolesAllowed({Roles.ADMIN})
  public void deregister(@PathParam("key") int key, @Auth User user) {
    registrar.deregister(key, user.getKey());
  }

  /**
   * Updates the GBIF registry record and asks GBIF to crawl the dataset again.
   */
  @POST
  @Path("sync")
  @RolesAllowed({Roles.ADMIN})
  public void sync(@PathParam("key") int key, @Auth User user) {
    registrar.sync(key, user.getKey());
  }
}
