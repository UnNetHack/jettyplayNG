/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * A stream that handles telnet negotiation on a given stream, filtering out
 * all telnet metadata and leaving the resulting stream clean.
 * @author ais523
 */
class TelnetFilterStream extends FilterInputStream {
    private final OutputStream o;

    private enum TelnetState {
        DATA, // the normal state
        IAC,  // expecting a command (IAC IAC = literal 255)
        WILL, // in a WILL command
        WONT, // in a WONT command
        DO,   // in a DO command
        DONT, // in a DONT command
        SB,   // at the start of subnegotiation
        SB39, // subnegotiating environment variables
        SB391,// subnegotiating environment variable send
        SBE,  // at the end of subnegotiation
        SBX,  // unknown subnegotiation, waiting for SE
        SB24  // subnegotiating terminal type, waiting for SE
    }
    private TelnetState s = TelnetState.DATA;
    private Set<Byte> wonts;
    private Set<Byte> donts;

    private final boolean debug = false;

    private enum SB391State {
        INITIAL, // only just started
        NORMAL, // not asking for anything in particular
        VAR, // asking for well-known variables
        USERVAR // asking for user variables
    }
    String environmentResponse = "";
    private SB391State t = SB391State.INITIAL;

    public TelnetFilterStream(InputStream i, OutputStream o) throws IOException {
        super(i);
        this.o = o;
        wonts = new HashSet<Byte>();
        donts = new HashSet<Byte>();
        // Offer to send binary, ask the other end of the connection to do the
        // same. Also offer to send environment variables, and say that we're
        // happy for the other side to suppress goahead, and to handle echoing
        // itself.
        o.write(255); // IAC
        o.write(251); // WILL
        o.write(0);   // BINARY
        o.write(255); // IAC
        o.write(253); // DO
        o.write(0);   // BINARY
        o.write(255); // IAC
        o.write(251); // WILL
        o.write(39);  // NEW-ENVIRON
        o.write(255); // IAC
        o.write(251); // WILL
        o.write(24);  // TERMINAL-TYPE
        o.write(255); // IAC
        o.write(251); // WILL
        o.write(31);  // NAWS
        o.write(255); // IAC
        o.write(251); // WILL
        o.write(33);  // TOGGLE-FLOW-CONTROL
        o.write(255); // IAC
        o.write(253); // DO
        o.write(3);   // SUPPRESS-GO-AHEAD
        o.write(255); // IAC
        o.write(251); // WILL
        o.write(3);   // SUPPRESS-GO-AHEAD
        o.write(255); // IAC
        o.write(253); // DO
        o.write(1);   // ECHO
        o.write(255); // IAC
        o.write(251); // WILL
        o.write(1);   // ECHO
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() throws IOException {
        while (true) {
            int i = super.read();
            if (i == -1) return -1;
            if (!respondInput((byte)i)) return i;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        // Implementation: read a chunk of input, delete all metadata from it
        // and return the resulting smaller chunk. Should it end up completely
        // empty, recurse.
        while (true) {
            int r = super.read(b, off, len);
            if (r == 0) return 0;
            int i = off;
            while (i - off < r) {
                if(respondInput(b[i])) {
                    r--;
                    System.arraycopy(b, i+1, b, i, r-i);
                } else i++;
            }
            if (r != 0) return r;
        }
    }

    /**
     * Responds to the given byte of input by sending replies on the output
     * stream.
     * @param b The byte to check.
     * @return Whether to delete the byte of input from the stream sent
     * onwards; if this is false, the byte should be sent onwards to the
     * user.
     */
    private boolean respondInput(byte b) throws IOException {
        if (debug) System.err.println("In state "+s);
        if (debug) System.err.println("Received byte " + ((int)b+256)%256);
        if (s == TelnetState.DATA && b != (byte)255) return false;
        if (s == TelnetState.DATA && b == (byte)255) {
            s = TelnetState.IAC;
            return true;
        }
        if (s == TelnetState.SB) {
            if (b == 39) {s = TelnetState.SB39; return true;}
            if (b == 24) {s = TelnetState.SB24; return true;}
            s = TelnetState.SBX;
            return true;
        } else if (s == TelnetState.SBE) {
            // If we get an SE, send our entire response.
            if (b == (byte)240) {
                // IAC SB NEW-ENVIRON IS
                if (debug) System.err.println(
                        "Sent byte 255\nSent byte 250\nSent byte 39\nSent byte 0");
                o.write(new byte[] {(byte)255, (byte)250, (byte)39, (byte)0});
                // The string of answers.
                for (char c: environmentResponse.toCharArray()) {
                    if (debug) System.err.println("Sent byte " + (int)c);
                    o.write(c);
                }
                // IAC SE
                if (debug) System.err.println("Sent byte 255\nSent byte 240");
                o.write(new byte[] {(byte)255, (byte)240});
                s = TelnetState.DATA;
            }
            return true;
        } else if (s == TelnetState.SB39) {
            if (b == 1) {
                environmentResponse = "";
                s = TelnetState.SB391;
                t = SB391State.INITIAL;
                return true;
            }
            s = TelnetState.SBX;
            return true;
        } else if (s == TelnetState.SB391) {
            if (b == 0 || b == 3 || b == (byte)255) { // SB special, or IAC
                // A special value. We send all variables of a type if the
                // previous value was also special, or if nothing (i.e.
                // everything) was requested.
                if (t == SB391State.USERVAR ||
                        (b == (byte)255 && t == SB391State.INITIAL)) {
                    // We're (pretending to be) a DEC vt220.
                    // Actually a vt320, but NAO (at least) doesn't have its
                    // termcap file installed.
                    // USERVAR "TERM" VALUE "vt220".
                    environmentResponse += "\u0003TERM\u0001vt220";
                }
                // If they requested the terminal type, that actually has
                // a value.
                if (environmentResponse.endsWith("\u0003TERM"))
                    environmentResponse += "\u0001vt220";
                if (b == (byte)255) s = TelnetState.SBE;
                if (b == 0) t = SB391State.VAR;
                if (b == 3) t = SB391State.USERVAR;
                return true;
            }
            // Not a special, send the value for the var requested.
            // Value is always blank, unless the var is "TERM".
            if (t == SB391State.VAR) environmentResponse += "\u0000";
            if (t == SB391State.USERVAR) environmentResponse += "\u0003";
            t = SB391State.NORMAL;
            environmentResponse += (char) b;
            return true;
        } else if (s == TelnetState.SBX) {
            if (b == (byte)240) s = TelnetState.DATA; // SE
            return true;
        } else if (s == TelnetState.SB24) {
            if (b == (byte)240) { // SE
                s = TelnetState.DATA;
                // We also have to output our terminal type.
                // IAC SB TERMINAL-TYPE IS
                if (debug) System.err.println("Sent terminal type");
                o.write(new byte[] {(byte)255, (byte)250, (byte)24, (byte)0});
                // DEC-VT220 IAC SE
                o.write(new byte[] {
                    'D','E','C','-','V','T','2','2','0', (byte)255, (byte)240
                });
            }
            return true;
        } else if (s == TelnetState.IAC) {
            if (b == (byte)255) {s = TelnetState.DATA; return false;} // IAC
            if (b == (byte)254) {s = TelnetState.DONT; return true;} // DONT
            if (b == (byte)253) {s = TelnetState.DO;   return true;} // DO
            if (b == (byte)252) {s = TelnetState.WONT; return true;} // WONT
            if (b == (byte)251) {s = TelnetState.WILL; return true;} // WILL
            if (b == (byte)250) {s = TelnetState.SB;   return true;} // SB
            if (b == (byte)249) {s = TelnetState.DATA; return true;} // GA
            if (b == (byte)241) {s = TelnetState.DATA; return true;} // NOP
            // Unhandled; just ignore it.
            s = TelnetState.DATA;
            return true;
        } else {
            // Time to respond to negotiation. The rules:
            // Nearly all DO/DONT is responded to via WONT; likewise, nearly
            // all WILL/WONT is responded to via DONT.
            // However, we never send a WONT or DONT twice without the matching
            // WILL or DO in between; that complies with the telnet spec, and
            // prevents a potential infinite loop of acknowledgements.
            // We treat option 0 (binary mode) specially, because we both
            // actually support that one, and want it turned on.
            // We also treat option 39 (environment variables) specially;
            // dgamelaunch needs it to work properly. We WILL do it, but DONT
            // want the other side to. Likewise, we have to reveal what our
            // window size is (we say 80x24); we WILL do it, but DONT care what
            // window size dgamelaunch is running in - that's option 31 - and
            // we'll give our terminal type via the older mechanism too if
            // necessary, option 24.
            // To save a bit of bandwidth, we treat option 3 (suppress-go-ahead)
            // specially, saying we're willing to let either side suppress it.
            // We're also happy to let the telnet server handle echo of the
            // stuff we send; if it wants to, then it probably is incapable of
            // letting us handle it ourselves. Likewise, we aren't planning to
            // send XON and XOFF, so we offer to let dgamelaunch control our
            // flow control, in the knowledge that we can safely ignore its
            // requests.
            if (((b != 0 && b != 1 && b != 3)
                    || s == TelnetState.DONT || s == TelnetState.WONT) &&
                    //(b != 1  || s != TelnetState.DO) &&
                    (b != 39 || s != TelnetState.DO) &&
                    (b != 24 || s != TelnetState.DO) &&
                    (b != 31 || s != TelnetState.DO) &&
                    (b != 33 || s != TelnetState.DO)) {
                if (s == TelnetState.WILL || s == TelnetState.WONT) {
                    s = TelnetState.DATA;
                    if (donts.contains(b)) return true;
                    if (debug) System.err.println("Sent byte 255\nSent byte 254");
                    if (debug) System.err.println("Sent byte "+b);
                    o.write(255); // IAC
                    o.write(254); // DONT
                    o.write(b); // the option stated
                    donts.add(b);
                    return true;
                } else {
                    s = TelnetState.DATA;
                    if (wonts.contains(b)) return true;
                    if (debug) System.err.println("Sent byte 255\nSent byte 252");
                    if (debug) System.err.println("Sent byte "+b);
                    o.write(255); // IAC
                    o.write(252); // WONT
                    o.write(b); // the option stated
                    wonts.add(b);
                    return true;
                }
            } else {
                // A request we can accept.
                if (s == TelnetState.DO) {
                    s = TelnetState.DATA;
                    if (b == 31) {
                        // "Do tell me your window size". We can send the size
                        // any time from now on; may as well send it immediately.
                        if (debug) System.err.println("Sent window size");
                        o.write(255); // IAC
                        o.write(250); // SB
                        o.write(31);  // NAWS
                        o.write(0);   // width  = 256 * 0
                        o.write(80);  //        + 80
                        o.write(0);   // height = 256 * 0
                        o.write(24);  //        + 24
                        o.write(255); // IAC
                        o.write(240); // SE
                    }
                    if (!wonts.contains(b)) return true;
                    wonts.remove(b);
                    if (debug) System.err.println("Sent byte 255\nSent byte 251");
                    if (debug) System.err.println("Sent byte "+b);
                    o.write(255); // IAC
                    o.write(251); // WILL
                    o.write(b); // option
                    return true;
                } else {
                    s = TelnetState.DATA;
                    if (!donts.contains(b)) return true;
                    donts.remove(b);
                    if (debug) System.err.println("Sent byte 255\nSent byte 253");
                    if (debug) System.err.println("Sent byte "+b);
                    o.write(255); // IAC
                    o.write(253); // DO
                    o.write(b); // option
                    return true;
                }
            }
        }
    }
}
