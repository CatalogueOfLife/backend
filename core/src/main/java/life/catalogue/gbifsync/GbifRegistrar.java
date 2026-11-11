package life.catalogue.gbifsync;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.GbifRegistration;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.config.GbifConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.GbifRegistrationMapper;
import life.catalogue.db.mapper.OutboxMapper;
import life.catalogue.outbox.Outbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

import jakarta.ws.rs.core.UriBuilder;

/**
 * Registers external ChecklistBank datasets in the GBIF registry and keeps their GBIF records in step, see #1438.
 *
 * A registered dataset is published by the chosen GBIF organisation and hosted by ChecklistBank's own GBIF installation.
 * Its GBIF record carries the dataset's ChecklistBank DOI, a CLB_DATASET_KEY identifier and two endpoints: the original
 * archive of its last successful import, typed by its format, and the EML metadata of the dataset.
 * Every change is passed on through the GBIF_REGISTRY outbox command, see {@link #reconcile(int, UUID)}.
 */
public class GbifRegistrar {
  private static final Logger LOG = LoggerFactory.getLogger(GbifRegistrar.class);
  static final String EML = "EML";
  private static final byte[] ZIP_MAGIC = {0x50, 0x4b, 0x03, 0x04};
  private final GbifConfig cfg;
  @Nullable
  private final URI apiURI;
  private final NormalizerConfig nCfg;
  private final SqlSessionFactory factory;
  private final DatasetDao dao;
  private final GbifRegistryClient client;

  /**
   * The state of a dataset's GBIF registration, with the outbox command still to be run, if any.
   */
  public record Status(GbifRegistration registration, UUID publisherKey, @Nullable OutboxEntry command) {}

  /**
   * @param apiURI the public base URI of the ChecklistBank API GBIF reads the endpoints from
   */
  public GbifRegistrar(GbifConfig cfg, @Nullable URI apiURI, NormalizerConfig nCfg, SqlSessionFactory factory, DatasetDao dao,
                       GbifRegistryClient client) {
    this.cfg = cfg;
    this.apiURI = apiURI;
    this.nCfg = nCfg;
    this.factory = factory;
    this.dao = dao;
    this.client = client;
  }

  /**
   * @return true if datasets can be registered in GBIF, i.e. the installation, the registry credentials and the API URI
   *   are all configured
   */
  public boolean isEnabled() {
    return cfg.installationKey != null && client.hasCredentials() && apiURI != null;
  }

  private void checkEnabled() {
    if (!isEnabled()) {
      throw new UnavailableException("Registering datasets in GBIF is not configured");
    }
  }

  /**
   * @return the GBIF endpoint type of an archive format or null if GBIF has none
   */
  @VisibleForTesting
  static @Nullable String endpointType(@Nullable DataFormat format) {
    if (format == null) return null;
    return switch (format) {
      case COLDP -> "COLDP";
      case DWCA -> "DWC_ARCHIVE";
      case ACEF -> "ACEF";
      case TEXT_TREE -> "TEXT_TREE";
      default -> null;
    };
  }

  URI archiveEndpoint(int datasetKey) {
    return UriBuilder.fromUri(apiURI).path("dataset").path(datasetKey + "/archive.zip").build();
  }

  URI emlEndpoint(int datasetKey) {
    return UriBuilder.fromUri(apiURI).path("dataset").path(datasetKey + ".xml").build();
  }

  /**
   * @return the registration status or null if the dataset is not registered in GBIF from ChecklistBank
   */
  @Nullable
  public Status status(int datasetKey) {
    try (SqlSession session = factory.openSession()) {
      var reg = session.getMapper(GbifRegistrationMapper.class).get(datasetKey);
      if (reg == null) {
        return null;
      }
      var publisher = session.getMapper(DatasetMapper.class).getPublisherKey(datasetKey);
      var cmd = session.getMapper(OutboxMapper.class).getByKey(OutboxType.GBIF_REGISTRY, String.valueOf(datasetKey));
      return new Status(reg, publisher, cmd);
    }
  }

  /**
   * Registers an external dataset in GBIF under the given organisation.
   * Whoever calls this must have checked that the user may write to the dataset and publish for the organisation.
   * The GBIF dataset is created right away, its endpoints, identifier and crawl follow through the outbox.
   *
   * @param userKey the user registering it, who thereby accepted the GBIF data publisher agreement
   * @throws IllegalArgumentException if the dataset cannot be registered in GBIF
   */
  public GbifRegistration register(int datasetKey, UUID publisherKey, int userKey) {
    checkEnabled();
    Objects.requireNonNull(publisherKey, "GBIF publishing organisation required");
    final Dataset d;
    final DatasetImport imp;
    try (SqlSession session = factory.openSession()) {
      d = session.getMapper(DatasetMapper.class).get(datasetKey);
      if (d == null || d.hasDeletedDate()) {
        throw NotFoundException.notFound(Dataset.class, datasetKey);
      }
      if (session.getMapper(GbifRegistrationMapper.class).exists(datasetKey)) {
        throw new IllegalArgumentException("Dataset " + datasetKey + " is registered in GBIF already");
      }
      imp = d.getAttempt() == null ? null : session.getMapper(DatasetImportMapper.class).current(datasetKey);
    }
    validate(d, imp);

    UUID gbifKey = client.createDataset(newGbifDataset(d, publisherKey));
    LOG.info("Created GBIF dataset {} for dataset {} {} published by {}", gbifKey, datasetKey, d.getTitle(), publisherKey);
    var reg = new GbifRegistration(datasetKey, gbifKey, userKey);
    try {
      dao.updateGbif(datasetKey, reg, gbifKey, publisherKey, userKey);
    } catch (RuntimeException e) {
      // e.g. a second request registering the same dataset at the same time: never leave an orphan in GBIF
      LOG.warn("Failed to record GBIF registration {} of dataset {}. Delete it in GBIF again", gbifKey, datasetKey, e);
      try {
        client.deleteDataset(gbifKey);
      } catch (RuntimeException e2) {
        LOG.error("Failed to delete orphaned GBIF dataset {} of dataset {}", gbifKey, datasetKey, e2);
      }
      throw e;
    }
    return reg;
  }

  private void validate(Dataset d, @Nullable DatasetImport imp) {
    final int key = d.getKey();
    if (d.getOrigin() != DatasetOrigin.EXTERNAL || DatasetDao.isTempKey(key)) {
      throw new IllegalArgumentException("Only external datasets can be registered in GBIF, dataset " + key + " is " + d.getOrigin());
    }
    if (d.isPrivat()) {
      throw new IllegalArgumentException("Dataset " + key + " is private. Only public datasets can be registered in GBIF");
    }
    if (d.getLicense() == null || !d.getLicense().isCreativeCommons()) {
      throw new IllegalArgumentException("Dataset " + key + " has license " + d.getLicense() + ". GBIF requires CC0, CC BY or CC BY-NC");
    }
    if (d.getGbifKey() != null) {
      throw new IllegalArgumentException("Dataset " + key + " is in GBIF already as " + d.getGbifKey());
    }
    if (d.getAttempt() == null || imp == null) {
      throw new IllegalArgumentException("Dataset " + key + " has never been imported successfully");
    }
    if (endpointType(imp.getFormat()) == null) {
      throw new IllegalArgumentException("Dataset " + key + " was imported from " + imp.getFormat() + ", which GBIF does not support");
    }
    File archive = nCfg.archive(key, d.getAttempt());
    if (!isZip(archive)) {
      throw new IllegalArgumentException("The archive of dataset " + key + " is no zip file. Please upload the data as a zip file");
    }
  }

  @VisibleForTesting
  static boolean isZip(File f) {
    if (!f.exists()) return false;
    try (InputStream in = Files.newInputStream(f.toPath())) {
      return Arrays.equals(ZIP_MAGIC, in.readNBytes(ZIP_MAGIC.length));
    } catch (IOException e) {
      LOG.warn("Cannot read archive {}", f, e);
      return false;
    }
  }

  private ObjectNode newGbifDataset(Dataset d, UUID publisherKey) {
    ObjectNode g = ApiModule.MAPPER.createObjectNode();
    g.put("type", "CHECKLIST");
    g.put("title", d.getTitle());
    if (d.getDescription() != null) {
      g.put("description", d.getDescription());
    }
    g.put("publishingOrganizationKey", publisherKey.toString());
    g.put("installationKey", cfg.installationKey.toString());
    g.put("license", d.getLicense().getUrl());
    if (d.getDoi() != null) {
      g.put("doi", d.getDoi().getDoiName());
    }
    if (d.getUrl() != null) {
      g.put("homepage", d.getUrl().toString());
    }
    return g;
  }

  /**
   * Moves a registered dataset to another GBIF organisation.
   */
  public void changePublisher(int datasetKey, UUID publisherKey, int userKey) {
    checkEnabled();
    var reg = registration(datasetKey);
    dao.updateGbif(datasetKey, null, reg.getGbifKey(), Objects.requireNonNull(publisherKey), userKey);
  }

  /**
   * Removes the GBIF registration of a dataset, the GBIF dataset is deleted through the outbox.
   */
  public void deregister(int datasetKey, int userKey) {
    checkEnabled();
    registration(datasetKey);
    dao.updateGbif(datasetKey, null, null, null, userKey);
  }

  /**
   * Requests a GBIF registry update and crawl of a registered dataset through the outbox.
   */
  public void sync(int datasetKey, int userKey) {
    checkEnabled();
    registration(datasetKey);
    try (SqlSession session = factory.openSession(true)) {
      Outbox.gbifRegistry(session, datasetKey, 0, userKey);
    }
  }

  private GbifRegistration registration(int datasetKey) {
    try (SqlSession session = factory.openSession()) {
      var reg = session.getMapper(GbifRegistrationMapper.class).get(datasetKey);
      if (reg == null) {
        throw new IllegalArgumentException("Dataset " + datasetKey + " is not registered in GBIF from ChecklistBank");
      }
      return reg;
    }
  }

  /**
   * Brings the GBIF record of a dataset in line with ChecklistBank and asks GBIF to crawl it,
   * or deletes it in GBIF if the dataset was deleted. Safe to run again, as the outbox may.
   *
   * @param deregisterKey a GBIF dataset to delete, as the dataset was deregistered, maybe registered again since
   */
  public void reconcile(int datasetKey, @Nullable UUID deregisterKey) {
    final GbifRegistration reg;
    final Dataset d;
    final DatasetImport imp;
    try (SqlSession session = factory.openSession()) {
      reg = session.getMapper(GbifRegistrationMapper.class).get(datasetKey);
      d = session.getMapper(DatasetMapper.class).get(datasetKey);
      imp = d == null || d.getAttempt() == null ? null : session.getMapper(DatasetImportMapper.class).current(datasetKey);
    }
    if (deregisterKey != null && (reg == null || !deregisterKey.equals(reg.getGbifKey()))) {
      deleteInGbif(datasetKey, deregisterKey);
    }
    if (reg == null) {
      return;
    }
    final UUID gbifKey = reg.getGbifKey();
    if (d == null || d.hasDeletedDate()) {
      deleteInGbif(datasetKey, gbifKey);
      try (SqlSession session = factory.openSession(true)) {
        session.getMapper(GbifRegistrationMapper.class).delete(datasetKey);
      }
      return;
    }

    ObjectNode g = client.getDataset(gbifKey);
    if (g == null || g.hasNonNull("deleted")) {
      throw new IllegalArgumentException("GBIF dataset " + gbifKey + " of dataset " + datasetKey + " was deleted in GBIF");
    }
    // organisation and DOI, all other metadata is read by GBIF from the EML endpoint
    boolean changed = false;
    if (d.getGbifPublisherKey() != null && !d.getGbifPublisherKey().toString().equals(g.path("publishingOrganizationKey").asText())) {
      g.put("publishingOrganizationKey", d.getGbifPublisherKey().toString());
      changed = true;
    }
    if (d.getDoi() != null && !d.getDoi().getDoiName().equalsIgnoreCase(g.path("doi").asText())) {
      g.put("doi", d.getDoi().getDoiName());
      changed = true;
    }
    if (changed) {
      LOG.info("Update GBIF dataset {} of dataset {}", gbifKey, datasetKey);
      client.updateDataset(g);
    }

    syncEndpoints(datasetKey, gbifKey, imp);
    syncIdentifier(datasetKey, gbifKey);
    LOG.info("Request GBIF to crawl {} for dataset {}", gbifKey, datasetKey);
    client.crawl(gbifKey);
  }

  private void syncEndpoints(int datasetKey, UUID gbifKey, @Nullable DatasetImport imp) {
    // type -> url
    Map<String, String> desired = new HashMap<>();
    String type = endpointType(imp == null ? null : imp.getFormat());
    if (type != null) {
      desired.put(type, archiveEndpoint(datasetKey).toString());
    } else {
      LOG.warn("Dataset {} has no archive in a format GBIF supports: {}", datasetKey, imp == null ? null : imp.getFormat());
    }
    desired.put(EML, emlEndpoint(datasetKey).toString());

    Set<String> existing = new HashSet<>();
    for (var e : client.listEndpoints(gbifKey)) {
      if (e.url() != null && e.url().equals(desired.get(e.type())) && existing.add(e.type())) {
        continue;
      }
      LOG.info("Remove {} endpoint {} from GBIF dataset {} of dataset {}", e.type(), e.url(), gbifKey, datasetKey);
      client.deleteEndpoint(gbifKey, e.key());
    }
    for (var e : desired.entrySet()) {
      if (!existing.contains(e.getKey())) {
        LOG.info("Add {} endpoint {} to GBIF dataset {} of dataset {}", e.getKey(), e.getValue(), gbifKey, datasetKey);
        client.addEndpoint(gbifKey, e.getKey(), URI.create(e.getValue()));
      }
    }
  }

  private void syncIdentifier(int datasetKey, UUID gbifKey) {
    final String id = String.valueOf(datasetKey);
    boolean exists = false;
    for (var i : client.listIdentifiers(gbifKey)) {
      if (GbifSyncJob.CLB_DATASET_KEY.equals(i.type())) {
        if (id.equals(i.identifier()) && !exists) {
          exists = true;
        } else {
          client.deleteIdentifier(gbifKey, i.key());
        }
      }
    }
    if (!exists) {
      client.addIdentifier(gbifKey, GbifSyncJob.CLB_DATASET_KEY, id);
    }
  }

  private void deleteInGbif(int datasetKey, UUID gbifKey) {
    if (client.deleteDataset(gbifKey)) {
      LOG.info("Deleted GBIF dataset {} of dataset {}", gbifKey, datasetKey);
    } else {
      LOG.info("GBIF dataset {} of dataset {} did not exist anymore", gbifKey, datasetKey);
    }
  }
}
