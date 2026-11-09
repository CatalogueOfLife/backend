package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.NameFormatter;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * labelHtml must be safe to assign to innerHTML whatever a source dataset or an editor put into the name parts:
 * every part is HTML escaped and the only markup are the italic tags of the epithets and sensu,
 * see https://github.com/CatalogueOfLife/backend/issues/1699
 */
public class LabelHtmlTest {
  private static final List<String> PAYLOADS = List.of(
    "<img src=x onerror=alert(1)>",
    "&lt;iframe src=x&gt;",
    "\"><b>x</b>",
    "<script>alert(1)</script>",
    "</i><svg onload=alert(1)>"
  );
  // any tag other than a bare <i> or </i>
  private static final Pattern OTHER_TAG = Pattern.compile("<(?!/?i>)");

  private static void assertSafe(String html) {
    assertNotNull(html);
    assertFalse("markup other than <i> in " + html, OTHER_TAG.matcher(html).find());
    // italics are balanced
    assertEquals(html, count(html, "<i>"), count(html, "</i>"));
  }

  private static int count(String x, String sub) {
    int cnt = 0;
    int idx = 0;
    while ((idx = x.indexOf(sub, idx)) >= 0) {
      cnt++;
      idx += sub.length();
    }
    return cnt;
  }

  @Test
  public void escapeHtml() {
    assertNull(NameFormatter.escapeHtml(null));
    assertEquals("", NameFormatter.escapeHtml(""));
    assertEquals("Abies × alba 'Golden' \"Candidatus\"", NameFormatter.escapeHtml("Abies × alba 'Golden' \"Candidatus\""));
    assertEquals("L. &amp; DC. &lt;b&gt; &amp;amp;", NameFormatter.escapeHtml("L. & DC. <b> &amp;"));
  }

  @Test
  public void name() {
    for (String p : PAYLOADS) {
      // as authorship
      Name n = Name.build("Abies alba", p, Rank.SPECIES);
      assertSafe(n.getLabelHtml());
      assertEquals("Abies alba " + p, n.getLabel());

      // as an unparsed scientific name, at species, genus and family rank
      for (Rank r : List.of(Rank.SPECIES, Rank.GENUS, Rank.FAMILY, Rank.UNRANKED)) {
        n = Name.build(p, "Mill.", r);
        assertSafe(n.getLabelHtml());
        n = Name.build("Abies " + p, null, r);
        assertSafe(n.getLabelHtml());
        n = Name.build("Abies alba var. " + p, null, r);
        assertSafe(n.getLabelHtml());
        n = Name.build("Abies alba var. montana " + p, null, r);
        assertSafe(n.getLabelHtml());
        n = Name.build("Abies " + p + " var. montana", null, r);
        assertSafe(n.getLabelHtml());
      }
    }
    // the text survives as escaped content
    Name n = Name.build("Abies alba", "<img src=x onerror=alert(1)>", Rank.SPECIES);
    assertEquals("<i>Abies alba</i> &lt;img src=x onerror=alert(1)&gt;", n.getLabelHtml());
  }

  @Test
  public void usage() {
    for (String p : PAYLOADS) {
      Taxon t = new Taxon();
      t.setName(Name.build("Abies alba", p, Rank.SPECIES));
      t.setExtinct(true);
      assertSafe(t.getLabelHtml());

      // name phrase
      t = new Taxon();
      t.setName(Name.build("Abies alba", "Mill.", Rank.SPECIES));
      t.setNamePhrase(p);
      assertSafe(t.getLabelHtml());
      assertEquals("Abies alba Mill. " + p, t.getLabel());

      // according to, abbreviated
      t = new Taxon();
      t.setName(Name.build("Abies alba", "Mill.", Rank.SPECIES));
      t.setAccordingTo(p);
      assertSafe(t.getLabelHtml());
      t.setAccordingTo("Smith " + p + " 1999");
      assertSafe(t.getLabelHtml());

      // misapplied names get auct. non
      Synonym s = new Synonym();
      s.setName(Name.build("Abies " + p, p, Rank.SPECIES));
      s.setStatus(TaxonomicStatus.MISAPPLIED);
      assertSafe(s.getLabelHtml());

      // the tree node builder
      assertSafe(NameUsageBase.labelBuilder(Name.build(p, p, Rank.GENUS), true, TaxonomicStatus.ACCEPTED, p, p, true).toString());
    }

    Taxon t = new Taxon();
    t.setName(Name.build("Abies alba", "Mill.", Rank.SPECIES));
    t.setAccordingTo("Smith 1999");
    assertEquals("Abies alba Mill. sensu Smith 1999", t.getLabel());
    assertEquals("<i>Abies alba</i> Mill. <i>sensu</i> Smith 1999", t.getLabelHtml());
  }

  @Test
  public void simpleName() {
    for (String p : PAYLOADS) {
      SimpleName sn = new SimpleName("x", "Abies alba", p, Rank.SPECIES);
      sn.setExtinct(true);
      sn.setPhrase(p);
      assertSafe(sn.getLabelHtml());
      assertEquals(NameUsageBase.EXTINCT_SYMBOL + "Abies alba " + p + " " + p, sn.getLabel());

      sn = new SimpleName("x", p, "Mill.", Rank.SPECIES);
      assertSafe(sn.getLabelHtml());
      sn = new SimpleName("x", "Abies alba subsp. " + p, null, Rank.SUBSPECIES);
      assertSafe(sn.getLabelHtml());
    }
    SimpleName sn = new SimpleName("x", "Abies alba", "L. & DC.", Rank.SPECIES);
    assertEquals("<i>Abies alba</i> L. &amp; DC.", sn.getLabelHtml());
  }

  /**
   * Labels of regular names keep their italics, hybrid and rank markers.
   */
  @Test
  public void unchanged() {
    assertEquals("<i>Abies alba</i> subsp. <i>montana</i> Mill.",
      Name.build("Abies alba subsp. montana", "Mill.", Rank.SUBSPECIES).getLabelHtml());
    assertEquals("<i>Ophrys</i> × <i>varvarae</i> Faller &amp; Kreutz",
      Name.build("Ophrys × varvarae", "Faller & Kreutz", Rank.SPECIES).getLabelHtml());
    assertEquals("+ <i>Laburnocytisus adamii</i>",
      Name.build("+ Laburnocytisus adamii", null, Rank.SPECIES).getLabelHtml());
    assertEquals("Pinaceae Lindl.", Name.build("Pinaceae", "Lindl.", Rank.FAMILY).getLabelHtml());
    assertEquals("Brassica oleracea 'Golden Wonder'",
      Name.build("Brassica oleracea 'Golden Wonder'", null, Rank.CULTIVAR).getLabelHtml());
  }
}
