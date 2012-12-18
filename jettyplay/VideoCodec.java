/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

/**
 * A class that represents codecs for representing individual frames
 * of video inside a video file.
 * 
 * The class will be passed frames to render, in order. It will also
 * be given a renderer to do rendering with; the same renderer should
 * be passed in on every call, and the codec may make changes to the
 * renderer if it likes (for instance, setting its size or resize
 * policy).
 * 
 * @author ais523
 */
public abstract class VideoCodec {
    /**
     * Calculates the maximum number of bytes that will be needed to represent
     * the given frame, rendered using the given renderer, in this codec. This
     * can be a very approximate guess and does not need to look at the frame
     * in detail, although it must be greater than or equal to the actual number
     * of bytes needed.
     * @param frame The frame to determine the size of.
     * @return The maximum number of bytes in the frame.
     */
    public abstract int getMaxFrameSize(TtyrecFrame frame);
    /**
     * Encodes the given frame using an encoding capable of standing on its
     * own, with no reference to previous frames.
     * 
     * @param frame The frame to encode.
     * @return The encoding of the frame, as a byte array.
     */
    public abstract byte[] encodeKeyframe(TtyrecFrame frame);
    /**
     * Encodes the given frame, possibly relative to the previous frame.
     * 
     * By default, this just ignores the previous frame and calls {@code
     * encodeKeyFrame}. It can (and probably should) be overridden in order to
     * produce more efficient encodings when using compressed formats.
     * 
     * @param frame The frame to encode.
     * @param prevFrame The previous frame encoded into the video.
     * @return The encoding of the frame, as a byte array.
     */
    public byte[] encodeNonKeyframe(TtyrecFrame frame, TtyrecFrame prevFrame) {
        return encodeKeyframe(frame);
    }
    /**
     * Encodes the given frame, which is identical to the previous frame.
     * 
     * By default, this simply copies the encoding of the previous frame. It
     * can (and probably should) be overridden in order to produce more
     * efficient encodings when using compressed formats, and must be in
     * cases where repeating the encoding of the previous frame would be
     * incorrect.
     * 
     * @param frame The frame to encode.
     * @param prevEncoding The encoding that was used last time this frame
     * was encoded.
     * @return The encoding of the frame. This might or might not share with
     * prevEncoding.
     */
    public byte[] encodeRepeatFrame(TtyrecFrame frame,
            byte[] prevEncoding) {
        return prevEncoding;
    }
    
    /**
     * Requests the height that this encoder actually used to encode frames.
     * This only has a meaningful value once at least one frame has been
     * encoded.
     * @return The height used for each frame.
     */
    public abstract int getActualHeight();
    
    /**
     * Requests the width that this encoder actually used to encode frames.
     * This only has a meaningful value once at least one frame has been
     * encoded.
     * @return The width used for each frame.
     */
    public abstract int getActualWidth();
    
    /**
     * Requests the size of the largest frame that was actually encoded.
     * (This can be much smaller than any return of getMaxFrameSize();
     * getMaxFrameSize() returns an estimate made in advance of how big a frame
     * can possibly get, whereas getActualMaxFrameSize looks back at the frames
     * that were actually produced and returns the actual size of the largest.)
     * This only has a meaningful value once at least one frame has been
     * encoded.
     * @return The size of the largest frame that has been encoded, in bytes.
     */
    public abstract int getActualMaxFrameSize();

    /**
     * Requests the FourCC code used to represent this codec inside containers.
     * @return A FourCC code specific to this container, represented as a
     * four-character string, each of whose characters is in the Latin-1 range.
     */
    public abstract String getFourCC();

    /**
     * Returns a suggested buffer size for holding "blocks" encoded in this
     * codec. This method should not be called until after frames are encoded,
     * in case the block size for the codec depends on the frame content.
     * (What a "block" is depends on the codec.)
     * @return The suggested buffer size, in bytes.
     */
    public abstract int getBlockSize();
    
    /**
     * Returns the number of bits per pixel that this codec uses to represent
     * colors. (Note that many container formats may only support powers of 2,
     * plus 24.)
     * @return The color depth, in bits per pixel.
     */
    public abstract int getColorDepth();
    
    /**
     * Returns whether the container should request that this video is flipped
     * vertically.
     * @return Whether to instruct players playing this video to flip it
     * vertically.
     */
    public abstract boolean getVerticalFlip();
}
