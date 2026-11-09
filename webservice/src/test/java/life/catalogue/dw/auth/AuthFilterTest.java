package life.catalogue.dw.auth;

import org.junit.Test;

import jakarta.ws.rs.WebApplicationException;

import static org.junit.Assert.*;

public class AuthFilterTest {

  @Test
  public void requestedDataset() {
    assertNull(AuthFilter.requestedDataset(""));
    assertNull(AuthFilter.requestedDataset("/"));
    assertNull(AuthFilter.requestedDataset("/dataset"));
    assertNull(AuthFilter.requestedDataset("dataset"));
    assertNull(AuthFilter.requestedDataset("/dataset/me"));
    assertNull(AuthFilter.requestedDataset("/dataset/keys"));
    assertNull(AuthFilter.requestedDataset("/datasets/1234"));
    assertNull(AuthFilter.requestedDataset("/dataset1234"));
    assertNull(AuthFilter.requestedDataset("/vocab/dataset/1234"));
    assertNull(AuthFilter.requestedDataset("/importer/1234"));

    assertEquals((Integer) 1234, AuthFilter.requestedDataset("/dataset/1234"));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset("dataset/1234"));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset("/dataset/1234/"));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset("/dataset/1234/name"));
    assertEquals((Integer) 3, AuthFilter.requestedDataset("/dataset/0003/name"));
    // matrix parameters and doubled slashes do not hide the key
    assertEquals((Integer) 1234, AuthFilter.requestedDataset("/dataset;a=b/1234/name"));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset("/dataset/1234;a=b/name"));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset("//dataset//1234"));
  }

  /**
   * Magic keys are rewritten to the real dataset key by the pre-matching DatasetKeyRewriteFilter, before this
   * post-matching filter runs. One that reaches it unrewritten (PUT, DELETE, an unknown alias) is no integer at all
   * and must never get a 400 here - the resource answers it as it always did.
   */
  @Test
  public void magicKeysPass() {
    for (String key : new String[]{"3LXR", "3LR", "3LXRC", "3LRC", "3R5", "COL25XR", "COL2025.10", "gbif-7ddf754f-d193-4cc9-b351-99906754a03b"}) {
      assertNull(key, AuthFilter.requestedDataset("/dataset/" + key + "/taxon/x"));
    }
  }

  /**
   * Keys Jersey converts into an integer, but which are not plain ASCII digits, must be rejected.
   * The paths are decoded already, so this also covers their percent encoded forms.
   */
  @Test
  public void lenientKeysRejected() {
    for (String key : new String[]{"+1234", "-5", " 1234", "1234 ", "١٢٣", "１２", "99999999999"}) {
      try {
        AuthFilter.requestedDataset("/dataset/" + key + "/name");
        fail("Key accepted: " + key);
      } catch (WebApplicationException e) {
        assertEquals(400, e.getResponse().getStatus());
      }
    }
  }
}
