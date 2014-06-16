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
 *  This class descibes a layer of a world map. It controls in which data
 *  structure the places are stored
 */

package mudmap2.backend;

import java.util.TreeMap;

/**
 * A layer stores places relatively to each other by position on the map.
 * Each world can consist of multiple layers
 * 
 * @author neop
 */
public class Layer {
    
    int id;
    TreeMap<Integer, TreeMap<Integer, LayerElement>> elements;
    
    public Layer(int _id) {
        id = _id;
        elements = new TreeMap<Integer, TreeMap<Integer, LayerElement>>();
    }
    
    /**
     * Sets the element at a positin
     * @param x x coordinate
     * @param y y coordinate
     * @param element new element
     */
    public void put(LayerElement element, int x, int y) throws Exception{
        element.get_layer().remove(element);
        element.set_position(x, y, this);
        put(element);
    }
    
    /**
     * Adds an element to the layer, uses the position of the element
     * @param element element to be added
     */
    public void put(LayerElement element) throws Exception{
        if(exist(element.get_x(), element.get_y())) throw new Exception("Position " + element.get_x() + ", " + element.get_y() + " is already in use");
        // create map if it doesn't exist
        if(!elements.containsKey(element.get_x())) elements.put(element.get_x(), new TreeMap<Integer, LayerElement>());
        elements.get(element.get_x()).put(element.get_y(), element);
    }
    
    /**
     * Gets the element at a position
     * @param x x coordinate
     * @param y y coordinate
     * @return element at that position
     */
    public LayerElement get(int x, int y) throws PlaceNotFoundException{
        if(!exist(x, y)) throw new PlaceNotFoundException(x, y);
        return elements.get(x).get(y);
    }
    
    /**
     * Gets the id of the layer
     * @return layer id
     */
    public int get_id(){
        return id;
    }
    
    /**
     * Gets the id
     * @return layer id
     */
    @Override
    public String toString(){
        return "" + get_id();
    }
    
    /**
     * Removes an element from the layer
     * @param element 
     */
    public void remove(LayerElement element) throws RuntimeException, PlaceNotFoundException{
        if(element.get_layer() != this) throw new RuntimeException("Element not in this layer");
        if(get(element.get_x(), element.get_y()) != element) throw new RuntimeException("Element location mismatch (" + element.get_x() + ", " + element.get_y() + ")");
        elements.get(element.get_x()).remove(element.get_y());
    }
    
    /**
     * Returns true, if an element at position x,y exists
     * @param x x position
     * @param y < position
     * @return true, if an element exists
     */
    public boolean exist(int x, int y){
        if(!elements.containsKey(x) || !elements.get(x).containsKey(y)) return false;
        return true;
    }
    
    /**
     * This exception will be thrown, if a place doesn't exist at a certain position
     */
    public static class PlaceNotFoundException extends Exception {
        int x, y;
        
        /**
         * Constructs an exception
         * @param _x x coordinate of the place
         * @param _y y coordinate of the place
         */
        public PlaceNotFoundException(int _x, int _y) {
            x = _x; y = _y;
        }
        
        @Override
        public String toString(){
            return "Element at position " + x + ", " + y + " doesn't exist";
        }
    }
    
}
