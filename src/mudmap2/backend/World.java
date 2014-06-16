package mudmap2.backend;

import mudmap2.Paths;
import mudmap2.Pair;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author neop
 */
public class World {
    
    public static final int file_version_major = 1;
    public static final int file_version_minor = 5;
    public static final int file_version_build = 0;
    
    public static boolean compatibility_mudmap_1 = true;
    
    // name and file of the world
    String name, file;
    // color of path lines and self-defined path colors
    Color path_color, path_color_nstd;
    // Coordinates of the home position
    int home_layer;
    double home_x, home_y;
    
    HashMap<Integer, RiskLevel> risk_levels;
    HashMap<Integer, Area> areas;
    HashMap<Integer, Place> places;
    HashMap<Integer, Layer> layers;
    
    /**
     * Loads a world from a file
     * @param _file world file
     */
    public World(String _file) throws Exception{
        file = _file;
        initialize();
        load_world();
    }
    
    /**
     * Creates an empty world
     * @param file new world file
     * @param name name of the world
     */
    public World(String _file, String _name){
        name = _name;
        file = _file;
        initialize();
    }
    
    /**
     * Initializes the world
     */
    private void initialize(){
        // path line colors
        path_color_nstd = path_color = new Color(0, 255, 0);
        // risk levels
        risk_levels = new HashMap<Integer, RiskLevel>();
        risk_levels.put(0, new RiskLevel(0, "not evaluated", new Color(188, 188, 188)));
        risk_levels.put(1, new RiskLevel(1, "secure", new Color(0, 255, 0)));
        risk_levels.put(2, new RiskLevel(2, "mobs don't attack", new Color(255, 255, 0)));
        risk_levels.put(3, new RiskLevel(3, "mobs might attack", new Color(255, 128, 0)));
        risk_levels.put(4, new RiskLevel(4, "mobs will attack", new Color(255, 0, 0)));
        
        areas = new HashMap<Integer, Area>();
        layers = new HashMap<Integer, Layer>();
        places = new HashMap<Integer, Place>();
    }
    
    /**
     * Loads the world
     */
    private void load_world() throws Exception{
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            
            String line;
            
            int file_major = 0, file_minor = 0, file_build = 0;
            
            int cur_area = -1;
            Place cur_place = new Place(-1, "", 0, 0, new Layer(-1));
            final RiskLevel risk_level_default = get_risk_level(0);

            // temporary data for creating a place
            int cur_place_id = -1;
            String cur_place_name = "";
            ArrayList<Pair<Place, Integer>> children = new ArrayList<Pair<Place, Integer>>();
            ArrayList<PathTmp> tmp_paths = new ArrayList<PathTmp>();
            ArrayList<PathTmp> tmp_paths_deprecated = new ArrayList<PathTmp>();
            // error counter for deprecated path specification format
            // increments if there are more than one path between two places
            // (there is no way to reconstruct which exits were connected)
            int path_connection_error_dep_double = 0;
            
            try {    
                while((line = reader.readLine()) != null){
                    line = line.trim();

                    if(line.startsWith("//") || line.startsWith("#")) continue; // comments
                    else if(line.startsWith("ver")){ // world file version
                        /* note: the version number should only vary if the
                         * world file structure changed. Before mudmap 1.4.60 the
                         * file version was always equal to the program version
                         */
                        
                        // remove tag and split version                      
                        String[] tmp = line.substring(4).trim().split("\\.");
                        file_major = Integer.parseInt(tmp[0]);
                        file_minor = Integer.parseInt(tmp[1]);
                        file_build = Integer.parseInt(tmp[2]);

                        if(file_major > file_version_major || (file_major == file_version_major && (file_minor > file_version_minor || (file_minor == file_version_minor && file_build > file_version_build))))
                            throw new Exception("World file version is greater than file reader version. Please update mudmap or consult the developer.");
                            // TODO: Show message dialog
                    } else if(line.startsWith("wname")){ // world name
                        name = line.substring(5).trim();
                    } else if(line.startsWith("wcol")){ // path line color
                        String[] tmp = line.substring(5).split(" ");
                        if(path_color == path_color_nstd) path_color_nstd = new Color(Integer.parseInt(tmp[0]), Integer.parseInt(tmp[1]), Integer.parseInt(tmp[2]));
                        path_color = new Color(Integer.parseInt(tmp[0]), Integer.parseInt(tmp[1]), Integer.parseInt(tmp[2]));
                    } else if(line.startsWith("wcnd")){ // path line color of self-defined paths
                        String[] tmp = line.substring(5).split(" ");
                        path_color_nstd = new Color(Integer.parseInt(tmp[0]), Integer.parseInt(tmp[1]), Integer.parseInt(tmp[2]));
                    } else if(line.startsWith("home")){ // home coordinates
                        String[] tmp = line.substring(5).split(" ");
                        home_layer = Integer.parseInt(tmp[0]);
                        home_x = Double.parseDouble(tmp[1]);
                        home_y = Double.parseDouble(tmp[2]);
                    } else if(line.startsWith("dlc")){ // risk level colors
                        String[] tmp = line.split(" ");
                        int rlid = Integer.parseInt(tmp[1]);
                        String description = config_get_text(5, line);
                        
                        // only create a new risk level if it doesn't exist yet
                        if(!risk_levels.containsKey(rlid))
                            risk_levels.put(rlid, new RiskLevel(rlid, description, new Color(Integer.parseInt(tmp[2]), Integer.parseInt(tmp[3]), Integer.parseInt(tmp[4]))));
                        else {
                            risk_levels.get(rlid).set_description(description);
                            risk_levels.get(rlid).set_color(new Color(Integer.parseInt(tmp[2]), Integer.parseInt(tmp[3]), Integer.parseInt(tmp[4])));
                        }
                    } 
                    
                    else if(line.startsWith("a ")){ // area id + name
                        cur_area = Integer.parseInt(line.split(" ")[1]);
                        areas.put(cur_area, new Area(cur_area, config_get_text(2, line)));
                    } else if(line.startsWith("acol")){ // area color
                        String[] tmp = line.split(" ");
                        areas.get(cur_area).set_color(new Color(Integer.parseInt(tmp[1]), Integer.parseInt(tmp[2]), Integer.parseInt(tmp[3])));
                    } 
                    
                    else if(line.startsWith("p ")){ // place id + name
                        cur_place_id = Integer.parseInt(line.split(" ")[1]);
                        cur_place_name = config_get_text(2, line);
                    } else if(line.startsWith("ppos")){ // place position
                        String tmp[] = line.split(" ");
                        int layer = Integer.parseInt(tmp[1]);
                        
                        // create the layer, if it doesn't exist
                        if(!layers.containsKey(layer)) layers.put(layer, new Layer(layer));
                        
                        if(cur_place_id != -1){
                            // create place and add it to the layer and places list
                            cur_place = new Place(cur_place_id, cur_place_name, Integer.parseInt(tmp[2]), Integer.parseInt(tmp[3]), layers.get(Integer.parseInt(tmp[1])));
                            cur_place.set_risk_level(risk_level_default);
                            places.put(cur_place.get_id(), cur_place);
                            layers.get(Integer.parseInt(tmp[1])).put(cur_place);
                        }
                    } else if(line.startsWith("par")){ // place area
                        cur_place.set_area(areas.get(Integer.parseInt(line.substring(3).trim())));
                    } else if(line.startsWith("pb")){ // place bool values
                        cur_place.set_flag(line.substring(3).trim(), true);
                    } else if(line.startsWith("pw")){ // place to place connection (deprecated)
                        /* This tag describes 3 of 4 pieces of information
                         * needed for a path connection. Both places have 
                         * corresponding tags, but a path might not be reconstructable
                         * if there are more than on connections between two places
                         */
                        String[] tmp = line.split(" ");
                        int other_place_id = Integer.parseInt(tmp[1]);
                        
                        boolean found_path = false;
                        
                        for(PathTmp path: tmp_paths_deprecated){
                            // find place - place pair and connect it
                            if(path.place_b == cur_place_id && path.place_a.get_id() == other_place_id){
                                if(path.exits[1] == null){
                                    path.exits[1] = new ExitDirection(tmp[2]);
                                    found_path = true;
                                    break;
                                } else path_connection_error_dep_double++;
                            }
                        }
                        // if there is no pair create a new entry
                        if(!found_path) tmp_paths_deprecated.add(new PathTmp(cur_place, other_place_id, new ExitDirection(tmp[2]), null));
                    } else if(line.startsWith("pp")){ // place to place (path) connection
                        String[] tmp = line.substring(3).split("\\$");
                        tmp_paths.add(new PathTmp(cur_place, Integer.parseInt(tmp[0]), new ExitDirection(tmp[1]), new ExitDirection(tmp[2])));
                    } else if(line.startsWith("pchi")){ // place child
                        String[] tmp = line.split(" ");
                        children.add(new Pair(cur_place, Integer.parseInt(tmp[1])));
                    } else if(line.startsWith("pdl")){ // place risk level
                        int rlid = Integer.parseInt(line.substring(3).trim());
                        RiskLevel rl = risk_levels.get(rlid);
                        if(rl != null) cur_place.set_risk_level(rl);
                        else System.out.println("Couldn't load risk level " + rlid + " for " + cur_place_name);
                    } else if(line.startsWith("prl")){ // place recommended level
                        String[] tmp = line.split(" ");
                        cur_place.set_rec_lvl_min(Integer.parseInt(tmp[1]));
                        cur_place.set_rec_lvl_max(Integer.parseInt(tmp[2]));
                    } else if(line.startsWith("pcom")){ // place comment
                        cur_place.add_comment(line.substring(4).trim());
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(WorldManager.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            // connect children and parent places
            for(Pair<Place, Integer> p: children){
                p.first.connect_child(places.get(p.second));
            }
            
            // new path specification format is introduced in file version 1.5.0 (after 1.4.45)
            if(file_major == 1 && file_minor >= 5){ // connect paths
                for(PathTmp path: tmp_paths){
                    path.place_a.connect_path(new Path(path.place_a, path.exits[0], places.get(path.place_b), path.exits[1]));
                }
            } else { // connect deprecated paths (for compatibility to mudmap 1)
                int error_not_paired_cnt = 0;
                for(PathTmp path: tmp_paths_deprecated){
                    if(path.exits[1] == null) error_not_paired_cnt++;
                    path.place_a.connect_path(new Path(path.place_a, path.exits[0], places.get(path.place_b), (path.exits[1] != null) ? path.exits[1] : new ExitDirection("unknown")));
                }
                if(path_connection_error_dep_double > 0) System.out.println("Warning: " + path_connection_error_dep_double + " paths might not be properly reconstructed (exit mispairings might occur at places with more than two connections to each other)");
                if(error_not_paired_cnt > 0) System.out.println("Warning: " + error_not_paired_cnt + " paths could not be properly reconstructed (an exit is unknown for each error place pair)");
                // TODO: show error message dialog
            }
            //System.out.println("paths: " + tmp_paths.size() + " " + tmp_paths_deprecated.size());
            
        } catch (FileNotFoundException ex) {
            System.out.println("Couldn't open world file \"" + Paths.get_available_worlds_file() + "\", file not found");
            Logger.getLogger(WorldManager.class.getName()).log(Level.INFO, null, ex);
        }
    }
    
    /**
     * Saves the world
     */
    public void write_world(){
        try {
            // open file
            PrintWriter outstream = new PrintWriter(new BufferedWriter( new FileWriter(file)));

            outstream.println("# MUD Map 2 world file");
            outstream.println("# compatibility for MUD Map 1 " + (compatibility_mudmap_1 ? "enabled" : "disabled"));
            
            outstream.println("ver " + file_version_major + "." + file_version_minor + "." + file_version_build);
            outstream.println("mver " + mudmap2.Mudmap2.get_version_major() + "." + mudmap2.Mudmap2.get_version_minor() + "." + mudmap2.Mudmap2.get_version_build());
            outstream.println("name " + get_name());
            outstream.println("wcol " + get_path_color());
            outstream.println("wcnd " + get_path_color_nstd());
            outstream.println("home " + get_home_layer() + " " + get_home_x() + " " + get_home_y());
            
            // risk levels
            for(RiskLevel rl: risk_levels.values())
                outstream.println("dlc " + rl.get_id() + " " + rl.get_color() + " " + rl.get_description());
            
            // areas
            for(Area a: areas.values()){
                outstream.println("a " + a.get_id() + " " + a.get_name());
                outstream.println("acol " + a.get_color());
            }
            
            // places
            for(Place p: places.values()){
                outstream.println("p " + p.get_id() + " " + p.get_name());
                outstream.println("ppos " + p.get_layer() + " " + p.get_x() + " " + p.get_y());
                if(p.get_area() != null) outstream.println("par " + p.get_area().get_id());
                
                // paths
                for(Path path: p.get_paths()){
                    Place other_place = path.get_other_place(p);
                    
                    if(compatibility_mudmap_1) // deprecated path format
                        outstream.println("pw " + other_place.get_id() + " " + path.get_exit(p));
                    
                    // new path format
                    if(path.get_places()[0] == p) // only one of both places should describe the path
                        outstream.println("pp " + other_place.get_id() + "$" + path.get_exit(p) + "$" + path.get_exit(other_place));
                }
                
                // risk level and recommended level
                outstream.println("pdl " + p.get_risk_level().get_id());
                if(p.get_rec_lvl_min() != -1 || p.get_rec_lvl_max() != -1) outstream.println("prl " + p.get_rec_lvl_min() + " " + p.get_rec_lvl_max());
                
                // children
                for(Place child: p.children) outstream.println("pchi " + child.get_id());
                // comments
                for(String comment: p.get_comments()) outstream.println("pcom " + comment);
                // flags
                for(Map.Entry<String, Boolean> flag: p.flags.entrySet()){
                    if(flag.getValue()) outstream.println("pb " + flag.getKey());
                }
            }
            
                            
            
            outstream.close();
        } catch (IOException ex) {
            System.out.printf("Couldn't write config file " + mudmap2.Paths.get_config_file());
            Logger.getLogger(World.class.getName()).log(Level.WARNING, null, ex);
        }
    }
    
    // Path creation helper class
    public class PathTmp {
        public Place place_a;
        public int place_b;
        public ExitDirection[] exits;
        
        PathTmp(Place _place_a, int _place_b, ExitDirection exit_a, ExitDirection exit_b){
            place_a = _place_a;
            place_b = _place_b;
            
            exits = new ExitDirection[2];
            exits[0] = exit_a;
            exits[1] = exit_b;
        }
    }
    
    /**
     * Returns the text data of a configuration file entry
     * 
     * Some config file lines consist of space-separated data followed by a text
     * This method returns the text part.
     * 
     * @param non_text number of data entries to be removed
     * @param line raw config line
     * @return text part
     */
    private String config_get_text(int non_text, String line){
        while(non_text-- != 0){
            line = line.substring(line.indexOf(" ")).trim();
        }
        return line;
    }
    
    /**
     * Gets a layer
     * @param id layer id
     * @return layer
     */
    public Layer get_layer(int id){
        return layers.get(id);
    }
    
    /**
     * Gets a place
     * @param id place id
     * @return place 
     */
    public Place get_place(int id){
        return places.get(id);
    }
    
    /**
     * Gets a risk level
     * @param id risk level id
     * @return risk level
     */
    public RiskLevel get_risk_level(int id){
        return risk_levels.get(id);
    }
    
    /**
     * Gets the world name
     * @return world name
     */
    public String get_name(){
        return name;
    }
    
    /**
     * Sets the world name
     * @param n new world name
     */
    public void set_name(String n){
        name = n;
    }
    
    /**
     * Gets the world file
     * @return world file string
     */
    public String get_file(){
        return file;
    }
    
    /**
     * Gets the layer of the home position
     * @return home layer
     */
    public int get_home_layer(){
        return home_layer;
    }
    
    /**
     * Gets the x coordinate of the home position
     * @return x coordinate
     */
    public double get_home_x(){
        return home_x;
    }
    
    /**
     * Gets the y coordinate of the home position
     * @return y coordinate
     */
    public double get_home_y(){
        return home_y;
    }
    
    /**
     * Gets the path color
     * @return path color
     */
    public Color get_path_color(){
        return path_color;
    }
    
    /**
     * Gets the color for paths that aren't predefined
     * @return path color
     */
    public Color get_path_color_nstd(){
        return path_color_nstd;
    }

}
