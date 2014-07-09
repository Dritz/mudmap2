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
 *  This class describes a place in a world
 */

package mudmap2.backend;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.TreeMap;
import mudmap2.backend.Layer.PlaceNotFoundException;

/**
 * A place in the world
 * @author neop
 */
public class Place extends LayerElement implements Comparable<Place> {
    
    public static final String placeholder_name = "?";
    
    // next id to be assigned
    static int next_id;
    
    int id;
    String name;
    Area area;
    int rec_lvl_min, rec_lvl_max;
    RiskLevel risk_level;
    
    HashSet<Place> children, parents;
    HashSet<Path> connected_places;
    TreeMap<String, Boolean> flags;
    LinkedList<String> comments;
    
    public Place(int _id, String _name, int pos_x, int pos_y, Layer l){
        super(pos_x, pos_y, l);
        name = _name;
        id = _id;
        if(_id >= next_id) next_id++;
        
        initialize();
    }
    
    /**
     * Constructs new a place a certain position
     * @param _name name
     * @param pos_x x coordinate
     * @param pos_y y coordinate
     * @param l
     */
    public Place(String _name, int pos_x, int pos_y, Layer l){
        super(pos_x, pos_y, l);
        name = _name;
        id = next_id++;
        
        initialize();
    }
    
    /**
     * Initializes the place
     */
    private void initialize(){
        area = null;
        risk_level = null;
        rec_lvl_min = rec_lvl_max = -1;
        
        children = new HashSet<Place>();
        parents = new HashSet<Place>();
        connected_places = new HashSet<Path>();
        flags = new TreeMap<String, Boolean>();
        comments = new LinkedList<String>();
    }
    
    /**
     * Gets the place id
     * @return place id
     */
    public int get_id(){
        return id;
    }
    
    /**
     * Gets the name
     * @return name of the place
     */
    public String get_name(){
        return name;
    }
    
    /**
     * Sets the name
     * @param _name new name
     */
    public void set_name(String _name){
        name = _name;
    }
    
    /**
     * Gets the position of a place as world coordinate
     * @return place coordinate
     */
    public WorldCoordinate get_coordinate(){
        return new WorldCoordinate(get_layer().get_id(), get_x(), get_y());
    }
    
    /**
     * Gets the area
     * @return area
     */
    public Area get_area(){
        return area;
    }
    
    /**
     * Sets the area
     * @param _area 
     */
    public void set_area(Area _area) {
        area = _area;
    }
    
    /**
     * Gets the minimal recommended level
     * @return minimal recommended level
     */
    public int get_rec_lvl_min(){
        return rec_lvl_min;
    }
    
    /**
     * Sets the minimal recommended level
     * @param _rec_lvl_min 
     */
    public void set_rec_lvl_min(int _rec_lvl_min){
        rec_lvl_min = _rec_lvl_min;
    }
    
    /**
     * Gets the maximal recommended level
     * @return maximal recommended level
     */
    public int get_rec_lvl_max(){
        return rec_lvl_max;
    }
    
    /**
     * Sets the maximal recommended level
     * @param _rec_lvl_max 
     */
    public void set_rec_lvl_max(int _rec_lvl_max){
        rec_lvl_max = _rec_lvl_max;
    }
    
    /**
     * Gets the risk level
     * @return risk level
     */
    public RiskLevel get_risk_level(){
        return risk_level;
    }
    
    /**
     * sets the risk level
     * @param _risk_level
     */
    public void set_risk_level(RiskLevel _risk_level){
        risk_level = _risk_level;
    }
    
    /**
     * Adds a comment at the end of the list
     * @param comment
     */
    public void add_comment(String comment){
        comments.add(comment);
    }
    
    /**
     * removes all comments
     */
    public void delete_comments(){
        comments.clear();
    }
    
    /**
     * Gets the comments list
     * @return comments list
     */
    public LinkedList<String> get_comments(){
        return comments;
    }
    
    /**
     * Gets the comments as a single string
     * @param newlines insert \n at lineendings, if true
     * @return 
     */
    public String get_comments_string(boolean newlines){
        String ret = "";
        String lineending = newlines ? "\n" : " ";
        for(String c: comments) ret += (ret.length() == 0 ? "" : lineending) + c;
        return ret;
    }
    
    /**
     * Gets the path connected to an exit
     * @param dir exit direction
     * @return path connected to that exit
     */
    public Path get_exit(String dir){
        for(Path path: connected_places){
            if(path.get_exit(this).equals(dir)) return path;
        }
        return null;
    }
    
    /**
     * Gets the path to a place, if available
     * @param place a place that this place is connected to
     * @return path to place, if available
     */
    public HashSet<Path> get_paths(Place place){
        HashSet<Path> ret = new HashSet<Path>();
        for(Path path: connected_places)
            if(path.has_place(place)) ret.add(path);            
        return ret;
    }
    
    /**
     * Removes a path
     * @param dir1 exit direction on this place
     * @param other connected place
     * @param dir2 exit direction of the other place
     * @throws java.lang.Exception if path could not be removed
     */
    public void remove_path(String dir1, Place other, String dir2) throws Exception{
        boolean ok = false;
        for(Path path: get_paths(other)){
            if(path.get_exit(this).equals(dir1) && path.get_exit(other).equals(dir2)){
                connected_places.remove(path);
                other.connected_places.remove(path);
                ok = true;
            }
        }
        if(!ok) throw new RuntimeException("Couldn't remove path connection (" + this + " [" + dir1 + "] - " + other + " [" + dir2 + "]), path not found");
    }
    
    /**
     * Connects a place to another one tht is specified in path
     * If 'this place' is not in path an exception will be thrown
     * @param path
     * @return true, if successfully connected
     */
    public boolean connect_path(Path path) throws RuntimeException{
        Place[] pp = path.get_places();
        Place other;
        
        if(pp[0] == this) other = pp[1];
        else if(pp[1] == this) other = pp[0];
        else throw new RuntimeException("This place is not specified in given path");
        
        boolean exit_occupied = false;
        String exit_this = path.get_exit(this);
        
        // check if exit is already connected with path
        for(Path p: connected_places){
            if(p.get_exit(this).equals(exit_this)){
                exit_occupied = true;
                break;
            }
        }
        if(!exit_occupied){
            exit_this = path.get_exit(other);
            for(Path p: other.connected_places){
                if(p.get_exit(other).equals(exit_this)){
                    exit_occupied = true;
                    break;
                }
            }
        
            if(!exit_occupied){
                connected_places.add(path);
                other.connected_places.add(path);
            }
        }
        return !exit_occupied;
    }
    
    /**
     * Gets all paths
     * @return all paths
     */
    public HashSet<Path> get_paths(){
        return connected_places;
    }
    
    /**
     * Gets a flag value
     * @param key flag name
     * @return flag value
     */
    public boolean get_flag(String key){
        if(flags.containsKey(key)) return flags.get(key);
        return false;
    }
    
    /**
     * Sets a string flag
     * @param key flag name
     * @param state value
     */
    public void set_flag(String key, boolean state){
        flags.put(key, state);
    }
    
    /**
     * Gets the flags of a place
     * @return 
     */
    public TreeMap<String, Boolean> get_flags(){
        return flags;
    }
    
    /**
     * Connects a place as child, this place will be added to it as parent
     * @param p 
     */
    public void connect_child(Place p){
        children.add(p);
        p.parents.add(this);
    }
    
    /**
     * Removes a parent - child connection (subarea)
     * @param child child to be removed
     */
    public void remove_child(Place child){
        children.remove(child);
        child.parents.remove(this);
    }
    
    /**
     * Gets the child places / subareas
     * @return 
     */
    public HashSet<Place> get_children(){
        return children;
    }
    
    /**
     * Gets the name
     * @return name of he place
     */
    @Override
    public String toString(){
        return name;
    }
    
    /**
     * Compares two places by their name
     * @param arg0
     * @return 
     */
    @Override
    public int compareTo(Place arg0) {
        if(arg0 == null) throw new NullPointerException();
        return (this.id == arg0.id) ? 0 : 1;
    }

    /**
     * Removes all connections to other places (paths, child-connections)
     */
    void remove_connections() {
        // remove paths (buffer, becaus connected_places will be modified
        HashSet<Path> cp_buffer = (HashSet<Path>) connected_places.clone();
        for(Path p: cp_buffer) p.remove();
        // remove connection to sub-areas (children / parents)
        for(Place pl: children) pl.parents.remove(this);
        for(Place pl: parents) pl.children.remove(this);
    }

    /**
     * Removes the place from its layer
     * @throws RuntimeException
     * @throws mudmap2.backend.Layer.PlaceNotFoundException 
     */
    public void remove() throws RuntimeException, PlaceNotFoundException {
        get_layer().get_world().remove(this);
    }
}
