/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.slf4j.impl;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtLogRecord.FormatStyle;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextSelector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.helpers.BasicMarkerFactory;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggerTestCase {
    private static final LogContext LOG_CONTEXT = LogContext.create();
    private static final java.util.logging.Logger ROOT = LOG_CONTEXT.getLogger("");
    private static final QueueHandler HANDLER = new QueueHandler();

    private static final LogContextSelector DEFAULT_SELECTOR = LogContext.getLogContextSelector();

    @BeforeAll
    public static void configureLogManager() {
        LogContext.setLogContextSelector(() -> LOG_CONTEXT);
        ROOT.addHandler(HANDLER);
    }

    @AfterAll
    public static void cleanup() throws Exception {
        LOG_CONTEXT.close();
        LogContext.setLogContextSelector(DEFAULT_SELECTOR);
    }

    @AfterEach
    public void clearHandler() {
        HANDLER.close();
    }

    @Test
    public void testLogger() {
        final Logger logger = LoggerFactory.getLogger(LoggerTestCase.class);
        Assertions.assertTrue(logger instanceof Slf4jLogger, expectedTypeMessage(Slf4jLogger.class, logger.getClass()));

        // Ensure the logger logs something
        final String testMsg = "This is a test message";
        logger.info(testMsg);
        ExtLogRecord record = HANDLER.messages.poll();
        Assertions.assertNotNull(record);
        Assertions.assertEquals(testMsg, record.getMessage());
        Assertions.assertNull(record.getParameters());

        // Test a formatted message
        logger.info("This is a test formatted {}", "{message}");
        record = HANDLER.messages.poll();
        Assertions.assertNotNull(record);
        Assertions.assertEquals("This is a test formatted {message}", record.getFormattedMessage());
        Assertions.assertArrayEquals(new Object[] { "{message}" }, record.getParameters(), "Expected parameter not found.");
    }

    @Test
    public void testLoggerWithExceptions() {
        final Logger logger = LoggerFactory.getLogger(LoggerTestCase.class);

        final RuntimeException e = new RuntimeException("Test exception");
        final String testMsg = "This is a test message";
        logger.info(testMsg, e);
        LogRecord record = HANDLER.messages.poll();
        Assertions.assertNotNull(record);
        Assertions.assertEquals(testMsg, record.getMessage());
        Assertions.assertEquals(e, record.getThrown(), "Cause is different from the expected cause");

        // Test format with the last parameter being the throwable which should set be set on the record
        logger.info("This is a test formatted {}", "{message}", e);
        record = HANDLER.messages.poll();
        Assertions.assertNotNull(record);
        Assertions.assertEquals("This is a test formatted {message}", record.getMessage());
        Assertions.assertEquals(e, record.getThrown(), "Cause is different from the expected cause");
    }

    @Test
    public void testLoggerWithMarkers() {
        final Logger logger = LoggerFactory.getLogger(LoggerTestCase.class);
        final Marker marker = new BasicMarkerFactory().getMarker("test");

        logger.info(marker, "log message");
        LogRecord record = HANDLER.messages.poll();
        Assertions.assertNotNull(record);
        // TODO: record.getMarker() must be same instance of "marker"
    }

    @Test
    public void testMDC() {
        Assertions.assertSame(MDC.getMDCAdapter()
                .getClass(), Slf4jMDCAdapter.class,
                expectedTypeMessage(Slf4jMDCAdapter.class, MDC.getMDCAdapter()
                        .getClass()));
        final String key = Long.toHexString(System.currentTimeMillis());
        MDC.put(key, "value");
        Assertions.assertEquals("value", MDC.get(key), "MDC value should be \"value\"");
        Assertions.assertEquals("value", org.jboss.logmanager.MDC.get(key), "MDC value should be \"value\"");
    }

    @Test
    public void testLoggerNoFormat() {
        final Logger logger = LoggerFactory.getLogger(LoggerTestCase.class);
        final Marker marker = new BasicMarkerFactory().getMarker("test");

        logger.info(marker, "log message {foo}");
        LogRecord record = HANDLER.messages.poll();
        Assertions.assertEquals(FormatStyle.NO_FORMAT, ((ExtLogRecord) record).getFormatStyle());
    }

    private static Supplier<String> expectedTypeMessage(final Class<?> expected, final Class<?> found) {
        return () -> String.format("Expected type %s but found type %s", expected.getName(), found.getName());
    }

    private static class QueueHandler extends ExtHandler {
        final BlockingDeque<ExtLogRecord> messages = new LinkedBlockingDeque<>();

        @Override
        protected void doPublish(final ExtLogRecord record) {
            messages.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
            messages.clear();
            setLevel(Level.ALL);
        }
    }
}
