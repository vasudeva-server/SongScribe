/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package songscribe.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import songscribe.UnitTest;
import songscribe.message.command.SaveCommand;

class MessageLoggerTest extends UnitTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        // Reset singleton so init() will create a fresh instance for each test
        MessageLogger.instance = null;

        // Attach a capturing appender at TRACE level to the MessageLogger logger
        logger = (Logger) LoggerFactory.getLogger(MessageLogger.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);

        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
        // Leave instance set so subsequent tests start clean via setUp's reset
        MessageLogger.instance = null;
    }

    // -------------------------------------------------------------------------
    // MessageLogger.init() — row 10
    // -------------------------------------------------------------------------

    @Test
    void testInitCreatesSingletonAndSubscribesToBus() {
        assertThat(MessageLogger.instance).isNull();

        MessageLogger.init();

        assertThat(MessageLogger.instance).isNotNull();

        // Posting a message must cause onMessage to fire → TRACE log entry
        MessageCenter.post(new SaveCommand());

        assertThat(appender.list)
            .anyMatch(e -> e.getLevel() == Level.TRACE);
    }

    @Test
    void testInitIsIdempotentSecondCallReturnsSameInstance() {
        MessageLogger.init();
        var first = MessageLogger.instance;

        MessageLogger.init();

        assertThat(MessageLogger.instance).isSameAs(first);
    }

    // -------------------------------------------------------------------------
    // MessageLogger.onMessage — row 11
    // -------------------------------------------------------------------------

    @Test
    void testOnMessageLogsMessageToStringAtTraceLevel() {
        MessageLogger.init();

        var command = new SaveCommand();
        MessageCenter.post(command);

        // The logged message must equal command.toString() (i.e., "SaveCommand")
        assertThat(appender.list)
            .anyMatch(e ->
                e.getLevel() == Level.TRACE
                    && e.getFormattedMessage().equals(command.toString()));
    }
}
