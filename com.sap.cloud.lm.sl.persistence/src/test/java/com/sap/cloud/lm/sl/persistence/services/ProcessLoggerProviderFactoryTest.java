package com.sap.cloud.lm.sl.persistence.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.activiti.engine.delegate.DelegateExecution;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.sap.cloud.lm.sl.persistence.message.Constants;
import com.sap.cloud.lm.sl.persistence.services.ProcessLoggerProviderFactory.ThreadLocalLogProvider;

public class ProcessLoggerProviderFactoryTest {

	private String logDir;

	@Mock
	private ProcessLogsPersistenceService processLogsPersistenceServiceMock;

	@InjectMocks
	private ProcessLoggerProviderFactory processLoggerProviderFactory = ProcessLoggerProviderFactory.getInstance();
	private ThreadLocalLogProvider threadLocalProvider;

	@Before
	public void setUp() throws IOException {
		MockitoAnnotations.initMocks(this);
		threadLocalProvider = processLoggerProviderFactory.getLoggerProvider("test");
		logDir = Files.createTempDirectory("testLogDir").toString();
		ProcessLoggerProviderFactory.LOG_DIR = logDir;
	}

	@After
	public void tearDown() throws IOException {
		processLoggerProviderFactory.removeAll();
		LogManager.shutdown();
		if (Files.isDirectory(Paths.get(logDir))) {
			deleteRecursivelyDirectory();
		}
	}

	private void deleteRecursivelyDirectory() throws IOException {
		Files.walkFileTree(Paths.get(logDir), new SimpleFileVisitor<Path>() {
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
	}

	@Test
	public void testGetLogger() {
		String processId = "1";
		String prefix = "-";

		Logger logger1 = threadLocalProvider.getLogger(processId, prefix);

		assertTrue(logger1.getName().contains(processId));
		assertTrue(logger1.getName().contains(prefix));
	}

	@Test
	public void testGetLoggerWithCustomeName() {
		String processId = "1";
		String prefix = "-";
		String name = "blabla";

		Logger logger2 = threadLocalProvider.getLogger(processId, prefix, name);
		assertTrue(logger2.getName().contains(name));
	}

	@Test
	public void testGetLoggerWithCustomLoggingLevel() throws IOException {
		String processId = "1";
		String prefix = "-";
		Level customLoggingLevel = Level.INFO;
		PatternLayout customLayout = mock(PatternLayout.class);

		Logger logger4 = threadLocalProvider.getLogger(processId, prefix, customLoggingLevel, customLayout, logDir);
		assertEquals(logger4.getLevel(), customLoggingLevel);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFileLogStorageThreadInterruption()
			throws InterruptedException, ExecutionException, TimeoutException {
		ExecutorService executorMock = mock(ExecutorService.class);
		Future<Logger> futureTaskMock = mock(Future.class);
		org.slf4j.Logger loggerMock = mock(org.slf4j.Logger.class);

		when(executorMock.submit((java.util.concurrent.Callable<Logger>) any())).thenReturn(futureTaskMock);
		doThrow(InterruptedException.class).when(futureTaskMock).get(anyLong(), any(TimeUnit.class));

		ProcessLoggerProviderFactory.LOGGER = loggerMock;
		threadLocalProvider.tryToGetLogger(executorMock);

		verify(loggerMock).warn(anyString());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFileLogStorageThreadTimeout() throws InterruptedException, ExecutionException, TimeoutException {
		ExecutorService executorMock = mock(ExecutorService.class);
		Future<Logger> futureTaskMock = mock(Future.class);
		org.slf4j.Logger loggerMock = mock(org.slf4j.Logger.class);

		when(executorMock.submit((java.util.concurrent.Callable<Logger>) any())).thenReturn(futureTaskMock);
		doThrow(TimeoutException.class).when(futureTaskMock).get(anyLong(), any(TimeUnit.class));

		ProcessLoggerProviderFactory.LOGGER = loggerMock;
		threadLocalProvider.tryToGetLogger(executorMock);

		verify(loggerMock).warn(anyString());
	}

	@Test
	public void testFlush() throws IOException, FileStorageException {
		org.slf4j.Logger loggerMock = mock(org.slf4j.Logger.class);
		DelegateExecution contextMock = mock(DelegateExecution.class);
		String processId = "11";

		ProcessLoggerProviderFactory.LOGGER = loggerMock;
		when(contextMock.getProcessInstanceId()).thenReturn(processId);
		when(contextMock.getVariable(Constants.VARIABLE_NAME_SPACE_ID)).thenReturn(null);

		threadLocalProvider.getLogger(processId, "-");
		processLoggerProviderFactory.flush(contextMock, processId);
		verify(loggerMock, atLeastOnce()).debug(anyString());
		verify(processLogsPersistenceServiceMock).saveLog(anyString(), anyString(), anyString());
	}

	@Test
	public void testAppend() throws IOException, FileStorageException {
		org.slf4j.Logger loggerMock = mock(org.slf4j.Logger.class);
		DelegateExecution contextMock = mock(DelegateExecution.class);
		String processId = "11";

		ProcessLoggerProviderFactory.LOGGER = loggerMock;
		when(contextMock.getProcessInstanceId()).thenReturn(processId);
		when(contextMock.getVariable(Constants.VARIABLE_NAME_SPACE_ID)).thenReturn(null);

		threadLocalProvider.getLogger(processId, "-");
		processLoggerProviderFactory.append(contextMock, processId);
		verify(loggerMock, atLeastOnce()).debug(anyString());
		verify(processLogsPersistenceServiceMock).appendLog(anyString(), anyString(), anyString());
	}
}
