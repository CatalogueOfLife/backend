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

import com.google.common.base.Strings;
import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.gbif.api.jackson.ExtensionDeserializer;
import org.gbif.api.jackson.ExtensionSerializer;

/**
 * Enumeration of dwc extensions for Taxon core archives that are supported by CoL.
 *
 * @see <a href="http://rs.gbif.org/extension">GBIF Resources</a>
 */
@JsonSerialize(using = ExtensionSerializer.class)
@JsonDeserialize(using = ExtensionDeserializer.class)
public enum Extension {

  /**
   * @see <a href="http://rs.gbif.org/extension/gbif/1.0/distribution.xml">extension definition</a>
   */
  DISTRIBUTION("http://rs.gbif.org/terms/1.0/Distribution"),

  /**
   * @see <a href="http://eol.org/schema/reference_extension.xml">extension definition</a>
   */
  EOL_REFERENCE("http://eol.org/schema/reference/Reference"),

  /**
   * @see <a href="http://rs.gbif.org/extension/dwc/measurements_or_facts.xml">extension definition</a>
   */
  REFERENCE("http://rs.gbif.org/terms/1.0/Reference"),

  /**
   * @see <a href="http://rs.gbif.org/extension/dwc/resource_relation.xml">extension definition</a>
   */
  RESOURCE_RELATIONSHIP("http://rs.tdwg.org/dwc/terms/ResourceRelationship"),

  /**
   * @see <a href="http://rs.gbif.org/extension/gbif/1.0/speciesprofile.xml">extension definition</a>
   */
  SPECIES_PROFILE("http://rs.gbif.org/terms/1.0/SpeciesProfile"),

  /**
   * @see <a href="http://rs.gbif.org/extension/gbif/1.0/typesandspecimen.xml">extension definition</a>
   */
  TYPES_AND_SPECIMEN("http://rs.gbif.org/terms/1.0/TypesAndSpecimen"),

  /**
   * @see <a href="http://rs.gbif.org/extension/gbif/1.0/vernacularname.xml">extension definition</a>
   */
  VERNACULAR_NAME("http://rs.gbif.org/terms/1.0/VernacularName");


  private final String rowType;

  /**
   * @param rowType the case insensitive row type uri for the extension
   * @return the matching extension or null
   */
  public static Extension fromRowType(String rowType) {
    if (!Strings.isNullOrEmpty(rowType)) {
      for (Extension extension : Extension.values()) {
        if (rowType.equalsIgnoreCase(extension.getRowType())
            || rowType.equalsIgnoreCase(extension.name().replaceAll("_", ""))) {
          return extension;
        }
      }
    }
    return null;
  }

  Extension(String rowType) {
    this.rowType = rowType;
  }

  public String getRowType() {
    return rowType;
  }

}
