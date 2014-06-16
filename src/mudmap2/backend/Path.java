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
 *  Describes a path connection between two places (which two places and exits
 *  are used)
 */

package mudmap2.backend;

/**
 * Describes a way direction between two places
 * 
 * @author neop
 */
public class Path {
    private Place[] places;
    private String[] exitdirections;
    
    /**
     * Constructs a new path between two places
     * 
     * @param pl1 A place to connect
     * @param exitdir1 the exit of place 1 to be used
     * @param pl2 the other place
     * @param exitdir2 exit of place 2
     */
    public Path(Place pl1, String exitdir1, Place pl2, String exitdir2) {
        places = new Place[2];
        places[0] = pl1;
        places[1] = pl2;
        
        exitdirections = new String[2];
        exitdirections[0] = exitdir1;
        exitdirections[1] = exitdir2;
    }
    
    /**
     * Gets the connected places
     * 
     * @return the two connected places
     */
    public Place[] get_places(){
        return places;
    }
    
    /**
     * Checks whether a certain place is connected with this path
     * @param place
     * @return true if place is in this path
     */
    public boolean has_place(Place place){
        if(places[0] == place || places[1] == place) return true;
        return false;
    }
    
    /**
     * Gets the exit directions
     * 
     * @return The two exit directions
     */
    public String[] get_exit_directions(){
        return exitdirections;
    }
    
    /**
     * Gets the exit direction of a place p used in this path
     * @param p
     * @return the exit direction of p in the path
     * @throws RuntimeException if the place isn't a member of the path
     */
    public String get_exit(Place p) throws RuntimeException{
        if(places[0] == p) return exitdirections[0];
        else if(places[1] == p) return exitdirections[1];
        else throw new RuntimeException("Place not found in path");
    }
    
    /**
     * Gets the place that is not equal to p in a path
     * @param p
     * @return place of path that is not p
     * @throws RuntimeException 
     */
    public Place get_other_place(Place p) throws RuntimeException{
        if(places[0] == p) return places[1];
        else if(places[1] == p) return places[0];
        else throw new RuntimeException("Place not found in path");
    }
}