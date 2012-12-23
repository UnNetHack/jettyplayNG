/*
 * This file was originally part of "JTA - Telnet/SSH for the JAVA(tm) platform".
 *
 * (c) Matthias L. Jugel, Marcus Meißner 1996-2005. All Rights Reserved.
 * Modified by Alex Smith, 2010, 2012.
 *
 * Please visit http://javatelnet.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * --LICENSE NOTICE--
 *
 */
package jettyplay;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.JComponent;

/**
 * Video Display Unit emulation for Swing/AWT. This class implements all necessary
 * features of a character display unit, but not the actual terminal emulation.
 * It can be used as the base for terminal emulations of any kind.
 * <P>
 * This is a lightweight component. It will render very badly if used
 * in standard AWT components without overloaded update() method. The
 * update() method must call paint() immediately without clearing the
 * components graphics context or parts of the screen will simply
 * disappear.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: SwingTerminal.java 511 2005-11-18 19:36:06Z marcus $
 * @author  Matthias L. Jugel, Marcus Mei�ner
 */
@SuppressWarnings("serial")
public class SwingTerminal extends JComponent {
    /** lightweight component definitions */
    private final static long VDU_EVENTS =
                AWTEvent.FOCUS_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK;
    protected Insets insets;                            /* size of the border */
    protected boolean raised;            /* indicator if the border is raised */

    private VDURenderer renderer;
    
    /**
     * Create a new terminal that draws using a default renderer.
     */
    public SwingTerminal() {
        // lightweight component handling
        enableEvents(VDU_EVENTS);
        setOpaque(true);
        setDoubleBuffered(true);
        setForeground(Color.white);
        setBackground(Color.black);

        this.renderer = new VDURenderer(new vt320(), getGraphics());
    }

    public void setVDUBuffer(VDUBuffer buffer) {
        renderer.setVDUBuffer(buffer);
        setBounds(getX(), getY(), getWidth(), getHeight());
        repaint();
    }

    public void setTextAntialiasingType(Object textAntialiasingType) {
        renderer.setTextAntialiasingType(textAntialiasingType);
        repaint();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        renderer.setFont(font, getGraphics());
        repaint();
    }

    public void setColorPrinting(boolean colorPrint) {
        renderer.setColorPrinting(colorPrint);
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        renderer.setBounds(x, y, w, h, getGraphics());
    }

    /**
     * Set the border thickness and the border type.
     * @param thickness border thickness in pixels, zero means no border
     * @param raised a boolean indicating a raised or embossed border
     */
    public void setBorder(int thickness, boolean raised) {
        if (thickness == 0) {
            insets = null;
        } else {
            insets = new Insets(thickness + 1, thickness + 1, thickness + 1, thickness + 1);
        }
        this.raised = raised;
    }
    
    public void redraw(Graphics g, int w, int h) {
        renderer.redraw(g, w, h);
        // draw border
        if (insets != null) {
            g.setColor(getBackground());
            int xoffset = (renderer.getCurrentTerminalWidth() - w) / 2 - 1;
            int yoffset = (renderer.getCurrentTerminalHeight() - h) / 2 - 1;
            for (int i = insets.top - 1; i >= 0; i--) {
                g.draw3DRect(xoffset - i, yoffset - i,
                        renderer.getCurrentTerminalWidth() + 1 + i * 2,
                        renderer.getCurrentTerminalHeight() + 1 + i * 2, raised);
            }
        }
    }

    private void chainPrinting(Graphics g) {
        super.print(g);
    }
    
    @Override
    public void print(Graphics g) {
        final SwingTerminal finalThis = this;
        renderer.print(g, new Printable(){
            public void print(Graphics g){
                finalThis.chainPrinting(g);
            }
        });
    }

    public VDUBuffer getVDUBuffer() {
        return renderer.getVDUBuffer();
    }

    public Object getTextAntialiasingType() {
        return renderer.getTextAntialiasingType();
    }

    @Override
    public Dimension getSize() {
        return renderer.getSize();
    }

    @Override
    public Dimension getPreferredSize() {
        return renderer.getPreferredSize();
    }

    @Override
    public Font getFont() {
        return renderer.getFont();
    }

    public String asHTML() {
        return renderer.asHTML();
    }

    public int getCurrentTerminalWidth() {
        return renderer.getCurrentTerminalWidth();
    }

    public int getCurrentTerminalHeight() {
        return renderer.getCurrentTerminalHeight();
    }
    
    /**
     * Paint the current screen.
     * @param g The Graphics to paint on.
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        redraw(g, getWidth(), getHeight());
    }

    /**
     * The insets of the character display define the border.
     * @return Insets border thickness in pixels
     */
    @Override
    public Insets getInsets() {
        return insets == null ? new Insets(0,0,0,0) : insets;
    }
}
