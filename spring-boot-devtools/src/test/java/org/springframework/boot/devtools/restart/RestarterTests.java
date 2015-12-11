/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.devtools.restart;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ThreadFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.devtools.restart.classloader.ClassLoaderFile;
import org.springframework.boot.devtools.restart.classloader.ClassLoaderFile.Kind;
import org.springframework.boot.devtools.restart.classloader.ClassLoaderFiles;
import org.springframework.boot.test.OutputCapture;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests for {@link Restarter}.
 *
 * @author Phillip Webb
 */
public class RestarterTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Rule
	public OutputCapture out = new OutputCapture();

	@Before
	public void setup() {
		Restarter.setInstance(new TestableRestarter());
	}

	@After
	public void cleanup() {
		Restarter.clearInstance();
	}

	@Test
	public void cantGetInstanceBeforeInitialize() throws Exception {
		Restarter.clearInstance();
		this.thrown.expect(IllegalStateException.class);
		this.thrown.expectMessage("Restarter has not been initialized");
		Restarter.getInstance();
	}

	@Test
	public void testRestart() throws Exception {
		Restarter.clearInstance();
		Thread thread = new Thread() {

			@Override
			public void run() {
				SampleApplication.main();
			};

		};
		thread.start();
		Thread.sleep(2600);
		String output = this.out.toString();
		assertThat(StringUtils.countOccurrencesOf(output, "Tick 0"), greaterThan(1));
		assertThat(StringUtils.countOccurrencesOf(output, "Tick 1"), greaterThan(1));
		assertThat(TestRestartListener.restarts, greaterThan(0));
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void getOrAddAttributeWithNewAttribute() throws Exception {
		ObjectFactory objectFactory = mock(ObjectFactory.class);
		given(objectFactory.getObject()).willReturn("abc");
		Object attribute = Restarter.getInstance().getOrAddAttribute("x", objectFactory);
		assertThat(attribute, equalTo((Object) "abc"));
	}

	public void addUrlsMustNotBeNull() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Urls must not be null");
		Restarter.getInstance().addUrls(null);
	}

	@Test
	public void addUrls() throws Exception {
		URL url = new URL("file:/proj/module-a.jar!/");
		Collection<URL> urls = Collections.singleton(url);
		Restarter restarter = Restarter.getInstance();
		restarter.addUrls(urls);
		restarter.restart();
		ClassLoader classLoader = ((TestableRestarter) restarter)
				.getRelaunchClassLoader();
		assertThat(((URLClassLoader) classLoader).getURLs()[0], equalTo(url));
	}

	@Test
	public void addClassLoaderFilesMustNotBeNull() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("ClassLoaderFiles must not be null");
		Restarter.getInstance().addClassLoaderFiles(null);
	}

	@Test
	public void addClassLoaderFiles() throws Exception {
		ClassLoaderFiles classLoaderFiles = new ClassLoaderFiles();
		classLoaderFiles.addFile("f", new ClassLoaderFile(Kind.ADDED, "abc".getBytes()));
		Restarter restarter = Restarter.getInstance();
		restarter.addClassLoaderFiles(classLoaderFiles);
		restarter.restart();
		ClassLoader classLoader = ((TestableRestarter) restarter)
				.getRelaunchClassLoader();
		assertThat(FileCopyUtils.copyToByteArray(classLoader.getResourceAsStream("f")),
				equalTo("abc".getBytes()));
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void getOrAddAttributeWithExistingAttribute() throws Exception {
		Restarter.getInstance().getOrAddAttribute("x", new ObjectFactory<String>() {
			@Override
			public String getObject() throws BeansException {
				return "abc";
			}
		});
		ObjectFactory objectFactory = mock(ObjectFactory.class);
		Object attribute = Restarter.getInstance().getOrAddAttribute("x", objectFactory);
		assertThat(attribute, equalTo((Object) "abc"));
		verifyZeroInteractions(objectFactory);
	}

	@Test
	public void getThreadFactory() throws Exception {
		final ClassLoader parentLoader = Thread.currentThread().getContextClassLoader();
		final ClassLoader contextClassLoader = new URLClassLoader(new URL[0]);
		Thread thread = new Thread() {
			@Override
			public void run() {
				Runnable runnable = mock(Runnable.class);
				Thread regular = new Thread();
				ThreadFactory factory = Restarter.getInstance().getThreadFactory();
				Thread viaFactory = factory.newThread(runnable);
				// Regular threads will inherit the current thread
				assertThat(regular.getContextClassLoader(), equalTo(contextClassLoader));
				// Factory threads should should inherit from the initial thread
				assertThat(viaFactory.getContextClassLoader(), equalTo(parentLoader));
			};
		};
		thread.setContextClassLoader(contextClassLoader);
		thread.start();
		thread.join();
	}

	@Test
	public void getInitialUrls() throws Exception {
		Restarter.clearInstance();
		RestartInitializer initializer = mock(RestartInitializer.class);
		URL[] urls = new URL[] { new URL("file:/proj/module-a.jar!/") };
		given(initializer.getInitialUrls(any(Thread.class))).willReturn(urls);
		Restarter.initialize(new String[0], false, initializer, false);
		assertThat(Restarter.getInstance().getInitialUrls(), equalTo(urls));
	}

	@Component
	@EnableScheduling
	public static class SampleApplication {

		private int count;

		private static volatile boolean quit;

		@Scheduled(fixedDelay = 200)
		public void tickBean() {
			System.out.println("Tick " + this.count++ + " " + Thread.currentThread());
		}

		@Scheduled(initialDelay = 500, fixedDelay = 500)
		public void restart() {
			System.out.println("Restart " + Thread.currentThread());
			if (!SampleApplication.quit) {
				Restarter.getInstance().restart();
			}
		}

		public static void main(String... args) {
			Restarter.initialize(args, false, new MockRestartInitializer(), true,
					new TestRestartListener());
			AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
					SampleApplication.class);
			context.registerShutdownHook();
			System.out.println("Sleep " + Thread.currentThread());
			sleep();
			quit = true;
			context.close();
		}

		private static void sleep() {
			try {
				Thread.sleep(1200);
			}
			catch (InterruptedException ex) {
				// Ignore
			}
		}

	}

	private static class TestableRestarter extends Restarter {

		private ClassLoader relaunchClassLoader;

		TestableRestarter() {
			this(Thread.currentThread(), new String[] {}, false,
					new MockRestartInitializer());
		}

		protected TestableRestarter(Thread thread, String[] args,
				boolean forceReferenceCleanup, RestartInitializer initializer) {
			super(thread, args, forceReferenceCleanup, initializer);
		}

		@Override
		public void restart(FailureHandler failureHandler) {
			try {
				stop();
				start(failureHandler);
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		protected Throwable relaunch(ClassLoader classLoader) throws Exception {
			this.relaunchClassLoader = classLoader;
			return null;
		}

		@Override
		protected void stop() throws Exception {
		}

		public ClassLoader getRelaunchClassLoader() {
			return this.relaunchClassLoader;
		}

	}

	private static class TestRestartListener implements RestartListener {

		private static int restarts;

		@Override
		public void beforeRestart() {
			restarts++;
		}

	}

}
