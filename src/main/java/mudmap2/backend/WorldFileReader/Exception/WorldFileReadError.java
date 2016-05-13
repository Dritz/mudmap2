/*  MUD Map (v2) - A tool to create and organize maps for text-based games
 *  Copyright (C) 2016  Neop (email: mneop@web.de)
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package mudmap2.backend.WorldFileReader.Exception;

/**
 * This Error will be thrown if an error occured while world file reading
 * @author neop
 */
public class WorldFileReadError extends WorldFileException {
    private static final long serialVersionUID = 1L;

    public WorldFileReadError(String file, String message, Throwable cause) {
        super(file, message, cause);
    }

    public WorldFileReadError(String file, Throwable cause) {
        super(file, "error in world file", cause);
    }

}
