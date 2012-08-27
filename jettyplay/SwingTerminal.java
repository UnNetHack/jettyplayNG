/*
 * This file was originally part of "JTA - Telnet/SSH for the JAVA(tm) platform".
 *
 * (c) Matthias L. Jugel, Marcus Meißner 1996-2005. All Rights Reserved.
 * Modified by Alex Smith, 2010.
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
import java.awt.AWTEventMulticaster;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.UIManager;

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
public class SwingTerminal extends JComponent
        implements MouseListener, MouseMotionListener {

    private final static int debug = 0;
    /** the VDU buffer */
    private VDUBuffer buffer;
    /** lightweight component definitions */
    private final static long VDU_EVENTS = AWTEvent.KEY_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK;
    private Insets insets;                            /* size of the border */

    private boolean raised;            /* indicator if the border is raised */

    private Font normalFont;                                 /* normal font */

    private FontMetrics fm;                         /* current font metrics */

    private int charWidth;                       /* current width of a char */

    private int charHeight;                     /* current height of a char */

    private int charDescent;                           /* base line descent */

    private int resizeStrategy;                /* current resizing strategy */

    private Point selectBegin, selectEnd;          /* selection coordinates */

    private String selection;                 /* contains the selected text */

    private Object textAntialiasingType;              /* how to render text */

    private boolean colorPrinting = false;	/* print display in color */

/*    private BufferedImage backingStore = null;
    private int backingStoreRows = 0;
    private int backingStoreColumns = 0;*/
    private boolean[] update;

    /**
     * Create a color representation that is brighter than the standard
     * color but not what we would like to use for bold characters.
     * @param clr the standard color
     * @return the new brighter color
     */
    private Color brighten(Color clr) {
        int r, g, b;

        r = (int) min((clr.getRed()+29) * 1.1, 255.0);
        g = (int) min((clr.getGreen()+29) * 1.1, 255.0);
        b = (int) min((clr.getBlue()+29) * 1.1, 255.0);
        return new Color(r, g, b);
    }

    /**
     * Create a color representation that is darker than the standard
     * color but not what we would like to use for bold characters.
     * @param clr the standard color
     * @return the new darker color
     */
    private Color darken(Color clr) {
        int r, g, b;

        r = (int) max(clr.getRed() * 0.9 - 29, 0.0);
        g = (int) max(clr.getGreen() * 0.9 - 29, 0.0);
        b = (int) max(clr.getBlue() * 0.9 - 29, 0.0);
        return new Color(r, g, b);
    }
    /** A list of colors used for representation of the display */
    private Color color[] = {new Color(0, 0, 0),
        new Color(128, 0, 0),
        new Color(0, 128, 0),
        new Color(128, 64, 0),
        new Color(0, 0, 192),
        new Color(128, 0, 128),
        new Color(0, 128, 128),
        new Color(170, 170, 170),
        null, // bold color
        null, // inverted color
        new Color(255, 255, 255), // color to use as bold with standard colors
        new Color(100, 100, 100),
        new Color(255, 0, 0),
        new Color(0, 255, 0),
        new Color(255, 255, 0),
        new Color(0, 64, 255),
        new Color(255, 0, 255),
        new Color(0, 255, 255),
        new Color(255, 255, 255),};
    public final static int RESIZE_NONE = 0;
    public final static int RESIZE_FONT = 1;
    public final static int RESIZE_SCREEN = 2;
    public final static int COLOR_BOLD = 8;
    public final static int COLOR_INVERT = 9;
    public final static int COLOR_UNCOLORED_BOLD_REPLACEMENT = 10;
    public final static int COLOR_FIRST_BOLD_REPLACEMENT = 11;
    public final static int NUM_COLORS = 19;
    /* definitions of standards for the display unit */
    private final static int COLOR_FG_STD = 7;
    private final static int COLOR_BG_STD = 0;

    protected double max(double f1, double f2) {
        return (f1 < f2) ? f2 : f1;
    }

    protected double min(double f1, double f2) {
        return (f1 < f2) ? f1 : f2;
    }

    /**
     * Create a new video display unit with the passed width and height in
     * characters using a special font and font size. These features can
     * be set independently using the appropriate properties.
     * @param buffer a VDU buffer to be associated with the display
     * @param font the font to be used (usually Monospaced)
     */
    public SwingTerminal(VDUBuffer buffer, Font font) {
        setVDUBuffer(buffer);

        // lightweight component handling
        enableEvents(VDU_EVENTS);

        // set the normal font to use
        setFont(font);
        // set the standard resize strategy
        setResizeStrategy(RESIZE_FONT);

        setForeground(Color.white);
        setBackground(Color.black);
        
        setOpaque(true);

        setDoubleBuffered(true);

        textAntialiasingType = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;

        clearSelection();

        addMouseListener(this);
        addMouseMotionListener(this);

        selection = null;
    }

    /**
     *  Find a sensible font for displaying ttyrecs with, by looking through
     *  a list of names of monospaced fonts.
     */
    private static String getSensibleFontName() {
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().
            getAvailableFontFamilyNames();
        HashSet<String> fontNameSet =
            new HashSet<String>(Arrays.asList(fontNames));
        /* Mac OS X fixedwidth fonts */
        if (fontNameSet.contains("Menlo")) return "Menlo";
        if (fontNameSet.contains("Monaco")) return "Monaco";
        /* fixedwidth fonts typically used on Linux */
        if (fontNameSet.contains("DejaVu Sans Mono"))
            return "DejaVu Sans Mono";
        if (fontNameSet.contains("Liberation Mono")) return "Liberation Mono";
        if (fontNameSet.contains("FreeMono")) return "FreeMono";
        /* Windows/Microsoft fixedwidth fonts */
        if (fontNameSet.contains("Inconsolata")) return "Inconsolata";
        if (fontNameSet.contains("Consolas")) return "Consolas";
        if (fontNameSet.contains("Courier New")) return "Courier New";
        return "Monospaced";
    }

    /**
     * Create a display unit with size 80x24 and a default font.
     * @param buffer The buffer to use to store the data.
     */
    public SwingTerminal(VDUBuffer buffer) {
        this(buffer, new Font(getSensibleFontName(), Font.PLAIN, 11));
    }

    /**
     * Set a new terminal (VDU) buffer.
     * @param buffer new buffer
     */
    public void setVDUBuffer(VDUBuffer buffer) {
        VDUBuffer old = this.buffer;
        if (buffer == null) {
            this.buffer = new vt320();
            update = new boolean[this.buffer.charAttributes.length + 1];
        } else {
            this.buffer = buffer;
            update = new boolean[buffer.charAttributes.length + 1];
            if (old != null &&
                    old.charAttributes.length == buffer.charAttributes.length) {
                for (int i = 0; i < old.charAttributes.length; i++) {
                    /* Due to the deduplication method used, if the
                     * charAttributes pointers are the same, then so is ther
                     * charArray. */
                    if (old.charAttributes[i] != buffer.charAttributes[i]) {
                        update[i + 1] = true;
                    } else {
                        update[i + 1] = false;
                    }
                }
                if (old != null) {
                    update[old.cursorY + 1] = true;
                    update[buffer.cursorY + 1] = true;
                }
            } else {
                update[0] = true;
            }
        }
        setBounds(getX(),getY(),getWidth(),getHeight());
        repaint();
    }

    /**
     * Return the currently associated VDUBuffer.
     * @return the current buffer
     */
    public VDUBuffer getVDUBuffer() {
        return buffer;
    }

    /**
     * Set new color set for the display.
     * @param colorset new color set
     */
    public void setColorSet(Color[] colorset) {
        System.arraycopy(colorset, 0, color, 0, NUM_COLORS);
        update[0] = true;
        repaint();
    }

    /**
     * Get current color set.
     * @return the color set currently associated
     */
    public Color[] getColorSet() {
        return color;
    }

    /**
     * Set the font to be used for rendering the characters on screen.
     * @param font the new font to be used.
     */
    @Override
    public void setFont(Font font) {
        super.setFont(normalFont = font);
        fm = getFontMetrics(font);
        if (fm != null) {
            charWidth = fm.charWidth('@');
            charHeight = fm.getHeight();
            charDescent = fm.getDescent();
        }
        if (update != null) {
            update[0] = true;
        }
        repaint();
    }

    @Override
    public Font getFont() {
        return normalFont;
    }

    public Object getTextAntialiasingType() {
        return textAntialiasingType;
    }

    public void setTextAntialiasingType(Object textAntialiasingType) {
        this.textAntialiasingType = textAntialiasingType;
        update[0] = true;
        repaint();
    }

    /**
     * Set the strategy when window is resized.
     * RESIZE_FONT is default.
     * @param strategy the strategy
     * @see #RESIZE_NONE
     * @see #RESIZE_FONT
     * @see #RESIZE_SCREEN
     */
    public void setResizeStrategy(int strategy) {
        resizeStrategy = strategy;
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
            insets = new Insets(thickness + 1, thickness + 1,
                    thickness + 1, thickness + 1);
        }
        this.raised = raised;
    }

    public String asHTML() {
        return redraw(getGraphics(), true, getWidth(), getHeight());
    }

    /**
     * Redraw all lines on the given Graphics.
     * @param g The Graphics to draw on.
     * @param w The amount of width of the Graphics to use.
     * @param h The amount of height of the Graphics to use.
     */
    public void redraw(Graphics g, int w, int h) {
        update[0] = true;
        redraw(g, false, w, h);
    }

    /**
     * Returns the current height of the area of this SwingTerminal used
     * to actually draw the terminal.
     * @return The height, in pixels.
     */
    public int getCurrentTerminalHeight() {
        if (insets != null) {
            return charHeight * buffer.height + insets.left + insets.right;
        } else {
            return charHeight * buffer.height;
        }
    }

    /**
     * Returns the current width of the area of this SwingTerminal used
     * to actually draw the terminal.
     * @return The width, in pixels.
     */
    public int getCurrentTerminalWidth() {
        if (insets != null) {
            return charWidth * buffer.width + insets.top + insets.bottom;
        } else {
            return charWidth * buffer.width;
        }
    }


    protected String redraw(Graphics g, boolean renderHTML,
            int drawWidth, int drawHeight) {
        if (g == null) return "";
        StringBuilder html = new StringBuilder("<pre>");
        int width, height;
        if (insets != null) {
            width = charWidth * buffer.width + insets.top + insets.bottom;
            height = charHeight * buffer.height + insets.left + insets.right;
        } else {
            width = charWidth * buffer.width;
            height = charHeight * buffer.height;
        }
        if (debug > 0) {
            System.err.println("redraw()");
        }

        if (g instanceof Graphics2D) {
            Map<RenderingHints.Key, Object> hints = new HashMap<RenderingHints.Key,Object>();
            hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, textAntialiasingType);
            ((Graphics2D)g).addRenderingHints(hints);
        }

        // clear background; grab the defaul background color for a panel in
        // the current LaF, and use it as the background color for the areas
        // of the SwingTerminal that aren't occupied by the terminal itself
        g.setColor(UIManager.getColor("Panel.background"));
        g.fillRect(0, 0, drawWidth, drawHeight);

        int xoffset = (drawWidth - width) / 2;
        int yoffset = (drawHeight - height) / 2;

        int selectStartLine=0, selectEndLine=0;
        try {
            selectStartLine = selectBegin.y - buffer.windowBase;
            selectEndLine = selectEnd.y - buffer.windowBase;
        } catch(NullPointerException e) {}

        Color fg = color[COLOR_FG_STD];
        Color bg = color[COLOR_BG_STD];

        g.setFont(normalFont);

        for (int l = 0; l < buffer.height; l++) {
            if (!update[0] && !update[l + 1] && !renderHTML) {
                continue;
            }
            update[l + 1] = false;
            if (debug > 2) {
                System.err.println("redraw(): line " + l);
            }
            for (int c = 0; c < buffer.width; c++) {
                int addr = 0;
                int currAttr = buffer.charAttributes[buffer.windowBase + l][c];

                fg = darken(getForeground());
                bg = darken(getBackground());

                if ((currAttr & VDUBuffer.COLOR_FG) != 0) {
                    fg = color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1];
                }
                if ((currAttr & VDUBuffer.COLOR_BG) != 0) {
                    bg = darken(color[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1]);
                }

                if ((currAttr & VDUBuffer.BOLD) != 0) {
                    // g.setFont(new Font(normalFont.getName(), Font.BOLD, normalFont.getSize()));
                    g.setFont(normalFont.deriveFont(Font.BOLD));
                    if (null != color[COLOR_BOLD]) {
                        fg = color[COLOR_BOLD];
                    } else {
                        fg = color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) -
                                1 + COLOR_FIRST_BOLD_REPLACEMENT];
                    }
                } else {
                    g.setFont(normalFont);
                }

                if ((currAttr & VDUBuffer.LOW) != 0) {
                    fg = darken(fg);
                }
                if ((currAttr & VDUBuffer.INVERT) != 0) {
                    if (null == color[COLOR_INVERT]) {
                        Color swapc = bg;
                        bg = fg;
                        fg = swapc;
                    } else {
                        if (null == color[COLOR_BOLD]) {
                            fg = bg;
                        } else {
                            fg = color[COLOR_BOLD];
                        }
                        bg = color[COLOR_INVERT];
                    }
                }


                // determine the maximum of characters we can print in one go
                while ((c + addr < buffer.width) &&
                        ((buffer.charArray[buffer.windowBase + l][c + addr] < ' ') ||
                        (buffer.charAttributes[buffer.windowBase + l][c + addr] == currAttr))) {
                    if (buffer.screenBase + buffer.cursorY == buffer.windowBase + l &&
                        buffer.cursorX == c + addr)
                        break;
                    if (buffer.charArray[buffer.windowBase + l][c + addr] < ' ') {
                        buffer.charArray[buffer.windowBase + l][c + addr] = ' ';
                        buffer.charAttributes[buffer.windowBase + l][c + addr] = 0;
                        continue;
                    }
                    addr++;
                }
                if (addr == 0) addr = 1;

                if (buffer.screenBase + buffer.cursorY == buffer.windowBase + l &&
                        buffer.cursorX == c) {
                    // Draw the cursor. This is done by swapping foreground and
                    // background, then brightening the foreground twice and
                    // darkening the background twice.
                    Color swapc = fg;
                    fg = brighten(brighten(bg));
                    bg = darken(darken(swapc));
                }

                // clear the part of the screen we want to change (fill rectangle)
                g.setColor(bg);
                g.fillRect(c * charWidth + xoffset, l * charHeight + yoffset,
                        addr * charWidth, charHeight);

                g.setColor(fg);

                if (renderHTML) {
                    try{
                    html.append(
                            "<span style='background-color:#" + colorHex(bg) +
                            ((currAttr & VDUBuffer.BOLD) != 0 ?
                                "; font-weight: bold" : "") +
                            ((currAttr & VDUBuffer.UNDERLINE) != 0 ?
                                "; text-decoration: underline" : "") +
                            "; color:#" + colorHex(fg) + "'>");
                    }catch(NullPointerException ex){
                        ex.printStackTrace();
                    }
                }
                // draw the characters, if not invisible.
                if ((currAttr & VDUBuffer.INVISIBLE) == 0) {
                    g.drawChars(buffer.charArray[buffer.windowBase + l], c, addr,
                            c * charWidth + xoffset,
                            (l + 1) * charHeight - charDescent + yoffset);
                    if (renderHTML) {
                        for (int i = 0; i < addr; i++) {
                            int cp = Character.codePointAt(
                              buffer.charArray[buffer.windowBase + l], c+i);
                            if (cp == 0) html.append(' ');
                            else if (cp >= 32 && cp <= 126 && cp != '&' && cp != '"')
                                html.append(Character.toChars(cp));
                            else
                                html.append("&#x"+Integer.toHexString(cp)+";");
                        }
                    }
                } else if (renderHTML) {
                    for (int i = 0; i < addr; i++)
                        html.append(' ');
                }
                if (renderHTML) {
                    html.append("</span>");
                }

                if ((currAttr & VDUBuffer.UNDERLINE) != 0) {
                    g.drawLine(c * charWidth + xoffset,
                            (l + 1) * charHeight - charDescent / 2 + yoffset,
                            c * charWidth + addr * charWidth + xoffset,
                            (l + 1) * charHeight - charDescent / 2 + yoffset);
                }

                c += addr - 1;
            }

            if (renderHTML) html.append('\n');

            // selection code, highlites line or part of it when it was
            // selected previously
            if (l >= selectStartLine && l <= selectEndLine) {
                int selectStartColumn = (l == selectStartLine ? selectBegin.x : 0);
                int selectEndColumn =
                        (l == selectEndLine ? (l == selectStartLine ? selectEnd.x - selectStartColumn : selectEnd.x) : buffer.width);
                if (selectStartColumn != selectEndColumn) {
                    if (debug > 0) {
                        System.err.println("select(" + selectStartColumn + "-" + selectEndColumn + ")");
                    }
                    g.setXORMode(bg);
                    g.fillRect(selectStartColumn * charWidth + xoffset,
                            l * charHeight + yoffset,
                            selectEndColumn * charWidth,
                            charHeight);
                    g.setPaintMode();
                }
            }

        }

        // draw border
        if (insets != null) {
            g.setColor(getBackground());
            xoffset--;
            yoffset--;
            for (int i = insets.top - 1; i >= 0; i--) {
                g.draw3DRect(xoffset - i, yoffset - i,
                        charWidth * buffer.width + 1 + i * 2,
                        charHeight * buffer.height + 1 + i * 2,
                        raised);
            }
        }
        update[0] = false;
        html.append("</pre>");
        return html.toString();
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
     * Set default for printing black&amp;white or colorized as displayed on
     * screen.
     * @param colorPrint true = print in full color, default b&amp;w only
     */
    public void setColorPrinting(boolean colorPrint) {
        colorPrinting = colorPrint;
    }

    /**
     * Redraws the terminal in a way suitable for printing on a printer.
     * @param g The graphics to redraw on.
     */
    @Override
    public void print(Graphics g) {
        if (debug > 0) {
            System.err.println("DEBUG: print()");
        }
        for (int i = 0; i <= buffer.height; i++) {
            update[i] = true;
        }
        Color fg = null, bg = null, colorSave[] = null;
        if (!colorPrinting) {
            fg = getForeground();
            bg = getBackground();
            setForeground(Color.black);
            setBackground(Color.white);
            colorSave = color;
            color = new Color[]{Color.black,
                        Color.black,
                        Color.black,
                        Color.black,
                        Color.black,
                        Color.black,
                        Color.black,
                        Color.white,
                        null,
                        null,
                        Color.black,
                        Color.black,
                        Color.black,
                        Color.black,
                        Color.black,
                        Color.black,
                        Color.black,
                        Color.white,};
        }

        super.print(g);

        if (!colorPrinting) {
            color = colorSave;
            setForeground(fg);
            setBackground(bg);
        }
    }

    /**
     * Convert Mouse Event coordinates into character cell coordinates
     * @param  evtpt the mouse point to be converted
     * @return Character cell coordinate of passed point
     */
    public Point mouseGetPos(Point evtpt) {
        Point mousepos;

        mousepos = new Point(0, 0);

        int xoffset = (super.getSize().width - buffer.width * charWidth) / 2;
        int yoffset = (super.getSize().height - buffer.height * charHeight) / 2;

        mousepos.x = (evtpt.x - xoffset) / charWidth;
        if (mousepos.x < 0) {
            mousepos.x = 0;
        }
        if (mousepos.x >= buffer.width) {
            mousepos.x = buffer.width - 1;
        }

        mousepos.y = (evtpt.y - yoffset) / charHeight;
        if (mousepos.y < 0) {
            mousepos.y = 0;
        }
        if (mousepos.y >= buffer.height) {
            mousepos.y = buffer.height - 1;
        }

        return mousepos;
    }

    /**
     * Reshape character display according to resize strategy.
     * @param x The left side of where to resize from
     * @param y The right side of where to resize from
     * @param w The number of columns in the newly resized display
     * @param h The number of rows in the newly resized display
     * @see #setResizeStrategy
     */
    @Override
    public void setBounds(int x, int y, int w, int h) {
        if (debug > 0) {
            System.err.println("VDU: setBounds(" + x + "," + y + "," + w + "," + h + ")");
        }

        super.setBounds(x, y, w, h);

        // ignore zero bounds
        if (x == 00 && y == 0 && w == 0 && h == 0) {
            return;
        }

        if (insets != null) {
            w -= insets.left + insets.right;
            h -= insets.top + insets.bottom;
        }

        if (debug > 0) {
            System.err.println("VDU: looking for better match for " + normalFont);
        }

        Font tmpFont = normalFont;
        String fontName = tmpFont.getName();
        int fontStyle = tmpFont.getStyle();
        fm = getFontMetrics(normalFont);
        if (fm != null) {
            charWidth = fm.charWidth('@');
            charHeight = fm.getHeight();
        }

        switch (resizeStrategy) {
            case RESIZE_SCREEN:
                buffer.setScreenSize(w / charWidth, buffer.height = h / charHeight);
                break;
            case RESIZE_FONT:
                int height = h / buffer.height;
                int width = w / buffer.width;

                fm = getFontMetrics(normalFont = new Font(fontName, fontStyle,
                        charHeight));

                // adapt current font size (from small up to best fit)
                if (fm.getHeight() < height || fm.charWidth('@') < width) {
                    do {
                        fm = getFontMetrics(normalFont = new Font(fontName, fontStyle,
                                ++charHeight));
                    } while (fm.getHeight() < height || fm.charWidth('@') < width);
                }

                // now check if we got a font that is too large
                if (fm.getHeight() > height || fm.charWidth('@') > width) {
                    do {
                        fm = getFontMetrics(normalFont = new Font(fontName, fontStyle,
                                --charHeight));
                    } while (charHeight > 1 &&
                            (fm.getHeight() > height ||
                            fm.charWidth('@') > width));
                }

                if (charHeight <= 1) {
                    System.err.println("VDU: error during resize, resetting");
                    normalFont = tmpFont;
                    //System.err.println("VDU: disabling font/screen resize");
                    //resizeStrategy = RESIZE_NONE;
                }

                setFont(normalFont);
                fm = getFontMetrics(normalFont);
                charWidth = fm.charWidth('@');
                charHeight = fm.getHeight();
                charDescent = fm.getDescent();
                break;
            case RESIZE_NONE:
            default:
                break;
        }
        if (debug > 0) {
            System.err.println("VDU: charWidth=" + charWidth + ", " +
                    "charHeight=" + charHeight + ", " +
                    "charDescent=" + charDescent);
        }

        update[0] = true;
    }

    /**
     * Return the real size in points of the character display.
     * @return Dimension the dimension of the display
     * @see java.awt.Dimension
     */
    @Override
    public Dimension getSize() {
        int xborder = 0, yborder = 0;
        if (insets != null) {
            xborder = insets.left + insets.right;
            yborder = insets.top + insets.bottom;
        }
        return new Dimension(buffer.width * charWidth + xborder,
                buffer.height * charHeight + yborder);
    }

    /**
     * Return the preferred Size of the character display.
     * This turns out to be the actual size.
     * @return Dimension dimension of the display
     * @see #size
     */
    @Override
    public Dimension getPreferredSize() {
        return getSize();
    }

    /**
     * The insets of the character display define the border.
     * @return Insets border thickness in pixels
     */
    @Override
    public Insets getInsets() {
        return insets;
    }

    public void clearSelection() {
        selectBegin = new Point(0, 0);
        selectEnd = new Point(0, 0);
        selection = null;
    }

    public String getSelection() {
        return selection;
    }

    private boolean buttonCheck(int modifiers, int mask) {
        return (modifiers & mask) == mask;
    }

    public void mouseMoved(MouseEvent evt) {
        /* nothing yet we do here */
    }

    public void mouseDragged(MouseEvent evt) {
        if (buttonCheck(evt.getModifiers(), MouseEvent.BUTTON1_MASK) ||
                // Windows NT/95 etc: returns 0, which is a bug
                evt.getModifiers() == 0) {
            int xoffset = (super.getSize().width - buffer.width * charWidth) / 2;
            int yoffset = (super.getSize().height - buffer.height * charHeight) / 2;
            int x = (evt.getX() - xoffset) / charWidth;
            int y = (evt.getY() - yoffset) / charHeight + buffer.windowBase;
            int oldx = selectEnd.x, oldy = selectEnd.y;

            if ((y <= selectBegin.y) || (y == selectBegin.y && x <= selectBegin.x)) {
                selectBegin.x = x;
                selectBegin.y = y;
            } else {
                selectEnd.x = x;
                selectEnd.y = y;
            }

            if (oldx != x || oldy != y) {
                update[0] = true;
                if (debug > 0) {
                    System.err.println("select([" + selectBegin.x + "," + selectBegin.y + "]," +
                            "[" + selectEnd.x + "," + selectEnd.y + "])");
                }
                repaint();
            }
        }
    }

    public void mouseClicked(MouseEvent evt) {
        /* nothing yet we do here */
    }

    public void mouseEntered(MouseEvent evt) {
        /* nothing yet we do here */
    }

    public void mouseExited(MouseEvent evt) {
        /* nothing yet we do here */
    }

    /**
     * Handle mouse pressed events for copy & paste.
     * @param evt the event that occured
     * @see java.awt.event.MouseEvent
     */
    public void mousePressed(MouseEvent evt) {
        requestFocus();

        int xoffset = (super.getSize().width - buffer.width * charWidth) / 2;
        int yoffset = (super.getSize().height - buffer.height * charHeight) / 2;

        // looks like we get no modifiers here ... ... We do? -Marcus
        if (buttonCheck(evt.getModifiers(), MouseEvent.BUTTON1_MASK)) {
            selectBegin.x = (evt.getX() - xoffset) / charWidth;
            selectBegin.y = (evt.getY() - yoffset) / charHeight + buffer.windowBase;
            selectEnd.x = selectBegin.x;
            selectEnd.y = selectBegin.y;
        }
    }

    /**
     * Handle mouse released events for copy & paste.
     * @param evt the mouse event
     */
    public void mouseReleased(MouseEvent evt) {

        if (buttonCheck(evt.getModifiers(), MouseEvent.BUTTON1_MASK)) {
            mouseDragged(evt);

            if (selectBegin.x == selectEnd.x && selectBegin.y == selectEnd.y) {
                update[0] = true;
                repaint();
                return;
            }
            selection = "";
            // fix end.x and end.y, they can get over the border
            if (selectEnd.x < 0) {
                selectEnd.x = 0;
            }
            if (selectEnd.y < 0) {
                selectEnd.y = 0;
            }
            if (selectEnd.y >= buffer.charArray.length) {
                selectEnd.y = buffer.charArray.length - 1;
            }
            if (selectEnd.x > buffer.charArray[0].length) {
                selectEnd.x = buffer.charArray[0].length;
            }

            // Initial buffer space for selectEnd - selectBegin + 1 lines
            // NOTE: Selection includes invisible text as spaces!
            // (also leaves invisible non-whitespace selection ending as spaces)
            StringBuffer selectionBuf =
                    new StringBuffer(buffer.charArray[0].length * (selectEnd.y - selectBegin.y + 1));

            for (int l = selectBegin.y; l <= selectEnd.y; l++) {
                int start, end;
                start = (l == selectBegin.y ? start = selectBegin.x : 0);
                end = (l == selectEnd.y ? end = selectEnd.x : buffer.charArray[l].length);

                boolean newlineFound = false;
                char ch = ' ';
                for (int i = start; i < end; i++) {
                    if ((buffer.charAttributes[l][i] & VDUBuffer.INVISIBLE) != 0) {
                        ch = ' ';
                    } else {
                        ch = buffer.charArray[l][i];
                    }
                    if (ch == '\n') {
                        newlineFound = true;
                    }
                    selectionBuf.append(ch);
                }
                if (!newlineFound) {
                    selectionBuf.append('\n');
                }
                // Trim all spaces from end of line, like xterm does.
                selection += ("-" + (selectionBuf.toString())).trim().substring(1);
                if (end == buffer.charArray[l].length) {
                    selection += "\n";
                }
            }
        }
    }
    // lightweight component event handling
    private MouseListener mouseListener;

    /**
     * Add a mouse listener to the VDU. This is the implementation for
     * the lightweight event handling.
     * @param listener the new mouse listener
     */
    @Override
    public void addMouseListener(MouseListener listener) {
        mouseListener = AWTEventMulticaster.add(mouseListener, listener);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    }

    /**
     * Remove a mouse listener to the VDU. This is the implementation for
     * the lightweight event handling.
     * @param listener the mouse listener to remove
     */
    @Override
    public void removeMouseListener(MouseListener listener) {
        mouseListener = AWTEventMulticaster.remove(mouseListener, listener);
    }
    private MouseMotionListener mouseMotionListener;

    /**
     * Add a mouse motion listener to the VDU. This is the implementation for
     * the lightweight event handling.
     * @param listener the mouse motion listener
     */
    @Override
    public void addMouseMotionListener(MouseMotionListener listener) {
        mouseMotionListener = AWTEventMulticaster.add(mouseMotionListener, listener);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    }

    /**
     * Remove a mouse motion listener to the VDU. This is the implementation for
     * the lightweight event handling.
     * @param listener the mouse motion listener to remove
     */
    @Override
    public void removeMouseMotionListener(MouseMotionListener listener) {
        mouseMotionListener =
                AWTEventMulticaster.remove(mouseMotionListener, listener);
    }

    /**
     * Process mouse events for this component. It will call the
     * methods (mouseClicked() etc) in the added mouse listeners.
     * @param evt the dispatched mouse event
     */
    @Override
    public void processMouseEvent(MouseEvent evt) {
        // handle simple mouse events
        if (mouseListener != null) {
            switch (evt.getID()) {
                case MouseEvent.MOUSE_CLICKED:
                    mouseListener.mouseClicked(evt);
                    break;
                case MouseEvent.MOUSE_ENTERED:
                    mouseListener.mouseEntered(evt);
                    break;
                case MouseEvent.MOUSE_EXITED:
                    mouseListener.mouseExited(evt);
                    break;
                case MouseEvent.MOUSE_PRESSED:
                    mouseListener.mousePressed(evt);
                    break;
                case MouseEvent.MOUSE_RELEASED:
                    mouseListener.mouseReleased(evt);
                    break;
            }
        }
        super.processMouseEvent(evt);
    }

    /**
     * Process mouse motion events for this component. It will call the
     * methods (mouseDragged() etc) in the added mouse motion listeners.
     * @param evt the dispatched mouse event
     */
    @Override
    public void processMouseMotionEvent(MouseEvent evt) {
        // handle mouse motion events
        if (mouseMotionListener != null) {
            switch (evt.getID()) {
                case MouseEvent.MOUSE_DRAGGED:
                    mouseMotionListener.mouseDragged(evt);
                    break;
                case MouseEvent.MOUSE_MOVED:
                    mouseMotionListener.mouseMoved(evt);
                    break;
            }
        }
        super.processMouseMotionEvent(evt);
    }
    private KeyListener keyListener;

    /**
     * Add a key listener to the VDU. This is necessary to be able to receive
     * keyboard input from this component. It is a prerequisite for a
     * lightweigh component.
     * @param listener the key listener
     */
    @Override
    public void addKeyListener(KeyListener listener) {
        keyListener = AWTEventMulticaster.add(keyListener, listener);
        enableEvents(AWTEvent.KEY_EVENT_MASK);
    }

    /**
     * Remove key listener from the VDU. It is a prerequisite for a
     * lightweigh component.
     * @param listener the key listener to remove
     */
    @Override
    public void removeKeyListener(KeyListener listener) {
        keyListener = AWTEventMulticaster.remove(keyListener, listener);
    }

    /**
     * Process key events for this component.
     * @param evt the dispatched key event
     */
    @Override
    public void processKeyEvent(KeyEvent evt) {
        if (keyListener != null) {
            switch (evt.getID()) {
                case KeyEvent.KEY_PRESSED:
                    keyListener.keyPressed(evt);
                    break;
                case KeyEvent.KEY_RELEASED:
                    keyListener.keyReleased(evt);
                    break;
                case KeyEvent.KEY_TYPED:
                    keyListener.keyTyped(evt);
                    break;
            }
        }
        // consume TAB keys if they originate from our component
        if (evt.getKeyCode() == KeyEvent.VK_TAB && evt.getSource() == this) {
            evt.consume();
        }
        super.processKeyEvent(evt);
    }

    private String hex2(int i) {
        if (i >= 16)
            return Integer.toHexString(i);
        else
            return "0" + Integer.toHexString(i);
    }
    private String colorHex(Color fg) {
        return hex2(fg.getRed())+hex2(fg.getGreen())+hex2(fg.getBlue());
    }
}
