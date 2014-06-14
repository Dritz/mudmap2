/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mudmap2.frontend;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import mudmap2.backend.WorldManager;

/**
 * Main class for the mudmap window
 * @author neop
 */
public class Mainwindow extends JFrame {

    /**
     * The available worlds tab
     */
    private static class AvailableWorldsTab extends JPanel {

        // Reference to the main window
        Mainwindow mwin;
        
        public AvailableWorldsTab(Mainwindow _mwin) {
            mwin = _mwin;
            
            WorldManager.read_world_list();
            Set<String> worlds = WorldManager.get_world_list();
            
            setLayout(new GridLayout(worlds.size(), 2));
            
            for(String world_name: worlds){
                JButton b = new JButton(world_name);
                b.addActionListener(new ListenerButtonOpenWorld(mwin, world_name));
                add(b);
            }
        }
        
        /**
         * Opens a world tab (existing or creates it) when the corresponding
         * button is pressed
         */
        public class ListenerButtonOpenWorld implements ActionListener {

            Mainwindow mwin;
            String world_name;
            
            /**
             * Constructor
             * @param _mwin reference to the main window
             * @param _world_name name of the world to open
             */
            public ListenerButtonOpenWorld(Mainwindow _mwin, String _world_name){
                mwin = _mwin;
                world_name = _world_name;
            }
            
            @Override
            public void actionPerformed(ActionEvent arg0) {
                mwin.open_world(world_name);
            }   
        }   
    }

    // Contains all opened maps <name, worldtab>
    HashMap<String, WorldTab> world_tabs;
    
    // GUI elements
    JMenuBar menu_bar;
    JMenu menu_file, menu_edit, menu_help;
    JMenuItem menu_file_new, menu_file_open, menu_file_save, menu_file_save_as_image, menu_file_quit;
    JMenuItem menu_edit_add_area, menu_edit_set_home_position, menu_edit_edit_world;
    JMenuItem menu_help_help, menu_help_info;
    
    JTabbedPane tabbed_pane;
    
    public Mainwindow(){
        super("MUD Map 2 alpha");
        
        world_tabs = new HashMap<String, WorldTab>();
        
        setSize(750, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new MainWindowListener());
        
        // Add GUI components
        menu_bar = new JMenuBar();
        add(menu_bar, BorderLayout.NORTH);
        
        menu_file = new JMenu("File");
        menu_bar.add(menu_file);
        menu_edit = new JMenu("Edit");
        menu_bar.add(menu_edit);
        menu_help = new JMenu("Help");
        menu_bar.add(menu_help);
        
        menu_file_new = new JMenuItem("New");
        menu_file.add(menu_file_new);
        menu_file_open = new JMenuItem("Open");
        menu_file.add(menu_file_open);
        menu_file.addSeparator();
        menu_file_save = new JMenuItem("Save");
        menu_file.add(menu_file_save);
        menu_file_save_as_image = new JMenuItem("Save as image");
        menu_file.add(menu_file_save_as_image);
        menu_file.addSeparator();
        menu_file_quit = new JMenuItem("Quit");
        menu_file.add(menu_file_quit);
        
        menu_edit_add_area = new JMenuItem("Add area");
        menu_edit.add(menu_edit_add_area);
        menu_edit_set_home_position = new JMenuItem("Set home position");
        menu_edit.add(menu_edit_set_home_position);
        menu_edit_edit_world = new JMenuItem("Edit world");
        menu_edit.add(menu_edit_edit_world);
        
        menu_help_help = new JMenuItem("Help");
        menu_help.add(menu_help_help);
        menu_help_info = new JMenuItem("Info");
        menu_help.add(menu_help_info);
        
        // ---
        tabbed_pane = new JTabbedPane();
        add(tabbed_pane);
        tabbed_pane.addTab("Available worlds", new AvailableWorldsTab(this));
        
        setVisible(true);
    }
    
    /**
     * shows the tab of the world, opens the world if necessary
     * @param world_name world name
     */
    public void open_world(String world_name){
        if(!world_tabs.containsKey(world_name)){ 
            // open new tab
            WorldTab tab = new WorldTab(this, world_name);
            world_tabs.put(world_name, tab);
            tabbed_pane.addTab(tab.get_world_name(), tab);
        }
        // change current tab
        tabbed_pane.setSelectedComponent(world_tabs.get(world_name));
    }
    
    public void close_tabs(){
        for(WorldTab tab: world_tabs.values()){
            // TODO: implement dialog which asks the user if the world should be saved
            /*if(save_world) tab.save();
            else*/ tab.write_meta();
            tabbed_pane.remove(tab);
        }
    }
    
    public class MainWindowListener implements WindowListener {

        @Override
        public void windowOpened(WindowEvent arg0) {}

        @Override
        public void windowClosing(WindowEvent arg0) {
            close_tabs();
        }

        @Override
        public void windowClosed(WindowEvent arg0) {}

        @Override
        public void windowIconified(WindowEvent arg0) {}

        @Override
        public void windowDeiconified(WindowEvent arg0) {}

        @Override
        public void windowActivated(WindowEvent arg0) {}

        @Override
        public void windowDeactivated(WindowEvent arg0) {}
        
    }
    
}
