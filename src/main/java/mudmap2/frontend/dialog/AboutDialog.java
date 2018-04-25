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
 *  The Help->About dialog, it shows information about the program
 */

package mudmap2.frontend.dialog;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import mudmap2.Environment;
import mudmap2.frontend.GUIElement.LinkLabel;

/**
 * The Help->About dialog, it shows information about the program
 * @author neop
 */
public class AboutDialog extends ActionDialog {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an about dialog
     * @param parent
     */
    public AboutDialog(JFrame parent){
        super(parent, "About MUD Map", true);
    }

    @Override
    protected void create() {
        setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 5, 4, 5);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;

        ClassLoader classLoader = getClass().getClassLoader();
        URL iconurl = classLoader.getResource("resources/mudmap-128.png");
        ImageIcon iconimage = new ImageIcon(iconurl);

        ++constraints.gridy;
        add(new JLabel(iconimage));

        ++constraints.gridy;
        add(new JLabel("<html><h1>MUD Map v2</h1></html>"), constraints);
        ++constraints.gridy;
        String version = AboutDialog.class.getPackage().getImplementationVersion();
        if(version == null){
            add(new JLabel("Developmental version"), constraints);
        } else {
            add(new JLabel("Version " + version), constraints);
        }
        ++constraints.gridy;
        add(new JLabel("License: GPLv3"), constraints);
        ++constraints.gridy;
        add(new JLabel("Use it on your own risk!"), constraints);
        ++constraints.gridy;
        add(new LinkLabel("GitHub", Environment.GITHUB_URL), constraints);
        ++constraints.gridy;
        add(new LinkLabel("Sourceforge", Environment.SOURCEFORGE_URL), constraints);
        ++constraints.gridy;
        add(new JLabel("by Neop (mneop@web.de)"), constraints);
        ++constraints.gridy;

        JButton button_ok = new JButton("Close");
        add(button_ok, constraints);
        getRootPane().setDefaultButton(button_ok);
        button_ok.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        pack();
        setLocation(getParent().getX() + (getParent().getWidth() - getWidth()) / 2, getParent().getY() + (getParent().getHeight() - getHeight()) / 2);
    }
}
