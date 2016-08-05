/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.Hashtable;

/**
 * A skeleton implementation for codecs. It handles rendering the frames to
 * a suitable buffer (the exact type can be chosen by an overriding codec).
 * 
 * @author ais523
 */
public abstract class AbstractVideoCodec extends VideoCodec {
    private int imageWidth = -1;
    private int imageHeight = -1;
    private final int height;
    private final Font font;
    private VDURenderer renderer = null;
    private final Object antialiasing;
    private Graphics2D graphics;
    private DataBufferByte dataBuffer = null;
    private BufferedImage image = null;
    private final boolean allowBold;
    
    /**
     * Creates a new abstract video codec to encode videos with the specified
     * parameters.
     * @param height The maximum height of the resulting encode.
     * @param font The font to encode with.
     * @param antialiasing The antialiasing scheme to use on the encode.
     * @param allowBold Whether to use bold fonts in addition to color.
     * (a RenderingHints.VALUE_TEXT_ANTIALIAS_* value).
     * @see RenderingHints#VALUE_TEXT_ANTIALIAS_OFF
     * @see RenderingHints#VALUE_TEXT_ANTIALIAS_ON
     */
    public AbstractVideoCodec(int height, Font font, Object antialiasing,
            boolean allowBold) {
        this.height = height;
        this.font = font;
        this.antialiasing = antialiasing;
        this.allowBold = allowBold;
    }
    
    /**
     * Encodes the current frame without reference to other frames. This
     * method returns the raw uncompressed data; compressed codecs will need
     * to compress it themselves.
     * 
     * @param frame The frame to encode.
     * @return The uncompressed data for the frame.
     */
    @Override
    public byte[] encodeKeyframe(TtyrecFrame frame) {
        /* Make sure the renderer is looking at the appropriate frame, and
         * make sure that the renderer and image exist. */
        int size = getUncompressedFrameSize(frame);
        renderer.redraw(graphics, imageWidth, imageHeight);
        return Arrays.copyOf(dataBuffer.getData(), size);
    }

    @Override
    public int getActualHeight() {
        if (imageHeight == -1) {
            throw new IllegalStateException("No frames have been encoded yet");
        }
        return imageHeight;
    }

    @Override
    public int getActualWidth() {
        if (imageWidth == -1) {
            throw new IllegalStateException("No frames have been encoded yet");
        }
        return imageWidth;
    }

    /**
     * Calculates the maximum number of bytes that might be used to render
     * the frame.
     * 
     * This abstract implementation calculates the number of bytes that would
     * be used for an uncompressed frame, and may need to be overriden for
     * compression algorithms that might make a file larger.
     */
    @Override
    public int getMaxFrameSize(TtyrecFrame frame) {
        return getUncompressedFrameSize(frame);
    }
    
    private int getUncompressedFrameSize(TtyrecFrame frame) {
        /* Chicken and egg problem here. The size of the renderer depends
         * on the sort of image it's drawing on, but the size of the image
         * depends on the size of the renderer. So we temporarily create
         * a BufferedImage just to calculate sizes. */
        if (renderer == null) {
            BufferedImage temp = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
            renderer = new VDURenderer(frame.getTerminalState(), font, temp.createGraphics());
            renderer.setResizeStrategy(VDURenderer.RESIZE_WIDTH);
            renderer.setTextAntialiasingType(antialiasing);
            renderer.setAllowBold(allowBold);
            renderer.setBounds(0, 0, 1, height, temp.createGraphics());
            constructImage();
            renderer.setBounds(0, 0, imageWidth, imageHeight, graphics);
        } else {
            /* If the terminal changes size during the ttyrec, our only
             * recourse is to change the font size. If it doesn't, this
             * is a no-op. */
            renderer.setResizeStrategy(VDURenderer.RESIZE_FONT);
            renderer.setVDUBuffer(frame.getTerminalState());
            renderer.setBounds(0, 0, imageWidth, imageHeight, graphics);
        }
        return imageWidth * imageHeight * getColorDepth() / 8;
    }

    private void constructImage() {
        if (image == null) {
            int w = renderer.getCurrentTerminalWidth();
            int h = renderer.getCurrentTerminalHeight();
            /* We lay the image out in memory by hand, because Java's standard
             * formats aren't enough for some codecs. */
            dataBuffer = new DataBufferByte(w * h * getColorDepth() / 8);
            WritableRaster wr = Raster.createInterleavedRaster(
                    dataBuffer, w, h, w * getColorDepth() / 8,
                    4, getPixelOrder(), null);
            
            image = new BufferedImage(getColorModel(), wr, false, new Hashtable<>());
            
            imageWidth = w;
            imageHeight = h;
            graphics = image.createGraphics();
        }
    }

    /**
     * Returns the renderer used by this codec for rendering.
     * 
     * This is intended for use by derived classes that want to query
     * additional information from the renderer in order to compress more
     * accurately (for instance, to determine the size of characters to
     * more accurately perform motion estimation).
     * 
     * The returned renderer should not be mutated except by derived classes
     * which really know what they're doing and why.
     * 
     * @return This codec's renderer.
     */
    protected VDURenderer getRenderer() {
        return renderer;
    }

    /**
     * Returns the pixel order this codec requires for use in memory.
     * 
     * @return An array containing the offset from the start of the pixel
     * to place the channels. The number and order of the channels is the
     * same as in the color model (e.g. for an RGB color space, there
     * should be three elements in the array, the offsets for red, green,
     * blue respectively).
     */
    protected abstract int[] getPixelOrder();

    /**
     * Returns the color model this codec requires to store elements in
     * memory.
     * 
     * @return A ColorModel that will be used when calculating the
     * uncompressed version of a bitmap.
     */
    protected abstract ColorModel getColorModel();
}
