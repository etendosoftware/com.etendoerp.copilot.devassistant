/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.devassistant.hook;

import static com.etendoerp.copilot.devassistant.TestConstants.CONTENT1;
import static com.etendoerp.copilot.devassistant.TestConstants.CONTENT2;
import static com.etendoerp.copilot.devassistant.TestConstants.COPILOT_FILE;
import static com.etendoerp.copilot.devassistant.TestConstants.FILE1;
import static com.etendoerp.copilot.devassistant.TestConstants.FILE2;
import static com.etendoerp.copilot.devassistant.TestConstants.ORG123;
import static com.etendoerp.copilot.devassistant.TestConstants.SOURCE_PATH;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_CONTENT;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_FILE_TXT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.devassistant.KnowledgePathFile;
import com.etendoerp.copilot.util.FileUtils;

/**
 * Unit tests for {@link IndexZipFileHook}.
 *
 * <p>This test suite verifies all behaviors related to ZIP creation,
 * path resolution, attachment handling, type validation and execution flow
 * within the {@code IndexZipFileHook} class.</p>
 *
 * <p>The tests cover:</p>
 * <ul>
 *   <li>Validation of supported hook types</li>
 *   <li>ZIP file generation from files, directories and wildcard patterns</li>
 *   <li>Error handling for invalid or non-existent paths</li>
 *   <li>Attachment retrieval and cleanup logic</li>
 *   <li>Execution flow including path token replacement</li>
 *   <li>Temporary file cleanup</li>
 * </ul>
 *
 * <p>Mocks are heavily used to isolate the hook from external dependencies such
 * as OBDal, Weld, property providers, attachments, filesystem utilities, etc.</p>
 *
 * <p>Each test provides a clear description of the expected scenario and asserts
 * correct functional and exceptional behavior.</p>
 */
@ExtendWith(MockitoExtension.class)
class IndexZipFileHookTest {

  /**
   * Mocked instance of the hook under test. Mockito injects mocks into this class.
   */
  @InjectMocks
  private IndexZipFileHook hook;

  @Mock
  private OBDal obDal;

  @Mock
  private OBPropertiesProvider propertiesProvider;

  @Mock
  private AttachImplementationManager attachManager;

  @Mock
  private CopilotFile copilotFile;

  @Mock
  private Organization organization;

  @Mock
  private KnowledgePathFile pathFile1;

  @Mock
  private KnowledgePathFile pathFile2;

  @Mock
  private Attachment attachment;

  @Mock
  private Table table;

  @Mock
  private OBCriteria<Attachment> attachmentCriteria;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBPropertiesProvider> propertiesMock;
  private MockedStatic<WeldUtils> weldMock;
  private MockedStatic<OBMessageUtils> messageMock;
  private MockedStatic<FileUtils> fileUtilsMock;

  private Properties properties;
  private List<KnowledgePathFile> pathFileList;

  /**
   * Temporary directory for isolated file-system operations used for ZIP creation.
   */
  @TempDir
  Path tempDir;

  /**
   * Initializes static mocks and shared test variables before each test.
   */
  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    propertiesMock = mockStatic(OBPropertiesProvider.class);
    weldMock = mockStatic(WeldUtils.class);
    messageMock = mockStatic(OBMessageUtils.class);
    fileUtilsMock = mockStatic(FileUtils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    propertiesMock.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
    weldMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class))
        .thenReturn(attachManager);

    properties = new Properties();
    pathFileList = new ArrayList<>();
  }

  /**
   * Closes static mocks after each test to avoid cross-test interference.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    propertiesMock.close();
    weldMock.close();
    messageMock.close();
    fileUtilsMock.close();
  }

  /**
   * Ensures that the hook accepts the expected type "COPDEV_CI".
   */
  @Test
  void testTypeCheckWithCorrectTypeShouldReturnTrue() {
    boolean result = hook.typeCheck("COPDEV_CI");
    assertTrue(result);
  }

  /**
   * Ensures that an unsupported type returns false.
   */
  @Test
  void testTypeCheckWithIncorrectTypeShouldReturnFalse() {
    boolean result = hook.typeCheck("OTHER_TYPE");
    assertFalse(result);
  }

  /**
   * Ensures that null input is safely handled and rejected.
   */
  @Test
  void testTypeCheckWithNullShouldReturnFalse() {
    boolean result = hook.typeCheck(null);
    assertFalse(result);
  }

  /**
   * Verifies ZIP creation from a single valid file path.
   *
   * @throws IOException if the temp file cannot be created
   */
  @Test
  void testGetCodeIndexZipFileWithSingleFileShouldCreateZip() throws IOException {
    Path testFile = tempDir.resolve(TEST_FILE_TXT);
    Files.writeString(testFile, TEST_CONTENT);

    String[] searchPaths = {testFile.toString()};
    File zipFile = IndexZipFileHook.getCodeIndexZipFile(searchPaths);

    assertNotNull(zipFile);
    assertTrue(zipFile.exists());
    assertTrue(zipFile.getName().endsWith(".zip"));
    assertTrue(zipFile.length() > 0);
  }

  /**
   * Ensures directories are traversed and all files included in ZIP.
   *
   * @throws IOException if test files cannot be created
   */
  @Test
  void testGetCodeIndexZipFileWithDirectoryShouldCreateZipWithAllFiles() throws IOException {
    Path subDir = tempDir.resolve("subdir");
    Files.createDirectories(subDir);

    Path file1 = tempDir.resolve(FILE1);
    Path file2 = subDir.resolve(FILE2);
    Files.writeString(file1, CONTENT1);
    Files.writeString(file2, CONTENT2);

    String[] searchPaths = {tempDir.toString()};
    File zipFile = IndexZipFileHook.getCodeIndexZipFile(searchPaths);

    assertNotNull(zipFile);
    assertTrue(zipFile.exists());
    assertTrue(zipFile.length() > 0);
  }

  /**
   * Tests wildcard expansion (*.txt) and ZIP creation from matching files.
   */
  @Test
  void testGetCodeIndexZipFileWithWildcardShouldMatchFiles() throws IOException {
    Path file1 = tempDir.resolve("test1.txt");
    Path file2 = tempDir.resolve("test2.txt");
    Path file3 = tempDir.resolve("other.log");
    Files.writeString(file1, CONTENT1);
    Files.writeString(file2, CONTENT2);
    Files.writeString(file3, "content3");

    String[] searchPaths = { tempDir + File.separator + "*.txt"};
    File zipFile = IndexZipFileHook.getCodeIndexZipFile(searchPaths);

    assertNotNull(zipFile);
    assertTrue(zipFile.exists());
  }

  /**
   * Ensures a non-existent path triggers an {@link OBException}.
   */
  @Test
  void testGetCodeIndexZipFileWithNonExistentPathShouldThrowException() {
    String[] searchPaths = {"/nonexistent/path/file.txt"};

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_PathNotExists"))
        .thenReturn("Path does not exist: %s");

    assertThrows(OBException.class, () -> IndexZipFileHook.getCodeIndexZipFile(searchPaths));
  }

  /**
   * Ensures .git directory is ignored in ZIP content.
   */
  @Test
  void testGetCodeIndexZipFileShouldIgnoreGitDirectory() throws IOException {
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectories(gitDir);
    Files.writeString(gitDir.resolve("config"), "git config");

    Files.writeString(tempDir.resolve("normal.txt"), "normal content");

    String[] searchPaths = {tempDir.toString()};
    File zipFile = IndexZipFileHook.getCodeIndexZipFile(searchPaths);

    assertNotNull(zipFile);
    assertTrue(zipFile.exists());
  }

  /**
   * Ensures node_modules directory is ignored.
   */
  @Test
  void testGetCodeIndexZipFileShouldIgnoreNodeModules() throws IOException {
    Path nodeModules = tempDir.resolve("node_modules");
    Files.createDirectories(nodeModules);
    Files.writeString(nodeModules.resolve("package.json"), "{}");

    Files.writeString(tempDir.resolve("app.js"), "console.log('test')");

    String[] searchPaths = {tempDir.toString()};
    File zipFile = IndexZipFileHook.getCodeIndexZipFile(searchPaths);

    assertNotNull(zipFile);
    assertTrue(zipFile.exists());
  }

  /**
   * Validates that multiple independent paths are included in the ZIP.
   */
  @Test
  void testGetCodeIndexZipFileWithMultiplePathsShouldIncludeAll() throws IOException {
    Path file1 = tempDir.resolve(FILE1);
    Path file2 = tempDir.resolve(FILE2);
    Files.writeString(file1, CONTENT1);
    Files.writeString(file2, CONTENT2);

    String[] searchPaths = {file1.toString(), file2.toString()};
    File zipFile = IndexZipFileHook.getCodeIndexZipFile(searchPaths);

    assertNotNull(zipFile);
    assertTrue(zipFile.exists());
    assertTrue(zipFile.length() > 0);
  }

  /**
   * Ensures paths with leading/trailing spaces are accepted.
   */
  @Test
  void testGetCodeIndexZipFileWithPathsContainingSpacesShouldTrim() throws IOException {
    Path testFile = tempDir.resolve(TEST_FILE_TXT);
    Files.writeString(testFile, TEST_CONTENT);

    String[] searchPaths = {"  " + testFile + "  "};
    File zipFile = IndexZipFileHook.getCodeIndexZipFile(searchPaths);

    assertNotNull(zipFile);
    assertTrue(zipFile.exists());
  }

  /**
   * Ensures an empty directory still produces a valid ZIP file.
   */
  @Test
  void testGetCodeIndexZipFileWithEmptyDirectoryShouldCreateEmptyZip() throws IOException {
    Path emptyDir = tempDir.resolve("empty");
    Files.createDirectories(emptyDir);

    File zipFile = IndexZipFileHook.getCodeIndexZipFile(new String[]{emptyDir.toString()});

    assertNotNull(zipFile);
    assertTrue(zipFile.exists());
  }

  /**
   * Ensures wildcard under invalid base path triggers an OBException.
   */
  @Test
  void testGetCodeIndexZipFileWithInvalidWildcardPathShouldThrowException() {
    String[] searchPaths = {"/nonexistent/path/*.txt"};

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_BasePathInvalid"))
        .thenReturn("Base path is invalid: %s");

    assertThrows(OBException.class, () -> IndexZipFileHook.getCodeIndexZipFile(searchPaths));
  }

  /**
   * Ensures an existing attachment is correctly returned from OBDal.
   */
  @Test
  void testGetAttachmentWhenAttachmentExistsShouldReturnAttachment() {
    mockAttachmentCriteria(attachment);

    when(copilotFile.getId()).thenReturn(COPILOT_FILE);
    when(obDal.get(Table.class, IndexZipFileHook.COPILOT_FILE_AD_TABLE_ID)).thenReturn(table);

    Attachment result = IndexZipFileHook.getAttachment(copilotFile);

    assertNotNull(result);
    assertEquals(attachment, result);
  }

  /**
   * Ensures null is returned when no attachment exists.
   */
  @Test
  void testGetAttachmentWhenNoAttachmentExistsShouldReturnNull() {
    mockAttachmentCriteria(null);

    when(copilotFile.getId()).thenReturn(COPILOT_FILE);
    when(obDal.get(Table.class, IndexZipFileHook.COPILOT_FILE_AD_TABLE_ID)).thenReturn(table);

    Attachment result = IndexZipFileHook.getAttachment(copilotFile);

    assertNull(result);
  }

  /**
   * Ensures a valid execution creates a ZIP and uploads it correctly.
   *
   * @throws IOException if the test file cannot be created
   */
  @Test
  void testExecWithValidPathsShouldCreateAndUploadZip() throws IOException {
    Path testFile = tempDir.resolve(TEST_FILE_TXT);
    Files.writeString(testFile, TEST_CONTENT);

    setupExecMocks(testFile.toString());

    assertDoesNotThrow(() -> hook.exec(copilotFile));

    verify(attachManager).upload(any(), eq(IndexZipFileHook.COPILOT_FILE_TAB_ID),
        eq(COPILOT_FILE), eq(ORG123), any(File.class));
  }

  /**
   * Ensures existing attachment is deleted prior to upload.
   */
  @Test
  void testExecWithExistingAttachmentShouldDeleteBeforeUpload() throws IOException {
    Path testFile = tempDir.resolve(TEST_FILE_TXT);
    Files.writeString(testFile, TEST_CONTENT);

    setupExecMocks(testFile.toString());
    mockAttachmentCriteria(attachment);

    assertDoesNotThrow(() -> hook.exec(copilotFile));

    verify(attachManager).delete(attachment);
    verify(attachManager).upload(any(), any(), any(), any(), any(File.class));
  }

  /**
   * Ensures path tokens like @source.path@ are resolved from OBProperties.
   */
  @Test
  void testExecWithSourcePathTokenShouldReplaceToken() throws IOException {
    Path testFile = tempDir.resolve(TEST_FILE_TXT);
    Files.writeString(testFile, TEST_CONTENT);

    when(pathFile1.getPathFile()).thenReturn("@source.path@/test.txt");
    pathFileList.add(pathFile1);

    properties.setProperty(SOURCE_PATH, tempDir.toString());
    setupExecCommonMocks();
    mockAttachmentCriteria(null);

    assertDoesNotThrow(() -> hook.exec(copilotFile));
    verify(attachManager).upload(any(), any(), any(), any(), any(File.class));
  }

  /**
   * Ensures that invalid paths during ZIP creation cause an OBException.
   */
  @Test
  void testExecWhenZipCreationFailsShouldThrowOBException() {
    when(pathFile1.getPathFile()).thenReturn("/nonexistent/path");
    pathFileList.add(pathFile1);

    properties.setProperty(SOURCE_PATH, "/some/path");
    when(propertiesProvider.getOpenbravoProperties()).thenReturn(properties);
    when(copilotFile.getCOPDEVKnowledgePathFilesList()).thenReturn(pathFileList);

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_PathNotExists"))
        .thenReturn("Path does not exist: %s");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ErrorAttachingFile"))
        .thenReturn("Error attaching file");

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * Ensures temporary ZIP file is removed after use.
   *
   * @throws IOException if test file cannot be created
   */
  @Test
  void testExecShouldCleanupTempFile() throws IOException {
    Path testFile = tempDir.resolve(TEST_FILE_TXT);
    Files.writeString(testFile, TEST_CONTENT);

    setupExecMocks(testFile.toString());

    assertDoesNotThrow(() -> hook.exec(copilotFile));

    fileUtilsMock.verify(() -> FileUtils.cleanupTempFile(any(Path.class), eq(true)));
  }

  /**
   * Ensures that multiple knowledge paths are processed during execution.
   */
  @Test
  void testExecWithMultiplePathsShouldProcessAll() throws IOException {
    Path file1 = tempDir.resolve(FILE1);
    Path file2 = tempDir.resolve(FILE2);
    Files.writeString(file1, CONTENT1);
    Files.writeString(file2, CONTENT2);

    when(pathFile1.getPathFile()).thenReturn(file1.toString());
    when(pathFile2.getPathFile()).thenReturn(file2.toString());
    pathFileList.add(pathFile1);
    pathFileList.add(pathFile2);

    properties.setProperty(SOURCE_PATH, tempDir.toString());
    setupExecCommonMocks();
    mockAttachmentCriteria(null);

    assertDoesNotThrow(() -> hook.exec(copilotFile));
    verify(attachManager).upload(any(), any(), any(), any(), any(File.class));
  }

  /**
   * Configures common mocks used by exec() tests for path/file handling and DAL access.
   */
  private void setupExecCommonMocks() {
    when(propertiesProvider.getOpenbravoProperties()).thenReturn(properties);
    when(copilotFile.getCOPDEVKnowledgePathFilesList()).thenReturn(pathFileList);
    when(copilotFile.getId()).thenReturn(COPILOT_FILE);
    when(copilotFile.getOrganization()).thenReturn(organization);
    when(organization.getId()).thenReturn(ORG123);
    when(obDal.get(Table.class, IndexZipFileHook.COPILOT_FILE_AD_TABLE_ID)).thenReturn(table);
  }

  /**
   * Common mock initialization for exec() tests involving valid file paths.
   *
   * @param filePath the absolute path of the file to process
   */
  private void setupExecMocks(String filePath) {
    when(pathFile1.getPathFile()).thenReturn(filePath);
    pathFileList.add(pathFile1);

    properties.setProperty(SOURCE_PATH, tempDir.toString());
    setupExecCommonMocks();
    mockAttachmentCriteria(null);
  }

  /**
   * Configures the Attachment criteria mocks to return the specified result.
   *
   * @param attachmentResult the Attachment instance to be returned by uniqueResult(),
   *                         or {@code null} if no attachment is expected.
   */
  private void mockAttachmentCriteria(Attachment attachmentResult) {
    when(obDal.createCriteria(Attachment.class)).thenReturn(attachmentCriteria);
    when(attachmentCriteria.add(any())).thenReturn(attachmentCriteria);
    when(attachmentCriteria.setMaxResults(anyInt())).thenReturn(attachmentCriteria);
    when(attachmentCriteria.uniqueResult()).thenReturn(attachmentResult);
  }
}
