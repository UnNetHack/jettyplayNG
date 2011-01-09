/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

/**
 * A custom Swing component used to display a range of various information
 * in the Jettyplay application.
 * @author ais523
 */
public class JettySidebar extends JComponent {
    private Iterable<AttributedString> contents;
    private boolean startToEnd;
    private boolean vertical;

    private Object textAntialiasingType;

    /**
     * Creates a new Jettyplay sidebar ("information bar" in the UI).
     * @param initialContents The information to show as the sidebar is created,
     * as a list of formatted strings.
     */
    public JettySidebar(AttributedString[] initialContents) {
        setContents(Arrays.asList(initialContents));
        startToEnd = true;
        textAntialiasingType = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
        vertical = false;
    }

    private class RawBreakIterator extends BreakIterator {

        // Boundaries: after space, middot, hyphen, and slash.

        private CharacterIterator ci;
        private boolean atLast;
        private static final String BREAKS = " -\u00b7/";

        public RawBreakIterator() {}
        
        @Override
        public Object clone() {
            RawBreakIterator r = (RawBreakIterator) super.clone();
            r.ci = (CharacterIterator) r.ci.clone();
            return r;
        }

        @Override
        public int first() {
            ci.first();
            atLast = false;
            return 0;
        }

        @Override
        public int last() {
            atLast = true;
            return ci.getEndIndex()+1;
        }

        @Override
        public int next(int n) {
            int r = current();
            while (n > 0) {r = next(); n--;}
            while (n < 0) {r = previous(); n++;}
            return r;
        }

        @Override
        public int next() {
            if (atLast) return DONE;
            char c = ci.next();
            while (BREAKS.indexOf(c) == -1) {
                c = ci.next();
                if (c == CharacterIterator.DONE) return last();
            }
            return ci.getIndex()+1;
        }

        @Override
        public int previous() {
            char c;
            if (atLast) c = ci.last();
            else c = ci.previous();
            if (c == CharacterIterator.DONE) return DONE;
            atLast = false;
            while (BREAKS.indexOf(c) == -1) {
                c = ci.previous();
                if (c == CharacterIterator.DONE) return first();
            }
            return ci.getIndex()+1;
        }

        @Override
        public int following(int offset) {
            int oi = ci.getIndex();
            boolean oa = atLast;
            ci.setIndex(offset);
            atLast = false;
            int r = next();
            if (r == DONE) {ci.setIndex(oi); atLast = oa;}
            return r;
        }

        @Override
        public int current() {
            if (atLast) return ci.getEndIndex()+1;
            return ci.getIndex()+1;
        }

        @Override
        public CharacterIterator getText() {
            return ci;
        }

        @Override
        public void setText(CharacterIterator newText) {
            ci = (CharacterIterator) newText.clone();
            first();
        }
    }

    /**
     * Redraws the Jettyplay sidebar onscreen.
     * @param g The Graphics to draw it on. If this happens to be a Graphics2D,
     * the drawing will look better, but it will work even with other sorts of
     * Graphics (unless set to a vertical orientation).
     */
    @Override
    public void paintComponent(Graphics g) {
        if (g instanceof Graphics2D) {
            Map<RenderingHints.Key, Object> hints = new HashMap<RenderingHints.Key,Object>();
            hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, textAntialiasingType);
            hints.put(RenderingHints.KEY_FRACTIONALMETRICS,
                      RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            ((Graphics2D)g).addRenderingHints(hints);
        }
        g.setColor(Color.white);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.black);
        float d = new TextLayout(new AttributedString("M|y").getIterator(),
                getFontRenderContext()).getDescent();
        Rectangle2D mb = null;
        try {
            mb = g.getFont().getMaxCharBounds(getFontRenderContext());
        } catch (UnsupportedOperationException e) {
            // GCJ doesn't like getMaxCharBounds.
            mb = new TextLayout(new AttributedString("M|y").getIterator(),
                getFontRenderContext()).getBounds();
        }
        int x = getWidth();
        if (startToEnd || vertical) x = 2;
        int y = (int) (getHeight() - d);
        if (startToEnd && vertical) y = (int) d;
        for (AttributedString as : contents) {
            Rectangle2D r = getSizeOfString(as, g);
            if (!vertical) {
                if (!startToEnd) {
                    x -= (int) r.getWidth() + 8;
                }
                g.drawString(as.getIterator(), x,
                        (int) (y + mb.getHeight()) / 2);
                if (startToEnd) {
                    x += (int) r.getWidth() + 8;
                }
            } else {
                AttributedCharacterIterator asi = as.getIterator();
                if (asi.getEndIndex() == 0) continue;
                LineBreakMeasurer m = new LineBreakMeasurer(asi,
                        new RawBreakIterator(),
                        getFontRenderContext());
                List<TextLayout> layoutList =
                        new ArrayList<TextLayout>();
                int wrapWidth = getWidth() - 4;
                while (m.getPosition() < asi.getEndIndex()) {
                    layoutList.add(m.nextLayout(wrapWidth));
                    wrapWidth = getWidth() - 16;
                }
                int i;
                if (startToEnd) {
                    i = -1;
                } else {
                    i = layoutList.size() - 1;
                }
                while (true) {
                    if (startToEnd) {
                        i++;
                        y += 2;
                        if (i == layoutList.size()) break;
                        y += mb.getHeight() + 1;
                    }
                    if (g instanceof Graphics2D) {
                        layoutList.get(i).draw(
                                (Graphics2D) g, x + (i == 0 ? 0 : 12), y);
                    }
                    // otherwise we can't do anything...
                    if (!startToEnd) {
                        i--;
                        y -= mb.getHeight() + 1;
                        if (i < 0) {y-=2; break;}
                    }
                }
            }
        }
    }

    private static FontRenderContext cachedFontRenderContext = null;
    private FontRenderContext getFontRenderContext() {
        if (cachedFontRenderContext != null)
            return cachedFontRenderContext;
        try {
            cachedFontRenderContext =
                FontRenderContext.class.getConstructor(
                 AffineTransform.class, Object.class, Object.class).
                 newInstance(null, textAntialiasingType,
                     RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        } catch (Exception e) {
            cachedFontRenderContext =
                new FontRenderContext(null, textAntialiasingType !=
                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF, false);
        }
        return cachedFontRenderContext;
    }

    /**
     * Returns the current contents of the sidebar.
     * @return The sidebar contents, a number of formatted strings in an order.
     * The exact data type may vary, but it will always be Iterable.
     */
    public Iterable<AttributedString> getContents() {
        return contents;
    }
    /**
     * Changes the current contents of the sidebar.
     * @param contents The new sidebar contents, a number of formatted strings
     * in an order.
     */
    public void setContents(Iterable<AttributedString> contents) {
        this.contents = contents;
        repaint();
    }

    /**
     * Returns the current sidebar order.
     * @return true if the first element of the sidebar contents is the first
     * element of the sidebar; false if the first element of the sidebar
     * contents is the last element of the sidebar.
     */
    public boolean isStartToEnd() {
        return startToEnd;
    }
    /**
     * Changes which end of the sidebar shows the first element of the sidebar
     * contents.
     * @param startToEnd true if the first element of the sidebar contents is the first
     * element of the sidebar; false if the first element of the sidebar
     * contents is the last element of the sidebar.
     */
    public void setStartToEnd(boolean startToEnd) {
        this.startToEnd = startToEnd;
    }

    /**
     * Queries what sort of antialiasing is used on the sidebar.
     * @return A text antialiasing type, one of the RenderingHints enumeration.
     */
    public Object getTextAntialiasingType() {
        return textAntialiasingType;
    }
    /**
     * Sets the sort of antialiasing to use on the sidebar.
     * @param textAntialiasingType A text antialiasing type, one of the
     * RenderingHints enumeration.
     */
    public void setTextAntialiasingType(Object textAntialiasingType) {
        this.textAntialiasingType = textAntialiasingType;
        repaint();
    }

    private Rectangle2D getSizeOfString(AttributedString s, Graphics g) {
        if (g==null) g = getGraphics();
        if (s.getIterator().getEndIndex()==0)
            s = new AttributedString("|");
        TextLayout tl = new TextLayout(s.getIterator(), getFontRenderContext());
        return tl.getBounds();
    }

    /**
     * Queries the minimum size at which to draw the sidebar.
     * @return The minimum size at which the sidebar can be drawn.
     */
    @Override
    public Dimension getMinimumSize() {
        Rectangle2D r = getSizeOfString(new AttributedString("A test string used to establish metrics"), null);
        return new Dimension((int)r.getWidth(),(int)r.getHeight());
    }
    /**
     * Queries the preferred size at which to draw the sidebar.
     * @return The size at which the sidebar wishes to be drawn.
     */
    @Override
    public Dimension getPreferredSize() {
        return getMinimumSize();
    }

    void setVertical(boolean b) {
        vertical = b;
    }
}
