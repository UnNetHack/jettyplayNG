/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import javax.swing.SwingUtilities;

/**
 * An interface describing a file format used to contain raw video.
 * @author ais523
 */
public interface VideoContainer {
    /**
     * Encodes a ttyrec into a video and muxes it into the container
     * represented by this class. The resulting encode is stored in the
     * VideoContainer object itself, and can be accessed via
     * {@code outputEncode}.
     * @param codec The codec to encode each frame with.
     * @param frames An iterator that contains the actual frames to encode.
     * @param timer An object that converts between times in the ttyrec and
     * times in the video.
     * @throws CancellationException if the encode is cancelled using
     * {@code cancelEncode()} before it finishes encoding.
     */
    public void encodeVideo(VideoCodec codec, Iterator<TtyrecFrame> frames,
            FrameTimeConvertor timer) throws CancellationException;

    /**
     * Writes the encode most recently produced via encodeVideo to an
     * output stream.
     * @param os The output stream to write the encode to.
     * @throws IOException if an error occurs trying to write to the stream
     * @throws IllegalStateException if encodeVideo has not successfully run
     */
    public void outputEncode(OutputStream os) throws IOException;
    
    /**
     * Cancels an encode in progress, causing encodeVideo() to throw a
     * CancellationException. This can (in fact, must be) called from a 
     * different thread from the one doing the encode.
     */
    public void cancelEncode();
    
    /**
     * Asks for a reasonable file extension for use with this container.
     * @return A file extension, without the leading ".".
     */
    public String getFileExtension();
    
    /**
     * Requests updates when progress is made encoding a video.
     * @param listener A ProgressListener which will be called whenever a frame
     * is encoded. Note that it may be called from an unusual thread, and as
     * such, it should take care to be thread-safe, e.g. by passing control
     * off to the UI thread rather than handling the notification itself.
     * @see SwingUtilities#invokeLater(java.lang.Runnable)
     */
    public void addProgressListener(ProgressListener listener);
    
    /**
     * Returns the amount of progress made encoding the video, in frames.
     * This method is guaranteed to be thread-safe; it can be called
     * asynchronously during encoding and will produce a sensible result.
     * @return The number of frames from the ttyrec that have been encoded.
     */
    public int getFramesEncoded();
}
