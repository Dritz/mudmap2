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

import mudmap2.frontend.GUIElement.MapPainter;
import mudmap2.frontend.GUIElement.MapPainterDefault;
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
import java.io.File;
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
import mudmap2.backend.LayerElement;
import mudmap2.backend.Path;
import mudmap2.backend.Place;
import mudmap2.backend.World;
import mudmap2.backend.WorldCoordinate;
import mudmap2.backend.WorldFileReader.WorldFile;
import mudmap2.backend.WorldFileReader.current.WorldFileDefault;
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
    private static final long serialVersionUID = 1L;

    World world;
    String filename;

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
     * @param world world
     * @param file
     * @param passive world won't be changed, if true
     */
    public WorldTab(JFrame parent, World world, String file, boolean passive){
        this.parent = parent;
        this.world = world;
        this.filename = file;
        this.passive = passive;
        create();
    }

    /**
     * Constructs the world tab, opens the world if necessary
     * @param parent parent frame
     * @param world world
     * @param passive world won't be changed, if true
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
        world = wt.getWorld();
        createVariables();

        tile_size = wt.tile_size;
        cursor_enabled = wt.cursor_enabled;
        // copy positions
        for(WorldCoordinate pos: wt.positions) positions.add(pos);

        createGui();
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
        createVariables();
        loadMeta();
        createGui();
    }

    /**
     * Sets the initial values of the member variables
     */
    private void createVariables(){
        positions = new LinkedList<>();
        tile_size = 120;

        is_context_menu_shown = false;
        forced_focus_disabled = true;

        mouse_in_panel = false;
        mouse_x_previous = mouse_y_previous = 0;

        force_selection = false;
        cursor_enabled = place_selection_enabled_default;

        place_group = new HashSet<>();
    }

    /**
     * Creates the GUI elements
     */
    private void createGui(){
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
                popPosition();
            }
        });

        JButton button_next = new JButton("Next");
        constraints.gridx++;
        panel_south.add(button_next, constraints);
        button_next.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                restorePosition();
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
                ArrayList<Place> found_places = new ArrayList<>();

                // search
                for(Place pl: getWorld().getPlaces())
                    if(pl.matchKeywords(keywords)) found_places.add(pl);

                // display
                if(found_places.isEmpty()) JOptionPane.showMessageDialog(parent, "No places found!", "Search - " + getWorld().getName(), JOptionPane.PLAIN_MESSAGE);
                else (new PlaceListDialog(WorldTab.this, found_places, passive)).setVisible(true);
            }
        });


        constraints.gridx++;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        panel_south.add(label_infobar = new ScrollLabel(), constraints);
        label_infobar.startThread();

        // set default selected place to hte center place
        cursor_x = (int) Math.round(getCurPosition().getX());
        cursor_y = (int) Math.round(getCurPosition().getY());

        slider_zoom = new JSlider(0, 100, (int) (100.0 / tile_size_max * tile_size));
        constraints.gridx++;
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        panel_south.add(slider_zoom, constraints);
        slider_zoom.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent arg0) {
                setTileSize((int) ((double) tile_size_max * ((JSlider) arg0.getSource()).getValue() / 100.0));
            }
        });
        // ---

        cursor_listeners = new LinkedList<>();
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
            int ret = JOptionPane.showConfirmDialog(this, "Save world \"" + getWorld().getName() + "\"?", "Save world", JOptionPane.YES_NO_OPTION);
            if(ret == JOptionPane.YES_OPTION) save();
            WorldManager.closeFile(filename);
            ((Mainwindow) parent).removeTab(this);
        }
    }

    /**
     * Get the world
     * @return world
     */
    public World getWorld(){
        return world;
    }

    /**
     * Get the worlds file name
     * @return
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Set the worlds file name
     * @param filename
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * Get the panel width / width of the actually drawn map
     * @return
     */
    public int getPanelWidth(){
        return (int) worldpanel.getScreenWidth();
    }

    /**
     * Get the panel height / height of the actually drawn map
     * @return
     */
    public int getPanelHeight(){
        return (int) worldpanel.getScreenHeight();
    }

    /**
     * Returns true if curved path lines are enabled
     * @return
     */
    public static boolean getShowPathsCurved(){
        return show_paths_curved;
    }

    /**
     * Enables or disables curved path lines
     * @param b
     */
    public static void setShowPathsCurved(boolean b){
        show_paths_curved = b;
    }

    /**
     * Saves the changes in the world
     */
    public void save(){
        if(!passive){
            writeMeta();

            WorldFile worldFile = world.getWorldFile();

            if(worldFile == null){
                if(filename == null || filename.isEmpty() || (new File(filename)).exists()){
                    // TODO: create new filename
                    throw new UnsupportedOperationException("no filename or file exists");
                } else {
                    worldFile = new WorldFileDefault(filename);
                    world.setWorldFile(worldFile);
                }
            }

            try {
                worldFile.writeFile(world);
            } catch (IOException ex) {
                Logger.getLogger(WorldTab.class.getName()).log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog(parent,
                        "Could not save world file " + worldFile.getFilename(),
                        "Saving world file",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        showMessage("World saved");
    }

    /**
     * Show message in infobar
     * @param message
     */
    public void showMessage(String message){
        label_infobar.showMessage(message);
    }

    // ========================== Place selection ==============================
    /**
     * Get the x coordinate of the selected place
     * @return x coordinate
     */
    public int getCursorX(){
        return cursor_x;
    }

    /**
     * Get the y coordinate of the selected place
     * @return y coordinate
     */
    public int getCursorY(){
        return cursor_y;
    }

    /**
     * Set the coordinates of the selected place
     * @param x x coordinate
     * @param y y coordinate
     */
    public void setCursor(int x, int y){
        cursor_x = x;
        cursor_y = y;
        updateInfobar();
        moveScreenToCursor();
        repaint();
        callCursorListeners();
    }

    /**
     * Moves the place selection coordinates
     * @param dx x movement
     * @param dy y movement
     */
    private void moveCursor(int dx, int dy){
        cursor_x += dx;
        cursor_y += dy;
        updateInfobar();
        moveScreenToCursor();
        repaint();
        callCursorListeners();
    }

    /**
     * moves the shown places so the selection is on the screen
     */
    private void moveScreenToCursor(){
        if(worldpanel != null){
            int screen_x = worldpanel.getScreenPosX(cursor_x);
            int screen_y = worldpanel.getScreenPosY(cursor_y);
            int tilesize = getTileSize();

            double dx = 0, dy = 0;

            if(screen_x < 0) dx = (double) screen_x / tilesize;
            else if(screen_x > worldpanel.screen_width - tilesize) dx = (double) (screen_x - worldpanel.screen_width) / tilesize + 1;
            if(screen_y < 0) dy = (double) -screen_y / tilesize;
            else if(screen_y > worldpanel.screen_height - tilesize) dy = (double) -(screen_y - worldpanel.screen_height) / tilesize - 1;

            if(dx != 0 || dy != 0) getCurPosition().move(dx, dy);
            repaint();
        }
    }

    public void updateCursorEnabled(){
        /*if(update_check_button){
            JCheckBoxMenuItem mi_show_place_selection = ((Mainwindow) parent).getMiShowPlaceSelection();
            if(mi_show_place_selection != null){
                mi_show_place_selection.setState(getCursorEnabled());
                mi_show_place_selection.setEnabled(!force_selection);
            }
        }*/

        updateInfobar();
        repaint();
    }

    /**
     * Set the cursor state (if true, the selection will be shown)
     * @param b
     */
    public void setCursorEnabled(boolean b){
        cursor_enabled = b || force_selection;
        updateCursorEnabled();
    }

    /**
     * Toggles the cursor enabled state
     */
    private void setCursorToggle(){
        if(!force_selection){
            cursor_enabled = !cursor_enabled;
            updateCursorEnabled();
        }
    }

    /**
     * Get the cursor state
     * @return
     */
    public boolean getCursorEnabled(){
        return cursor_enabled || force_selection;
    }

    /**
     * Forces the cursor to be enabled, if true
     * @param b
     */
    public void setCursorForced(boolean b){
        if(force_selection = b) setCursorEnabled(true);
        updateCursorEnabled();
    }

    /**
     * Get the currently shown position
     * @return current position
     */
    public WorldCoordinate getCurPosition(){
        return positions.get(positions_cur_index);
        //return positions.getFirst();
    }

    /**
     * Pushes a new position on the position stack ("goto")
     * @param _pos new position
     */
    public void pushPosition(WorldCoordinate _pos){
        WorldCoordinate pos = _pos.clone();
        // remove all entries after the current one
        while(positions_cur_index > 0){
            positions.pop();
            positions_cur_index--;
        }
        // add new position
        positions.push(pos);

        // move place selection
        setCursor((int) pos.getX(), (int) pos.getY());
        while(positions.size() > history_max_length) positions.removeLast();
        repaint();
    }

    /**
     * Removes the first position from the position stack,
     * go to home position if the stack is empty
     */
    public void popPosition(){
        // if end not reached
        if(positions_cur_index < positions.size() - 1) positions_cur_index++;
        // add home coord at list end (unlike gotoHome())
        else positions.addLast(getWorld().getHome());

        //if(positions.size() > 0) positions.removeFirst();
        //if(positions.size() == 0) gotoHome();

        setCursor((int) getCurPosition().getX(), (int) getCurPosition().getY());
        repaint();
    }

    public void restorePosition(){
        if(positions_cur_index > 0){
            positions_cur_index--;
            setCursor((int) getCurPosition().getX(), (int) getCurPosition().getY());
            repaint();
        }
    }

    /**
     * Updates the infobar
     */
    private void updateInfobar(){
        if(label_infobar != null ){
            if(getCursorEnabled()){
                Layer layer = world.getLayer(getCurPosition().getLayer());
                if(layer != null && layer.exist(getCursorX(), getCursorY())){
                    Place pl;
                        pl = (Place) layer.get(getCursorX(), getCursorY());

                        boolean has_area = pl.getArea() != null;
                        boolean has_comments = pl.getComments().size() != 0;

                        String infotext = pl.getName();
                        if(has_area || has_comments) infotext += " (";
                        if(has_area) infotext += pl.getArea().getName();
                        if(has_comments) infotext += (has_area ? ", " : "") + pl.getCommentsString(false);
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
    public void resetHistory(WorldCoordinate pos){
        positions.clear();
        positions.add(pos);
        cursor_x = (int) Math.round(pos.getX());
        cursor_y = (int) Math.round(pos.getY());
        updateInfobar();
        repaint();
    }

    /**
     * Go to the home position
     */
    public void gotoHome(){
        pushPosition(world.getHome());
        setCursor((int) Math.round(getCurPosition().getX()), (int) Math.round(getCurPosition().getY()));
    }

    /**
     * Set a new home position
     */
    public void setHome(){
        world.setHome(getCurPosition().clone());
    }

    /**
     * Get a place on the current layer
     * @param x x coordinate
     * @param y y coordinate
     * @return place or null
     */
    public Place getPlace(int x, int y){
        Place ret = null;
        Layer layer = world.getLayer(getCurPosition().getLayer());
        if(layer != null) ret = (Place) layer.get(x, y);
        return ret;
    }

    /**
     * Get the selected place or null
     * @return place or null
     */
    public Place getSelectedPlace(){
        return getPlace(getCursorX(), getCursorY());
    }

    /**
     * Get the current tile size
     * @return tile size
     */
    public int getTileSize(){
        return (int) tile_size;
    }

    /**
     * set the tile size
     * @param ts new tile size
     */
    public void setTileSize(double ts){
        tile_size = Math.min(Math.max(ts, tile_size_min), tile_size_max);
        slider_zoom.setValue((int) (100.0 / tile_size_max * tile_size));
        repaint();
    }

    /**
     * increases the tile size
     */
    public void tileSizeIncrement(){
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
    public void tileSizeDecrement(){
        double ts = tile_size;
        ts = Math.exp(Math.log(ts / 10) - 0.02) * 10;
        ts = Math.max(ts, tile_size_min);
        tile_size = Math.max(Math.min(ts, tile_size - 1), tile_size_min);

        //if(tile_size > tile_size_min) tile_size--;

        slider_zoom.setValue((int) (100.0 / tile_size_max * tile_size));
        repaint();
    }


    /**
     * Set whether a context menu is shown, to disable forced focus
     * @param b
     */
    private void setContextMenu(boolean b) {
        is_context_menu_shown = b;
    }

    /**
     * Returns true, if a context menu is shown and forced focus is disabled
     * @return
     */
    private boolean hasContextMenu(){
        return is_context_menu_shown;
    }

    /**
     * Manually disables the forced focus
     * @param b
     */
    public void setForcedFocusDisabled(boolean b){
        forced_focus_disabled = b;
    }

    /**
     * Returns true, if forced focus is disabled manually
     * @return
     */
    public boolean getForcedFocusDisabled(){
        return forced_focus_disabled;
    }

    /**
     * Returs true, if forced focus can be enabled
     * @return
     */
    private boolean getForcedFocus(){
        return !forced_focus_disabled && !is_context_menu_shown;
    }

    // ========================= selection listener ============================
    /**
     * Adds a place selection listener
     * @param listener
     */
    public void addCursorListener(CursorListener listener){
        if(!cursor_listeners.contains(listener))
            cursor_listeners.add(listener);
    }

    /**
     * Removes a place selection listener
     * @param listener
     */
    public void removeCursorListener(CursorListener listener){
        cursor_listeners.remove(listener);
    }

    /**
     * calls all place selection listeners
     */
    private void callCursorListeners(){
        Place place = getPlace(getCursorX(), getCursorY());

        if(cursor_listeners != null) {
            if(place != null)
                for(CursorListener listener: cursor_listeners)
                    listener.placeSelected(place);
            else{
                Layer layer = getWorld().getLayer(getCurPosition().getLayer());
                for(CursorListener listener: cursor_listeners)
                    listener.placeDeselected(layer, getCursorX(), getCursorY());
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
    private void placeGroupBoxResetSelection(){
        place_group_box_end = place_group_box_start = null;
    }

    /**
     * Modifies the box/shift selection box (eg on shift + direction key)
     * @param x new coordinate
     * @param y new coordinate
     */
    private void placeGroupBoxModifySelection(int x, int y){
        place_group.clear();
        place_group_box_end = new WorldCoordinate(getCurPosition().getLayer(), x, y);
        // reset if layer changed
        if(place_group_box_start != null && place_group_box_start.getLayer() != place_group_box_end.getLayer()) place_group_box_start = null;
        // set start, if not set
        if(place_group_box_start == null) place_group_box_start = place_group_box_end;
    }

    /**
     * Moves the box/shift selection to the selected places list
     */
    private void placeGroupBoxSelectionToList(){
        if(place_group_box_end != null && place_group_box_start != null){
            int x1 = (int) Math.round(place_group_box_end.getX());
            int x2 = (int) Math.round(place_group_box_start.getX());
            int y1 = (int) Math.round(place_group_box_end.getY());
            int y2 = (int) Math.round(place_group_box_start.getY());

            int x_min = Math.min(x1, x2);
            int x_max = Math.max(x1, x2);
            int y_min = Math.min(y1, y2);
            int y_max = Math.max(y1, y2);

            Layer layer = world.getLayer(place_group_box_end.getLayer());

            for(int x = x_min; x <= x_max; ++x){
                for(int y = y_min; y <= y_max; ++y){
                    Place pl = (Place) layer.get(x, y);
                    if(pl != null) place_group.add(pl);
                }
            }
        }
        placeGroupBoxResetSelection();
    }

    /**
     * adds a place to the place selection list (eg on ctrl + click)
     * @param pl
     */
    private void placeGroupAdd(Place pl){
        placeGroupBoxSelectionToList();
        // clear list, if new place is on a different layer
        if(!place_group.isEmpty() && place_group.iterator().next().getLayer() != pl.getLayer()) place_group.clear();
        if(pl != null){
            if(place_group.contains(pl)) place_group.remove(pl);
            else place_group.add(pl);
        }
    }

    /**
     * Sets the selection to a new set
     * @param set
     */
    private void placeGroupSet(HashSet<Place> set){
        place_group.clear();
        place_group = set;
    }

    /**
     * Clears the selected places list and the shift selection
     */
    private void placeGroupReset(){
        place_group.clear();
        placeGroupBoxResetSelection();
    }

    /**
     * Returns true, if places are selected
     * @return
     */
    public boolean placeGroupHasSelection(){
        return (place_group_box_start != null && place_group_box_end != null) || !place_group.isEmpty();
    }

    /**
     * gets all selected places
     * @return
     */
    public HashSet<Place> placeGroupGetSelection(){
        if(place_group_box_start != null) placeGroupBoxSelectionToList();
        return place_group;
    }

    /**
     * Returns true, if a place is selected by group selection
     * @param place
     * @return
     */
    private boolean placeGroupIsSelected(Place place){
        if(place != null){
            if(place_group_box_end != null && place_group_box_start != null
                && place_group_box_end.getLayer() == place.getLayer().getId()){
                int x1 = (int) Math.round(place_group_box_end.getX());
                int x2 = (int) Math.round(place_group_box_start.getX());
                int y1 = (int) Math.round(place_group_box_end.getY());
                int y2 = (int) Math.round(place_group_box_start.getY());

                int x_min = Math.min(x1, x2);
                int x_max = Math.max(x1, x2);
                int y_min = Math.min(y1, y2);
                int y_max = Math.max(y1, y2);

                if(place.getX() >= x_min && place.getX() <= x_max
                    && place.getY() >= y_min && place.getY() <= y_max) return true;
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
    private void loadMeta(){
        if(getFilename() != null){
            String file = getFilename() + "_meta";
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));

                String line;
                int layer_id = world.getHome().getLayer();
                double pos_x = world.getHome().getX();
                double pos_y = world.getHome().getY();

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
                                if(positions.size() == 0 || !getCurPosition().equals(newcoord)) pushPosition(newcoord);
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

                pushPosition(new WorldCoordinate(layer_id, pos_x, pos_y));

            } catch (FileNotFoundException ex) {
                System.out.println("Couldn't open world meta file \"" + file + "\", file not found");
                //Logger.getLogger(WorldManager.class.getName()).log(Level.INFO, null, ex);

                pushPosition(world.getHome());
            }
        }
    }

    /**
     * Saves the world meta file
     */
    public void writeMeta(){
        if(!passive){
            try {
                // open file
                if(!Paths.isDirectory(Paths.getWorldsDir())) Paths.createDirectory(Paths.getWorldsDir());
                File file = new File(getFilename() + "_meta");
                file.getParentFile().mkdirs();
                PrintWriter outstream = new PrintWriter(new BufferedWriter(new FileWriter(file)));

                outstream.println("# MUD Map (v2) world meta data file");
                outstream.println("ver " + meta_file_ver_major + "." + meta_file_ver_minor);

                // tile size
                outstream.println("tile_size " + (int) tile_size);

                // write whether the place selection is shown
                outstream.println("enable_place_selection " + getCursorEnabled());

                // write current position and position history
                outstream.println("lp " + getCurPosition().getMetaString());

                // shown place history
                for(Iterator<WorldCoordinate> wcit = positions.descendingIterator(); wcit.hasNext();){
                    WorldCoordinate next = wcit.next();
                    if(next != getCurPosition()) outstream.println("pcv " + next.getMetaString());
                }

                outstream.close();
            } catch (IOException ex) {
                System.out.printf("Couldn't write world meta file " + getFilename()+ "_meta");
                Logger.getLogger(WorldTab.class.getName()).log(Level.WARNING, null, ex);
            }
        }
    }

    private static class WorldPanel extends JPanel {
        private static final long serialVersionUID = 1L;

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
                    if(parent.getForcedFocus()) requestFocusInWindow();
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
                    double ts = parent.getTileSize();
                    ts = Math.exp(Math.log(ts / 10) + e.getWheelRotation() * 0.05) * 10;
                    if(e.getWheelRotation() > 0) ts = Math.max(ts, parent.getTileSize() + 1);
                    else if(e.getWheelRotation() < 0) ts = Math.min(ts, parent.getTileSize() - 1);
                    parent.setTileSize(ts);
                    //parent.setTileSize(parent.getTileSize() + e.getWheelRotation());
                }
            });
            addMouseMotionListener(new TabMouseMotionListener());
        }

        /**
         * Gets the screen width
         * @return screen width
         */
        public double getScreenWidth(){
            return screen_width;
        }

        /**
         * Gets the screen height
         * @return screen height
         */
        public double getScreenHeight(){
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
        private int getPlacePosX(int screen_x){
            return (int) Math.ceil((double) (screen_x - screen_width / 2) / parent.getTileSize() + parent.getCurPosition().getX()) - 1;
        }

        /**
         * Converts screen coordinates to world coordinates
         * @param mouse_y a screen coordinate (y-axis)
         * @return world coordinate y
         */
        private int getPlacePosY(int screen_y){
            return (int) -Math.ceil((double) (screen_y - screen_height / 2) / parent.getTileSize() - parent.getCurPosition().getY()) + 1;
        }

        /**
         * Converts world coordinates to screen coordinates
         * @param place_x a world (place) coordinate (x axis)
         * @return a screen coordinate x
         */
        private int getScreenPosX(int place_x){
            int tile_size = parent.getTileSize();
            double screen_center_x = ((double) screen_width / tile_size) / 2; // note: wdtwd2
            int place_x_offset = (int) (Math.round((double) parent.getCurPosition().getX()) - Math.round(screen_center_x));
            return (int)((place_x - place_x_offset + remint(screen_center_x) - remint(parent.getCurPosition().getX())) * tile_size);
        }

        /**
         * Converts world coordinates to screen coordinates
         * @param place_y a world (place) coordinate (y axis)
         * @return a screen coordinate y
         */
        private int getScreenPosY(int place_y){
            int tile_size = parent.getTileSize();
            double screen_center_y = ((double) screen_height / tile_size) / 2;
            int place_y_offset = (int) (Math.round(parent.getCurPosition().getY()) - Math.round(screen_center_y));
            return (int)((-place_y + place_y_offset - remint(screen_center_y) + remint(parent.getCurPosition().getY())) * tile_size + screen_height);
        }

        // ======================= DRAW WORLD HERE =============================

        @Override
        public void paintComponent(Graphics g){
            mappainter.setPlaceGroup(parent.place_group, parent.place_group_box_start, parent.place_group_box_end);
            mappainter.setPlaceSelection(parent.getCursorX(), parent.getCursorY());
            mappainter.setPlaceSelectionEnabled(parent.getCursorEnabled());

            mappainter.paint(g, parent.getTileSize(), screen_width = getWidth(), screen_height = getHeight(), parent.getWorld().getLayer(parent.getCurPosition().getLayer()), parent.getCurPosition());
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
                    TabContextMenu context_menu = new TabContextMenu(parent, getPlacePosX(arg0.getX()), getPlacePosY(arg0.getY()));
                    context_menu.show(arg0.getComponent(), arg0.getX(), arg0.getY());
                } else if(arg0.getButton() == MouseEvent.BUTTON1){ // left click
                    if(!arg0.isShiftDown()){ // left click + hift gets handled in active listener
                        // set place selection to coordinates if keyboard selection is enabled
                        parent.setCursor(getPlacePosX(arg0.getX()), getPlacePosY(arg0.getY()));
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
                    Place place = parent.getPlace(getPlacePosX(arg0.getX()), getPlacePosY(arg0.getY()));
                    if(arg0.isControlDown()){ // left click + ctrl
                        if(place != null) parent.placeGroupAdd(place);
                    } else if(!arg0.isShiftDown()) { // left click and not shift
                        parent.placeGroupReset();
                        if(arg0.getClickCount() > 1){ // double click
                            if(place != null) (new PlaceDialog(parent.parent, parent.getWorld(), place)).setVisible(true);
                            else (new PlaceDialog(parent.parent, parent.world, parent.getWorld().getLayer(parent.getCurPosition().getLayer()), getPlacePosX(arg0.getX()), getPlacePosY(arg0.getY()))).setVisible(true);
                        }
                    } else {
                        if(!parent.placeGroupHasSelection())
                            parent.placeGroupBoxModifySelection(parent.getCursorX(), parent.getCursorY());
                        parent.placeGroupBoxModifySelection(getPlacePosX(arg0.getX()), getPlacePosY(arg0.getY()));
                        // cursor has to be set after the selection -> not handled by passive listener
                        parent.setCursor(getPlacePosX(arg0.getX()), getPlacePosY(arg0.getY()));
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
                    double dx = (double) (arg0.getX() - parent.mouse_x_previous) / parent.getTileSize();
                    double dy = (double) (arg0.getY() - parent.mouse_y_previous) / parent.getTileSize();
                    if(!arg0.isShiftDown()) // shift not pressed: move view
                        parent.getCurPosition().move(-dx , dy);
                    else { // shift pressed: box selection
                        parent.placeGroupBoxModifySelection(getPlacePosX(arg0.getX()), getPlacePosY(arg0.getY()));
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
                    int x_bef = parent.getCursorX();
                    int y_bef = parent.getCursorY();

                    switch(arg0.getKeyCode()){
                        // zoom the map
                        case KeyEvent.VK_PLUS:
                        case KeyEvent.VK_ADD:
                        case KeyEvent.VK_PAGE_UP:
                            parent.tileSizeIncrement();
                            break;
                        case KeyEvent.VK_MINUS:
                        case KeyEvent.VK_SUBTRACT:
                        case KeyEvent.VK_PAGE_DOWN:
                            parent.tileSizeDecrement();
                            break;

                        // enable / disable cursor
                        case KeyEvent.VK_P:
                            parent.setCursorToggle();
                            break;

                        // shift place selection - wasd
                        case KeyEvent.VK_NUMPAD8:
                        case KeyEvent.VK_UP:
                        case KeyEvent.VK_W:
                            if(parent.getCursorEnabled()) parent.moveCursor(0, +1);
                            break;
                        case KeyEvent.VK_NUMPAD4:
                        case KeyEvent.VK_LEFT:
                        case KeyEvent.VK_A:
                            if(parent.getCursorEnabled()) parent.moveCursor(-1, 0);
                            break;
                        case KeyEvent.VK_NUMPAD2:
                        case KeyEvent.VK_DOWN:
                        case KeyEvent.VK_S:
                            if(parent.getCursorEnabled()) parent.moveCursor(0, -1);
                            break;
                        case KeyEvent.VK_NUMPAD6:
                        case KeyEvent.VK_RIGHT:
                        case KeyEvent.VK_D:
                            if(parent.getCursorEnabled()) parent.moveCursor(+1, 0);
                            break;

                        // diagonal movement
                        case KeyEvent.VK_NUMPAD1:
                            if(parent.getCursorEnabled()) parent.moveCursor(-1, -1);
                            break;
                        case KeyEvent.VK_NUMPAD3:
                            if(parent.getCursorEnabled()) parent.moveCursor(+1, -1);
                            break;
                        case KeyEvent.VK_NUMPAD7:
                            if(parent.getCursorEnabled()) parent.moveCursor(-1, +1);
                            break;
                        case KeyEvent.VK_NUMPAD9:
                            if(parent.getCursorEnabled()) parent.moveCursor(+1, +1);
                            break;

                        // goto home
                        case KeyEvent.VK_NUMPAD5:
                        case KeyEvent.VK_H:
                        case KeyEvent.VK_HOME:
                            parent.gotoHome();
                            break;

                        // show place list
                        case KeyEvent.VK_L:
                            (new PlaceListDialog(parent, passive)).setVisible(true);
                            break;

                        // reset place group selection
                        case KeyEvent.VK_ESCAPE:
                            parent.placeGroupReset();
                            break;
                    }

                    int x_sel = parent.getCursorX();
                    int y_sel = parent.getCursorY();

                    // change group selection, if place selection changed
                    if(x_sel != x_bef || y_sel != y_bef){
                        if(parent.place_group_box_start != null) parent.placeGroupBoxSelectionToList();
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
                            parent.placeGroupSet(parent.getWorld().getLayer(parent.getCurPosition().getLayer()).getPlaces());
                            break;
                        case KeyEvent.VK_X: // cut selected places
                            if(!parent.placeGroupGetSelection().isEmpty()){ // cut group selection
                                mudmap2.CopyPaste.cut(parent.place_group, parent.getCursorX(), parent.getCursorY());
                                parent.showMessage(parent.place_group.size() + " places cut");
                                parent.placeGroupReset();
                            } else if(parent.getSelectedPlace() != null){ // cut cursor selection
                                HashSet<Place> tmp_selection = new HashSet<Place>();
                                tmp_selection.add(parent.getSelectedPlace());
                                mudmap2.CopyPaste.cut(tmp_selection, parent.getCursorX(), parent.getCursorY());
                                parent.showMessage("1 place cut");
                            } else parent.showMessage("No places cut: selection empty");
                            break;
                        case KeyEvent.VK_C: // copy selected places
                            if(!parent.placeGroupGetSelection().isEmpty()){ // copy group selection
                                mudmap2.CopyPaste.copy(parent.place_group, parent.getCursorX(), parent.getCursorY());
                                parent.showMessage(parent.place_group.size() + " places copied");
                                parent.placeGroupReset();
                            } else if(parent.getSelectedPlace() != null){ // copy cursor selection
                                HashSet<Place> tmp_selection = new HashSet<Place>();
                                tmp_selection.add(parent.getSelectedPlace());
                                mudmap2.CopyPaste.copy(tmp_selection, parent.getCursorX(), parent.getCursorY());
                                parent.showMessage("1 place copied");
                            } else {
                                mudmap2.CopyPaste.resetCopy();
                                parent.showMessage("No places copied: selection empty");
                            }
                            break;
                        case KeyEvent.VK_V: // paste copied / cut places
                            if(mudmap2.CopyPaste.hasCopyPlaces()){
                                if(mudmap2.CopyPaste.canPaste(parent.getCursorX(), parent.getCursorY(), parent.getWorld().getLayer(parent.getCurPosition().getLayer()))){
                                    int paste_num = mudmap2.CopyPaste.getCopyPlaces().size();
                                    if(mudmap2.CopyPaste.paste(parent.getCursorX(), parent.getCursorY(), parent.getWorld().getLayer(parent.getCurPosition().getLayer()))){
                                        parent.showMessage(paste_num + " places pasted");
                                    } else {
                                        parent.showMessage("No places pasted");
                                    }
                                } else {
                                    parent.showMessage("Can't paste: not enough free space on map");
                                }
                            } else {
                                mudmap2.CopyPaste.resetCopy();
                                parent.showMessage("Can't paste: no places cut or copied");
                            }
                            break;

                        case KeyEvent.VK_NUMPAD8:
                        case KeyEvent.VK_UP:
                        //case KeyEvent.VK_W: // add path to direction 'n'
                            place = parent.getSelectedPlace();
                            other = parent.getPlace(parent.getCursorX(), parent.getCursorY() + 1);
                            if(place != null && other != null){ // if places exist
                                if(place.getExit("n") == null && other.getExit("s") == null){ // if exits aren't occupied
                                    place.connectPath(new Path(place, "n", other, "s"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD9: // add path to direction 'ne'
                            place = parent.getSelectedPlace();
                            other = parent.getPlace(parent.getCursorX() + 1, parent.getCursorY() + 1);
                            if(place != null && other != null){ // if places exist
                                if(place.getExit("ne") == null && other.getExit("sw") == null){ // if exits aren't occupied
                                    place.connectPath(new Path(place, "ne", other, "sw"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD6:
                        case KeyEvent.VK_RIGHT:
                        //case KeyEvent.VK_D: // add path to direction 'e'
                            place = parent.getSelectedPlace();
                            other = parent.getPlace(parent.getCursorX() + 1, parent.getCursorY());
                            if(place != null && other != null){ // if places exist
                                if(place.getExit("e") == null && other.getExit("w") == null){ // if exits aren't occupied
                                    place.connectPath(new Path(place, "e", other, "w"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD3: // add path to direction 'se'
                            place = parent.getSelectedPlace();
                            other = parent.getPlace(parent.getCursorX() + 1, parent.getCursorY() - 1);
                            if(place != null && other != null){ // if places exist
                                if(place.getExit("se") == null && other.getExit("nw") == null){ // if exits aren't occupied
                                    place.connectPath(new Path(place, "se", other, "nw"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD2:
                        case KeyEvent.VK_DOWN:
                        //case KeyEvent.VK_S: // add path to direction 's'
                            place = parent.getSelectedPlace();
                            other = parent.getPlace(parent.getCursorX(), parent.getCursorY() - 1);
                            if(place != null && other != null){ // if places exist
                                if(place.getExit("s") == null && other.getExit("n") == null){ // if exits aren't occupied
                                    place.connectPath(new Path(place, "s", other, "n"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD1: // add path to direction 'sw'
                            place = parent.getSelectedPlace();
                            other = parent.getPlace(parent.getCursorX() - 1, parent.getCursorY() - 1);
                            if(place != null && other != null){ // if places exist
                                if(place.getExit("sw") == null && other.getExit("ne") == null){ // if exits aren't occupied
                                    place.connectPath(new Path(place, "sw", other, "ne"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD4:
                        case KeyEvent.VK_LEFT:
                        //case KeyEvent.VK_A: // add path to direction 'w'
                            place = parent.getSelectedPlace();
                            other = parent.getPlace(parent.getCursorX() - 1, parent.getCursorY());
                            if(place != null && other != null){ // if places exist
                                if(place.getExit("w") == null && other.getExit("e") == null){ // if exits aren't occupied
                                    place.connectPath(new Path(place, "w", other, "e"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD7: // add path to direction 'nw'
                            place = parent.getSelectedPlace();
                            other = parent.getPlace(parent.getCursorX() - 1, parent.getCursorY() + 1);
                            if(place != null && other != null){ // if places exist
                                if(place.getExit("nw") == null && other.getExit("se") == null){ // if exits aren't occupied
                                    place.connectPath(new Path(place, "nw", other, "se"));
                                }
                            }
                            break;
                        case KeyEvent.VK_NUMPAD5: // open add path dialog
                            (new PathConnectDialog(parent, parent.getSelectedPlace())).setVisible(true);
                            break;
                    }
                } else if(arg0.isShiftDown()){ // shift key pressed -> modify selection
                    int x_bef = parent.getCursorX();
                    int y_bef = parent.getCursorY();

                    switch(arg0.getKeyCode()){
                        case KeyEvent.VK_NUMPAD8:
                        case KeyEvent.VK_UP:
                        case KeyEvent.VK_W:
                            if(parent.getCursorEnabled()) parent.moveCursor(0, +1);
                            break;
                        case KeyEvent.VK_NUMPAD4:
                        case KeyEvent.VK_LEFT:
                        case KeyEvent.VK_A:
                            if(parent.getCursorEnabled()) parent.moveCursor(-1, 0);
                            break;
                        case KeyEvent.VK_NUMPAD2:
                        case KeyEvent.VK_DOWN:
                        case KeyEvent.VK_S:
                            if(parent.getCursorEnabled()) parent.moveCursor(0, -1);
                            break;
                        case KeyEvent.VK_NUMPAD6:
                        case KeyEvent.VK_RIGHT:
                        case KeyEvent.VK_D:
                            if(parent.getCursorEnabled()) parent.moveCursor(+1, 0);
                            break;

                        // diagonal movement
                        case KeyEvent.VK_NUMPAD1:
                            if(parent.getCursorEnabled()) parent.moveCursor(-1, -1);
                            break;
                        case KeyEvent.VK_NUMPAD3:
                            if(parent.getCursorEnabled()) parent.moveCursor(+1, -1);
                            break;
                        case KeyEvent.VK_NUMPAD7:
                            if(parent.getCursorEnabled()) parent.moveCursor(-1, +1);
                            break;
                        case KeyEvent.VK_NUMPAD9:
                            if(parent.getCursorEnabled()) parent.moveCursor(+1, +1);
                            break;

                        case KeyEvent.VK_SPACE: // add or remove single place to place group selection
                            Place place = parent.getSelectedPlace();
                            if(place != null) parent.placeGroupAdd(place);
                            break;
                    }
                    int x_sel = parent.getCursorX();
                    int y_sel = parent.getCursorY();

                    // change group selection, if place selection changed
                    if(x_sel != x_bef || y_sel != y_bef){
                        if(parent.place_group_box_start == null) parent.placeGroupBoxModifySelection(x_bef, y_bef);
                        parent.placeGroupBoxModifySelection(x_sel, y_sel);
                    }
                } else if(arg0.isAltDown() || arg0.isAltGraphDown()){ // alt or altgr key pressed
                    Place place = parent.getSelectedPlace();
                    Place other;
                    Path path;

                    if(place != null){
                        switch(arg0.getKeyCode()){
                            case KeyEvent.VK_NUMPAD8:
                            case KeyEvent.VK_UP:
                            case KeyEvent.VK_W: // remove path to direction 'n'
                                    path = place.getPathTo("n");
                                    if(path != null) place.removePath(path);
                                break;
                            case KeyEvent.VK_NUMPAD9: // remove path to direction 'ne'
                                    path = place.getPathTo("ne");
                                    if(path != null) place.removePath(path);
                                break;
                            case KeyEvent.VK_NUMPAD6:
                            case KeyEvent.VK_RIGHT:
                            case KeyEvent.VK_D: // remove path to direction 'e'
                                    path = place.getPathTo("e");
                                    if(path != null) place.removePath(path);
                                break;
                            case KeyEvent.VK_NUMPAD3: // remove path to direction 'se'
                                    path = place.getPathTo("se");
                                    if(path != null) place.removePath(path);
                                break;
                            case KeyEvent.VK_NUMPAD2:
                            case KeyEvent.VK_DOWN:
                            case KeyEvent.VK_S: // remove path to direction 's'
                                    path = place.getPathTo("s");
                                    if(path != null) place.removePath(path);
                                break;
                            case KeyEvent.VK_NUMPAD1: // remove path to direction 'sw'
                                    path = place.getPathTo("sw");
                                    if(path != null) place.removePath(path);
                                break;
                            case KeyEvent.VK_NUMPAD4:
                            case KeyEvent.VK_LEFT:
                            case KeyEvent.VK_A: // remove path to direction 'w'
                                    path = place.getPathTo("w");
                                    if(path != null) place.removePath(path);
                                break;
                            case KeyEvent.VK_NUMPAD7: // remove path to direction 'nw'
                                    path = place.getPathTo("nw");
                                    if(path != null) place.removePath(path);
                                break;
                        }
                    }
                } else { // ctrl, shift and alt not pressed
                    switch(arg0.getKeyCode()){
                        // show context menu
                        case KeyEvent.VK_CONTEXT_MENU:
                            if(parent.getCursorEnabled()){
                                TabContextMenu context_menu = new TabContextMenu(parent, parent.getCursorX(), parent.getCursorY());
                                context_menu.show(arg0.getComponent(), getScreenPosX(parent.getCursorX()) + worldpanel.parent.getTileSize() / 2, getScreenPosY(parent.getCursorY()) + worldpanel.parent.getTileSize() / 2);
                            }
                            break;

                        // edit / add place
                        case KeyEvent.VK_INSERT:
                        case KeyEvent.VK_ENTER:
                        case KeyEvent.VK_E:
                            if(parent.getCursorEnabled()){
                                Place place = parent.getSelectedPlace();
                                PlaceDialog dlg;

                                Layer layer = null;
                                if(parent.getCurPosition() != null) layer = parent.getWorld().getLayer(parent.getCurPosition().getLayer());

                                if(place != null) dlg = new PlaceDialog(parent.parent, parent.world, place);
                                else dlg = new PlaceDialog(parent.parent, parent.world, parent.world.getLayer(parent.getCurPosition().getLayer()), parent.getCursorX(), parent.getCursorY());
                                dlg.setVisible(true);

                                if(layer == null) parent.pushPosition(dlg.getPlace().getCoordinate());
                            }
                            break;
                        // create placeholder
                        case KeyEvent.VK_F:
                            if(parent.getCursorEnabled()){
                                Place place = parent.getSelectedPlace();
                                // create placeholder or remove one
                                if(place == null){
                                    parent.world.putPlaceholder(parent.getCurPosition().getLayer(), parent.getCursorX(), parent.getCursorY());
                                } else if(place.getName().equals(Place.placeholderName)){
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
                            if(!parent.placeGroupHasSelection()){ // no places selected
                                if(parent.getCursorEnabled()){
                                    Place place = parent.getSelectedPlace();
                                    if(place != null) (new PlaceRemoveDialog(parent.parent, parent.world, place)).show();
                                }
                            } else { // places selected
                                HashSet<Place> place_group = parent.placeGroupGetSelection();
                                if(place_group != null){
                                    PlaceRemoveDialog dlg = new PlaceRemoveDialog(parent.parent, parent.world, place_group);
                                    dlg.show();
                                    // reset selection, if places were removed
                                    if(dlg.getPlacesRemoved()) parent.placeGroupReset();
                                }
                            }
                            break;
                        // edit place comments
                        case KeyEvent.VK_C:
                            if(parent.getCursorEnabled()){
                                Place place = parent.getSelectedPlace();
                                if(place != null){
                                    (new PlaceCommentDialog(parent.parent, place)).setVisible(true);
                                    parent.updateInfobar();
                                }
                            }
                            break;
                        // modify area
                        case KeyEvent.VK_Q:
                            Place place = parent.getSelectedPlace();

                            if(!parent.placeGroupHasSelection()){
                                // no place selected
                                if(place == null) (new AreaDialog(parent.parent, parent.world)).setVisible(true);
                                // place selected
                                else (new AreaDialog(parent.parent, parent.world, place)).setVisible(true);
                            } else { // place group selection
                                (new AreaDialog(parent.parent, parent.world, parent.placeGroupGetSelection(), place)).setVisible(true);
                            }
                            break;

                        case KeyEvent.VK_SPACE: // add or remove single place to place group selection
                            place = parent.getSelectedPlace();
                            if(place != null) parent.placeGroupAdd(place);
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
                final Layer layer = parent.world.getLayer(parent.getCurPosition().getLayer());

                final Place place = (layer != null ? (Place) layer.get(px, py) : null);
                final boolean has_place = layer != null && place != null;

                if(has_place){ // if place exists
                    if(!parent.passive){
                        JMenuItem mi_edit = new JMenuItem("Edit place");
                        PlaceDialog pdlg = new PlaceDialog(parent.parent, parent.world, place);
                        mi_edit.addActionListener(pdlg);
                        if(layer == null) parent.pushPosition(pdlg.getPlace().getCoordinate());

                        add(mi_edit);
                        mi_edit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0));

                        HashSet<Place> place_group = parent.placeGroupGetSelection();

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
                    if(!parent.passive || !place.getPaths().isEmpty())
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

                        LinkedList<Place> places = layer.getNeighbors(px, py, 1);
                        if(!places.isEmpty()){
                            m_path_connect.add(new JSeparator());

                            for(LayerElement neighbor: places){
                                // only show, if no connection exists, yet
                                if(place.getPaths((Place) neighbor).isEmpty()){
                                    String dir1 = "", dir2 = "";

                                    if(neighbor.getY() > place.getY())
                                        {dir1 = "n"; dir2 = "s";}
                                    else if(neighbor.getY() < place.getY())
                                        {dir1 = "s"; dir2 = "n";}
                                    if(neighbor.getX() > place.getX())
                                        {dir1 = dir1 + "e"; dir2 = dir2 + "w";}
                                    else if(neighbor.getX() < place.getX())
                                        {dir1 = dir1 + "w"; dir2 = dir2 + "e";}

                                    // if exits aren't occupied yet -> add menu item
                                    if(place.getPathTo(dir1) == null && ((Place) neighbor).getPathTo(dir2) == null){
                                        JMenuItem mi_path_connect = new JMenuItem("[" + dir1 + "] " + ((Place) neighbor).getName());
                                        m_path_connect.add(mi_path_connect);
                                        mi_path_connect.addActionListener(new ConnectPathActionListener(place, ((Place) neighbor), dir1, dir2));

                                        // add accelerator
                                        int dirnum = Path.getDirNum(dir1);
                                        if(dirnum > 0 & dirnum <= 9)
                                            mi_path_connect.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + dirnum, Event.CTRL_MASK));
                                    }
                                }
                            }
                        }
                    }

                    // get all connected places
                    HashSet<Path> paths = place.getPaths();

                    if(!paths.isEmpty()){
                        JMenu m_path_remove = null;
                        if(!parent.passive){
                            m_path_remove = new JMenu("Remove");
                            m_paths.add(m_path_remove);
                            m_path_remove.setToolTipText("Remove a path");

                            m_paths.add(new JSeparator());
                        }

                        for(Path path: paths){
                            Place other_place = path.getOtherPlace(place);
                            JMenuItem mi_path_goto = new JMenuItem("Go to [" + path.getExit(place) + "] " + other_place.getName());
                            m_paths.add(mi_path_goto);
                            mi_path_goto.addActionListener(new GotoPlaceActionListener(parent, other_place));

                            if(!parent.passive){
                                String dir = path.getExit(place);
                                JMenuItem mi_path_remove = new JMenuItem("Remove [" + dir + "] " + other_place.getName());
                                mi_path_remove.addActionListener(new RemovePathActionListener(path));
                                m_path_remove.add(mi_path_remove);

                                // add accelerator
                                int dirnum = Path.getDirNum(dir);
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
                                    PlaceSelectionDialog dlg = new PlaceSelectionDialog((JFrame) parent.parent, parent.world, parent.getCurPosition(), true);
                                    dlg.setVisible(true);
                                    Place end = dlg.getSelection();
                                    if(end != null){
                                        parent.placeGroupReset();
                                        Place place_it = parent.world.breadthSearch(place, end);
                                        if(place_it == null) parent.label_infobar.showMessage("No Path found");
                                        else {
                                            int path_length = 0;
                                            while(place_it != null){
                                                parent.place_group.add((Place) place_it);
                                                place_it = place_it.getBreadthSearchData().predecessor;
                                                ++path_length;
                                            }
                                            parent.label_infobar.showMessage("Path found, length: " + (path_length - 1));
                                        }

                                    }
                                }
                            });
                        }
                    }

                    // ------------- sub-areas ------------------
                    JMenu m_subareas = new JMenu("Sub-areas");
                    m_subareas.setToolTipText("Not to be confused with areas, sub-areas usually connect a place to another layer of the map, eg. a building <-> rooms inside it");
                    if(!parent.passive || !place.getChildren().isEmpty())
                        add(m_subareas);

                    if(!parent.passive){
                        JMenuItem mi_sa_connect = new JMenuItem("Connect with place");
                        m_subareas.add(mi_sa_connect);
                        mi_sa_connect.setToolTipText("Connects another place to this place as sub-area");
                        mi_sa_connect.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                PlaceSelectionDialog dlg = new PlaceSelectionDialog(parent.parent, parent.world, parent.getCurPosition(), true);
                                dlg.setVisible(true);
                                Place child = dlg.getSelection();
                                if(child != null && child != place){
                                    int ret = JOptionPane.showConfirmDialog(parent, "Connect \"" + child.getName() + "\" to \"" + place.getName() + "\"?", "Connect sub-area", JOptionPane.YES_NO_OPTION);
                                    if(ret == JOptionPane.YES_OPTION){
                                        place.connectChild(child);
                                        parent.repaint();
                                    }
                                }
                            }
                        });

                        JMenuItem mi_sa_new_layer = new JMenuItem("Add on new layer");
                        mi_sa_new_layer.setToolTipText("Creates a new place on a new layer and connects it with \"" + place.getName() + "\" as a sub-area");
                        m_subareas.add(mi_sa_new_layer);
                        mi_sa_new_layer.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent arg0) {
                                // create new place
                                PlaceDialog dlg = new PlaceDialog(parent.parent, parent.getWorld(), null, 0, 0);
                                dlg.setVisible(true);

                                Place place_new = dlg.getPlace();
                                if(place_new != null){
                                    // connect new place with place as a child
                                    place.connectChild(place_new);
                                    // go to new place
                                    parent.pushPosition(place_new.getCoordinate());
                                }
                            }
                        });
                    }

                    HashSet<Place> children = place.getChildren();
                    if(!children.isEmpty()){
                        if(!parent.passive){
                            JMenu m_sa_remove = new JMenu("Remove");
                            m_subareas.add(m_sa_remove);

                            for(Place child: children){
                                JMenuItem mi_sa_remove = new JMenuItem("Remove " + child.getName());
                                m_sa_remove.add(mi_sa_remove);
                                mi_sa_remove.addActionListener(new RemoveSubAreaActionListener(place, child));
                            }
                        }

                        m_subareas.add(new JSeparator());

                        for(Place child: children){
                            JMenuItem mi_sa_goto = new JMenuItem("Go to " + child.getName());
                            m_subareas.add(mi_sa_goto);
                            mi_sa_goto.addActionListener(new GotoPlaceActionListener(parent, child));
                        }
                    }

                    HashSet<Place> parents = place.getParents();
                    if(!parents.isEmpty()){
                        m_subareas.add(new JSeparator());

                        for(Place child: parents){
                            JMenuItem mi_sa_goto = new JMenuItem("Go to parent " + child.getName());
                            m_subareas.add(mi_sa_goto);
                            mi_sa_goto.addActionListener(new GotoPlaceActionListener(parent, child));
                        }
                    }

                }  else { // if layer doesn't exist or no place exists at position x,y
                    JMenuItem mi_new = new JMenuItem("New place");
                    mi_new.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));
                    mi_new.addActionListener(new PlaceDialog(parent.parent, parent.world, layer, px, py));
                    add(mi_new);
                    JMenuItem mi_placeholder = new JMenuItem("New placeholder");
                    add(mi_placeholder);
                    mi_placeholder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0));
                    mi_placeholder.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            // creates a placeholder place
                            parent.world.putPlaceholder(parent.getCurPosition().getLayer(), px, py);
                            parent.repaint();
                        }
                    });
                }

                // cut / copy / paste for selected places
                final boolean can_paste = layer != null && mudmap2.CopyPaste.canPaste(px, py, layer);
                final boolean has_paste_places = layer != null && mudmap2.CopyPaste.hasCopyPlaces();
                final boolean has_selection = parent.placeGroupHasSelection();

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
                                set = parent.placeGroupGetSelection();
                            } else {
                                set = new HashSet<Place>();
                                set.add(place);
                            }
                            mudmap2.CopyPaste.cut(set, px, py);
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
                                set = parent.placeGroupGetSelection();
                            } else {
                                set = new HashSet<>();
                                set.add(place);
                            }
                            mudmap2.CopyPaste.copy(set, px, py);
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
                            mudmap2.CopyPaste.paste(px, py, layer);
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
                    parent.setContextMenu(true);
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent arg0) {
                    parent.setContextMenu(false);
                    parent.repaint();
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent arg0) {
                    parent.setContextMenu(false);
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
                    if(place != null) worldtab.pushPosition(place.getCoordinate());
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
                    if(place != null && child != null) place.removeChild(child);
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
                    pl1.connectPath(new Path(pl1, dir1, pl2, dir2));
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