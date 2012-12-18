/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.CancellationException;

/**
 * A class describing the AVI video container format, and performing encodes
 * into that container.
 * @author ais523
 */
public class AVIVideoContainer extends FixedFramerateVideoContainer {

    /* The "movi" list under construction, filled in by callbacks from the
       parent class. */
    private AVIList moviList;
    /* The most recently encoded video. */
    private AVIList encode;
    /* The previously encoded chunk. */
    private AVIChunk prevChunk;
    private VideoCodec codec;
    
    @Override
    public void encodeVideo(VideoCodec codec, Iterator<TtyrecFrame> frames,
        FrameTimeConvertor timer) throws CancellationException {
        
        /* We can't construct the headers until we've encoded the actual
           frames. So we encode those first. We place all the frames in
           one "movi" list. */
        moviList = new AVIList(false, "movi");
        this.codec = codec;
        prevChunk = null;
        int encodeFrames = encodeFrames(frames, timer);
        prevChunk = null; /* deallocate it */
        
        /* Now we've encoded the frames, we can work out the headers. */
        AVIList avi = new AVIList(true, "AVI ");
        AVIChunk header;
        
        checkForCancellation();
        
        /* avih header */
        header = new AVIChunk("avih");
        header.appendDword((int)(1000000 / timer.getFrameRate())); /* dwMicroSecPerFrame */
        header.appendDword((int)((codec.getActualMaxFrameSize() + 8) *
                timer.getFrameRate())); /* dwMaxBytesPerSec */
        header.appendDword(0); /* dwPaddingGranularity */
        header.appendDword(0); /* dwFlags */
        header.appendDword(encodeFrames); /* dwTotalFrames */
        header.appendDword(0); /* dwInitialFrames */
        header.appendDword(1); /* dwStreams */
        header.appendDword(codec.getActualMaxFrameSize() + 8); /* dwSuggestedBufferSize */
        header.appendDword(codec.getActualWidth()); /* dwWidth */
        header.appendDword(codec.getActualHeight()); /* dwHeight */
        header.appendDword(0); header.appendDword(0);
        header.appendDword(0); header.appendDword(0); /* 4 reserved dwords */
        avi.appendAtom(header);
        
        checkForCancellation();
        
        /* strl header */
        AVIList strl = new AVIList(false, "strl");
        /* strh header */
        header = new AVIChunk("strh");
        header.appendFourcc("vids"); /* fccType */
        header.appendFourcc(codec.getFourCC()); /* fccHandler */
        header.appendDword(0); /* dwFlags */
        header.appendWord((short)0); /* dwPriority */
        header.appendWord((short)0); /* dwLanguage */
        header.appendDword(0); /* dwInitialFrames */
        FramerateRatio fr = convertFramerateToFraction(timer.getFrameRate());
        header.appendDword(fr.numerator); /* dwRate */
        header.appendDword(fr.denominator); /* dwScale */
        header.appendDword(0); /* dwStart */
        header.appendDword(encodeFrames); /* dwLength */
        header.appendDword(codec.getBlockSize()); /* dwSuggestedBufferSize */
        header.appendDword(-1); /* dwQuality (-1 = lossless) */
        header.appendDword(0); /* dwSampleSize */
        header.appendWord((short)0); /* left */
        header.appendWord((short)0); /* top */
        header.appendWord((short)codec.getActualWidth()); /* right */
        header.appendWord((short)codec.getActualHeight()); /* bottom */
        strl.appendAtom(header);
        /* strf header */
        header = new AVIChunk("strf");
        header.appendDword(40); /* biSize */
        header.appendDword(codec.getActualWidth()); /* biWidth */
        header.appendDword(codec.getActualHeight() *
                (codec.getVerticalFlip() ? -1 : 1)); /* biHeight */
        header.appendWord((short)1); /* biPlanes; "always 1" according to docs */
        header.appendWord((short)codec.getColorDepth()); /* biBitCount */
        header.appendFourcc(codec.getFourCC()); /* biCompression, seems to use a fourcc */
        header.appendDword(codec.getActualMaxFrameSize()); /* biSizeImage */
        header.appendDword(0); /* biXPelsPerMeter */
        header.appendDword(0); /* biYPelsPerMeter */
        header.appendDword(0); /* biClrUsed */
        header.appendDword(0); /* biClrImportant */
        strl.appendAtom(header);
        avi.appendAtom(strl);

        checkForCancellation();        
        
        avi.appendAtom(moviList);
        moviList = null; /* free it */

        checkForCancellation();
        
        encode = avi;
        
        checkForCancellation();
    }

    /**
     * Requests the appropriate file extension for this container.
     * @return Always returns "avi".
     */
    @Override
    public String getFileExtension() {
        return "avi";
    }

    private static void copyIntegerAs4ByteLE(int i, byte[] a, int offset) {
        a[offset] = (byte)i;
        a[offset+1] = (byte)Integer.rotateRight(i, 8);
        a[offset+2] = (byte)Integer.rotateRight(i, 16);
        a[offset+3] = (byte)Integer.rotateRight(i, 24);
    }

    @Override
    protected void encodeKeyframe(TtyrecFrame frame) {
        AVIChunk chunk = new AVIChunk("00dc"); /* fourcc for video stream 0 */
        chunk.appendKeyframe(codec, frame);
        moviList.appendAtom(chunk);
        prevChunk = chunk;
    }

    @Override
    protected void encodeNonKeyframe(TtyrecFrame frame, TtyrecFrame prevFrame) {
        AVIChunk chunk = new AVIChunk("00dc"); /* fourcc for video stream 0 */
        chunk.appendNonKeyframe(codec, frame, prevFrame);
        moviList.appendAtom(chunk);
        prevChunk = chunk;
    }

    @Override
    protected void encodeRepeatedFrame(TtyrecFrame frame) {
        AVIChunk chunk = new AVIChunk("00dc"); /* fourcc for video stream 0 */
        chunk.appendRepeatedFrame(codec, frame, prevChunk);
        moviList.appendAtom(chunk);
        prevChunk = chunk;
    }

    @Override
    public void outputEncode(OutputStream os) throws IOException {
        //if (encode == null) throw new IllegalStateException("No encode to write.");
        encode.serialize(os);
    }
    
    /**
     * An AVIAtom is the abstract data structure used to represent a video
     * in an AVI file. It uses a 4-byte FourCC, a 4-byte size (which is the
     * total size of the entire structure minus 8), perhaps another 4-byte
     * FourCC (for "lists" which recursively contain other AVI atoms),
     * followed by data (for a "chunk", arbitrary data, for a "list", other
     * AVI atoms).
     */
    public abstract class AVIAtom {
        /**
         * The internal storage used to contain a partially constructed AVI
         * atom. Can have more memory allocated than is actually being used.
         */
        protected ByteChunkList bytes;
        private byte[] lengthPlaceholder;
        /**
         * The length of {@code bytes[]} that is actually being used to store
         * data.
         * @see #bytes
         */
        protected int length;

        /**
         * Creates a new AVI atom with the given fourcc.
         * This constructor does the initialization common to lists and chunks;
         * further initialization (e.g. for the inner fourcc) may be needed by
         * deriving classes.
         * @param fourcc The fourcc of this atom type, as a string containing
         * only Latin-1 characters.
         */
        protected AVIAtom(String fourcc) {
            this.bytes = new ByteChunkList();
            this.length = 0;
            appendFourcc(fourcc);
            lengthPlaceholder = new byte[4];
            appendByteArray(lengthPlaceholder);
        }

        /**
         * Returns the length of this atom.
         * @return The total number of bytes in this atom, including its
         * fourcc and its length field.
         */
        public int getLength() {
            return length;
        }

        /**
         * Inserts this AVIAtom into an AVIList. After performing this
         * operation, this AVIAtom should not be changed.
         * @param a The AVIList to be mutated.
         */
        public void insertInto(AVIList a) {
            copyIntegerAs4ByteLE(length-8, lengthPlaceholder, 0);
            a.bytes.appendByteChunkList(bytes);
            a.length += length;
        }
        
        /**
         * Writes this AVIAtom to an output stream.
         * @param os The OutputStream to write the atom to.
         * @throws IOException if an error occurs writing to the stream 
         */
        public void serialize(OutputStream os) throws IOException {
            copyIntegerAs4ByteLE(length-8, lengthPlaceholder, 0);
            int i = 0;
            while (i < length) {
                byte[] buffer = new byte[65536];
                int len = bytes.getRestOfChunk(i, buffer, 0, 65536);
                os.write(buffer, 0, len);
                i += len;
            }
        }
        
        /**
         * Appends the given fourcc to the data in the atom.
         * @param fourcc The fourcc to append to the atom, as a string
         * containing only Latin-1 characters.
         */
        public final void appendFourcc(String fourcc) {
            appendByteArray(fourcc.getBytes(StandardCharsets.ISO_8859_1));
        }

        /**
         * Appends the given dword (4-byte integer) to the data in the atom.
         * @param i The integer to append to that atom.
         */
        public final void appendDword(int i) {
            byte[] tempBytes = new byte[4];
            copyIntegerAs4ByteLE(i, tempBytes, 0);
            appendByteArray(tempBytes);
        }

        /**
         * Appends the given word (2-byte integer) to the data in the atom.
         * @param i The short integer to append to that atom.
         */
        public final void appendWord(short i) {
            byte[] tempBytes = new byte[2];
            tempBytes[0] = (byte)i;
            tempBytes[1] = (byte)Short.reverseBytes(i);
            appendByteArray(tempBytes);
        }        

        /**
         * Appends a byte array to the end of the atom.
         * The array is wrapped not copied, and as such, the bytes in question
         * can be changed retrospectively by changing the array.
         * @param array The array to append.
         */
        public final void appendByteArray(byte[] array) {
            bytes.appendArray(array);
            length += array.length;
        }
    }

    /**
     * An AVIList is an AVIAtom that consists entirely of other AVIAtoms.
     */
    public class AVIList extends AVIAtom {
        /**
         * Creates a new AVI list, either a Riff-List or a regular List.
         * @param riff true for a Riff-List; false for a regular list.
         * @param fourcc The inner fourcc of the AVIList (e.g. "AVI " for
         * a Riff-AVI-List).
         */
        public AVIList(boolean riff, String fourcc) {
            super(riff ? "RIFF" : "LIST");
            appendFourcc(fourcc);
        }
        
        /**
         * Appends the given AVI atom to this list. The given atom should
         * not be changed after the insertion.
         * @param a The atom to append to the list.
         */
        public final void appendAtom(AVIAtom a) {
            a.insertInto(this);
        }
    }
    
    /**
     * An AVIChunk is an AVIAtom that contains no nested atoms (but contains
     * arbitrary data, typically either headers or an encoded video frame).
     * 
     * It contains methods for appending frames to the atoms; the methods for
     * appending other sorts of data are in the parent class AVIAtom.
     */
    public class AVIChunk extends AVIAtom {
        private byte[] lastFrameEncoding = null;
        
        /**
         * Creates a new AVI chunk.
         * @param fourcc The fourcc of the chunk to create.
         */
        public AVIChunk(String fourcc) {
            super(fourcc);
        }
        
        /**
         * Appends a keyframe to the data in the chunk. (A keyframe is one
         * whose encoding does not depend on the encoding of previous frames.)
         * @param codec The codec to use to encode the keyframe.
         * @param frame The ttyrec frame to encode.
         */
        public void appendKeyframe(VideoCodec codec,
                TtyrecFrame frame) {
            lastFrameEncoding = codec.encodeKeyframe(frame);
            appendByteArray(lastFrameEncoding);
        }
        
        /**
         * Appends a frame to the data in the chunk that is neither a keyframe,
         * nor a repeat of the previous frame.
         * @param codec The codec to use to encode the frame.
         * @param frame The ttyrec frame to encode.
         * @param prevFrame The previous ttyrec frame that was encoded.
         */
        public void appendNonKeyframe(VideoCodec codec,
                TtyrecFrame frame, TtyrecFrame prevFrame) {
            lastFrameEncoding = codec.encodeNonKeyframe(frame, prevFrame);
            appendByteArray(lastFrameEncoding);
        }

        /**
         * Appends a frame to the data in the chunk that's a repeat of the
         * previous frame.
         * @param codec The codec to use to encode the frame.
         * @param frame The ttyrec frame to encode.
         * @param prevChunk The chunk the previous frame was encoded into.
         * (This can be, but does not have to be, {@code this}.)
         */
        public void appendRepeatedFrame(VideoCodec codec,
                TtyrecFrame frame, AVIChunk prevChunk) {
            if (prevChunk.lastFrameEncoding == null)
                throw new IllegalStateException("prevChunk has not encoded a frame");
            lastFrameEncoding = codec.encodeRepeatFrame(frame,
                    prevChunk.lastFrameEncoding);
            appendByteArray(lastFrameEncoding);
        }
    }
    
    private class FramerateRatio {
        public int numerator;
        public int denominator;
    }
    
    private FramerateRatio convertFramerateToFraction(double frameRate) {
        FramerateRatio ratio = new FramerateRatio();
        /* We rely on the fact that frameRate was entered via a text field,
         * and so must be a terminating decimal. We also stop after 8
         * decimal places. */
        ratio.denominator = 1;
        while (ratio.denominator < 10000000 &&
               frameRate != (double)(int)frameRate) {
            frameRate *= 10;
            ratio.denominator *= 10;
        }
        /* Apparently AVI players break if the fraction isn't in its lowest
         * terms. */
        ratio.numerator = (int)frameRate;
        int gcd_x = ratio.numerator;
        int gcd_y = ratio.denominator;
        while (gcd_x > 0 && gcd_y > 0) {
            gcd_x %= gcd_y;
            if (gcd_x > 0) gcd_y %= gcd_x;
        }
        ratio.numerator /= (gcd_x + gcd_y);
        ratio.denominator /= (gcd_x + gcd_y);
        return ratio;
    }
}
