/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

/**
 * A conversion routine that's given a sequence of frame times in a ttyrec,
 * and converts them to frame times for use with a video.
 * 
 * Such classes can be stateful; they are given frames one at a time, and
 * can remember information from one frame to the next.
 * 
 * @author ais523
 */
public interface FrameTimeConvertor {
    /**
     * Requests a sensible frame rate for use with this convertor.
     * @return A frame rate, as a fraction of a second. Cannot sensibly
     * return a zero or negative value.
     */
    public double getFrameRate();
    /**
     * Tells the convertor to forget any information accumulated so far;
     * the next frame read will be considered to be the first in the ttyrec.
     */
    public void resetConvertor();
    /**
     * Calculates an appropriate time for the next frame.
     * @param frameTime The time of the next frame in the ttyrec, in seconds.
     * @return The time of the next frame in the video, in units of
     * 1/getFrameRate() seconds.
     * @see #getFrameRate() 
     */
    public int convertFrameTime(double frameTime);
}
