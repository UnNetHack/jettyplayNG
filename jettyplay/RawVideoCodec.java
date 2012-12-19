/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A codec for uncompressed encoding of video in the bgr24 color space.
 *
 * @author ais523
 */
class RawVideoCodec extends AbstractVideoCodec {
    
    /**
     * Creaes a new raw video codec to encode videos with the specified
     * parameters.
     * @param height The maximum height of the resulting encode.
     * @param font The font to encode with.
     * @param antialiasing The antialiasing scheme to use on the encode
     * (a RenderingHints.VALUE_TEXT_ANTIALIAS_* value).
     * @see RenderingHints#VALUE_TEXT_ANTIALIAS_OFF
     * @see RenderingHints#VALUE_TEXT_ANTIALIAS_OFF
     */
    public RawVideoCodec(int height, Font font, Object antialiasing) {
        super(height, font, antialiasing);
    }

    @Override
    public String getFourCC() {
        return "\0\0\0\0";
    }

    @Override
    public int getBlockSize() {
        return 65536; /* the value used by avconv, appears to work */
    }

    @Override
    public int getColorDepth() {
        return 24;
    }

    /**
     * Returns whether the container should vertically invert this image.
     * 
     * Uncompressed BGR data (especially in AVI) is interpreted as a Windows
     * bitmap, and those are typically interpreted from bottom to top. However,
     * Java uses a top-to-bottom implementation (and this cannot be overriden).
     * Thus, the only possibilities are inverting the scanlines by hand, or
     * putting a flag in the container to invert the image.
     * 
     * @return Always returns true.
     */
    @Override
    public boolean getVerticalFlip() {
        return true;
    }

    @Override
    protected int getBufferedImageType() {
        return BufferedImage.TYPE_3BYTE_BGR;
    }

    @Override
    public int getActualMaxFrameSize() {
        return getActualWidth() * getActualHeight() * 3;
    }

    @Override
    public boolean newFramesAreKeyframes() {
        return true;
    }

    @Override
    public boolean repeatedFramesAreKeyframes() {
        return true;
    }
}
