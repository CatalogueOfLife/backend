package org.col.admin.command.export;

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.io.Files;
import org.col.admin.config.AdminServerConfig;
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
  private final AdminServerConfig cfg;

  public AcExporter(AdminServerConfig cfg) {
    this.cfg = cfg;
  }
  
  public void export(int catalogueKey) throws IOException, SQLException {
    try (Connection c = cfg.db.connect()) {
      c.setAutoCommit(false);
      InputStream sql = AcExporter.class.getResourceAsStream(EXPORT_SQL);
      executeAcExportSql(catalogueKey, (PgConnection)c, new BufferedReader(new InputStreamReader(sql, "UTF8")));
      //TODO: zip files and move to download
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
  
  private void executeAcExportSql(int datasetKey, PgConnection con, BufferedReader sql) throws IOException, SQLException {
    File tmpDir = new File(cfg.scratchDir, "exports/"+datasetKey);
    
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = sql.readLine()) != null) {
      Matcher m = COPY_END.matcher(line);
      if (COPY_START.matcher(line).find()) {
        executeSql(con, sb.toString());
        sb = new StringBuilder();
      
      } else if (m.find()) {
        // copy to file
        File f = new File(tmpDir, m.group(1).trim());
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
