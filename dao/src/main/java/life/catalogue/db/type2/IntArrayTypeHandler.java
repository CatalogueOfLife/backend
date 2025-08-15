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

import org.apache.ibatis.type.ArrayTypeHandler;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * String array handler that avoids nulls and uses empty arrays instead.
 */
public class IntArrayTypeHandler extends ArrayTypeHandler {

  @Override
  protected Object extractArray(Array array) throws SQLException {
    if (array == null) {
      return null;
    }
    Integer[] src = (Integer[]) super.extractArray(array);
    int[] dest = new int[src.length];
    // Assuming there is no null in the data
    Arrays.setAll(dest, i -> src[i]);
    return dest;
  }
}
