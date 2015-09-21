package com.faforever.client.patch;

import com.faforever.client.game.GameType;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.ForgedAlliancePrefs;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.TaskService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.util.Callback;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.faforever.client.patch.GitRepositoryGameUpdateService.STEAM_API_DLL;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class GitRepositoryGameUpdateServiceTest extends AbstractPlainJavaFxTest {

  private static final long TIMEOUT = 5000;
  private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;
  @Mock
  private ForgedAlliancePrefs forgedAlliancePrefs;
  @Mock
  private ApplicationContext applicationContext;
  @Mock
  private Environment environment;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private TaskService taskService;
  @Mock
  private I18n i18n;
  @Mock
  private GitWrapper gitWrapper;
  @Mock
  private NotificationService notificationService;
  @Mock
  private Preferences preferences;
  private Path faBinDirectory;
  private GitRepositoryGameUpdateService instance;

  @Before
  public void setUp() throws Exception {
    instance = new GitRepositoryGameUpdateService();
    instance.preferencesService = preferencesService;
    instance.taskService = taskService;
    instance.i18n = i18n;
    instance.gitWrapper = gitWrapper;
    instance.notificationService = notificationService;
    instance.applicationContext = applicationContext;

    GitCheckGameUpdateTask gameUpdateTask = mock(GitCheckGameUpdateTask.class, withSettings().useConstructor());
    when(preferencesService.getPreferences()).thenReturn(preferences);
    when(preferences.getForgedAlliance()).thenReturn(forgedAlliancePrefs);
    when(applicationContext.getBean(GitCheckGameUpdateTask.class)).thenReturn(gameUpdateTask);

    faBinDirectory = forgedAlliancePrefs.getPath().resolve("bin");

    instance.postConstruct();
  }

  @Test
  public void testUpdateInBackgroundFaDirectoryUnspecified() throws Exception {
    when(forgedAlliancePrefs.getPath()).thenReturn(null);

    instance.updateInBackground(GameType.FAF.getString(), null, null, null);

    verifyZeroInteractions(instance.taskService);
  }

  @Test
  public void testUpdateInBackgroundThrowsException() throws Exception {
    doAnswer((InvocationOnMock invocation) -> {
      Callback callback = invocation.getArgumentAt(1, Callback.class);
      callback.error(new Exception("This exception mimicks that something went wrong"));
      return null;
    }).when(instance.taskService).submitTask(any(), any());

    CompletableFuture<Void> future = instance.updateInBackground(GameType.FAF.getString(), null, null, null);

    future.get(TIMEOUT, TIMEOUT_UNIT);
    assertThat(future.isCompletedExceptionally(), is(true));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testCheckForUpdatesInBackgroundPatchingIsNeeded() throws Exception {
    doAnswer((InvocationOnMock invocation) -> {
      invocation.getArgumentAt(1, Callback.class).success(true);
      return null;
    }).when(instance.taskService).submitTask(any(), any());

    CompletableFuture<Void> future = instance.checkForUpdateInBackground();

    future.get(TIMEOUT, TIMEOUT_UNIT);
    assertThat(future.isCompletedExceptionally(), is(false));
  }

  @Test
  public void testCheckForUpdatesInBackgroundThrowsException() throws Exception {
    doAnswer((InvocationOnMock invocation) -> {
      Callback callback = invocation.getArgumentAt(1, Callback.class);
      callback.error(new Exception("This exception mimicks that something went wrong"));
      return null;
    }).when(instance.taskService).submitTask(any(), any());

    CompletableFuture<Void> future = instance.checkForUpdateInBackground();

    future.get(TIMEOUT, TIMEOUT_UNIT);
    assertThat(future.isCompletedExceptionally(), is(false));
  }

  @Test
  public void testGuessInstallTypeRetail() throws Exception {
    instance.checkForUpdateInBackground();

    assertTrue(Files.notExists(faBinDirectory.resolve(STEAM_API_DLL)));

    GitRepositoryGameUpdateService.InstallType installType = instance.guessInstallType();
    assertThat(installType, is(GitRepositoryGameUpdateService.InstallType.RETAIL));
  }

  @Test
  public void testGuessInstallTypeSteam() throws Exception {
    instance.checkForUpdateInBackground();

    Files.createDirectories(faBinDirectory);
    Files.createFile(faBinDirectory.resolve(STEAM_API_DLL));

    GitRepositoryGameUpdateService.InstallType installType = instance.guessInstallType();
    assertThat(installType, is(GitRepositoryGameUpdateService.InstallType.STEAM));
  }
}