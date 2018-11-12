package org.col.api;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class BeanPrinter {
  
  private static final Package PKG_JAVA_LANG = Package.getPackage("java.lang");
  // Separates field name from field value
  private static final String FLD_SEP = ": ";
  // Separates map key from values in map value
  private static final String KEY_SEP = " => ";
  private static final String INDENT = "\t";
  
  /**
   * Dumps the specified object to std out.
   *
   * @param bean The object to be printed
   */
  public static void out(Object bean) {
    new BeanPrinter().dump(bean);
  }
  
  /**
   * Dumps the specified object to a string.
   *
   * @param bean The object to be printed
   * @return The dump
   */
  public static String toString(Object bean) {
    StringWriter sw = new StringWriter(1024);
    BeanPrinter bp = new BeanPrinter(new PrintWriter(sw));
    bp.dump(bean);
    return sw.toString();
  }
  
  private final PrintWriter pw;
  
  // Caches objects before we print them in order to detect cyclical
  // references.
  @SuppressWarnings("rawtypes")
  private HashMap cache;
  
  private boolean showClassNames = true;
  private boolean showSimpleMapKeys = true;
  private boolean showSuper = true;
  
  private boolean showSimpleClassNames = false;
  private boolean showRawMaps = false;
  private boolean showObjectIds = false;
  private boolean showStringLength = false;
  private boolean showConstants = false;
  private boolean printGetters = false;
  
  private int maxRecursion = 20;
  
  private HashSet<Package> opaquePackages = new HashSet<>();
  private HashSet<Class<?>> opaqueClasses = new HashSet<>();
  
  /**
   * Creates a {@code BeanPrinter} that writes to standard out. See
   * {@link #BeanPrinter(PrintWriter)}.
   */
  public BeanPrinter() {
    this(new PrintWriter(System.out));
  }
  
  /**
   * Creates a {@code BeanPrinter} that writes to the specified file.
   *
   * @param path The full path of the file
   * @throws FileNotFoundException
   */
  public BeanPrinter(String path) {
    try {
      this.pw = new PrintWriter(path);
      printOpaque("java.lang");
      printOpaque("java.lang.reflect");
      printOpaque("sun.misc");
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Creates a new {@code BeanPrinter} that writes to the specified
   * {@code PrintWriter}.
   *
   * @param printWriter The {@code PrintWriter} to write to.
   */
  public BeanPrinter(PrintWriter printWriter) {
    this.pw = printWriter;
    printOpaque("java.lang");
    printOpaque("java.lang.reflect");
    printOpaque("sun.misc");
  }
  
  /**
   * Recursively writes the specified object's state to the chosen
   * {@code PrintWriter}.
   *
   * @param bean The object whose state to print
   */
  @SuppressWarnings("rawtypes")
  public void dump(Object bean) {
    try {
      cache = new HashMap(64);
      print(bean, 0);
      pw.println();
    } catch (Throwable t) {
      pw.println();
      pw.println();
      t.printStackTrace(pw);
      pw.println();
      pw.println("Error dumping bean: " + t);
    } finally {
      pw.flush();
    }
  }
  
  /**
   * Whether to print the fields' class names.
   *
   * @return
   */
  public boolean isShowClassNames() {
    return showClassNames;
  }
  
  /**
   * Whether to print the fields' class names. Default {@code true}. If
   * {@code false}, only the values of the fields are printed, which may result in
   * a more easily readable representation of the bean.
   *
   * @param showClassNames Whether to display the class of the bean's fields
   * @return This {@code BeanPrinter} instance
   */
  public void setShowClassNames(boolean showClassNames) {
    this.showClassNames = showClassNames;
  }
  
  /**
   * Whether to print simple class names or fully qualified class names.
   *
   * @return
   */
  public boolean isShowSimpleClassNames() {
    return showSimpleClassNames;
  }
  
  /**
   * Whether to print simple class names or fully qualified class names. By
   * default the fully qualified class name is displayed, except for classes in
   * the java.lang package. These are <i>always</i> printed using their simple
   * names, irrespective of this setting.
   *
   * @param showSimpleClassNames
   */
  public void setShowSimpleClassNames(boolean showSimpleClassNames) {
    this.showSimpleClassNames = showSimpleClassNames;
  }
  
  /**
   * Whether to print superclass fields.
   *
   * @return
   */
  public boolean isShowSuper() {
    return showSuper;
  }
  
  /**
   * Whether to print superclass fields. Default {@code true}.
   *
   * @param showSuper
   */
  public void setShowSuper(boolean showSuper) {
    this.showSuper = showSuper;
  }
  
  /**
   * Whether to print {@link java.util.Map} instances like any other object or to
   * print them in a special, more intuitive way.
   *
   * @return
   */
  public boolean isShowRawMaps() {
    return showRawMaps;
  }
  
  /**
   * Whether to print {@link java.util.Map} instances like any other object or to
   * print them in a special, more intuitive way. By default maps are printed in a
   * non-standard, more inuitive way.
   *
   * @param showRawMaps
   */
  public void setShowRawMaps(boolean showRawMaps) {
    this.showRawMaps = showRawMaps;
  }
  
  /**
   * Whether to print {@code Map} keys in a simplified manner.
   *
   * @return
   */
  public boolean isShowSimpleMapKeys() {
    return showSimpleMapKeys;
  }
  
  /**
   * Whether to print {@code Map} keys in a simplified manner. Default
   * {@code true}. If {@code true}, alphanumerical keys will be represented simply
   * by their value (rather than as full-blown objects). For other types of keys,
   * if the key's class overrides {@code toString()}, the result of calling
   * {@code toString()} is used to display the key. Otherwise the concatenation of
   * the key's class name and object ID is used to display the key. If
   * {@code false}, map keys are printed like any other object, which may cause
   * the map as a whole to become unintelligible.
   *
   * @param showSimpleMapKeys
   */
  public void setShowSimpleMapKeys(boolean showSimpleMapKeys) {
    this.showSimpleMapKeys = showSimpleMapKeys;
  }
  
  /**
   * Whether to print object IDs.
   *
   * @return
   */
  public boolean isShowObjectIds() {
    return showObjectIds;
  }
  
  /**
   * Whether to print an object's identity hash code (retrieved through
   * {@link System#identityHashCode(Object)}). Default {@code false}. Helpful when
   * trying to spot cyclical references and/or duplicate objects.
   *
   * @param showObjectIds
   */
  public void setShowObjectIds(boolean showObjectIds) {
    this.showObjectIds = showObjectIds;
  }
  
  /**
   * Whether to print the length of {@code String}s and other {@code CharSequence}
   * objects.
   *
   * @return
   */
  public boolean isShowStringLength() {
    return showStringLength;
  }
  
  /**
   * Whether to print the length of {@code String}s and other
   * {@code CharSequence}s. Default {@code false}.
   *
   * @param showStringLength
   */
  public void setShowStringLength(boolean showStringLength) {
    this.showStringLength = showStringLength;
  }
  
  /**
   * Whether to show static final fields.
   *
   * @return
   */
  public boolean isShowConstants() {
    return showConstants;
  }
  
  /**
   * Whether to show static final fields. Default {@code false}.
   *
   * @param showConstants
   */
  public void setShowConstants(boolean showConstants) {
    this.showConstants = showConstants;
  }
  
  /**
   * Whether to print the objects returned by the getter methods.
   *
   * @return
   */
  public boolean isPrintGetters() {
    return printGetters;
  }
  
  /**
   * Whether to print the objects returned by the getter methods. Default
   * {@code false}. Any method with zero parameters and a non-void return type is
   * considered a getter.
   *
   * @param printGetters
   */
  public void setPrintGetters(boolean printGetters) {
    this.printGetters = printGetters;
  }
  
  /**
   * Sets the maximum number of levels the {@code BeanPrinter} will descend into
   * the bean it is printing.
   *
   * @return
   */
  public int getMaxRecursion() {
    return maxRecursion;
  }
  
  /**
   * Sets the maximum number of levels the {@code BeanPrinter} will descend into
   * the bean it is printing. Default 20. Although the bean printer will usually
   * detect cyclical references, some objects (e.g. from the sun.misc package and
   * bytecode-enhanced JPA objects) respond badly to inspection via the
   * java.lang.reflect package, which is what the {@code BeanPrinter} is all
   * about. Therefore a hard cap is also necessary in order to avoid infinite
   * recursion.
   *
   * @param maxRecursion
   */
  public void setMaxRecursion(int maxRecursion) {
    this.maxRecursion = maxRecursion;
  }
  
  /**
   * Get the set of opaque packages.
   *
   * @return
   */
  public HashSet<Package> getIgnoredPackages() {
    return opaquePackages;
  }
  
  /**
   * Determines which packages should be treated as opaque. If a field's type
   * belongs to any of those packages, the field itself will printed, but no
   * recursion into its internals will take place. By default, the following
   * packages are ignored:
   * <ul>
   * <li>java.lang</li>
   * <li>java.lang.reflect</li>
   * <li>sun.misc</li>
   * </ul>
   *
   * @param opaquePackages
   */
  public void setIgnoredPackages(HashSet<Package> opaquePackages) {
    this.opaquePackages = opaquePackages;
  }
  
  /**
   * Add a package to the set of opaque packages.
   *
   * @param packageName The name of the package
   */
  public void printOpaque(String packageName) {
    Package p = Package.getPackage(packageName);
    if (p == null)
      throw new RuntimeException("No such package: " + packageName);
    opaquePackages.add(p);
  }
  
  /**
   * Get the set of opaque classes.
   *
   * @return
   */
  public HashSet<Class<?>> getIgnoredClasses() {
    return opaqueClasses;
  }
  
  /**
   * Determines which classes should be treated as opaque. If a field's type is an
   * opaque classes, or any of its subclasses, the field itself will be printed,
   * but no recursion into its internals will take place.
   *
   * @param opaqueClasses
   */
  public void setIgnoredClasses(HashSet<Class<?>> opaqueClasses) {
    this.opaqueClasses = opaqueClasses;
  }
  
  public void ignore(Class<?>... opaqueClasses) {
    this.opaqueClasses.addAll(Arrays.asList(opaqueClasses));
  }
  
  /**
   * Add a class to the set of ignored classes.
   *
   * @param c
   */
  public void printOpaque(Class<?> c) {
    if (opaqueClasses == null) {
      opaqueClasses = new HashSet<>();
    }
    opaqueClasses.add(c);
  }
  
  /**
   * Get the {@code PrintWriter} through which the bean is printed.
   *
   * @return
   */
  public PrintWriter getPrintWriter() {
    return pw;
  }
  
  // ///////////////////////////
  // END OF PUBLIC INTERFACE
  // ///////////////////////////
  
  private void print(Object obj, int level) throws Exception {
    if (obj == null)
      printNull();
    else
      print(obj, obj.getClass(), level);
  }
  
  /*
   * To print an object we specify both the object itself and the type to print
   * (2nd parameter), because sometimes we need to print the object's declared
   * type rather than its runtime type (obj.getClass()).
   */
  @SuppressWarnings("rawtypes")
  private void print(Object obj, Class<?> type, int level) throws Exception {
    if (level == maxRecursion) {
      pw.println();
      pw.println("/* Maximum recursion depth reached */");
      pw.println();
      return;
    }
    if (obj == null) {
      printNull(type);
      return;
    }
    if (type.isPrimitive())
      printOpaque(obj, type);
    else if (isOpaque(obj.getClass()))
      printOpaque(obj, obj.getClass());
    else if (obj.getClass().isArray())
      printArray(obj, level);
    else if (isA(obj.getClass(), Iterable.class))
      printIterator(((Iterable) obj).iterator(), level);
    else if (isA(obj.getClass(), Iterator.class))
      printIterator(obj, level);
    else if (!showRawMaps && isA(obj.getClass(), Map.class))
      printMap(obj, level);
    else if (isA(obj.getClass(), Enum.class))
      printEnum(obj, level);
    else if (isA(obj.getClass(), Enumeration.class))
      printEnumeration(obj, level);
    else {
      printClassAndId(obj, type);
      if (cached(obj, type)) {
        pw.print("{ /* Cyclical reference */ }");
      } else {
        pw.print('{');
        if (showSuper && type.getSuperclass() != null && type.getSuperclass() != Object.class) {
          printSuper(obj, type, level + 1);
        }
        printFields(obj, type, level + 1);
        if (printGetters) {
          printGetters(obj, type, level + 1);
        }
        pw.println();
        indent(level, '}');
      }
    }
  }
  
  /*
   * Caches the object we are about to print. An object may be printed out
   * multiple times, but only once per runtime type. For example, a {@code
   * javax.swing.JButton} object will be printed out once for the {@code
   * javax.swing.JButton} class, once for the {@code javax.swing.AbstractButton}
   * class, once for the {@code javax.swing.JComponent} class, once for the {@code
   * java.awt.Container} class and once for {@code java.awt.Component}. However,
   * if the exact same combination of object and class already exists in the
   * cache, it means we are dealing with a cyclical reference, and the object will
   * not be printed again.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private boolean cached(Object obj, Class<?> type) {
    // Special case for arrays
    if (type == null) {
      if (cache.containsKey(obj))
        return true;
      cache.put(obj, null);
      return false;
    }
    // Never cache primitives and their immutable wrappers
    if (type.isPrimitive() || isWrapper(type)) {
      return false;
    }
    Object key;
    /*
     * Hack for edge cases where hashCode()/equals() throws an Exception or where
     * equals() does not do type checking (resulting in a ClassCastException) or
     * null checking (resulting in a NullPointerException).
     */
    try {
      obj.hashCode();
      obj.equals(this); // Compare with arbitrary object
      obj.equals(null);
      key = obj;
    } catch (Throwable t) {
      key = Integer.valueOf(System.identityHashCode(obj));
    }
    ArrayList types = (ArrayList) cache.get(key);
    if (types == null) {
      types = new ArrayList(3);
      types.add(type);
      cache.put(key, types);
      return false;
    }
    if (types.contains(type))
      return true;
    types.add(type);
    return false;
  }
  
  // Prints the fields from the super class as though belonging to
  // a separate object assigned to a field named "super".
  private void printSuper(Object obj, Class<?> type, int level) throws Exception {
    pw.println();
    indent(level, "super", FLD_SEP);
    print(obj, type.getSuperclass(), level);
  }
  
  private void printFields(Object obj, Class<?> type, int level) throws Exception {
    Field[] fields = type.getDeclaredFields();
    for (Field f : fields) {
      if (!showConstants) {
        int i = f.getModifiers();
        if (Modifier.isFinal(i) && Modifier.isStatic(i))
          continue;
      }
      pw.println();
      f.setAccessible(true);
      Object val = f.get(obj);
      indent(level, f.getName(), FLD_SEP);
      if (val == null)
        printNull(f.getType());
      else {
        /*
         * If there can be a difference between the object's declared type and its
         * runtime type, choose its runtime type.
         */
        if (f.getType().isPrimitive())
          type = f.getType();
        else
          type = val.getClass();
        print(val, type, level);
      }
    }
  }
  
  private void printGetters(Object obj, Class<?> type, int level) throws Exception {
    Method[] methods = type.getMethods();
    for (Method m : methods) {
      if (m.getParameterTypes().length == 0) {
        if (m.getReturnType() != void.class) {
          pw.println();
          indent(level, m.getName() + "()", FLD_SEP);
          Object val = m.invoke(obj);
          if (val == null || m.getReturnType().isPrimitive())
            type = m.getReturnType();
          else
            type = val.getClass();
          print(val, type, level);
        }
      }
    }
  }
  
  private void printOpaque(Object obj, Class<?> type) {
    if (isA(type, CharSequence.class))
      printCharSequence(obj);
    else if (isNumber(type))
      printNumber(obj, type);
    else if (type == boolean.class || type == Boolean.class)
      printBool(obj, type);
    else if (type == char.class || type == Character.class)
      printChar(obj, type);
    else {
      printClassAndId(obj, type);
      pw.print("{ /* Opaque */ }");
    }
  }
  
  private void printCharSequence(Object o) {
    printClassAndId(o, o.getClass());
    pw.format("\"%s\"", escape(o.toString()));
  }
  
  private void printNumber(Object o, Class<?> type) {
    printClassAndId(o, type);
    pw.format("%s", o);
  }
  
  private void printBool(Object o, Class<?> type) {
    printClassAndId(o, type);
    pw.format("%s", o);
  }
  
  private void printChar(Object o, Class<?> type) {
    printClassAndId(o, type);
    pw.format("'%s'", o);
  }
  
  private void printArray(Object obj, int level) throws Exception, SecurityException {
    printClassAndId(obj, obj.getClass());
    if (cached(obj, null)) {
      pw.print("{ /* Cyclical reference */ }");
      return;
    }
    int len = Array.getLength(obj);
    if (len == 0) {
      printEmpty();
      return;
    }
    pw.println('{');
    for (int i = 0; i < len; ++i) {
      indent(level + 1, String.valueOf(i), FLD_SEP);
      Object o = Array.get(obj, i);
      print(o, obj.getClass().getComponentType(), level + 1);
      pw.println();
    }
    indent(level, '}');
  }
  
  @SuppressWarnings("rawtypes")
  private void printIterator(Object obj, int level) throws Exception {
    printClassAndId(obj, obj.getClass());
    Iterator iterator = (Iterator) obj;
    if (!iterator.hasNext()) {
      printEmpty();
      return;
    }
    pw.println('{');
    iterate(iterator, level + 1);
    indent(level, '}');
  }
  
  @SuppressWarnings("rawtypes")
  private void printMap(Object obj, int level) throws Exception {
    printClassAndId(obj, obj.getClass());
    Iterator iterator = ((Map) obj).entrySet().iterator();
    if (!iterator.hasNext()) {
      printEmpty();
      return;
    }
    pw.println('{');
    while (iterator.hasNext()) {
      Map.Entry entry = (Map.Entry) iterator.next();
      indent(level + 1);
      printMapKey(entry.getKey(), level + 1);
      pw.print(KEY_SEP);
      print(entry.getValue(), level + 1);
      pw.println();
    }
    indent(level, '}');
  }
  
  @SuppressWarnings({"rawtypes", "unused"})
  private void printEnum(Object obj, int level) {
    printClassAndId(obj, obj.getClass());
    pw.print(((Enum) obj).name());
  }
  
  @SuppressWarnings({"rawtypes", "unchecked"})
  private void printEnumeration(Object obj, int level) throws Exception {
    printClassAndId(obj, obj.getClass());
    Enumeration e = (Enumeration) obj;
    if (!e.hasMoreElements()) {
      printEmpty();
      return;
    }
    ArrayList list = new ArrayList();
    while (e.hasMoreElements()) {
      list.add(e.nextElement());
    }
    pw.println('{');
    iterate(list.iterator(), level + 1);
    indent(level, '}');
  }
  
  private void printClassAndId(Object obj, Class<?> type) {
    if (showClassNames) {
      pw.print('(');
      pw.print(getClassName(obj, type));
      if (showObjectIds) {
        pw.print('@');
        pw.print(System.identityHashCode(obj));
      }
      pw.print(") ");
    } else if (showObjectIds) {
      pw.print("(@");
      pw.print(System.identityHashCode(obj));
      pw.print(") ");
    }
  }
  
  @SuppressWarnings("rawtypes")
  private void iterate(Iterator iterator, int level) throws Exception {
    int i = 0;
    while (iterator.hasNext()) {
      indent(level, String.valueOf(i++), FLD_SEP);
      Object o = iterator.next();
      if (o == null)
        printNull();
      else
        print(o, o.getClass(), level);
      pw.println();
    }
  }
  
  private void printMapKey(Object key, int level) throws Exception {
    if (!showSimpleMapKeys)
      print(key, level);
    else if (key == null)
      printNull();
    else if (key instanceof CharSequence)
      pw.append('"').append(escape(key.toString())).append('"');
    else if (key instanceof Number)
      pw.print(key);
    else if (key instanceof Character)
      pw.append('\'').append(key.toString()).append('\'');
    else if (key instanceof Boolean)
      pw.print(key);
    else if (key instanceof Enum)
      pw.print(((Enum<?>) key).name());
    else {
      try {
        key.getClass().getDeclaredMethod("toString", (Class[]) null);
        pw.append('"').append(escape(key.toString())).append('"');
        if (showObjectIds) {
          pw.print('@');
          pw.print(System.identityHashCode(key));
        }
      } catch (NoSuchMethodException e) {
        pw.print(getClassName(key));
        pw.print('@');
        pw.print(System.identityHashCode(key));
      }
    }
  }
  
  private void printNull(Class<?> type) {
    if (showClassNames)
      pw.append('(').append(className(type)).append(") ");
    printNull();
  }
  
  private void printNull() {
    pw.print("null");
  }
  
  private void printEmpty() {
    pw.print("{ /* Empty */ }");
  }
  
  private String getClassName(Object obj) {
    return getClassName(obj, obj.getClass());
  }
  
  @SuppressWarnings("rawtypes")
  private String getClassName(Object obj, Class<?> cls) {
    if (cls.isPrimitive()) {
      return cls.getSimpleName();
    }
    if (obj instanceof CharSequence) {
      int size = ((CharSequence) obj).length();
      StringBuilder sb = new StringBuilder(className(cls));
      if (showStringLength) {
        sb.append('(').append(size).append(')');
      }
      return sb.toString();
    }
    if (cls.isArray()) {
      int size = Array.getLength(obj);
      StringBuilder sb = new StringBuilder(className(cls.getComponentType()));
      sb.append('[').append(size).append(']');
      return sb.toString();
    }
    if (obj instanceof Collection) {
      int size = ((Collection) obj).size();
      StringBuilder sb = new StringBuilder(className(cls));
      sb.append('[').append(size).append(']');
      return sb.toString();
    }
    if (obj instanceof Map) {
      int size = ((Map) obj).size();
      StringBuilder sb = new StringBuilder(className(cls));
      sb.append('[').append(size).append(']');
      return sb.toString();
    }
    return className(cls);
  }
  
  private String className(Class<?> cls) {
    if (showSimpleClassNames || cls.getPackage() == PKG_JAVA_LANG)
      return cls.getSimpleName();
    return cls.getName();
  }
  
  private boolean isOpaque(Class<?> type) {
    if (opaquePackages.contains(type.getPackage()))
      return true;
    for (Class<?> cls : opaqueClasses)
      if (isA(type, cls))
        return true;
    return false;
  }
  
  private void indent(int level, char c) {
    indent(level);
    pw.print(c);
  }
  
  private void indent(int level, String... strings) {
    indent(level);
    for (String s : strings)
      pw.print(s);
  }
  
  private void indent(int level) {
    for (int i = 0; i < level; ++i)
      pw.print(INDENT);
  }
  
  private static String escape(String str) {
    StringBuilder sb = new StringBuilder(str.length() + 8);
    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      switch (c) {
        case '\\':
          sb.append("\\\\");
          break;
        case '\"':
          sb.append("\\\"");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\b':
          sb.append("\\b");
          break;
        default:
          sb.append(c);
      }
    }
    return sb.toString();
  }
  
  public static boolean isNumber(Class<?> c) {
    return c == int.class
        || c == byte.class
        || c == short.class
        || c == float.class
        || c == long.class
        || c == double.class
        || isA(c, Number.class);
  }
  
  private static boolean isWrapper(Class<?> c) {
    return c == Integer.class
        || c == Byte.class
        || c == Short.class
        || c == Float.class
        || c == Long.class
        || c == Double.class
        || c == Character.class;
  }
  
  private static boolean isA(Class<?> what, Class<?> interfaceOrSuper) {
    return interfaceOrSuper.isAssignableFrom(what);
  }
  
}
