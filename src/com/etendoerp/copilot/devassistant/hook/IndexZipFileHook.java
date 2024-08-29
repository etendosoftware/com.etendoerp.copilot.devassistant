package com.etendoerp.copilot.devassistant.hook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
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

  // Logger for this class
  private static final Logger log = LogManager.getLogger(IndexZipFileHook.class);
  // Tab ID for CopilotFile
  public static final String COPILOT_FILE_TAB_ID = "09F802E423924081BC2947A64DDB5AF5";
  public static final String COPILOT_FILE_AD_TABLE_ID = "6B246B1B3A6F4DE8AFC208E07DB29CE2";
  public static final String[] IGNORE_STRINGS = { ".git", "node_modules", ".idea", "/.", "/venv/", "/.venv/" };

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

  public static File getCodeIndexZipFile(String[] searchPaths) throws IOException {
    Set<Path> filesToZip = new HashSet<>();

    for (String searchPath : searchPaths) {


      int lastDotIndex = searchPath.lastIndexOf('.');
      int firstAstIndex = searchPath.indexOf('*');
      String pathToProcess = searchPath.substring(0, firstAstIndex);
      String glob = "*" + searchPath.substring(lastDotIndex);

      Path basePath = Paths.get(pathToProcess);
      if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
        throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_IsNotADirectory"), basePath));
      }

      PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
      try (Stream<Path> paths = Files.walk(basePath)) {
        List<Path> matchingFiles = new ArrayList<>();
        List<Path> nonMatchingFiles = new ArrayList<>();

        for (Path path : (Iterable<Path>) paths::iterator) {
          if (matcher.matches(path.getFileName()) && checkIgnoredFiles(path.toString())) {
            matchingFiles.add(path);
          } else {
            nonMatchingFiles.add(path);
          }
        }
        log.debug("Found {} matching files in {}", matchingFiles.size(), basePath.toString());

        filesToZip.addAll(matchingFiles);
      } catch (IOException e) {
        throw new OBException(OBMessageUtils.messageBD("COPDEV_NoFilesAddedToZip"));
      }
    }

    File zipFile = File.createTempFile("files", ".zip");
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
      int i = 0;
      int total = filesToZip.size();
      for (Path filePath : filesToZip) {
        ZipEntry zipEntry = new ZipEntry(filePath.toString());
        zos.putNextEntry(zipEntry);
        Files.copy(filePath, zos);
        zos.closeEntry();
        log.debug("Added file {} to zip file. {} of {}", filePath.toString(), ++i, total);
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
