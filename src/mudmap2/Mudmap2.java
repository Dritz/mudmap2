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
 *  Main class, it creates the Mainwindow and provides version information
 */

package mudmap2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import mudmap2.backend.Layer;
import mudmap2.backend.Path;
import mudmap2.backend.Place;
import mudmap2.frontend.Mainwindow;

/**
 *
 * @author neop
 */
public class Mudmap2 {
    static Mainwindow mwin;
    
    static final int version_major = 2;
    static final int version_minor = 0;
    static final int version_build = 1;
    static final String version_state = "beta";
    
    static boolean portable_mode = false;
    
    // copy-paste data
    // places to be copied or cut
    static HashSet<Place> copy_places;
    static int copy_dx, copy_dy;
    // if true, the places will be copied instead of cut
    static boolean copy_not_cut;
    // locations of copied places relative to cursor (places will be inserted here)
    static HashSet<Pair<Integer, Integer>> copy_place_locations;
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            // TODO code application logic here
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Mudmap2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(Mudmap2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(Mudmap2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedLookAndFeelException ex) {
            Logger.getLogger(Mudmap2.class.getName()).log(Level.SEVERE, null, ex);
        }
                
        mwin = new Mainwindow();
    }
    
    // ========================== Version info =================================
    
    /**
     * Gets the version major number
     * @return major version
     */
    public static int get_version_major(){
        return version_major;
    }
    
    /**
     * Gets the version minor number
     * @return minor version
     */
    public static int get_version_minor(){
        return version_minor;
    }
    
    /**
     * Gets the version build number
     * @return build version
     */
    public static int get_version_build(){
        return version_build;
    }
    
    /**
     * Gets the version state (eg. "alpha" or "beta")
     * @return version state
     */
    public static String get_version_state(){
        return version_state;
    }
    
    /**
     * Gets the version number as a string
     * @return major, minor and build version, separated b '.'
     */
    public static String get_version(){
        return version_major + "." + version_minor + "." + version_build;
    }
    
    // ========================== copy-paste ===================================
    
    /**
     * Cuts the places relatively to x, y
     * @param places 
     * @param x 
     * @param y 
     */
    public static void copy(HashSet<Place> places, int x, int y){
        reset_copy();
        
        copy_places = (HashSet<Place>) places.clone();
        copy_not_cut = true;
        copy_dx = x;
        copy_dy = y;
        generate_locations(x, y);
    }
    
    /**
     * Copies the places relatively to x, y
     * @param places 
     * @param x 
     * @param y 
     */
    public static void cut(HashSet<Place> places, int x, int y){
        reset_copy();
        
        copy_places = (HashSet<Place>) places.clone();
        copy_not_cut = false;
        copy_dx = x;
        copy_dy = y;
        generate_locations(x, y);
    }
    
    /**
     * Calculates copy_place_locations relatively to x, y
     */
    private static void generate_locations(int x, int y){
        copy_place_locations = new HashSet<Pair<Integer, Integer>>();
        for(Place place: copy_places) copy_place_locations.add(new Pair<Integer, Integer>(place.get_x() - x, place.get_y() - y));
    }
    
    /**
     * Checks whether the places can be pasted relatively to x,y on layer
     * @param x
     * @param y
     * @param layer
     * @return 
     */
    public static boolean can_paste(int x, int y, Layer layer){
        if(copy_places != null && copy_place_locations != null && layer != null){
            for(Pair<Integer, Integer> coordinate: copy_place_locations)
                if(layer.exist(x + coordinate.first, y + coordinate.second)) return false;
        }
        return true;
    }
    
    /**
     * Pastes the cut / copied places to layer, if possible
     * @param x
     * @param y
     * @param layer 
     * @return false on error or user abort
     */
    public static boolean paste(int x, int y, Layer layer){
        if(!can_paste(x, y, layer)) return false;
        
        // ask user
        String title = (copy_not_cut ? "Copy " : "Paste ") + "place(s)";
        String message = title + "? This can not be undone!" 
                + (copy_places.iterator().next().get_layer().get_world() != layer.get_world() ? " Pasting to another world might cause problems!" : "");
        int ret = JOptionPane.showConfirmDialog(mwin, message, title, JOptionPane.YES_NO_OPTION);
        if(ret == JOptionPane.YES_OPTION){
            // map to translate from old to new place
            HashMap<Place, Place> place_to_new_place = new HashMap<Place, Place>();
            
            // copy places
            for(Place place: copy_places){
                try {
                    if(place.get_layer().get_world() != layer.get_world()){
                        if(place.get_area() != null && !layer.get_world().get_areas().contains(place.get_area())) layer.get_world().add_area(place.get_area());
                    }
                    if(copy_not_cut){ // copy places -> duplicate on new layer
                        Place new_place = place.duplicate();
                        place_to_new_place.put(place, new_place);
                        layer.get_world().put(new_place, layer.get_id(), place.get_x() - copy_dx + x, place.get_y() - copy_dy + y);
                    } else {
                        layer.get_world().put(place, layer.get_id(), place.get_x() - copy_dx + x, place.get_y() - copy_dy + y);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(Mudmap2.class.getName()).log(Level.SEVERE, null, ex);
                    return false;
                }
            }
            
            // recreate paths and subareas after copy-paste
            if(copy_not_cut){
                for(Place place: copy_places){
                    Place new_place = place_to_new_place.get(place);
                    // connect paths
                    for(Path path: place.get_paths()){
                        // only check first place, because the other side will
                        // check itself
                        Place path_end_place = path.get_places()[0];
                        // if end place is not this place and is also copied
                        if(path_end_place != place && copy_places.contains(path_end_place)){
                            Place other_new_place = place_to_new_place.get(path_end_place);
                            new_place.connect_path(new Path(other_new_place, path.get_exit_directions()[0], new_place, path.get_exit_directions()[1]));
                        }
                    }
                    // connect children
                    for(Place child: place.get_children()){
                        // if child is copied, too
                        if(copy_places.contains(child)){
                            Place new_child = place_to_new_place.get(child);
                            new_place.connect_child(new_child);
                        }
                    }
                }
            // moving places modifies their coordinates so that they cant be pasted again
            } else reset_copy();
        }
        
        // cleanup
        //copy_places = null;
        //copy_place_locations = null;
        // don't clean up but change cut to copy
        return true;
    }
    
    /**
     * Returns true, if there are places to paste
     * @return 
     */
    public static boolean has_copy_places(){
        return copy_places != null;
    }
    
    /**
     * Gets the places to cut / copy
     * @return places or null
     */
    public static HashSet<Place> get_copy_places(){
        return copy_places;
    }
    
    /**
     * Gets the locations of the copied places relatively to the cursor
     * @return locations or null
     */
    public static HashSet<Pair<Integer, Integer>> get_copy_place_locations(){
        return copy_place_locations;
    }
    
    /**
     * Gets the layer of the copied places
     * @return layer or null
     */
    public static Layer get_copy_place_layer(){
        if(copy_places.isEmpty()) return null;
        return copy_places.iterator().next().get_layer();
    }
    
    /**
     * Removes all places from the copy/cut list
     */
    public static void reset_copy(){
        // cleanup
        copy_places = null;
        copy_place_locations = null;
    }
}
