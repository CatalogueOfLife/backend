package life.catalogue.resources.legacy;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.common.Managed;
import life.catalogue.common.func.ThrowingSupplier;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.function.Supplier;


import org.apache.commons.io.FileUtils;
import org.mapdb.DB;
import org.mapdb.DBException;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdMap implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(IdMap.class);
  private static final String SERVICE_NAME = "legacy ID redirection";

  private final Supplier<InputStream> tsvSupplier;
  private final String tsvName;
  private final File file;
  private final DBMaker.Maker dbMaker;
  private DB mapDb;
  private Map<String, String> map;

  private IdMap(File file, String tsvName, ThrowingSupplier<InputStream, IOException> tsvSupplier) throws IOException {
    this.file = file;
    this.tsvName = tsvName;
    this.tsvSupplier = tsvSupplier;
    if (file == null) {
      LOG.info("Create new memory IdMap");
      dbMaker = DBMaker.memoryDB();
    } else {
      if (!file.exists()) {
        FileUtils.forceMkdirParent(file);
        LOG.info("Create persistent IdMap at {}", file.getAbsolutePath());
      } else {
        LOG.info("Use persistent IdMap at {}", file.getAbsolutePath());
      }
      dbMaker = DBMaker
        .fileDB(file)
        .fileMmapEnableIfSupported();
    }
  }

  public static IdMap empty(File file) throws IOException {
    return new IdMap(file, "none", null);
  }

  public static IdMap fromURI(File file, URI tsv) throws IOException {
    if (tsv == null) return empty(file);

    final URL url = tsv.toURL();
    return new IdMap(file, tsv.toString(), url::openStream);
  }

  public static IdMap fromResource(File file, String tsvResourceName) throws IOException {
    return new IdMap(file, tsvResourceName, () -> Resources.stream(tsvResourceName));
  }

  /**
   * @return the persistent map and make sure it is working and online
   */
  private Map<String, String> onlineMap() {
    if (map != null && mapDb != null && !mapDb.isClosed()) {
      return map;
    }
    throw UnavailableException.unavailable(SERVICE_NAME);
  }

  public boolean reload() throws IOException {
    if (tsvSupplier != null) {
      LOG.info("Reload IdMap from {}", tsvName);
      reload(tsvSupplier.get());
      return true;
    } else {
      LOG.warn("No IdMap source configured");
    }
    return false;
  }

  public void reload(InputStream data) throws IOException {
    map.clear();
    try (BufferedReader br = UTF8IoUtils.readerFromStream(data)) {
      String line;
      while ((line = br.readLine()) != null) {
        int tabIdx = line.indexOf('\t');
        if (tabIdx<0) continue;
        String legacyID = line.substring(0, tabIdx);
        int tabIdx2 = line.indexOf('\t', tabIdx+1);
        String usageID = line.substring(tabIdx + 1, tabIdx2<0 ? line.length() : tabIdx2);
        if (legacyID.length() > 0 && usageID.length() > 0) {
          map.put(legacyID, usageID);
        }
      }
    }
    LOG.info("Loaded {} entries into IdMap", map.size());
  }

  public boolean contains(String id) {
    return id != null && onlineMap().containsKey(id);
  }

  public String lookup(String id) {
    return onlineMap().get(id);
  }

  public int size() {
    return onlineMap().size();
  }

  public void clear() {
    onlineMap().clear();
  }

  @Override
  public void start() throws IOException {
    if (mapDb == null || mapDb.isClosed()) {
      try {
        mapDb = dbMaker.make();
      } catch (DBException.DataCorruption e) {
        if (file != null) {
          LOG.warn("IdMap mapdb was corrupt. Remove and rebuild from scratch. {}", e.getMessage());
          file.delete();
          mapDb = dbMaker.make();
        } else {
          throw e;
        }
      }
      map = mapDb.hashMap("legacy")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .createOrOpen();
    }
    // reload if empty
    if (map.isEmpty()) {
      reload();
    }
  }

  @Override
  public void stop() {
    try {
      if (mapDb != null && !mapDb.isClosed()) {
        mapDb.close();
      }
    } catch (Exception e) {
      LOG.error("Failed to close mapDb for legacy IdMap at {}", file, e);
    }
  }

  @Override
  public boolean hasStarted() {
    try {
      var notNeeded = onlineMap().get("something1234567");
      return true;
    } catch (UnavailableException | IllegalAccessError e) {
      return false;
    }
  }
}