package life.catalogue.api.vocab;

import org.gbif.nameparser.api.NomCode;

import java.util.Arrays;

import javax.annotation.Nullable;

import static org.gbif.nameparser.api.NomCode.*;
/**
 * A vocabulary to be used for the nomenclatural type status of a specimen.
 *
 * @see <a href="http://rs.gbif.org/vocabulary/gbif/type_status.xml">rs.gbif.org vocabulary</a>
 * @see <a href="http://www.bgbm.org/TDWG/CODATA/Schema/ABCD_2.06/HTML/ABCD_2.06.html#element_NomenclaturalTypeDesignations_Link">Types in ABCD</a>
 * @see <a href="http://www.insdc.org/controlled-vocabulary-typematerial-qualifer">Types in INSDC GenBank</a>
 * @see <a href="http://www1.biologie.uni-hamburg.de/b-online/library/tennessee/nom-type.htm">GLOSSARY OF "TYPE" TERMINOLOGY</a>
 * @see <a href="https://micropal-basel.unibas.ch/Colls_NMB/GENERALS/COMTYPES.HTML">Compendium of Types</a>
 * @see <a href="http://mailman.nhm.ku.edu/pipermail/taxacom/2000-November/084885.html">Taxacom discussion, Roger J. Burkhalter based on C.E. Decker DEFINITIONS OF KINDS OF TYPE SPECIMENS (F.B. Horrell in Geological Society of America Bulletin, Vol. 49,1929, p, 219)</a>
 *
 */
public enum TypeStatus {

  /**
   * An epitype is a specimen or illustration selected to serve as an interpretative type
   * when any kind of holotype, lectotype, etc. is demonstrably ambiguous
   * and cannot be critically identified for purposes of the precise application of the name of a taxon (see Art. ICN 9.7, 9.18).
   * An epitype supplements, rather than replaces existing types.
   *
   * ICN: A specimen or illustration selected to serve as an interpretative type when the holotype, lectotype
   * or previously designated neotype, or all original material associated with a validly published name,
   * cannot be identified for the purpose of the precise application of the name to a taxon (Art. 9.9).
   */
  EPITYPE(BOTANICAL),
  
  /**
   * An ergatotype is a specimen selected to represent a worker member in hymenopterans which have polymorphic castes.
   */
  ERGATOTYPE(ZOOLOGICAL),

  /**
   * A strain or living eukaryotic culture derived from some kind of type material.
   * A living isolate obtained from the type of a name when this is a culture permanently preserved in a metabolically inactive state (Rec. 8B.2).
   * Ex-types are not regulated by the botanical or zoological code.
   */
  EX_TYPE(BOTANICAL, ZOOLOGICAL),

  /**
   * One or more preparations of directly related individuals representing distinct stages in the life cycle,
   * which together form the type in an extant species of protistan [ICZN Article 72.5.4].
   * A hapantotype, while a series of individuals, is a holotype that must not be restricted by lectotype selection.
   * If an hapantotype is found to contain individuals of more than one species, however,
   * components may be excluded until it contains individuals of only one species [ICZN Article 73.3.2].
   */
  HAPANTOTYPE(ZOOLOGICAL),
  
  /**
   * The one specimen or other element used or designated by the original author at the time of publication
   * of the original description as the nomenclatural type of a species or infraspecific taxon.
   * A holotype may be 'explicit' if it is clearly stated in the originating publication
   * or 'implicit' if it is the single specimen proved to have been in the hands of the originating author when the description was published.
   *
   * ICN: The one specimen or illustration indicated as the nomenclatural type by the author(s) of a name of a new species or infraspecific taxon
   * or, when no type was indicated, used by the author(s) when preparing the account of the new taxon (Art. 9.1 and Note 1; see also Art. 9.2).
   */
  HOLOTYPE(true, BOTANICAL, ZOOLOGICAL, BACTERIAL),
  
  /**
   * A drawing or photograph (also called 'phototype') of a type specimen.
   * Note: the term 'iconotype' is not used in the ICN, but implicit in, e. g., ICN Art. 7 and 38.
   */
  ICONOTYPE(BOTANICAL),
  
  /**
   * A specimen or other element designated subsequent to the publication of the original description
   * from the original material (syntypes or paratypes) to serve as nomenclatural type.
   * Lectotype designation can occur only where no holotype was designated at the time of publication
   * or if it is missing (ICN Art. 7, ICZN Art. 74).
   *
   * ICN: One specimen or illustration designated from the original material as the nomenclatural type, 
   * in conformity with Art. 9.11 and 9.12, if the name was published without a holotype, 
   * or if the holotype is lost or destroyed, or if a type is found to belong to more than one taxon (Art. 9.3).
   */
  LECTOTYPE(true, BOTANICAL, ZOOLOGICAL, BACTERIAL),
  
  /**
   * A specimen designated as nomenclatural type subsequent to the publication of the original description
   * in cases where the original holotype, lectotype, all paratypes and syntypes are lost or destroyed,
   * or suppressed by the (botanical or zoological) commission on nomenclature.
   * In zoology also called 'Standard specimen' or 'Representative specimen'.
   *
   * ICN: A specimen or illustration selected to serve as nomenclatural type if no original material is extant
   * or as long as it is missing (Art. 9.8 and 9.13; see also Art. 9.16 and 9.19).
   */
  NEOTYPE(true, BOTANICAL, ZOOLOGICAL, BACTERIAL),

  /**
   *  ICN Art. 9.4: For the purposes of this Code, original material comprises the following elements:
   *
   *  (a) those specimens and illustrations (both unpublished and published prior to publication of the protologue)
   *  that the author associated with the taxon, and that were available to the author prior to, or at the time of,
   *  preparation of the description, diagnosis, or illustration with analysis (Art. 38.7 and 38.8) validating the name;
   *
   *  (b) any illustrations published as part of the protologue;
   *
   *  (c) the holotype and those specimens which, even if not seen by the author of the description or diagnosis
   *  validating the name, were indicated as types (syntypes or paratypes) of the name at its valid publication;
   *
   *  (d) the isotypes or isosyntypes of the name irrespective of whether such specimens were seen
   *  by either the author of the validating description or diagnosis or the author of the name (but see Art. 7.8, 7.9, and F.3.9).
   */
  ORIGINAL_MATERIAL(true, BOTANICAL),

  /**
   * All of the specimens in the type series of a species or infraspecific taxon other than the holotype (and, in botany, isotypes).
   * Paratypes must have been at the disposition of the author at the time when the original description was created
   * and must have been designated and indicated in the publication.
   * Judgment must be exercised on paratype status, for only rarely are specimens explicitly cited as paratypes,
   * but usually as 'specimens examined,' 'other material seen', etc.
   *
   * ICN: Any specimen cited in the protologue that is neither the holotype nor an isotype, nor one of the syntypes
   * if in the protologue two or more specimens were simultaneously designated as types (Art. 9.7).
   */
  PARATYPE(true, BOTANICAL, ZOOLOGICAL),

  /**
   * Strain designated as the type for the associated pathovar
   * See https://www.isppweb.org/about_tppb_naming.asp
   */
  PATHOTYPE(BACTERIAL),
  
  /**
   * One of the series of specimens used to describe a species or infraspecific taxon
   * when neither a single holotype nor a lectotype has been designated.
   * The syntypes collectively constitute the name-bearing type.
   *
   * ICN: Any specimen cited in the protologue when there is no holotype,
   * or any of two or more specimens simultaneously designated in the protologue as types (Art. 9.6).
   */
  SYNTYPE(true, BOTANICAL, ZOOLOGICAL),
  
  /**
   * One or more specimens collected at the same location as the type series (type locality), regardless of whether they are part of the type series.
   * Topotypes are not regulated by the botanical or zoological code. Also called 'locotype'. [Zoo./Bot.]
   */
  TOPOTYPE(BOTANICAL, ZOOLOGICAL),



  // ++++++++++++++++++++
  // DERIVED STATUS TYPES
  // ++++++++++++++++++++

  
  
  /**
   * An isotype is any duplicate of the holotype (i. e. part of a single gathering made by a collector at one time,
   * from which the holotype was derived); it is always a specimen (ICN Art. 7).
   */
  ISOTYPE(HOLOTYPE, BOTANICAL),

  /**
   * A duplicate specimen of the epitype
   */
  ISOEPITYPE(EPITYPE, BOTANICAL),

  /**
   * A duplicate of a lectotype, compare lectotype.
   */
  ISOLECTOTYPE(LECTOTYPE, BOTANICAL),

  /**
   * A duplicate of a neotype, compare neotype.
   */
  ISONEOTYPE(NEOTYPE, BOTANICAL),

  /**
   * An isoparatype is any duplicate of a paratype; it is always a specimen.
   */
  ISOPARATYPE(PARATYPE, BOTANICAL),

  /**
   * A duplicate of a syntype, compare isotype = duplicate of holotype.
   */
  ISOSYNTYPE(SYNTYPE, BOTANICAL),



  /**
   * All of the specimens in the syntype series of a species or infraspecific taxon other than the lectotype itself.
   * Also called 'lectoparatype'.
   */
  PARALECTOTYPE(LECTOTYPE, ZOOLOGICAL),

  /**
   * All of the specimens in the syntype series of a species or infraspecific taxon other than the neotype itself.
   * Also called 'neoparatype'.
   */
  PARANEOTYPE(NEOTYPE, ZOOLOGICAL),



  /**
   * A paralectotype specimen that is the opposite sex of the lectotype.
   * The term is not regulated by the ICZN.
   */
  ALLOLECTOTYPE(PARALECTOTYPE, ZOOLOGICAL),

  /**
   * A paraneotype specimen that is the opposite sex of the neotype.
   * The term is not regulated by the ICZN.
   */
  ALLONEOTYPE(PARANEOTYPE, ZOOLOGICAL),

  /**
   * A paratype specimen designated from the type series by the original author that is the opposite sex of the holotype.
   * The term is not regulated by the ICZN.
   */
  ALLOTYPE(PARATYPE, ZOOLOGICAL),



  /**
   * A copy or cast of holotype material (compare Plastotype).
   */
  PLASTOHOLOTYPE(HOLOTYPE),

  /**
   * A copy or cast of isotype material (compare Plastotype).
   */
  PLASTOISOTYPE(ISOTYPE),

  /**
   * A copy or cast of lectotype material (compare Plastotype).
   */
  PLASTOLECTOTYPE(LECTOTYPE),

  /**
   * A copy or cast of neotype material (compare Plastotype).
   */
  PLASTONEOTYPE(NEOTYPE),

  /**
   * A copy or cast of paratype material (compare Plastotype).
   */
  PLASTOPARATYPE(PARATYPE, BOTANICAL, ZOOLOGICAL),

  /**
   * A copy or cast of syntype material (compare Plastotype).
   */
  PLASTOSYNTYPE(SYNTYPE, BOTANICAL, ZOOLOGICAL),

  /**
   * A copy or cast of type material, esp. relevant for fossil types.
   * Not regulated by the botanical or zoological code (?). [Zoo./Bot.]
   */
  PLASTOTYPE(BOTANICAL, ZOOLOGICAL),

  /**
   * A specimen which was not used in the original description of a species or subspecies,
   * but which is used for a later description or figure of it.
   * A specimen illustrated in a publication. These are not type specimens.
   * Also called hypotype.
   */
  PLESIOTYPE(ZOOLOGICAL),

  /**
   * A specimen compared with the type and found to be conspecific with it.
   */
  HOMOEOTYPE(ZOOLOGICAL),


    /**
     * Any other or not interpretable status of the type material cited.
     */
  OTHER();

  private final TypeStatus base;
  private final NomCode[] codes;
  private final Boolean primary;

  TypeStatus(TypeStatus base, Boolean primary, NomCode... code) {
    this.base = base;
    this.primary = primary;
    this.codes = code;
    Arrays.sort(codes);
  }

  TypeStatus(TypeStatus base, NomCode... code) {
    this(base,null, code);
  }

  TypeStatus(boolean primary, NomCode... code) {
    this(null, primary, code);
  }

  TypeStatus(NomCode... code) {
    this(null, false, code);
  }

  @Nullable
  public TypeStatus getBase() {
    return base;
  }

  /**
   * Retrieves the deepest base, i.e. root status of a derived type status.
   * If no base exists the root is considered to be the current instance.
   */
  public TypeStatus getRoot() {
    return base == null ? this : base.getRoot();
  }

  @Nullable
  public NomCode[] getCodes() {
    return codes;
  }

  public boolean appliesTo(NomCode code) {
    return codes == null || Arrays.binarySearch(codes, code) >= 0;
  }

  public boolean isPrimary() {
    return primary != null ? primary : (
      base != null && base.isPrimary()
    );
  }
}
