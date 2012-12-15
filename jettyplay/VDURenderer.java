/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import javax.swing.UIManager;

/**
 *
 * @author ais523
 */
public class VDURenderer {
    private static final int debug = 0;
    /**
     * Specifies that the renderer should not automatically resize.
     */
    public static final int RESIZE_NONE = 0;
    /**
     * Specifies that the renderer should automatically resize the font when the
     * terminal resizes.
     */
    public static final int RESIZE_FONT = 1;
    /**
     * Specifies that the renderer should automatically resize the area of the
     * screen it repaints in when the terminal resizes.
     */
    public static final int RESIZE_SCREEN = 2;
    private static final int COLOR_BOLD = 8;
    private static final int COLOR_INVERT = 9;
    private static final int COLOR_UNCOLORED_BOLD_REPLACEMENT = 10;
    private static final int COLOR_FIRST_BOLD_REPLACEMENT = 11;
    private static final int NUM_COLORS = 19;
    /* definitions of standards for the display unit */
    private static final int COLOR_FG_STD = 7;
    private static final int COLOR_BG_STD = 0;

    /* So, Java responds to a request for the font family names on the system
     * by parsing the information for every font installed on the system!
     * I changed it to use this method instead, instantiating the fonts one
     * at a time, but it /still/ responds by parsing the information for every
     * font installed on the system :( Leaving it this way because it's a bit
     * clearer, and massively inefficient no matter what I do.
     */
    private static boolean fontFamilyExists(String family) {
        return (new Font(family,Font.PLAIN,1).getFamily(Locale.ROOT).equals(family));
    }
    
    /**
     *  Find a sensible font for displaying ttyrecs with, by looking through
     *  a list of names of monospaced fonts.
     */
    private static String getSensibleFontName() {
        /* Mac OS X fixedwidth fonts */
        if (fontFamilyExists("Menlo")) {
            return "Menlo";
        }
        if (fontFamilyExists("Monaco")) {
            return "Monaco";
        }
        /* fixedwidth fonts typically used on Linux */
        if (fontFamilyExists("DejaVu Sans Mono")) {
            return "DejaVu Sans Mono";
        }
        if (fontFamilyExists("Liberation Mono")) {
            return "Liberation Mono";
        }
        if (fontFamilyExists("FreeMono")) {
            return "FreeMono";
        }
        /* Windows/Microsoft fixedwidth fonts */
        if (fontFamilyExists("Inconsolata")) {
            return "Inconsolata";
        }
        if (fontFamilyExists("Consolas")) {
            return "Consolas";
        }
        if (fontFamilyExists("Courier New")) {
            return "Courier New";
        }
        return "Monospaced";
    }
    /** the VDU buffer */
    private VDUBuffer buffer;
    private Font normalFont; /* normal font */
    private FontMetrics fm; /* current font metrics */
    private int charWidth; /* current width of a char */
    private int charHeight; /* current height of a char */
    private int charDescent; /* base line descent */
    private int resizeStrategy; /* current resizing strategy */
    private Object textAntialiasingType; /* how to render text */
    private boolean colorPrinting = false; /* print display in color */
    /*    private BufferedImage backingStore = null;
    private int backingStoreRows = 0;
    private int backingStoreColumns = 0;*/
    private boolean[] update;
    /** A list of colors used for representation of the display */
    private Color[] color = {new Color(0, 0, 0),
                             new Color(128, 0, 0),
                             new Color(0, 128, 0),
                             new Color(128, 64, 0),
                             new Color(0, 0, 192),
                             new Color(128, 0, 128),
                             new Color(0, 128, 128),
                             new Color(192, 192, 192),
                             null,
                             null,
                             new Color(255, 255, 255),
                             new Color(100, 100, 100),
                             new Color(255, 0, 0),
                             new Color(0, 255, 0),
                             new Color(255, 255, 0),
                             new Color(0, 64, 255),
                             new Color(255, 0, 255),
                             new Color(0, 255, 255),
                             new Color(255, 255, 255)};

    /**
     * Creates a new VDU renderer with a default font.
     * @param buffer The VDU buffer to render.
     * @param g A Graphics object used to calculate the initial size.
     */
    public VDURenderer(VDUBuffer buffer, Graphics g) {
        this(buffer, new Font(getSensibleFontName(), Font.PLAIN, 11), g);
    }

    /**
     * Creates a new VDU renderer with a specified font.
     * @param buffer The VDU buffer to render.
     * @param font The font to render with.
     * @param g A Graphics object used to calculate the initial size.
     */
    public VDURenderer(VDUBuffer buffer, Font font, Graphics g) {
        setVDUBuffer(buffer);
        // set the normal font to use
        setFont(font, g);
        // set the standard resize strategy
        setResizeStrategy(RESIZE_FONT);

        textAntialiasingType = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
    }

    /**
     * Create a color representation that is brighter than the standard
     * color but not what we would like to use for bold characters.
     * @param clr the standard color
     * @return the new brighter color
     */
    private Color brighten(Color clr) {
        int r;
        int g;
        int b;
        r = (int) min((clr.getRed() + 29) * 1.1, 255.0);
        g = (int) min((clr.getGreen() + 29) * 1.1, 255.0);
        b = (int) min((clr.getBlue() + 29) * 1.1, 255.0);
        return new Color(r, g, b);
    }

    /**
     * Create a color representation that is darker than the standard
     * color but not what we would like to use for bold characters.
     * @param clr the standard color
     * @return the new darker color
     */
    private Color darken(Color clr) {
        int r;
        int g;
        int b;
        r = (int) max(clr.getRed() * 0.9 - 29, 0.0);
        g = (int) max(clr.getGreen() * 0.9 - 29, 0.0);
        b = (int) max(clr.getBlue() * 0.9 - 29, 0.0);
        return new Color(r, g, b);
    }

    private double max(double f1, double f2) {
        return (f1 < f2) ? f2 : f1;
    }

    private double min(double f1, double f2) {
        return (f1 < f2) ? f1 : f2;
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
            if (old != null && old.charAttributes.length == buffer.charAttributes.length) {
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
    }

    /**
     * Return the currently associated VDUBuffer.
     * @return the current buffer
     */
    public VDUBuffer getVDUBuffer() {
        return buffer;
    }

    /**
     * Set the font to be used for rendering the characters on screen.
     * @param font the new font to be used
     * @param g A Graphics object used to recalculate font sizes
     */
    public void setFont(Font font, Graphics g) {
        normalFont = font;
        if (g != null) {
            fm = g.getFontMetrics(normalFont);
        }
        if (fm != null) {
            charWidth = fm.charWidth('@');
            charHeight = fm.getHeight();
            charDescent = fm.getDescent();
        }
        if (update != null) {
            update[0] = true;
        }
    }

    /**
     * Gets the font that is currently being used for rendering.
     * @return The font object being used for rendering.
     */
    public Font getFont() {
        return normalFont;
    }

    /**
     * Gets the antialiasing type that is currently being used for rendering.
     * @return A value that would be suitable for use as a value corresponding
     * to the key java.awt.ReneringHints.KEY_TEXT_ANTIALIASING when setting the
     * rendering hints of a Graphics2D value.
     */
    public Object getTextAntialiasingType() {
        return textAntialiasingType;
    }

    /**
     * Sets the antialiasing type to use for rendering.
     * @param textAntialiasingType A value that would be suitable for use as a
     * value corresponding to the key java.awt.ReneringHints.KEY_TEXT_ANTIALIASING
     * when setting the rendering hints of a Graphics2D value.
     */
    public void setTextAntialiasingType(Object textAntialiasingType) {
        this.textAntialiasingType = textAntialiasingType;
        update[0] = true;
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
     * Render the current VDU buffer as HTML.
     * @return An HTML representation of the current VDU buffer.
     */
    public String asHTML() {
        return redraw(null, true, 0, 0);
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
        return charHeight * buffer.height;
    }

    /**
     * Returns the current width of the area of this SwingTerminal used
     * to actually draw the terminal.
     * @return The width, in pixels.
     */
    public int getCurrentTerminalWidth() {
        return charWidth * buffer.width;
    }

    /**
     * The internal rendering function that contains code common to asHTML() and
     * redraw().
     * @param g The Graphics to render on. Can be null (in which case no
     * rendering to any Graphics is done). Is ignored if renderHTML is true.
     * @param renderHTML If true, will render to HTML rather than to a Graphics.
     * @param drawWidth The amount of the Graphics' width to draw on.
     * @param drawHeight The amount of the Graphics' height to draw on.
     * @return If renderHTML is true, an HTML representation of the VDU buffer.
     * Otherwise, an arbitrary string (which may or may not vaguely resemble
     * HTML).
     * @see #asHTML() 
     * @see #redraw(java.awt.Graphics, int, int) 
     */
    protected String redraw(Graphics g, boolean renderHTML, int drawWidth, int drawHeight) {
        if (g == null && !renderHTML) {
            return "";
        }
        StringBuilder html = new StringBuilder("<pre>");
        int width;
        int height;
        width = charWidth * buffer.width;
        height = charHeight * buffer.height;
        if (debug > 0) {
            System.err.println("redraw()");
        }
        if (g != null && g instanceof Graphics2D) {
            Map<RenderingHints.Key, Object> hints = new HashMap<>();
            hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, textAntialiasingType);
            ((Graphics2D) g).addRenderingHints(hints);
        }
        // clear background; grab the default background color for a panel in
        // the current LaF, and use it as the background color for the areas
        // of the SwingTerminal that aren't occupied by the terminal itself
        int xoffset = 0;
        int yoffset = 0;
        if (!renderHTML) {
            g.setColor(UIManager.getColor("Panel.background"));
            g.fillRect(0, 0, drawWidth, drawHeight);
            xoffset = (drawWidth - width) / 2;
            yoffset = (drawHeight - height) / 2;
            g.setFont(normalFont);
        }
        Color fg = color[COLOR_FG_STD];
        Color bg = color[COLOR_BG_STD];
        for (int l = 0; l < buffer.height; l++) {
            if (!update[0] && !update[l + 1] && !renderHTML) {
                continue;
            }
            if (!renderHTML) {
                update[l + 1] = false;
                if (debug > 2) {
                    System.err.println("redraw(): line " + l);
                }
            }
            for (int c = 0; c < buffer.width; c++) {
                int addr = 0;
                int currAttr = buffer.charAttributes[buffer.windowBase + l][c];
                fg = darken(color[COLOR_FG_STD]);
                bg = darken(color[COLOR_BG_STD]);
                if ((currAttr & VDUBuffer.COLOR_FG) != 0) {
                    fg = color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1];
                }
                if ((currAttr & VDUBuffer.COLOR_BG) != 0) {
                    bg = darken(color[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1]);
                }
                if ((currAttr & VDUBuffer.BOLD) != 0) {
                    // g.setFont(new Font(normalFont.getName(), Font.BOLD, normalFont.getSize()));
                    if (!renderHTML) g.setFont(normalFont.deriveFont(Font.BOLD));
                    if (null != color[COLOR_BOLD]) {
                        fg = color[COLOR_BOLD];
                    } else {
                        fg = color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1 + COLOR_FIRST_BOLD_REPLACEMENT];
                    }
                } else if (!renderHTML) {
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
                while ((c + addr < buffer.width) && ((buffer.charArray[buffer.windowBase + l][c + addr] < ' ') || (buffer.charAttributes[buffer.windowBase + l][c + addr] == currAttr))) {
                    if (buffer.screenBase + buffer.cursorY == buffer.windowBase + l && buffer.cursorX == c + addr) {
                        break;
                    }
                    if (buffer.charArray[buffer.windowBase + l][c + addr] < ' ') {
                        buffer.charArray[buffer.windowBase + l][c + addr] = ' ';
                        buffer.charAttributes[buffer.windowBase + l][c + addr] = 0;
                        continue;
                    }
                    addr++;
                }
                if (addr == 0) {
                    addr = 1;
                }
                if (buffer.screenBase + buffer.cursorY == buffer.windowBase + l && buffer.cursorX == c) {
                    // Draw the cursor. This is done by swapping foreground and
                    // background, then brightening the foreground twice and
                    // darkening the background twice.
                    Color swapc = fg;
                    fg = brighten(brighten(bg));
                    bg = darken(darken(swapc));
                }
                // clear the part of the screen we want to change (fill rectangle)
                if (!renderHTML) {
                    g.setColor(bg);
                    g.fillRect(c * charWidth + xoffset, l * charHeight + yoffset, addr * charWidth, charHeight);
                    g.setColor(fg);
                } else {
                    html.append("<span style='background-color:#")
                            .append(colorHex(bg))
                            .append((currAttr & VDUBuffer.BOLD) != 0 ? "; font-weight: bold" : "")
                            .append((currAttr & VDUBuffer.UNDERLINE) != 0 ? "; text-decoration: underline" : "")
                            .append("; color:#").append(colorHex(fg)).append("'>");
                }
                // draw the characters, if not invisible.
                if ((currAttr & VDUBuffer.INVISIBLE) == 0) {
                    if (!renderHTML) {
                        g.drawChars(buffer.charArray[buffer.windowBase + l], c, addr,
                                    c * charWidth + xoffset, (l + 1) * charHeight - charDescent + yoffset);
                    } else {
                        for (int i = 0; i < addr; i++) {
                            int cp = Character.codePointAt(buffer.charArray[buffer.windowBase + l], c + i);
                            if (cp == 0) {
                                html.append(' ');
                            } else if (cp >= 32 && cp <= 126 && cp != '&' && cp != '"') {
                                html.append(Character.toChars(cp));
                            } else {
                                html.append("&#x").append(Integer.toHexString(cp)).append(";");
                            }
                        }
                    }
                } else if (renderHTML) {
                    for (int i = 0; i < addr; i++) {
                        html.append(' ');
                    }
                }
                if (renderHTML) {
                    html.append("</span>");
                }
                if ((currAttr & VDUBuffer.UNDERLINE) != 0) {
                    g.drawLine(c * charWidth + xoffset, (l + 1) * charHeight - charDescent / 2 + yoffset, c * charWidth + addr * charWidth + xoffset, (l + 1) * charHeight - charDescent / 2 + yoffset);
                }
                c += addr - 1;
            }
            if (renderHTML) {
                html.append('\n');
            }
        }
        update[0] = false;
        html.append("</pre>");
        return html.toString();
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
     * This is designed to be used as part of a print chain; that is, it
     * can be wrapped by other .print methods specified via printchain.
     * @param g The graphics to print on.
     * @param printchain The 
     */
    public void print(Graphics g, Printable printchain) {
        if (debug > 0) {
            System.err.println("DEBUG: print()");
        }
        for (int i = 0; i <= buffer.height; i++) {
            update[i] = true;
        }
        Color fg = null;
        Color bg = null;
        Color[] colorSave = null;
        if (!colorPrinting) {
            colorSave = color;
            color = new Color[]{Color.black, Color.black, Color.black, Color.black, Color.black, Color.black, Color.black, Color.white, null, null, Color.black, Color.black, Color.black, Color.black, Color.black, Color.black, Color.black, Color.white};
        }
        printchain.print(g);
        if (!colorPrinting) {
            color = colorSave;
        }
    }

    /**
     * Reshape character display according to resize strategy.
     * @param x The left side of where to resize from
     * @param y The right side of where to resize from
     * @param w The number of columns in the newly resized display
     * @param h The number of rows in the newly resized display
     * @param g A Graphics object to use for calculating the font sizes
     * @see #setResizeStrategy
     */
    public void setBounds(int x, int y, int w, int h, Graphics g) {
        if (debug > 0) {
            System.err.println("VDU: setBounds(" + x + "," + y + "," + w + "," + h + ")");
        }
        // ignore zero bounds
        if (x == 0 && y == 0 && w == 0 && h == 0) {
            return;
        }
        if (debug > 0) {
            System.err.println("VDU: looking for better match for " + normalFont);
        }
        Font tmpFont = normalFont;
        String fontName = tmpFont.getName();
        int fontStyle = tmpFont.getStyle();
        fm = g.getFontMetrics(tmpFont);
        if (fm != null) {
            charWidth = fm.charWidth('@');
            charHeight = fm.getHeight();
            charDescent = fm.getDescent();
        }
        switch (resizeStrategy) {
            case RESIZE_SCREEN:
                buffer.setScreenSize(w / charWidth, buffer.height = h / charHeight);
                break;
            case RESIZE_FONT:
                int height = h / buffer.height;
                int width = w / buffer.width;
                fm = g.getFontMetrics(normalFont = new Font(fontName, fontStyle, charHeight));
                // adapt current font size (from small up to best fit)
                if (fm.getHeight() < height || fm.charWidth('@') < width) {
                    do {
                        fm = g.getFontMetrics(normalFont = new Font(fontName, fontStyle, ++charHeight));
                    } while (fm.getHeight() < height || fm.charWidth('@') < width);
                }
                // now check if we got a font that is too large
                if (fm.getHeight() > height || fm.charWidth('@') > width) {
                    do {
                        fm = g.getFontMetrics(normalFont = new Font(fontName, fontStyle, --charHeight));
                    } while (charHeight > 1 && (fm.getHeight() > height || fm.charWidth('@') > width));
                }
                if (charHeight <= 1) {
                    System.err.println("VDU: error during resize, resetting");
                    normalFont = tmpFont;
                    //System.err.println("VDU: disabling font/screen resize");
                    //resizeStrategy = RESIZE_NONE;
                }
                setFont(normalFont,g);
                fm = g.getFontMetrics(normalFont);
                charWidth = fm.charWidth('@');
                charHeight = fm.getHeight();
                charDescent = fm.getDescent();
                break;
            case RESIZE_NONE:
            default:
                break;
        }
        if (debug > 0) {
            System.err.println("VDU: charWidth=" + charWidth + ", " + "charHeight=" + charHeight + ", " + "charDescent=" + charDescent);
        }
        update[0] = true;
    }

    /**
     * Return the real size in points of the character display.
     * @return Dimension the dimension of the display
     * @see java.awt.Dimension
     */
    public Dimension getSize() {
        int xborder = 0;
        int yborder = 0;
        return new Dimension(buffer.width * charWidth + xborder,
                buffer.height * charHeight + yborder);
    }

    /**
     * Return the preferred Size of the character display.
     * This turns out to be the actual size.
     * @return Dimension dimension of the display
     */
    public Dimension getPreferredSize() {
        return getSize();
    }

    private String hex2(int i) {
        if (i >= 16) {
            return Integer.toHexString(i);
        } else {
            return "0" + Integer.toHexString(i);
        }
    }

    private String colorHex(Color fg) {
        return hex2(fg.getRed()) + hex2(fg.getGreen()) + hex2(fg.getBlue());
    }
}
