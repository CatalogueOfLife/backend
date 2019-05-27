package org.col.command.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.common.io.CompressionUtil;
import org.col.WsServerConfig;
import org.col.postgres.PgCopyUtils;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcExporter {
  private static final Logger LOG = LoggerFactory.getLogger(AcExporter.class);
  private static final String EXPORT_SQL = "/exporter/ac-export.sql";
  private static final String COPY_WITH = "CSV HEADER NULL '\\N'";
  private static final Pattern COPY_START = Pattern.compile("^\\s*COPY\\s*\\(");
  private static final Pattern COPY_END   = Pattern.compile("^\\s*\\)\\s*TO\\s*'(.+)'");
  private static final Pattern VAR_DATASET_KEY = Pattern.compile("\\{\\{datasetKey}}", Pattern.CASE_INSENSITIVE);
  private final WsServerConfig cfg;

  public AcExporter(WsServerConfig cfg) {
    this.cfg = cfg;
  }
  
  /**
   * @return final archive
   */
  public File export(int catalogueKey) throws IOException, SQLException {
    File csvDir = new File(cfg.normalizer.scratchDir(catalogueKey), "exports");
    try {
      // create csv files
      try (Connection c = cfg.db.connect()) {
        c.setAutoCommit(false);
        InputStream sql = AcExporter.class.getResourceAsStream(EXPORT_SQL);
        executeAcExportSql(catalogueKey, (PgConnection)c, new BufferedReader(new InputStreamReader(sql, StandardCharsets.UTF_8)), csvDir);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
      // zip up archive and move to download
      File arch = new File(cfg.downloadDir, "ac-export.zip");
      if (arch.exists()) {
        LOG.debug("Remove previous export file {}", arch.getAbsolutePath());
      }
      LOG.info("Creating final export archive {}", arch.getAbsolutePath());
      CompressionUtil.zipDir(csvDir, arch);
      return arch;
      
    } finally {
      LOG.debug("Remove temp export directory {}", csvDir.getAbsolutePath());
      FileUtils.deleteQuietly(csvDir);
    }
  }
  
  /**
   * @return directory with all CSV dump files
   */
  private void executeAcExportSql(int datasetKey, PgConnection con, BufferedReader sql, File csvDir) throws IOException, SQLException {
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = sql.readLine()) != null) {
      Matcher m = COPY_END.matcher(line);
      if (COPY_START.matcher(line).find()) {
        executeSql(con, sb.toString());
        sb = new StringBuilder();
      
      } else if (m.find()) {
        // copy to file
        File f = new File(csvDir, m.group(1).trim());
        Files.createParentDirs(f);
        LOG.info("Exporting {}", f.getAbsolutePath());
        PgCopyUtils.dump(con, sb.toString(), f, COPY_WITH);
        sb = new StringBuilder();
      
      } else {
        if (sb.length() > 0) {
          sb.append("\n");
        }
        // substitute datasetKey variable
        sb.append(VAR_DATASET_KEY.matcher(line).replaceAll(String.valueOf(datasetKey)));
      }
    }
    if (sb.length() > 0) {
      executeSql(con, sb.toString());
    }
    con.commit();
  }
  
  private void executeSql(PgConnection con, String sql) throws SQLException {
    try (Statement stmnt = con.createStatement()) {
      stmnt.execute(sql);
      con.commit();
    }
  }
}
