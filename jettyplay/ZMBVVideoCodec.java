/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.awt.Font;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.util.Arrays;
import java.util.zip.Deflater;

/**
 * The video encoding method Zip Motion Blocks Video. (Also known as the
 * DOSBox Capture Codec, after its original use.)
 * 
 * @author ais523
 */
public class ZMBVVideoCodec extends AbstractVideoCodec {

    Deflater deflater;
    int largestFrameSize = 0;
    int blockWidth = -1;
    int blockHeight = -1;
    byte[] prevUncompressedData;
    
    public ZMBVVideoCodec(int height, Font terminalFont, Object object,
                          boolean allowBold) {
        super(height, terminalFont, object, allowBold);
        deflater = new Deflater(Deflater.BEST_COMPRESSION);
    }

    @Override
    public String getFourCC() {
        return "ZMBV";
    }

    @Override
    public int getBlockSize() {
        return getRenderer().getCharHeight() * getRenderer().getCharWidth() * 4;
    }

    @Override
    public int getColorDepth() {
        return 32;
    }

    @Override
    public boolean getVerticalFlip() {
        return false;
    }

    /**
     * Compresses using zlib and sync-flushes the given input.
     * @param input The input to compress.
     * @param headerSize The number of bytes padding to put before the
     * compressed data.
     * @return An array with {@code headerSize} bytes of padding, followed
     * by the compression of {@code input}.
     */
    private byte[] deflationOf(byte[] input, int headerSize) {
        deflater.setInput(input);
        byte[] compressedOutput = new byte[1024];
        int coPos = 0;
        do {
            if (coPos == compressedOutput.length)
                compressedOutput = Arrays.copyOf(compressedOutput, coPos*3/2);
            coPos += deflater.deflate(compressedOutput, coPos,
                    compressedOutput.length - coPos, Deflater.SYNC_FLUSH);
        } while (coPos == compressedOutput.length);
        byte[] deflation = new byte[coPos + headerSize];
        System.arraycopy(compressedOutput, 0, deflation, headerSize, coPos);
        return deflation;
    }
    
    @Override
    public byte[] encodeKeyframe(TtyrecFrame frame) {
        byte[] uncompressedData = super.encodeKeyframe(frame);
        prevUncompressedData = uncompressedData;
        deflater.reset();
        /* Keyframe header: 01 00 01 01 08 blockwidth blockheight */
        byte[] encodedData = deflationOf(uncompressedData, 7);
        encodedData[0] = (byte)0x1;
        encodedData[1] = (byte)0x0;
        encodedData[2] = (byte)0x1;
        encodedData[3] = (byte)0x1;
        encodedData[4] = (byte)0x8;
        int bW = getRenderer().getCharWidth();
        if (super.getActualWidth() % bW == 0) blockWidth = bW;
        int bH = getRenderer().getCharHeight();
        if (super.getActualHeight() % bH == 0) blockHeight = bH;
        encodedData[5] = (byte)blockWidth;
        encodedData[6] = (byte)blockHeight;
        if (encodedData.length > largestFrameSize)
            largestFrameSize = encodedData.length;
        return encodedData;
    }

    /**
     * Encodes a frame that's a repeat of the previous one.
     * 
     * Due to the details of the ZMBV codec, there isn't actually a need to
     * look at the previous data; we can just encode a frame of "no motion",
     * that's a non-keyframe that's all 0s (and thus compresses very well).
     * 
     * @param frame The frame that was repeated. Only examined to check its
     * size.
     * @param prevEncoding The previous encoding of the frame; ignored.
     * @return The encoding of a no-motion frame.
     */
    @Override
    public byte[] encodeRepeatFrame(TtyrecFrame frame, byte[] prevEncoding) {
        /* 2 bytes per block */
        int len = (getActualWidth() * getActualHeight() /
                blockWidth / blockHeight * 2);
        if (len % 4 == 2)
            len += 2; /* 2 bytes of padding if there are an odd number of blocks */
        byte[] uncompressedMotionVectors = new byte[len];
        for (int i = 0; i < len; i++) {
            uncompressedMotionVectors[i] = 0;
        }
        byte[] encodedData = deflationOf(uncompressedMotionVectors, 1);
        encodedData[0] = (byte)0;
        if (encodedData.length > largestFrameSize)
            largestFrameSize = encodedData.length;
        return encodedData;
    }

    /**
     * Encodes a frame relative to a previous frame.
     * 
     * This is mostly done via encoding the XOR of this frame with the
     * previous frame. We rely on the fact that frames are always encoded in
     * order (it can skip or repeat frames, but not do them out of order),
     * meaning that we know the block size, and we have a copy of the previous
     * uncompressed data available.
     * 
     * @param frame The frame to encode.
     * @param prevFrame The frame to encode relative to. This is ignored;
     * rather we use the most recent frame to be encoded.
     * @return The encoded data.
     */
    @Override
    public byte[] encodeNonKeyframe(TtyrecFrame frame, TtyrecFrame prevFrame) {
        byte[] uncompressedData = super.encodeKeyframe(frame);
        byte[] residual = new byte[uncompressedData.length];
        int residualPos = 0;
        int residualCount = 0;
        final int w = getActualWidth();
        final int h = getActualHeight();
        int len = (w / blockWidth * h / blockHeight * 2);
        if (len % 4 == 2)
            len += 2; /* 2 bytes of padding if there are an odd number of blocks */
        byte[] motionVectors = new byte[len];
        int motionPos = 0;
        for (int y = 0; y < h / blockHeight; y++) {
            for (int x = 0; x < w / blockWidth; x++) {
                // TODO: better estimation
                byte motionX = 0;
                byte motionY = 0;
                int motion = ((int)motionX * 4) + ((int)motionY * w * 4);
                // The ZMBV format allows specifying motion relative to
                // outside the frame. This algorithm doesn't, though.
                boolean hasResidual = false;
                for (int j = y * blockHeight * w * 4;
                        j < (y + 1) * blockHeight * w * 4; j += w * 4) {
                    for (int k = x * 4 * blockWidth + j;
                            k < (x + 1) * (blockWidth * 4) + j; k += 4) {
                        if ((residual[residualPos++] =
                                (byte) (uncompressedData[k]
                                ^ prevUncompressedData[k + motion])) != 0) {
                            hasResidual = true;
                        }
                        if ((residual[residualPos++] =
                                (byte) (uncompressedData[k + 1]
                                ^ prevUncompressedData[k + 1 + motion])) != 0) {
                            hasResidual = true;
                        }
                        if ((residual[residualPos++] =
                                (byte) (uncompressedData[k + 2]
                                ^ prevUncompressedData[k + 2 + motion])) != 0) {
                            hasResidual = true;
                        }
                        residual[residualPos++] = 0;
                    }
                }
                if (!hasResidual) {
                    residualPos -= blockWidth * blockHeight * 4;
                } else {
                    residualCount++;
                }
                motionVectors[motionPos++] = (byte) ((motionX << 1)
                                | (hasResidual ? 1 : 0));
                motionVectors[motionPos++] = (byte) (motionY << 1);
            }
        }

        byte[] uncompressed = new byte[len + residualPos];
        System.arraycopy(motionVectors, 0, uncompressed, 0, len);
        System.arraycopy(residual, 0, uncompressed, len, residualPos);
        
        prevUncompressedData = uncompressedData;

        byte[] encodedData = deflationOf(uncompressed, 1);
        encodedData[0] = (byte)0;
        if (encodedData.length > largestFrameSize)
            largestFrameSize = encodedData.length;
        return encodedData;    
    }
       
    @Override
    public int getActualMaxFrameSize() {
        return largestFrameSize;
    }

    @Override
    public boolean newFramesAreKeyframes() {
        return false;
    }

    @Override
    public boolean repeatedFramesAreKeyframes() {
        return false;
    }

    @Override
    protected int[] getPixelOrder() {
        int[] pixelOrder = {2, 1, 0, 3}; /* BGRA */
        return pixelOrder;
    }

    @Override
    protected ColorModel getColorModel() {
        /* For whatever reason, Java seems to just ignore the pixel stride if
         * it would involve padding. So instead, we add an alpha channel to the
         * image and let it do the padding for us. */
        int[] colorWidths = {8, 8, 8, 8}; /* 24-bit color + 8 bits of alpha */
        return new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                colorWidths, true, false, Transparency.OPAQUE,
                DataBuffer.TYPE_BYTE);
    }
}
