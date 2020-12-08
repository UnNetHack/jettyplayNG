/*
 * This file was originally part of "JTA - Telnet/SSH for the JAVA(tm) platform".
 *
 * (c) Matthias L. Jugel, Marcus Meißner 1996-2005. All Rights Reserved.
 * Modified by Alex Smith, 2010.
 *
 * Please visit http://javatelnet.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * --LICENSE NOTICE--
 *
 */

package jettyplay;

/**
 * Implementation of a VT terminal emulation plus ANSI compatible.
 * <P>
 * <B>Maintainer:</B> Marcus Mei\u00dfner
 *
 * @version $Id: vt320.java 507 2005-10-25 10:14:52Z marcus $
 * @author  Matthias L. Jugel, Marcus Mei\u00dfner
 */
public class vt320 extends VDUBuffer implements Cloneable {

  /** the debug level */
  private final static int debugVT = 0;
  private final static int debugAutoResize = 0;

  /** whether to resize on terminal overflow */
  private boolean autoResize = false;
  private boolean vetoAutoResize = false;

    /**
     * The possible terminal encodings that can be detected from input to
     * the terminal.
     */
    public enum EncodingOverride {
        /**
         * Specifies that no encoding information has been detected from the
         * terminal input.
         */
        NONE,
        /**
         * Specifies that the terminal input indicates a non-Unicode character
         * set (and Latin-1 by default).
         */
        Latin1,
        /**
         * Specifies that the terminal input indicates that it's encoded in
         * UTF-8.
         */
        UTF8
    };
    private EncodingOverride characterEncodingOverride;

  /**
   * Play the beep sound ...
   */
  public void beep() { /* do nothing by default */
  }

  /**
   * Put string at current cursor position. Moves cursor
   * according to the String. Does NOT wrap.
   * @param s the string
   */
  public void putString(String s) {
    int len = s.length();
    // System.err.println("'"+s+"'");

    if (len > 0) {
      for (int i = 0; i < len; i++) {
        // System.err.print(s.charAt(i)+"("+(int)s.charAt(i)+")");
        putChar(s.charAt(i), false);
      }
      setCursorPosition(C, R);
      redraw();
    }
  }

  /** 
   * Sent the changed window size from the terminal to all listeners.
   * @param c The number of columns.
   * @param r The number of rows.
   */
  protected void setWindowSize(int c, int r) {
    /* To be overridden by Terminal.java */
  }

    @Override
    public void setScreenSize(int c, int r) {
        int oldrows = getRows(), oldcols = getColumns();
        if (oldrows == r && oldcols == c) {
            return;
        }

        if (debugVT > 2) {
            System.err.println("setscreensize (" + c + "," + r + ")");
        }

        super.setScreenSize(c, r);

        /* Tricky, since the VDUBuffer works strangely. */
        if (r > oldrows) {
            setCursorPosition(C, R + (r - oldrows));
            redraw();
        }
    }

    /**
     * Queries whether the input to the terminal suggests that it should be
     * automatically resized according to cursor movements.
     * @return True if the terminal is currently in a state where all cursor
     * movements would be expected to be inside the terminal bounds (e.g. if
     * the terminal seems to have been initialized by curses).
     */
    public boolean isAutoResize() {
        return autoResize;
    }

    /**
     * Tells the terminal whether to automatically resize based on cursor
     * movements. The provided value is ignored if the input provides a
     * sufficiently strong indication that auto-resizing should not happen
     * (e.g. if the terminal input explicitly specified a size).
     * @param autoResize Whether or not to resize based on cursor movements.
     */
    public void setAutoResize(boolean autoResize) {
        if (!vetoAutoResize) {
            this.autoResize = autoResize;
        }
    }

    /**
     * Returns whether the input provided a strong signal that automatic
     * resizing should never be used (e.g. explicitly specifying a size).
     * This property can also be set manually, e.g. to impose a fixed size on
     * the terminal independent of its input.
     * @return True if the terminal should never be auto-resized.
     * @see #setVetoAutoResize(boolean) 
     */
    public boolean isVetoAutoResize() {
        return vetoAutoResize;
    }

    /**
     * Changes whether the terminal should consider auto-resizing.
     * @param vetoAutoResize True if the terminal should never be auto-resized.
     * Setting this to false does not necessarily make the terminal resize,
     * unless autoResize is also set.
     * @see #setAutoResize(boolean) 
     */
    public void setVetoAutoResize(boolean vetoAutoResize) {
        this.vetoAutoResize = vetoAutoResize;
    }

    /**
     * Returns information about the encoding that has been inferred from the
     * input (and as such will override any information about the encoding that
     * might be supplied elsewhere).
     * @return An encoding override state. May be NONE if there is no
     * information about encoding in the terminal input.
     */
    public EncodingOverride getCharacterEncodingOverride() {
        return characterEncodingOverride;
    }

  /**
   * Create a new vt320 terminal and intialize it with useful settings.
   * @param width  The initial width of the terminal.
   * @param height The initial height of the terminal.
   */
  public vt320(int width, int height) {
    super(width, height);
    setVMS(false);
    setIBMCharset(false);
    setTerminalID("vt320");
    setBufferSize(0);
    //setBorder(2, false);

    int nw = getColumns();
    if (nw < 132) nw = 132; //catch possible later 132/80 resizes
    Tabs = new byte[nw];
    for (int i = 0; i < nw; i += 8) {
      Tabs[i] = 1;
    }

    vetoAutoResize = false;
    characterEncodingOverride = EncodingOverride.NONE;
  }

  /**
   * Create a default vt320 terminal with 80 columns and 24 lines.
   */
  public vt320() {
    this(80, 24);
  }

  /**
   * Enable the VMS mode of the terminal to handle some things differently
   * for VMS hosts.
   * @param vms true for vms mode, false for normal mode
   */
  public void setVMS(boolean vms) {
    this.vms = vms;
  }

  /**
   * Enable the usage of the IBM character set used by some BBS's. Special
   * graphical character are available in this mode.
   * @param ibm true to use the ibm character set
   */
  public void setIBMCharset(boolean ibm) {
    useibmcharset = ibm;
  }

  /**
   * Set the terminal id used to identify this terminal.
   * @param terminalID the id string
   */
  public void setTerminalID(String terminalID) {
    this.terminalID = terminalID;
  }

  public void setAnswerBack(String ab) {
    this.answerBack = unEscape(ab);
  }

  /**
   * Get the terminal id used to identify this terminal.
   * @return The terminal ID string
   */
  public String getTerminalID() {
    return terminalID;
  }

  // ===================================================================
  // the actual terminal emulation code comes here:
  // ===================================================================

  private String terminalID = "vt320";
  private String answerBack = "Use Terminal.answerback to set ...\n";

  // X - COLUMNS, Y - ROWS
  int R,C;
  short attributes = 0;

  int Sc,Sr,Stm,Sbm;
  short Sa;
  char Sgr,Sgl;
  char Sgx[];

  int insertmode = 0;
  int statusmode = 0;
  boolean vt52mode = false;
  boolean keypadmode = false; /* false - numeric, true - application */
  boolean output8bit = false;
  int normalcursor = 0;
  boolean moveoutsidemargins = true;
  boolean wraparound = true;
  boolean sendcrlf = true;
  boolean capslock = false;
  boolean numlock = false;
  int mouserpt = 0;
  byte mousebut = 0;

  boolean useibmcharset = false;

  int lastwaslf = 0;
  boolean usedcharsets = false;

  private final static char ESC = 27;
  private final static char IND = 132;
  private final static char NEL = 133;
  private final static char RI = 141;
  private final static char SS2 = 142;
  private final static char SS3 = 143;
  private final static char DCS = 144;
  private final static char HTS = 136;
  private final static char CSI = 155;
  private final static char OSC = 157;
  private final static int TSTATE_DATA = 0;
  private final static int TSTATE_ESC = 1; /* ESC */
  private final static int TSTATE_CSI = 2; /* ESC [ */
  private final static int TSTATE_DCS = 3; /* ESC P */
  private final static int TSTATE_DCEQ = 4; /* ESC [? */
  private final static int TSTATE_ESCSQUARE = 5; /* ESC # */
  private final static int TSTATE_OSC = 6;       /* ESC ] */
  private final static int TSTATE_SETG0 = 7;     /* ESC (? */
  private final static int TSTATE_SETG1 = 8;     /* ESC )? */
  private final static int TSTATE_SETG2 = 9;     /* ESC *? */
  private final static int TSTATE_SETG3 = 10;    /* ESC +? */
  private final static int TSTATE_CSI_DOLLAR = 11; /* ESC [ Pn $ */
  private final static int TSTATE_CSI_EX = 12; /* ESC [ ! */
  private final static int TSTATE_ESCSPACE = 13; /* ESC <space> */
  private final static int TSTATE_VT52X = 14;
  private final static int TSTATE_VT52Y = 15;
  private final static int TSTATE_CSI_TICKS = 16;
  private final static int TSTATE_CSI_EQUAL = 17; /* ESC [ = */
  private final static int TSTATE_ESCPERCENT = 18; /* ESC % */

  /* The graphics charsets
   * B - default ASCII
   * A - ISO Latin 1
   * 0 - DEC SPECIAL
   * < - User defined
   * ....
   */
  char gx[] = {// same initial set as in XTERM.
    'B', // g0
    '0', // g1
    'B', // g2
    'B', // g3
  };
  char gl = 0;		// default GL to G0
  char gr = 2;		// default GR to G2
  int onegl = -1;	// single shift override for GL.

  // Map from scoansi linedrawing to DEC _and_ unicode (for the stuff which
  // is not in linedrawing). Got from experimenting with scoadmin.
  private final static String scoansi_acs = "Tm7k3x4u?kZl@mYjEnB\u2566DqCtAvM\u2550:\u2551N\u2557I\u2554;\u2557H\u255a0a<\u255d";
  // array to store DEC Special -> Unicode mapping
  //  Unicode   DEC  Unicode name    (DEC name)
  private static char DECSPECIAL[] = {
    '\u0040', //5f blank
    '\u2666', //60 black diamond
    '\u2592', //61 grey square
    '\u2409', //62 Horizontal tab  (ht) pict. for control
    '\u240c', //63 Form Feed       (ff) pict. for control
    '\u240d', //64 Carriage Return (cr) pict. for control
    '\u240a', //65 Line Feed       (lf) pict. for control
    '\u00ba', //66 Masculine ordinal indicator
    '\u00b1', //67 Plus or minus sign
    '\u2424', //68 New Line        (nl) pict. for control
    '\u240b', //69 Vertical Tab    (vt) pict. for control
    '\u2518', //6a Forms light up   and left
    '\u2510', //6b Forms light down and left
    '\u250c', //6c Forms light down and right
    '\u2514', //6d Forms light up   and right
    '\u253c', //6e Forms light vertical and horizontal
    '\u2594', //6f Upper 1/8 block                        (Scan 1)
    '\u2580', //70 Upper 1/2 block                        (Scan 3)
    '\u2500', //71 Forms light horizontal or ?em dash?    (Scan 5)
    '\u25ac', //72 \u25ac black rect. or \u2582 lower 1/4 (Scan 7)
    '\u005f', //73 \u005f underscore  or \u2581 lower 1/8 (Scan 9)
    '\u251c', //74 Forms light vertical and right
    '\u2524', //75 Forms light vertical and left
    '\u2534', //76 Forms light up   and horizontal
    '\u252c', //77 Forms light down and horizontal
    '\u2502', //78 vertical bar
    '\u2264', //79 less than or equal
    '\u2265', //7a greater than or equal
    '\u00b6', //7b paragraph
    '\u2260', //7c not equal
    '\u00a3', //7d Pound Sign (british)
    '\u00b7'  //7e Middle Dot
  };

  private String osc,dcs;  /* to memorize OSC & DCS control sequence */

  /** vt320 state variable (internal) */
  private int term_state = TSTATE_DATA;
  /** in vms mode, set by Terminal.VMS property */
  private boolean vms = false;
  /** Tabulators */
  private byte[] Tabs;
  /** The list of integers as used by CSI */
  private int[] DCEvars = new int[30];
  private int DCEvar;

  /**
   * Replace escape code characters (backslash + identifier) with their
   * respective codes.
   * @param tmp the string to be parsed
   * @return a unescaped string
   */
  static String unEscape(String tmp) {
    int idx = 0, oldidx = 0;
    String cmd;
    // System.err.println("unescape("+tmp+")");
    cmd = "";
    while ((idx = tmp.indexOf('\\', oldidx)) >= 0 &&
            ++idx <= tmp.length()) {
      cmd += tmp.substring(oldidx, idx - 1);
      if (idx == tmp.length()) return cmd;
      switch (tmp.charAt(idx)) {
        case 'b':
          cmd += "\b";
          break;
        case 'e':
          cmd += "\u001b";
          break;
        case 'n':
          cmd += "\n";
          break;
        case 'r':
          cmd += "\r";
          break;
        case 't':
          cmd += "\t";
          break;
        case 'v':
          cmd += "\u000b";
          break;
        case 'a':
          cmd += "\u0012";
          break;
        default :
          if ((tmp.charAt(idx) >= '0') && (tmp.charAt(idx) <= '9')) {
            int i;
            for (i = idx; i < tmp.length(); i++)
              if ((tmp.charAt(i) < '0') || (tmp.charAt(i) > '9'))
                break;
            cmd += (char) Integer.parseInt(tmp.substring(idx, i));
            idx = i - 1;
          } else
            cmd += tmp.substring(idx, ++idx);
          break;
      }
      oldidx = ++idx;
    }
    if (oldidx <= tmp.length()) cmd += tmp.substring(oldidx);
    return cmd;
  }

  private final static char unimap[] = {
    //#
    //#    Name:     cp437_DOSLatinUS to Unicode table
    //#    Unicode version: 1.1
    //#    Table version: 1.1
    //#    Table format:  Format A
    //#    Date:          03/31/95
    //#    Authors:       Michel Suignard <michelsu@microsoft.com>
    //#                   Lori Hoerth <lorih@microsoft.com>
    //#    General notes: none
    //#
    //#    Format: Three tab-separated columns
    //#        Column #1 is the cp1255_WinHebrew code (in hex)
    //#        Column #2 is the Unicode (in hex as 0xXXXX)
    //#        Column #3 is the Unicode name (follows a comment sign, '#')
    //#
    //#    The entries are in cp437_DOSLatinUS order
    //#

    0x0000, // #NULL
    0x0001, // #START OF HEADING
    0x0002, // #START OF TEXT
    0x0003, // #END OF TEXT
    0x0004, // #END OF TRANSMISSION
    0x0005, // #ENQUIRY
    0x0006, // #ACKNOWLEDGE
    0x0007, // #BELL
    0x0008, // #BACKSPACE
    0x0009, // #HORIZONTAL TABULATION
    0x000a, // #LINE FEED
    0x000b, // #VERTICAL TABULATION
    0x000c, // #FORM FEED
    0x000d, // #CARRIAGE RETURN
    0x000e, // #SHIFT OUT
    0x000f, // #SHIFT IN
    0x0010, // #DATA LINK ESCAPE
    0x0011, // #DEVICE CONTROL ONE
    0x0012, // #DEVICE CONTROL TWO
    0x0013, // #DEVICE CONTROL THREE
    0x0014, // #DEVICE CONTROL FOUR
    0x0015, // #NEGATIVE ACKNOWLEDGE
    0x0016, // #SYNCHRONOUS IDLE
    0x0017, // #END OF TRANSMISSION BLOCK
    0x0018, // #CANCEL
    0x0019, // #END OF MEDIUM
    0x001a, // #SUBSTITUTE
    0x001b, // #ESCAPE
    0x001c, // #FILE SEPARATOR
    0x001d, // #GROUP SEPARATOR
    0x001e, // #RECORD SEPARATOR
    0x001f, // #UNIT SEPARATOR
    0x0020, // #SPACE
    0x0021, // #EXCLAMATION MARK
    0x0022, // #QUOTATION MARK
    0x0023, // #NUMBER SIGN
    0x0024, // #DOLLAR SIGN
    0x0025, // #PERCENT SIGN
    0x0026, // #AMPERSAND
    0x0027, // #APOSTROPHE
    0x0028, // #LEFT PARENTHESIS
    0x0029, // #RIGHT PARENTHESIS
    0x002a, // #ASTERISK
    0x002b, // #PLUS SIGN
    0x002c, // #COMMA
    0x002d, // #HYPHEN-MINUS
    0x002e, // #FULL STOP
    0x002f, // #SOLIDUS
    0x0030, // #DIGIT ZERO
    0x0031, // #DIGIT ONE
    0x0032, // #DIGIT TWO
    0x0033, // #DIGIT THREE
    0x0034, // #DIGIT FOUR
    0x0035, // #DIGIT FIVE
    0x0036, // #DIGIT SIX
    0x0037, // #DIGIT SEVEN
    0x0038, // #DIGIT EIGHT
    0x0039, // #DIGIT NINE
    0x003a, // #COLON
    0x003b, // #SEMICOLON
    0x003c, // #LESS-THAN SIGN
    0x003d, // #EQUALS SIGN
    0x003e, // #GREATER-THAN SIGN
    0x003f, // #QUESTION MARK
    0x0040, // #COMMERCIAL AT
    0x0041, // #LATIN CAPITAL LETTER A
    0x0042, // #LATIN CAPITAL LETTER B
    0x0043, // #LATIN CAPITAL LETTER C
    0x0044, // #LATIN CAPITAL LETTER D
    0x0045, // #LATIN CAPITAL LETTER E
    0x0046, // #LATIN CAPITAL LETTER F
    0x0047, // #LATIN CAPITAL LETTER G
    0x0048, // #LATIN CAPITAL LETTER H
    0x0049, // #LATIN CAPITAL LETTER I
    0x004a, // #LATIN CAPITAL LETTER J
    0x004b, // #LATIN CAPITAL LETTER K
    0x004c, // #LATIN CAPITAL LETTER L
    0x004d, // #LATIN CAPITAL LETTER M
    0x004e, // #LATIN CAPITAL LETTER N
    0x004f, // #LATIN CAPITAL LETTER O
    0x0050, // #LATIN CAPITAL LETTER P
    0x0051, // #LATIN CAPITAL LETTER Q
    0x0052, // #LATIN CAPITAL LETTER R
    0x0053, // #LATIN CAPITAL LETTER S
    0x0054, // #LATIN CAPITAL LETTER T
    0x0055, // #LATIN CAPITAL LETTER U
    0x0056, // #LATIN CAPITAL LETTER V
    0x0057, // #LATIN CAPITAL LETTER W
    0x0058, // #LATIN CAPITAL LETTER X
    0x0059, // #LATIN CAPITAL LETTER Y
    0x005a, // #LATIN CAPITAL LETTER Z
    0x005b, // #LEFT SQUARE BRACKET
    0x005c, // #REVERSE SOLIDUS
    0x005d, // #RIGHT SQUARE BRACKET
    0x005e, // #CIRCUMFLEX ACCENT
    0x005f, // #LOW LINE
    0x0060, // #GRAVE ACCENT
    0x0061, // #LATIN SMALL LETTER A
    0x0062, // #LATIN SMALL LETTER B
    0x0063, // #LATIN SMALL LETTER C
    0x0064, // #LATIN SMALL LETTER D
    0x0065, // #LATIN SMALL LETTER E
    0x0066, // #LATIN SMALL LETTER F
    0x0067, // #LATIN SMALL LETTER G
    0x0068, // #LATIN SMALL LETTER H
    0x0069, // #LATIN SMALL LETTER I
    0x006a, // #LATIN SMALL LETTER J
    0x006b, // #LATIN SMALL LETTER K
    0x006c, // #LATIN SMALL LETTER L
    0x006d, // #LATIN SMALL LETTER M
    0x006e, // #LATIN SMALL LETTER N
    0x006f, // #LATIN SMALL LETTER O
    0x0070, // #LATIN SMALL LETTER P
    0x0071, // #LATIN SMALL LETTER Q
    0x0072, // #LATIN SMALL LETTER R
    0x0073, // #LATIN SMALL LETTER S
    0x0074, // #LATIN SMALL LETTER T
    0x0075, // #LATIN SMALL LETTER U
    0x0076, // #LATIN SMALL LETTER V
    0x0077, // #LATIN SMALL LETTER W
    0x0078, // #LATIN SMALL LETTER X
    0x0079, // #LATIN SMALL LETTER Y
    0x007a, // #LATIN SMALL LETTER Z
    0x007b, // #LEFT CURLY BRACKET
    0x007c, // #VERTICAL LINE
    0x007d, // #RIGHT CURLY BRACKET
    0x007e, // #TILDE
    0x007f, // #DELETE
    0x00c7, // #LATIN CAPITAL LETTER C WITH CEDILLA
    0x00fc, // #LATIN SMALL LETTER U WITH DIAERESIS
    0x00e9, // #LATIN SMALL LETTER E WITH ACUTE
    0x00e2, // #LATIN SMALL LETTER A WITH CIRCUMFLEX
    0x00e4, // #LATIN SMALL LETTER A WITH DIAERESIS
    0x00e0, // #LATIN SMALL LETTER A WITH GRAVE
    0x00e5, // #LATIN SMALL LETTER A WITH RING ABOVE
    0x00e7, // #LATIN SMALL LETTER C WITH CEDILLA
    0x00ea, // #LATIN SMALL LETTER E WITH CIRCUMFLEX
    0x00eb, // #LATIN SMALL LETTER E WITH DIAERESIS
    0x00e8, // #LATIN SMALL LETTER E WITH GRAVE
    0x00ef, // #LATIN SMALL LETTER I WITH DIAERESIS
    0x00ee, // #LATIN SMALL LETTER I WITH CIRCUMFLEX
    0x00ec, // #LATIN SMALL LETTER I WITH GRAVE
    0x00c4, // #LATIN CAPITAL LETTER A WITH DIAERESIS
    0x00c5, // #LATIN CAPITAL LETTER A WITH RING ABOVE
    0x00c9, // #LATIN CAPITAL LETTER E WITH ACUTE
    0x00e6, // #LATIN SMALL LIGATURE AE
    0x00c6, // #LATIN CAPITAL LIGATURE AE
    0x00f4, // #LATIN SMALL LETTER O WITH CIRCUMFLEX
    0x00f6, // #LATIN SMALL LETTER O WITH DIAERESIS
    0x00f2, // #LATIN SMALL LETTER O WITH GRAVE
    0x00fb, // #LATIN SMALL LETTER U WITH CIRCUMFLEX
    0x00f9, // #LATIN SMALL LETTER U WITH GRAVE
    0x00ff, // #LATIN SMALL LETTER Y WITH DIAERESIS
    0x00d6, // #LATIN CAPITAL LETTER O WITH DIAERESIS
    0x00dc, // #LATIN CAPITAL LETTER U WITH DIAERESIS
    0x00a2, // #CENT SIGN
    0x00a3, // #POUND SIGN
    0x00a5, // #YEN SIGN
    0x20a7, // #PESETA SIGN
    0x0192, // #LATIN SMALL LETTER F WITH HOOK
    0x00e1, // #LATIN SMALL LETTER A WITH ACUTE
    0x00ed, // #LATIN SMALL LETTER I WITH ACUTE
    0x00f3, // #LATIN SMALL LETTER O WITH ACUTE
    0x00fa, // #LATIN SMALL LETTER U WITH ACUTE
    0x00f1, // #LATIN SMALL LETTER N WITH TILDE
    0x00d1, // #LATIN CAPITAL LETTER N WITH TILDE
    0x00aa, // #FEMININE ORDINAL INDICATOR
    0x00ba, // #MASCULINE ORDINAL INDICATOR
    0x00bf, // #INVERTED QUESTION MARK
    0x2310, // #REVERSED NOT SIGN
    0x00ac, // #NOT SIGN
    0x00bd, // #VULGAR FRACTION ONE HALF
    0x00bc, // #VULGAR FRACTION ONE QUARTER
    0x00a1, // #INVERTED EXCLAMATION MARK
    0x00ab, // #LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
    0x00bb, // #RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
    0x2591, // #LIGHT SHADE
    0x2592, // #MEDIUM SHADE
    0x2593, // #DARK SHADE
    0x2502, // #BOX DRAWINGS LIGHT VERTICAL
    0x2524, // #BOX DRAWINGS LIGHT VERTICAL AND LEFT
    0x2561, // #BOX DRAWINGS VERTICAL SINGLE AND LEFT DOUBLE
    0x2562, // #BOX DRAWINGS VERTICAL DOUBLE AND LEFT SINGLE
    0x2556, // #BOX DRAWINGS DOWN DOUBLE AND LEFT SINGLE
    0x2555, // #BOX DRAWINGS DOWN SINGLE AND LEFT DOUBLE
    0x2563, // #BOX DRAWINGS DOUBLE VERTICAL AND LEFT
    0x2551, // #BOX DRAWINGS DOUBLE VERTICAL
    0x2557, // #BOX DRAWINGS DOUBLE DOWN AND LEFT
    0x255d, // #BOX DRAWINGS DOUBLE UP AND LEFT
    0x255c, // #BOX DRAWINGS UP DOUBLE AND LEFT SINGLE
    0x255b, // #BOX DRAWINGS UP SINGLE AND LEFT DOUBLE
    0x2510, // #BOX DRAWINGS LIGHT DOWN AND LEFT
    0x2514, // #BOX DRAWINGS LIGHT UP AND RIGHT
    0x2534, // #BOX DRAWINGS LIGHT UP AND HORIZONTAL
    0x252c, // #BOX DRAWINGS LIGHT DOWN AND HORIZONTAL
    0x251c, // #BOX DRAWINGS LIGHT VERTICAL AND RIGHT
    0x2500, // #BOX DRAWINGS LIGHT HORIZONTAL
    0x253c, // #BOX DRAWINGS LIGHT VERTICAL AND HORIZONTAL
    0x255e, // #BOX DRAWINGS VERTICAL SINGLE AND RIGHT DOUBLE
    0x255f, // #BOX DRAWINGS VERTICAL DOUBLE AND RIGHT SINGLE
    0x255a, // #BOX DRAWINGS DOUBLE UP AND RIGHT
    0x2554, // #BOX DRAWINGS DOUBLE DOWN AND RIGHT
    0x2569, // #BOX DRAWINGS DOUBLE UP AND HORIZONTAL
    0x2566, // #BOX DRAWINGS DOUBLE DOWN AND HORIZONTAL
    0x2560, // #BOX DRAWINGS DOUBLE VERTICAL AND RIGHT
    0x2550, // #BOX DRAWINGS DOUBLE HORIZONTAL
    0x256c, // #BOX DRAWINGS DOUBLE VERTICAL AND HORIZONTAL
    0x2567, // #BOX DRAWINGS UP SINGLE AND HORIZONTAL DOUBLE
    0x2568, // #BOX DRAWINGS UP DOUBLE AND HORIZONTAL SINGLE
    0x2564, // #BOX DRAWINGS DOWN SINGLE AND HORIZONTAL DOUBLE
    0x2565, // #BOX DRAWINGS DOWN DOUBLE AND HORIZONTAL SINGLE
    0x2559, // #BOX DRAWINGS UP DOUBLE AND RIGHT SINGLE
    0x2558, // #BOX DRAWINGS UP SINGLE AND RIGHT DOUBLE
    0x2552, // #BOX DRAWINGS DOWN SINGLE AND RIGHT DOUBLE
    0x2553, // #BOX DRAWINGS DOWN DOUBLE AND RIGHT SINGLE
    0x256b, // #BOX DRAWINGS VERTICAL DOUBLE AND HORIZONTAL SINGLE
    0x256a, // #BOX DRAWINGS VERTICAL SINGLE AND HORIZONTAL DOUBLE
    0x2518, // #BOX DRAWINGS LIGHT UP AND LEFT
    0x250c, // #BOX DRAWINGS LIGHT DOWN AND RIGHT
    0x2588, // #FULL BLOCK
    0x2584, // #LOWER HALF BLOCK
    0x258c, // #LEFT HALF BLOCK
    0x2590, // #RIGHT HALF BLOCK
    0x2580, // #UPPER HALF BLOCK
    0x03b1, // #GREEK SMALL LETTER ALPHA
    0x00df, // #LATIN SMALL LETTER SHARP S
    0x0393, // #GREEK CAPITAL LETTER GAMMA
    0x03c0, // #GREEK SMALL LETTER PI
    0x03a3, // #GREEK CAPITAL LETTER SIGMA
    0x03c3, // #GREEK SMALL LETTER SIGMA
    0x00b5, // #MICRO SIGN
    0x03c4, // #GREEK SMALL LETTER TAU
    0x03a6, // #GREEK CAPITAL LETTER PHI
    0x0398, // #GREEK CAPITAL LETTER THETA
    0x03a9, // #GREEK CAPITAL LETTER OMEGA
    0x03b4, // #GREEK SMALL LETTER DELTA
    0x221e, // #INFINITY
    0x03c6, // #GREEK SMALL LETTER PHI
    0x03b5, // #GREEK SMALL LETTER EPSILON
    0x2229, // #INTERSECTION
    0x2261, // #IDENTICAL TO
    0x00b1, // #PLUS-MINUS SIGN
    0x2265, // #GREATER-THAN OR EQUAL TO
    0x2264, // #LESS-THAN OR EQUAL TO
    0x2320, // #TOP HALF INTEGRAL
    0x2321, // #BOTTOM HALF INTEGRAL
    0x00f7, // #DIVISION SIGN
    0x2248, // #ALMOST EQUAL TO
    0x00b0, // #DEGREE SIGN
    0x2219, // #BULLET OPERATOR
    0x00b7, // #MIDDLE DOT
    0x221a, // #SQUARE ROOT
    0x207f, // #SUPERSCRIPT LATIN SMALL LETTER N
    0x00b2, // #SUPERSCRIPT TWO
    0x25a0, // #BLACK SQUARE
    0x00a0, // #NO-BREAK SPACE
  };

  /**
   * Translates characters from code page 850 to Unicode.
   * @param x A character. This is interpreted as code page 850 if it's in the
   * range 0 to 0x100, and as Unicode otherwise.
   * @return The unicode equivalent of that character.
   */
  public char map_cp850_unicode(char x) {
    if (x >= 0x100)
      return x;
    return unimap[x];
  }

  private void _SetCursor(int row, int col) {
    int maxr = getRows();
    int tm = getTopMargin();

    R = (row < 0)?0:row;
    C = (col < 0)?0:col;

    if (!moveoutsidemargins) {
      R += tm;
      maxr = getBottomMargin();
    }
    if (R > maxr && (!autoResize || !moveoutsidemargins)) R = maxr;
  }

  private void putChar(char c, boolean doshowcursor) {
    int rows = getRows(); //statusline
    int columns = getColumns();
    int tm = getTopMargin();
    int bm = getBottomMargin();
    // byte msg[];
    boolean mapped = false;
    boolean wrappedOnThisCharacter = false;

    if (debugVT > 4) System.out.println("putChar(" + c + " [" + ((int) c) + "]) at R=" + R + " , C=" + C + ", columns=" + columns + ", rows=" + rows);
    if (c > 255) {
      if (debugVT > 0)
        System.out.println("char > 255:" + (int) c);
      //return;
    }


    switch (term_state) {
      case TSTATE_DATA:
        /* FIXME: we shouldn't use chars with bit 8 set if ibmcharset.
         * probably... but some BBS do anyway...
         */
        if (!useibmcharset) {
          boolean doneflag = true;
          switch (c) {
            case OSC:
              osc = "";
              term_state = TSTATE_OSC;
              break;
            case RI:
              if (R > tm)
                R--;
              else
                insertLine(R, 1, SCROLL_DOWN);
              if (debugVT > 1)
                System.out.println("RI");
              break;
            case IND:
              if (debugVT > 2)
                System.out.println("IND at " + R + ", tm is " + tm + ", bm is " + bm);
              if (R == bm || R == rows - 1)
                insertLine(R, 1, SCROLL_UP);
              else
                R++;
              if (debugVT > 1)
                System.out.println("IND (at " + R + " )");
              break;
            case NEL:
              if (R == bm || R == rows - 1)
                insertLine(R, 1, SCROLL_UP);
              else
                R++;
              C = 0;
              if (debugVT > 1)
                System.out.println("NEL (at " + R + " )");
              break;
            case HTS:
              Tabs[C] = 1;
              if (debugVT > 1)
                System.out.println("HTS");
              break;
            case DCS:
              dcs = "";
              term_state = TSTATE_DCS;
              break;
            default:
              doneflag = false;
              break;
          }
          if (doneflag) break;
        }
        switch (c) {
          case SS3:
            onegl = 3;
            break;
          case SS2:
            onegl = 2;
            break;
          case CSI: // should be in the 8bit section, but some BBS use this
            DCEvar = 0;
            DCEvars[0] = 0;
            DCEvars[1] = 0;
            DCEvars[2] = 0;
            DCEvars[3] = 0;
            term_state = TSTATE_CSI;
            break;
          case ESC:
            term_state = TSTATE_ESC;
            lastwaslf = 0;
            break;
          case 5: /* ENQ */
            //write(answerBack, false); // don't reply to a recording
            break;
          case 12:
            /* FormFeed, Home for the BBS world */
            deleteArea(0, 0, columns, rows, attributes);
            C = R = 0;
            break;
          case '\b': /* 8 */
            C--;
            if (C < 0)
              C = 0;
            lastwaslf = 0;
            break;
          case '\t':
            do {
              // Don't overwrite or insert! TABS are not destructive, but movement!
              C++;
            } while (C < columns && (Tabs[C] == 0));
            lastwaslf = 0;
            break;
          case '\r':
            C = 0;
            break;
          case '\n':
            if (debugVT > 3)
              System.out.println("R= " + R + ", bm " + bm + ", tm=" + tm + ", rows=" + rows);
            if (!vms) {
              if (lastwaslf != 0 && lastwaslf != c)   //  Ray: I do not understand this logic.
                break;
              lastwaslf = c;
              /*C = 0;*/
            }
            if (R == bm || R >= rows - 1)
              insertLine(R, 1, SCROLL_UP);
            else
              R++;
            break;
          case 7:
            beep();
            break;
          case '\016': /* SMACS , as */
            /* ^N, Shift out - Put G1 into GL */
            gl = 1;
            usedcharsets = true;
            break;
          case '\017': /* RMACS , ae */
            /* ^O, Shift in - Put G0 into GL */
            gl = 0;
            usedcharsets = true;
            break;
          default:
            {
              int thisgl = gl;

              if (onegl >= 0) {
                thisgl = onegl;
                onegl = -1;
              }
              lastwaslf = 0;
              if (c < 32) {
                if (c != 0)
                  if (debugVT > 0)
                    System.out.println("TSTATE_DATA char: " + ((int) c));
                /*break; some BBS really want those characters, like hearst etc. */
                if (c == 0) /* print 0 ... you bet */
                  break;
              }
              if (C >= columns) {
                if (autoResize) {
                    if (debugAutoResize > 0) System.out.println("Making window wider (cursor movement after output)");
                    this.setScreenSize(C+1, rows);
                    columns = C + 1;
                } else if (wraparound) {
                  if (R < rows - 1)
                    R++;
                  else
                    insertLine(R, 1, SCROLL_UP);
                  C = 0;
                } else {
                  // cursor stays on last character.
                  C = columns - 1;
                }
              } else if (C == columns - 1)
                wrappedOnThisCharacter = true;

              // Mapping if DEC Special is chosen charset
              if (usedcharsets) {
                if (c >= '\u0020' && c <= '\u007f') {
                  switch (gx[thisgl]) {
                    case '0':
                      // Remap SCOANSI line drawing to VT100 line drawing chars
                      // for our SCO using customers.
                      if (terminalID.equals("scoansi") || terminalID.equals("ansi")) {
                        for (int i = 0; i < scoansi_acs.length(); i += 2) {
                          if (c == scoansi_acs.charAt(i)) {
                            c = scoansi_acs.charAt(i + 1);
                            break;
                          }
                        }
                      }
                      if (c >= '\u005f' && c <= '\u007e') {
                        c = DECSPECIAL[(short) c - 0x5f];
                        mapped = true;
                      }
                      break;
                    case '<': // 'user preferred' is currently 'ISO Latin-1 suppl
                      c = (char) (((int) c & 0x7f) | 0x80);
                      mapped = true;
                      break;
                    case 'A':
                    case 'B': // Latin-1 , ASCII -> fall through
                      mapped = true;
                      break;
                    default:
                      System.out.println("Unsupported GL mapping: " + gx[thisgl]);
                      break;
                  }
                }
                if (!mapped && (c >= '\u0080' && c <= '\u00ff')) {
                  switch (gx[gr]) {
                    case '0':
                      if (c >= '\u00df' && c <= '\u00fe') {
                        c = DECSPECIAL[c - '\u00df'];
                        mapped = true;
                      }
                      break;
                    case '<':
                    case 'A':
                    case 'B':
                      //mapped = true;
                      break;
                    default:
                      System.out.println("Unsupported GR mapping: " + gx[gr]);
                      break;
                  }
                }
              }
              if (!mapped && useibmcharset)
                c = map_cp850_unicode(c);

              /*if(true || (statusmode == 0)) { */
              if (insertmode == 1) {
                insertChar(C, R, c, attributes);
              } else {
                putChar(C, R, c, attributes);
              }
              /*
                } else {
                if (insertmode==1) {
                insertChar(C, rows, c, attributes);
                } else {
                putChar(C, rows, c, attributes);
                }
                }
              */
              C++;
              break;
            }
        } /* switch(c) */
        break;
      case TSTATE_OSC:
        if ((c < 0x20) && (c != ESC)) {// NP - No printing character
          term_state = TSTATE_DATA;
          break;
        }
        //but check for vt102 ESC \
        if (c == '\\' && osc.charAt(osc.length() - 1) == ESC) {
          term_state = TSTATE_DATA;
          break;
        }
        osc += c;
        break;
      case TSTATE_ESCPERCENT:
        term_state = TSTATE_DATA;
        switch (c) {
            case '@':
                characterEncodingOverride = EncodingOverride.Latin1; break;
            case '8': case 'G':
                characterEncodingOverride = EncodingOverride.UTF8; break;
            default:
                System.out.println("ESC % " + c + "unhandled.");
        }
        break;
      case TSTATE_ESCSPACE:
        term_state = TSTATE_DATA;
        switch (c) {
          case 'F': /* S7C1T, Disable output of 8-bit controls, use 7-bit */
            output8bit = false;
            break;
          case 'G': /* S8C1T, Enable output of 8-bit control codes*/
            output8bit = true;
            break;
          default:
            System.out.println("ESC <space> " + c + " unhandled.");
        }
        break;
      case TSTATE_ESC:
        term_state = TSTATE_DATA;
        switch (c) {
          case ' ':
            term_state = TSTATE_ESCSPACE;
            break;
          case '#':
            term_state = TSTATE_ESCSQUARE;
            break;
          case 'c':
            /* Hard terminal reset */
            /* reset character sets */
            gx[0] = 'B';
            gx[1] = '0';
            gx[2] = 'B';
            gx[3] = 'B';
            gl = 0;  // default GL to G0
            gr = 1;  // default GR to G1
            /* reset tabs */
            int nw = getColumns();
            if (nw < 132) nw = 132;
            Tabs = new byte[nw];
            for (int i = 0; i < nw; i += 8) {
              Tabs[i] = 1;
            }
            /*FIXME:*/
            break;
          case '[':
            DCEvar = 0;
            DCEvars[0] = 0;
            DCEvars[1] = 0;
            DCEvars[2] = 0;
            DCEvars[3] = 0;
            term_state = TSTATE_CSI;
            break;
          case ']':
            osc = "";
            term_state = TSTATE_OSC;
            break;
          case 'P':
            dcs = "";
            term_state = TSTATE_DCS;
            break;
          case 'A': /* CUU */
            R--;
            if (R < 0) R = 0;
            break;
          case 'B': /* CUD */
            R++;
            if (R > rows - 1) R = rows - 1;
            break;
          case 'C':
            C++;
            if (C >= columns) C = columns - 1;
            break;
          case 'I': // RI
            insertLine(R, 1, SCROLL_DOWN);
            break;
          case 'E': /* NEL */
            if (R == bm || R == rows - 1)
              insertLine(R, 1, SCROLL_UP);
            else
              R++;
            C = 0;
            if (debugVT > 1)
              System.out.println("ESC E (at " + R + ")");
            break;
          case 'D': /* IND */
            if (R == bm || R == rows - 1)
              insertLine(R, 1, SCROLL_UP);
            else
              R++;
            if (debugVT > 1)
              System.out.println("ESC D (at " + R + " )");
            break;
          case 'J': /* erase to end of screen */
            if (R < rows - 1)
              deleteArea(0, R + 1, columns, rows - R - 1, attributes);
            if (C < columns - 1)
              deleteArea(C, R, columns - C, 1, attributes);
            break;
          case 'K':
            if (C < columns - 1)
              deleteArea(C, R, columns - C, 1, attributes);
            break;
          case 'M': // RI
            if (debugVT > 0)
              System.out.println("ESC M : R is "+R+", tm is "+tm+", bm is "+bm);
            if (R > bm) // outside scrolling region
              break;
            if (R > tm) { // just go up 1 line.
              R--;
            } else { // scroll down
              insertLine(R, 1, SCROLL_DOWN);
            }
            /* else do nothing ; */
            if (debugVT > 2)
              System.out.println("ESC M ");
            break;
          case 'H':
            if (debugVT > 1)
              System.out.println("ESC H at " + C);
            /* right border probably ...*/
            if (C >= columns)
              C = columns - 1;
            Tabs[C] = 1;
            break;
          case 'N': // SS2
            onegl = 2;
            break;
          case 'O': // SS3
            onegl = 3;
            break;
          case '=':
            /*application keypad*/
            if (debugVT > 0)
              System.out.println("ESC =");
            keypadmode = true;
            break;
          case '<': /* vt52 mode off */
            vt52mode = false;
            break;
          case '>': /*normal keypad*/
            if (debugVT > 0)
              System.out.println("ESC >");
            keypadmode = false;
            break;
          case '7': /*save cursor, attributes, margins */
/*            Sc = C;
            Sr = R;
            Sgl = gl;
            Sgr = gr;
            Sa = attributes;
            Sgx = new char[4];
            for (int i = 0; i < 4; i++) Sgx[i] = gx[i];
            Stm = getTopMargin();
            Sbm = getBottomMargin();
            if (debugVT > 1)
              System.out.println("ESC 7");
            break;*/
            break;
          case '8': /*restore cursor, attributes, margins */
/*            C = Sc;
            R = Sr;
            gl = Sgl;
            gr = Sgr;
            for (int i = 0; i < 4; i++) gx[i] = Sgx[i];
            setTopMargin(Stm);
            setBottomMargin(Sbm);
            attributes = Sa;
            if (debugVT > 1)
              System.out.println("ESC 8");
            break;*/
            break;
          case '(': /* Designate G0 Character set (ISO 2022) */
            term_state = TSTATE_SETG0;
            usedcharsets = true;
            break;
          case ')': /* Designate G1 character set (ISO 2022) */
            term_state = TSTATE_SETG1;
            usedcharsets = true;
            break;
          case '*': /* Designate G2 Character set (ISO 2022) */
            term_state = TSTATE_SETG2;
            usedcharsets = true;
            break;
          case '+': /* Designate G3 Character set (ISO 2022) */
            term_state = TSTATE_SETG3;
            usedcharsets = true;
            break;
          case '%': /* specify character encoding */
            term_state = TSTATE_ESCPERCENT;
            break;
          case '~': /* Locking Shift 1, right */
            gr = 1;
            usedcharsets = true;
            break;
          case 'n': /* Locking Shift 2 */
            gl = 2;
            usedcharsets = true;
            break;
          case '}': /* Locking Shift 2, right */
            gr = 2;
            usedcharsets = true;
            break;
          case 'o': /* Locking Shift 3 */
            gl = 3;
            usedcharsets = true;
            break;
          case '|': /* Locking Shift 3, right */
            gr = 3;
            usedcharsets = true;
            break;
          case 'Y': /* vt52 cursor address mode , next chars are x,y */
            term_state = TSTATE_VT52Y;
            break;
          default:
            System.out.println("ESC unknown letter: " + c + " (" + ((int) c) + ")");
            break;
        }
        break;
      case TSTATE_VT52X:
        C = c - 37;
        term_state = TSTATE_VT52Y;
        break;
      case TSTATE_VT52Y:
        R = c - 37;
        term_state = TSTATE_DATA;
        break;
      case TSTATE_SETG0:
        if (c != '0' && c != 'A' && c != 'B' && c != '<')
          System.out.println("ESC ( " + c + ": G0 char set?  (" + ((int) c) + ")");
        else {
          if (debugVT > 2) System.out.println("ESC ( : G0 char set  (" + c + " " + ((int) c) + ")");
          gx[0] = c;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_SETG1:
        if (c != '0' && c != 'A' && c != 'B' && c != '<') {
          System.out.println("ESC ) " + c + " (" + ((int) c) + ") :G1 char set?");
        } else {
          if (debugVT > 2) System.out.println("ESC ) :G1 char set  (" + c + " " + ((int) c) + ")");
          gx[1] = c;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_SETG2:
        if (c != '0' && c != 'A' && c != 'B' && c != '<')
          System.out.println("ESC*:G2 char set?  (" + ((int) c) + ")");
        else {
          if (debugVT > 2) System.out.println("ESC*:G2 char set  (" + c + " " + ((int) c) + ")");
          gx[2] = c;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_SETG3:
        if (c != '0' && c != 'A' && c != 'B' && c != '<')
          System.out.println("ESC+:G3 char set?  (" + ((int) c) + ")");
        else {
          if (debugVT > 2) System.out.println("ESC+:G3 char set  (" + c + " " + ((int) c) + ")");
          gx[3] = c;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_ESCSQUARE:
        switch (c) {
          case '8':
            for (int i = 0; i < columns; i++)
              for (int j = 0; j < rows; j++)
                putChar(i, j, 'E', (short)0);
            break;
          default:
            System.out.println("ESC # " + c + " not supported.");
            break;
        }
        term_state = TSTATE_DATA;
        break;
      case TSTATE_DCS:
        if (c == '\\' && dcs.charAt(dcs.length() - 1) == ESC) {
          term_state = TSTATE_DATA;
          break;
        }
        dcs += c;
        break;

      case TSTATE_DCEQ:
        term_state = TSTATE_DATA;
        switch (c) {
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            DCEvars[DCEvar] = DCEvars[DCEvar] * 10 + ((int) c) - 48;
            term_state = TSTATE_DCEQ;
            break;
          case ';':
            DCEvar++;
            DCEvars[DCEvar] = 0;
            term_state = TSTATE_DCEQ;
            break;
          case 's': // XTERM_SAVE missing!
            if (true || debugVT > 1)
              System.out.println("ESC [ ? " + DCEvars[0] + " s unimplemented!");
            break;
          case 'r': // XTERM_RESTORE
            if (true || debugVT > 1)
              System.out.println("ESC [ ? " + DCEvars[0] + " r");
            /* DEC Mode reset */
            for (int i = 0; i <= DCEvar; i++) {
              switch (DCEvars[i]) {
                case 3: /* 80 columns*/
                  setScreenSize(80, getRows());
                  break;
                case 4: /* scrolling mode, smooth */
                  break;
                case 5: /* light background */
                  break;
                case 6: /* DECOM (Origin Mode) move inside margins. */
                  moveoutsidemargins = true;
                  break;
                case 7: /* DECAWM: Autowrap Mode */
                  wraparound = false;
                  break;
                case 12:/* local echo off */
                  break;
                case 9: 	/* X10 mouse */
                case 1000:	/* xterm style mouse report on */
                case 1001:
                case 1002:
                case 1003:
                  mouserpt = DCEvars[i];
                  break;
                default:
                  System.out.println("ESC [ ? " + DCEvars[0] + " r, unimplemented!");
              }
            }
            break;
          case 'h': // DECSET
            if (debugVT > 0)
              System.out.println("ESC [ ? " + DCEvars[0] + " h");
            /* DEC Mode set */
            for (int i = 0; i <= DCEvar; i++) {
              switch (DCEvars[i]) {
                case 1:  /* Application cursor keys */
                  break;
                case 2: /* DECANM */
                  vt52mode = false;
                  break;
                case 3: /* 132 columns*/
                  setScreenSize(132, getRows());
                  break;
                case 6: /* DECOM: move inside margins. */
                  moveoutsidemargins = false;
                  break;
                case 7: /* DECAWM: Autowrap Mode */
                  wraparound = true;
                  break;
                case 25: /* turn cursor on */
                  showCursor(true);
                  break;
                case 9: 	/* X10 mouse */
                case 1000:	/* xterm style mouse report on */
                case 1001:
                case 1002:
                case 1003:
                  mouserpt = DCEvars[i];
                  break;
                case 1049:
                  /* Save/restore screen. This is used by curses to mark
                   * the beginning and end of the session, but is used for
                   * another purpose here: because curses never allows the
                   * cursor to wrap, it means that anything that would
                   * apparently leave the cursor offscreen should instead
                   * resize the window larger. (This can break slightly if
                   * something else is outputting that sequence, which is
                   * apparently undocumented; but not too badly.) */
                  if (debugAutoResize > 0) System.out.println("Turning autoresize on (curses detected)");
                  if (!vetoAutoResize) autoResize = true;
                  break;
                  /* unimplemented stuff, fall through */
                  /* 4  - scrolling mode, smooth */
                  /* 5  - light background */
                  /* 12 - local echo off */
                  /* 18 - DECPFF - Printer Form Feed Mode -> On */
                  /* 19 - DECPEX - Printer Extent Mode -> Screen */
                default:
                  System.out.println("ESC [ ? " + DCEvars[0] + " h, unsupported.");
                  break;
              }
            }
            break;
          case 'i': // DEC Printer Control, autoprint, echo screenchars to printer
            // This is different to CSI i!
            // Also: "Autoprint prints a final display line only when the
            // cursor is moved off the line by an autowrap or LF, FF, or
            // VT (otherwise do not print the line)."
            switch (DCEvars[0]) {
              case 1:
                if (debugVT > 1)
                  System.out.println("CSI ? 1 i : Print line containing cursor");
                break;
              case 4:
                if (debugVT > 1)
                  System.out.println("CSI ? 4 i : Start passthrough printing");
                break;
              case 5:
                if (debugVT > 1)
                  System.out.println("CSI ? 4 i : Stop passthrough printing");
                break;
            }
            break;
          case 'l':	//DECRST
            /* DEC Mode reset */
            if (debugVT > 0)
              System.out.println("ESC [ ? " + DCEvars[0] + " l");
            for (int i = 0; i <= DCEvar; i++) {
              switch (DCEvars[i]) {
                case 1:  /* Application cursor keys */
                  break;
                case 2: /* DECANM */
                  vt52mode = true;
                  break;
                case 3: /* 80 columns*/
                  setScreenSize(80, getRows());
                  break;
                case 6: /* DECOM: move outside margins. */
                  moveoutsidemargins = true;
                  break;
                case 7: /* DECAWM: Autowrap Mode OFF */
                  wraparound = false;
                  break;
                case 25: /* turn cursor off */
                  showCursor(false);
                  break;
                  /* Unimplemented stuff: */
                  /* 4  - scrolling mode, jump */
                  /* 5  - dark background */
                  /* 7  - DECAWM - no wrap around mode */
                  /* 12 - local echo on */
                  /* 18 - DECPFF - Printer Form Feed Mode -> Off*/
                  /* 19 - DECPEX - Printer Extent Mode -> Scrolling Region */
                case 9: 	/* X10 mouse */
                case 1000:	/* xterm style mouse report OFF */
                case 1001:
                case 1002:
                case 1003:
                  mouserpt = 0;
                  break;
                case 1049:
                  if (debugAutoResize > 0) System.out.println("Turning autoresize off (curses ending detected)");
                  autoResize = false;
                  break;
                default:
                  System.out.println("ESC [ ? " + DCEvars[0] + " l, unsupported.");
                  break;
              }
            }
            break;
          case 'n':
            if (debugVT > 0)
              System.out.println("ESC [ ? " + DCEvars[0] + " n");
            switch (DCEvars[0]) {
              case 15:
                /* printer? no printer. */
                System.out.println("ESC[5n");
                break;
              default:
                System.out.println("ESC [ ? " + DCEvars[0] + " n, unsupported.");
                break;
            }
            break;
          default:
            System.out.println("ESC [ ? " + DCEvars[0] + " " + c + ", unsupported.");
            break;
        }
        break;
      case TSTATE_CSI_EX:
        term_state = TSTATE_DATA;
        switch (c) {
          case ESC:
            term_state = TSTATE_ESC;
            break;
          default:
            System.out.println("Unknown character ESC[! character is " + (int) c);
            break;
        }
        break;
      case TSTATE_CSI_TICKS:
        term_state = TSTATE_DATA;
        switch (c) {
          case 'p':
            System.out.println("Conformance level: " + DCEvars[0] + " (unsupported)," + DCEvars[1]);
            if (DCEvars[0] == 61) {
              output8bit = false;
              break;
            }
            if (DCEvars[1] == 1) {
              output8bit = false;
            } else {
              output8bit = true; /* 0 or 2 */
            }
            break;
          default:
            System.out.println("Unknown ESC [...  \"" + c);
            break;
        }
        break;
      case TSTATE_CSI_EQUAL:
        term_state = TSTATE_DATA;
        switch (c) {
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            DCEvars[DCEvar] = DCEvars[DCEvar] * 10 + ((int) c) - 48;
            term_state = TSTATE_CSI_EQUAL;
            break;
          case ';':
            DCEvar++;
            DCEvars[DCEvar] = 0;
            term_state = TSTATE_CSI_EQUAL;
            break;

          case 'F': /* SCO ANSI foreground */
	  {
	    int newcolor;

            System.out.println("ESC [ = "+DCEvars[0]+" F");

            attributes &= ~COLOR_FG;
	    newcolor =	((DCEvars[0] & 1) << 2)	|
	    		 (DCEvars[0] & 2)	|
	    		((DCEvars[0] & 4) >> 2) ;
            attributes |= (newcolor+1) << COLOR_FG_SHIFT;

	    break;
	  }
          case 'G': /* SCO ANSI background */
	  {
	    int newcolor;

            System.out.println("ESC [ = "+DCEvars[0]+" G");

            attributes &= ~COLOR_BG;
	    newcolor =	((DCEvars[0] & 1) << 2)	|
	    		 (DCEvars[0] & 2)	|
	    		((DCEvars[0] & 4) >> 2) ;
            attributes |= (newcolor+1) << COLOR_BG_SHIFT;
	    break;
          }

          default:
            System.out.print("Unknown ESC [ = ");
	    for (int i=0;i<=DCEvar;i++)
		System.out.print(DCEvars[i]+",");
	    System.out.println("" + c);
            break;
        }
        break;
      case TSTATE_CSI_DOLLAR:
        term_state = TSTATE_DATA;
        switch (c) {
          case '}':
            System.out.println("Active Status Display now " + DCEvars[0]);
            statusmode = DCEvars[0];
            break;
            /* bad documentation?
               case '-':
               System.out.println("Set Status Display now "+DCEvars[0]);
               break;
            */
          case '~':
            System.out.println("Status Line mode now " + DCEvars[0]);
            break;
          default:
            System.out.println("UNKNOWN Status Display code " + c + ", with Pn=" + DCEvars[0]);
            break;
        }
        break;
      case TSTATE_CSI:
        term_state = TSTATE_DATA;
        switch (c) {
          case '"':
            term_state = TSTATE_CSI_TICKS;
            break;
          case '$':
            term_state = TSTATE_CSI_DOLLAR;
            break;
          case '=':
            term_state = TSTATE_CSI_EQUAL;
            break;
          case '!':
            term_state = TSTATE_CSI_EX;
            break;
          case '?':
            DCEvar = 0;
            DCEvars[0] = 0;
            term_state = TSTATE_DCEQ;
            break;
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            DCEvars[DCEvar] = DCEvars[DCEvar] * 10 + ((int) c) - 48;
            term_state = TSTATE_CSI;
            break;
          case ';':
            DCEvar++;
            DCEvars[DCEvar] = 0;
            term_state = TSTATE_CSI;
            break;
          case 'c':/* send primary device attributes */
            /* send (ESC[?61c) */

            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " c");
            break;
          case 'q':
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " q");
            break;
          case 'g':
            /* used for tabsets */
            switch (DCEvars[0]) {
              case 3:/* clear them */
                Tabs = new byte[getColumns()];
                break;
              case 0:
                Tabs[C] = 0;
                break;
            }
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " g");
            break;
          case 'h':
            switch (DCEvars[0]) {
              case 4:
                insertmode = 1;
                break;
              case 20:
                System.out.println("Setting CRLF to TRUE");
                sendcrlf = true;
                break;
              default:
                System.out.println("unsupported: ESC [ " + DCEvars[0] + " h");
                break;
            }
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " h");
            break;
          case 'i': // Printer Controller mode.
            // "Transparent printing sends all output, except the CSI 4 i
            //  termination string, to the printer and not the screen,
            //  uses an 8-bit channel if no parity so NUL and DEL will be
            //  seen by the printer and by the termination recognizer code,
            //  and all translation and character set selections are
            //  bypassed."
            switch (DCEvars[0]) {
              case 0:
                if (debugVT > 1)
                  System.out.println("CSI 0 i:  Print Screen, not implemented.");
                break;
              case 4:
                if (debugVT > 1)
                  System.out.println("CSI 4 i:  Enable Transparent Printing, not implemented.");
                break;
              case 5:
                if (debugVT > 1)
                  System.out.println("CSI 4/5 i:  Disable Transparent Printing, not implemented.");
                break;
              default:
                System.out.println("ESC [ " + DCEvars[0] + " i, unimplemented!");
            }
            break;
          case 'l':
            switch (DCEvars[0]) {
              case 4:
                insertmode = 0;
                break;
              case 20:
                System.out.println("Setting CRLF to FALSE");
                sendcrlf = false;
                break;
              default:
                System.out.println("ESC [ " + DCEvars[0] + " l, unimplemented!");
                break;
            }
            break;
          case 'A': // CUU
            {
              int limit;
              /* FIXME: xterm only cares about 0 and topmargin */
              if (R > bm)
                limit = bm + 1;
              else if (R >= tm) {
                limit = tm;
              } else
                limit = 0;
              if (DCEvars[0] == 0)
                R--;
              else
                R -= DCEvars[0];
              if (R < limit)
                R = limit;
              if (debugVT > 1)
                System.out.println("ESC [ " + DCEvars[0] + " A");
              break;
            }
          case 'B':	// CUD
            /* cursor down n (1) times */
            {
              int limit;
              if (R < tm)
                limit = tm - 1;
              else if (R <= bm) {
                limit = bm;
              } else
                limit = rows - 1;
              if (DCEvars[0] == 0)
                R++;
              else
                R += DCEvars[0];
              if (R > limit) {
                if (limit == rows-1 && autoResize) {
                  if (debugAutoResize > 0) System.out.println("Making window taller (CUD)");
                  this.setWindowSize(columns, R+1);
                  rows = R+1;
                } else
                  R = limit;
              }
              else {
                if (debugVT > 2) System.out.println("Not limited.");
              }
              if (debugVT > 2) System.out.println("to: " + R);
              if (debugVT > 1)
                System.out.println("ESC [ " + DCEvars[0] + " B (at C=" + C + ")");
              break;
            }
          case 'C':
            if (DCEvars[0] == 0)
              C++;
            else
              C += DCEvars[0];
            if (C > columns - 1) {
              if (autoResize) {
                  if (debugAutoResize > 0) System.out.println("Making window wider (CSI C)");
                  this.setWindowSize(C+1, rows);
                  columns = C+1;
              } else
                  C = columns - 1;
            }
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " C");
            break;
          case 'd': // CVA
            R = DCEvars[0] - 1;
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " d");
            break;
          case 'D':
            if (DCEvars[0] == 0)
              C--;
            else
              C -= DCEvars[0];
            if (C < 0) C = 0;
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " D");
            break;
          case 'r': // DECSTBM
            if (DCEvar > 0)   //  Ray:  Any argument is optional
            {
              R = DCEvars[1] - 1;
              if (R < 0)
                R = rows - 1;
              else if (R >= rows) {
                R = rows - 1;
              }
            } else
              R = rows - 1;
            setBottomMargin(R);
            if (R >= DCEvars[0]) {
              R = DCEvars[0] - 1;
              if (R < 0)
                R = 0;
            }
            setTopMargin(R);
            _SetCursor(0, 0);
            if (debugVT > 1)
              System.out.println("ESC [" + DCEvars[0] + " ; " + DCEvars[1] + " r");
            break;
          case 'G':  /* CUP  / cursor absolute column */
            C = DCEvars[0] - 1;
            if (debugVT > 1) System.out.println("ESC [ " + DCEvars[0] + " G");
            break;
          case 'H':  /* CUP  / cursor position */
            /* gets 2 arguments */
            _SetCursor(DCEvars[0] - 1, DCEvars[1] - 1);
            if (debugVT > 2) {
              System.out.println("ESC [ " + DCEvars[0] + ";" + DCEvars[1] + " H, moveoutsidemargins " + moveoutsidemargins);
              System.out.println("	-> R now " + R + ", C now " + C);
            }
            break;
          case 'f':  /* move cursor 2 */
            /* gets 2 arguments */
            R = DCEvars[0] - 1;
            C = DCEvars[1] - 1;
            if (C < 0) C = 0;
            if (R < 0) R = 0;
            if (debugVT > 2)
              System.out.println("ESC [ " + DCEvars[0] + ";" + DCEvars[1] + " f");
            break;
          case 'S': /* ind aka 'scroll forward' */
            if (DCEvars[0] == 0)
              insertLine(rows - 1, SCROLL_UP);
            else
              insertLine(rows - 1, DCEvars[0], SCROLL_UP);
            break;
          case 'L':
            /* insert n lines */
            if (DCEvars[0] == 0)
              insertLine(R, SCROLL_DOWN);
            else
              insertLine(R, DCEvars[0], SCROLL_DOWN);
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + "" + (c) + " (at R " + R + ")");
            break;
          case 'T': /* 'ri' aka scroll backward */
            if (DCEvars[0] == 0)
              insertLine(0, SCROLL_DOWN);
            else
              insertLine(0, DCEvars[0], SCROLL_DOWN);
            break;
          case 'M':
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + "" + (c) + " at R=" + R);
            if (DCEvars[0] == 0)
              deleteLine(R);
            else
              for (int i = 0; i < DCEvars[0]; i++)
                deleteLine(R);
            break;
          case 'K':
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " K");
            /* clear in line */
            switch (DCEvars[0]) {
              case 6: /* 97801 uses ESC[6K for delete to end of line */
              case 0:/*clear to right*/
                if (C < columns - 1)
                  deleteArea(C, R, columns - C, 1, attributes);
                break;
              case 1:/*clear to the left, including this */
                if (C > 0)
                  deleteArea(0, R, C + 1, 1, attributes);
                break;
              case 2:/*clear whole line */
                deleteArea(0, R, columns, 1, attributes);
                break;
            }
            break;
          case 'J':
            /* clear below current line */
            switch (DCEvars[0]) {
              case 0:
                if (R < rows - 1)
                  deleteArea(0, R + 1, columns, rows - R - 1, attributes);
                if (C < columns - 1)
                  deleteArea(C, R, columns - C, 1, attributes);
                break;
              case 1:
                if (R > 0)
                  deleteArea(0, 0, columns, R, attributes);
                if (C > 0)
                  deleteArea(0, R, C + 1, 1, attributes);// include up to and including current
                break;
              case 2:
                deleteArea(0, 0, columns, rows, attributes);
                break;
            }
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " J");
            break;
          case '@':
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " @");
            for (int i = 0; i < DCEvars[0]; i++)
              insertChar(C, R, ' ', attributes);
            break;
          case 'X':
            {
              int toerase = DCEvars[0];
              if (debugVT > 1)
                System.out.println("ESC [ " + DCEvars[0] + " X, C=" + C + ",R=" + R);
              if (toerase == 0)
                toerase = 1;
              if (toerase + C > columns)
                toerase = columns - C;
              deleteArea(C, R, toerase, 1, attributes);
              // does not change cursor position
              break;
            }
          case 'P':
            if (debugVT > 1)
              System.out.println("ESC [ " + DCEvars[0] + " P, C=" + C + ",R=" + R);
            if (DCEvars[0] == 0) DCEvars[0] = 1;
            for (int i = 0; i < DCEvars[0]; i++)
              deleteChar(C, R);
            break;
          case 'n':
            switch (DCEvars[0]) {
              case 5: /* malfunction? No malfunction. */
                if (debugVT > 1)
                  System.out.println("ESC[5n");
                break;
              case 6:
                // DO NOT offset R and C by 1! (checked against /usr/X11R6/bin/resize
                // FIXME check again.
                // FIXME: but vttest thinks different???
                if (debugVT > 1)
                  System.out.println("ESC[6n");
                break;
              default:
                if (debugVT > 0)
                  System.out.println("ESC [ " + DCEvars[0] + " n??");
                break;
            }
            break;
          case 's':  /* DECSC - save cursor */
            Sc = C;
            Sr = R;
            Sa = attributes;
            System.out.println("ESC[s");
            break;
          case 't': /* some terminals respond to this by setting the screen size */
            if (DCEvars[0] == 8 ){
              // I'm not entirely sure what the 8 is for (can't find docs...)
              // but it seems to be required.
              setScreenSize(DCEvars[2],DCEvars[1]);
              vetoAutoResize = true;
              autoResize = false;
              if (debugAutoResize > 0) System.out.println("Vetoing autoresize (explicit screen size set detected)");
            } else {
              System.out.println("Unhandled ESC [ t");
            }
            break;
          case 'u': /* DECRC - restore cursor */
            C = Sc;
            R = Sr;
            attributes = Sa;
            if (debugVT > 3)
              System.out.println("ESC[u");
            break;
          case 'm':  /* attributes as color, bold , blink,*/
            if (debugVT > 3)
              System.out.print("ESC [ ");
            if (DCEvar == 0 && DCEvars[0] == 0)
              attributes = 0;
            for (int i = 0; i <= DCEvar; i++) {
              switch (DCEvars[i]) {
                case 0:
                  if (DCEvar > 0) {
                    if (terminalID.equals("scoansi")) {
                      attributes &= COLOR; /* Keeps color. Strange but true. */
                    } else {
                      attributes = 0;
                    }
                  }
                  break;
                case 1:
                  attributes |= BOLD;
                  attributes &= ~LOW;
                  break;
                case 2:
                  /* SCO color hack mode */
                  if (terminalID.equals("scoansi") && ((DCEvar - i) >= 2)) {
                    int ncolor;
                    attributes &= ~(COLOR | BOLD);

                    ncolor = DCEvars[i + 1];
                    if ((ncolor & 8) == 8)
                      attributes |= BOLD;
                    ncolor = ((ncolor & 1) << 2) | (ncolor & 2) | ((ncolor & 4) >> 2);
                    attributes |= ((ncolor) + 1) << COLOR_FG_SHIFT;
                    ncolor = DCEvars[i + 2];
                    ncolor = ((ncolor & 1) << 2) | (ncolor & 2) | ((ncolor & 4) >> 2);
                    attributes |= ((ncolor) + 1) << COLOR_BG_SHIFT;
                    i += 2;
                  } else {
                    attributes |= LOW;
                  }
                  break;
                case 4:
                  attributes |= UNDERLINE;
                  break;
                case 7:
                  attributes |= INVERT;
                  break;
                case 8:
                  attributes |= INVISIBLE;
                  break;
                case 5: /* blink on */
                  break;
                  /* 10 - ANSI X3.64-1979, select primary font, don't display control
                   *      chars, don't set bit 8 on output */
                case 10:
                  gl = 0;
                  usedcharsets = true;
                  break;
                  /* 11 - ANSI X3.64-1979, select second alt. font, display control
                   *      chars, set bit 8 on output */
                case 11: /* SMACS , as */
                case 12:
                  gl = 1;
                  usedcharsets = true;
                  break;
                case 21: /* normal intensity */
                  attributes &= ~(LOW | BOLD);
                  break;
                case 25: /* blinking off */
                  break;
                case 27:
                  attributes &= ~INVERT;
                  break;
                case 28:
                  attributes &= ~INVISIBLE;
                  break;
                case 24:
                  attributes &= ~UNDERLINE;
                  break;
                case 22:
                  attributes &= ~BOLD;
                  break;
                case 30:
                case 31:
                case 32:
                case 33:
                case 34:
                case 35:
                case 36:
                case 37:
                  attributes &= ~COLOR_FG;
                  attributes |= ((DCEvars[i] - 30) + 1) << COLOR_FG_SHIFT;
                  break;
                case 39:
                  attributes &= ~COLOR_FG;
                  break;
                case 40:
                case 41:
                case 42:
                case 43:
                case 44:
                case 45:
                case 46:
                case 47:
                  attributes &= ~COLOR_BG;
                  attributes |= ((DCEvars[i] - 40) + 1) << COLOR_BG_SHIFT;
                  break;
                case 49:
                  attributes &= ~COLOR_BG;
                  break;

                default:
                  System.out.println("ESC [ " + DCEvars[i] + " m unknown...");
                  break;
              }
              if (debugVT > 3)
                System.out.print("" + DCEvars[i] + ";");
            }
            if (debugVT > 3)
              System.out.print(" (attributes = " + attributes + ")m \n");
            break;
          default:
            System.out.println("ESC [ unknown letter:" + c + " (" + ((int) c) + ")");
            break;
        }
        break;
      default:
        term_state = TSTATE_DATA;
        break;
    }
    if (autoResize) {
        if (C >= columns && !wrappedOnThisCharacter && term_state == TSTATE_DATA) {
            if (debugAutoResize > 0) System.out.println("Making window wider (cursor beyond edge of screen)");
            columns = C+1;
            this.setScreenSize(columns, rows);
        }
        if (R >= rows) {
            if (debugAutoResize > 0) System.out.println("Making window taller (cursor beyond edge of screen)");
            rows = R+1;
            this.setScreenSize(columns, rows);
        }
    } else {
        if (C > columns) C = columns;
        if (R > rows) R = rows;
    }
    if (C < 0) C = 0;
    if (R < 0) R = 0;
    if (doshowcursor)
      setCursorPosition(C, R);
  }

  /**
   * Completely reset the terminal state, as if a reset sequence had
   * been received.
   */
  public void reset() {
    gx[0] = 'B';
    gx[1] = '0';
    gx[2] = 'B';
    gx[3] = 'B';
    gl = 0;  // default GL to G0
    gr = 1;  // default GR to G1
    /* reset tabs */
    int nw = getColumns();
    if (nw < 132) nw = 132;
    Tabs = new byte[nw];
    for (int i = 0; i < nw; i += 8) {
      Tabs[i] = 1;
    }
    /*FIXME:*/
    term_state = TSTATE_DATA;
  }
}
