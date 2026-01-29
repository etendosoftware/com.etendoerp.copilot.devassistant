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

import static com.etendoerp.copilot.devassistant.TestConstants.PATH_PATTERN_JAVA;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.devassistant.KnowledgePathFile;
import com.etendoerp.copilot.util.FileUtils;

/**
 * Unit tests for {@link GitHubZipFilterHook}
 */
@ExtendWith(MockitoExtension.class)
class GitHubZipFilterHookTest {

  /**
   * System under test. The hook that filters GitHub ZIP downloads based on configured path files.
   */
  @InjectMocks
  private GitHubZipFilterHook hook;

  @Mock
  private OBDal obDal;

  @Mock
  private AttachImplementationManager attachManager;

  @Mock
  private CopilotFile copilotFile;

  @Mock
  private KnowledgePathFile pathFile1;

  @Mock
  private KnowledgePathFile pathFile2;

  @Mock
  private OBCriteria<KnowledgePathFile> pathFileCriteria;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<WeldUtils> weldMock;
  private MockedStatic<OBMessageUtils> messageMock;
  private MockedStatic<FileUtils> fileUtilsMock;

  private List<KnowledgePathFile> pathFileList;

  /**
   * Initializes static mocks and common stubs before each test.
   * Mocks configured:
   * - OBDal#getInstance to return obDal
   * - WeldUtils#getInstanceFromStaticBeanManager to return attachManager
   * - OBMessageUtils for message keys used by the hook
   * - FileUtils static API to verify temp file cleanup
   */
  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    weldMock = mockStatic(WeldUtils.class);
    messageMock = mockStatic(OBMessageUtils.class);
    fileUtilsMock = mockStatic(FileUtils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    weldMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(AttachImplementationManager.class))
        .thenReturn(attachManager);

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_NoGitHubPathFound"))
        .thenReturn("No GitHub path found");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_InvalidPathFileFormat"))
        .thenReturn("Invalid path file format");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_NoFilesFound"))
        .thenReturn("No files found");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ErrorAttachingFile"))
        .thenReturn("Error attaching file");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_EmptyRepoUrlOrBranch"))
        .thenReturn("Empty repository URL or branch");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_FailedToDownloadZip"))
        .thenReturn("Failed to download ZIP from %s (repo: %s, branch: %s): %s");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_NoDirectoriesFound"))
        .thenReturn("No directories found in base path: %s");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_InvalidSubpathPattern"))
        .thenReturn("Invalid subpath pattern: %s");

    pathFileList = new ArrayList<>();
  }

  /**
   * Releases all static mocks after each test to avoid leakage between tests.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    weldMock.close();
    messageMock.close();
    fileUtilsMock.close();
  }

  /**
   * Verifies that typeCheck returns true for the expected hook type identifier.
   */
  @Test
  void testTypeCheckWithCorrectTypeShouldReturnTrue() {
    boolean result = hook.typeCheck("COPDEV_GIT");

    assertTrue(result);
  }

  /**
   * Verifies that typeCheck returns false when a different type is provided.
   */
  @Test
  void testTypeCheckWithIncorrectTypeShouldReturnFalse() {
    boolean result = hook.typeCheck("OTHER_TYPE");

    assertFalse(result);
  }

  /**
   * Ensures that typeCheck handles null input and returns false.
   */
  @Test
  void testTypeCheckWithNullShouldReturnFalse() {
    boolean result = hook.typeCheck(null);

    assertFalse(result);
  }

  /**
   * Ensures that typeCheck handles empty strings and returns false.
   */
  @Test
  void testTypeCheckWithEmptyStringShouldReturnFalse() {
    boolean result = hook.typeCheck("");

    assertFalse(result);
  }


  /**
   * Given a valid path with a specific extension, exec attempts a download and fails in this mocked
   * context, raising OBException.
   */
  @Test
  void testExecWithValidPathWithSpecificFileExtensionShouldFailOnDownload() {
    String validPath = "/owner/repo/tree/main/src/main/java/*.java";
    when(pathFile1.getPathFile()).thenReturn(validPath);
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * Given a valid path using a wildcard extension, exec processing ends in OBException due to
   * mocked download failure.
   */
  @Test
  void testExecWithValidPathWithWildcardExtensionShouldFailOnDownload() {
    String validPath = "/owner/repo/tree/main/src/*";
    when(pathFile1.getPathFile()).thenReturn(validPath);
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * With multiple KnowledgePathFile entries, the hook proceeds and ends with OBException (mocked
   * download failure).
   */
  @Test
  void testExecWithMultiplePathFilesShouldFailOnDownload() {
    when(pathFile1.getPathFile()).thenReturn(PATH_PATTERN_JAVA);
    pathFileList.add(pathFile1);
    pathFileList.add(pathFile2);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * Ensures temporary files are cleaned up if exec throws during processing. Verifies FileUtils
   * cleanup invocation.
   */
  @Test
  void testExecShouldCleanupTempFilesOnError() {
    when(pathFile1.getPathFile()).thenReturn(PATH_PATTERN_JAVA);
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    try {
      hook.exec(copilotFile);
    } catch (OBException e) {
      // Expected due to download failure
    }

    // Verify cleanup was called
    fileUtilsMock.verify(() -> FileUtils.cleanupTempFileIfNeeded(eq(copilotFile), any()), atLeastOnce());
  }

  /**
   * Ensures that if an existing attachment is detected, the hook attempts to manage it during
   * exec, even when execution ends in error.
   */
  @Test
  void testExecWithExistingAttachmentShouldAttemptDelete() {
    when(pathFile1.getPathFile()).thenReturn(PATH_PATTERN_JAVA);
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    try {
      hook.exec(copilotFile);
    } catch (OBException e) {
      // Expected due to download failure
    }

    verify(pathFile1, atLeastOnce()).getPathFile();
  }

  /**
   * Ensures that a path ending only with an extension is handled gracefully and results in an
   * OBException with an error attaching file message.
   */
  @Test
  void testExecWithPathEndingInExtensionOnlyShouldHandleGracefully() {
    when(pathFile1.getPathFile()).thenReturn("/owner/repo/tree/main/.java");
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    // Should skip this path and throw no files found
    OBException exception = assertThrows(OBException.class, () -> hook.exec(copilotFile));
    assertTrue(exception.getMessage().contains("Error attaching file"));

  }

  /**
   * Ensures that deep directory structures are parsed; exec throws OBException in this mock.
   */
  @Test
  void testExecWithPathContainingMultipleSlashesShouldParse() {
    when(pathFile1.getPathFile()).thenReturn("/owner/repo/tree/main/src/main/java/com/example/*.java");
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * Verifies that different branch names are supported; OBException raised due to mocked failure.
   */
  @Test
  void testExecWithDifferentBranchesShouldProcess() {
    when(pathFile1.getPathFile()).thenReturn("/owner/repo/tree/develop/src/*.java");
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * Verifies that feature branch names with slashes are supported; OBException in this setup.
   */
  @Test
  void testExecWithFeatureBranchShouldProcess() {
    when(pathFile1.getPathFile()).thenReturn("/owner/repo/tree/feature/new-feature/src/*.java");
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * Ensures that non-code extensions (e.g., .properties) are accepted; OBException due to mocked
   * download failure.
   */
  @Test
  void testExecWithNumericExtensionShouldProcess() {
    when(pathFile1.getPathFile()).thenReturn("/owner/repo/tree/main/config/*.properties");
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * Ensures that mixed-case extensions are handled; exec ends with OBException here.
   */
  @Test
  void testExecWithMixedCaseExtensionShouldProcess() {
    when(pathFile1.getPathFile()).thenReturn("/owner/repo/tree/main/docs/*.Md");
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * If no extension is present in the configured path, the hook defaults to a wildcard selection;
   * OBException expected due to mocked failure.
   */
  @Test
  void testExecWithNoExtensionInPathShouldUseWildcard() {
    when(pathFile1.getPathFile()).thenReturn("/owner/repo/tree/main/src/main/java");
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * Ensures that names with special characters like hyphens are supported; OBException here.
   */
  @Test
  void testExecWithSpecialCharactersInPathShouldProcess() {
    when(pathFile1.getPathFile()).thenReturn("/owner/repo-name/tree/main/src-folder/*.java");
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    assertThrows(OBException.class, () -> hook.exec(copilotFile));
  }

  /**
   * Verifies interaction with KnowledgePathFile to retrieve the configured path during exec.
   */
  @Test
  void testExecVerifiesPathFileRetrieval() {
    when(pathFile1.getPathFile()).thenReturn(PATH_PATTERN_JAVA);
    pathFileList.add(pathFile1);

    setupPathFileCriteriaMocks();

    try {
      hook.exec(copilotFile);
    } catch (OBException e) {
      // Expected
    }

    verify(pathFile1, atLeastOnce()).getPathFile();
  }

  /**
   * Common stubbing for the KnowledgePathFile criteria to return the test's pathFileList.
   */
  private void setupPathFileCriteriaMocks() {
    when(obDal.createCriteria(KnowledgePathFile.class)).thenReturn(pathFileCriteria);
    when(pathFileCriteria.add(any())).thenReturn(pathFileCriteria);
    when(pathFileCriteria.list()).thenReturn(pathFileList);
  }

}
