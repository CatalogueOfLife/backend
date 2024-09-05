package life.catalogue.api.vocab;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static life.catalogue.api.vocab.TaxGroup.*;
import static org.junit.Assert.*;

public class TaxGroupTest {

  @Test
  @Ignore("manual to generate an html tree of all groups")
  public void generateHtmlTree() {
    final var sb = new StringBuilder();
    Set<TaxGroup> printed = new HashSet<>();
    sb.append("<html>\n<head>\n")
      .append("<style type=\"text/css\">\n" +
        " span.name {\n" +
        "        font-size: 12px;\n" +
        "        color: cadetblue;\n" +
        "        font-family: sans-serif;\n" +
        " }\n" +
        " span.name.copy {\n" +
        "        font-style: italic;\n" +
        "        color: lightslategrey;\n" +
        " }\n" +
        "span.copy:after {\n" +
        "  content: \" ...\";\n" +
        "}\n" +
        " span.other {\n" +
        "       margin-left: 18px;\n" +
        " }\n" +
        " img {\n" +
        "        height: 14;\n" +
        "        transform: translateY(2px);\n" +
        "        margin-right: 4px;\n" +
        " }\n" +
        " ul {\n" +
        "        list-style-type: none;\n" +
        " }\n" +
        " li {\n" +
        "        margin-left: -10px;\n" +
        " }\n" +
        "</style>")
      .append("</head>\n<body>\n")
      .append("\n<ul id=\"root\">");
    for (var root : TaxGroup.values()) {
      if (root.parents.isEmpty()) {
        printGroup(root, sb, printed);
      }
    }
    sb.append("\n</ul>\n\n</body>\n</html>\n");

    System.out.println(sb);
  }

  void printGroup(TaxGroup tg, StringBuilder sb, Set<TaxGroup> printed) {
    final boolean seen = printed.contains(tg);
    sb.append("\n<li>");
    if (tg.isOther()) {
      sb.append("<span class=\"other\"></span>");
    } else {
      sb.append("<img src=\"")
        .append(tg.getIcon(SIZE.PX192))
        .append("\" />");
    }
    sb.append("<span class=\"name");
    if (seen) {
      sb.append(" copy");
    }
    sb.append("\">")
      .append(tg.name())
      .append("</span>");
    printed.add(tg);

    if (!seen) {
      var children = new ArrayList<TaxGroup>();
      for (var c : TaxGroup.values()) {
        if (c.parents.contains(tg)) {
          children.add(c);
        }
      }
      if (!children.isEmpty()) {
        sb.append("\n<ul>");
        for (var child : children) {
          printGroup(child, sb, printed);
        }
        sb.append("\n</ul>");
      }
    }

    sb.append("\n</li>");
  }

  @Test
  public void root() {
    assertEquals(Set.of(Eukaryotes), Birds.roots());
    assertEquals(Set.of(Eukaryotes), Arthropods.roots());
    assertEquals(Set.of(Eukaryotes), Chordates.roots());
    assertEquals(Set.of(Eukaryotes), Animals.roots());
    assertEquals(Set.of(Eukaryotes), Angiosperms.roots());
    assertEquals(Set.of(Eukaryotes), Algae.roots());
  }

  @Test
  public void contains() {
    assertTrue(Animals.contains(Birds));
    assertTrue(Animals.contains(Arthropods));
    assertTrue(Chordates.contains(Birds));

    assertFalse(Mammals.contains(Birds));
    assertFalse(Plants.contains(Animals));
  }

  @Test
  public void classification() {
    assertEquals(Set.of(Eukaryotes, Animals, Chordates), Birds.classification());
    assertEquals(Set.of(Eukaryotes, Plants, Protists), Algae.classification());
  }

  @Test
  public void code() {
    for (var g : TaxGroup.values()) {
      if (!g.parents.isEmpty()) {
        assertFalse(g.codes.isEmpty());
      } else if (g != OtherEukaryotes) {
        assertFalse(g.codes.isEmpty());
      } else {
        assertTrue(g.codes.isEmpty());
      }
    }
  }

  @Test
  public void phylopics() {
    for (var g : TaxGroup.values()) {
      if (g.name().startsWith("Other")) {
        assertNull(g.getPhylopic());
        assertNull(g.getIcon());
        assertNull(g.getIconSVG());
      } else {
        assertNotNull(g.getPhylopic());
        assertNotNull(g.getIcon());
        assertNotNull(g.getIconSVG());
      }
    }
  }

  @Test
  public void disparate() {
    assertTrue(Angiosperms.isDisparateTo(OtherEukaryotes));
    assertTrue(Angiosperms.isDisparateTo(Prokaryotes));
    assertTrue(Angiosperms.isDisparateTo(Animals));
    assertTrue(Angiosperms.isDisparateTo(Arachnids));
    assertTrue(Angiosperms.isDisparateTo(Gymnosperms));
    assertTrue(Coleoptera.isDisparateTo(Hemiptera));
    assertTrue(Animals.isDisparateTo(Plants));
    assertTrue(Basidiomycetes.isDisparateTo(Ascomycetes));
    assertTrue(Basidiomycetes.isDisparateTo(Mammals));

    assertFalse(Angiosperms.isDisparateTo(Plants));
    assertFalse(Plants.isDisparateTo(Angiosperms));
    assertFalse(Coleoptera.isDisparateTo(Insects));
    assertFalse(Algae.isDisparateTo(Plants));
    assertFalse(Algae.isDisparateTo(Protists));
    assertFalse(Plants.isDisparateTo(null));
    assertFalse(Animals.isDisparateTo(Chordates));

  }
}