package life.catalogue.common.tax.authormap;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TsvDumpSourceTest {
  @Test
  public void readsManualAndDump() throws Exception {
    Path f = Files.createTempFile("dump", ".tsv");
    // columns: standardForm <tab> abbreviation <tab> fullName
    Files.writeString(f, "C Linnaeus\tL.\tCarl Linnaeus\nG Cuvier\tCuvier\tGeorges Cuvier\n");
    var src = TsvDumpSource.dump("ipni", f, 0, -1, AuthorCode.BOT, 1, 2);
    List<AuthorEntry> entries = src.read();
    assertEquals(2, entries.size());
    assertEquals("C Linnaeus", entries.get(0).canonical());
    assertEquals(AuthorCode.BOT, entries.get(0).code());
    assertEquals(List.of("L.", "Carl Linnaeus"), entries.get(0).aliases());
  }

  @Test
  public void manualReadsCodeColumnAndAllAliases() throws Exception {
    Path f = Files.createTempFile("manual", ".txt");
    Files.writeString(f, "C Linnaeus\tBOT\tL.\tCarl Linnaeus\nG Cuvier\tZOO\tGeorges Cuvier\n");
    var src = TsvDumpSource.manual(f);
    List<AuthorEntry> entries = src.read();
    assertEquals(2, entries.size());
    assertEquals("C Linnaeus", entries.get(0).canonical());
    assertEquals(AuthorCode.BOT, entries.get(0).code());
    assertEquals(List.of("L.", "Carl Linnaeus"), entries.get(0).aliases());
    assertEquals(AuthorCode.ZOO, entries.get(1).code());
    assertEquals(List.of("Georges Cuvier"), entries.get(1).aliases());
  }
}
