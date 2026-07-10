package life.catalogue.printer.diff;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameChunkerTest {

  private static String reconstruct(List<Chunk> chunks, boolean before) {
    StringBuilder sb = new StringBuilder();
    for (Chunk c : chunks) {
      if (c.op() == ChunkOp.EQUAL || c.op() == (before ? ChunkOp.DELETE : ChunkOp.INSERT)) {
        sb.append(c.text());
      }
    }
    return sb.toString();
  }

  @Test
  public void authorshipAppended() {
    List<Chunk> chunks = NameChunker.chunks("Cus cus L.", "Cus cus L., 1758");
    assertEquals("Cus cus L.", reconstruct(chunks, true));
    assertEquals("Cus cus L., 1758", reconstruct(chunks, false));
    assertTrue(chunks.stream().anyMatch(c -> c.op() == ChunkOp.INSERT && c.text().contains("1758")));
  }

  @Test
  public void midWordChange() {
    List<Chunk> chunks = NameChunker.chunks("Cus cus", "Cus cvs");
    assertEquals("Cus cus", reconstruct(chunks, true));
    assertEquals("Cus cvs", reconstruct(chunks, false));
    assertTrue(chunks.stream().anyMatch(c -> c.op() == ChunkOp.DELETE && c.text().equals("u")));
    assertTrue(chunks.stream().anyMatch(c -> c.op() == ChunkOp.INSERT && c.text().equals("v")));
  }

  @Test
  public void identical() {
    List<Chunk> chunks = NameChunker.chunks("Aus aus", "Aus aus");
    assertEquals(1, chunks.size());
    assertEquals(ChunkOp.EQUAL, chunks.get(0).op());
    assertEquals("Aus aus", chunks.get(0).text());
  }
}
