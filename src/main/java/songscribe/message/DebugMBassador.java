/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.message;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import net.engio.mbassy.bus.publication.SyncAsyncPostCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link MBassador} that logs every message as it is posted, before any handler runs.
 * <p>
 * The entry point installs this bus in place of a plain one when the process runs in debug
 * mode, so the log shows each post in the order it was made, whether or not anything was
 * subscribed to it.
 */
public final class DebugMBassador<T> extends MBassador<T> {

    private static final Logger LOG = LoggerFactory.getLogger(DebugMBassador.class);

    public DebugMBassador(IPublicationErrorHandler errorHandler) {
        super(errorHandler);
    }

    /**
     * @log {@code message} at debug level, before delivery begins
     */
    @Override
    public SyncAsyncPostCommand<T> post(T message) {
        LOG.debug("{}", message);
        return super.post(message);
    }
}
