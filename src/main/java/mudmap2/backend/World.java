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
 *  This class contains all data of a world. Places, Layers, PlaceGroups,... can be
 *  accessed via World. It also reads and writes world files
 */

package mudmap2.backend;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import mudmap2.backend.Layer.PlaceNotInsertedException;
import mudmap2.backend.WorldFileReader.WorldFile;
import mudmap2.backend.memento.Originator;
import mudmap2.backend.memento.Memento;
import mudmap2.backend.sssp.BreadthSearchGraph;
import org.json.JSONObject;

/**
 *
 * @author neop
 */
public class World extends Originator implements BreadthSearchGraph {

    // worldname and file of the world
    private String worldName;
    private WorldFile worldFile;

    // color of path lines and self-defined path colors
    private Color pathColorCardinal = new Color(0, 255, 0);
    private Color pathColorNonCardinal = new Color(0, 255, 0);
    private Color tileCenterColor = new Color(207, 190, 134);
    private HashMap<String, Color> pathColors = new HashMap<>();

    // Coordinates of the home position
    private WorldCoordinate home = new WorldCoordinate(0, 0, 0);

    // ID and object
    private TreeMap<Integer, RiskLevel> riskLevels = new TreeMap<>();
    private HashSet<PlaceGroup> placeGroups = new HashSet<>();
    private TreeMap<Integer, Layer> layers = new TreeMap<>();

    // For creating world-unique layer ids
    private Integer nextLayerID = 1;

    // Preferences
    private ShowPlaceID showPlaceID = ShowPlaceID.UNIQUE;

    // World-related preferences for dialogs etc.
    private JSONObject preferences = new JSONObject();
    public final static String PREFERENCES_KEY_DIALOG = "dialog";

    // Listeners
    private final LinkedList<WorldChangeListener> changeListeners = new LinkedList<>();

    /**
     * Creates an empty world
     */
    public World(){
        initialize();
    }

    /**
     * Creates an empty world
     * @param name worldname of the world
     */
    public World(String name){
        worldName = name;
        initialize();
    }

    /**
     * Initializes the world
     */
    private void initialize(){
        // risk levels
        riskLevels.put(0, new RiskLevel(0, "not evaluated", new Color(188, 188, 188)));
        riskLevels.put(1, new RiskLevel(1, "safe", new Color(0, 255, 0)));
        riskLevels.put(2, new RiskLevel(2, "mobs don't attack", new Color(255, 255, 0)));
        riskLevels.put(3, new RiskLevel(3, "mobs might attack", new Color(255, 128, 0)));
        riskLevels.put(4, new RiskLevel(4, "mobs will attack", new Color(255, 0, 0)));
    }

    // --------- WorldFile -----------------------------------------------------
    /**
     * Get world file reader
     * @return WorldFileReader or null
     */
    public WorldFile getWorldFile() {
        return worldFile;
    }

    /**
     * Set world file reader
     * @param worldFile
     */
    public void setWorldFile(WorldFile worldFile) {
        this.worldFile = worldFile;
    }

    // --------- World name ----------------------------------------------------
    /**
     * Gets the world worldname
     * @return world worldname
     */
    public String getName(){
        if(worldName == null){
            return "unnamed";
        } else {
            return worldName;
        }
    }

    /**
     * Sets the world worldname
     * @param name new world worldname
     */
    public void setName(String name){
        mementoPush();
        worldName = name;
        callListeners(this);
    }

    // --------- home position -------------------------------------------------
    /**
     * Gets the home position
     * @return home coordinate
     */
    public WorldCoordinate getHome(){
        return home;
    }

    /**
     * Sets a new home position
     * @param home
     */
    public void setHome(WorldCoordinate home){
        mementoPush();
        this.home = home;
    }

    // --------- places --------------------------------------------------------

    /**
     * Creates a placeholder place
     * @param layerId layer
     * @param x x coordinate
     * @param y y coordinate
     */
    public void putPlaceholder(int layerId, int x, int y){
        try {
            Place place = new Place(Place.PLACEHOLDER_NAME, x, y, null);

            // find or create placeholder group
            PlaceGroup placeGroup = null;
            for(PlaceGroup a: placeGroups) if(a.getName().equals("placeholder")){
                placeGroup = a;
                break;
            }
            // create new placeholder group
            if(placeGroup == null) addPlaceGroup(placeGroup = new PlaceGroup("placeholder", Color.GREEN));

            place.setPlaceGroup(placeGroup);
            place.setRiskLevel(getRiskLevel(0));

            Layer layer = getLayer(layerId);
            if(layer == null){
                addLayer(new Layer(layerId, this));
                layer = getLayer(layerId);
            }
            layer.put(place, x, y);
        } catch(PlaceNotInsertedException ex){ // ignore
        } catch (Exception ex) {
            Logger.getLogger(World.class.getName()).log(Level.WARNING, "Couldn't put placeholder to map: " + ex, ex);
        }
    }

    // --------- layers --------------------------------------------------------
    /**
     * Gets a layer
     * @param id layer id
     * @return layer or null
     */
    public Layer getLayer(int id){
        return layers.get(id);
    }

    /**
     * Adds or replaces a layer
     * @param layer
     */
    public void addLayer(Layer layer){ // TODO: throw Exception if layer exists
        if(layer == null){
            throw new NullPointerException();
        }

        mementoPush();

        if(!layers.containsKey(layer.getId()))
            layers.put(layer.getId(), layer);

        layer.setMementoParent(this);
        addChangeListener(layer);
        callListeners(layer);
    }

    /**
     * Creates a new and empty layer
     * @param name layer name
     * @return new layer
     */
    public Layer getNewLayer(String name){
        Layer layer = getNewLayer();
        if(name != null && !name.isEmpty()){
            layer.setName(name);
        }
        callListeners(layer);
        return layer;
    }

    /**
     * Create a new anonymous, empty layer
     * @return new layer
     */
    public Layer getNewLayer(){
        Layer layer = new Layer(this);
        addLayer(layer);
        return layer;
    }

    /**
     * Gets the next layer id and increases the internal counter
     * @return
     */
    public Integer getNextLayerID(){
        return nextLayerID++;
    }

    /**
     * Sets the next layer id
     * @param id
     */
    public void setNextLayerID(Integer id){
        nextLayerID = id;
    }

    /**
     * Gets all layers
     * @return all layers
     */
    public Collection<Layer> getLayers(){
        return layers.values();
    }

    // --------- colors --------------------------------------------------------
    /**
     * Gets the standard path color
     * @return path color
     */
    public Color getPathColorStd(){
        return pathColorCardinal;
    }

    /**
     * Gets the color for paths that aren't predefined
     * @return path color
     */
    public Color getPathColorNstd(){
        return pathColorNonCardinal;
    }

    /**
     * Gets the color of an exit direction
     * @param dir exit direction
     * @return path color
     */
    public Color getPathColor(String dir){
        Color ret;

        if(dir == null){
            ret = getPathColorStd();
        } else if(!pathColors.containsKey(dir)){
            if(dir.equals("n") || dir.equals("s") || dir.equals("e") || dir.equals("q") ||
               dir.equals("ne") || dir.equals("nw") || dir.equals("se") || dir.equals("sw") ||
               dir.equals("w") || dir.equals("e"))
                ret = pathColorCardinal;
            else ret = pathColorNonCardinal;
        } else ret = pathColors.get(dir);

        return ret;
    }

    /**
     * Gets exit direction colors (without default colors)
     * @return
     */
    public HashMap<String, Color> getPathColors(){
        return pathColors;
    }

    /**
     * Sets the color of an exit direction
     * @param dir
     * @param color
     */
    public void setPathColor(String dir, Color color){
        if(dir == null){
            throw new NullPointerException();
        }
        if(color == null){
            throw new NullPointerException();
        }

        mementoPush();

        pathColors.put(dir, color);
        callListeners(this);
    }

    /**
     * Sets the path color
     * @param color new color
     */
    public void setPathColorStd(Color color){
        if(color == null){
            throw new NullPointerException();
        }

        mementoPush();

        pathColorCardinal = color;
        callListeners(this);
    }

    /**
     * Sets the color for paths that aren't predefined
     * @param color
     */
    public void setPathColorNstd(Color color){
        if(color == null){
            throw new NullPointerException();
        }

        mementoPush();

        pathColorNonCardinal = color;
        callListeners(this);
    }

    /**
     * Gets the tile center color
     * @return
     */
    public Color getTileCenterColor(){
        return tileCenterColor;
    }

    /**
     * Sets the tile center color
     * @param color
     */
    public void setTileCenterColor(Color color){
        if(color == null){
            throw new NullPointerException();
        }

        mementoPush();

        tileCenterColor = color;
        callListeners(this);
    }

    // --------- config --------------------------------------------------------
    public enum ShowPlaceID {
        NONE, // don't show place ID on map
        UNIQUE, // show place ID if worldname isn't unique
        ALL // always show place ID
    }

    /**
     * Sets whether the place ID is shown on the map
     * @param show
     */
    public void setShowPlaceID(ShowPlaceID show){
        showPlaceID = show;
        callListeners(this);
    }

    /**
     * Gets the in which case the place ID is shown on the map
     * @return
     */
    public ShowPlaceID getShowPlaceId(){
        return showPlaceID;
    }

    // --------- PlaceGroupss ---------------------------------------------------------
    /**
     * Gets all PlaceGroupss (eg. for lists)
     * @return all PlaceGroupss
     */
    public ArrayList<PlaceGroup> getPlaceGroups(){
        ArrayList<PlaceGroup> ret = new ArrayList<>(placeGroups);
        Collections.sort(ret);
        return ret;
    }

    /**
     * Adds a PlaceGroup
     * @param placeGroup new PlaceGroup
     */
    public void addPlaceGroup(PlaceGroup placeGroup) {
        if(placeGroup == null){
            throw new NullPointerException();
        }

        mementoPush();

        if(!placeGroups.contains(placeGroup)){
            placeGroups.add(placeGroup);
        }
        callListeners(placeGroup);
    }

    /**
     * Removes a PlaceGroup
     * @param placeGroup PlaceGroup to be removed
     */
    public void removePlaceGroup(PlaceGroup placeGroup){
        mementoPush();

        for(Layer layer: getLayers()){
            for(Place p: layer.getPlaces()){
                if(p.getPlaceGroup() == placeGroup) p.setPlaceGroup(null);
            }
        }
        placeGroups.remove(placeGroup);
        callListeners(placeGroup);
    }

    // --------- risk levels ---------------------------------------------------
    /**
     * Gets all risk levels (eg. for lists)
     * @return all risk levels
     */
    public Collection<RiskLevel> getRiskLevels(){
        return riskLevels.values();
    }

    /**
     * Gets a risk level
     * @param id risk level id
     * @return risk level
     */
    public RiskLevel getRiskLevel(int id){
        return riskLevels.get(id);
    }

    /**
     * Adds a risk level, assignes a unique id
     * @param rl new risk level
     */
    public void addRiskLevel(RiskLevel rl){
        if(rl == null){
            throw new NullPointerException();
        }

        mementoPush();

        if(!riskLevels.containsValue(rl)){
            // ID-collision?
            while(riskLevels.containsKey(rl.getId())){
                ++rl.id;
            }
            riskLevels.put(rl.getId(), rl);
        }

        callListeners(rl);
    }

    /**
     * Adds or replaces a risk level by it's id
     * @param rl risk level to add or replace
     */
    public void setRiskLevel(RiskLevel rl){
        mementoPush();
        riskLevels.put(rl.getId(), rl);
    }

    /**
     * Removes a risk level
     * @param rl
     * @throws java.lang.Exception
     */
    public void removeRiskLevel(RiskLevel rl) throws Exception {
        if(rl != null){
            mementoPush();

            if(!riskLevels.containsValue(rl)) throw new Exception("Tried to remove risk level that does not belong to this world");
            // remode from risk level list
            riskLevels.remove(rl.getId());
            // removePlace from places
            for(Layer layer: getLayers()){
                for(Place place: layer.getPlaces()){
                    if(place.getRiskLevel() == rl) place.setRiskLevel(null);
                }
            }

            callListeners(rl);
        }
    }

    // --------- preference ----------------------------------------------------
    /**
     * Get preferences object
     * @return JSON object
     */
    public JSONObject getPreferences() {
        return preferences;
    }

    /**
     * Replace preferences object
     * @param preferences JSON object
     */
    public void setPreferences(JSONObject preferences) {
        this.preferences = preferences;
    }

    // --------- path finding --------------------------------------------------
    /**
     * does a breadth search
     * @param start start place
     * @param end end place
     * @return end place or null. Following the predecessors of this path leads
     * to the start place
     */
    @Override
    public Place breadthSearch(Place start, Place end) {
        for(Layer layer: getLayers()){
            for(Place place: layer.getPlaces()){
                place.breadthSearchReset();
            }
        }
        start.getBreadthSearchData().marked = true;

        LinkedList<Place> queue = new LinkedList<>();
        queue.add(start);

        while(!queue.isEmpty()){
            Place v = queue.pollFirst();
            if(v == end) return v;

            for(Path pa: v.getPaths()){
                Place vi = pa.getOtherPlace(v);
                if(!vi.getBreadthSearchData().marked && vi != v){
                    vi.getBreadthSearchData().marked = true;
                    vi.getBreadthSearchData().predecessor = v;
                    queue.addLast(vi);
                }
            }
        }
        return null;
    }

    // --------- listeners -----------------------------------------------------
    /**
     * Add change listener
     * @param listener listener to add
     */
    public void addChangeListener(WorldChangeListener listener){
        if(!changeListeners.contains(listener)) changeListeners.add(listener);
    }

    /**
     * Remove change listener
     * @param listener listener to remove
     */
    public void removeChangeListener(WorldChangeListener listener){
        changeListeners.remove(listener);
    }

    /**
     * Call listeners
     * @param source changed object
     */
    public void callListeners(Object source){
        for(WorldChangeListener listener: changeListeners){
            listener.worldChanged(source);
        }
    }


    @Override
    protected Memento createMemento() {
        return new WorldMemento(this);
    }

    @Override
    protected void applyMemento(Memento memento) {
        if(memento instanceof WorldMemento){
            ((WorldMemento) memento).restore(this);
        }
    }

    private class WorldMemento implements Memento {

        private final String worldName;

        // color of path lines and self-defined path colors
        private final Color pathColorCardinal;
        private final Color pathColorNonCardinal;
        private final Color tileCenterColor;
        private final HashMap<String, Color> pathColors;

        // Coordinates of the home position
        private final WorldCoordinate home;

        // ID and object
        private final TreeMap<Integer, RiskLevel> riskLevels;
        private final HashSet<PlaceGroup> placeGroups;
        private final TreeMap<Integer, Layer> layers;

        public WorldMemento(World world) {
            worldName = world.getName();

            pathColorCardinal = world.pathColorCardinal;
            pathColorNonCardinal = world.pathColorNonCardinal;
            tileCenterColor = world.tileCenterColor;
            pathColors = new HashMap(world.pathColors);

            home = world.home;

            riskLevels = new TreeMap<>(world.riskLevels);
            placeGroups = new HashSet<>(world.placeGroups);
            layers = new TreeMap<>(world.layers);
        }

        public void restore(World world) {
            world.worldName = worldName;

            world.pathColorCardinal = pathColorCardinal;
            world.pathColorNonCardinal = pathColorNonCardinal;
            world.tileCenterColor = tileCenterColor;
            world.pathColors = new HashMap<>(pathColors);

            world.home = home;

            world.riskLevels = new TreeMap<>(riskLevels);
            world.placeGroups = new HashSet(placeGroups);
            world.layers = new TreeMap<>(layers);
        }

    }

}
