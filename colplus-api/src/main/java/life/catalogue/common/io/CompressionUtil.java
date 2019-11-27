package life.catalogue.common.io;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import com.google.common.collect.Lists;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompressionUtil {
  
  private CompressionUtil() {
    throw new UnsupportedOperationException("Can't initialize class");
  }
  
  private static final Logger LOG = LoggerFactory.getLogger(CompressionUtil.class);
  private static final int BUFFER = 2048;
  private static final String APPLE_RESOURCE_FORK = "__MACOSX";
  
  /**
   * Tries to decompress a file trying gzip or zip regardless of the filename or its suffix.
   * If nothing works leave file as it is.
   *
   * @param directory      directory where archive's contents will be decompressed to.
   *                       If already existing it will be wiped
   * @param compressedFile compressed file
   * @return list of files that have been extracted or null an empty list if archive couldn't be decompressed
   * @throws IOException if problem occurred reading compressed file, or directory couldn't be written
   *                     to
   */
  public static List<File> decompressFile(File directory, File compressedFile) throws IOException {
    if (directory.exists()) {
      LOG.debug("Remove existing uncompressed dir {}", directory.getAbsolutePath());
      FileUtils.deleteDirectory(directory);
    }
    directory.mkdirs();
    
    List<File> files = null;
    // first try zip
    try {
      files = unzipFile(directory, compressedFile);
      
    } catch (ZipException e) {
      LOG.debug("No zip compression");
      // Try gzip if needed
      try {
        files = ungzipFile(directory, compressedFile);
        
      } catch (Exception e2) {
        LOG.debug("No gzip compression");
        files = copyOriginalFile(directory, compressedFile);
        LOG.info("Assuming source file is uncompressed");
      }
    }
    
    return files;
  }
  
  private static List<File> copyOriginalFile(File directory, File sourceFile) throws IOException {
    File targetFile = new File(directory, sourceFile.getName());
    FileUtils.copyFile(sourceFile, targetFile);
    return Lists.newArrayList(targetFile);
  }
  
  /**
   * Extracts a gzipped file. Subdirectories or hidden files (i.e. files starting with a dot) are being ignored.
   *
   * @param directory where the file should be extracted to
   * @param zipFile   to extract
   * @return a list of all created files
   */
  public static List<File> ungzipFile(File directory, File zipFile) throws IOException {
    List<File> files = new ArrayList<File>();
    TarArchiveInputStream in = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(zipFile)));
    try {
      TarArchiveEntry entry;
      while ((entry = in.getNextTarEntry()) != null) {
        if (entry.isDirectory()) {
          LOG.debug("TAR archive contains directories which are being ignored");
          continue;
        }
        String fn = new File(entry.getName()).getName();
        if (fn.startsWith(".")) {
          LOG.debug("TAR archive contains a hidden file which is being ignored");
          continue;
        }
        File targetFile = new File(directory, fn);
        if (targetFile.exists()) {
          LOG.warn("TAR archive contains duplicate filenames, only the first is being extracted");
          continue;
        }
        LOG.debug("Extracting file: {} to: {}", entry.getName(), targetFile.getAbsolutePath());
        FileOutputStream out = new FileOutputStream(targetFile);
        try {
          IOUtils.copy(in, out);
          out.close();
        } finally {
          IOUtils.closeQuietly(out);
        }
        files.add(targetFile);
      }
    } finally {
      in.close();
    }
    return files;
  }
  
  /**
   * Gunzip a file.  Use this method with isTarred false if the gzip contains a single file.  If it's a gzip
   * of a tar archive pass true to isTarred (or call @ungzipFile(directory, zipFile) which is what this method
   * just redirects to for isTarred).
   *
   * @param directory the output directory for the uncompressed file(s)
   * @param zipFile   the gzip file
   * @param isTarred  true if the gzip contains a tar archive
   * @return a List of the uncompressed file name(s)
   * @throws IOException if reading or writing fails
   */
  public static List<File> ungzipFile(File directory, File zipFile, boolean isTarred) throws IOException {
    if (isTarred) return ungzipFile(directory, zipFile);
    
    List<File> files = new ArrayList<File>();
    GZIPInputStream in = null;
    BufferedOutputStream dest = null;
    try {
      in = new GZIPInputStream(new FileInputStream(zipFile));
      
      // assume that the gzip filename is the filename + .gz
      String unzippedName = zipFile.getName().substring(0, zipFile.getName().lastIndexOf("."));
      File outputFile = new File(directory, unzippedName);
      LOG.debug("Extracting file: {} to: {}", unzippedName, outputFile.getAbsolutePath());
      FileOutputStream fos = new FileOutputStream(outputFile);
      
      dest = new BufferedOutputStream(fos, BUFFER);
      int count;
      byte[] data = new byte[BUFFER];
      while ((count = in.read(data, 0, BUFFER)) != -1) {
        dest.write(data, 0, count);
      }
      files.add(outputFile);
    } finally {
      if (in != null) in.close();
      if (dest != null) {
        dest.flush();
        dest.close();
      }
    }
    
    return files;
  }
  
  /**
   * Zip a directory with all files but skipping included subdirectories.
   * Only files directly within the directory are added to the archive.
   *
   * @param dir     the directory to zip
   * @param zipFile the zipped file
   */
  public static void zipDir(File dir, File zipFile) throws IOException {
    zipDir(dir, zipFile, false);
  }
  
  /**
   * Zip a directory with all files. Files in Subdirectories will be included if the inclSubdirs is true.
   *
   * @param dir         the directory to zip
   * @param zipFile     the zipped file
   * @param inclSubdirs if true includes all subdirectories recursively
   */
  public static void zipDir(File dir, File zipFile, boolean inclSubdirs) throws IOException {
    Collection<File> files = org.apache.commons.io.FileUtils.listFiles(dir, null, inclSubdirs);
    zipFiles(files, dir, zipFile);
  }
  
  public static void zipFile(File file, File zipFile) throws IOException {
    Set<File> files = new HashSet<File>();
    files.add(file);
    zipFiles(files, file.getParentFile(), zipFile);
  }
  
  /**
   * Creates a zip archive from a given collection of files.
   * In order to preserve paths in the archive a rootContext can be specified which will be removed from the individual
   * zip entries. For example a rootContext of /home/freak with a file /home/freak/photo/birthday.jpg to be zipped
   * will result in a zip entry with a path photo/birthday.jpg.
   *
   * @param files       to be included in the zip archive
   * @param rootContext optional path to be removed from each file
   * @param zipFile     the zip file to be created
   * @throws IOException
   */
  public static void zipFiles(Collection<File> files, File rootContext, File zipFile) throws IOException {
    if (files.isEmpty()) {
      LOG.info("no files to zip.");
    } else {
      try {
        BufferedInputStream origin = null;
        FileOutputStream dest = new FileOutputStream(zipFile);
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
        // out.setMethod(ZipOutputStream.DEFLATED);
        byte[] data = new byte[BUFFER];
        for (File f : files) {
          LOG.debug("Adding file {} to archive", f);
          FileInputStream fi = new FileInputStream(f);
          origin = new BufferedInputStream(fi, BUFFER);
          
          String zipPath = StringUtils.removeStart(f.getAbsolutePath(), rootContext.getAbsolutePath() + File.separator);
          ZipEntry entry = new ZipEntry(zipPath);
          out.putNextEntry(entry);
          int count;
          while ((count = origin.read(data, 0, BUFFER)) != -1) {
            out.write(data, 0, count);
          }
          origin.close();
        }
        out.finish();
        out.close();
      } catch (IOException e) {
        LOG.error("IOException while zipping files: {}", files);
        throw e;
      }
    }
  }
  
  /**
   * Extracts a zipped file into a target directory. If the file is wrapped in a root directory, this is removed by
   * default. Other subdirectories are ignored according to the parameter keepSubdirectories.
   * </br>
   * The following types of files are also ignored by default:
   * i) hidden files (i.e. files starting with a dot)
   * ii) Apple resource fork (__MACOSX), including its subdirectories and subfiles
   *
   * @param directory where the zipped file and its subdirectories should be extracted to
   * @param zipFile   to extract
   * @return a list of all created files and directories extracted to target directory
   */
  public static List<File> unzipFile(File directory, File zipFile) throws IOException {
    LOG.debug("Unzipping archive " + zipFile.getName() + " into directory: " + directory.getAbsolutePath());
    ZipFile zf = new ZipFile(zipFile);
    Enumeration<? extends ZipEntry> entries = zf.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      // ignore resource fork directories and subfiles
      if (entry.getName().toUpperCase().contains(APPLE_RESOURCE_FORK)) {
        LOG.debug("Ignoring resource fork file: " + entry.getName());
      }
      // ignore directories and hidden directories (e.g. .svn) (based on flag)
      else if (entry.isDirectory()) {
        if (isHiddenFile(new File(entry.getName()))) {
          LOG.debug("Ignoring hidden directory: " + entry.getName());
        } else {
          new File(directory, entry.getName()).mkdir();
        }
      }
      // ignore hidden files
      else {
        if (isHiddenFile(new File(entry.getName()))) {
          LOG.debug("Ignoring hidden file: " + entry.getName());
        } else {
          File targetFile = new File(directory, entry.getName());
          // ensure parent folder always exists, and extract file
          targetFile.getParentFile().mkdirs();
          extractFile(zf, entry, targetFile);
        }
      }
    }
    zf.close();
    // remove the wrapping root directory and flatten structure
    removeRootDirectory(directory);
    return (directory.listFiles() == null) ? new ArrayList<File>() : Arrays.asList(directory.listFiles());
  }
  
  /**
   * @return true if file is a hidden file or directory, or if any of its parent directories are hidden checking
   * recursively
   */
  private static boolean isHiddenFile(File f) {
    if (f.getName().startsWith(".")) {
      return true;
    } else if (f.getParentFile() != null) {
      return isHiddenFile(f.getParentFile());
    }
    return false;
  }
  
  /**
   * Removes a wrapping root directory and flatten its structure by moving all that root directory's files and
   * subdirectories up to the same level as the root directory.
   */
  private static void removeRootDirectory(File directory) {
    File[] rootFiles = directory.listFiles((FileFilter) HiddenFileFilter.VISIBLE);
    if (rootFiles.length == 1) {
      File root = rootFiles[0];
      if (root.isDirectory()) {
        LOG.debug("Removing single root folder {} found in decompressed archive", root.getAbsoluteFile());
        for (File f : org.apache.commons.io.FileUtils.listFilesAndDirs(root, TrueFileFilter.TRUE, TrueFileFilter.TRUE)) {
          File f2 = new File(directory, f.getName());
          f.renameTo(f2);
        }
        root.delete();
      }
    }
  }
  
  /**
   * Extract an entry from a zipped file into a target file.
   *
   * @param zf         .zip file being unzipped (ZipFile)
   * @param entry      entry in .zip file currently being examined (ZipEntry)
   * @param targetFile destination file to extract to
   */
  private static void extractFile(ZipFile zf, ZipEntry entry, File targetFile) {
    try {
      LOG.debug("Extracting file: {} to: {}", entry.getName(), targetFile.getAbsolutePath());
      InputStream in = zf.getInputStream(entry);
      OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile));
      try {
        IOUtils.copy(zf.getInputStream(entry), out);
      } finally {
        in.close();
        out.close();
      }
    } catch (IOException e) {
      LOG.error("File could not be extraced: " + e.getMessage(), e);
    }
  }
  
}