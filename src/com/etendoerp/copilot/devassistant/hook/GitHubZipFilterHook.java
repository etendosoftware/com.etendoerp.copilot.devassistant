package com.etendoerp.copilot.devassistant.hook;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
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
      OBCriteria<KnowledgePathFile> pathCriteria = OBDal.getInstance().createCriteria(KnowledgePathFile.class);
      pathCriteria.add(Restrictions.eq(KnowledgePathFile.PROPERTY_FILE, hookObject));
      pathCriteria.add(Restrictions.isNotNull(KnowledgePathFile.PROPERTY_PATHFILE));

      List<KnowledgePathFile> pathFiles = pathCriteria.list();
      if (pathFiles.isEmpty()) {
        throw new OBException("No GitHub repository path found in Path Files subtab for CopilotFile ID " + hookObject.getId());
      }

      for (KnowledgePathFile pathFile : pathFiles) {
        String repoPath = pathFile.getPathFile();
        if (StringUtils.isBlank(repoPath)) {
          log.warn("Empty Path File found for CopilotFile ID {}. Skipping.", hookObject.getId());
          continue;
        }

        log.info("Processing Path File: {}", repoPath);

        if (!repoPath.startsWith("/")) {
          throw new OBException("Path File must start with '/', got: " + repoPath);
        }

        Matcher matcher = OWNER_REPO_PATTERN.matcher(repoPath);
        if (!matcher.find()) {
          throw new OBException("Invalid Path File format. Expected format: /owner/repo-name/tree/branch/subpath, got: " + repoPath);
        }

        String owner = matcher.group(1);
        String repoName = matcher.group(2);
        String branch = matcher.group(3);
        String subPathWithExtension = repoPath.substring(matcher.end());

        Matcher extMatcher = EXTENSION_PATTERN.matcher(subPathWithExtension);
        String fileExtension = "*";
        if (extMatcher.find()) {
          fileExtension = extMatcher.group(1);
          subPathWithExtension = subPathWithExtension.substring(0, subPathWithExtension.length() - (fileExtension.length() + 1));
        }

        String repoUrl = GITHUB_BASE_URL + "/" + owner + "/" + repoName;
        log.info("Constructed GitHub repository URL: {}", repoUrl);
        log.info("Owner: {}", owner);
        log.info("Branch: {}", branch);
        log.info("Subpath to filter files: {}", subPathWithExtension);
        log.info("File extension to filter: {}", fileExtension);

        File zipFile = downloadGitHubZip(repoUrl, branch);
        log.info("Downloaded ZIP file: {}", zipFile.getAbsolutePath());

        File extractedDir = unzipToTempDirectory(zipFile);
        log.info("Extracted repository to: {}", extractedDir.getAbsolutePath());

        log.info("Listing all files in extracted directory: {}", extractedDir.getAbsolutePath());
        Files.walkFileTree(extractedDir.toPath(), new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            log.info("File: {}", extractedDir.toPath().relativize(file));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            log.info("Directory: {}\n", extractedDir.toPath().relativize(dir));
            return FileVisitResult.CONTINUE;
          }
        });

        extractedPaths.add(extractedDir.toPath());

        if (!zipFile.delete()) {
          log.warn("Could not delete temporary ZIP file: {}", zipFile.getAbsolutePath());
        }

        collectFiles(extractedDir.toPath(), subPathWithExtension, branch, fileExtension, filesToZip);
      }

      if (filesToZip.isEmpty()) {
        throw new OBException("No files found for CopilotFile ID " + hookObject.getId() + " across all specified paths.");
      }

      Path basePath = extractedPaths.get(0);
      finalZip = createZip(filesToZip, basePath);
      log.info("Created filtered ZIP file: {}", finalZip.getAbsolutePath());

      AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class);
      removeAttachment(aim, hookObject);

      aim.upload(new HashMap<>(), COPILOT_FILE_TAB_ID, hookObject.getId(), hookObject.getOrganization().getId(), finalZip);
      log.info("Successfully attached ZIP file to CopilotFile ID: {}", hookObject.getId());

    } catch (Exception e) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ErrorAttachingFile")), e);
    } finally {
      if (finalZip != null && finalZip.exists()) {
        if (!finalZip.delete()) {
          log.warn("Could not delete temporary ZIP file: {}", finalZip.getAbsolutePath());
        }
      }
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
          log.warn("Could not delete temporary directory: {}", extractedPath, e);
        }
      }
    }
  }

  private File downloadGitHubZip(String repoUrl, String branch) throws IOException {
    if (StringUtils.isBlank(repoUrl) || StringUtils.isBlank(branch)) {
      throw new IllegalArgumentException("Repository URL and branch cannot be empty");
    }
    String zipUrl = repoUrl.endsWith("/") ? repoUrl + "archive/refs/heads/" + branch + ".zip" : repoUrl + "/archive/refs/heads/" + branch + ".zip";
    log.info("Downloading ZIP from: {}", zipUrl);
    File tempZip = File.createTempFile("githubRepo", ".zip");
    try (InputStream in = new URL(zipUrl).openStream()) {
      Files.copy(in, tempZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IOException("Failed to download ZIP from " + zipUrl, e);
    }
    return tempZip;
  }

  private File unzipToTempDirectory(File zipFile) throws IOException {
    Path tempDir = Files.createTempDirectory("unzippedRepo");
    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        log.info("Extracting entry: {}", entry.getName());
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

  private void collectFiles(Path basePath, String subPath, String branch, String fileExtension, Set<Path> filesToZip) throws IOException {
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
        throw new IOException("No directories found in basePath: " + basePath);
      }
    }

    log.info("Repository directory prefix: {}", repoDirPrefix);

    String globPattern = repoDirPrefix + "/" + subPath;
    if (!fileExtension.equals("*")) {
      globPattern += "." + fileExtension;
    }
    log.info("Filtering files with glob pattern: {}", globPattern);

    try {
      PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
      Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Path relPath = basePath.relativize(file);
          log.info("Checking file: {}", relPath);
          boolean matchesPattern = matcher.matches(relPath);
          log.info("File {} matches pattern: {}", relPath, matchesPattern);
          boolean notIgnored = checkIgnoredFiles(file.toString());
          log.info("File {} is not ignored: {}", relPath, notIgnored);
          if (matchesPattern && notIgnored) {
            filesToZip.add(file);
            log.info("Found file: {}", file.toString());
          } else {
            log.debug("File {} does not match pattern or is ignored", relPath);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IllegalArgumentException e) {
      log.error("Invalid subpath pattern: {}", subPath, e);
      throw new IOException("Invalid subpath pattern: " + subPath, e);
    }
  }

  private boolean checkIgnoredFiles(String path) {
    for (String ignore : IGNORE_STRINGS) {
      if (StringUtils.containsIgnoreCase(path, ignore)) {
        log.debug("Ignoring file {} because it contains '{}'", path, ignore);
        return false;
      }
    }
    return true;
  }

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

  private void removeAttachment(AttachImplementationManager aim, CopilotFile hookObject) {
    Attachment attachment = getAttachment(hookObject);
    if (attachment != null) {
      aim.delete(attachment);
    }
  }

  public static Attachment getAttachment(CopilotFile targetInstance) {
    OBCriteria<Attachment> attchCriteria = OBDal.getInstance().createCriteria(Attachment.class);
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, targetInstance.getId()));
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE,
        OBDal.getInstance().get(Table.class, COPILOT_FILE_AD_TABLE_ID)));
    attchCriteria.add(Restrictions.ne(Attachment.PROPERTY_ID, targetInstance.getId()));
    return (Attachment) attchCriteria.setMaxResults(1).uniqueResult();
  }
}