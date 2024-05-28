package life.catalogue.common.io;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.*;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class CompressionUtil {
  
  private CompressionUtil() {
    throw new UnsupportedOperationException("Can't initialize class");
  }
  
  private static final Logger LOG = LoggerFactory.getLogger(CompressionUtil.class);
  private static final int BUFFER = 2048;

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
        files = unpackOther(directory, compressedFile);

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
   * Uses apache to decompress any archive and preserve subdirectories, but remove hidden files and directories
   */
  public static List<File> unpackOther(File directory, File zipFile) throws IOException {
    List<File> files = new ArrayList<>();
    try (var fin = new BufferedInputStream(new FileInputStream(zipFile))) {
      try (var in = new CompressorStreamFactory().createCompressorInputStream(fin)) {
        files = unarchive(directory, in, zipFile.getName());
      } catch (CompressorException e) {
        // tar only?
        files = unarchive(directory, fin, zipFile.getName());
      }
    }
    return files;
  }

  private static List<File> unarchive(File directory, InputStream in, String originalFilename) throws IOException {
    try (ArchiveInputStream ain = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(in))) {
      ArchiveEntry entry;
      while ((entry = ain.getNextEntry()) != null) {
        // ignore hidden directories
        if (entry.isDirectory()) {
          if (isHiddenFile(new File(entry.getName()))) {
            LOG.debug("Ignoring hidden directory: {}", entry.getName());
          } else {
            new File(directory, entry.getName()).mkdir();
          }
        }
        // ignore hidden files
        else {
          if (isHiddenFile(new File(entry.getName()))) {
            LOG.debug("Ignoring hidden file: {}", entry.getName());
          } else {
            File targetFile = new File(directory, entry.getName());
            // ensure parent folder always exists, and extract file
            targetFile.getParentFile().mkdirs();
            LOG.debug("Extracting file: {} to: {}", entry.getName(), targetFile.getAbsolutePath());
            try (FileOutputStream out = new FileOutputStream(targetFile)) {
              IOUtils.copy(ain, out);
            }
          }
        }
      }
      // remove the wrapping root directory and flatten structure
      removeRootDirectories(directory);
      return listFiles(directory);

    } catch (ArchiveException e) {
      // single, compressed file
      File targetFile = new File(directory, FilenameUtils.getBaseName(originalFilename));
      LOG.debug("Decompressing single file: {} ", targetFile.getAbsolutePath());
      try (FileOutputStream out = new FileOutputStream(targetFile)) {
        IOUtils.copy(in, out);
      }
      return List.of(targetFile);
    }
  }

  static List<File> listFiles(File root) throws IOException {
    var rootPath = root.toPath();
    return Files.walk(rootPath)
      .filter(p -> !p.equals(rootPath))
      .map(Path::toFile)
      .collect(Collectors.toList());
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
    zipFiles(Set.of(file), file.getParentFile(), zipFile);
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
      try (
        FileOutputStream dest = new FileOutputStream(zipFile);
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
      ) {
        // out.setMethod(ZipOutputStream.DEFLATED);
        byte[] data = new byte[BUFFER];
        for (File f : files) {
          LOG.debug("Adding file {} to archive", f);
          try (FileInputStream fi = new FileInputStream(f);
               BufferedInputStream origin = new BufferedInputStream(fi, BUFFER)
          ) {
            String zipPath = StringUtils.removeStart(f.getAbsolutePath(), rootContext.getAbsolutePath() + File.separator);
            ZipEntry entry = new ZipEntry(zipPath);
            out.putNextEntry(entry);
            int count;
            while ((count = origin.read(data, 0, BUFFER)) != -1) {
              out.write(data, 0, count);
            }
          }
        }
        out.finish();
      } catch (IOException e) {
        LOG.error("IOException while zipping files: {}", files);
        throw e;
      }
    }
  }
  
  /**
   * Extracts a zipped file into a target directory. If the file is wrapped in a root directory, this is removed by
   * default. Other subdirectories, unless hidden, are kept.
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
    LOG.debug("Unzipping archive {} into directory: {}", zipFile.getName(), directory.getAbsolutePath());
    try (ZipFile zf = new ZipFile(zipFile)) {
      Enumeration<? extends ZipEntry> entries = zf.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        // ignore hidden directories
        if (entry.isDirectory()) {
          if (isHiddenFile(new File(entry.getName()))) {
            LOG.debug("Ignoring hidden directory: {}", entry.getName());
          } else {
            new File(directory, entry.getName()).mkdir();
          }
        }
        // ignore hidden files
        else {
          if (isHiddenFile(new File(entry.getName()))) {
            LOG.debug("Ignoring hidden file: {}", entry.getName());
          } else {
            File targetFile = new File(directory, entry.getName());
            // ensure parent folder always exists, and extract file
            targetFile.getParentFile().mkdirs();
            extractFile(zf, entry, targetFile);
          }
        }
      }
    }
    // remove the wrapping root directory and flatten structure
    removeRootDirectories(directory);
    return listFiles(directory);
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
  private static void removeRootDirectories(File directory) {
    File[] rootFiles = directory.listFiles((FileFilter) HiddenFileFilter.VISIBLE);
    if (rootFiles != null && rootFiles.length == 1) {
      File root = rootFiles[0];
      if (root.isDirectory()) {
        LOG.debug("Removing single root folder {} found in decompressed archive", root.getAbsoluteFile());
        for (File f : org.apache.commons.io.FileUtils.listFilesAndDirs(root, TrueFileFilter.TRUE, TrueFileFilter.TRUE)) {
          if (!f.equals(root)) {
            File f2 = new File(f.getAbsolutePath().replaceFirst(root.getAbsolutePath(), directory.getAbsolutePath()));
            f.renameTo(f2);
          }
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
    try (InputStream in = zf.getInputStream(entry);
        OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile))
    ){
      LOG.debug("Extracting file: {} to: {}", entry.getName(), targetFile.getAbsolutePath());
      IOUtils.copy(in, out);
    } catch (IOException e) {
      LOG.error("File could not be extraced: {}", e.getMessage(), e);
    }
  }
  
}