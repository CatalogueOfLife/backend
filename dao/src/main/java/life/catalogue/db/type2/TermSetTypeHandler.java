/*
 * Copyright 2013 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyTaxon of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package life.catalogue.db.type2;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stores term sets as text arrays in postgres, avoiding nulls and uses empty sets instead.
 */
public class TermSetTypeHandler extends AbstractArrayTypeHandler<Set<Term>> {

  public TermSetTypeHandler() {
    super("text", Collections.emptySet());
  }
  
  @Override
  public Object[] toArray(Set<Term> obj) throws SQLException {
    return obj.stream()
      .map(Term::prefixedName)
      .toArray();
  }

  @Override
  public Set<Term> toObj(Array pgArray) throws SQLException {
    if (pgArray == null) return new HashSet<>();

    return Arrays.stream((String[]) pgArray.getArray())
      .map(TermFactory.instance()::findTerm)
      .collect(Collectors.toSet());
  }
}
