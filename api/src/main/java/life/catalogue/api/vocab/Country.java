/*
 * Copyright 2014 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package life.catalogue.api.vocab;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Enumeration for all current ISO 3166-1 ALPHA2 country codes using 2 letters,
 * with the exception of PS and TW which are overridden by GBIF.
 * <p>
 * INTERNATIONAL_WATERS and OCEANIA are additional wellknown entries.
 * <p>
 * User-assigned code elements are ignored.
 * These are codes at the disposal of users who need to add further names
 * of countries, territories, or other geographical entities to their in-house application of ISO 3166-1,
 * and the ISO 3166/MA will never use these codes in the updating process of the standard.
 * The following codes can be user-assigned:
 * Alpha-2: AA, QM to QZ, XA to XZ, and ZZ
 * Alpha-3: AAA to AAZ, QMA to QZZ, XAA to XZZ, and ZZA to ZZZ
 * Numeric: 900 to 999
 * <p>
 * The enumeration maps to ALPHA3 3-letter codes.
 *
 * @see <a href="https://www.iso.org/obp/ui/#home">ISO Online Browsing Platform</a>
 * @see <a href="http://en.wikipedia.org/wiki/ISO_3166">ISO 3166 on Wikipedia</a>
 * @see <a href="http://en.wikipedia.org/wiki/ISO_3166-1_alpha-2">ISO 3166-1 alpha2 on Wikipedia</a>
 * @see <a href="http://en.wikipedia.org/wiki/ISO_3166-3">ISO_3166-3 on Wikipedia</a>
 * <p>
 */

public enum Country implements Area {
  
  /**
   * Afghanistan.
   */
  AFGHANISTAN("AF", "AFG", 4, "Afghanistan", Continent.ASIA),
  
  /**
   * Åland Islands.
   */
  ALAND_ISLANDS("AX", "ALA", 248, "Åland Islands", Continent.EUROPE),
  
  /**
   * Albania.
   */
  ALBANIA("AL", "ALB", 8, "Albania", Continent.EUROPE),
  
  /**
   * Algeria.
   */
  ALGERIA("DZ", "DZA", 12, "Algeria", Continent.AFRICA),
  
  /**
   * American Samoa.
   */
  AMERICAN_SAMOA("AS", "ASM", 16, "American Samoa", Continent.OCEANIA),
  
  /**
   * Andorra.
   */
  ANDORRA("AD", "AND", 20, "Andorra", Continent.EUROPE),
  
  /**
   * Angola.
   */
  ANGOLA("AO", "AGO", 24, "Angola", Continent.AFRICA),
  
  /**
   * Anguilla.
   */
  ANGUILLA("AI", "AIA", 660, "Anguilla", Continent.SOUTH_AMERICA),
  
  /**
   * Antarctica.
   */
  ANTARCTICA("AQ", "ATA", 10, "Antarctica", Continent.ANTARCTICA),
  
  /**
   * Antigua and Barbuda.
   */
  ANTIGUA_BARBUDA("AG", "ATG", 28, "Antigua and Barbuda", Continent.SOUTH_AMERICA),
  
  /**
   * Argentina.
   */
  ARGENTINA("AR", "ARG", 32, "Argentina", Continent.SOUTH_AMERICA),
  
  /**
   * Armenia.
   */
  ARMENIA("AM", "ARM", 51, "Armenia", Continent.EUROPE),
  
  /**
   * Aruba.
   */
  ARUBA("AW", "ABW", 533, "Aruba", Continent.SOUTH_AMERICA),
  
  /**
   * Australia.
   */
  AUSTRALIA("AU", "AUS", 36, "Australia", Continent.OCEANIA),
  
  /**
   * Austria.
   */
  AUSTRIA("AT", "AUT", 40, "Austria", Continent.EUROPE),
  
  /**
   * Azerbaijan.
   */
  AZERBAIJAN("AZ", "AZE", 31, "Azerbaijan", Continent.EUROPE),
  
  /**
   * Bahamas.
   */
  BAHAMAS("BS", "BHS", 44, "Bahamas", Continent.SOUTH_AMERICA),
  
  /**
   * Bahrain.
   */
  BAHRAIN("BH", "BHR", 48, "Bahrain", Continent.ASIA),
  
  /**
   * Bangladesh.
   */
  BANGLADESH("BD", "BGD", 50, "Bangladesh", Continent.ASIA),
  
  /**
   * Barbados.
   */
  BARBADOS("BB", "BRB", 52, "Barbados", Continent.SOUTH_AMERICA),
  
  /**
   * Belarus.
   */
  BELARUS("BY", "BLR", 112, "Belarus", Continent.EUROPE),
  
  /**
   * Belgium.
   */
  BELGIUM("BE", "BEL", 56, "Belgium", Continent.EUROPE),
  
  /**
   * Belize.
   */
  BELIZE("BZ", "BLZ", 84, "Belize", Continent.SOUTH_AMERICA),
  
  /**
   * Benin.
   */
  BENIN("BJ", "BEN", 204, "Benin", Continent.AFRICA),
  
  /**
   * Bermuda.
   */
  BERMUDA("BM", "BMU", 60, "Bermuda", Continent.SOUTH_AMERICA),
  
  /**
   * Bhutan.
   */
  BHUTAN("BT", "BTN", 64, "Bhutan", Continent.ASIA),
  
  /**
   * Bolivia, Plurinational State of.
   */
  BOLIVIA("BO", "BOL", 68, "Bolivia, Plurinational State of", Continent.SOUTH_AMERICA),
  
  /**
   * Bonaire, Sint Eustatius and Saba.
   */
  BONAIRE_SINT_EUSTATIUS_SABA("BQ", "BES", 535, "Bonaire, Sint Eustatius and Saba", Continent.SOUTH_AMERICA),
  
  /**
   * Bosnia and Herzegovina.
   */
  BOSNIA_HERZEGOVINA("BA", "BIH", 70, "Bosnia and Herzegovina", Continent.EUROPE),
  
  /**
   * Botswana.
   */
  BOTSWANA("BW", "BWA", 72, "Botswana", Continent.AFRICA),
  
  /**
   * Bouvet Island.
   */
  BOUVET_ISLAND("BV", "BVT", 74, "Bouvet Island", Continent.ANTARCTICA),
  
  /**
   * Brazil.
   */
  BRAZIL("BR", "BRA", 76, "Brazil", Continent.SOUTH_AMERICA),
  
  /**
   * British Indian Ocean Territory.
   */
  BRITISH_INDIAN_OCEAN_TERRITORY("IO", "IOT", 86, "British Indian Ocean Territory", Continent.ASIA),
  
  /**
   * Brunei Darussalam.
   */
  BRUNEI_DARUSSALAM("BN", "BRN", 96, "Brunei Darussalam", Continent.ASIA),
  
  /**
   * Bulgaria.
   */
  BULGARIA("BG", "BGR", 100, "Bulgaria", Continent.EUROPE),
  
  /**
   * Burkina Faso.
   */
  BURKINA_FASO("BF", "BFA", 854, "Burkina Faso", Continent.AFRICA),
  
  /**
   * Burundi.
   */
  BURUNDI("BI", "BDI", 108, "Burundi", Continent.AFRICA),
  
  /**
   * Cambodia.
   */
  CAMBODIA("KH", "KHM", 116, "Cambodia", Continent.ASIA),
  
  /**
   * Cameroon.
   */
  CAMEROON("CM", "CMR", 120, "Cameroon", Continent.AFRICA),
  
  /**
   * Canada.
   */
  CANADA("CA", "CAN", 124, "Canada", Continent.NORTH_AMERICA),
  
  /**
   * Cape Verde.
   */
  CAPE_VERDE("CV", "CPV", 132, "Cape Verde", Continent.AFRICA),
  
  /**
   * Cayman Islands.
   */
  CAYMAN_ISLANDS("KY", "CYM", 136, "Cayman Islands", Continent.SOUTH_AMERICA),
  
  /**
   * Central African Republic.
   */
  CENTRAL_AFRICAN_REPUBLIC("CF", "CAF", 140, "Central African Republic", Continent.AFRICA),
  
  /**
   * Chad.
   */
  CHAD("TD", "TCD", 148, "Chad", Continent.AFRICA),
  
  /**
   * Chile.
   */
  CHILE("CL", "CHL", 152, "Chile", Continent.SOUTH_AMERICA),
  
  /**
   * China.
   */
  CHINA("CN", "CHN", 156, "China", Continent.ASIA),
  
  /**
   * Christmas Island.
   */
  CHRISTMAS_ISLAND("CX", "CXR", 162, "Christmas Island", Continent.ASIA),
  
  /**
   * COCOS (Keeling) Islands.
   */
  COCOS_ISLANDS("CC", "CCK", 166, "Cocos (Keeling) Islands", Continent.AFRICA),
  
  /**
   * Colombia.
   */
  COLOMBIA("CO", "COL", 170, "Colombia", Continent.SOUTH_AMERICA),
  
  /**
   * Comoros.
   */
  COMOROS("KM", "COM", 174, "Comoros", Continent.AFRICA),
  
  /**
   * Congo, the Democratic Republic of the.
   */
  CONGO_DEMOCRATIC_REPUBLIC("CD", "COD", 180, "Congo, Democratic Republic of the", Continent.AFRICA),
  
  /**
   * Congo.
   */
  CONGO("CG", "COG", 178, "Congo, Republic of the", Continent.AFRICA),
  
  /**
   * Cook Islands.
   */
  COOK_ISLANDS("CK", "COK", 184, "Cook Islands", Continent.OCEANIA),
  
  /**
   * Costa Rica.
   */
  COSTA_RICA("CR", "CRI", 188, "Costa Rica", Continent.SOUTH_AMERICA),
  
  /**
   * Côte d'Ivoire.
   */
  CÔTE_DIVOIRE("CI", "CIV", 384, "Côte d'Ivoire", Continent.AFRICA),
  
  /**
   * Croatia.
   */
  CROATIA("HR", "HRV", 191, "Croatia", Continent.EUROPE),
  
  /**
   * Cuba.
   */
  CUBA("CU", "CUB", 192, "Cuba", Continent.SOUTH_AMERICA),
  
  /**
   * Curaçao.
   */
  CURAÇAO("CW", "CUW", 531, "Curaçao", Continent.SOUTH_AMERICA),
  
  /**
   * Cyprus.
   */
  CYPRUS("CY", "CYP", 196, "Cyprus", Continent.EUROPE),
  
  /**
   * Czech Republic.
   */
  CZECH_REPUBLIC("CZ", "CZE", 203, "Czech Republic", Continent.EUROPE),
  
  /**
   * Denmark.
   */
  DENMARK("DK", "DNK", 208, "Denmark", Continent.EUROPE),
  
  /**
   * Djibouti.
   */
  DJIBOUTI("DJ", "DJI", 262, "Djibouti", Continent.AFRICA),
  
  /**
   * Dominica.
   */
  DOMINICA("DM", "DMA", 212, "Dominica", Continent.SOUTH_AMERICA),
  
  /**
   * Dominican Republic.
   */
  DOMINICAN_REPUBLIC("DO", "DOM", 214, "Dominican Republic", Continent.SOUTH_AMERICA),
  
  /**
   * Ecuador.
   */
  ECUADOR("EC", "ECU", 218, "Ecuador", Continent.SOUTH_AMERICA),
  
  /**
   * Egypt.
   */
  EGYPT("EG", "EGY", 818, "Egypt", Continent.AFRICA),
  
  /**
   * El Salvador.
   */
  EL_SALVADOR("SV", "SLV", 222, "El Salvador", Continent.SOUTH_AMERICA),
  
  /**
   * Equatorial Guinea.
   */
  EQUATORIAL_GUINEA("GQ", "GNQ", 226, "Equatorial Guinea", Continent.AFRICA),
  
  /**
   * Eritrea.
   */
  ERITREA("ER", "ERI", 232, "Eritrea", Continent.AFRICA),
  
  /**
   * Estonia.
   */
  ESTONIA("EE", "EST", 233, "Estonia", Continent.EUROPE),
  
  /**
   * Ethiopia.
   */
  ETHIOPIA("ET", "ETH", 231, "Ethiopia", Continent.AFRICA),
  
  /**
   * FALKLAND ISLANDS (Malvinas).
   */
  FALKLAND_ISLANDS("FK", "FLK", 238, "Falkland Islands (Malvinas)", Continent.SOUTH_AMERICA),
  
  /**
   * Faroe Islands.
   */
  FAROE_ISLANDS("FO", "FRO", 234, "Faroe Islands", Continent.EUROPE),
  
  /**
   * Fiji.
   */
  FIJI("FJ", "FJI", 242, "Fiji", Continent.OCEANIA),
  
  /**
   * Finland.
   */
  FINLAND("FI", "FIN", 246, "Finland", Continent.EUROPE),
  
  /**
   * France.
   */
  FRANCE("FR", "FRA", 250, "France", Continent.EUROPE),
  
  /**
   * French Guiana.
   */
  FRENCH_GUIANA("GF", "GUF", 254, "French Guiana", Continent.SOUTH_AMERICA),
  
  /**
   * French Polynesia.
   */
  FRENCH_POLYNESIA("PF", "PYF", 258, "French Polynesia", Continent.OCEANIA),
  
  /**
   * French Southern Territories.
   */
  FRENCH_SOUTHERN_TERRITORIES("TF", "ATF", 260, "French Southern Territories", Continent.ANTARCTICA),
  
  /**
   * Gabon.
   */
  GABON("GA", "GAB", 266, "Gabon", Continent.AFRICA),
  
  /**
   * Gambia.
   */
  GAMBIA("GM", "GMB", 270, "Gambia", Continent.AFRICA),
  
  /**
   * Georgia.
   */
  GEORGIA("GE", "GEO", 268, "Georgia", Continent.EUROPE),
  
  /**
   * Germany.
   */
  GERMANY("DE", "DEU", 276, "Germany", Continent.EUROPE),
  
  /**
   * Ghana.
   */
  GHANA("GH", "GHA", 288, "Ghana", Continent.AFRICA),
  
  /**
   * Gibraltar.
   */
  GIBRALTAR("GI", "GIB", 292, "Gibraltar", Continent.EUROPE),
  
  /**
   * Greece.
   */
  GREECE("GR", "GRC", 300, "Greece", Continent.EUROPE),
  
  /**
   * Greenland.
   */
  GREENLAND("GL", "GRL", 304, "Greenland", Continent.EUROPE),
  
  /**
   * Grenada.
   */
  GRENADA("GD", "GRD", 308, "Grenada", Continent.SOUTH_AMERICA),
  
  /**
   * Guadeloupe.
   */
  GUADELOUPE("GP", "GLP", 312, "Guadeloupe", Continent.SOUTH_AMERICA),
  
  /**
   * Guam.
   */
  GUAM("GU", "GUM", 316, "Guam", Continent.OCEANIA),
  
  /**
   * Guatemala.
   */
  GUATEMALA("GT", "GTM", 320, "Guatemala", Continent.SOUTH_AMERICA),
  
  /**
   * Guernsey.
   */
  GUERNSEY("GG", "GGY", 831, "Guernsey", Continent.EUROPE),
  
  /**
   * Guinea.
   */
  GUINEA("GN", "GIN", 324, "Guinea", Continent.AFRICA),
  
  /**
   * Guinea-Bissau.
   */
  GUINEA_BISSAU("GW", "GNB", 624, "Guinea-Bissau", Continent.AFRICA),
  
  /**
   * Guyana.
   */
  GUYANA("GY", "GUY", 328, "Guyana", Continent.SOUTH_AMERICA),
  
  /**
   * Haiti.
   */
  HAITI("HT", "HTI", 332, "Haiti", Continent.SOUTH_AMERICA),
  
  /**
   * Heard Island and McDonald Islands.
   */
  HEARD_MCDONALD_ISLANDS("HM", "HMD", 334, "Heard Island and McDonald Islands", Continent.ANTARCTICA),
  
  /**
   * HOLY SEE (Vatican City State).
   */
  VATICAN("VA", "VAT", 336, "Holy See", Continent.EUROPE),
  
  /**
   * Honduras.
   */
  HONDURAS("HN", "HND", 340, "Honduras", Continent.SOUTH_AMERICA),
  
  /**
   * Hong Kong.
   */
  HONG_KONG("HK", "HKG", 344, "Hong Kong", Continent.ASIA),
  
  /**
   * Hungary.
   */
  HUNGARY("HU", "HUN", 348, "Hungary", Continent.EUROPE),
  
  /**
   * Iceland.
   */
  ICELAND("IS", "ISL", 352, "Iceland", Continent.EUROPE),
  
  /**
   * India.
   */
  INDIA("IN", "IND", 356, "India", Continent.ASIA),
  
  /**
   * Indonesia.
   */
  INDONESIA("ID", "IDN", 360, "Indonesia", Continent.ASIA),
  
  /**
   * Iran, Islamic Republic of.
   */
  IRAN("IR", "IRN", 364, "Iran, Islamic Republic of", Continent.ASIA),
  
  /**
   * Iraq.
   */
  IRAQ("IQ", "IRQ", 368, "Iraq", Continent.ASIA),
  
  /**
   * Ireland.
   */
  IRELAND("IE", "IRL", 372, "Ireland", Continent.EUROPE),
  
  /**
   * Isle of Man.
   */
  ISLE_OF_MAN("IM", "IMN", 833, "Isle of Man", Continent.EUROPE),
  
  /**
   * Israel.
   */
  ISRAEL("IL", "ISR", 376, "Israel", Continent.EUROPE),
  
  /**
   * Italy.
   */
  ITALY("IT", "ITA", 380, "Italy", Continent.EUROPE),
  
  /**
   * Jamaica.
   */
  JAMAICA("JM", "JAM", 388, "Jamaica", Continent.SOUTH_AMERICA),
  
  /**
   * Japan.
   */
  JAPAN("JP", "JPN", 392, "Japan", Continent.ASIA),
  
  /**
   * Jersey.
   */
  JERSEY("JE", "JEY", 832, "Jersey", Continent.EUROPE),
  
  /**
   * Jordan.
   */
  JORDAN("JO", "JOR", 400, "Jordan", Continent.ASIA),
  
  /**
   * Kazakhstan.
   */
  KAZAKHSTAN("KZ", "KAZ", 398, "Kazakhstan", Continent.EUROPE),
  
  /**
   * Kenya.
   */
  KENYA("KE", "KEN", 404, "Kenya", Continent.AFRICA),
  
  /**
   * Kiribati.
   */
  KIRIBATI("KI", "KIR", 296, "Kiribati", Continent.OCEANIA),
  
  /**
   * Korea, Democratic People's Republic of.
   */
  KOREA_NORTH("KP", "PRK", 408, "Korea, Democratic People's Republic of", Continent.ASIA),
  
  /**
   * Korea, Republic of.
   */
  KOREA_SOUTH("KR", "KOR", 410, "Korea, Republic of", Continent.ASIA),
  
  /**
   * Kosovo.
   * User-assigned temporary code, XK and XKX are the same as used by several other international organizations.
   * 902 is assigned by GBIF, but these codes aren't used anywhere.
   */
  KOSOVO("XK", "XKX", 902, "Kosovo", Continent.EUROPE),

  /**
   * Kuwait.
   */
  KUWAIT("KW", "KWT", 414, "Kuwait", Continent.ASIA),
  
  /**
   * Kyrgyzstan.
   */
  KYRGYZSTAN("KG", "KGZ", 417, "Kyrgyzstan", Continent.EUROPE),
  
  /**
   * Lao People's Democratic Republic.
   */
  LAO("LA", "LAO", 418, "Lao People's Democratic Republic", Continent.ASIA),
  
  /**
   * Latvia.
   */
  LATVIA("LV", "LVA", 428, "Latvia", Continent.EUROPE),
  
  /**
   * Lebanon.
   */
  LEBANON("LB", "LBN", 422, "Lebanon", Continent.ASIA),
  
  /**
   * Lesotho.
   */
  LESOTHO("LS", "LSO", 426, "Lesotho", Continent.AFRICA),
  
  /**
   * Liberia.
   */
  LIBERIA("LR", "LBR", 430, "Liberia", Continent.AFRICA),
  
  /**
   * Libya.
   */
  LIBYA("LY", "LBY", 434, "Libya", Continent.AFRICA),
  
  /**
   * Liechtenstein.
   */
  LIECHTENSTEIN("LI", "LIE", 438, "Liechtenstein", Continent.EUROPE),
  
  /**
   * Lithuania.
   */
  LITHUANIA("LT", "LTU", 440, "Lithuania", Continent.EUROPE),
  
  /**
   * Luxembourg.
   */
  LUXEMBOURG("LU", "LUX", 442, "Luxembourg", Continent.EUROPE),
  
  /**
   * Macao.
   */
  MACAO("MO", "MAC", 446, "Macao", Continent.ASIA),
  
  /**
   * North Macedonia.
   * The former Yugoslav Republic of.
   */
  MACEDONIA("MK", "MKD", 807, "North Macedonia", Continent.EUROPE),
  
  /**
   * Madagascar.
   */
  MADAGASCAR("MG", "MDG", 450, "Madagascar", Continent.AFRICA),
  
  /**
   * Malawi.
   */
  MALAWI("MW", "MWI", 454, "Malawi", Continent.AFRICA),
  
  /**
   * Malaysia.
   */
  MALAYSIA("MY", "MYS", 458, "Malaysia", Continent.ASIA),
  
  /**
   * Maldives.
   */
  MALDIVES("MV", "MDV", 462, "Maldives", Continent.ASIA),
  
  /**
   * Mali.
   */
  MALI("ML", "MLI", 466, "Mali", Continent.AFRICA),
  
  /**
   * Malta.
   */
  MALTA("MT", "MLT", 470, "Malta", Continent.EUROPE),
  
  /**
   * Marshall Islands.
   */
  MARSHALL_ISLANDS("MH", "MHL", 584, "Marshall Islands", Continent.OCEANIA),
  
  /**
   * Martinique.
   */
  MARTINIQUE("MQ", "MTQ", 474, "Martinique", Continent.SOUTH_AMERICA),
  
  /**
   * Mauritania.
   */
  MAURITANIA("MR", "MRT", 478, "Mauritania", Continent.AFRICA),
  
  /**
   * Mauritius.
   */
  MAURITIUS("MU", "MUS", 480, "Mauritius", Continent.AFRICA),
  
  /**
   * Mayotte.
   */
  MAYOTTE("YT", "MYT", 175, "Mayotte", Continent.AFRICA),
  
  /**
   * Mexico.
   */
  MEXICO("MX", "MEX", 484, "Mexico", Continent.SOUTH_AMERICA),
  
  /**
   * Micronesia, Federated States of.
   */
  MICRONESIA("FM", "FSM", 583, "Micronesia, Federated States of", Continent.OCEANIA),
  
  /**
   * Moldova, Republic of.
   */
  MOLDOVA("MD", "MDA", 498, "Moldova, Republic of", Continent.EUROPE),
  
  /**
   * Monaco.
   */
  MONACO("MC", "MCO", 492, "Monaco", Continent.EUROPE),
  
  /**
   * Mongolia.
   */
  MONGOLIA("MN", "MNG", 496, "Mongolia", Continent.ASIA),
  
  /**
   * Montenegro.
   */
  MONTENEGRO("ME", "MNE", 499, "Montenegro", Continent.EUROPE),
  
  /**
   * Montserrat.
   */
  MONTSERRAT("MS", "MSR", 500, "Montserrat", Continent.SOUTH_AMERICA),
  
  /**
   * Morocco.
   */
  MOROCCO("MA", "MAR", 504, "Morocco", Continent.AFRICA),
  
  /**
   * Mozambique.
   */
  MOZAMBIQUE("MZ", "MOZ", 508, "Mozambique", Continent.AFRICA),
  
  /**
   * Myanmar.
   */
  MYANMAR("MM", "MMR", 104, "Myanmar", Continent.ASIA),
  
  /**
   * Namibia.
   */
  NAMIBIA("NA", "NAM", 516, "Namibia", Continent.AFRICA),
  
  /**
   * Nauru.
   */
  NAURU("NR", "NRU", 520, "Nauru", Continent.OCEANIA),
  
  /**
   * Nepal.
   */
  NEPAL("NP", "NPL", 524, "Nepal", Continent.ASIA),
  
  /**
   * Netherlands.
   */
  NETHERLANDS("NL", "NLD", 528, "Netherlands", Continent.EUROPE),
  
  /**
   * New Caledonia.
   */
  NEW_CALEDONIA("NC", "NCL", 540, "New Caledonia", Continent.OCEANIA),
  
  /**
   * New Zealand.
   */
  NEW_ZEALAND("NZ", "NZL", 554, "New Zealand", Continent.OCEANIA),
  
  /**
   * Nicaragua.
   */
  NICARAGUA("NI", "NIC", 558, "Nicaragua", Continent.SOUTH_AMERICA),
  
  /**
   * Niger.
   */
  NIGER("NE", "NER", 562, "Niger", Continent.AFRICA),
  
  /**
   * Nigeria.
   */
  NIGERIA("NG", "NGA", 566, "Nigeria", Continent.AFRICA),
  
  /**
   * Niue.
   */
  NIUE("NU", "NIU", 570, "Niue", Continent.OCEANIA),
  
  /**
   * Norfolk Island.
   */
  NORFOLK_ISLAND("NF", "NFK", 574, "Norfolk Island", Continent.OCEANIA),
  
  /**
   * Northern Mariana Islands.
   */
  NORTHERN_MARIANA_ISLANDS("MP", "MNP", 580, "Northern Mariana Islands", Continent.OCEANIA),
  
  /**
   * Norway.
   */
  NORWAY("NO", "NOR", 578, "Norway", Continent.EUROPE),
  
  /**
   * Oman.
   */
  OMAN("OM", "OMN", 512, "Oman", Continent.ASIA),
  
  /**
   * Pakistan.
   */
  PAKISTAN("PK", "PAK", 586, "Pakistan", Continent.ASIA),
  
  /**
   * Palau.
   */
  PALAU("PW", "PLW", 585, "Palau", Continent.OCEANIA),
  
  /**
   * Palestine, State Of.
   */
  PALESTINIAN_TERRITORY("PS", "PSE", 275, "Palestine, State Of", Continent.ASIA),
  
  /**
   * Panama.
   */
  PANAMA("PA", "PAN", 591, "Panama", Continent.SOUTH_AMERICA),
  
  /**
   * Papua New Guinea.
   */
  PAPUA_NEW_GUINEA("PG", "PNG", 598, "Papua New Guinea", Continent.OCEANIA),
  
  /**
   * Paraguay.
   */
  PARAGUAY("PY", "PRY", 600, "Paraguay", Continent.SOUTH_AMERICA),
  
  /**
   * Peru.
   */
  PERU("PE", "PER", 604, "Peru", Continent.SOUTH_AMERICA),
  
  /**
   * Philippines.
   */
  PHILIPPINES("PH", "PHL", 608, "Philippines", Continent.ASIA),
  
  /**
   * Pitcairn.
   */
  PITCAIRN("PN", "PCN", 612, "Pitcairn", Continent.OCEANIA),
  
  /**
   * Poland.
   */
  POLAND("PL", "POL", 616, "Poland", Continent.EUROPE),
  
  /**
   * Portugal.
   */
  PORTUGAL("PT", "PRT", 620, "Portugal", Continent.EUROPE),
  
  /**
   * Puerto Rico.
   */
  PUERTO_RICO("PR", "PRI", 630, "Puerto Rico", Continent.SOUTH_AMERICA),
  
  /**
   * Qatar.
   */
  QATAR("QA", "QAT", 634, "Qatar", Continent.ASIA),
  
  /**
   * Réunion.
   */
  RÉUNION("RE", "REU", 638, "Réunion", Continent.AFRICA),
  
  /**
   * Romania.
   */
  ROMANIA("RO", "ROU", 642, "Romania", Continent.EUROPE),
  
  /**
   * Russian Federation.
   */
  RUSSIAN_FEDERATION("RU", "RUS", 643, "Russian Federation", Continent.EUROPE),
  
  /**
   * Rwanda.
   */
  RWANDA("RW", "RWA", 646, "Rwanda", Continent.AFRICA),
  
  /**
   * Saint Barthélemy.
   */
  SAINT_BARTHÉLEMY("BL", "BLM", 652, "Saint Barthélemy", Continent.SOUTH_AMERICA),
  
  /**
   * Saint Helena, Ascension and Tristan da Cunha.
   */
  SAINT_HELENA_ASCENSION_TRISTAN_DA_CUNHA("SH", "SHN", 654, "Saint Helena, Ascension and Tristan da Cunha", Continent.AFRICA),
  
  /**
   * Saint Kitts and Nevis.
   */
  SAINT_KITTS_NEVIS("KN", "KNA", 659, "Saint Kitts and Nevis", Continent.SOUTH_AMERICA),
  
  /**
   * Saint Lucia.
   */
  SAINT_LUCIA("LC", "LCA", 662, "Saint Lucia", Continent.SOUTH_AMERICA),
  
  /**
   * SAINT MARTIN (French part).
   */
  SAINT_MARTIN_FRENCH("MF", "MAF", 663, "Saint Martin (French part)", Continent.SOUTH_AMERICA),
  
  /**
   * Saint Pierre and Miquelon.
   */
  SAINT_PIERRE_MIQUELON("PM", "SPM", 666, "Saint Pierre and Miquelon", Continent.NORTH_AMERICA),
  
  /**
   * Saint Vincent and the Grenadines.
   */
  SAINT_VINCENT_GRENADINES("VC", "VCT", 670, "Saint Vincent and the Grenadines", Continent.SOUTH_AMERICA),
  
  /**
   * Samoa.
   */
  SAMOA("WS", "WSM", 882, "Samoa", Continent.OCEANIA),
  
  /**
   * San Marino.
   */
  SAN_MARINO("SM", "SMR", 674, "San Marino", Continent.EUROPE),
  
  /**
   * Sao Tome and Principe.
   */
  SAO_TOME_PRINCIPE("ST", "STP", 678, "Sao Tome and Principe", Continent.AFRICA),
  
  /**
   * Saudi Arabia.
   */
  SAUDI_ARABIA("SA", "SAU", 682, "Saudi Arabia", Continent.ASIA),
  
  /**
   * Senegal.
   */
  SENEGAL("SN", "SEN", 686, "Senegal", Continent.AFRICA),
  
  /**
   * Serbia.
   */
  SERBIA("RS", "SRB", 688, "Serbia", Continent.EUROPE),
  
  /**
   * Seychelles.
   */
  SEYCHELLES("SC", "SYC", 690, "Seychelles", Continent.AFRICA),
  
  /**
   * Sierra Leone.
   */
  SIERRA_LEONE("SL", "SLE", 694, "Sierra Leone", Continent.AFRICA),
  
  /**
   * Singapore.
   */
  SINGAPORE("SG", "SGP", 702, "Singapore", Continent.ASIA),
  
  /**
   * SINT MAARTEN (Dutch part).
   */
  SINT_MAARTEN("SX", "SXM", 534, "Sint Maarten (Dutch part)", Continent.SOUTH_AMERICA),
  
  /**
   * Slovakia.
   */
  SLOVAKIA("SK", "SVK", 703, "Slovakia", Continent.EUROPE),
  
  /**
   * Slovenia.
   */
  SLOVENIA("SI", "SVN", 705, "Slovenia", Continent.EUROPE),
  
  /**
   * Solomon Islands.
   */
  SOLOMON_ISLANDS("SB", "SLB", 90, "Solomon Islands", Continent.OCEANIA),
  
  /**
   * Somalia.
   */
  SOMALIA("SO", "SOM", 706, "Somalia", Continent.AFRICA),
  
  /**
   * South Africa.
   */
  SOUTH_AFRICA("ZA", "ZAF", 710, "South Africa", Continent.AFRICA),
  
  /**
   * South Georgia and the South Sandwich Islands.
   */
  SOUTH_GEORGIA_SANDWICH_ISLANDS("GS", "SGS", 239, "South Georgia and the South Sandwich Islands", Continent.ANTARCTICA),
  
  /**
   * South Sudan.
   */
  SOUTH_SUDAN("SS", "SSD", 728, "South Sudan", Continent.AFRICA),
  
  /**
   * Spain.
   */
  SPAIN("ES", "ESP", 724, "Spain", Continent.EUROPE),
  
  /**
   * Sri Lanka.
   */
  SRI_LANKA("LK", "LKA", 144, "Sri Lanka", Continent.ASIA),
  
  /**
   * Sudan.
   */
  SUDAN("SD", "SDN", 729, "Sudan", Continent.AFRICA),
  
  /**
   * Suriname.
   */
  SURINAME("SR", "SUR", 740, "Suriname", Continent.SOUTH_AMERICA),
  
  /**
   * Svalbard and Jan Mayen.
   */
  SVALBARD_JAN_MAYEN("SJ", "SJM", 744, "Svalbard and Jan Mayen", Continent.EUROPE),
  
  /**
   * Swaziland.
   */
  SWAZILAND("SZ", "SWZ", 748, "Eswatini", Continent.AFRICA),
  
  /**
   * Sweden.
   */
  SWEDEN("SE", "SWE", 752, "Sweden", Continent.EUROPE),
  
  /**
   * Switzerland.
   */
  SWITZERLAND("CH", "CHE", 756, "Switzerland", Continent.EUROPE),
  
  /**
   * Syrian Arab Republic.
   */
  SYRIA("SY", "SYR", 760, "Syrian Arab Republic", Continent.ASIA),
  
  /**
   * Taiwan.
   */
  TAIWAN("TW", "TWN", 158, "Taiwan", Continent.ASIA),
  
  /**
   * Tajikistan.
   */
  TAJIKISTAN("TJ", "TJK", 762, "Tajikistan", Continent.EUROPE),
  
  /**
   * Tanzania, United Republic of.
   */
  TANZANIA("TZ", "TZA", 834, "Tanzania, United Republic of", Continent.AFRICA),
  
  /**
   * Thailand.
   */
  THAILAND("TH", "THA", 764, "Thailand", Continent.ASIA),
  
  /**
   * Timor-Leste.
   */
  TIMOR_LESTE("TL", "TLS", 626, "Timor-Leste", Continent.ASIA),
  
  /**
   * Togo.
   */
  TOGO("TG", "TGO", 768, "Togo", Continent.AFRICA),
  
  /**
   * Tokelau.
   */
  TOKELAU("TK", "TKL", 772, "Tokelau", Continent.OCEANIA),
  
  /**
   * Tonga.
   */
  TONGA("TO", "TON", 776, "Tonga", Continent.OCEANIA),
  
  /**
   * Trinidad and Tobago.
   */
  TRINIDAD_TOBAGO("TT", "TTO", 780, "Trinidad and Tobago", Continent.SOUTH_AMERICA),
  
  /**
   * Tunisia.
   */
  TUNISIA("TN", "TUN", 788, "Tunisia", Continent.AFRICA),
  
  /**
   * Turkey.
   */
  TURKEY("TR", "TUR", 792, "Türkiye", Continent.EUROPE),
  
  /**
   * Turkmenistan.
   */
  TURKMENISTAN("TM", "TKM", 795, "Turkmenistan", Continent.EUROPE),
  
  /**
   * Turks and Caicos Islands.
   */
  TURKS_CAICOS_ISLANDS("TC", "TCA", 796, "Turks and Caicos Islands", Continent.SOUTH_AMERICA),
  
  /**
   * Tuvalu.
   */
  TUVALU("TV", "TUV", 798, "Tuvalu", Continent.OCEANIA),
  
  /**
   * Uganda.
   */
  UGANDA("UG", "UGA", 800, "Uganda", Continent.AFRICA),
  
  /**
   * Ukraine.
   */
  UKRAINE("UA", "UKR", 804, "Ukraine", Continent.EUROPE),
  
  /**
   * United Arab Emirates.
   */
  UNITED_ARAB_EMIRATES("AE", "ARE", 784, "United Arab Emirates", Continent.ASIA),
  
  /**
   * United Kingdom.
   */
  UNITED_KINGDOM("GB", "GBR", 826, "United Kingdom", Continent.EUROPE),
  
  /**
   * United States.
   */
  UNITED_STATES("US", "USA", 840, "United States of America", Continent.NORTH_AMERICA),
  
  /**
   * United States Minor Outlying Islands.
   */
  UNITED_STATES_OUTLYING_ISLANDS("UM", "UMI", 581, "United States Minor Outlying Islands", Continent.OCEANIA),
  
  /**
   * Uruguay.
   */
  URUGUAY("UY", "URY", 858, "Uruguay", Continent.SOUTH_AMERICA),
  
  /**
   * Uzbekistan.
   */
  UZBEKISTAN("UZ", "UZB", 860, "Uzbekistan", Continent.EUROPE),
  
  /**
   * Vanuatu.
   */
  VANUATU("VU", "VUT", 548, "Vanuatu", Continent.OCEANIA),
  
  /**
   * Venezuela, Bolivarian Republic of.
   */
  VENEZUELA("VE", "VEN", 862, "Venezuela, Bolivarian Republic of", Continent.SOUTH_AMERICA),
  
  /**
   * Viet Nam.
   */
  VIETNAM("VN", "VNM", 704, "Viet Nam", Continent.ASIA),
  
  /**
   * Virgin Islands, British.
   */
  VIRGIN_ISLANDS_BRITISH("VG", "VGB", 92, "Virgin Islands, British", Continent.SOUTH_AMERICA),
  
  /**
   * Virgin Islands, U.S..
   */
  VIRGIN_ISLANDS("VI", "VIR", 850, "Virgin Islands, U.S.", Continent.SOUTH_AMERICA),
  
  /**
   * Wallis and Futuna.
   */
  WALLIS_FUTUNA("WF", "WLF", 876, "Wallis and Futuna", Continent.OCEANIA),
  
  /**
   * Western Sahara.
   */
  WESTERN_SAHARA("EH", "ESH", 732, "Western Sahara", Continent.AFRICA),
  
  /**
   * Yemen.
   */
  YEMEN("YE", "YEM", 887, "Yemen", Continent.ASIA),
  
  /**
   * Zambia.
   */
  ZAMBIA("ZM", "ZMB", 894, "Zambia", Continent.AFRICA),
  
  /**
   * Zimbabwe.
   */
  ZIMBABWE("ZW", "ZWE", 716, "Zimbabwe", Continent.AFRICA),
  
  /**
   * @see <a href="http://en.wikipedia.org/wiki/UN/LOCODE">UN/LOCODE</a>
   */
  INTERNATIONAL_WATERS("XZ", "XZZ", 901, "international waters");
  
  /**
   * A set of all 2 and 3 letter codes that are reserved by ISO for custom application specific usages.
   * The following codes can be user-assigned:
   * Alpha-2: AA, QM to QZ, XA to XZ, and ZZ
   * Alpha-3: AAA to AAZ, QMA to QZZ, XAA to XZZ, and ZZA to ZZZ
   */
  public static final Set<String> CUSTOM_CODES;
  public static final List<Country> OFFICIAL_COUNTRIES;

  private final String alpha2;
  private final String alpha3;
  private final int numericalCode;
  private final String name;
  private final Continent continent;
  
  static {
    List<Country> officials = Lists.newArrayList();
    for (Country c : Country.values()) {
      if (c.isOfficial()) {
        officials.add(c);
      }
    }
    OFFICIAL_COUNTRIES = ImmutableList.copyOf(officials);
    
    Set<String> custom = Sets.newHashSet("AA", "ZZ");
    // QM-QZ
    for (char c = 'M'; c <= 'Z'; c++) {
      custom.add("Q" + c);
    }
    // AAA-AAZ, ZZA-ZZZ
    for (char c = 'A'; c <= 'Z'; c++) {
      custom.add("AA" + c);
      custom.add("ZZ" + c);
    }
    // QMA-QZZ
    for (char c = 'M'; c <= 'Z'; c++) {
      for (char c2 = 'A'; c2 <= 'Z'; c2++) {
        custom.add("Q" + c + c2);
      }
    }
    // XA-XZ, XAA to XZZ
    for (char c = 'A'; c <= 'Z'; c++) {
      custom.add("X" + c);
      for (char c2 = 'A'; c2 <= 'Z'; c2++) {
        custom.add("X" + c + c2);
      }
    }
    CUSTOM_CODES = ImmutableSet.copyOf(custom);
  }
  
  public static boolean isCustomCode(String code) {
    return code != null && CUSTOM_CODES.contains(code.toUpperCase());
  }
  
  /**
   * @param code the case insensitive 2 or 3 letter codes
   * @return the matching country or null
   */
  public static Optional<Country> fromIsoCode(String code) {
    if (!Strings.isNullOrEmpty(code)) {
      String codeUpper = code.toUpperCase().trim();
      for (Country c : Country.values()) {
        if (codeUpper.equals(c.getIso2LetterCode()) || codeUpper.equals(c.getIso3LetterCode())) {
          return Optional.of(c);
        }
      }
    }
    return Optional.empty();
  }
  
  /**
   * Temporary constructor to keep the code stable until https://github.com/gbif/gbif-api/issues/6 is fully
   * implemented.
   *
   * @param alpha2
   * @param alpha3
   * @param numericalCode
   * @param name
   */
  Country(String alpha2, String alpha3, int numericalCode, String name) {
    this(alpha2, alpha3, numericalCode, name, null);
  }
  
  /**
   * @param alpha2
   * @param alpha3
   * @param numericalCode
   * @param name
   * @param continent
   */
  Country(String alpha2, String alpha3, int numericalCode, String name, Continent continent) {
    this.alpha2 = alpha2;
    this.alpha3 = alpha3;
    this.numericalCode = numericalCode;
    this.name = name;
    this.continent = continent;
  }

  @Override
  public Gazetteer getGazetteer() {
    return Gazetteer.ISO;
  }

  @Override
  @JsonIgnore
  public String getId() {
    return alpha2;
  }

  /**
   * @return the country name in the English language as maintained by ISO.
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * @return the 2 letter ISO 3166-1 ALPHA2 code in upper case.
   */
  @JsonIgnore
  public String getIso2LetterCode() {
    return alpha2;
  }
  
  /**
   * @return the 3 letter ISO 3166-1 ALPHA3 code in upper case.
   */
  @JsonIgnore
  public String getIso3LetterCode() {
    return alpha3;
  }
  
  /**
   * @return the numerical ISO 3166-1 code.
   */
  @JsonIgnore
  public Integer getIsoNumericalCode() {
    return numericalCode;
  }
  
  /**
   * Get the {@link Continent} associated with this {@link Country}.
   *
   * @return
   */
  public Continent getContinent() {
    return continent;
  }
  
  /**
   * @return true if its a non user defined, current ISO 3166-1 alpha2 code.
   */
  public boolean isOfficial() {
    return !(this == INTERNATIONAL_WATERS);
  }
  
}
