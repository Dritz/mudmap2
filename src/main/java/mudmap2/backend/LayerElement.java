/*  MUD Map (v2) - A tool to create and organize maps for text-based games
 *  Copyright (C) 2014  Neop (email: mneop@web.de)
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

/*  File description
 *
 *  This class is is an subelement of the place data structure used in Layer,
 *  Places are derived from this class
 */

package mudmap2.backend;

/**
 * An element of a layer
 * @author neop
 */
public interface LayerElement {

    /**
     * Gets the x position
     * @return x position
     */
    public int getX();
    
    /**
     * Gets the y position
     * @return y position
     */
    public int getY();
    
    /**
     * Gets the layer
     * @return layer
     */
    public Layer getLayer();
    
    /**
     * Sets the position
     * @param x x position
     * @param y y position
     * @param layer layer
     */
    void setPosition(int x, int y, Layer layer);
    
}