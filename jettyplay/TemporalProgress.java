/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

/**
 * An interface for things that can give two progress values as time
 * measured out of a maximum time.
 * @author ais523
 */
interface TemporalProgress {
    /**
     * Gets the maximum progress with the time.
     * @return The maximum progress with the time, in seconds.
     */
    public double getMaximumTime();
    /**
     * Gets the current progress with the time, performing the
     * operation correctly.
     * @return The current progress with the time, in seconds.
     */
    public double getCurrentTime();
    /**
     * Gets the current progress with the time, performing the
     * operation as an initial approximation.
     * @return The current progress with the time, in seconds.
     */
    public double getFuzzyTime();
}
