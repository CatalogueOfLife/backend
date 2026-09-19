package life.catalogue.release.review;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReleaseLogDigestTest {

  /** A line as the JobAppender writes it: %d %-5level %-25logger{0} %6X{source} %msg */
  private static String line(String time, String level, String logger, String msg) {
    return String.format("2026-09-11 %s,000 %-5s %-25s %6s %s%n", time, level, logger, "", msg);
  }

  /**
   * The shape of a real release log: a few hundred lines that tell what the job did, drowned in one line per
   * identifier from the IdProvider.
   */
  private static String releaseLog() {
    StringBuilder log = new StringBuilder();
    log.append(line("04:19:46", "INFO", "BackgroundJob", "Started ProjectRelease job 0cbe82fb"));
    log.append(line("04:20:52", "INFO", "AbstractProjectCopy", "Change step for dataset 316321 to MATCHING"));
    for (int i = 0; i < 1000; i++) {
      log.append(line("04:21:00", "DEBUG", "IdProvider", "Add 67J8R" + i + " from 50/2328: SYNONYM SPECIES Abies alba"));
      log.append(line("04:21:00", "INFO", "IdProvider", "Ignoring ID zZZwbU_" + i + " from all releases: Abies alba Mill."));
      if (i == 500) {
        log.append(line("04:40:00", "INFO", "IdProvider", "Done mapping name usage IDs. 20447 ids from the last release will be deleted, 5401234 have been reused."));
      }
    }
    log.append(line("04:49:07", "WARN", "IdProvider", "ID V4HB7 [17034692] reported without name usage in release"));
    log.append(line("04:50:00", "ERROR", "ReleaseAction", "Post release action failed"));
    log.append("java.io.IOException: connection refused\n");
    log.append("\tat life.catalogue.release.ReleaseAction.call(ReleaseAction.java:42)\n");
    log.append(line("05:31:39", "INFO", "ReleaseAction",
      "POST https://builds.example.org/job/col-portal/buildWithParameters?ENV=preview&token=s3cr3tT0ken&cause=x -> 201"));
    log.append(line("06:05:42", "INFO", "BackgroundJob", "Finished ProjectRelease #3 0cbe82fb: FINISHED"));
    return log.toString();
  }

  private static String digest(String log) throws Exception {
    return ReleaseLogDigest.digest(new BufferedReader(new StringReader(log)), "job.log.gz");
  }

  private static int occurrences(String haystack, String needle) {
    int n = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
      n++;
    }
    return n;
  }

  @Test
  public void quotesWhatTheJobDidInLogOrder() throws Exception {
    String d = digest(releaseLog());
    int started = d.indexOf("Started ProjectRelease job");
    int step = d.indexOf("Change step for dataset 316321 to MATCHING");
    int finished = d.indexOf("Finished ProjectRelease #3");
    assertTrue(started > 0);
    assertTrue(step > started);
    assertTrue(finished > step);
  }

  @Test
  public void summarisesALoggerThatFloodsALevel() throws Exception {
    String d = digest(releaseLog());
    assertTrue(d.contains("### INFO IdProvider - 1,001 lines"));
    // one pattern stands for all thousand identifiers
    assertTrue(d.contains("| 1,000 | `Ignoring # # from all releases` |"));
    // only its first and last lines are quoted
    assertEquals(ReleaseLogDigest.HEAD + ReleaseLogDigest.TAIL, occurrences(d, "Ignoring ID zZZwbU_"));
    assertTrue(d.contains("Ignoring ID zZZwbU_0 from all releases"));
    assertTrue(d.contains("Ignoring ID zZZwbU_999 from all releases"));
  }

  /**
   * A logger that floods a level can still say something once that matters, like the IdProvider's counts of
   * deleted and reused identifiers. Rare messages go to the timeline in full.
   */
  @Test
  public void quotesRareMessagesOfAFloodingLoggerInTheTimeline() throws Exception {
    String d = digest(releaseLog());
    int timeline = d.indexOf("## Timeline");
    int summarised = d.indexOf("## Summarised loggers");
    int done = d.indexOf("Done mapping name usage IDs. 20447 ids from the last release will be deleted");
    assertTrue(done > timeline);
    assertTrue(done < summarised);
    // and in log order, between the step before and the warning after it
    assertTrue(done > d.indexOf("Change step for dataset 316321 to MATCHING"));
    assertTrue(done < d.indexOf("ID V4HB7 [17034692] reported without name usage"));
  }

  @Test
  public void countsDebugLinesButNeverQuotesThem() throws Exception {
    String d = digest(releaseLog());
    assertFalse(d.contains("Add 67J8R"));
    assertTrue(d.contains("| DEBUG | IdProvider | 1,000 |"));
  }

  @Test
  public void keepsTheStackTraceWithItsError() throws Exception {
    String d = digest(releaseLog());
    int error = d.indexOf("Post release action failed");
    int trace = d.indexOf("java.io.IOException: connection refused");
    int frame = d.indexOf("at life.catalogue.release.ReleaseAction.call");
    assertTrue(error > 0);
    assertTrue(trace > error);
    assertTrue(frame > trace);
  }

  /**
   * The ReleaseAction logs the build URL it posts to, token and all.
   */
  @Test
  public void redactsCredentialsInUrls() throws Exception {
    String d = digest(releaseLog());
    assertFalse(d.contains("s3cr3tT0ken"));
    assertTrue(d.contains("token=REDACTED&cause=x"));
  }

  @Test
  public void patternsMaskIdentifiersAndNumbers() {
    assertEquals("Ignoring # # from all releases",
      ReleaseLogDigest.pattern("Ignoring ID zZZZwbU_467FDKlnGNaex from all releases: Anthurium rigidissimum Engl."));
    assertEquals("Ignoring # # from all releases",
      ReleaseLogDigest.pattern("Ignoring ID LPSBY from all releases: Anthurium rigidissimum Engl."));
    assertEquals("# # # reported without name usage in",
      ReleaseLogDigest.pattern("ID V4HB7 [17034692] reported without name usage in release"));
    assertEquals("Copied # Sectors from # to #",
      ReleaseLogDigest.pattern("Copied 586 Sectors from 3 to 316321"));
  }

  @Test
  public void readsTheGzippedLogFromDisk() throws Exception {
    File f = Files.createTempFile("job", ".log.gz").toFile();
    try {
      try (Writer w = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(f)), StandardCharsets.UTF_8)) {
        w.write(releaseLog());
      }
      String d = ReleaseLogDigest.digest(f);
      assertTrue(d.contains("Started ProjectRelease job"));
      assertTrue(d.contains("2,007 lines"));
    } finally {
      f.delete();
    }
  }
}
