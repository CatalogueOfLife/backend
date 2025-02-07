package life.catalogue.parser;

import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.common.io.Resources;

import org.gbif.nameparser.api.NomCode;

import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class NomenOntology {
  private static final Logger LOG = LoggerFactory.getLogger(NomenOntology.class);
  private static final String RESOURCE = "parser/nomen.owl";
  private static final String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  private static final String NS_OWL = "http://www.w3.org/2002/07/owl#";

  public static final URI NAMESPACE = URI.create("http://purl.obolibrary.org/obo/");
  private static final String ROOT = NAMESPACE.resolve("NOMEN").toString();

  private Map<String, Nomen> entries;

  public static class Nomen {
    public final String name;
    public Nomen parent;
    public NomCode code;
    public NomStatus status;
    public NomRelType nomRelType;

    Nomen(String about) {
      this.name = norm(about);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Nomen)) return false;
      Nomen nomen = (Nomen) o;
      return name.equals(nomen.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }

  public NomenOntology(){
    Map<String, Nomen> map = read().stream()
      .collect(Collectors.toMap(n -> n.name, n -> n));
    // link parents
    map.values().forEach(n -> {
      if (n.parent != null) {
        n.parent = map.get(n.parent.name);
      }
    });
    entries = Map.copyOf(map);
    LOG.info("Loaded {} NOMEN classes", entries.size());
    // lookup status, reltype & code
    mapNomen();
  }

  private void mapNomen() {
    apply("NOMEN_0000107", NomCode.ZOOLOGICAL);
    apply("NOMEN_0000109", NomCode.BOTANICAL);
    apply("NOMEN_0000110", NomCode.BACTERIAL);
    apply("NOMEN_0000111", NomCode.VIRUS);
    // ICN
    apply("NOMEN_0000377", NomStatus.NOT_ESTABLISHED);
    apply("NOMEN_0000008", NomStatus.NOT_ESTABLISHED);
    apply("NOMEN_0000007", NomStatus.ESTABLISHED);
    apply("NOMEN_0000386", NomStatus.UNACCEPTABLE);
    apply("NOMEN_0000384", NomStatus.ACCEPTABLE);
    apply("NOMEN_0000009", NomStatus.CONSERVED);
    apply("NOMEN_0000385", NomStatus.CONSERVED);
    // ICNP
    apply("NOMEN_0000082", NomStatus.NOT_ESTABLISHED);
    apply("NOMEN_0000083", NomStatus.NOT_ESTABLISHED);
    apply("NOMEN_0000084", NomStatus.ESTABLISHED);
    apply("NOMEN_0000085", NomStatus.UNACCEPTABLE);
    apply("NOMEN_0000086", NomStatus.ACCEPTABLE);
    // ICTV
    apply("NOMEN_0000126", NomStatus.NOT_ESTABLISHED);
    apply("NOMEN_0000125", NomStatus.ESTABLISHED);
    apply("NOMEN_0000128", NomStatus.UNACCEPTABLE);
    apply("NOMEN_0000127", NomStatus.ACCEPTABLE);
    // ICZN
    apply("NOMEN_0000168", NomStatus.NOT_ESTABLISHED);
    apply("NOMEN_0000219", NomStatus.REJECTED);
    apply("NOMEN_0000223", NomStatus.ESTABLISHED);
    apply("NOMEN_0000226", NomStatus.UNACCEPTABLE);
    apply("NOMEN_0000224", NomStatus.ACCEPTABLE);
    apply("NOMEN_0000225", NomStatus.DOUBTFUL);
    apply("NOMEN_0000129", NomStatus.DOUBTFUL);
    // relations
    apply("NOMEN_0000270", NomRelType.REPLACEMENT_NAME);
    apply("NOMEN_0000275", NomRelType.SPELLING_CORRECTION);
    apply("NOMEN_0000277", NomRelType.HOMOTYPIC);
    apply("NOMEN_0000289", NomRelType.LATER_HOMONYM);
    apply("NOMEN_0000290", NomRelType.LATER_HOMONYM);
    apply("NOMEN_0000291", NomRelType.LATER_HOMONYM);
  }

  private void apply(String name, NomCode code){
    apply(entries.get(norm(name)), n -> n.code=code);
  }

  private void apply(String name, NomRelType relType){
    apply(entries.get(norm(name)), n -> {
      n.nomRelType = relType;
    });
  }

  private void apply(String name, NomStatus status){
    apply(entries.get(norm(name)), n -> n.status=status);
  }

  private void apply(Nomen n, Consumer<Nomen> func){
    Preconditions.checkNotNull(n, "NOMEN concept missing");
    func.accept(n);
    // apply to children
    for (Nomen c : entries.values()) {
      if (c.parent != null && c.parent.equals(n)) {
        apply(c, func);
      }
    }
  }

  /**
   * Reads all classes of the NOMEN owl ontology.
   * @return key=class URI, value = subclassOf
   */
  private List<Nomen> read() {
    try {
      InputStream stream = Resources.stream(RESOURCE);
      XMLStreamReader parser = XMLInputFactory.newInstance().createXMLStreamReader(stream, "UTF-8");

      final List<Nomen> nomen = new ArrayList<>();
      Nomen n = null;
      int event;
      while ((event = parser.next()) != XMLStreamConstants.END_DOCUMENT) {
        switch (event) {
          case XMLStreamConstants.START_ELEMENT:
            switch (parser.getLocalName()) {
              case "Class":
              case "ObjectProperty":
                if (n != null) {
                  // we are already inside a class - ignore!
                  break;
                }
                String id = parser.getAttributeValue(NS_RDF, "about");
                //System.out.println(id);
                //printAttrs(parser);
                if (id != null && id.startsWith(ROOT)) {
                  n = new Nomen(id);
                }
                break;
              case "subClassOf":
              case "subPropertyOf":
                if (n != null) {
                  String sub = parser.getAttributeValue(NS_RDF, "resource");
                  //printAttrs(parser);
                  if (sub != null && sub.startsWith(ROOT)) {
                    n.parent = new Nomen(sub);
                  }
                }
                break;
            }
            break;

          case XMLStreamConstants.END_ELEMENT:
            switch (parser.getLocalName()) {
              case "Class":
              case "ObjectProperty":
                if (n != null) {
                  nomen.add(n);
                  n = null;
                }
                break;
            }
        }
      }
      parser.close();
      Preconditions.checkArgument(nomen.size() > 200, "NOMEN ontology contains only " + nomen.size() + " entries");
      return nomen;

    } catch (XMLStreamException e) {
      throw new IllegalStateException("Failed to read NOMEN ontology", e);
    }
  }

  private static void printAttrs(XMLStreamReader parser){
    System.out.println(parser.getAttributeCount() + " attributes");
    for (int i=0; i<parser.getAttributeCount(); i++){
      System.out.println(parser.getAttributeName(i) + " -> " + parser.getAttributeValue(i));
    }
  }

  public int size() {
    return entries.size();
  }

  public Collection<Nomen> list() {
    return entries.values();
  }

  public NomCode code(String name) {
    if (entries.containsKey(norm(name))) {
      return entries.get(norm(name)).code;
    }
    return null;
  }

  public NomStatus status(String name) {
    if (entries.containsKey(norm(name))) {
      return entries.get(norm(name)).status;
    }
    return null;
  }

  private static String norm(String name){
    if (name.startsWith(ROOT)) {
      name = name.substring(ROOT.length()-5);
    }
    return name.toUpperCase();
  }
}
