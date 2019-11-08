package org.col.release;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.io.Files;
import freemarker.template.*;
import no.api.freemarker.java8.Java8ObjectWrapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.WsServerConfig;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.search.DatasetSearchRequest;
import org.col.common.io.CompressionUtil;
import org.col.common.io.Utf8IOUtils;
import org.col.db.mapper.DatasetMapper;
import org.col.img.ImgConfig;
import org.col.postgres.PgCopyUtils;
import org.gbif.nameparser.api.Rank;
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
  private static final Version freemarkerVersion = Configuration.VERSION_2_3_28;
  private static final Configuration fmk = new Configuration(freemarkerVersion);
  static {
    fmk.setClassForTemplateLoading(AcExporter.class, "/exporter");
    // see https://freemarker.apache.org/docs/pgui_quickstart_createconfiguration.html
    fmk.setDefaultEncoding("UTF-8");
    fmk.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    fmk.setLogTemplateExceptions(false);
    fmk.setWrapUncheckedExceptions(true);
    // allow the use of java8 dates, see https://github.com/lazee/freemarker-java-8
    fmk.setObjectWrapper(new Java8ObjectWrapper(freemarkerVersion));
  }
  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private Writer logger;

  public AcExporter(WsServerConfig cfg, SqlSessionFactory factory) {
    this.cfg = cfg;
    this.factory = factory;
  }
  
  private void log(String msg) throws IOException {
    logger.append(msg);
    logger.append("\n");
    logger.flush();
  }
  
  /**
   * @return final archive
   */
  public File export(int catalogueKey, Writer writer) throws IOException, SQLException, IllegalStateException {
    File csvDir = new File(cfg.normalizer.scratchDir(catalogueKey), "exports");
    try {
      this.logger = writer;
      // create csv files
      try (Connection c = cfg.db.connect()) {
        c.setAutoCommit(false);
        setupTables(c);
        InputStream sql = AcExporter.class.getResourceAsStream(EXPORT_SQL);
        executeAcExportSql(catalogueKey, (PgConnection)c, new BufferedReader(new InputStreamReader(sql, StandardCharsets.UTF_8)), csvDir);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
      // include images
      exportLogos(catalogueKey, csvDir);
  
      // export citation.ini
      exportCitations(catalogueKey, csvDir);
      
      // zip up archive and move to download
      File arch = new File(cfg.downloadDir, "ac-export.zip");
      log("Zip up archive and move to download");
      if (arch.exists()) {
        LOG.debug("Remove previous export file {}", arch.getAbsolutePath());
      }
      LOG.info("Creating final export archive {}", arch.getAbsolutePath());
      CompressionUtil.zipDir(csvDir, arch);
      return arch;
      
    } finally {
      LOG.debug("Remove temp export directory {}", csvDir.getAbsolutePath());
      log("Clean up temp files");
      FileUtils.deleteQuietly(csvDir);
      log("Export completed");
      this.logger = null;
    }
  }
  
  private void exportCitations(int catalogueKey, File dir) throws IOException {
    log("Export citations");
    File cf = new File(dir, "credits.ini");
  
    Map<String, Object> data = new HashMap<>();
  
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(catalogueKey);
      if (d.getReleased()==null) {
        // use today as default release date if missing
        d.setReleased(LocalDate.now());
      }
      data.put("d", d);
      
      Template temp = fmk.getTemplate("credits.ftl");
      Writer out = Utf8IOUtils.writerFromFile(cf);
      temp.process(data, out);
    } catch (TemplateException e) {
      LOG.error("Failed to write credits", e);
      throw new RuntimeException(e);
    }
  }
  
  private void exportLogos(int catalogueKey, File dir) throws IOException {
    log("Export logos");
    File logoDir = new File(dir, "logos");
    logoDir.mkdir();
  
    int counter = 0;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetSearchRequest req = new DatasetSearchRequest();
      req.setContributesTo(catalogueKey);
      List<Dataset> resp = dm.search(req, new Page(0,1000));
      for (Dataset d : resp) {
        Path p = cfg.img.datasetLogo(d.getKey(), ImgConfig.Scale.MEDIUM);
        if (java.nio.file.Files.exists(p)) {
          File img =  new File(logoDir, (d.getKey()-1000) + ".png");
          Files.copy(p.toFile(), img);
          
          p = cfg.img.datasetLogo(d.getKey(), ImgConfig.Scale.SMALL);
          img =  new File(logoDir, (d.getKey()-1000) + "-sm.png");
          Files.copy(p.toFile(), img);
          counter++;
          
        } else {
          LOG.warn("Missing logo for dataset {}: {}", d.getKey(), d.getTitle());
          log("Missing logo for dataset " + d.getTitle());
        }
      }
    }
    log(counter + " logos exported");
  }
  
  private static void setupTables(Connection c) throws SQLException, IOException {
    String sqlTable = "CREATE TABLE __ranks (key rank PRIMARY KEY, marker TEXT)";
    c.createStatement().execute(sqlTable);
    PreparedStatement pst = c.prepareStatement("INSERT INTO __ranks (key, marker) values (?::rank, ?)");
    for (Rank r : Rank.values()) {
      // exclude infrasp., see https://github.com/Sp2000/colplus-backend/issues/478
      if (r.isUncomparable()) continue;
      pst.setString(1, r.name().toLowerCase());
      pst.setString(2, r.getMarker());
      pst.execute();
    }
    c.commit();
    pst.close();
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
        log("Exporting " + f.getName());
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
  
  private void executeSql(PgConnection con, String sql) throws SQLException, IOException {
    try (Statement stmnt = con.createStatement()) {
      if (sql.startsWith("--")) {
        if (sql.contains("\n")) {
          log(StringUtils.capitalize(sql.substring(3, sql.indexOf('\n'))));
        }
      } else if (sql.contains(" ")){
        log("Execute " + sql.substring(0, sql.indexOf(' ')) + " SQL");
      }
      stmnt.execute(sql);
      con.commit();
    }
  }
}
