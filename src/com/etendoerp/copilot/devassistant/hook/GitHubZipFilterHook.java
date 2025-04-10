package com.etendoerp.copilot.devassistant.hook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.URL;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.FileSystems;

import java.nio.file.attribute.BasicFileAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
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
 * This class implements the CopilotFileHook interface to handle GitHub repositories.
 * It downloads ZIP files from GitHub repositories specified in the Path Files subtab,
 * extracts them, filters files within the specified subpaths, creates a new ZIP with the filtered files,
 * and attaches it to the CopilotFile record.
 */
public class GitHubZipFilterHook implements CopilotFileHook {

  private static final Logger log = LogManager.getLogger(GitHubZipFilterHook.class);
  private static final String[] IGNORE_STRINGS = { ".git", "node_modules", ".idea", "/.", "/venv/", "/.venv/" };
  public static final String COPILOT_FILE_TAB_ID = "09F802E423924081BC2947A64DDB5AF5";
  public static final String COPILOT_FILE_AD_TABLE_ID = "6B246B1B3A6F4DE8AFC208E07DB29CE2";
  private static final String GITHUB_BASE_URL = "https://github.com";
  private static final Pattern OWNER_REPO_PATTERN = Pattern.compile("^/([^/]+)/([^/]+)/tree/([^/]+)/");
  private static final Pattern EXTENSION_PATTERN = Pattern.compile("\\.([a-zA-Z0-9*]+)$");

  @Override
  public boolean typeCheck(String type) {
    return StringUtils.equals(type, "COPDEV_GIT");
  }

  @Override
  public void exec(CopilotFile hookObject) throws OBException {
    List<Path> extractedPaths = new ArrayList<>();
    File finalZip = null;
    Set<Path> filesToZip = new HashSet<>();

    try {
      // 1. Fetch the paths from the Path Files subtab
      List<KnowledgePathFile> pathFiles = fetchPathFiles(hookObject);

      // 2. Process each path
      for (KnowledgePathFile pathFile : pathFiles) {
        processPathFile(pathFile, extractedPaths, filesToZip);
      }

      // 3. Check if any files were found
      if (filesToZip.isEmpty()) {
        throw new OBException(OBMessageUtils.messageBD("COPDEV_NoFilesFound"));
      }

      // 4. Create and attach the ZIP
      Path basePath = extractedPaths.get(0);
      finalZip = createZip(filesToZip, basePath);
      log.debug("Created filtered ZIP file: {}", finalZip.getAbsolutePath());

      attachZipFile(hookObject, finalZip);

    } catch (Exception e) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ErrorAttachingFile")), e);
    } finally {
      cleanup(finalZip, extractedPaths);
    }
  }

  /**
   * Fetches the list of KnowledgePathFile records associated with the given CopilotFile.
   * @param hookObject The CopilotFile to fetch paths for.
   * @return A list of KnowledgePathFile records.
   * @throws OBException If no paths are found.
   */
  private List<KnowledgePathFile> fetchPathFiles(CopilotFile hookObject) {
    OBCriteria<KnowledgePathFile> pathCriteria = OBDal.getInstance().createCriteria(KnowledgePathFile.class);
    pathCriteria.add(Restrictions.eq(KnowledgePathFile.PROPERTY_FILE, hookObject));
    pathCriteria.add(Restrictions.isNotNull(KnowledgePathFile.PROPERTY_PATHFILE));

    List<KnowledgePathFile> pathFiles = pathCriteria.list();
    if (pathFiles.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_NoGitHubPathFound"));
    }
    return pathFiles;
  }

  /**
   * Processes a single KnowledgePathFile by downloading, extracting, and filtering files from the specified GitHub repository.
   * @param pathFile The KnowledgePathFile to process.
   * @param extractedPaths A list to store the paths of extracted directories.
   * @param filesToZip A set to store the paths of files to be included in the final ZIP.
   * @throws IOException If an I/O error occurs during processing.
   */
  private void processPathFile(KnowledgePathFile pathFile, List<Path> extractedPaths, Set<Path> filesToZip) throws IOException {
    String repoPath = pathFile.getPathFile();
    if (StringUtils.isBlank(repoPath)) {
      log.warn("Empty Path File found for CopilotFile ID {}. Skipping.", pathFile.getFile().getId());
      return;
    }

    log.debug("Processing Path File: {}", repoPath);

    // Validate and parse the relative path
    validatePathFile(repoPath);

    // Extract repository information
    Matcher matcher = OWNER_REPO_PATTERN.matcher(repoPath);
    matcher.find(); // Already validated that it matches
    String owner = matcher.group(1);
    String repoName = matcher.group(2);
    String branch = matcher.group(3);

    // Extract the subpath, ensuring repoPath is not null (already validated)
    String subPathWithExtension = StringUtils.substring(repoPath, matcher.end());

    // Extract the file extension from the subpath
    String fileExtension = extractFileExtension(subPathWithExtension);
    if (!StringUtils.equals(fileExtension, "*") && StringUtils.isNotBlank(subPathWithExtension) && StringUtils.isNotBlank(fileExtension)) {
      int extensionLength = fileExtension.length() + 1; // +1 for the dot
      int endIndex = subPathWithExtension.length() - extensionLength;
      if (endIndex <= 0) {
        return;
      }
      subPathWithExtension = StringUtils.substring(subPathWithExtension, 0, endIndex);
    }

    // Construct the repository URL
    String repoUrl = GITHUB_BASE_URL + "/" + owner + "/" + repoName;
    log.debug("Constructed GitHub repository URL: {}", repoUrl);

    // Download and extract the repository
    File zipFile = downloadGitHubZip(repoUrl, branch);
    log.info("Downloaded ZIP file: {}", zipFile.getAbsolutePath());

    File extractedDir = unzipToTempDirectory(zipFile);
    log.info("Extracted repository to: {}", extractedDir.getAbsolutePath());

    // List all files in the extracted directory
    log.debug("Listing all files in extracted directory: {}", extractedDir.getAbsolutePath());
    Files.walkFileTree(extractedDir.toPath(), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        log.debug("File: {}", extractedDir.toPath().relativize(file));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        log.debug("Directory: {}\n", extractedDir.toPath().relativize(dir));
        return FileVisitResult.CONTINUE;
      }
    });

    extractedPaths.add(extractedDir.toPath());

    // Delete the temporary ZIP file using Files.delete
    try {
      Files.delete(zipFile.toPath());
    } catch (IOException e) {
      log.warn("Could not delete temporary ZIP file: {}. Reason: {}", zipFile.getAbsolutePath(), e.getMessage());
    }

    // Filter files within the subpath
    collectFiles(extractedDir.toPath(), subPathWithExtension, fileExtension, filesToZip);
  }

  /**
   * Validates the format of the Path File.
   * @param repoPath The path to validate.
   * @throws OBException If the path format is invalid.
   */
  private void validatePathFile(String repoPath) {
    // Use StringUtils for null-safe string operation
    if (!StringUtils.startsWith(repoPath, "/")) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_InvalidPathFileFormat"));
    }

    Matcher matcher = OWNER_REPO_PATTERN.matcher(repoPath);
    if (!matcher.find()) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_InvalidPathFileFormat"));
    }
  }

  /**
   * Extracts the file extension from the subpath.
   * @param subPathWithExtension The subpath that may include an extension.
   * @return The extracted file extension, or "*" if none is found.
   */
  private String extractFileExtension(String subPathWithExtension) {
    Matcher extMatcher = EXTENSION_PATTERN.matcher(StringUtils.defaultString(subPathWithExtension));
    String fileExtension = "*";
    if (extMatcher.find()) {
      fileExtension = extMatcher.group(1);
    }
    return fileExtension;
  }

  /**
   * Attaches the filtered ZIP file to the CopilotFile record.
   * @param hookObject The CopilotFile to attach the ZIP to.
   * @param finalZip The ZIP file to attach.
   */
  private void attachZipFile(CopilotFile hookObject, File finalZip) {
    AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
    removeAttachment(aim, hookObject);

    aim.upload(new HashMap<>(), COPILOT_FILE_TAB_ID, hookObject.getId(), hookObject.getOrganization().getId(), finalZip);
    log.info("Successfully attached ZIP file to CopilotFile ID: {}", hookObject.getId());
  }

  /**
   * Cleans up temporary files and directories.
   * @param finalZip The final ZIP file to delete.
   * @param extractedPaths The list of extracted directories to delete.
   */
  private void cleanup(File finalZip, List<Path> extractedPaths) {
    // Delete the final ZIP file if it exists
    if (finalZip != null && finalZip.exists()) {
      try {
        Files.delete(finalZip.toPath());
      } catch (IOException e) {
        log.warn("Could not delete temporary ZIP file: {}. Reason: {}", finalZip.getAbsolutePath(), e.getMessage());
      }
    }

    // Delete extracted directories
    for (Path extractedPath : extractedPaths) {
      try {
        Files.walkFileTree(extractedPath, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
      } catch (IOException e) {
        log.warn("Could not delete temporary directory: {}. Reason: {}", extractedPath, e.getMessage());
      }
    }
  }

  /**
   * Downloads a ZIP file from a GitHub repository.
   * @param repoUrl The URL of the repository.
   * @param branch The branch to download.
   * @return The downloaded ZIP file.
   * @throws IOException If an I/O error occurs during the download.
   */
  private File downloadGitHubZip(String repoUrl, String branch) throws IOException {
    if (StringUtils.isBlank(repoUrl) || StringUtils.isBlank(branch)) {
      throw new IllegalArgumentException(OBMessageUtils.messageBD("COPDEV_EmptyRepoUrlOrBranch"));
    }
    String zipUrl = StringUtils.endsWith(repoUrl, "/") ? repoUrl + "archive/refs/heads/" + branch + ".zip" : repoUrl + "/archive/refs/heads/" + branch + ".zip";
    log.debug("Downloading ZIP from: {}", zipUrl);
    File tempZip = File.createTempFile("githubRepo", ".zip");
    try (InputStream in = new URL(zipUrl).openStream()) {
      Files.copy(in, tempZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IOException(String.format(OBMessageUtils.messageBD("COPDEV_FailedToDownloadZip"), zipUrl, repoUrl, branch, e.getMessage()), e);
    }
    return tempZip;
  }

  /**
   * Unzips a ZIP file to a temporary directory.
   * @param zipFile The ZIP file to unzip.
   * @return The directory where the ZIP was extracted.
   * @throws IOException If an I/O error occurs during extraction.
   */
  private File unzipToTempDirectory(File zipFile) throws IOException {
    Path tempDir = Files.createTempDirectory("unzippedRepo");
    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        log.debug("Extracting entry: {}", entry.getName());
        Path filePath = tempDir.resolve(entry.getName());
        if (!entry.isDirectory()) {
          Files.createDirectories(filePath.getParent());
          Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        zis.closeEntry();
      }
    }
    return tempDir.toFile();
  }

  /**
   * Collects files from the extracted repository that match the specified subpath and extension.
   * @param basePath The base path of the extracted repository.
   * @param subPath The subpath to filter files.
   * @param fileExtension The file extension to filter.
   * @param filesToZip A set to store the paths of files to be included in the final ZIP.
   * @throws IOException If an I/O error occurs during file collection.
   */
  private void collectFiles(Path basePath, String subPath, String fileExtension, Set<Path> filesToZip) throws IOException {
    if (StringUtils.isBlank(subPath)) {
      log.warn("Subpath is empty, cannot filter files");
      return;
    }

    String repoDirPrefix;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(basePath)) {
      Iterator<Path> iterator = stream.iterator();
      if (iterator.hasNext()) {
        repoDirPrefix = iterator.next().getFileName().toString();
        if (iterator.hasNext()) {
          log.warn("Multiple directories found in basePath, using the first one: {}", repoDirPrefix);
        }
      } else {
        throw new IOException(String.format(OBMessageUtils.messageBD("COPDEV_NoDirectoriesFound"), basePath));
      }
    }

    log.debug("Repository directory prefix: {}", repoDirPrefix);

    String globPattern = repoDirPrefix + "/" + subPath;
    if (!StringUtils.equals(fileExtension, "*")) {
      globPattern += "." + fileExtension;
    }
    log.debug("Filtering files with glob pattern: {}", globPattern);

    try {
      PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
      Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Path relPath = basePath.relativize(file);
          log.debug("Checking file: {}", relPath);
          boolean matchesPattern = matcher.matches(relPath);
          log.debug("File {} matches pattern: {}", relPath, matchesPattern);
          boolean notIgnored = checkIgnoredFiles(file.toString());
          log.debug("File {} is not ignored: {}", relPath, notIgnored);
          if (matchesPattern && notIgnored) {
            filesToZip.add(file);
            log.debug("Found file: {}", file.toString());
          } else {
            log.debug("File {} does not match pattern or is ignored", relPath);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IllegalArgumentException e) {
      log.error("Invalid subpath pattern: {}", subPath, e);
      throw new IOException(String.format(OBMessageUtils.messageBD("COPDEV_InvalidSubpathPattern"), subPath), e);
    }
  }

  /**
   * Checks if a file should be ignored based on predefined ignore strings.
   * @param path The path of the file to check.
   * @return True if the file should not be ignored, false otherwise.
   */
  private boolean checkIgnoredFiles(String path) {
    for (String ignore : IGNORE_STRINGS) {
      if (StringUtils.containsIgnoreCase(path, ignore)) {
        log.debug("Ignoring file {} because it contains '{}'", path, ignore);
        return false;
      }
    }
    return true;
  }

  /**
   * Creates a ZIP file containing the filtered files.
   * @param files The set of files to include in the ZIP.
   * @param basePath The base path for relativizing file paths.
   * @return The created ZIP file.
   * @throws IOException If an I/O error occurs during ZIP creation.
   */
  private File createZip(Set<Path> files, Path basePath) throws IOException {
    Path tempDir = Files.createTempDirectory("filteredZip");
    File zipFile = File.createTempFile("filtered", ".zip", tempDir.toFile());
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
      for (Path file : files) {
        String relativePath = basePath.relativize(file).toString();
        zos.putNextEntry(new ZipEntry(relativePath));
        Files.copy(file, zos);
        zos.closeEntry();
      }
    }
    return zipFile;
  }

  /**
   * Removes any existing attachment from the CopilotFile.
   * @param aim The AttachImplementationManager to use.
   * @param hookObject The CopilotFile to remove the attachment from.
   */
  private void removeAttachment(AttachImplementationManager aim, CopilotFile hookObject) {
    Attachment attachment = getAttachment(hookObject);
    if (attachment != null) {
      aim.delete(attachment);
    }
  }

  /**
   * Retrieves the attachment associated with the specified CopilotFile record.
   * This method queries the database to find an attachment linked to the given CopilotFile,
   * ensuring it matches the correct table and record ID, and is not the same as the target instance ID.
   * @param targetInstance The CopilotFile instance to retrieve the attachment for.
   * @return The Attachment object associated with the CopilotFile, or null if no attachment is found.
   */
  public static Attachment getAttachment(CopilotFile targetInstance) {
    OBCriteria<Attachment> attchCriteria = OBDal.getInstance().createCriteria(Attachment.class);
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, targetInstance.getId()));
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE,
        OBDal.getInstance().get(Table.class, COPILOT_FILE_AD_TABLE_ID)));
    attchCriteria.add(Restrictions.ne(Attachment.PROPERTY_ID, targetInstance.getId()));
    return (Attachment) attchCriteria.setMaxResults(1).uniqueResult();
  }
}
