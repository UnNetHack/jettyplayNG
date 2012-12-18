/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.Hashtable;

/**
 * A codec for uncompressed encoding of video in the bgr24 color space.
 *
 * @author ais523
 */
class RawVideoCodec extends VideoCodec {

    BufferedImage image = null;
    DataBufferByte dataBuffer = null;
    private int imageWidth = -1;
    private int imageHeight = -1;
    private int largestFrameSize = 0;
    private final int height;
    private final Font font;
    private VDURenderer renderer = null;
    private final Object antialiasing;
    private Graphics2D graphics;
    
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
        this.height = height;
        this.font = font;
        this.antialiasing = antialiasing;
    }

    /**
     * Calculates the maximum number of bytes that might be used to render
     * the frame. (In fact, this codec is so simple that it can get it
     * exact: 3, times the width, times the height, for the 3bpp format.)
     */
    @Override
    public int getMaxFrameSize(TtyrecFrame frame) {
        /* Chicken and egg problem here. The size of the renderer depends
         * on the sort of image it's drawing on, but the size of the image
         * depends on the size of the renderer. So we temporarily create
         * a BufferedImage just to calculate sizes. */
        if (renderer == null) {
            BufferedImage temp = new BufferedImage(1, 1,
                    BufferedImage.TYPE_3BYTE_BGR);
            renderer = new VDURenderer(frame.getTerminalState(), font,
                    temp.createGraphics());
            renderer.setResizeStrategy(VDURenderer.RESIZE_WIDTH);
            renderer.setTextAntialiasingType(antialiasing);
            renderer.setBounds(0,0,1,height,temp.createGraphics());
            constructImage();
            renderer.setBounds(0,0,imageWidth,imageHeight,graphics);
        } else {
            /* If the terminal changes size during the ttyrec, our only
             * recourse is to change the font size. If it doesn't, this
             * is a no-op. */
            renderer.setResizeStrategy(VDURenderer.RESIZE_FONT);
            renderer.setVDUBuffer(frame.getTerminalState());
            renderer.setBounds(0,0,imageWidth,imageHeight,graphics);
        }
        return imageWidth * imageHeight * 3;
    }

    @Override
    public byte[] encodeKeyframe(TtyrecFrame frame) {
        /* Make sure the renderer is looking at the appropriate frame, and
         * make sure that the renderer and image exist. */
        int size = getMaxFrameSize(frame);
        renderer.redraw(graphics, imageWidth, imageHeight);
        if (size > largestFrameSize) largestFrameSize = size;
        return Arrays.copyOf(dataBuffer.getData(), size);
    }

    private void constructImage() {
        if (image == null) {
            int w = renderer.getCurrentTerminalWidth();
            int h = renderer.getCurrentTerminalHeight();
            /* We'd like to tell the WritableRaster to store the image
             * upside-down, but it doesn't accept a negative scanline
             * stride. We also can't flip the Graphics; then it starts
             * reporting negative heights for characters, and VDURenderer
             * gets very confused. Instead, we tell the container to
             * apply the vertical flip for us (probably in the video player). */
            int[] bands = {2,1,0};
            int[] colorWidths = {8,8,8};
            dataBuffer = new DataBufferByte(w * h * 3);
            ColorModel cm = new ComponentColorModel(
                    ColorSpace.getInstance(ColorSpace.CS_sRGB),
                    colorWidths, false, false, Transparency.OPAQUE,
                    DataBuffer.TYPE_BYTE);
            WritableRaster wr = Raster.createInterleavedRaster(
                    dataBuffer, w, h, w*3, 3, bands, null);
            /* BufferedImage's constructor takes a Hashtable, so we have
             * to use it even though it's obsolete… */
            image = new BufferedImage(cm, wr, false, new Hashtable());;
            imageWidth = w;
            imageHeight = h;
            graphics = image.createGraphics();
        }
    }

    @Override
    public int getActualHeight() {
        if (imageHeight == -1)
            throw new IllegalStateException("No frames have been encoded yet");
        return imageHeight;
    }

    @Override
    public int getActualWidth() {
        if (imageWidth == -1)
            throw new IllegalStateException("No frames have been encoded yet");
        return imageWidth;
    }

    @Override
    public int getActualMaxFrameSize() {
        return largestFrameSize;
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

    @Override
    public boolean getVerticalFlip() {
        return true;
    }
}
