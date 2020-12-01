package life.catalogue.legacy;

import io.dropwizard.lifecycle.Managed;
import life.catalogue.common.io.UTF8IoUtils;
import org.apache.commons.io.FileUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

public class IdMap implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(IdMap.class);

  private final URI tsv;
  private final File file;
  private final DB mapDb;
  private Map<String, String> map;

  public IdMap(File file, URI tsv) throws IOException {
    this.file = file;
    this.tsv = tsv;
    DBMaker.Maker dbMaker;
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
    mapDb = dbMaker.make();
  }

  public void reload() throws IOException {
    if (tsv != null) {
      LOG.info("Reload IdMap from {}", tsv);
      reload(tsv.toURL().openStream());
    } else {
      LOG.warn("No IdMap source configured");
    }
  }

  public void reload(InputStream data) throws IOException {
    map.clear();
    try (BufferedReader br = UTF8IoUtils.readerFromStream(data)) {
      String line;
      while ((line = br.readLine()) != null) {
        int index = line.indexOf('\t');
        if (index<0) continue;
        String legacyID = line.substring(0, index);
        String usageID = line.substring(index + 1);
        if (legacyID.length() > 0 && usageID.length() > 0) {
          map.put(legacyID, usageID);
        }
      }
    }
    LOG.info("Loaded {} entries into IdMap", map.size());
  }

  public boolean contains(String id) {
    return map.containsKey(id);
  }

  public String lookup(String id) {
    return map.get(id);
  }

  @Override
  public void start() throws Exception {
    map = mapDb.hashMap("legacy")
      .keySerializer(Serializer.STRING)
      .valueSerializer(Serializer.STRING)
      .createOrOpen();
    // reload if empty
    if (map.isEmpty()) {
      reload();
    }
  }

  @Override
  public void stop() throws Exception {
    try {
      if (mapDb != null && !mapDb.isClosed()) {
        mapDb.close();
      }
    } catch (Exception e) {
      LOG.error("Failed to close mapDb for legacy IdMap at {}", file, e);
    }
  }
}