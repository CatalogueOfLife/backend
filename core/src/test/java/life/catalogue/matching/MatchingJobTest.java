package life.catalogue.matching;

import life.catalogue.TestConfigs;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TempFile;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import life.catalogue.matching.nidx.NameIndex;

import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NameIndexImpl;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MatchingJobTest extends EmailNotificationTemplateTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  UsageMatcherFactory matcherFactory;
  TestConfigs cfg;

  @Before
  public void setUp() throws Exception {
    this.cfg = TestConfigs.build();
    var matcher = mock(UsageMatcher.class);
    when(matcher.match(any(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(UsageMatch.empty(0));
    matcherFactory = mock(UsageMatcherFactory.class);
    when(matcherFactory.persistent(anyInt())).thenReturn(matcher);
    when(matcherFactory.getNameIndex()).thenReturn(NameIndexFactory.passThru());
  }

  @Override
  public BackgroundJob buildJob() throws IOException {
    try (TempFile tmp = new TempFile()) {
      MatchingRequest req = new MatchingRequest();
      req.setDatasetKey(dataRule.testData.key);
      req.setUpload(tmp.file);
      return new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory, cfg.matching);
    }
  }

  @Test
  public void testMatching() throws Exception {
    MatchingRequest req = new MatchingRequest();
    req.setDatasetKey(dataRule.testData.key);
    req.setSourceDatasetKey(dataRule.testData.key);
    var job = new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory, cfg.matching);
    job.run();
    assertTrue(job.isFinished());
  }

  @Test
  public void testMatchUploadCsv() throws Exception {
    runUploadMatching(",", StandardCharsets.UTF_8, null, "csv");
  }

  @Test
  public void testMatchUploadCsvWithUtf8Bom() throws Exception {
    runUploadMatching(",", StandardCharsets.UTF_8, new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "csv");
  }

  @Test
  public void testMatchUploadTsvWithUtf16leBom() throws Exception {
    // Excel "Unicode Text" export on Windows: UTF-16 LE + BOM, tab-delimited
    runUploadMatching("\t", StandardCharsets.UTF_16LE, new byte[]{(byte) 0xFF, (byte) 0xFE}, "txt");
  }

  private void runUploadMatching(String sep, java.nio.charset.Charset charset, byte[] bom, String suffix) throws Exception {
    // put scientificName first so a leaked BOM (﻿) lands on the term that
    // RowMapper's constructor explicitly requires.
    String text = String.join(sep, "scientificName", "authorship", "rank", "id") + "\n"
                + String.join(sep, "Aus bus", "L.", "species", "1") + "\n"
                + String.join(sep, "Aus bus alpha", "Mill.", "subspecies", "2") + "\n"
                + String.join(sep, "Cus dus", "Smith", "species", "3") + "\n";
    File upload = File.createTempFile("col-bom-test-", "." + suffix);
    upload.deleteOnExit();
    try (FileOutputStream fos = new FileOutputStream(upload)) {
      if (bom != null) {
        fos.write(bom);
      }
      fos.write(text.getBytes(charset));
    }

    String variant = " [" + charset + (bom == null ? " no-BOM" : " +BOM") + "]";

    MatchingRequest req = new MatchingRequest();
    req.setDatasetKey(dataRule.testData.key);
    req.setUpload(upload);
    var job = new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory, cfg.matching);
    job.run();

    assertNull("Job should not error" + variant, job.getError());
    assertTrue("Job should be finished" + variant, job.isFinished());

    File resultFile = job.getResult().getFile();
    assertTrue("Result file should exist", resultFile.exists());

    int dataRows = 0;
    try (ZipFile zipFile = new ZipFile(resultFile)) {
      ZipEntry entry = zipFile.entries().nextElement();
      try (BufferedReader br = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
        String header = br.readLine();
        assertNotNull("Result must have a header", header);
        // if a BOM leaked into header[0] the first column would be "original_﻿scientificName"
        String[] headerCols = header.split("[\t,]");
        assertEquals("First result column should be original_scientificName" + variant
                     + ", full header: " + header,
          "original_scientificName", headerCols[0]);
        while (br.readLine() != null) {
          dataRows++;
        }
      }
    }
    assertEquals("Expected 3 data rows in result" + variant, 3, dataRows);
  }

  @Test
  public void testMatchingProducesValidZip() throws Exception {
    MatchingRequest req = new MatchingRequest();
    req.setDatasetKey(dataRule.testData.key);
    req.setSourceDatasetKey(dataRule.testData.key);
    var job = new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory, cfg.matching);
    job.run();
    assertTrue(job.isFinished());

    // Verify the result file is a valid ZIP
    var resultFile = job.getResult().getFile();
    assertTrue("Result file should exist", resultFile.exists());
    assertTrue("Result file should not be empty", resultFile.length() > 0);

    // Try to open as ZIP - this will throw if ZIP is invalid
    try (ZipFile zipFile = new ZipFile(resultFile)) {
      ZipEntry entry = zipFile.entries().nextElement();
      assertNotNull("ZIP should contain at least one entry", entry);
      assertTrue("Entry name should end with .tsv or .csv",
        entry.getName().endsWith(".tsv") || entry.getName().endsWith(".csv"));
    }
  }
}