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
class ZMBVVideoCodec extends AbstractVideoCodec {

    Deflater deflater;
    int largestFrameSize = 0;
    
    public ZMBVVideoCodec(int height, Font terminalFont, Object object) {
        super(height, terminalFont, object);
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

    @Override
    public byte[] encodeKeyframe(TtyrecFrame frame) {
        byte[] uncompressedData = super.encodeKeyframe(frame);
        deflater.reset();
        deflater.setInput(uncompressedData);
        byte[] compressedOutput = new byte[1024];
        int coPos = 0;
        do {
            if (coPos == compressedOutput.length)
                compressedOutput = Arrays.copyOf(compressedOutput, coPos*3/2);
            coPos += deflater.deflate(compressedOutput, coPos,
                    compressedOutput.length - coPos, Deflater.SYNC_FLUSH);
        } while (coPos == compressedOutput.length);
        /* Keyframe header: 01 00 01 01 08 blockwidth blockheight */
        byte[] encodedData = new byte[7 + coPos];
        encodedData[0] = (byte)0x1;
        encodedData[1] = (byte)0x0;
        encodedData[2] = (byte)0x1;
        encodedData[3] = (byte)0x1;
        encodedData[4] = (byte)0x8;
        encodedData[5] = (byte)getRenderer().getCharWidth();
        encodedData[6] = (byte)getRenderer().getCharHeight();
        System.arraycopy(compressedOutput, 0, encodedData, 7, coPos);
        if (coPos + 7 > largestFrameSize) largestFrameSize = coPos + 7;
        return encodedData;
    }

    @Override
    public int getActualMaxFrameSize() {
        return largestFrameSize;
    }

    @Override
    public boolean newFramesAreKeyframes() {
        return true; // TODO make this false
    }

    @Override
    public boolean repeatedFramesAreKeyframes() {
        return true; // TODO make this false
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
