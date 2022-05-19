package life.catalogue.parser;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.model.ParserConfig;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.NameParserGBIF;
import org.gbif.nameparser.api.*;
import org.gbif.nameparser.utils.NamedThreadFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import static org.junit.Assert.*;

/**
 * The bulk of parsing tests are part of the GBIF Name Parser project.
 * Some name parsing tests kept in this project.
 */
public class NameParserTest {
  static Logger LOG = LoggerFactory.getLogger(NameParserTest.class);
  static AtomicInteger COUNTER = new AtomicInteger(0);

  NameParser parser = NameParser.PARSER;

  @Test
  public void parseAuthorship() throws Exception {
    assertAuthorship("L.f", null, "L.f");
    assertAuthorship("DC.", null, "DC.");
  }

  @Test
  public void inconsistentAuthorship() throws Exception {
    Name n = new Name();
    n.setScientificName("Boyeria vinosa (Say, 1840)");
    n.setAuthorship("(Say, 1840)");
    n.setGenus("Boyeria");
    n.setSpecificEpithet("vinosa");
    n.setRank(Rank.SPECIES);
    n.setId("957");
    IssueContainer issues = new IssueContainer.Simple();
    NameParser.PARSER.parse(n, issues);
    assertFalse(issues.hasIssues());
  }

  @Test
  public void parseVirusConfig() throws Exception {
    // no configs yet
    assertName("Aspilota vector Belokobylskij, 2007", "Aspilota vector Belokobylskij, 2007", NameType.VIRUS)
        .nothingElse();

    // add parser config
    ParserConfig cfg = new ParserConfig();
    cfg.updateID("Aspilota vector",  "Belokobylskij, 2007");
    cfg.setGenus("Aspilota");
    cfg.setSpecificEpithet("vector");
    cfg.setCombinationAuthorship(Authorship.yearAuthors("2007", "Belokobylskij"));
    cfg.setType(NameType.SCIENTIFIC);
    cfg.setRank(Rank.SPECIES);
    addToParser(cfg);

    assertName("Aspilota vector Belokobylskij, 2007", "Aspilota vector")
        .species("Aspilota", "vector")
        .combAuthors("2007", "Belokobylskij")
        .type(NameType.SCIENTIFIC)
        .nothingElse();
  }

  private static void addToParser(ParserConfig obj){
    ParsedName pn = Name.toParsedName(obj);
    pn.setTaxonomicNote(obj.getTaxonomicNote());
    NameParser.PARSER.configs().setName(obj.getScientificName() + " " + obj.getAuthorship(), pn);
  }

  @Test
  public void normalizeAuthorship() throws Exception {
    // https://github.com/CatalogueOfLife/backend/issues/1067
    assertNull(NameParser.normalizeAuthorship("(non Snyder, 1904)", "non Snyder, 1904"));
    assertNull(NameParser.normalizeAuthorship("( non Snyder, 1904 )", "(non Snyder, 1904)"));

    assertEquals("(Huteret ali)", NameParser.normalizeAuthorship("(Huteret ali)", null));
    assertEquals("(Huter et al.) P. D. Sell & Whitehead", NameParser.normalizeAuthorship("(Huter et al.) P. D. Sell & Whitehead", null));
    assertNull(NameParser.normalizeAuthorship("sensu Wilson & Henderson [Brit. Rust Fungi:47-48 (1966) ]; fide Checklist of Basidiomycota of Great", "sensu Wilson & Henderson[Brit. Rust Fungi:47-48 (1966) ]; fide Checklist of Basidiomycota of Great"));
    assertEquals("Brouss. ex Willd.", NameParser.normalizeAuthorship("Brouss. ex Willd.", null));
    assertEquals("(St.John) Sachet", NameParser.normalizeAuthorship("(St.John) Sachet", null));
    assertEquals("Trautv. & Meyer", NameParser.normalizeAuthorship("Trautv.&Meyer", null));
    assertEquals("Trautv. & Meyer", NameParser.normalizeAuthorship("Trautv. & Meyer", null));
    assertEquals("Rossi, 1988", NameParser.normalizeAuthorship("Rossi 1988 non DC.1988", "non DC. 1988"));
    assertEquals("Rossi, 1790", NameParser.normalizeAuthorship("Rossi, 1790", null));
    assertEquals("Rossi, 1790", NameParser.normalizeAuthorship("Rossi 1790", null));
    assertEquals("(Rossi, 1790)", NameParser.normalizeAuthorship("(Rossi 1790)", null));
    assertEquals("(Ridl.) ined.", NameParser.normalizeAuthorship("(Ridl.) ined.", null));
    assertEquals("(L.) DC", NameParser.normalizeAuthorship("( L.)DC ", null));
    assertEquals("(Walther & Rück) van der Damme & Resorbin, 1999", NameParser.normalizeAuthorship("( Walther&Rück ) van der Damme and Resorbin 1999", null));
    assertEquals("Miller, 1989", NameParser.normalizeAuthorship("Miller 1989 sensu Carol 2001", "sensu Carol 2001"));

    assertNull(NameParser.normalizeAuthorship("(non Scacchi, 1836) sensu Zibrowius, 1968", "(non Scacchi, 1836) sensu Zibrowius, 1968"));
    assertEquals("Fischer-Le Saux et al., 1999", NameParser.normalizeAuthorship("Fischer-Le Saux et al., 1999 emend. Akhurst et al., 2004", "emend. Akhurst et al. , 2004"));
    assertEquals("Engl., nom. illeg.", NameParser.normalizeAuthorship("Engl., nom. illeg., non. A. lancea.", "non. A.lancea."));
  }

  @Test
  public void parseManuscript() throws Exception {
    assertName("Acranthera virescens (Ridl.) ined.", "Acranthera virescens")
          .species("Acranthera", "virescens")
          .basAuthors(null, "Ridl.")
          .type(NameType.SCIENTIFIC)
          .nomNote("ined.")
          .status(NomStatus.MANUSCRIPT)
          .nothingElse();
  }

  @Test
  @Ignore
  public void parsePhrases() throws Exception {
    assertName("Lepidoptera sp. JGP0404", "Lepidoptera sp.")
      .monomial("Lepidoptera", Rank.SPECIES)
      .type(NameType.INFORMAL)
      .status(NomStatus.MANUSCRIPT)
      .nothingElse();
  }
  
  @Test
  public void parseSubgenera() throws Exception {
    assertName("Eteone subgen. Mysta", "Eteone (Mysta)") // no code given, defaults to zoology
        .infraGeneric("Eteone", Rank.SUBGENUS, "Mysta")
        .nothingElse();
    
    assertName("Eteone (Mysta)", Rank.SUBGENUS, NomCode.ZOOLOGICAL, "Eteone (Mysta)")
        .infraGeneric("Eteone", Rank.SUBGENUS, "Mysta")
        .nothingElse();
  }
  
  @Test
  public void parseSpecies() throws Exception {
    
    assertName("Zophosis persis (Chatanay 1914)", "Zophosis persis")
        .species("Zophosis", "persis")
        .basAuthors("1914", "Chatanay")
        .nothingElse();
    
    assertName("Abies alba Mill.", "Abies alba")
        .species("Abies", "alba")
        .combAuthors(null, "Mill.")
        .nothingElse();
  
    assertName("Acranthera virescens (Ridl.) ined.", "Acranthera virescens")
        .species("Acranthera", "virescens")
        .basAuthors(null, "Ridl.")
        .status(NomStatus.MANUSCRIPT)
        .nomNote("ined.")
        .nothingElse();

    assertName("Alstonia vieillardii Van Heurck & Müll.Arg.", "Alstonia vieillardii")
        .species("Alstonia", "vieillardii")
        .combAuthors(null, "Van Heurck", "Müll.Arg.")
        .nothingElse();
    //TODO: do we expect d'urvilleana or durvilleana ???
    assertName("Angiopteris d'urvilleana de Vriese", "Angiopteris d'urvilleana")
        .species("Angiopteris", "d'urvilleana")
        .combAuthors(null, "de Vriese")
        .nothingElse();
  }
  
  @Test
  public void parseInfraSpecies() throws Exception {
    
    assertName("Abies alba ssp. alpina Mill.", "Abies alba alpina")
        .infraSpecies("Abies", "alba", Rank.SUBSPECIES, "alpina")
        .combAuthors(null, "Mill.")
        .nothingElse();
    
    assertName("Festuca ovina L. subvar. gracilis Hackel", "Festuca ovina subvar. gracilis")
        .infraSpecies("Festuca", "ovina", Rank.SUBVARIETY, "gracilis")
        .combAuthors(null, "Hackel")
        .nothingElse();
    
    assertName("Pseudomonas syringae pv. aceris (Ark, 1939) Young, Dye & Wilkie, 1978", "Pseudomonas syringae pv. aceris")
        .infraSpecies("Pseudomonas", "syringae", Rank.PATHOVAR, "aceris")
        .combAuthors("1978", "Young", "Dye", "Wilkie")
        .basAuthors("1939", "Ark");
    
    assertName("Baccharis microphylla Kunth var. rhomboidea Wedd. ex Sch. Bip. (nom. nud.)", "Baccharis microphylla var. rhomboidea")
        .infraSpecies("Baccharis", "microphylla", Rank.VARIETY, "rhomboidea")
        .combAuthors(null, "Sch.Bip.")
        .combExAuthors("Wedd.")
        .nomNote("nom.nud.")
        .nothingElse();
    
    assertName("Achillea millefolium subsp. pallidotegula B. Boivin var. pallidotegula", "Achillea millefolium var. pallidotegula")
        .infraSpecies("Achillea", "millefolium", Rank.VARIETY, "pallidotegula")
        .nothingElse();
    
  }
  
  @Test
  public void test4PartedNames() throws Exception {
    assertName("Bombus sichelii alticola latofasciatus", "Bombus sichelii latofasciatus")
        .infraSpecies("Bombus", "sichelii", Rank.INFRASUBSPECIFIC_NAME, "latofasciatus")
        .nothingElse();
    
    assertName("Poa pratensis kewensis primula (L.) Rouy, 1913", "Poa pratensis primula")
        .infraSpecies("Poa", "pratensis", Rank.INFRASUBSPECIFIC_NAME, "primula")
        .combAuthors("1913", "Rouy")
        .basAuthors(null, "L.")
        .nothingElse();
    
    assertName("Acipenser gueldenstaedti colchicus natio danubicus Movchan, 1967", "Acipenser gueldenstaedti natio danubicus")
        .infraSpecies("Acipenser", "gueldenstaedti", Rank.NATIO, "danubicus")
        .combAuthors("1967", "Movchan");
  }
  
  @Test
  public void parseMonomial() throws Exception {
    
    assertName("Acripeza Guérin-Ménéville 1838", "Acripeza")
        .monomial("Acripeza", Rank.UNRANKED)
        .combAuthors("1838", "Guérin-Ménéville")
        .nothingElse();
    
  }
  
  @Test
  public void parseInfraGeneric() throws Exception {
    
    assertName("Zignoella subgen. Trematostoma Sacc.", "Zignoella (Trematostoma)")
        .infraGeneric("Zignoella", Rank.SUBGENUS, "Trematostoma")
        .combAuthors(null, "Sacc.")
        .nothingElse();
    
    assertName("subgen. Trematostoma Sacc.", "Trematostoma")
        .infraGeneric(null, Rank.SUBGENUS, "Trematostoma")
        .combAuthors(null, "Sacc.")
        .nothingElse();
    
  }
  
  @Test
  public void parsePlaceholder() throws Exception {
    
    assertName("[unassigned] Cladobranchia", "[unassigned] Cladobranchia", NameType.PLACEHOLDER)
        .nothingElse();
    
    assertName("Biota incertae sedis", "Biota incertae sedis", NameType.PLACEHOLDER)
        .nothingElse();
    
    assertName("Mollusca not assigned", "Mollusca not assigned", NameType.PLACEHOLDER)
        .nothingElse();
  }

  @Test
  public void flagBadAuthorship() throws Exception {
    assertName("Cynoglossus aurolineatus Not applicable", "Cynoglossus aurolineatus", NameType.SCIENTIFIC)
      .species("Cynoglossus", "aurolineatus")
      .issue(Issue.AUTHORSHIP_REMOVED)
      .nothingElse();

    assertName("Asellus major Not given", "Asellus major", NameType.SCIENTIFIC)
      .species("Asellus", "major")
      .issue(Issue.AUTHORSHIP_REMOVED)
      .nothingElse();
  }
  
  /**
   * Expect empty results for nothing or whitespace
   */
  @Test
  public void testEmpty() throws Exception {
    assertEquals(Optional.empty(), NameParser.PARSER.parse(null));
    assertEquals(Optional.empty(), NameParser.PARSER.parse(""));
    assertEquals(Optional.empty(), NameParser.PARSER.parse(" "));
    assertEquals(Optional.empty(), NameParser.PARSER.parse("\t"));
    assertEquals(Optional.empty(), NameParser.PARSER.parse("\n"));
    assertEquals(Optional.empty(), NameParser.PARSER.parse("\t\n"));
  }
  
  /**
   * Avoid NPEs and other exceptions for very short non names and other extremes found in occurrences.
   */
  @Test
  public void testAvoidNPE() throws Exception {
    assertNoName("\\");
    assertNoName(".");
    assertNoName("a");
    assertNoName("X");
    assertNoName("@");
    assertNoName("&nbsp;");
  }
  
  private void assertNoName(String name) throws UnparsableException, InterruptedException {
    assertName(name, name, NameType.NO_NAME)
        .nothingElse();
  }
  
  @Test
  public void parseSanctioned() throws Exception {
    // sanctioning authors not supported
    // https://github.com/GlobalNamesArchitecture/gnparser/issues/409
    assertName("Agaricus compactus sarcocephalus (Fr. : Fr.) Fr. ", "Agaricus compactus sarcocephalus")
        .infraSpecies("Agaricus", "compactus", Rank.INFRASPECIFIC_NAME, "sarcocephalus")
        .combAuthors(null, "Fr.")
        .basAuthors(null, "Fr.")
        .nothingElse();
    
    assertName("Boletus versicolor L. : Fr.", "Boletus versicolor")
        .species("Boletus", "versicolor")
        .combAuthors(null, "L.")
        .sanctAuthor("Fr.")
        .nothingElse();
  }
  
  @Test
  public void parseNothotaxa() throws Exception {
    // https://github.com/GlobalNamesArchitecture/gnparser/issues/410
    assertName("Iris germanica nothovar. florentina", "Iris germanica nothovar. florentina")
        .infraSpecies("Iris", "germanica", Rank.VARIETY, "florentina")
        .notho(NamePart.INFRASPECIFIC)
        .nothingElse();
    
    assertName("Abies alba var. ×alpina L.", "Abies alba nothovar. alpina")
        .infraSpecies("Abies", "alba", Rank.VARIETY, "alpina")
        .notho(NamePart.INFRASPECIFIC)
        .combAuthors(null, "L.")
        .nothingElse();
  }
  
  @Test
  public void parseHybridFormulas() throws Exception {
    // fix hybrids formulas
    assertName("Asplenium rhizophyllum DC. x ruta-muraria E.L. Braun 1939", "Asplenium rhizophyllum DC. x ruta-muraria E.L. Braun 1939", NameType.HYBRID_FORMULA)
        .nothingElse();
  }

  @Test
  public void phraseNames() throws Exception {
    assertName("Acacia sp. Bigge Island (A.A. Mitchell 3436) WA Herbarium", "Acacia sp. Bigge Island (A.A. Mitchell 3436) WA Herbarium", NameType.INFORMAL)
      .species("Acacia", null)
      .unparsed("Bigge Island (A.A. Mitchell 3436) WA Herbarium")
      .nothingElse();
  }


  <T> void assertThreadPoolEmpty(List<? extends Callable<T>> jobs) throws Exception {
    ExecutorService exec = Executors.newFixedThreadPool(10, new NamedThreadFactory("test-executor"));

    // this blocks until all jobs are done
    System.out.println("Wait for job completion");
    var res = exec.invokeAll(jobs);
    res.forEach(r -> System.out.println(r.toString()));

    // now sleep >3s (the default idleTime) to let idle threads be cleaned up
    TimeUnit.SECONDS.sleep(4);

    int counter = 0;
    long wsize = 1;
    while (wsize > 0) {
      wsize = Thread.getAllStackTraces().keySet().stream()
                          .filter(t -> t.getName().startsWith(NameParserGBIF.THREAD_NAME))
                          .count();
      System.out.println(wsize + " worker threads still existing");
      if (counter++ == 20) {
        break;
      }
      TimeUnit.SECONDS.sleep(1);
    }

    parser.close();
    assertEquals(0, wsize);
  }

  @Test
  public void threadPoolNameParsing() throws Exception {
    parser = new NameParser(new NameParserGBIF(50, 0, 4));
    List<ParseNameJob> jobs = Lists.newArrayList();
    IntStream.range(1, 10).forEach(i -> {
      jobs.add(new ParseNameJob("Desmarestia ligulata subsp. muelleri (M.E.Ramirez, A.F.Peters, S.S.Y. Wong, A.H.Y. Ngan, Riggs, J.L.L. Teng, A.F.Peters, E.C.Yang, A.F.Peters, E.C.Yang & F.C.Küpper & van Reine, 2014) S.S.Y. Wong, A.H.Y. Ngan, Riggs, J.L.L. Teng, A.F.Peters, E.C.Yang, A.F.Peters, E.C.Yang, F.C.Küpper, van Reine, S.S.Y. Wong, A.H.Y. Ngan, Riggs, J.L.L. Teng, A.F.Peters, E.C.Yang, A.F.Peters, E.C.Yang, A.F.Peters, S.S.Y. Wong, A.H.Y. Ngan, Riggs, J.L.L. Teng, A.F.Peters, E.C.Yang, A.F.Peters, E.C.Yang & F.C.Küpper & van Reine, 2014) S.S.Y. Wong, A.H.Y. Ngan, Riggs, J.L.L. Teng, A.F.Peters, E.C.Yang, A.F.Peters, E.C.Yang, F.C.Küpper, van Reine, S.S.Y. Wong, A.H.Y. Ngan, Riggs, J.L.L. Teng, A.F.Peters, E.C.Yang, A.F.Peters, E.C.Yang, F.C.Küpper & van Reine, 2014"));
    });
    assertThreadPoolEmpty(jobs);
  }

  @Test
  public void threadPoolAuthorParsingTimeout() throws Exception {
    parser = new NameParser(new NameParserGBIF(50, 0, 4));
    List<ParseAuthorshipJob> jobs = Lists.newArrayList();
    IntStream.range(1, 10).forEach(i -> {
      jobs.add(new ParseAuthorshipJob("Coloma, Carvajal-Endara, Dueñas, Paredes-Recalde, Morales-Mite, Almeida-Reinoso et al., 2012)"));
    });
    assertThreadPoolEmpty(jobs);
  }

  class ParseNameJob implements Callable<ParsedNameUsage> {
    public final int key = COUNTER.incrementAndGet();
    private final String name;

    ParseNameJob(int year) {
      this.name = "Abies alba Miller, " + year;
    }

    ParseNameJob(String name) {
      this.name = name;
    }

    @Override
    public ParsedNameUsage call() throws Exception {
      try {
        LOG.info("Start {}", key);
        return parser.parse(name).orElse(null);
      } catch (Exception e) {
        LOG.info("Failed {}", key, e);
        throw e;
      }
    }
  }

  class ParseAuthorshipJob implements Callable<ParsedAuthorship> {
    private final String authorship;

    ParseAuthorshipJob(String name) {
      this.authorship = name;
    }

    @Override
    public ParsedAuthorship call() throws Exception {
      return parser.parseAuthorship(authorship).orElse(null);
    }
  }

  static void assertAuthorship(String authorship, String year, String... authors) throws UnparsableException {
    ParsedAuthorship pa = NameParser.PARSER.parseAuthorship(authorship).get();
    Authorship a = new Authorship();
    a.setYear(year);
    for (String x : authors) {
      a.getAuthors().add(x);
    }
    assertEquals(a, pa.getCombinationAuthorship());
  }
  
  static NameAssertion assertName(String rawName, String sciname) throws UnparsableException, InterruptedException {
    return assertName(rawName, sciname, NameType.SCIENTIFIC);
  }
  
  static NameAssertion assertName(String rawName, String sciname, NameType type) throws UnparsableException, InterruptedException {
    return assertName(rawName, null, null, sciname, type);
  }
  
  static NameAssertion assertName(String rawName, Rank rank, NomCode code, String sciname) throws UnparsableException, InterruptedException {
    return assertName(rawName, rank, code, sciname, NameType.SCIENTIFIC);
  }
  
  static NameAssertion assertName(String rawName, Rank rank, NomCode code, String sciname, NameType type) throws UnparsableException, InterruptedException {
    var issues = new IssueContainer.Simple();
    ParsedNameUsage n = NameParser.PARSER.parse(rawName, rank, code, issues).get();
    assertEquals(sciname, n.getName().getScientificName());
    return new NameAssertion(n.getName(), issues).type(type);
  }

  static class NameAssertion {
    private final Name n;
    private final IssueContainer issues;
    private Set<NP> tested = Sets.newHashSet();
    
    private enum NP {
      EPITHETS,
      NOTHO,
      AUTH,
      EXAUTH,
      BAS,
      EXBAS,
      SANCT,
      RANK,
      TYPE,
      STATUS,
      NOMNOTE,
      UNPARSED,
      REMARKS
    }
    
    public NameAssertion(Name n, IssueContainer issues) {
      this.n = n;
      this.issues = issues;
    }
    
    void nothingElse() {
      for (NP p : NP.values()) {
        if (!tested.contains(p)) {
          switch (p) {
            case EPITHETS:
              assertNull(n.getGenus());
              assertNull(n.getInfragenericEpithet());
              assertNull(n.getSpecificEpithet());
              assertNull(n.getInfraspecificEpithet());
              break;
            case NOTHO:
              assertNull(n.getNotho());
              break;
            case AUTH:
              assertNull(n.getCombinationAuthorship().getYear());
              assertTrue(n.getCombinationAuthorship().getAuthors().isEmpty());
              break;
            case EXAUTH:
              assertTrue(n.getCombinationAuthorship().getExAuthors().isEmpty());
              break;
            case BAS:
              assertNull(n.getBasionymAuthorship().getYear());
              assertTrue(n.getBasionymAuthorship().getAuthors().isEmpty());
              break;
            case EXBAS:
              assertTrue(n.getBasionymAuthorship().getExAuthors().isEmpty());
              break;
            case SANCT:
              assertNull(n.getSanctioningAuthor());
              break;
            case RANK:
              assertEquals(Rank.UNRANKED, n.getRank());
              break;
            case TYPE:
              assertEquals(NameType.SCIENTIFIC, n.getType());
              break;
            case STATUS:
              assertNull(n.getNomStatus());
              break;
            case NOMNOTE:
              assertNull(n.getNomenclaturalNote());
              break;
            case UNPARSED:
              assertNull(n.getUnparsed());
              break;
            case REMARKS:
              assertNull(n.getRemarks());
          }
        }
      }
    }
    
    private NameAssertion add(NP... props) {
      for (NP p : props) {
        tested.add(p);
      }
      return this;
    }

    NameAssertion issue(Issue issue) {
      assertTrue(this.issues.hasIssue(issue));
      return this;
    }

    NameAssertion noIssue(Issue issue) {
      assertFalse(this.issues.hasIssue(issue));
      return this;
    }

    NameAssertion monomial(String monomial, Rank rank) {
      assertEquals(monomial, n.getUninomial());
      assertNull(n.getGenus());
      assertNull(n.getInfragenericEpithet());
      assertNull(n.getSpecificEpithet());
      assertNull(n.getInfraspecificEpithet());
      assertEquals(rank, n.getRank());
      return add(NP.EPITHETS, NP.RANK);
    }
    
    NameAssertion infraGeneric(String genus, Rank rank, String infraGeneric) {
      assertEquals(genus, n.getGenus());
      assertEquals(infraGeneric, n.getInfragenericEpithet());
      assertNull(n.getSpecificEpithet());
      assertNull(n.getInfraspecificEpithet());
      assertEquals(rank, n.getRank());
      return add(NP.EPITHETS, NP.RANK);
    }
    
    NameAssertion species(String genus, String epithet) {
      assertEquals(genus, n.getGenus());
      assertNull(n.getInfragenericEpithet());
      assertEquals(epithet, n.getSpecificEpithet());
      assertNull(n.getInfraspecificEpithet());
      assertEquals(Rank.SPECIES, n.getRank());
      return add(NP.EPITHETS, NP.RANK);
    }
    
    NameAssertion infraSpecies(String genus, String epithet, Rank rank, String infraEpithet) {
      assertEquals(genus, n.getGenus());
      assertNull(n.getInfragenericEpithet());
      assertEquals(epithet, n.getSpecificEpithet());
      assertEquals(infraEpithet, n.getInfraspecificEpithet());
      assertEquals(rank, n.getRank());
      return add(NP.EPITHETS, NP.RANK);
    }
    
    NameAssertion combAuthors(String year, String... authors) {
      assertEquals(year, n.getCombinationAuthorship().getYear());
      assertEquals(Lists.newArrayList(authors), n.getCombinationAuthorship().getAuthors());
      return add(NP.AUTH);
    }
    
    NameAssertion notho(NamePart notho) {
      assertEquals(notho, n.getNotho());
      return add(NP.NOTHO);
    }
    
    NameAssertion sanctAuthor(String author) {
      assertEquals(author, n.getSanctioningAuthor());
      return add(NP.SANCT);
    }
    
    NameAssertion combExAuthors(String... authors) {
      assertEquals(Lists.newArrayList(authors), n.getCombinationAuthorship().getExAuthors());
      return add(NP.EXAUTH);
    }
    
    NameAssertion basAuthors(String year, String... authors) {
      assertEquals(year, n.getBasionymAuthorship().getYear());
      assertEquals(Lists.newArrayList(authors), n.getBasionymAuthorship().getAuthors());
      return add(NP.BAS);
    }
    
    NameAssertion basExAuthors(String year, String... authors) {
      assertEquals(Lists.newArrayList(authors), n.getBasionymAuthorship().getExAuthors());
      return add(NP.EXBAS);
    }

    NameAssertion type(NameType type) {
      assertEquals(type, n.getType());
      return add(NP.TYPE);
    }
  
    NameAssertion status(NomStatus status) {
      assertEquals(status, n.getNomStatus());
      return add(NP.STATUS);
    }

    NameAssertion remarks(String remarks) {
      assertEquals(remarks, n.getRemarks());
      return add(NP.REMARKS);
    }

    NameAssertion nomNote(String nomNote) {
      assertEquals(nomNote, n.getNomenclaturalNote());
      return add(NP.NOMNOTE);
    }

    NameAssertion unparsed(String unparsed) {
      assertEquals(unparsed, n.getUnparsed());
      return add(NP.UNPARSED);
    }
  }
}