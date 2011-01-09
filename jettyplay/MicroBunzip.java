/*
micro-bunzip, a small, simple bzip2 decompression implementation.
Copyright 2003 by Rob Landley (rob@landley.net).

Based on bzip2 decompression code by Julian R Seward (jseward@acm.org),
which also acknowledges contributions by Mike Burrows, David Wheeler,
Peter Fenwick, Alistair Moffat, Radford Neal, Ian H. Witten,
Robert Sedgewick, and Jon L. Bentley.

Ported to Java by Alex Smith, copyright 2010 Alex Smith (in addition
to the previously mentioned copyrights).

I hereby release this code under the GNU Library General Public License
(LGPL) version 2, available at http://www.gnu.org/copyleft/lgpl.html

The use of this library within Jettyplay as a whole is under the terms
of the full GPL version 2, as Jettyplay is a GPL-licenced work. This does
not, however, preclude using a copy of this library under the LGPL in
another context.
*/
package jettyplay;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * A relatively literal port to Java of Rob Landley's micro-bunzip.c, a
 * bzip2 decompressor. The coding style has not been changed and is still
 * very C-like; the main difference is that it now uses Java streams rather
 * than C file descriptors, and that it will throw an InterruptedException
 * if the thread it's running in is interrupted.
 * @author Rob Landley
 * @author Alex Smith
 */
public class MicroBunzip {

    /* Constants for huffman coding */
    public static final int MAX_GROUPS = 6;
    public static final int GROUP_SIZE = 50;       /* 64 would have been more efficient */

    public static final int MAX_HUFCODE_BITS = 20; /* Longest huffman code allowed */

    public static final int MAX_SYMBOLS = 258;      /* 256 literals + RUNA + RUNB */

    public static final int SYMBOL_RUNA = 0;
    public static final int SYMBOL_RUNB = 1;

    /* Status return values */
    public static final int RETVAL_OK = 0;
    public static final int RETVAL_LAST_BLOCK = (-1);
    public static final int RETVAL_NOT_BZIP_DATA = (-2);
    public static final int RETVAL_UNEXPECTED_INPUT_EOF = (-3);
    public static final int RETVAL_UNEXPECTED_OUTPUT_EOF = (-4);
    public static final int RETVAL_DATA_ERROR = (-5);
    public static final int RETVAL_OUT_OF_MEMORY = (-6);
    public static final int RETVAL_OBSOLETE_INPUT = (-7);

    /* Other housekeeping constants */
    public static final int IOBUF_SIZE = 4096;
    public static final String bunzip_errors[] = new String[]{
        null, "Bad file checksum", "Not bzip data",
        "Unexpected input EOF", "Unexpected output EOF", "Data error",
        "Out of memory", "Obsolete (pre 0.9.5) bzip format not supported."};

    /* This is what we know about each huffman coding group */
    private static class group_data {

        int[] limit = new int[MAX_HUFCODE_BITS];
        int[] base = new int[MAX_HUFCODE_BITS];
        int[] permute = new int[MAX_SYMBOLS];
        char minLen, maxLen;
    };

    /* Structure holding all the housekeeping data, including IO buffers and
    memory that persists between calls to bunzip */
    public static class bunzip_data {
        /* Input stream, input buffer, input bit buffer */

        int inbufCount, inbufPos;
        InputStream in_fd;
        char[] inbuf;
        int inbufBitCount, inbufBits;
        /* Output buffer */
        char[] outbuf = new char[IOBUF_SIZE];
        int outbufPos;
        /* The CRC values stored in the block header and calculated from the data */
        int[] crc32Table = new int[256];
        int headerCRC, dataCRC, totalCRC;
        /* Intermediate buffer and its size (in chars) */
        int[] dbuf;
        int dbufSize;
        /* State for interrupting output loop */
        int writePos, writeRun, writeCount, writeCurrent;

        /* These things are a bit too big to go on the stack */
        char[] selectors = new char[32768];
        group_data[] groups = new group_data[MAX_GROUPS]; /* huffman coding tables */
        public bunzip_data() {
            for (int i = 0; i < MAX_GROUPS; i++) 
                groups[i] = new group_data();
        }
    }

    /* The java translation of C setjmp/longjmp */
    private static class IntegerException extends Exception {

        int i;

        IntegerException(int j) {
            super("Integer thrown: " + j);
            i = j;
        }
    }
    /* The java translation of C forwards goto */

    private static class GotoException extends Exception {
    }
    /* And how you do a pointer */

    public static class bunzip_data_pointer {

        public bunzip_data bd;
    }

    /* Return the next nnn bits of input.  All reads from the compressed input
    are done through this function.  All reads are big endian */
    private static int get_bits(bunzip_data bd, char bits_wanted) throws IntegerException {
        int bits = 0;

        /* If we need to get more data from the char buffer, do so.  (Loop getting
        one char at a time to enforce endianness and avoid unaligned access.) */
        while (bd.inbufBitCount < bits_wanted) {
            /* If we need to read more data from file into char buffer, do so */
            if (bd.inbufPos == bd.inbufCount) {
                bd.inbufCount = 0;
                try {
                    int l = bd.in_fd.available();
                    if (l > IOBUF_SIZE) l = IOBUF_SIZE;
                    if (l < 1) l = 1;
                    byte[] ba = new byte[l];
                    bd.inbufCount = bd.in_fd.read(ba);
                    for (int i = 0; i < bd.inbufCount; i++) {
                        bd.inbuf[i] = (char) (((char) ba[i]) & 0xff);
                    }
                } catch (IOException ex) {
                }
                if (bd.inbufCount <= 0) {
                    throw new IntegerException(RETVAL_UNEXPECTED_INPUT_EOF);
                }
                bd.inbufPos = 0;
            }
            /* Avoid 32-bit overflow (dump bit buffer to top of output) */
            if (bd.inbufBitCount >= 24) {
                bits = bd.inbufBits & ((1 << bd.inbufBitCount) - 1);
                bits_wanted -= bd.inbufBitCount;
                bits <<= bits_wanted;
                bd.inbufBitCount = 0;
            }
            /* Grab next 8 bits of input from buffer. */
            bd.inbufBits = (bd.inbufBits << 8) | bd.inbuf[bd.inbufPos++];
            bd.inbufBitCount += 8;
        }
        /* Calculate result */
        bd.inbufBitCount -= bits_wanted;
        bits |= (bd.inbufBits >>> bd.inbufBitCount) & ((1 << bits_wanted) - 1);

        return bits;
    }

    /* Decompress a block of text to into intermediate buffer */
    public static int read_bunzip_data(bunzip_data bd)
            throws IntegerException, InterruptedException {
        group_data hufGroup = null;
        int dbufCount, nextSym, dbufSize, origPtr, groupCount, selector;
        int i, j, k, t, runPos, symCount, symTotal, nSelectors;
        int[] charCount = new int[256];
        char uc;
        char[] symTochar = new char[256];
        char[] mtfSymbol = new char[256];
        char[] headerSig = new char[6];
        char[] selectors;
        int[] dbuf;

        /* Read in header signature. */
        for (i = 0; i < 6; i++) {
            headerSig[i] = (char) get_bits(bd, (char) 8);
        }
        /* Read CRC (which is stored big endian). */
        bd.headerCRC = get_bits(bd, (char) 32);
        /* Is this the last block (with CRC for file)? */
        if ("\u0017\u0072\u0045\u0038\u0050\u0090".contentEquals(new String(headerSig))) {
            return RETVAL_LAST_BLOCK;
        }
        /* If it's not a valid data block, barf. */
        if (!"\u0031\u0041\u0059\u0026\u0053\u0059".contentEquals(new String(headerSig))) {
            return RETVAL_NOT_BZIP_DATA;
        }

        dbuf = bd.dbuf;
        dbufSize = bd.dbufSize;
        selectors = bd.selectors;
        /* We can add support for blockRandomised if anybody complains.  There was
        some code for this in busybox 1.0.0-pre3, but nobody ever noticed that
        it didn't actually work. */
        if (0 != get_bits(bd, (char) 1)) {
            return RETVAL_OBSOLETE_INPUT;
        }
        if ((origPtr = get_bits(bd, (char) 24)) > dbufSize) {
            return RETVAL_DATA_ERROR;
        }
        /* mapping table: if some char values are never used (encoding things
        like ascii text), the compression code removes the gaps to have fewer
        symbols to deal with, and writes a sparse bitfield indicating which
        values were present.  We make a translation table to convert the symbols
        back to the corresponding chars. */
        t = get_bits(bd, (char) 16);
        Arrays.fill(symTochar, (char) 0);
        symTotal = 0;
        for (i = 0; i < 16; i++) {
            if (Thread.interrupted()) throw new InterruptedException();
            if (0 != (t & (1 << (15 - i)))) {
                k = get_bits(bd, (char) 16);
                for (j = 0; j < 16; j++) {
                    if (0 != (k & (1 << (15 - j)))) {
                        symTochar[symTotal++] = (char) ((16 * i) + j);
                    }
                }
            }
        }
        /* How many different huffman coding groups does this block use? */
        groupCount = get_bits(bd, (char) 3);
        if (groupCount < 2 || groupCount > MAX_GROUPS) {
            return RETVAL_DATA_ERROR;
        }
        /* nSelectors: Every GROUP_SIZE many symbols we select a new huffman coding
        group.  Read in the group selector list, which is stored as MTF encoded
        bit runs. */
        if (0 == (nSelectors = get_bits(bd, (char) 15))) {
            return RETVAL_DATA_ERROR;
        }
        for (i = 0; i < groupCount; i++) {
            mtfSymbol[i] = (char) i;
        }
        for (i = 0; i < nSelectors; i++) {
            if (Thread.interrupted()) throw new InterruptedException();
            /* Get next value */
            for (j = 0; 0 != get_bits(bd, (char) 1); j++) {
                if (j >= groupCount) {
                    return RETVAL_DATA_ERROR;
                }
            }
            /* Decode MTF to get the next selector */
            uc = mtfSymbol[j];
            System.arraycopy(mtfSymbol, 0, mtfSymbol, 1, j);
            mtfSymbol[0] = selectors[i] = uc;
        }
        /* Read the huffman coding tables for each group, which code for symTotal
        literal symbols, plus two run symbols (RUNA, RUNB) */
        symCount = symTotal + 2;
        for (j = 0; j < groupCount; j++) {
            if (Thread.interrupted()) throw new InterruptedException();
            char[] length = new char[MAX_SYMBOLS];
            char[] temp = new char[MAX_HUFCODE_BITS + 1];
            int minLen, maxLen, pp;
            /* Read lengths */
            t = get_bits(bd, (char) 5);
            for (i = 0; i < symCount; i++) {
                for (;;) {
                    if (t < 1 || t > MAX_HUFCODE_BITS) {
                        return RETVAL_DATA_ERROR;
                    }
                    if (0 == get_bits(bd, (char) 1)) {
                        break;
                    }
                    if (0 == get_bits(bd, (char) 1)) {
                        t++;
                    } else {
                        t--;
                    }
                }
                length[i] = (char) t;
            }
            /* Find largest and smallest lengths in this group */
            minLen = maxLen = length[0];
            for (i = 1; i < symCount; i++) {
                if (length[i] > maxLen) {
                    maxLen = length[i];
                } else if (length[i] < minLen) {
                    minLen = length[i];
                }
            }
            /* Calculate permute[], base[], and limit[] tables from length[].
             *
             * permute[] is the lookup table for converting huffman coded symbols
             * into decoded symbols.  base[] is the amount to subtract from the
             * value of a huffman symbol of a given length when using permute[].
             *
             * limit[] indicates the largest numerical value a symbol with a given
             * number of bits can have.  It lets us know when to stop reading.
             *
             * To use these, keep reading bits until value<=limit[bitcount] or
             * you've read over 20 bits (error).  Then the decoded symbol
             * equals permute[hufcode_value-base[hufcode_bitcount]].
             */
            hufGroup = bd.groups[j];
            hufGroup.minLen = (char) minLen;
            hufGroup.maxLen = (char) maxLen;
            /* Note that minLen can't be smaller than 1, so we adjust the base
            and limit array pointers so we're not always wasting the first
            entry.  We do this again when using them (during symbol decoding).*/
            /* Calculate permute[] */
            pp = 0;
            for (i = minLen; i <= maxLen; i++) {
                for (t = 0; t < symCount; t++) {
                    if (length[t] == i) {
                        hufGroup.permute[pp++] = t;
                    }
                }
            }
            /* Count cumulative symbols coded for at each bit length */
            for (i = minLen; i <= maxLen; i++) {
                temp[i] = (char) (hufGroup.limit[i - 1] = (char) 0);
            }
            for (i = 0; i < symCount; i++) {
                temp[length[i]]++;
            }
            /* Calculate limit[] (the largest symbol-coding value at each bit
             * length, which is (previous limit<<1)+symbols at this level), and
             * base[] (number of symbols to ignore at each bit length, which is
             * limit-cumulative count of symbols coded for already). */
            t = pp = 0;
            for (i = minLen; i < maxLen; i++) {
                pp += temp[i];
                hufGroup.limit[i - 1] = pp - 1;
                pp <<= 1;
                hufGroup.base[i] = pp - (t += temp[i]);
            }
            hufGroup.limit[maxLen - 1] = pp + temp[maxLen] - 1;
            hufGroup.base[minLen - 1] = 0;
        }
        /* We've finished reading and digesting the block header.  Now read this
        block's huffman coded symbols from the file and undo the huffman coding
        and run length encoding, saving the result into dbuf[dbufCount++]=uc */

        /* Initialize symbol occurrence counters and symbol mtf table */
        Arrays.fill(charCount, 0);
        for (i = 0; i < 256; i++) {
            mtfSymbol[i] = (char) i;
        }
        /* Loop through compressed symbols */
        runPos = dbufCount = symCount = selector = 0;
        for (;;) {
            if (Thread.interrupted()) throw new InterruptedException();
            /* Determine which huffman coding group to use. */
            if (0 == (symCount--)) {
                symCount = GROUP_SIZE - 1;
                if (selector >= nSelectors) {
                    return RETVAL_DATA_ERROR;
                }
                hufGroup = bd.groups[selectors[selector++]];
            }
            /* Read next huffman-coded symbol */
            i = hufGroup.minLen;
            j = get_bits(bd, (char) i);
            for (;;) {
                if (i > hufGroup.maxLen) {
                    return RETVAL_DATA_ERROR;
                }
                if (j <= hufGroup.limit[i - 1]) {
                    break;
                }
                i++;

                j = (j << 1) | get_bits(bd, (char) 1);
            }
            /* Huffman decode nextSym (with bounds checking) */
            j -= hufGroup.base[i - 1];
            if (j < 0 || j >= MAX_SYMBOLS) {
                return RETVAL_DATA_ERROR;
            }
            nextSym = hufGroup.permute[j];
            /* If this is a repeated run, loop collecting data */
            if (nextSym == SYMBOL_RUNA || nextSym == SYMBOL_RUNB) {
                /* If this is the start of a new run, zero out counter */
                if (0 == runPos) {
                    runPos = 1;
                    t = 0;
                }
                /* Neat trick that saves 1 symbol: instead of or-ing 0 or 1 at
                each bit position, add 1 or 2 instead.  For example,
                1011 is 1<<0 + 1<<1 + 2<<2.  1010 is 2<<0 + 2<<1 + 1<<2.
                You can make any bit pattern that way using 1 less symbol than
                the basic or 0/1 method (except all bits 0, which would use no
                symbols, but a run of length 0 doesn't mean anything in this
                context).  Thus space is saved. */
                if (nextSym == SYMBOL_RUNA) {
                    t += runPos;
                } else {
                    t += 2 * runPos;
                }
                runPos <<= 1;
                continue;
            }
            /* When we hit the first non-run symbol after a run, we now know
            how many times to repeat the last literal, so append that many
            copies to our buffer of decoded symbols (dbuf) now.  (The last
            literal used is the one at the head of the mtfSymbol array.) */
            if (0 != runPos) {
                runPos = 0;
                if (dbufCount + t >= dbufSize) {
                    return RETVAL_DATA_ERROR;
                }

                uc = symTochar[mtfSymbol[0]];
                charCount[uc] += t;
                while (0 != t--) {
                    dbuf[dbufCount++] = uc;
                }
            }
            /* Is this the terminating symbol? */
            if (nextSym > symTotal) {
                break;
            }
            /* At this point, the symbol we just decoded indicates a new literal
            character.  Subtract one to get the position in the MTF array
            at which this literal is currently to be found.  (Note that the
            result can't be -1 or 0, because 0 and 1 are RUNA and RUNB.
            Another instance of the first symbol in the mtf array, position 0,
            would have been handled as part of a run.) */
            if (dbufCount >= dbufSize) {
                return RETVAL_DATA_ERROR;
            }
            i = nextSym - 1;
            uc = mtfSymbol[i];
            System.arraycopy(mtfSymbol, 0, mtfSymbol, 1, i);
            mtfSymbol[0] = uc;
            uc = symTochar[uc];
            /* We have our literal char.  Save it into dbuf. */
            charCount[uc]++;
            dbuf[dbufCount++] = uc;
        }
        /* At this point, we've finished reading huffman-coded symbols and
        compressed runs from the input stream.  There are dbufCount many of
        them in dbuf[].  Now undo the Burrows-Wheeler transform on dbuf.
        See http://dogma.net/markn/articles/bwt/bwt.htm
         */

        /* Now we know what dbufCount is, do a better sanity check on origPtr.  */
        if (origPtr < 0 || origPtr >= dbufCount) {
            return RETVAL_DATA_ERROR;
        }
        /* Turn charCount into cumulative occurrence counts of 0 to n-1. */
        j = 0;
        for (i = 0; i < 256; i++) {
            if (Thread.interrupted()) throw new InterruptedException();
            k = j + charCount[i];
            charCount[i] = j;
            j = k;
        }
        /* Figure out what order dbuf would be in if we sorted it. */
        for (i = 0; i < dbufCount; i++) {
            if (Thread.interrupted()) throw new InterruptedException();
            uc = (char) (dbuf[i] & 0xff);
            dbuf[charCount[uc]] |= (i << 8);
            charCount[uc]++;
        }
        /* blockRandomised support would go here. */

        /* Using i as position, j as previous character, t as current character,
        and uc as run count */
        bd.dataCRC = 0xffffffff;
        /* Decode first char by hand to initialize "previous" char.  Note that it
        doesn't get output, and if the first three characters are identical
        it doesn't qualify as a run (hence uc=255, which will either wrap
        to 1 or get reset). */
        if (0 != dbufCount) {
            bd.writePos = dbuf[origPtr];
            bd.writeCurrent = (char) (bd.writePos & 0xff);
            bd.writePos >>>= 8;
            bd.writeRun = -1;
        }
        bd.writeCount = dbufCount;

        return RETVAL_OK;
    }

    /* Flush output buffer to disk */
    public static void flush_bunzip_outbuf(bunzip_data bd, OutputStream out_fd) throws IntegerException {
        if (0 != bd.outbufPos) {
            byte[] op = new byte[bd.outbufPos];
            for (int i = 0; i < bd.outbufPos; i++) {
                op[i] = (byte) bd.outbuf[i];
            }
            try {
                out_fd.write(op);
            } catch (IOException ex) {
                throw new IntegerException(RETVAL_UNEXPECTED_OUTPUT_EOF);
            }
            bd.outbufPos = 0;
        }
    }


    /* Undo burrows-wheeler transform on intermediate buffer to produce output.
    If !len, write up to len chars of data to buf.  Otherwise write to out_fd.
    Returns len ? chars written : RETVAL_OK.  Notice all errors negative #'s. */
    public static int write_bunzip_data(bunzip_data bd, OutputStream out_fd, char[] outbuf, int len)
            throws IntegerException, InterruptedException {
        int[] dbuf = bd.dbuf;
        int count = 0, pos = 0, current = 0, run = 0, copies, outchar, previous, gotcount = 0;
        for (;;) {
            if (Thread.interrupted()) throw new InterruptedException();
            try {
                /* If last read was short due to end of file, return last block now */
                if (bd.writeCount < 0) {
                    return bd.writeCount;
                }
                /* If we need to refill dbuf, do it. */
                if (0 == bd.writeCount) {
                    int i = read_bunzip_data(bd);
                    if (0 != i) {
                        if (i == RETVAL_LAST_BLOCK) {
                            bd.writeCount = i;
                            return gotcount;
                        } else {
                            return i;
                        }
                    }
                }
                /* Loop generating output */
                count = bd.writeCount;
                pos = bd.writePos;
                current = bd.writeCurrent;
                run = bd.writeRun;
                while (0 != count) {
                    /* If somebody (like busybox tar) wants a certain number of chars of
                    data from memory instead of written to a file, humor them */
                    if (len != 0 && bd.outbufPos >= len) {
                        throw new GotoException();
                    }
                    count--;
                    /* Follow sequence vector to undo Burrows-Wheeler transform */
                    previous = current;
                    pos = dbuf[pos];
                    current = pos & 0xff;
                    pos >>>= 8;
                    /* Whenever we see 3 consecutive copies of the same char,
                    the 4th is a repeat count */
                    if (run++ == 3) {
                        copies = current;
                        outchar = previous;
                        current = -1;
                    } else {
                        copies = 1;
                        outchar = current;
                    }
                    /* Output chars to buffer, flushing to file if necessary */
                    while (0 != copies--) {
                        if (bd.outbufPos == IOBUF_SIZE) {
                            flush_bunzip_outbuf(bd, out_fd);
                        }
                        bd.outbuf[bd.outbufPos++] = (char) outchar;
                        bd.dataCRC = (bd.dataCRC << 8) ^ bd.crc32Table[(bd.dataCRC >>> 24) ^ outchar];
                    }
                    if (current != previous) {
                        run = 0;
                    }
                }
                /* Decompression of this block completed successfully */
                bd.dataCRC = ~(bd.dataCRC);
                bd.totalCRC = ((bd.totalCRC << 1) | (bd.totalCRC >>> 31)) ^ bd.dataCRC;
                /* If this block had a CRC error, force file level CRC error. */
                if (bd.dataCRC != bd.headerCRC) {
                    bd.totalCRC = bd.headerCRC + 1;
                    return RETVAL_LAST_BLOCK;
                }
            } catch (GotoException ex) {
            }
            bd.writeCount = count;
            if (0 != len) {
                gotcount += bd.outbufPos;
                System.arraycopy(bd.outbuf, 0, outbuf, 0, len);
                /* If we got enough data, checkpoint loop state and return */
                if ((len -= bd.outbufPos) < 1) {
                    bd.outbufPos -= len;
                    if (0 != bd.outbufPos) {
                        System.arraycopy(bd.outbuf, 0, bd.outbuf, len, bd.outbufPos);
                    }
                    bd.writePos = pos;
                    bd.writeCurrent = current;
                    bd.writeRun = run;
                    return gotcount;
                }
            }
        }
    }

    /* Allocate the structure, read file header.  If !len, src_fd contains
    filehandle to read from.  Else inbuf contains data. */
    public static int start_bunzip(bunzip_data_pointer bdp, InputStream src_fd, char[] inbuf, int len) {
        bunzip_data bd;
        int i, j, c;

        bd = new bunzip_data();
        bdp.bd = bd;

        if (0 != len) {
            bd.inbuf = inbuf;
            bd.inbufCount = len;
            bd.in_fd = null;
        } else {
            bd.inbuf = new char[IOBUF_SIZE];
            bd.in_fd = src_fd;
        }
        /* Init the CRC32 table (big endian) */
        for (i = 0; i < 256; i++) {
            c = i << 24;
            for (j = 8; 0 != j; j--) {
                c = (0 != (c & 0x80000000)) ? (c << 1) ^ 0x04c11db7 : (c << 1);
            }
            bd.crc32Table[i] = c;
        }
        /* Setup for I/O error handling via longjmp */
        try {
            /* Ensure that file starts with "BZh" */
            for (i = 0; i < 3; i++) {
                if (get_bits(bd, (char) 8) != "BZh".charAt(i)) {
                    return RETVAL_NOT_BZIP_DATA;
                }
            }
            /* Next char ascii '1'-'9', indicates block size in units of 100k of
            uncompressed data.  Allocate intermediate buffer for block. */
            i = get_bits(bd, (char) 8);
            if (i < '1' || i > '9') {
                return RETVAL_NOT_BZIP_DATA;
            }
            bd.dbufSize = 100000 * (i - '0');
            bd.dbuf = new int[bd.dbufSize];
            return RETVAL_OK;
        } catch (IntegerException ex) {
            return ex.i;
        }
    }

    /* Example usage: decompress src_fd to dst_fd.  (Stops at end of bzip data,
    not end of file.) */
    public static int uncompressStream(InputStream src_fd, OutputStream dst_fd)
        throws InterruptedException {
        bunzip_data_pointer bdp = new bunzip_data_pointer();
        int i;
        try {
            if (0 == (i = start_bunzip(bdp, src_fd, null, 0))) {
                i = write_bunzip_data(bdp.bd, dst_fd, null, 0);
                if (i == RETVAL_LAST_BLOCK && bdp.bd.headerCRC == bdp.bd.totalCRC) {
                    i = RETVAL_OK;
                }
            }
            flush_bunzip_outbuf(bdp.bd, dst_fd);
        } catch (IntegerException ex) {
            i = ex.i;
        }
        return i;
    }
    public static String uncompressStreamWrapper(
            InputStream src_fd, OutputStream dst_fd) {
        String c;
        try {
            c = bunzip_errors[-uncompressStream(src_fd, dst_fd)];
            if (c == null) c = "Completed OK";
        } catch (InterruptedException ex) {
            c = "Interrupted";
        }
        return c;
    }

    /* Dumb little test thing, decompress stdin to stdout */
    public static void main(String[] args) throws FileNotFoundException {

        System.err.print('\n');
        System.err.println(uncompressStreamWrapper(
                new FileInputStream("/tmp/COPYING.txt.bz2"), System.out));
    }
}
