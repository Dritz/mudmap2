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
 *  This class displays a world and the GUI elements that belong to it. It also
 *  processes keyboard commands. The class is supposed to be a tab in Mainwindow.
 *  It reads and writes the world meta (*_meta) files
 */

package mudmap2.frontend;

import java.awt.BorderLayout;
import java.awt.Event;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import mudmap2.Paths;
import mudmap2.backend.Layer;
import mudmap2.backend.Layer.PlaceNotFoundException;
import mudmap2.backend.Path;
import mudmap2.backend.Place;
import mudmap2.backend.World;
import mudmap2.backend.WorldCoordinate;
import mudmap2.backend.WorldManager;
import mudmap2.frontend.GUIElement.ScrollLabel;
import mudmap2.frontend.dialog.AreaDialog;
import mudmap2.frontend.dialog.OpenWorldDialog;
import mudmap2.frontend.dialog.PathConnectDialog;
import mudmap2.frontend.dialog.PathConnectNeighborsDialog;
import mudmap2.frontend.dialog.PlaceCommentDialog;
import mudmap2.frontend.dialog.PlaceDialog;
import mudmap2.frontend.dialog.PlaceListDialog;
import mudmap2.frontend.dialog.PlaceRemoveDialog;
import mudmap2.frontend.dialog.PlaceSelectionDialog;

/**
 * A tab in the main window that displays a world
 * 
 * @author neop
 */
public class WorldTab extends JPanel {
    
    World world;
    
    static boolean show_paths_curved = true;
    
    // GUI elements
    JFrame parent;
    WorldPanel worldpanel;
    JSlider slider_zoom;
    JPanel panel_south;
    ScrollLabel label_infobar;
    
    // history of shown position
    LinkedList<WorldCoordinate> positions;
    int positions_cur_index; // index of currently shown position
    // max amount of elements in the list
    static final int history_max_length = 25;
    
    // true, if the mouse is in the panel, for relative motion calculation
    boolean mouse_in_panel;
    // previous position of the mouse
    int mouse_x_previous, mouse_y_previous;
    
    // the position of the selected place (selected by mouse or keyboard)
    static boolean place_selection_enabled_default = true; // default value
    boolean cursor_enabled;
    int cursor_x, cursor_y;
    boolean force_selection;
    
    // world_meta file version supported by this WorldTab
    static final int meta_file_ver_major = 1;
    static final int meta_file_ver_minor = 1;
    
    // tile size in pixel
    double tile_size;
    public static final int tile_size_min = 10;
    public static final int tile_size_max = 200;
    
    // true, if a context menu is shown (to disable forced focus)
    boolean is_context_menu_shown;
    boolean forced_focus_disabled;
    
    // passive worldtabs don't modify the world
    final boolean passive;
    
    LinkedList<CursorListener> cursor_listeners;
    
    // place (group) selection
    WorldCoordinate place_group_box_start, place_group_box_end;
    HashSet<Place> place_group;

    // ============================= Methods ===================================
    
    /**
     * Constructs the world tab, opens the world if necessary
     * @param parent parent frame
     * @param world_file file of the world
     * @param passive if true, everything, that modifies the world, is disabled
     */
    public WorldTab(JFrame parent, String world_file, boolean passive){
        this.parent = parent;
        world = WorldManager.get_world(world_file);
        this.passive = passive;
        create();
    }
    
    /**
     * Constructs the world tab, opens the world if necessary
     * @param parent parent frame
     * @param world world
     * @param passive if true, everything, that modifies the world, is disabled
     */
    public WorldTab(JFrame parent, World world, boolean passive){
        this.parent = parent;
        this.world = world;
        this.passive = passive;
        create();
    }
    
    /**
     * Copies a WorldTab and creates a new passive one
     * @param wt 
     */
    public WorldTab(WorldTab wt){
        parent = wt.get_parent();
        passive = true;
        world = wt.get_world();
        create_variables();
        
        tile_size = wt.tile_size;
        cursor_enabled = wt.cursor_enabled;
        // copy positions
        for(WorldCoordinate pos: wt.positions) positions.add(pos);
        
        create_gui();
    }
    
    /**
     * Clones the WorldTab
     * @return 
     */
    @Override
    public Object clone(){
        return new WorldTab(this);
    }
    
    /**
     * Creates the WorldTab from scratch
     */
    private void create(){
        create_variables();
        load_meta();
        create_gui();
    }
    
    /**
     * Sets the initial values of the member variables
     */
    private void create_variables(){
        positions = new LinkedList<WorldCoordinate>();
        tile_size = 120;
        
        is_context_menu_shown = false;
        forced_focus_disabled = true;

        mouse_in_panel = false;
        mouse_x_previous = mouse_y_previous = 0;
        
        force_selection = false;
        cursor_enabled = place_selection_enabled_default;
        
        place_group = new HashSet<Place>();
    }
    
    /**
     * Creates the GUI elements
     */
    private void create_gui(){
        setLayout(new BorderLayout());
        
        worldpanel = new WorldPanel(this, passive);
        add(worldpanel, BorderLayout.CENTER);
                        
        add(panel_south = new JPanel(), BorderLayout.SOUTH);
        panel_south.setLayout(new GridBagLayout());
        
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(1, 2, 0, 2);
                
        // add bottom panel elements
        // previous / next buttons for the history
        JButton button_prev = new JButton("Prev");
        constraints.gridx++;
        panel_south.add(button_prev, constraints);
        button_prev.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                pop_position();
            }
        });

        JButton button_next = new JButton("Next");
        constraints.gridx++;
        panel_south.add(button_next, constraints);
        button_next.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                restore_position();
            }
        });
        
        JButton button_list = new JButton("List");
        constraints.gridx++;
        panel_south.add(button_list, constraints);
        button_list.addActionListener(new PlaceListDialog(this, passive)); // passive WorldTab creates modal PlaceListDialogs
        
        JTextField textfield_search = new JTextField("Search");
        constraints.gridx++;
        panel_south.add(textfield_search, constraints);
        textfield_search.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                String[] keywords = ((JTextField) arg0.getSource()).getText().toLowerCase().split(" ");
                ArrayList<Place> found_places = new ArrayList<Place>();
                
                // search
                for(Place pl: get_world().get_places())
                    if(pl.match_keywords(keywords)) found_places.add(pl);
                
                // display
                if(found_places.isEmpty()) JOptionPane.showMessageDialog(parent, "No places found!", "Search - " + get_world().get_name(), JOptionPane.PLAIN_MESSAGE);
                else (new PlaceListDialog(WorldTab.this, found_places, passive)).setVisible(true);
            }
        });
        
        
        constraints.gridx++;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        panel_south.add(label_infobar = new ScrollLabel(), constraints);
        label_infobar.start_thread();
        
        // set default selected place to hte center place
        cursor_x = (int) Math.round(get_cur_position().get_x());
        cursor_y = (int) Math.round(get_cur_position().get_y());
        
        slider_zoom = new JSlider(0, 100, (int) (100.0 / tile_size_max * tile_size));
        constraints.gridx++;
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        panel_south.add(slider_zoom, constraints);
        slider_zoom.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent arg0) {
                set_tile_size((int) ((double) tile_size_max * ((JSlider) arg0.getSource()).getValue() / 100.0));
            }
        });
        // ---
        
        cursor_listeners = new LinkedList<CursorListener>();
    }
    
    /**
     * Gets the parent frame
     * @return 
     */
    public JFrame get_parent(){
        return parent;
    }
    
    /**
     * Closes the tab
     */
    public void close(){
        if(parent instanceof Mainwindow){
            int ret = JOptionPane.showConfirmDialog(this, "Save world \"" + get_world().get_name() + "\"?", "Save world", JOptionPane.YES_NO_OPTION);
            if(ret == JOptionPane.YES_OPTION) save();
            WorldManager.close_world(world.get_file());
            ((Mainwindow) parent).remove_tab(this);
        }
    }
    
    /**
     * Gets the world
     * @return world
     */
    public World get_world(){
        return world;
    }
    
    /**
     * Gets the panel width / width of the actually drawn map
     * @return 
     */
    public int get_panel_width(){
        return (int) worldpanel.get_screen_width();
    }
    
    /**
     * Gets the panel height / height of the actually drawn map
     * @return 
     */
    public int get_panel_height(){
        return (int) worldpanel.get_screen_height();
    }
    
    /**
     * Returns true if curved path lines are enabled
     * @return 
     */
    public static boolean get_show_paths_curved(){
        return show_paths_curved;
    }
    
    /**
     * Enables or disables curved path lines
     * @param b 
     */
    public static void set_show_paths_curved(boolean b){
        show_paths_curved = b;
    }
    
    /**
     * Saves the changes in the world
     */
    public void save(){
        if(!passive){
            write_meta();
            world.write_world();
        }
        show_message("World saved");
    }
    
    /**
     * Show message in infobar
     * @param message 
     */
    public void show_message(String message){
        label_infobar.show_message(message);
    }
    
    // ========================== Place selection ==============================
    /**
     * Gets the x coordinate of the selected place
     * @return x coordinate
     */
    public int get_cursor_x(){
        return cursor_x;
    }
    
    /**
     * Gets the y coordinate of the selected place 
     * @return y coordinate
     */
    public int get_cursor_y(){
        return cursor_y;
    }
    
    /**
     * Sets the coordinates of the selected place
     * @param x x coordinate
     * @param y y coordinate
     */
    public void set_cursor(int x, int y){
        cursor_x = x;
        cursor_y = y;
        update_infobar();
        move_screen_to_cursor();
        repaint();
        call_cursor_listeners();
    }
    
    /**
     * Moves the place selection coordinates
     * @param dx x movement
     * @param dy y movement
     */
    private void move_cursor(int dx, int dy){
        cursor_x += dx;
        cursor_y += dy;
        update_infobar();
        move_screen_to_cursor();
        repaint();
        call_cursor_listeners();
    }
    
    /**
     * moves the shown places so the selection is on the screen
     */
    private void move_screen_to_cursor(){
        if(worldpanel != null){
            int screen_x = worldpanel.get_screen_pos_x(cursor_x);
            int screen_y = worldpanel.get_screen_pos_y(cursor_y);
            int tilesize = get_tile_size();

            double dx = 0, dy = 0;

            if(screen_x < 0) dx = (double) screen_x / tilesize;
            else if(screen_x > worldpanel.screen_width - tilesize) dx = (double) (screen_x - worldpanel.screen_width) / tilesize + 1;
            if(screen_y < 0) dy = (double) -screen_y / tilesize;
            else if(screen_y > worldpanel.screen_height - tilesize) dy = (double) -(screen_y - worldpanel.screen_height) / tilesize - 1;

            if(dx != 0 || dy != 0) get_cur_position().move(dx, dy);
            repaint();
        }
    }
    
    /**
     * Sets the cursor state (if true, the selection will be shown)
     * @param b 
     */
    public void set_cursor_enabled(boolean b){
        cursor_enabled = b || force_selection;
        update_infobar();
        repaint();
    }
    
    /**
     * Toggles the cursor enabled state
     */
    public void set_cursor_toggle(){
        if(!force_selection){
            cursor_enabled = !cursor_enabled;
            update_infobar();
            repaint();
        }
    }
    
    /**
     * Gets the cursor state
     * @return 
     */
    public boolean get_cursor_enabled(){
        return cursor_enabled || force_selection;
    }
    
    /**
     * Enables or disables the cursor
     * @param b
     */
    private void set_cursor(boolean b){
        cursor_enabled = b || force_selection;
        repaint();
    }
    
    /**
     * Forces the cursor to be enabled, if true
     * @param b 
     */
    public void set_cursor_forced(boolean b){
        if(force_selection = b) set_cursor(true);
    }
    
    /**
     * Gets the currently shown position
     * @return current position
     */
    public WorldCoordinate get_cur_position(){
        return positions.get(positions_cur_index);
        //return positions.getFirst();
    }
    
    /**
     * Pushes a new position on the position stack ("goto")
     * @param _pos new position
     */
    public void push_position(WorldCoordinate _pos){
        WorldCoordinate pos = _pos.clone();
        // remove all entries after the current one
        while(positions_cur_index > 0){
            positions.pop();
            positions_cur_index--;
        }
        // add new position
        positions.push(pos);
        
        // move place selection
        set_cursor((int) pos.get_x(), (int) pos.get_y());
        while(positions.size() > history_max_length) positions.removeLast();
        repaint();
    }
    
    /**
     * Removes the first position from the position stack,
     * go to home position if the stack is empty
     */
    public void pop_position(){
        // if end not reached
        if(positions_cur_index < positions.size() - 1) positions_cur_index++;
        // add home coord at list end (unlike goto_home())
        else positions.addLast(get_world().get_home());
        
        //if(positions.size() > 0) positions.removeFirst();
        //if(positions.size() == 0) goto_home();
        
        set_cursor((int) get_cur_position().get_x(), (int) get_cur_position().get_y());
        repaint();
    }
    
    public void restore_position(){
        if(positions_cur_index > 0){
            positions_cur_index--;
            set_cursor((int) get_cur_position().get_x(), (int) get_cur_position().get_y());
            repaint();
        }
    }
    
    /**
     * Updates the infobar
     */
    private void update_infobar(){
        if(label_infobar != null ){ 
            if(get_cursor_enabled()){
                Layer layer = world.get_layer(get_cur_position().get_layer());
                if(layer != null && layer.exist(get_cursor_x(), get_cursor_y())){
                    Place pl;
                        pl = (Place) layer.get(get_cursor_x(), get_cursor_y());

                        boolean has_area = pl.get_area() != null;
                        boolean has_comments = pl.get_comments().size() != 0;

                        String infotext = pl.get_name();
                        if(has_area || has_comments) infotext += " (";
                        if(has_area) infotext += pl.get_area().get_name();
                        if(has_comments) infotext += (has_area ? ", " : "") + pl.get_comments_string(false);
                        if(has_area || has_comments) infotext += ")";

                        label_infobar.setText(infotext);
                } else {
                    label_infobar.setText("");
                }
            } else label_infobar.setText("");
        }
    }
    
    /**
     * Removes all previously visited positions from history and sets pos
     * @param pos new position
     */
    public void reset_history(WorldCoordinate pos){
        positions.clear();
        positions.add(pos);
        cursor_x = (int) Math.round(pos.get_x());
        cursor_y = (int) Math.round(pos.get_y());
        update_infobar();
        repaint();
    }
    
    /**
     * Go to the home position
     */
    public void goto_home(){
        push_position(world.get_home());
        set_cursor((int) Math.round(get_cur_position().get_x()), (int) Math.round(get_cur_position().get_y()));
    }
    
    /**
     * Sets a new home position
     */
    public void set_home(){
        world.set_home(get_cur_position().clone());
    }
    
    /**
     * Gets a place on the current layer
     * @param x x coordinate
     * @param y y coordinate
     * @return place or null
     */
    public Place get_place(int x, int y){
        Place ret = null;
        Layer layer = world.get_layer(get_cur_position().get_layer());
        if(layer != null) ret = (Place) layer.get(x, y);
        return ret;
    }
    
    /**
     * Gets the selected place or null
     * @return place or null
     */
    public Place get_selected_place(){
        return get_place(get_cursor_x(), get_cursor_y());
    }
    
    /**
     * Gets the current tile size
     * @return tile size
     */
    public int get_tile_size(){
        return (int) tile_size;
    }
    
    /**
     * sets the tile size
     * @param ts new tile size
     */
    public void set_tile_size(double ts){
        tile_size = Math.min(Math.max(ts, tile_size_min), tile_size_max);
        slider_zoom.setValue((int) (100.0 / tile_size_max * tile_size));
        repaint();
    }
    
    /**
     * increases the tile size
     */
    public void tile_size_increment(){
        double ts = tile_size;
        ts = Math.exp(Math.log(ts / 10) + 0.03) * 10;
        ts = Math.min(ts, tile_size_max);
        tile_size = Math.min(Math.max(ts, tile_size + 1), tile_size_max);
        
        //if(tile_size < tile_size_max) tile_size++;
        slider_zoom.setValue((int) (100.0 / tile_size_max * tile_size));
        repaint();
    }
    
    /**
     * decreases the tile size
     */
    public void tile_size_decrement(){
        double ts = tile_size;
        ts = Math.exp(Math.log(ts / 10) - 0.02) * 10;
        ts = Math.max(ts, tile_size_min);
        tile_size = Math.max(Math.min(ts, tile_size - 1), tile_size_min);
        
        //if(tile_size > tile_size_min) tile_size--;
        
        slider_zoom.setValue((int) (100.0 / tile_size_max * tile_size));
        repaint();
    }
    
    
    /**
     * Sets whether a context menu is shown, to disable forced focus
     * @param b 
     */
    private void set_context_menu(boolean b) {
        is_context_menu_shown = b;
    }
    
    /**
     * Returns true, if a context menu is shown and forced focus is disabled
     * @return 
     */
    private boolean has_context_menu(){
        return is_context_menu_shown;
    }
    
    /**
     * Manually disables the forced focus
     * @param b 
     */
    public void set_forced_focus_disabled(boolean b){
        forced_focus_disabled = b;
    }
    
    /**
     * Returns true, if forced focus is disabled manually
     * @return 
     */
    public boolean get_forced_focus_disabled(){
        return forced_focus_disabled;
    }
    
    /**
     * Returs true, if forced focus can be enabled
     * @return 
     */
    private boolean get_forced_focus(){
        return !forced_focus_disabled && !is_context_menu_shown;
    }
    
    // ========================= selection listener ============================
    /**
     * Adds a place selection listener
     * @param listener 
     */
    public void add_cursor_listener(CursorListener listener){
        if(!cursor_listeners.contains(listener))
            cursor_listeners.add(listener);
    }
    
    /**
     * Removes a place selection listener
     * @param listener 
     */
    public void remove_cursor_listener(CursorListener listener){
        cursor_listeners.remove(listener);
    }
    
    /**
     * calls all place selection listeners
     */
    private void call_cursor_listeners(){
        Place place = get_place(get_cursor_x(), get_cursor_y());

        if(cursor_listeners != null) {
            if(place != null) 
                for(CursorListener listener: cursor_listeners) 
                    listener.placeSelected(place);
            else{
                Layer layer = get_world().get_layer(get_cur_position().get_layer());
                for(CursorListener listener: cursor_listeners) 
                    listener.placeDeselected(layer, get_cursor_x(), get_cursor_y());
            }
        }
    }
    
    public interface CursorListener{
        // gets called, when the cursor moves to another place
        public void placeSelected(Place p);
        // gets called, when the cursor changes to null
        public void placeDeselected(Layer layer, int x, int y);
    }
    
    // ========================= place (group) selection =======================
    /**
     * Clears the box/shift selection box
     */
    private void place_group_box_reset_selection(){
        place_group_box_end = place_group_box_start = null;
    }
    
    /**
     * Modifies the box/shift selection box (eg on shift + direction key)
     * @param x new coordinate
     * @param y new coordinate
     */
    private void place_group_box_modify_selection(int x, int y){
        place_group.clear();
        place_group_box_end = new WorldCoordinate(get_cur_position().get_layer(), x, y);
        // reset if layer changed
        if(place_group_box_start != null && place_group_box_start.get_layer() != place_group_box_end.get_layer()) place_group_box_start = null;
        // set start, if not set
        if(place_group_box_start == null) place_group_box_start = place_group_box_end;
    }
    
    /**
     * Moves the box/shift selection to the selected places list
     */
    private void place_group_box_selection_to_list(){
        if(place_group_box_end != null && place_group_box_start != null){
            int x1 = (int) Math.round(place_group_box_end.get_x());
            int x2 = (int) Math.round(place_group_box_start.get_x());
            int y1 = (int) Math.round(place_group_box_end.get_y());
            int y2 = (int) Math.round(place_group_box_start.get_y());
            
            int x_min = Math.min(x1, x2);
            int x_max = Math.max(x1, x2);
            int y_min = Math.min(y1, y2);
            int y_max = Math.max(y1, y2);
            
            Layer layer = world.get_layer(place_group_box_end.get_layer());
            
            for(int x = x_min; x <= x_max; ++x){
                for(int y = y_min; y <= y_max; ++y){
                    Place pl = (Place) layer.get(x, y);
                    if(pl != null) place_group.add(pl);
                }
            }
        }
        place_group_box_reset_selection();
    }
    
    /**
     * adds a place to the place selection list (eg on ctrl + click)
     * @param pl 
     */
    private void place_group_add(Place pl){
        place_group_box_selection_to_list();
        // clear list, if new place is on a different layer
        if(!place_group.isEmpty() && place_group.iterator().next().get_layer() != pl.get_layer()) place_group.clear();
        if(pl != null){
            if(place_group.contains(pl)) place_group.remove(pl);
            else place_group.add(pl);
        }
    }
    
    /**
     * Sets the selection to a new set
     * @param set 
     */
    private void place_group_set(HashSet<Place> set){
        place_group.clear();
        place_group = set;
    }
    
    /**
     * Clears the selected places list and the shift selection
     */
    private void place_group_reset(){
        place_group.clear();
        place_group_box_reset_selection();
    }
    
    /**
     * Returns true, if places are selected
     * @return 
     */
    public boolean place_group_has_selection(){
        return (place_group_box_start != null && place_group_box_end != null) || !place_group.isEmpty();
    }
    
    /**
     * gets all selected places
     * @return 
     */
    public HashSet<Place> place_group_get_selection(){
        if(place_group_box_start != null) place_group_box_selection_to_list();
        return place_group;
    }
    
    /**
     * Returns true, if a place is selected by group selection
     * @param place
     * @return 
     */
    private boolean place_group_is_selected(Place place){
        if(place != null){
            if(place_group_box_end != null && place_group_box_start != null 
                && place_group_box_end.get_layer() == place.get_layer().get_id()){
                int x1 = (int) Math.round(place_group_box_end.get_x());
                int x2 = (int) Math.round(place_group_box_start.get_x());
                int y1 = (int) Math.round(place_group_box_end.get_y());
                int y2 = (int) Math.round(place_group_box_start.get_y());

                int x_min = Math.min(x1, x2);
                int x_max = Math.max(x1, x2);
                int y_min = Math.min(y1, y2);
                int y_max = Math.max(y1, y2);

                if(place.get_x() >= x_min && place.get_x() <= x_max 
                    && place.get_y() >= y_min && place.get_y() <= y_max) return true;
            }
            if(place_group.contains(place)) return true;
        }
        return false;
    }
    
    /**
     * Loads the world meta data file
     * this file describes the coordinates of the last shown positions
     *
     * important: call this after creation of worldpanel!
     */
    private void load_meta(){
        String file = world.get_file() + "_meta";
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            
            String line;
            int layer_id = -1;
            double pos_x = 0, pos_y = 0;
            
            try {
                while((line = reader.readLine()) != null){
                    line = line.trim();

                    if(!line.isEmpty() && !line.startsWith("//") && !line.startsWith("#")){
                        if(line.startsWith("lp")){ // last position
                            String[] tmp = line.split(" ");
                            layer_id = Integer.parseInt(tmp[1]);
                            // the x coordinate has to be negated for backward compatibility to mudmap 1.x
                            pos_x = -Double.parseDouble(tmp[2]);
                            pos_y = Double.parseDouble(tmp[3]);


                        } else if(line.startsWith("pcv")){ // previously shown places
                            String[] tmp = line.split(" ");
                            int tmp_layer_id = Integer.parseInt(tmp[1]);

                            // the x coordinate has to be negated for backward compatibility to mudmap 1.x
                            double tmp_pos_x = -Double.parseDouble(tmp[2]);
                            double tmp_pos_y = Double.parseDouble(tmp[3]);

                            WorldCoordinate newcoord = new WorldCoordinate(tmp_layer_id, tmp_pos_x, tmp_pos_y);
                            if(positions.size() == 0 || !get_cur_position().equals(newcoord)) push_position(newcoord);
                        } else if(line.startsWith("tile_size")){
                            String[] tmp = line.split(" ");
                            tile_size = Double.parseDouble(tmp[1]);
                        } else if(line.startsWith("enable_place_selection")){
                            String[] tmp = line.split(" ");
                            cursor_enabled = Boolean.parseBoolean(tmp[1]) || force_selection;
                        }
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(WorldManager.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            push_position(new WorldCoordinate(layer_id, pos_x, pos_y));
            
        } catch (FileNotFoundException ex) {
            System.out.println("Couldn't open world meta file \"" + file + "\", file not found");
            //Logger.getLogger(WorldManager.class.getName()).log(Level.INFO, null, ex);
            
            push_position(new WorldCoordinate(0, 0, 0));
        }
    }
    
    /**
     * Saves the world meta file
     */
    public void write_meta(){
        if(!passive){
            try {
                // open file
                if(!Paths.is_directory(Paths.get_worlds_dir())) Paths.create_directory(Paths.get_worlds_dir());
                PrintWriter outstream = new PrintWriter(new BufferedWriter( new FileWriter(world.get_file() + "_meta")));

                outstream.println("# MUD Map (v2) world meta data file");
                outstream.println("ver " + meta_file_ver_major + "." + meta_file_ver_minor);

                // tile size
                outstream.println("tile_size " + (int) tile_size);

                // write whether the place selection is shown
                outstream.println("enable_place_selection " + get_cursor_enabled());

                // write current position and position history
                outstream.println("lp " + get_cur_position().get_meta_String());

                // shown place history
                for(Iterator<WorldCoordinate> wcit = positions.descendingIterator(); wcit.hasNext();){
                    WorldCoordinate next = wcit.next();
                    if(next != get_cur_position()) outstream.println("pcv " + next.get_meta_String());
                }

                outstream.close();
            } catch (IOException ex) {
                System.out.printf("Couldn't write world meta file " + world.get_file() + "_meta");
                Logger.getLogger(WorldTab.class.getName()).log(Level.WARNING, null, ex);
            }
        }
    }
    
    private static class WorldPanel extends JPanel {
        
        double screen_width, screen_height;
        
        WorldTab parent;
        MapPainter mappainter;

        // passive worldpanels don't modify the world
        final boolean passive;
        
        /**
         * Constructs a world panel
         * @param _parent parent world tab
         */
        public WorldPanel(WorldTab _parent, boolean _passive) {
            parent = _parent;
            passive = _passive;
            mappainter = new MapPainterDefault();
                    
            setFocusable(true);
            requestFocusInWindow();
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent arg0) {
                    if(parent.get_forced_focus()) requestFocusInWindow();
                }
            });
               
            addKeyListener(new TabKeyPassiveListener(this));
            addMouseListener(new TabMousePassiveListener());
            if(!passive){
                addKeyListener(new TabKeyListener(this));
                addMouseListener(new TabMouseListener());
            }
            addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double ts = parent.get_tile_size();
                    ts = Math.exp(Math.log(ts / 10) + e.getWheelRotation() * 0.05) * 10;
                    if(e.getWheelRotation() > 0) ts = Math.max(ts, parent.get_tile_size() + 1);
                    else if(e.getWheelRotation() < 0) ts = Math.min(ts, parent.get_tile_size() - 1);
                    parent.set_tile_size(ts);
                    //parent.set_tile_size(parent.get_tile_size() + e.getWheelRotation());
                }
            });
            addMouseMotionListener(new TabMouseMotionListener());
        }
        
        /**
         * Gets the screen width
         * @return screen width
         */
        public double get_screen_width(){
            return screen_width;
        }
        
        /**
         * Gets the screen height
         * @return screen height
         */
        public double get_screen_height(){
            return screen_height;
        }
                
        /**
         * Remove integer part, the part after the point remains
         * @param val
         * @return 
         */
        private double remint(double val){
            return val - Math.round(val);
        }
        
        /**
         * Converts screen coordinates to world coordinates
         * @param screen_x a screen coordinate (x-axis)
         * @return world coordinate x
         */
        private int get_place_pos_x(int screen_x){
            return (int) Math.ceil((double) (screen_x - screen_width / 2) / parent.get_tile_size() + parent.get_cur_position().get_x()) - 1;
        }
        
        /**
         * Converts screen coordinates to world coordinates
         * @param mouse_y a screen coordinate (y-axis)
         * @return world coordinate y
         */
        private int get_place_pos_y(int screen_y){
            return (int) -Math.ceil((double) (screen_y - screen_height / 2) / parent.get_tile_size() - parent.get_cur_position().get_y()) + 1;
        }
        
        /**
         * Converts world coordinates to screen coordinates
         * @param place_x a world (place) coordinate (x axis)
         * @return a screen coordinate x
         */
        private int get_screen_pos_x(int place_x){
            int tile_size = parent.get_tile_size();
            double screen_center_x = ((double) screen_width / tile_size) / 2; // note: wdtwd2
            int place_x_offset = (int) (Math.round((double) parent.get_cur_position().get_x()) - Math.round(screen_center_x));
            return (int)((place_x - place_x_offset + remint(screen_center_x) - remint(parent.get_cur_position().get_x())) * tile_size);
        }
        
        /**
         * Converts world coordinates to screen coordinates
         * @param place_y a world (place) coordinate (y axis)
         * @return a screen coordinate y
         */
        private int get_screen_pos_y(int place_y){
            int tile_size = parent.get_tile_size();
            double screen_center_y = ((double) screen_height / tile_size) / 2;
            int place_y_offset = (int) (Math.round(parent.get_cur_position().get_y()) - Math.round(screen_center_y));
            return (int)((-place_y + place_y_offset - remint(screen_center_y) + remint(parent.get_cur_position().get_y())) * tile_size + screen_height);
        }
        
        // ======================= DRAW WORLD HERE =============================
        
        @Override
        public void paintComponent(Graphics g){ 
            mappainter.set_place_group(parent.place_group, parent.place_group_box_start, parent.place_group_box_end);
            mappainter.set_place_selection(parent.get_cursor_x(), parent.get_cursor_y());
            mappainter.set_place_selection_enabled(parent.get_cursor_enabled());
            
            mappainter.paint(g, parent.get_tile_size(), screen_width = getWidth(), screen_height = getHeight(), parent.get_world().get_layer(parent.get_cur_position().get_layer()), parent.get_cur_position());
        }
        
        // ========================= Listeners and context menu ================
        
        /**
         * This listener only contains actions, that don't modify the world
         */
        private class TabMousePassiveListener extends TabMouseListener implements MouseListener {
            @Override
            public void mouseClicked(MouseEvent arg0) {
                if(arg0.getButton() == MouseEvent.BUTTON3){ // right click
                    // show context menu
                    TabContextMenu context_menu = new TabContextMenu(parent, get_place_pos_x(arg0.getX()), get_place_pos_y(arg0.getY()));
                    context_menu.show(arg0.getComponent(), arg0.getX(), arg0.getY());
                } else if(arg0.getButton() == MouseEvent.BUTTON1){ // left click
                    if(!arg0.isShiftDown()){ // left click + hift gets handled in active listener
                        // set place selection to coordinates if keyboard selection is enabled
                        parent.set_cursor(get_place_pos_x(arg0.getX()), get_place_pos_y(arg0.getY()));
                    }
                }
            }
        }
        
        /**
         * This listener contains actions that modify the world
         */
        private class TabMouseListener implements MouseListener {

            @Override
            public void mouseClicked(MouseEvent arg0) {
                if(arg0.getButton() == MouseEvent.BUTTON1){ // left click
                    Place place = parent.get_place(get_place_pos_x(arg0.getX()), get_place_pos_y(arg0.getY()));
                    if(arg0.isControlDown()){ // left click + ctrl
                        if(place != null) parent.place_group_add(place);
                    } else if(!arg0.isShiftDown()) { // left click and not shift
                        parent.place_group_reset();
                        if(arg0.getClickCount() > 1){ // double click
                            if(place != null) (new PlaceDialog(parent.parent, parent.get_world(), place)).setVisible(true);
                            else (new PlaceDialog(parent.parent, parent.world, parent.get_world().get_layer(parent.get_cur_position().get_layer()), get_place_pos_x(arg0.getX()), get_place_pos_y(arg0.getY()))).setVisible(true);
                        }
                    } else {
                        if(!parent.place_group_has_selection())
                            parent.place_group_box_modify_selection(parent.get_cursor_x(), parent.get_cursor_y());
                        parent.place_group_box_modify_selection(get_place_pos_x(arg0.getX()), get_place_pos_y(arg0.getY()));
                        // cursor has to be set after the selection -> not handled by passive listener
                        parent.set_cursor(get_place_pos_x(arg0.getX()), get_place_pos_y(arg0.getY()));
                    }
                }
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent arg0) {
                requestFocusInWindow();
            }

            @Override
            public void mouseReleased(MouseEvent arg0) {}

            @Override
            public void mouseEntered(MouseEvent arg0) {
                parent.mouse_in_panel = true;
                parent.mouse_x_previous = arg0.getX();
                parent.mouse_y_previous = arg0.getY();
            }

            @Override
            public void mouseExited(MouseEvent arg0) {
                parent.mouse_in_panel = false;
            }
        }
                
        private class TabMouseMotionListener implements MouseMotionListener {

            @Override
            public void mouseDragged(MouseEvent arg0) {
                if(parent.mouse_in_panel){
                    double dx = (double) (arg0.getX() - parent.mouse_x_previous) / parent.get_tile_size();
                    double dy = (double) (arg0.getY() - parent.mouse_y_previous) / parent.get_tile_size();
                    if(!arg0.isShiftDown()) // shift not pressed: move view
                        parent.get_cur_position().move(-dx , dy);
                    else { // shift pressed: box selection
                        parent.place_group_box_modify_selection(get_place_pos_x(arg0.getX()), get_place_pos_y(arg0.getY()));
                    }
                    parent.repaint();
                }
                parent.mouse_x_previous = arg0.getX();
                parent.mouse_y_previous = arg0.getY();
            }

            @Override
            public void mouseMoved(MouseEvent arg0) {
                parent.mouse_x_previous = arg0.getX();
                parent.mouse_y_previous = arg0.getY();
            }   
        }
        
        /**
         * This listener only contains actions, that don't modify the world
         */
        private class TabKeyPassiveListener extends TabKeyListener {
            public TabKeyPassiveListener(WorldPanel parent){
                super(parent);
            }
            
            @Override
            public void keyPressed(KeyEvent arg0) {                
                if(!arg0.isShiftDown() && !arg0.isControlDown() && !arg0.isAltDown() && !arg0.isAltGraphDown()){ // ctrl, shift and alt not pressed
                    int x_bef = parent.get_cursor_x();
                    int y_bef = parent.get_cursor_y();
                    
                    switch(arg0.getKeyCode()){
                        // zoom the map
                        case KeyEvent.VK_PLUS:
                        case KeyEvent.VK_ADD:
                        case KeyEvent.VK_PAGE_UP:
                            parent.tile_size_increment();
                            break;
                        case KeyEvent.VK_MINUS:
                        case KeyEvent.VK_SUBTRACT:
                        case KeyEvent.VK_PAGE_DOWN:
                            parent.tile_size_decrement();
                            break;

                        // enable / disable cursor
                        case KeyEvent.VK_P:
                            parent.set_cursor_toggle();
                            break;

                        // shift place selection - wasd
                        case KeyEvent.VK_NUMPAD8:
                        case KeyEvent.VK_UP:
                        case KeyEvent.VK_W:
                            if(parent.get_cursor_enabled()) parent.move_cursor(0, +1);
                            break;
                        case KeyEvent.VK_NUMPAD4:
                        case KeyEvent.VK_LEFT:
                        case KeyEvent.VK_A:
                            if(parent.get_cursor_enabled()) parent.move_cursor(-1, 0);
                            break;
                        case KeyEvent.VK_NUMPAD2:
                        case KeyEvent.VK_DOWN:
                        case KeyEvent.VK_S:
                            if(parent.get_cursor_enabled()) parent.move_cursor(0, -1);
                            break;
                        case KeyEvent.VK_NUMPAD6:
                        case KeyEvent.VK_RIGHT:
                        case KeyEvent.VK_D:
                            if(parent.get_cursor_enabled()) parent.move_cursor(+1, 0);
                            break;

                        // diagonal movement
                        case KeyEvent.VK_NUMPAD1:
                            if(parent.get_cursor_enabled()) parent.move_cursor(-1, -1);
                            break;
                        case KeyEvent.VK_NUMPAD3:
                            if(parent.get_cursor_enabled()) parent.move_cursor(+1, -1);
                            break;
                        case KeyEvent.VK_NUMPAD7:
                            if(parent.get_cursor_enabled()) parent.move_cursor(-1, +1);
                            break;
                        case KeyEvent.VK_NUMPAD9:
                            if(parent.get_cursor_enabled()) parent.move_cursor(+1, +1);
                            break;

                        // goto home
                        case KeyEvent.VK_NUMPAD5:
                        case KeyEvent.VK_H:
                        case KeyEvent.VK_HOME:
                            parent.goto_home();
                            break;

                        // show place list
                        case KeyEvent.VK_L:
                            (new PlaceListDialog(parent, passive)).setVisible(true);
                            break;
                            
                        // reset place group selection
                        case KeyEvent.VK_ESCAPE:
                            parent.place_group_reset();
                            break;
                    }
                    
                    int x_sel = parent.get_cursor_x();
                    int y_sel = parent.get_cursor_y();
                    
                    // change group selection, if place selection changed
                    if(x_sel != x_bef || y_sel != y_bef){
                        if(parent.place_group_box_start != null) parent.place_group_box_selection_to_list();
                    }
                }
            }
        }
        
        /**
         * This listener contains actions, that modify the world
         */
        private class TabKeyListener implements KeyListener {
            
            WorldPanel worldpanel;
            
            public TabKeyListener(WorldPanel parent){
                worldpanel = parent;
            }

            @Override
            public void keyTyped(KeyEvent arg0) {}

            @Override
            public void keyPressed(KeyEvent arg0) {
                if(arg0.isControlDown()){ // ctrl key pressed
                    Place place, other;
                    
                    switch(arg0.getKeyCode()){
                        case KeyEvent.VK_S: // save world
                            parent.save();
                            break;
                        case KeyEvent.VK_O: // open world
                            (new OpenWorldDialog((Mainwindow) parent.parent)).setVisible();
                            break;
                    
                        case KeyEvent.VK_A: // select all places
                            parent.place_group_set(parent.get_world().get_layer(parent.get_cur_position().get_layer()).get_places());
                            break;
                        case KeyEvent.VK_X: // cut selected places
                            if(!parent.place_group_get_selection().isEmpty()){ // cut group selection
                                mudmap2.Mudmap2.cut(parent.place_group, parent.get_cursor_x(), parent.get_cursor_y());
                                parent.show_message(parent.place_group.size() + " places cut");
                                parent.place_group_reset();
                            } else if(parent.get_selected_place() != null){ // cut cursor selection
                                HashSet<Place> tmp_selection = new HashSet<Place>();
                                tmp_selection.add(parent.get_selected_place());
                                mudmap2.Mudmap2.cut(tmp_selection, parent.get_cursor_x(), parent.get_cursor_y());
                                parent.show_message("1 place cut");
                            } else parent.show_message("No places cut: selection empty");                   
                            break;
                        case KeyEvent.VK_C: // copy selected places
                            if(!parent.place_group_get_selection().isEmpty()){ // copy group selection
                                mudmap2.Mudmap2.copy(parent.place_group, parent.get_cursor_x(), parent.get_cursor_y());
                                parent.show_message(parent.place_group.size() + " places copied");
                                parent.place_group_reset();
                            } else if(parent.get_selected_place() != null){ // copy cursor selection
                                HashSet<Place> tmp_selection = new HashSet<Place>();
                                tmp_selection.add(parent.get_selected_place());
                                mudmap2.Mudmap2.copy(tmp_selection, parent.get_cursor_x(), parent.get_cursor_y());
                                parent.show_message("1 place copied");
                            } else {
                                mudmap2.Mudmap2.reset_copy();
                                parent.show_message("No places copied: selection empty");
                            }
                            break;
                        case KeyEvent.VK_V: // paste copied / cut places
                            if(mudmap2.Mudmap2.has_copy_places()){
                                if(mudmap2.Mudmap2.can_paste(parent.get_cursor_x(), parent.get_cursor_y(), parent.get_world().get_layer(parent.get_cur_position().get_layer()))){
                                    int paste_num = mudmap2.Mudmap2.get_copy_places().size();
                                    if(mudmap2.Mudmap2.paste(parent.get_cursor_x(), parent.get_cursor_y(), parent.get_world().get_layer(parent.get_cur_position().get_layer()))){
                                        parent.show_message(paste_num + " places pasted");
                                    } else {
                                        parent.show_message("No places pasted");
                                    }
                                } else {
                                    parent.show_message("Can't paste: not enough free space on map");
                                }
                            } else {
                                mudmap2.Mudmap2.reset_copy();
                                parent.show_message("Can't paste: no places cut or copied");
                            }
                            break;
                            
                        case KeyEvent.VK_NUMPAD8:
                        case KeyEvent.VK_UP:
                        //case KeyEvent.VK_W: // add path to direction 'n'
                            place = parent.get_selected_place();
                            other = parent.get_place(parent.get_cursor_x(), parent.get_cursor_y() + 1);
                            if(place != null && other != null){ // if places exist
                                if(place.get_exit("n") == null && other.get_exit("s") == null){ // if exits aren't occupied
                                    place.connect_path(new Path(place, "n", other, "s"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD9: // add path to direction 'ne'
                            place = parent.get_selected_place();
                            other = parent.get_place(parent.get_cursor_x() + 1, parent.get_cursor_y() + 1);
                            if(place != null && other != null){ // if places exist
                                if(place.get_exit("ne") == null && other.get_exit("sw") == null){ // if exits aren't occupied
                                    place.connect_path(new Path(place, "ne", other, "sw"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD6:
                        case KeyEvent.VK_RIGHT:
                        //case KeyEvent.VK_D: // add path to direction 'e'
                            place = parent.get_selected_place();
                            other = parent.get_place(parent.get_cursor_x() + 1, parent.get_cursor_y());
                            if(place != null && other != null){ // if places exist
                                if(place.get_exit("e") == null && other.get_exit("w") == null){ // if exits aren't occupied
                                    place.connect_path(new Path(place, "e", other, "w"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD3: // add path to direction 'se'
                            place = parent.get_selected_place();
                            other = parent.get_place(parent.get_cursor_x() + 1, parent.get_cursor_y() - 1);
                            if(place != null && other != null){ // if places exist
                                if(place.get_exit("se") == null && other.get_exit("nw") == null){ // if exits aren't occupied
                                    place.connect_path(new Path(place, "se", other, "nw"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD2:
                        case KeyEvent.VK_DOWN:
                        //case KeyEvent.VK_S: // add path to direction 's'
                            place = parent.get_selected_place();
                            other = parent.get_place(parent.get_cursor_x(), parent.get_cursor_y() - 1);
                            if(place != null && other != null){ // if places exist
                                if(place.get_exit("s") == null && other.get_exit("n") == null){ // if exits aren't occupied
                                    place.connect_path(new Path(place, "s", other, "n"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD1: // add path to direction 'sw'
                            place = parent.get_selected_place();
                            other = parent.get_place(parent.get_cursor_x() - 1, parent.get_cursor_y() - 1);
                            if(place != null && other != null){ // if places exist
                                if(place.get_exit("sw") == null && other.get_exit("ne") == null){ // if exits aren't occupied
                                    place.connect_path(new Path(place, "sw", other, "ne"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD4:
                        case KeyEvent.VK_LEFT:
                        //case KeyEvent.VK_A: // add path to direction 'w'
                            place = parent.get_selected_place();
                            other = parent.get_place(parent.get_cursor_x() - 1, parent.get_cursor_y());
                            if(place != null && other != null){ // if places exist
                                if(place.get_exit("w") == null && other.get_exit("e") == null){ // if exits aren't occupied
                                    place.connect_path(new Path(place, "w", other, "e"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD7: // add path to direction 'nw'
                            place = parent.get_selected_place();
                            other = parent.get_place(parent.get_cursor_x() - 1, parent.get_cursor_y() + 1);
                            if(place != null && other != null){ // if places exist
                                if(place.get_exit("nw") == null && other.get_exit("se") == null){ // if exits aren't occupied
                                    place.connect_path(new Path(place, "nw", other, "se"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD5: // open add path dialog
                            (new PathConnectDialog(parent, parent.get_selected_place())).setVisible(true);
                            break;
                    } 
                } else if(arg0.isShiftDown()){ // shift key pressed -> modify selection
                    int x_bef = parent.get_cursor_x();
                    int y_bef = parent.get_cursor_y();
                    
                    switch(arg0.getKeyCode()){
                        case KeyEvent.VK_NUMPAD8:
                        case KeyEvent.VK_UP:
                        case KeyEvent.VK_W:
                            if(parent.get_cursor_enabled()) parent.move_cursor(0, +1);
                            break;
                        case KeyEvent.VK_NUMPAD4:
                        case KeyEvent.VK_LEFT:
                        case KeyEvent.VK_A:
                            if(parent.get_cursor_enabled()) parent.move_cursor(-1, 0);
                            break;
                        case KeyEvent.VK_NUMPAD2:
                        case KeyEvent.VK_DOWN:
                        case KeyEvent.VK_S:
                            if(parent.get_cursor_enabled()) parent.move_cursor(0, -1);
                            break;
                        case KeyEvent.VK_NUMPAD6:
                        case KeyEvent.VK_RIGHT:
                        case KeyEvent.VK_D:
                            if(parent.get_cursor_enabled()) parent.move_cursor(+1, 0);
                            break;

                        // diagonal movement
                        case KeyEvent.VK_NUMPAD1:
                            if(parent.get_cursor_enabled()) parent.move_cursor(-1, -1);
                            break;
                        case KeyEvent.VK_NUMPAD3:
                            if(parent.get_cursor_enabled()) parent.move_cursor(+1, -1);
                            break;
                        case KeyEvent.VK_NUMPAD7:
                            if(parent.get_cursor_enabled()) parent.move_cursor(-1, +1);
                            break;
                        case KeyEvent.VK_NUMPAD9:
                            if(parent.get_cursor_enabled()) parent.move_cursor(+1, +1);
                            break;
                            
                        case KeyEvent.VK_SPACE: // add or remove single place to place group selection
                            Place place = parent.get_selected_place();
                            if(place != null) parent.place_group_add(place);
                            break;
                    }
                    int x_sel = parent.get_cursor_x();
                    int y_sel = parent.get_cursor_y();
                    
                    // change group selection, if place selection changed
                    if(x_sel != x_bef || y_sel != y_bef){
                        if(parent.place_group_box_start == null) parent.place_group_box_modify_selection(x_bef, y_bef);
                        parent.place_group_box_modify_selection(x_sel, y_sel);
                    }
                } else if(arg0.isAltDown() || arg0.isAltGraphDown()){ // alt or altgr key pressed                    
                    Place place = parent.get_selected_place();
                    Place other;
                    Path path;
                    
                    if(place != null){
                        switch(arg0.getKeyCode()){
                            case KeyEvent.VK_NUMPAD8:
                            case KeyEvent.VK_UP:
                            case KeyEvent.VK_W: // remove path to direction 'n'
                                    path = place.get_path_to("n");
                                    if(path != null) place.remove_path(path);
                                break;
                            case KeyEvent.VK_NUMPAD9: // remove path to direction 'ne'
                                    path = place.get_path_to("ne");
                                    if(path != null) place.remove_path(path);
                                break;
                            case KeyEvent.VK_NUMPAD6:
                            case KeyEvent.VK_RIGHT:
                            case KeyEvent.VK_D: // remove path to direction 'e'
                                    path = place.get_path_to("e");
                                    if(path != null) place.remove_path(path);
                                break;
                            case KeyEvent.VK_NUMPAD3: // remove path to direction 'se'
                                    path = place.get_path_to("se");
                                    if(path != null) place.remove_path(path);
                                break;
                            case KeyEvent.VK_NUMPAD2:
                            case KeyEvent.VK_DOWN:
                            case KeyEvent.VK_S: // remove path to direction 's'
                                    path = place.get_path_to("s");
                                    if(path != null) place.remove_path(path);
                                break;
                            case KeyEvent.VK_NUMPAD1: // remove path to direction 'sw'
                                    path = place.get_path_to("sw");
                                    if(path != null) place.remove_path(path);
                                break;
                            case KeyEvent.VK_NUMPAD4:
                            case KeyEvent.VK_LEFT:
                            case KeyEvent.VK_A: // remove path to direction 'w'
                                    path = place.get_path_to("w");
                                    if(path != null) place.remove_path(path);
                                break;
                            case KeyEvent.VK_NUMPAD7: // remove path to direction 'nw'
                                    path = place.get_path_to("nw");
                                    if(path != null) place.remove_path(path);
                                break;
                        }
                    }
                } else { // ctrl, shift and alt not pressed
                    switch(arg0.getKeyCode()){
                        // show context menu
                        case KeyEvent.VK_CONTEXT_MENU:
                            if(parent.get_cursor_enabled()){
                                TabContextMenu context_menu = new TabContextMenu(parent, parent.get_cursor_x(), parent.get_cursor_y());
                                context_menu.show(arg0.getComponent(), get_screen_pos_x(parent.get_cursor_x()) + worldpanel.parent.get_tile_size() / 2, get_screen_pos_y(parent.get_cursor_y()) + worldpanel.parent.get_tile_size() / 2);
                            }
                            break;

                        // edit / add place
                        case KeyEvent.VK_INSERT:
                        case KeyEvent.VK_ENTER:
                        case KeyEvent.VK_E:
                            if(parent.get_cursor_enabled()){
                                Place place = parent.get_selected_place();
                                PlaceDialog dlg;
                                
                                Layer layer = null;
                                if(parent.get_cur_position() != null) layer = parent.get_world().get_layer(parent.get_cur_position().get_layer());
                                
                                if(place != null) dlg = new PlaceDialog(parent.parent, parent.world, place);
                                else dlg = new PlaceDialog(parent.parent, parent.world, parent.world.get_layer(parent.get_cur_position().get_layer()), parent.get_cursor_x(), parent.get_cursor_y());
                                dlg.setVisible(true);
                                
                                if(layer == null) parent.push_position(dlg.get_place().get_coordinate());
                            }
                            break;
                        // create placeholder
                        case KeyEvent.VK_F:
                            if(parent.get_cursor_enabled()){
                                Place place = parent.get_selected_place();
                                // create placeholder or remove one
                                if(place == null){
                                    parent.world.put_placeholder(parent.get_cur_position().get_layer(), parent.get_cursor_x(), parent.get_cursor_y());
                                } else if(place.get_name().equals(Place.placeholder_name)){
                                    try {
                                        place.remove();
                                    } catch (RuntimeException ex) {
                                        Logger.getLogger(WorldTab.class.getName()).log(Level.SEVERE, null, ex);
                                        JOptionPane.showMessageDialog(parent, "Could not remove place: " + ex.getMessage());
                                    } catch (PlaceNotFoundException ex) {
                                        Logger.getLogger(WorldTab.class.getName()).log(Level.SEVERE, null, ex);
                                        JOptionPane.showMessageDialog(parent, "Could not remove place: Place not found.");
                                    }
                                }
                            }
                            parent.repaint();
                            break;
                        // remove place
                        case KeyEvent.VK_DELETE:
                        case KeyEvent.VK_R:
                            if(!parent.place_group_has_selection()){ // no places selected
                                if(parent.get_cursor_enabled()){
                                    Place place = parent.get_selected_place();
                                    if(place != null) (new PlaceRemoveDialog(parent.parent, parent.world, place)).show();
                                }
                            } else { // places selected
                                HashSet<Place> place_group = parent.place_group_get_selection();
                                if(place_group != null){
                                    PlaceRemoveDialog dlg = new PlaceRemoveDialog(parent.parent, parent.world, place_group);
                                    dlg.show();
                                    // reset selection, if places were removed
                                    if(dlg.get_places_removed()) parent.place_group_reset();
                                }
                            }
                            break;
                        // edit place comments
                        case KeyEvent.VK_C:
                            if(parent.get_cursor_enabled()){
                                Place place = parent.get_selected_place();
                                if(place != null){
                                    (new PlaceCommentDialog(parent.parent, place)).setVisible(true);
                                    parent.update_infobar();
                                }
                            }
                            break;
                        // modify area
                        case KeyEvent.VK_Q:
                            Place place = parent.get_selected_place();
                            
                            if(!parent.place_group_has_selection()){
                                // no place selected
                                if(place == null) (new AreaDialog(parent.parent, parent.world)).setVisible(true);
                                // place selected
                                else (new AreaDialog(parent.parent, parent.world, place)).setVisible(true);
                            } else { // place group selection
                                (new AreaDialog(parent.parent, parent.world, parent.place_group_get_selection(), place)).setVisible(true);
                            }
                            break;
                            
                        case KeyEvent.VK_SPACE: // add or remove single place to place group selection
                            place = parent.get_selected_place();
                            if(place != null) parent.place_group_add(place);
                            break;
                    }
                }
                parent.repaint();
            }

            @Override
            public void keyReleased(KeyEvent arg0) {}
        }
        
        // constructs the context menu (on right click)
        private static class TabContextMenu extends JPopupMenu {
            
            WorldTab parent;
            
            /**
             * Constructs a context menu at position (x,y)
             * @param x screen / panel coordinate x
             * @param y screen / panel coordinate y
             */
            public TabContextMenu(WorldTab _parent, final int px, final int py) {
                addPopupMenuListener(new TabContextPopMenuListener());
                
                parent = _parent;
                final Layer layer = parent.world.get_layer(parent.get_cur_position().get_layer());
                
                final Place place = (layer != null ? (Place) layer.get(px, py) : null);
                final boolean has_place = layer != null && place != null;
                
                if(has_place){ // if place exists
                    if(!parent.passive){
                        JMenuItem mi_edit = new JMenuItem("Edit place");
                        PlaceDialog pdlg = new PlaceDialog(parent.parent, parent.world, place);
                        mi_edit.addActionListener(pdlg);
                        if(layer == null) parent.push_position(pdlg.get_place().get_coordinate());
                        
                        add(mi_edit);
                        mi_edit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0));
                        
                        HashSet<Place> place_group = parent.place_group_get_selection();

                        JMenuItem mi_remove;
                        if(place_group.isEmpty()){
                            mi_remove = new JMenuItem("Remove place");
                            mi_remove.addActionListener(new PlaceRemoveDialog(parent.parent, parent.world, place));
                            mi_remove.setToolTipText("Remove this place");
                        } else {
                            mi_remove = new JMenuItem("*Remove places");
                            mi_remove.addActionListener(new PlaceRemoveDialog(parent.parent, parent.world, place_group));
                            mi_remove.setToolTipText("Remove all selected places");
                        }
                        add(mi_remove);
                        mi_remove.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));

                        JMenuItem mi_comments = new JMenuItem("Edit comments");
                        mi_comments.addActionListener(new PlaceCommentDialog(parent.parent, place));
                        mi_comments.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0));
                        add(mi_comments);

                        JMenuItem mi_area;
                        if(place_group.isEmpty()){
                            mi_area = new JMenuItem("Edit area");
                            mi_area.addActionListener(new AreaDialog(parent.parent, parent.world, place));
                            mi_area.setToolTipText("Edit the area of this place");
                        } else {
                            mi_area = new JMenuItem("*Edit area");
                            mi_area.addActionListener(new AreaDialog(parent.parent, parent.world, place_group, place));
                            mi_area.setToolTipText("Sets a common area for all selected places");
                        }
                        add(mi_area);
                    }
                    
                    // ------------- Paths ------------------
                    JMenu m_paths = new JMenu("Paths / Exits");
                    if(!parent.passive || !place.get_paths().isEmpty())
                        add(m_paths);
                    
                    if(!parent.passive){
                        JMenu m_path_connect = new JMenu("Connect");
                        m_paths.add(m_path_connect);
                        m_path_connect.setToolTipText("Connect a path from this place to another one");

                        JMenuItem mi_path_connect_select = new JMenuItem("Select");
                        m_path_connect.add(mi_path_connect_select);
                        mi_path_connect_select.setToolTipText("Select any place from the map");
                        mi_path_connect_select.addActionListener(new PathConnectDialog(parent, place));
                        mi_path_connect_select.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD5, Event.CTRL_MASK));
                        
                        JMenuItem mi_path_connect_neighbors = new JMenuItem("Neighbors");
                        m_path_connect.add(mi_path_connect_neighbors);
                        mi_path_connect_neighbors.setToolTipText("Choose from surrounding places");
                        mi_path_connect_neighbors.addActionListener(new PathConnectNeighborsDialog(parent.parent, place));

                        LinkedList<Place> places = layer.get_neighbors(px, py, 1);
                        if(!places.isEmpty()){
                            m_path_connect.add(new JSeparator());

                            for(Place neighbor: places){
                                // only show, if no connection exists, yet
                                if(place.get_paths(neighbor).isEmpty()){
                                    String dir1 = "", dir2 = "";

                                    if(neighbor.get_y() > place.get_y())
                                        {dir1 = "n"; dir2 = "s";}
                                    else if(neighbor.get_y() < place.get_y())
                                        {dir1 = "s"; dir2 = "n";}
                                    if(neighbor.get_x() > place.get_x())
                                        {dir1 = dir1 + "e"; dir2 = dir2 + "w";}
                                    else if(neighbor.get_x() < place.get_x())
                                        {dir1 = dir1 + "w"; dir2 = dir2 + "e";}

                                    // if exits aren't occupied yet -> add menu item
                                    if(place.get_path_to(dir1) == null && neighbor.get_path_to(dir2) == null){
                                        JMenuItem mi_path_connect = new JMenuItem("[" + dir1 + "] " + neighbor.get_name());
                                        m_path_connect.add(mi_path_connect);
                                        mi_path_connect.addActionListener(new ConnectPathActionListener(place, neighbor, dir1, dir2));

                                        // add accelerator
                                        int dirnum = Path.get_dir_num(dir1);
                                        if(dirnum > 0 & dirnum <= 9)
                                            mi_path_connect.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + dirnum, Event.CTRL_MASK));
                                    }
                                }
                            }
                        }
                    }
                    
                    // get all connected places
                    HashSet<Path> paths = place.get_paths();
                    
                    if(!paths.isEmpty()){
                        JMenu m_path_remove = null;
                        if(!parent.passive){
                            m_path_remove = new JMenu("Remove");
                            m_paths.add(m_path_remove);
                            m_path_remove.setToolTipText("Remove a path");

                            m_paths.add(new JSeparator());
                        }
                        
                        for(Path path: paths){
                            Place other_place = path.get_other_place(place);
                            JMenuItem mi_path_goto = new JMenuItem("Go to [" + path.get_exit(place) + "] " + other_place.get_name());
                            m_paths.add(mi_path_goto);
                            mi_path_goto.addActionListener(new GotoPlaceActionListener(parent, other_place));
                            
                            if(!parent.passive){
                                String dir = path.get_exit(place);
                                JMenuItem mi_path_remove = new JMenuItem("Remove [" + dir + "] " + other_place.get_name());
                                mi_path_remove.addActionListener(new RemovePathActionListener(path));
                                m_path_remove.add(mi_path_remove);
                                
                                // add accelerator
                                int dirnum = Path.get_dir_num(dir);
                                if(dirnum > 0 & dirnum <= 9)
                                    mi_path_remove.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + dirnum, Event.ALT_MASK));
                            }
                        }
                        
                        if(!parent.passive){
                            JMenuItem mi_shortest_path = new JMenuItem("Find shortest path");
                            m_paths.add(new JSeparator());
                            m_paths.add(mi_shortest_path);
                            mi_shortest_path.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    PlaceSelectionDialog dlg = new PlaceSelectionDialog((JFrame) parent.parent, parent.world, parent.get_cur_position(), true);
                                    dlg.setVisible(true);
                                    Place end = dlg.get_selection();
                                    if(end != null){
                                        parent.place_group_reset();
                                        Place place_it = parent.world.breadth_search(place, end);
                                        if(place_it == null) parent.label_infobar.show_message("No Path found");
                                        else {
                                            int path_length = 0;
                                            while(place_it != null){
                                                parent.place_group.add((Place) place_it);
                                                place_it = place_it.get_breadth_search_data().predecessor;
                                                ++path_length;
                                            }
                                            parent.label_infobar.show_message("Path found, length: " + (path_length - 1));
                                        }

                                    } 
                                }
                            });
                        }
                    }
                    
                    // ------------- sub-areas ------------------
                    JMenu m_subareas = new JMenu("Sub-areas");
                    m_subareas.setToolTipText("Not to be confused with areas, sub-areas usually connect a place to another layer of the map, eg. a building <-> rooms inside it");
                    if(!parent.passive || !place.get_children().isEmpty())
                        add(m_subareas);
                    
                    if(!parent.passive){
                        JMenuItem mi_sa_connect = new JMenuItem("Connect with place");
                        m_subareas.add(mi_sa_connect);
                        mi_sa_connect.setToolTipText("Connects another place to this place as sub-area");
                        mi_sa_connect.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                PlaceSelectionDialog dlg = new PlaceSelectionDialog((JFrame) parent.parent, parent.world, parent.get_cur_position(), true);
                                dlg.setVisible(true);
                                Place child = dlg.get_selection();
                                if(child != null && child != place){
                                    int ret = JOptionPane.showConfirmDialog(parent, "Connect \"" + child.get_name() + "\" to \"" + place.get_name() + "\"?", "Connect sub-area", JOptionPane.YES_NO_OPTION);
                                    if(ret == JOptionPane.YES_OPTION){
                                        place.connect_child(child);
                                        parent.repaint();
                                    }
                                }
                            }
                        });

                        JMenuItem mi_sa_new_layer = new JMenuItem("Add on new layer");
                        mi_sa_new_layer.setToolTipText("Creates a new place on a new layer and connects it with \"" + place.get_name() + "\" as a sub-area");
                        m_subareas.add(mi_sa_new_layer);
                        mi_sa_new_layer.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent arg0) {
                                // create new place
                                PlaceDialog dlg = new PlaceDialog(parent.parent, parent.get_world(), null, 0, 0);
                                dlg.setVisible(true);

                                Place place_new = dlg.get_place();
                                if(place_new != null){
                                    // connect new place with place as a child
                                    place.connect_child(place_new);
                                    // go to new place
                                    parent.push_position(place_new.get_coordinate());
                                }
                            }
                        });
                    }
                    
                    HashSet<Place> children = place.get_children();
                    if(!children.isEmpty()){
                        if(!parent.passive){
                            JMenu m_sa_remove = new JMenu("Remove");
                            m_subareas.add(m_sa_remove);

                            for(Place child: children){
                                JMenuItem mi_sa_remove = new JMenuItem("Remove " + child.get_name());
                                m_sa_remove.add(mi_sa_remove);
                                mi_sa_remove.addActionListener(new RemoveSubAreaActionListener(place, child));
                            }
                        }
                        
                        m_subareas.add(new JSeparator());
                        
                        for(Place child: children){
                            JMenuItem mi_sa_goto = new JMenuItem("Go to " + child.get_name());
                            m_subareas.add(mi_sa_goto);
                            mi_sa_goto.addActionListener(new GotoPlaceActionListener(parent, child));
                        }
                    }
                    
                    HashSet<Place> parents = place.get_parents();
                    if(!parents.isEmpty()){                        
                        m_subareas.add(new JSeparator());
                        
                        for(Place child: parents){
                            JMenuItem mi_sa_goto = new JMenuItem("Go to parent " + child.get_name());
                            m_subareas.add(mi_sa_goto);
                            mi_sa_goto.addActionListener(new GotoPlaceActionListener(parent, child));
                        }
                    }
                    
                }  else { // if layer doesn't exist or no place exists at position x,y
                    JMenuItem mi_new = new JMenuItem("New place");
                    mi_new.addActionListener(new PlaceDialog(parent.parent, parent.world, layer, px, py));
                    add(mi_new);
                    JMenuItem mi_placeholder = new JMenuItem("New placeholder");
                    add(mi_placeholder);
                    mi_placeholder.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            // creates a placeholder place
                            parent.world.put_placeholder(parent.get_cur_position().get_layer(), px, py);
                            parent.repaint();
                        }
                    });
                }
                
                // cut / copy / paste for selected places
                final boolean can_paste = layer != null && mudmap2.Mudmap2.can_paste(px, py, layer);
                final boolean has_paste_places = layer != null && mudmap2.Mudmap2.has_copy_places();
                final boolean has_selection = parent.place_group_has_selection();
                
                if(has_place || has_selection || has_paste_places)
                    add(new JSeparator());
                
                if(has_place || has_selection){
                    JMenuItem mi_cut_place = new JMenuItem("Cut" + (has_selection ? " selection" : " place"));
                    add(mi_cut_place);
                    mi_cut_place.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            HashSet<Place> set;
                            if(has_selection){
                                set = parent.place_group_get_selection();
                            } else {
                                set = new HashSet<Place>();
                                set.add(place);
                            }
                            mudmap2.Mudmap2.cut(set, px, py);
                            parent.repaint();
                        }
                    });
                    mi_cut_place.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
                    
                    JMenuItem mi_copy_place = new JMenuItem("Copy" + (has_selection ? " selection" : " place"));
                    add(mi_copy_place);
                    mi_copy_place.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            HashSet<Place> set = null;
                            if(has_selection){
                                set = parent.place_group_get_selection();
                            } else {
                                set = new HashSet<Place>();
                                set.add(place);
                            }
                            mudmap2.Mudmap2.copy(set, px, py);
                            parent.repaint();
                        }
                    });
                    mi_copy_place.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
                }
                
                if(has_paste_places){
                    JMenuItem mi_paste_place = new JMenuItem("Paste");
                    add(mi_paste_place);
                    mi_paste_place.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            mudmap2.Mudmap2.paste(px, py, layer);
                            parent.repaint();
                        }
                    });
                    if(!can_paste) mi_paste_place.setEnabled(false);
                    mi_paste_place.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));
                }
                
            }
            
            /**
             * redraws the world tab after the popup is closed
             */
            private class TabContextPopMenuListener implements PopupMenuListener {

                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent arg0) {
                    parent.set_context_menu(true);
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent arg0) {
                    parent.set_context_menu(false);
                    parent.repaint();
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent arg0) {
                    parent.set_context_menu(false);
                    parent.repaint();
                }
            }
            
            /**
             * Moves the map to the place, if action is performed
             */
            private class GotoPlaceActionListener implements ActionListener{
                WorldTab worldtab;
                Place place;
                
                public GotoPlaceActionListener(WorldTab _worldtab, Place _place){
                    worldtab = _worldtab;
                    place = _place;
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    if(place != null) worldtab.push_position(place.get_coordinate());
                }
            }
            
            /**
             * Removes a subarea child from a place, if action performed
             */
            private class RemoveSubAreaActionListener implements ActionListener{
                Place place, child;
                
                public RemoveSubAreaActionListener(Place _place, Place _child) {
                    place = _place;
                    child = _child;
                }
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    if(place != null && child != null) place.remove_child(child);
                }
            }
            
            /**
             * Connects a new path, if called
             */
            private class ConnectPathActionListener implements ActionListener{
                
                Place pl1, pl2;
                String dir1, dir2;

                public ConnectPathActionListener(Place _pl1, Place _pl2, String _dir1, String _dir2) {
                    pl1 = _pl1;
                    pl2 = _pl2;
                    dir1 = _dir1;
                    dir2 = _dir2;
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    pl1.connect_path(new Path(pl1, dir1, pl2, dir2));
                }
            }
            
            /**
             * removes a path, if called
             */
            private class RemovePathActionListener implements ActionListener{
                Path path;

                private RemovePathActionListener(Path _path) {
                    path = _path;
                }
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    path.remove();
                }   
            }
            
        }
    }
}