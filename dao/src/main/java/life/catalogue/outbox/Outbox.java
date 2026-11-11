package life.catalogue.outbox;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.db.mapper.OutboxMapper;
import life.catalogue.doi.service.DoiConfig;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Writes durable commands to the outbox table, see {@link OutboxRunner}.
 *
 * Pass the session of the change that causes the command wherever there is one: the command is then committed with
 * the change and vanishes if it rolls back. The factory variants write in their own autocommit transaction.
 */
public final class Outbox {
  private static final Logger LOG = LoggerFactory.getLogger(Outbox.class);
  private static final String RELEASE_KEY = "releaseKey";
  private static final String GBIF_DEREGISTER = "deregister:";

  private Outbox() {
  }

  public static void enqueue(SqlSession session, OutboxType type, String key, ObjectNode params, int delaySeconds, int user) {
    LOG.debug("Enqueue {} {} {} due in {}s", type, key, params, delaySeconds);
    session.getMapper(OutboxMapper.class).enqueue(type, key, params, delaySeconds, user);
  }

  /**
   * Requests a DOI action. All requests for the same DOI become flags of a single command, so they are run together
   * and in a fixed order, see {@link DoiAction}. An update waits for the configured period, so a burst of
   * metadata changes ends up in a single call to DataCite.
   */
  public static void doi(SqlSession session, @Nullable DoiConfig cfg, DOI doi, DoiAction action, int user) {
    ObjectNode params = ApiModule.MAPPER.createObjectNode();
    params.put(action.name(), true);
    int delay = action == DoiAction.UPDATE && cfg != null ? cfg.waitPeriod : 0;
    enqueue(session, OutboxType.DOI, doi.getDoiName(), params, delay, user);
  }

  public static void doi(SqlSessionFactory factory, @Nullable DoiConfig cfg, DOI doi, DoiAction action, int user) {
    try (SqlSession session = factory.openSession(true)) {
      doi(session, cfg, doi, action, user);
    }
  }

  /**
   * @return the DOI actions flagged in the params of a DOI command
   */
  public static Set<DoiAction> doiActions(JsonNode params) {
    Set<DoiAction> actions = EnumSet.noneOf(DoiAction.class);
    if (params != null) {
      for (DoiAction a : DoiAction.values()) {
        if (params.path(a.name()).asBoolean(false)) {
          actions.add(a);
        }
      }
    }
    return actions;
  }

  /**
   * Requests the full deletion of a sector.
   */
  public static void sectorDelete(SqlSession session, DSID<Integer> sectorKey, int user) {
    enqueue(session, OutboxType.SECTOR_DELETE, sectorKey(sectorKey), ApiModule.MAPPER.createObjectNode(), 0, user);
  }

  public static String sectorKey(DSID<Integer> key) {
    return key.getDatasetKey() + ":" + key.getId();
  }

  public static DSID<Integer> parseSectorKey(String key) {
    int idx = key.indexOf(':');
    if (idx < 1) {
      throw new IllegalArgumentException("Bad sector key " + key);
    }
    return DSID.of(Integer.parseInt(key.substring(0, idx)), Integer.parseInt(key.substring(idx + 1)));
  }

  /**
   * Requests that the GBIF registry record of a dataset ChecklistBank registered in GBIF is brought in line with
   * ChecklistBank and crawled again, or deleted if the dataset is gone. The handler reads the current registration and
   * dataset when it runs, so all requests for a dataset merge into a single command.
   *
   * @param delaySeconds lets a burst of metadata edits end up in a single call to GBIF
   */
  public static void gbifRegistry(SqlSession session, int datasetKey, int delaySeconds, int user) {
    enqueue(session, OutboxType.GBIF_REGISTRY, String.valueOf(datasetKey), ApiModule.MAPPER.createObjectNode(), delaySeconds, user);
  }

  /**
   * Requests the deletion of a GBIF registry record of a dataset that is no longer registered in GBIF from ChecklistBank.
   * It merges with any waiting request for the dataset, which might by then be registered again under a new GBIF key.
   * Every GBIF key gets a params field of its own, so merging a second deregistration keeps the first one.
   */
  public static void gbifDeregister(SqlSession session, int datasetKey, UUID gbifKey, int user) {
    ObjectNode params = ApiModule.MAPPER.createObjectNode();
    params.put(GBIF_DEREGISTER + gbifKey, true);
    enqueue(session, OutboxType.GBIF_REGISTRY, String.valueOf(datasetKey), params, 0, user);
  }

  /**
   * @return the GBIF keys a GBIF_REGISTRY command has to delete, empty if none
   */
  public static Set<UUID> gbifDeregisterKeys(JsonNode params) {
    Set<UUID> keys = new HashSet<>();
    if (params != null) {
      params.fieldNames().forEachRemaining(f -> {
        if (f.startsWith(GBIF_DEREGISTER)) {
          keys.add(UUID.fromString(f.substring(GBIF_DEREGISTER.length())));
        }
      });
    }
    return keys;
  }

  /**
   * Requests the work that follows a release becoming public, see the RELEASE_PUBLISHED handler.
   */
  public static void releasePublished(SqlSession session, int releaseKey, int user) {
    ObjectNode params = ApiModule.MAPPER.createObjectNode();
    params.put(RELEASE_KEY, releaseKey);
    enqueue(session, OutboxType.RELEASE_PUBLISHED, String.valueOf(releaseKey), params, 0, user);
  }
}
