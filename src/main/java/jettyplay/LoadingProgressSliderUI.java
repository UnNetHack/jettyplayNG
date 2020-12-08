/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.border.BevelBorder;

/**
 * An overriden UI for use with the main slider in JettyPlay.
 * @author ais523
 */
class LoadingProgressSliderUI extends BasicSliderUI {
    private JSlider component;
    private TemporalProgress progress;
    /**
     * 
     * @param slider
     * @param progress
     */
    public LoadingProgressSliderUI(JSlider slider, TemporalProgress progress) {
        super(slider);
        this.component = slider;
        this.progress = progress;
    }

    /**
     * Repaints the slider.
     * @param g The graphics to paint on.
     */
    @Override
    public void paintTrack(Graphics g) {
        Dimension slidersize = component.getSize();
        g.setColor(new Color(200,200,200));
        g.fillRect(0, 0,
                slidersize.width,
                slidersize.height - 1);
        g.setColor(new Color(150,150,150));
        /* Avoid division by 0 */
        double hpos = (slidersize.width - 1) * progress.getFuzzyTime();
        if (hpos != 0) {
            double m = progress.getMaximumTime();
            if (m != 0)
                hpos /= m;
            else
                hpos = slidersize.width - 1;
        }
        g.fillRect(0, 0, (int)hpos, slidersize.height - 1);
        g.setColor(new Color(100,100,100));
        hpos = (slidersize.width - 1) * progress.getCurrentTime();
        if (hpos != 0) {
            double m = progress.getMaximumTime();
            if (m != 0)
                hpos /= m;
            else
                hpos = slidersize.width - 1;
        }
        g.fillRect(0, 0, (int)hpos, slidersize.height - 1);
        new BevelBorder(BevelBorder.LOWERED).paintBorder(component, g,
                0, 0, slidersize.width - 1, slidersize.height - 1);
    }
}
