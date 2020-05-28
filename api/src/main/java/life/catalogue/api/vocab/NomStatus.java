/*
 * Copyright 2014 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package life.catalogue.api.vocab;

import org.gbif.nameparser.api.NomCode;

import javax.annotation.Nullable;

/**
 * Vocabulary for the nomenclatural status of a name.
 * <p>
 * Distilled to the most important entries relevant for both codes and leaving the detailed
 * status and argumentation to free text and the name relations instead.
 * <p>
 * We use BioCode terminology as much as possible, see https://github.com/Sp2000/colplus-backend/issues/318
 * As valid/invalid is very overloaded and used for different things in both codes we avoid the term entirely.
 * <p>
 * The main purpose for this enumeration is to differ between available/unavailable and legitimate/illegitimate names
 * that can be used as a correct/"valid" name.
 * <p>
 * See WFO Contributor Guidelines,
 *
 * @see <a href="https://archive.bgbm.org/IAPT/biocode/biocode.html#Table">BioCode</a>
 * @see <a href="https://dev.e-taxonomy.eu/redmine/projects/edit/wiki/NomenclaturalStatus">EDIT CDM</a>
 * @see <a href="http://wiki.tdwg.org/twiki/bin/view/UBIF/LinneanCoreNomenclaturalStatus">TDWG LinneanCoreNomenclaturalStatus</a>
 * @see <a href="http://www.biologybrowser.org/nomglos">Nomenclatural Glossary for Zoology</a>
 * @see <a href="http://www.northernontarioflora.ca/definitions.cfm">Northern Ontario plant database</a>
 * @see <a href="http://rs.gbif.org/vocabulary/gbif/nomenclatural_status.xml">rs.gbif.org vocabulary</a>
 * @see <a href="http://darwin.eeb.uconn.edu/systsem/table.html">Nomenclatural equivalences</a>
 * @see <a href="https://github.com/SpeciesFileGroup/nomen">NOMEN ontology</a>
 */
public enum NomStatus {
  
  ESTABLISHED("nomen validum", null, "available"),
  
  /**
   * A name that was not validly published according to the rules of the code,
   * or a name that was not accepted by the author in the original publication, for example,
   * if the name was suggested as a synonym of an accepted name.
   * In zoology referred to as an unavailable name.
   * <p>
   * There are many reasons for a name to be unavailable.
   * The exact reason should be indicated in the Names remarks field, e.g.:
   * - published as synonym (pro syn.) ICN Art 36
   * - nomen nudum (nom. nud.) published without an adequate description
   * - not latin (ICN Art 32)
   * - provisional/manuscript names
   * - suppressed publication
   * - tautonym (ICN) e.g. Opuntia opuntia H.Karst.
   */
  NOT_ESTABLISHED("nomen invalidum", "nom. inval.", "unavailable"),
  
  /**
   * Botany: Names that are validly published and legitimate
   * Zoology: Available name and potentially valid, i.e. not otherwise invalid
   * for any other objective reason, such as being a junior homonym.
   */
  ACCEPTABLE("nomen legitimum", null, "potentially valid"),
  
  /**
   * An established name and thus has nomenclatural standing.
   * But one that objectively contravenes some of the rules laid down by nomenclatural codes
   * and thus cannot be used as a name for an accepted taxon.
   * <p>
   * The name could be unacceptable because:
   * <ul>
   * <li>Botany: superfluous at its time of publication (article 52), i.e., the taxon (as represented by the type) already has a name</li>
   * <li>Botany: the name has already been applied to another plant (a homonym) articles 53 and 54</li>
   * <li>Zoology: junior homonym or objective synonyms</li>
   * <li>Zoology: nomen oblitum</li>
   * <li>Zoology: suppressed name</li>
   * </ul>
   */
  UNACCEPTABLE("nomen illegitimum", "nom. illeg.", "objectively invalid"),
  
  /**
   * A scientific name that enjoys special nomenclatural protection,
   * i.e. a name conserved, protected or sanctioned in respective code.
   *
   * Names classified as available and valid by action of the ICZN or ICBN exercising its Plenary Powers .
   * Includes rulings to conserve junior/later synonyms in place of rejected forgotten names (nomen oblitum)
   * via "Reversal of Precedence" in accordance with ICZN Article 23.9.1.
   * Such names are entered on the Official Lists.
   * <p>
   * Conservation of botanical names is only possible at the rank of family, genus or species.
   * <p>
   * Conserved names are a more generalized definition than the one for nomen protectum,
   * which is specifically a conserved name that is either a junior synonym or homonym that is in use
   * because the senior synonym or homonym has been made an available, but invalid nomen oblitum ("forgotten name").
   */
  CONSERVED("nomen conservandum", "nom. cons.", "conserved name"),
  
  /**
   * Rejected / suppressed name. Inverse of conserved. Outright rejection is possible for a name at any rank.
   */
  REJECTED("nomen rejiciendum", "nom. rej.", "rejected"),
  
  /**
   * A name of uncertain sense, of doubtful validity.
   * E.g. the name Encephalartos tridentatus (Willdenow) Lehmann (Pugillus 6, 1834) is a nomen dubium
   * which may refer to several species of Encephalartos or Macrozamia.
   * ICZN: doubtful or dubious names, names which are not certainly applicable to any known taxon or
   * for which the evidence is insufficient to permit recognition of the taxon to which they belong.
   * May possess availability conducive to uncertainty and instability.
   * <p>
   * In botany a name whose application is uncertain;
   * the confusion being derived from an incomplete or confusing description.
   * Example: Platanus hispanica auct., non Mill. ex Münchh., nom. dub.
   * The application of the name Platanus hispanica is uncertain, so the name has been rejected
   * in favour of Platanus ×acerifolia (Aiton) Willd., pro. sp.
   * <p>
   * Includes nomen ambiguum and nomen inquirendum, a species of doubtful identity requiring further investigation.
   */
  DOUBTFUL("nomen dubium", "nom. dub.", "doubtful"),
  
  /**
   * An unpublished, provisional name that was given a temporary placeholder name to work with.
   * Sometimes these names do not get properly published for decades and can be cited in other works.
   * Example: Genoplesium vernalis D.L. Jones ms.
   * <p>
   * Often abbreviated as ined. (ineditus) and sometimes called chironym/cheironym.
   * <p>
   * In the 1980s in Australia, botanists agreed on a formula (Croft 1989, Conn 2000) for use with
   * unpublished names to avoid the confusion that was arising through the use of such things as
   * “Verticordia sp.1”, “Verticordia sp.2” etc.
   * There was no guarantee that what was called “sp.1” in one institution was identical to “sp.1” in a second.
   * <p>
   * The agreed formula is in the form of:
   * Genus sp. <colloquial name or description> (<Voucher>)
   * <p>
   * Examples:
   * Prostanthera sp. Somersbey (B.J.Conn 4024)
   * Elseya sp. nov. (AMS – R140984)
   * <p>
   * Some zoologists use a similar convention, but it is not done so universally.
   */
  MANUSCRIPT("manuscript name", "ms.", "manuscript name"),
  
  /**
   * A name usage erroneously cited without a sec/sensu indication so it appears to be a published homonym with a different authority.
   * See https://en.wikipedia.org/wiki/Chresonym
   */
  CHRESONYM("chresonym", null, "chresonym");
  
  
  private final String botany;
  private final String abbreviated;
  private final String zoology;
  
  private NomStatus(String botany, String abbreviated, String zoology) {
    this.botany = botany;
    this.abbreviated = abbreviated;
    this.zoology = zoology;
  }
  
  public String getBotanicalLabel() {
    return botany;
  }
  
  /**
   * The abbreviated status name, often used in botany.
   * For example nom. inval.
   */
  @Nullable
  public String getAbbreviatedLabel() {
    return abbreviated;
  }
  
  @Nullable
  public String getZoologicalLabel() {
    return zoology;
  }

  /**
   * Returns a label based on the nomenclatural code
   * @param code
   */
  public String getLabel(NomCode code) {
    if (code == null) {
      return this.name().toLowerCase().replaceAll("_", " ");
    }
    switch (code) {
      case BOTANICAL:
      case BACTERIAL:
      case CULTIVARS:
      case VIRUS:
        return botany;

      default:
        return zoology;
    }
  }

  /**
   * @return true if the name status indicates that it is validly published / available.
   */
  public boolean isAvailable() {
    return this != CHRESONYM && this != MANUSCRIPT && this != NOT_ESTABLISHED;
  }
  
  /**
   * @return true if the name is potentially accepted to be used, following the codes conventions.
   * It excludes doubtful or unevaluated names.
   */
  public boolean isLegitimate() {
    return this == ACCEPTABLE || this == CONSERVED;
  }
}
