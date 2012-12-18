/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * A class that defines a skeleton implementation for encoding videos into
 * a container that uses a fixed framerate. It handles the progressListeners
 * and the encoding process for the individual frames (calling abstract methods
 * in the derived class to incorporate the encoded frames into the container).
 * @author ais523
 */
public abstract class FixedFramerateVideoContainer implements VideoContainer {

    private final Set<ProgressListener> progressListeners;
    private int frameCount;
    private static int KEYFRAME_INTERVAL = 60;
    private boolean cancelEncoding = false;
    
    /**
     * The constructor. Does nothing in this skeleton implementation
     * but initializing listeners.
     */
    public FixedFramerateVideoContainer() {
        this.progressListeners = new HashSet<>();
        this.frameCount = 0;
    }        
    
    @Override
    public void addProgressListener(ProgressListener listener) {
        progressListeners.add(listener);
    }
    
    /**
     * Encodes one keyframe into the container; a keyframe is a frame encoded
     * in a way that does not depend on previous frames.
     * @param frame The keyframe to encode.
     */
    protected abstract void encodeKeyframe(TtyrecFrame frame);
    /**
     * Encodes one frame into the container relative to the previous frame,
     * that is not a repeat of the previous frame.
     * @param frame The frame to encode.
     * @param prevFrame The previous frame that was encoded.
     */
    protected abstract void encodeNonKeyframe(
            TtyrecFrame frame, TtyrecFrame prevFrame);
    /**
     * Encodes a frame into the container that is a repeat of the previous
     * frame.
     * @param frame The frame to encode.
     */
    protected abstract void encodeRepeatedFrame(TtyrecFrame frame);

    /**
     * Encodes the frames of the video into the container, via calling back
     * through abstract methods. This method is intended to be called by
     * derived classes as part of their implementations of encodeVideo.
     * {@code timer.getFrameRate()} frames will be produced (via callback) for
     * each second of encoded video.
     * @param frames The frames to encode.
     * @param timer An object describing the translation from times in the
     * ttyrec to times in the encode.
     * @return The number of encode frames in the resulting video.
     * @throws CancellationException 
     * @see VideoContainer#encodeVideo(jettyplay.VideoCodec, java.util.Iterator, jettyplay.FrameTimeConvertor) 
     * @see #encodeKeyframe(jettyplay.TtyrecFrame) 
     * @see #encodeNonKeyframe(jettyplay.TtyrecFrame, jettyplay.TtyrecFrame) 
     * @see #encodeRepeatedFrame(jettyplay.TtyrecFrame)
     */
    protected final int encodeFrames(Iterator<TtyrecFrame> frames,
        FrameTimeConvertor timer) throws CancellationException {
        timer.resetConvertor();
        double frameRate = timer.getFrameRate();
        int encodeFrames = 0;
        int lastKeyframe = Integer.MIN_VALUE;
        TtyrecFrame prevFrame = null;
        synchronized(this) {
            frameCount = 0;
        }
        TtyrecFrame nextFrame = frames.hasNext() ? frames.next() : null;
        double nextFrameTime =
            timer.convertFrameTime(nextFrame.getRelativeTimestamp());
        while(nextFrame != null) {
            TtyrecFrame frame = nextFrame;
            nextFrame = frames.hasNext() ? frames.next() : null;
            double frameTime = nextFrameTime;
            if (nextFrame != null) {
                nextFrameTime =
                    timer.convertFrameTime(nextFrame.getRelativeTimestamp());
            } else {
                /* The last frame in the video appears exactly once in the
                 * encode. So we use 1.5 here to minimize rounding error. */
                nextFrameTime = (encodeFrames + 1.5);
            }
            boolean repeat = false;
            while (encodeFrames < nextFrameTime) {
                checkForCancellation();
                /* We place a keyframe every KEYFRAME_INTERVAL frames. The
                 * other frames can be non-keyframes. Because we're using a
                 * fixed framerate, we may have to repeat frames. Alternatively,
                 * if frames come too fast, we skip some (the while loop isn't
                 * entered at all). */
                if (!repeat && lastKeyframe + KEYFRAME_INTERVAL < encodeFrames)
                    encodeKeyframe(frame);
                else if (!repeat)
                    encodeNonKeyframe(frame, prevFrame);
                else
                    encodeRepeatedFrame(frame);
                encodeFrames++;
                repeat = true;
            }
            
            checkForCancellation();
            
            /* It's possible we dropped the frame. In this case, don't update
             * prevFrame. */
            if (repeat) prevFrame = frame;
            
            /* frameCount must be incremented in a thread-safe way */
            synchronized(this) {
                frameCount++;
            }
            for(ProgressListener pl : progressListeners) {
                pl.progressMade();
            }
        }
        checkForCancellation();
        return encodeFrames;
    }

    @Override
    public synchronized void cancelEncode() {
        cancelEncoding = true;
    }

    public synchronized int getFramesEncoded() {
        return frameCount;
    }

    /**
     * Immediately throws a CancellationException if cancelEncode() has
     * been called more recently than checkForCancellation() has been called.
     * @throws CancellationException if another thread is trying to cancel
     * the encode
     */
    protected synchronized void checkForCancellation()
            throws CancellationException {
        if (cancelEncoding) {
            cancelEncoding = false;
            throw new CancellationException();
        }
    }
}