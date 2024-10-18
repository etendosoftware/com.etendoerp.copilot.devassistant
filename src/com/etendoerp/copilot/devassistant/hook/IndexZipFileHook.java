package com.etendoerp.copilot.devassistant.hook;

import static com.etendoerp.copilot.devassistant.Utils.logIfDebug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.utility.Attachment;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.devassistant.KnowledgePathFile;
import com.etendoerp.copilot.hook.CopilotFileHook;

/**
 * This class implements the CopilotFileHook interface and provides functionality
 * for handling remote files.
 */
public class IndexZipFileHook implements CopilotFileHook {

  // Tab ID for CopilotFile
  public static final String COPILOT_FILE_TAB_ID = "09F802E423924081BC2947A64DDB5AF5";
  public static final String COPILOT_FILE_AD_TABLE_ID = "6B246B1B3A6F4DE8AFC208E07DB29CE2";
  protected static final String[] IGNORE_STRINGS = { ".git", "node_modules", ".idea", "/.", "/venv/", "/.venv/" };
  private static final Logger log = LogManager.getLogger(IndexZipFileHook.class);

 /**
 * Creates a ZIP file containing the specified set of files.
 * This method processes an array of search paths, identifies whether each path contains wildcards,
 * and handles each path accordingly to add matching files to the ZIP file.
 *
 * @param searchPaths The array of search paths to be processed.
 * @return The created ZIP file containing the files from the specified search paths.
 * @throws IOException If an I/O error occurs during the creation of the ZIP file.
 */
public static File getCodeIndexZipFile(String[] searchPaths) throws IOException {
  Set<Path> filesToZip = new HashSet<>();

  for (String searchPath : searchPaths) {
    // Trim whitespace
    searchPath = searchPath.trim();
    boolean hasWildcards = searchPath.contains("*") || searchPath.contains("?");
    if (hasWildcards) {
      handleWildcardPath(searchPath, filesToZip);
    } else {
      handleSpecificFilePath(searchPath, filesToZip);
    }
  }
  return getZipFile(filesToZip);
}

 /**
 * Handles specific files or directories without wildcards and adds them to the provided set.
 * This method processes the given search path, normalizes it, and checks if it exists.
 * If the path is a regular file and not ignored, it is added to the set.
 * If the path is a directory, all files in the directory are added recursively.
 *
 * @param searchPath The specific file or directory path to be processed.
 * @param filesToZip The set to which the files will be added.
 * @throws IOException If an I/O error occurs during file processing.
 * @throws OBException If the path does not exist or is invalid.
 */
private static void handleSpecificFilePath(String searchPath, Set<Path> filesToZip) throws IOException {
  // Handle specific files or directories without wildcards
  Path path = Paths.get(searchPath).normalize();

  if (!Files.exists(path)) {
    throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_PATHEXISTENCEERROR"), path.toString()));
  }

  if (Files.isRegularFile(path)) {
    if (checkIgnoredFiles(path.toString())) {
      filesToZip.add(path);
    }
  } else if (Files.isDirectory(path)) {
    // Add all files in directory recursively
    Files.walkFileTree(path, getSimpleFileVisitor(filesToZip));
  } else {
    throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_INVALIDPATH"), path.toString()));
  }
}

  /**
 * Handles paths with wildcards and adds matching files to the provided set.
 * This method processes the given search path, identifies the base path and glob pattern,
 * and walks the file tree from the base path to find matching files.
 *
 * @param searchPath The search path containing wildcards.
 * @param filesToZip The set to which matching files will be added.
 * @throws IOException If an I/O error occurs during file tree traversal.
 */
private static void handleWildcardPath(String searchPath, Set<Path> filesToZip) throws IOException {
  String globPattern;
  Path basePath;
  // Handle paths with wildcards
  int firstWildcardIndex = indexOfWildcard(searchPath);
  int lastSeparatorBeforeWildcard = searchPath.lastIndexOf(File.separator, firstWildcardIndex);

  // Base path is up to the last separator before the first wildcard
  String basePathString = (lastSeparatorBeforeWildcard >= 0)
      ? searchPath.substring(0, lastSeparatorBeforeWildcard)
      : ".";
  basePath = Paths.get(basePathString).normalize();

  // Validate base path
  if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
    throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_BASEPATHINVALID"), basePath.toString()));
  }

  // Glob pattern is the rest of the path after the base path
  String patternAfterBasePath = searchPath.substring(lastSeparatorBeforeWildcard + 1);
  globPattern = "glob:" + patternAfterBasePath;

  // Create PathMatcher
  PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

  // Walk file tree from base path
  Files.walkFileTree(basePath, getSimpleFileVisitor(basePath, matcher, filesToZip));
}

  /**
 * Creates a SimpleFileVisitor to visit files and add matching files to the provided set.
 * This method returns a SimpleFileVisitor that checks each visited file against the provided PathMatcher
 * and adds it to the set if it matches and is not ignored.
 *
 * @param basePath The base path to relativize the file paths.
 * @param matcher The PathMatcher to check if the file matches the pattern.
 * @param filesToZip The set to which matching files will be added.
 * @return A SimpleFileVisitor that processes files as described.
 */
private static SimpleFileVisitor<Path> getSimpleFileVisitor(Path basePath, PathMatcher matcher,
    Set<Path> filesToZip) {
  return new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      Path relativePath = basePath.relativize(file);
      if (matcher.matches(relativePath) && checkIgnoredFiles(file.toString())) {
        filesToZip.add(file);
      }
      return FileVisitResult.CONTINUE;
    }
  };
}

/**
 * Creates a SimpleFileVisitor to visit files and add non-ignored files to the provided set.
 * This method returns a SimpleFileVisitor that adds each visited file to the set if it is not ignored.
 *
 * @param filesToZip The set to which non-ignored files will be added.
 * @return A SimpleFileVisitor that processes files as described.
 */
private static SimpleFileVisitor<Path> getSimpleFileVisitor(Set<Path> filesToZip) {
  return new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      if (checkIgnoredFiles(file.toString())) {
        filesToZip.add(file);
      }
      return FileVisitResult.CONTINUE;
    }
  };
}

  /**
 * Finds the index of the first wildcard character ('*' or '?') in the given path string.
 * This method searches for the first occurrence of '*' and '?' in the provided path string
 * and returns the index of the first one found. If neither is found, it returns -1.
 *
 * @param path The path string to search for wildcard characters.
 * @return The index of the first wildcard character, or -1 if none are found.
 */
private static int indexOfWildcard(String path) {
  int indexAsterisk = path.indexOf('*');
  int indexQuestion = path.indexOf('?');

  if (indexAsterisk == -1) return indexQuestion;
  if (indexQuestion == -1) return indexAsterisk;

  return Math.min(indexAsterisk, indexQuestion);
}


  /**
 * Creates a ZIP file containing the specified set of files.
 * This method generates a temporary ZIP file and adds each file from the provided set to the ZIP file.
 *
 * @param filesToZip The set of file paths to be included in the ZIP file.
 * @return The created ZIP file.
 * @throws IOException If an I/O error occurs during the creation of the ZIP file.
 */
private static File getZipFile(Set<Path> filesToZip) throws IOException {
  // Create the ZIP file
  File zipFile = File.createTempFile("files", ".zip");
  try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
    int i = 0;
    int total = filesToZip.size();
    for (Path filePath : filesToZip) {
      ZipEntry zipEntry = new ZipEntry(filePath.toString());
      zos.putNextEntry(zipEntry);
      Files.copy(filePath, zos);
      zos.closeEntry();
      logIfDebug(log, String.format("Added file %s to zip file. %d of %d%n", filePath.toString(), ++i, total));
    }
  }
  return zipFile;
}

  /**
   * Checks if the given path string contains any of the ignored substrings.
   * This method iterates over a predefined list of substrings and returns false
   * if any of these substrings are found in the provided path string. Otherwise, it returns true.
   *
   * @param pathString
   *     The path string to be checked against the ignored substrings.
   * @return true if the path string does not contain any ignored substrings, false otherwise.
   */
  private static boolean checkIgnoredFiles(String pathString) {
    for (String ignoreString : IGNORE_STRINGS) {
      if (StringUtils.contains(pathString, ignoreString)) {
        return false;
      }
    }
    return true;
  }

  /**
 * Retrieves the attachment associated with the given CopilotFile.
 * This method creates a criteria query to find an attachment that matches the specified CopilotFile.
 *
 * @param targetInstance The CopilotFile instance for which the attachment is to be retrieved.
 * @return The Attachment associated with the given CopilotFile, or null if no attachment is found.
 */
public static Attachment getAttachment(CopilotFile targetInstance) {
  OBCriteria<Attachment> attchCriteria = OBDal.getInstance().createCriteria(Attachment.class);
  attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, targetInstance.getId()));
  attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE,
      OBDal.getInstance().get(Table.class, COPILOT_FILE_AD_TABLE_ID)));
  attchCriteria.add(Restrictions.ne(Attachment.PROPERTY_ID, targetInstance.getId()));
  return (Attachment) attchCriteria.setMaxResults(1).uniqueResult();
}

  /**
   * Executes the hook for a given CopilotFile.
   *
   * @param hookObject
   *     The CopilotFile for which to execute the hook.
   * @throws OBException
   *     If there is an error executing the hook.
   */
  @Override
  public void exec(CopilotFile hookObject) throws OBException {
    if (log.isDebugEnabled()) {
      log.debug(String.format("IndexZipFile for file: %s executed start", hookObject.getName()));
    }

    try {

      List<KnowledgePathFile> pathList = hookObject.getCOPDEVKnowledgePathFilesList();

      Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
      String sourcePath = "@source.path@";
      String sourcePathProp = properties.getProperty("source.path");

      String[] paths = pathList.stream()
          .map(KnowledgePathFile::getPathFile)
          .map(path -> path.startsWith(sourcePath) ? path.replaceFirst(sourcePath, sourcePathProp) : path)
          .toArray(String[]::new);

      File file = getCodeIndexZipFile(paths);

      AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
      removeAttachment(aim, hookObject);

      aim.upload(new HashMap<>(), COPILOT_FILE_TAB_ID, hookObject.getId(),
          hookObject.getOrganization().getId(), file);

    } catch (Exception e) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ErrorAttachingFile")), e);
    }
  }

  /**
 * Removes the attachment associated with the given CopilotFile.
 * This method retrieves the attachment for the specified CopilotFile and deletes it using the provided AttachImplementationManager.
 *
 * @param aim The AttachImplementationManager instance used to delete the attachment.
 * @param hookObject The CopilotFile instance for which the attachment is to be removed.
 */
private void removeAttachment(AttachImplementationManager aim, CopilotFile hookObject) {
  Attachment attachment = getAttachment(hookObject);
  if (attachment != null) {
    aim.delete(attachment);
  }
}

  /**
   * Checks if the hook is applicable for the given type.
   *
   * @param type
   *     The type to check.
   * @return true if the hook is applicable, false otherwise.
   */
  @Override
  public boolean typeCheck(String type) {
    return StringUtils.equals(type, "COPDEV_CI");
  }
}
