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
package org.col.api.vocab;

import javax.annotation.Nullable;

/**
 * Vocabulary for the nomenclatural status of a name.
 *
 * TODO: consider adding:
 * nom. superfl.
 * nomen protectum; nom. prot. a name granted protection, more specific than conserved
 * nomen erratum (nom. err.) - a name given in error
 * nomen suppressum (nom. supp.; plural: nomina suppressa) – a name that has been suppressed and cannot be used
 *
 * See WFO Contributor Guidelines,
 * @see <a href="http://dev.e-taxonomy.eu/trac/wiki/NomenclaturalStatus">EDIT CDM</a>
 * @see <a href="http://wiki.tdwg.org/twiki/bin/view/UBIF/LinneanCoreNomenclaturalStatus">TDWG LinneanCoreNomenclaturalStatus</a>
 * @see <a href="http://www.biologybrowser.org/nomglos">Nomenclatural Glossary for Zoology</a>
 * @see <a href="http://www.northernontarioflora.ca/definitions.cfm">Northern Ontario plant database</a>
 * @see <a href="http://rs.gbif.org/vocabulary/gbif/nomenclatural_status.xml">rs.gbif.org vocabulary</a>
 * @see <a href="http://darwin.eeb.uconn.edu/systsem/table.html">Nomenclatural equivalences</a>
 */
public enum NomStatus {

  /**
   * Botany: Names that are validly published and legitimate
   * Zoology: Available name and potentially valid, i.e. not otherwise invalid
   *          for any other objective reason, such as being a junior homonym.
   */
  LEGITIMATE("nomen legitimum", null, "potentially valid"),

  /**
   * Orthographic variant. An alternative or corrected spelling for the name.
   *
   * In botanical nomenclature, an orthographical variant (abbreviated orth. var.) is a variant spelling
   * of the same name. For example, Hieronima and Hyeronima are orthographical variants of Hieronyma.
   * One of the spellings must be treated as the correct one. In this case, the spelling Hieronyma has been conserved
   * and is to be used as the correct spelling.
   * <p>
   * An inadvertent use of one of the other spellings has no consequences:
   * the name is to be treated as if it were correctly spelled.
   * Any subsequent use is to be corrected. Orthographical variants are treated in Art 61 of the ICBN.
   * <p>
   * In zoology, orthographical variants in the formal sense do not exist;
   * a misspelling or orthographic error is treated as a lapsus, a form of inadvertent error.
   * The first reviser is allowed to choose one variant for mandatory further use, but in other ways,
   * these errors generally have no further formal standing.
   * Inadvertent misspellings are treated in Art. 32-33 of the ICZN.
   */
  VARIANT("nomen orthographia", "orth. var.", "spelling variant"),

  /**
   * A nomen novum, replacement name or new substitute name indicates a scientific name
   * that is created specifically to replace another preoccupied name,
   * but only when this other name can not be used for technical, nomenclatural reasons.
   * It automatically inherits the same type and type locality and is commonly applied
   * to names proposed to replace junior homonyms.
   */
  REPLACEMENT("nomen novum", "nom. nov.", "replacement name"),

  /**
   * A scientific name that enjoys special nomenclatural protection, i.e. a name conserved in respective code.
   * Names classed as available and valid by action of the ICZN or ICBN exercising its Plenary Powers .
   * Includes rulings to conserve junior/later synonyms in place of rejected forgotten names (nomen oblitum).
   * Such names are entered on the Official Lists.
   *
   * Conservation is possible only for botanical names at the rank of family, genus or species.
   *
   * Conserved names are a more generalized definition than the one for nomen protectum,
   * which is specifically a conserved name that is either a junior synonym or homonym that is in use
   * because the senior synonym or homonym has been made a nomen oblitum ("forgotten name").
   */
  CONSERVED("nomen conservandum", "nom. cons.", "conserved name"),

  /**
   * A name that was not validly published according to the rules of the code,
   * or a name that was not accepted by the author in the original publication, for example,
   * if the name was suggested as a synonym of an accepted name.
   * In zoology this is called an unavailable name.
   * Example: Linaria vulgaris Hill, nom. inval.
   * Many names published by John Hill between 1753 and 1757 were not accepted as validly published.
   */
  UNAVAILABLE("nomen invalidum", "nom. inval.", "unavailable"),

  /**
   * The term is used to indicate a designation which looks exactly like a scientific name of an organism,
   * and may have originally been intended to be a scientific name, but fails to be one
   * because it has not (or has not yet) been published with an adequate description (or a reference to such a description),
   * and thus is a "bare" or "naked" name, one which cannot be accepted as it currently stands.
   * <p>
   * Because a nomen nudum fails to qualify as a formal scientific name,
   * a later author can publish a real scientific name that is identical in spelling.
   * If one and the same author puts a name in print, first as a nomen nudum and later publishes the name
   * accompanied by a description that meets the formal requirements,
   * then the date of publication of the latter, formally correct, publication becomes the name's date of establishment.
   * <p>
   * According to the rules of zoological nomenclature a nomen nudum is unavailable.
   * According to the rules of botanical nomenclature a nomen nudum is not validly published.
   */
  NAKED("nomen nudum", "nom. nud.", "naked name"),

  /**
   * A name which has been overlooked (literally, forgotten) and is no longer valid.
   */
  FORGOTTEN("nomen oblitum", "nom. obl.", "forgotten name"),

  /**
   * A nomen illegitimum is a validly published name, but one that contravenes some of the articles laid down by
   * the International Botanical Congress. The name could be illegitimate because:
   * <ul>
   * <li>(article 52) it was superfluous at its time of publication, i.e., the taxon (as represented by the type) already has a name</li>
   * <li>(articles 53 and 54) the name has already been applied to another plant (a homonym)</li>
   * </ul>
   *
   * Zoology: Available, but objectively invalid names, e.g. junior homonym or objective synonyms
   */
  ILLEGITIMATE("nomen illegitimum", "nom. illeg.", "objectively invalid"),

  /**
   * A name that was superfluous at its time of publication,
   * i. e. it was based on the same type as another, previously published name (ICN article 52).
   */
  SUPERFLUOUS("nomen superfluum", "nom. superfl.", "superfluous name"),

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
   * Includes nomen ambiguum
   */
  DOUBTFUL("nomen dubium", "nom. dub.", "doubtful"),

  /**
   * Species inquirenda, a species of doubtful identity requiring further investigation
   */
  UNEVALUATED("nomen inquirendum", null, "unevaluated");



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
   * @return true if the name is potentially accepted to be used, following the codes conventions.
   */
  public boolean isLegitimate() {
    return this == LEGITIMATE || this == CONSERVED || this == REPLACEMENT || this == VARIANT;
  }
}
