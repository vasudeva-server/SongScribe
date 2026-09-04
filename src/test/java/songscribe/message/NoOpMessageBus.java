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

import net.engio.mbassy.bus.IMessagePublication;
import net.engio.mbassy.bus.MBassador;

/**
 * A bus that delivers nothing. {@link MessageCenterTestHelper} puts one in force between test
 * classes, so a post from something a finished class leaked — queued EDT work, a background
 * thread — reaches no listener instead of the listeners of whichever bus happens to be in force.
 * <p>
 * Only synchronous publication is overridden, because {@link MessageCenter#post} is synchronous
 * and is the only way anything in the codebase posts.
 */
final class NoOpMessageBus extends MBassador<Message> {

    NoOpMessageBus() {
        super(MessageCenter::exitOnPublicationError);
    }

    /**
     * @return the publication MBassador would have executed, unexecuted
     * @effects nothing: no handler runs
     */
    @Override
    public IMessagePublication publish(Message message) {
        return createMessagePublication(message);
    }
}
