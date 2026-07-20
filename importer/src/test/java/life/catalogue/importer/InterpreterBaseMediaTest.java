package life.catalogue.importer;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.Media;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.MediaType;
import life.catalogue.coldp.ColdpTerm;

import java.util.List;

import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.Term;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * ColDP has no format term for Media - the MIME type belongs in the type column.
 * See https://github.com/CatalogueOfLife/checklistbank/issues/1711
 */
public class InterpreterBaseMediaTest {

  // deliberately without a file suffix so MediaInterpreter.detectType cannot guess the format
  // from the URL and mask what we actually read from the columns
  private static final String URL = "https://www.biodiversitylibrary.org/pageimage/39205667";

  private final InterpreterBase ib = new InterpreterBase(new DatasetSettings(), null, null, false);

  private Media interpret(Term typeTerm, String type, Term formatTerm, String format) {
    VerbatimRecord v = new VerbatimRecord();
    v.setType(ColdpTerm.Media);
    v.put(ColdpTerm.url, URL);
    if (type != null) {
      v.put(typeTerm, type);
    }
    if (format != null) {
      v.put(formatTerm, format);
    }
    List<Media> media = ib.interpretMedia(v, (m, r) -> {},
      typeTerm, ColdpTerm.url, ColdpTerm.link, ColdpTerm.license,
      ColdpTerm.creator, ColdpTerm.created, ColdpTerm.title, formatTerm, ColdpTerm.remarks);
    assertEquals(1, media.size());
    return media.get(0);
  }

  /**
   * The spec conformant ColDP form: a full MIME type in the type column and no format column at all.
   * Both the format and the media type must be recovered from it.
   */
  @Test
  public void mimeTypeInTypeColumn() {
    Media m = interpret(ColdpTerm.type, "image/jpeg", ColdpTerm.format, null);
    assertNotNull(m);
    assertEquals("image/jpeg", m.getFormat());
    assertEquals(MediaType.IMAGE, m.getType());
  }

  /**
   * Audio and video must work the same way, they cannot be guessed from an extensionless URL either.
   */
  @Test
  public void mimeTypeInTypeColumnNonImage() {
    Media m = interpret(ColdpTerm.type, "audio/mpeg", ColdpTerm.format, null);
    assertEquals("audio/mpeg", m.getFormat());
    assertEquals(MediaType.AUDIO, m.getType());

    m = interpret(ColdpTerm.type, "video/mp4", ColdpTerm.format, null);
    assertEquals("video/mp4", m.getFormat());
    assertEquals(MediaType.VIDEO, m.getType());
  }

  /**
   * The split form CLB used to write itself must keep working.
   */
  @Test
  public void separateTypeAndFormat() {
    Media m = interpret(ColdpTerm.type, "IMAGE", ColdpTerm.format, "image/jpeg");
    assertEquals("image/jpeg", m.getFormat());
    assertEquals(MediaType.IMAGE, m.getType());
  }

  /**
   * DwC-A keeps the MIME type in dc:format and a DCMI type in dc:type, which must not regress.
   */
  @Test
  public void dwcaStyle() {
    Media m = interpret(DcTerm.type, "StillImage", DcTerm.format, "image/jpeg");
    assertEquals("image/jpeg", m.getFormat());
    assertEquals(MediaType.IMAGE, m.getType());
  }
}
