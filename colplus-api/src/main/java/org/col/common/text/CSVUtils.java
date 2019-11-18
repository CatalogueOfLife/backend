package org.col.common.text;

import java.util.ArrayList;
import java.util.List;

public class CSVUtils {
  
  private static final char DEFAULT_SEPARATOR = ',';
  private static final char DEFAULT_QUOTE = '"';
  
  public static List<String> parseLine(String line) {
    return parseLine(line, DEFAULT_SEPARATOR, DEFAULT_QUOTE);
  }
  
  public static List<String> parseLine(String line, char delimiter) {
    return parseLine(line, delimiter, DEFAULT_QUOTE);
  }
  
  public static List<String> parseLine(String line, char delimiter, char quote) {
    
    List<String> result = new ArrayList<>();
    
    //if empty, return!
    if (line == null || line.isEmpty()) {
      return result;
    }
    
    StringBuffer curVal = new StringBuffer();
    boolean inQuotes = false;
    boolean startCollectChar = false;
    
    char[] chars = line.toCharArray();
    
    for (char ch : chars) {
      
      if (inQuotes) {
        startCollectChar = true;
        if (ch == quote) {
          inQuotes = false;
        } else {
          curVal.append(ch);
        }
      } else {
        if (ch == quote) {
          
          inQuotes = true;
          
          //double quotes in column will hit this!
          if (startCollectChar) {
            curVal.append(quote);
          }
          
        } else if (ch == delimiter) {
  
          add(result, curVal);
          
          curVal = new StringBuffer();
          startCollectChar = false;
          
        } else if (ch == '\r') {
          //ignore LF characters
          continue;
        } else if (ch == '\n') {
          //the end, break!
          break;
        } else {
          curVal.append(ch);
        }
      }
      
    }
    
    add(result, curVal);
    
    return result;
  }
  
  private static void add(List<String> row, StringBuffer col){
    row.add(col.length()==0 ? null : col.toString());
  }
}
