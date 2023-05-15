package life.catalogue.dw.jersey.filter;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class DatasetKeyRewriteFilterTest {

  @Test
  public void filter() {
    var m = DatasetKeyRewriteFilter.GBIF_PATTERN.matcher("gbif-bfb878f3-8a74-46d3-a104-36485c32aaba");
    assertTrue(m.find());
    assertEquals("bfb878f3-8a74-46d3-a104-36485c32aaba", m.group(1));

    m = DatasetKeyRewriteFilter.GBIF_PATTERN.matcher("gbif-BFB878f3-8A74-46D3-A104-36485C32AABA");
    assertTrue(m.find());
    assertEquals(UUID.fromString("bfb878f3-8a74-46d3-a104-36485c32aaba"), UUID.fromString(m.group(1)));
  }
}