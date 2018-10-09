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
 *  The PlaceCommentDialog modifies the comments of a place
 */
package mudmap2.frontend.dialog;

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

import mudmap2.backend.Place;

/**
 * The PlaceCommentDialog modifies the comments of a place
 * @author neop
 */
public class PlaceCommentDialog extends ActionDialog {

    private static final long serialVersionUID = 1L;

    Place place;

    JTextArea commentArea;
    JOptionPane optionPane;
    JScrollPane scrollPane;

    public PlaceCommentDialog(final JFrame parent, final Place place) {
        super(parent, "Comments - " + place, true);
        this.place = place;
    }

    @Override
    protected void create() {
        setMinimumSize(new Dimension(300, 200));

        optionPane = new JOptionPane();
        optionPane.setOptionType(JOptionPane.YES_NO_OPTION);

        commentArea = new JTextArea(place.getComments());
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);

        scrollPane = new JScrollPane(commentArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        setContentPane(optionPane);
        optionPane.setMessage(scrollPane);

        optionPane.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent arg0) {
                if (isVisible() && arg0.getSource() == optionPane && arg0.getPropertyName().equals(JOptionPane.VALUE_PROPERTY)) {
                    final int value = (Integer) optionPane.getValue();
                    if (value == JOptionPane.YES_OPTION) {
                        place.setComments(commentArea.getText());
                    }
                    dispose();
                    getParent().repaint();
                }
            }
        });

        pack();
        setLocation(getParent().getX() + (getParent().getWidth() - getWidth()) / 2, getParent().getY() + (getParent().getHeight() - getHeight()) / 2);
    }
}
