package life.catalogue.exporter;

import com.codahale.metrics.Timer;
import com.google.common.io.Files;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImgConfig;
import life.catalogue.postgres.PgCopyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exporter for the old AC schema.
 * Blocks parallel exports of the same dataset.
 */
public class AcefExporter extends DatasetExporter {
  private static final Logger LOG = LoggerFactory.getLogger(AcefExporter.class);
  private static final String EXPORT_SQL = "/export/acef/ac-export.sql";
  private static final String COPY_WITH = "CSV HEADER NULL '\\N' DELIMITER E'\\t' QUOTE E'\\f' ENCODING 'UTF8' ";
  private static final Pattern COPY_START = Pattern.compile("^\\s*COPY\\s*\\(");
  private static final Pattern COPY_END   = Pattern.compile("^\\s*\\)\\s*TO\\s*'(.+)'");
  private static final Pattern VAR_DATASET_KEY = Pattern.compile("\\{\\{datasetKey}}", Pattern.CASE_INSENSITIVE);

  public AcefExporter(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService, Timer timer) {
    super(req, userKey, DataFormat.ACEF, false, factory, cfg, imageService, timer);
    if (req.hasFilter()) {
      throw new IllegalArgumentException("ACEF exports cannot have any filters");
    }
  }

  @Override
  protected void export() throws Exception {
    LOG.info("Export dataset {} to {}", datasetKey, tmpDir.getAbsolutePath());
    // create csv files
    PgConnection c = cfg.db.connect();
    c.setAutoCommit(false);
    try {
      InputStream sql = AcefExporter.class.getResourceAsStream(EXPORT_SQL);
      executeAcExportSql(datasetKey, c, new BufferedReader(new InputStreamReader(sql, StandardCharsets.UTF_8)));

    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);

    } finally {
      dropSchema(c);
      c.commit();
      c.close();
    }
    // include images
    exportLogos();

    // get metrics
    updateMetrics();
  }

  private void updateMetrics() {
    try (SqlSession session = factory.openSession(true)) {
      DatasetImport di = session.getMapper(DatasetImportMapper.class).last(datasetKey);
      if (di != null) {
        counter.getSynCounter().set(ObjectUtils.coalesce(di.getSynonymCount(), 0));
        counter.getTaxCounter().set(ObjectUtils.coalesce(di.getTaxonCount(), 0));
        counter.putRankCounter(di.getTaxaByRankCount());
      }
    }
  }

  private static String exportSchema(int datasetKey){
    return "exp"+datasetKey;
  }

  private void dropSchema(Connection con) throws SQLException {
    final String schema = exportSchema(datasetKey);
    LOG.info("Remove export schema {} with all tables & sequences from postgres", schema);
    try (Statement stmnt = con.createStatement()) {
      stmnt.execute(String.format("DROP SCHEMA IF EXISTS %s CASCADE", schema));
      con.commit();
    }
  }

  private void exportLogos() throws IOException {
    LOG.info("Export logos for sources of project " + datasetKey);
    File logoDir = new File(tmpDir, "logos");
    logoDir.mkdir();
  
    int counter = 0;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetSearchRequest req = new DatasetSearchRequest();
      req.setContributesTo(datasetKey);
      List<Dataset> resp = dm.search(req, null, new Page(0,1000));
      LOG.info("Found " +resp.size()+ " source datasets of project " + datasetKey);
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
          LOG.info("Missing logo for dataset " + d.getTitle());
        }
      }
    }
    LOG.info(counter + " logos exported");
  }
  
  /**
   * @return directory with all CSV dump files
   */
  private void executeAcExportSql(int datasetKey, PgConnection con, BufferedReader sql) throws IOException, SQLException {
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
        LOG.info("Exporting {}", f.getName());
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
          LOG.info(StringUtils.capitalize(sql.substring(3, sql.indexOf('\n'))));
        }
      } else if (sql.contains(" ")){
        LOG.info("Execute " + sql.substring(0, sql.indexOf(' ')) + " SQL");
      }
      stmnt.execute(sql);
      con.commit();
    }
  }

}
