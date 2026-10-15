/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.terminal.tty;

/**
 * Terminal capabilities as defined in the terminfo database.
 * Each capability has a name and a short cap code.
 * <p>
 * This enum provides a comprehensive list of terminal capabilities including:
 * <ul>
 * <li>Boolean capabilities - indicate whether a feature is supported</li>
 * <li>Numeric capabilities - provide numeric values like screen dimensions</li>
 * <li>String capabilities - contain escape sequences for terminal operations</li>
 * </ul>
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum Capability {

    /** Terminal has automatic margins (cursor wraps from last column to first of next line). */
    auto_left_margin("bw", "bw"),
    /** Cursor wraps from right margin to left of next line. */
    auto_right_margin("am", "am"),
    /** Screen erased with background color. */
    back_color_erase("bce", "ut"),
    /** Terminal can redefine existing colors. */
    can_change("ccc", "cc"),
    /** Standout mode causes glitch at end of line. */
    ceol_standout_glitch("xhp", "xs"),
    /** Column address has glitch. */
    col_addr_glitch("xhpa", "YA"),
    /** Changing character pitch changes resolution. */
    cpi_changes_res("cpix", "YF"),
    /** Carriage return cancels micro mode. */
    cr_cancels_micro_mode("crxm", "YB"),
    /** Destructive tabs with magic standout. */
    dest_tabs_magic_smso("xt", "xt"),
    /** Newline ignored after 80 columns. */
    eat_newline_glitch("xenl", "xn"),
    /** Can erase overstrikes with a blank. */
    erase_overstrike("eo", "eo"),
    /** Generic terminal type. */
    generic_type("gn", "gn"),
    /** Hardcopy terminal. */
    hard_copy("hc", "hc"),
    /** Cursor is hard to see. */
    hard_cursor("chts", "HC"),
    /** Terminal has a meta key. */
    has_meta_key("km", "km"),
    /** Printer needs operator to change character set. */
    has_print_wheel("daisy", "YC"),
    /** Terminal has a status line. */
    has_status_line("hs", "hs"),
    /** Terminal uses HLS color notation. */
    hue_lightness_saturation("hls", "hl"),
    /** Insert mode distinguishes nulls. */
    insert_null_glitch("in", "in"),
    /** Changing line pitch changes resolution. */
    lpi_changes_res("lpix", "YG"),
    /** Display may be retained above the screen. */
    memory_above("da", "da"),
    /** Display may be retained below the screen. */
    memory_below("db", "db"),
    /** Safe to move while in insert mode. */
    move_insert_mode("mir", "mi"),
    /** Safe to move while in standout mode. */
    move_standout_mode("msgr", "ms"),
    /** Padding will not work, xon/xoff required. */
    needs_xon_xoff("nxon", "nx"),
    /** Beehive (f1=escape, f2=ctrl C). */
    no_esc_ctlc("xsb", "xb"),
    /** Padding character does not exist. */
    no_pad_char("npc", "NP"),
    /** Scrolling region is nondestructive. */
    non_dest_scroll_region("ndscr", "ND"),
    /** Exit alternate screen without restoring cursor. */
    non_rev_rmcup("nrrmc", "NR"),
    /** Terminal can overstrike. */
    over_strike("os", "os"),
    /** Printer will not echo on screen. */
    prtr_silent("mc5i", "5i"),
    /** Row address has glitch. */
    row_addr_glitch("xvpa", "YD"),
    /** Printing in last column causes carriage return. */
    semi_auto_right_margin("sam", "YE"),
    /** Escape can be used on the status line. */
    status_line_esc_ok("eslok", "es"),
    /** Cannot print tilde (~) character. */
    tilde_glitch("hz", "hz"),
    /** Underline character overstrikes. */
    transparent_underline("ul", "ul"),
    /** Terminal uses xon/xoff handshaking. */
    xon_xoff("xon", "xo"),
    /** Number of columns in a line. */
    columns("cols", "co"),
    /** Tabs initially every n spaces. */
    init_tabs("it", "it"),
    /** Number of rows in each label. */
    label_height("lh", "lh"),
    /** Number of columns in each label. */
    label_width("lw", "lw"),
    /** Number of lines on screen or page. */
    lines("lines", "li"),
    /** Lines of memory if greater than lines. */
    lines_of_memory("lm", "lm"),
    /** Number of blank characters left by smso/rmso. */
    magic_cookie_glitch("xmc", "sg"),
    /** Maximum combined attributes terminal can handle. */
    max_attributes("ma", "ma"),
    /** Maximum number of colors on screen. */
    max_colors("colors", "Co"),
    /** Maximum number of color pairs on screen. */
    max_pairs("pairs", "pa"),
    /** Maximum number of definable windows. */
    maximum_windows("wnum", "MW"),
    /** Video attributes that cannot be used with colors. */
    no_color_video("ncv", "NC"),
    /** Number of labels on screen. */
    num_labels("nlab", "Nl"),
    /** Lowest baud rate where padding is needed. */
    padding_baud_rate("pb", "pb"),
    /** Virtual terminal number. */
    virtual_terminal("vt", "vt"),
    /** Number of columns in status line. */
    width_status_line("wsl", "ws"),
    /** Number of passes for each bit-image row. */
    bit_image_entwining("bitwin", "Yo"),
    /** Type of bit-image device. */
    bit_image_type("bitype", "Yp"),
    /** Number of bytes buffered before printing. */
    buffer_capacity("bufsz", "Ya"),
    /** Number of buttons on mouse. */
    buttons("btns", "BT"),
    /** Spacing of pins horizontally in dots per inch. */
    dot_horz_spacing("spinh", "Yc"),
    /** Spacing of dots vertically in pins per inch. */
    dot_vert_spacing("spinv", "Yb"),
    /** Maximum value in micro_..._address. */
    max_micro_address("maddr", "Yd"),
    /** Maximum value in parm_..._micro. */
    max_micro_jump("mjump", "Ye"),
    /** Character step size in micro mode. */
    micro_col_size("mcs", "Yf"),
    /** Line step size in micro mode. */
    micro_line_size("mls", "Yg"),
    /** Number of pins in print head. */
    number_of_pins("npins", "Yh"),
    /** Horizontal resolution in units per character. */
    output_res_char("orc", "Yi"),
    /** Horizontal resolution in units per inch. */
    output_res_horz_inch("orhi", "Yk"),
    /** Vertical resolution in units per line. */
    output_res_line("orl", "Yj"),
    /** Vertical resolution in units per inch. */
    output_res_vert_inch("orvi", "Yl"),
    /** Print rate in characters per second. */
    print_rate("cps", "Ym"),
    /** Character step size when in double-wide mode. */
    wide_char_size("widcs", "Yn"),
    /** Graphics charset pairs, based on vt100. */
    acs_chars("acsc", "ac"),
    /** Back tab. */
    back_tab("cbt", "bt"),
    /** Audible signal (bell). */
    bell("bel", "bl"),
    /** Carriage return. */
    carriage_return("cr", "cr"),
    /** Change horizontal pitch. */
    change_char_pitch("cpi", "ZA"),
    /** Change vertical pitch. */
    change_line_pitch("lpi", "ZB"),
    /** Change horizontal resolution. */
    change_res_horz("chr", "ZC"),
    /** Change vertical resolution. */
    change_res_vert("cvr", "ZD"),
    /** Change scrolling region to lines #1 to #2. */
    change_scroll_region("csr", "cs"),
    /** Like ip but when in replace mode. */
    char_padding("rmp", "rP"),
    /** Clear all tab stops. */
    clear_all_tabs("tbc", "ct"),
    /** Clear right and left soft margins. */
    clear_margins("mgc", "MC"),
    /** Clear screen and home cursor. */
    clear_screen("clear", "cl"),
    /** Clear to beginning of line. */
    clr_bol("el1", "cb"),
    /** Clear to end of line. */
    clr_eol("el", "ce"),
    /** Clear to end of screen. */
    clr_eos("ed", "cd"),
    /** Move cursor to column #1. */
    column_address("hpa", "ch"),
    /** Terminal settable cmd character in prototype. */
    command_character("cmdch", "CC"),
    /** Create a window #1 #2 #3 #4 #5. */
    create_window("cwin", "CW"),
    /** Move cursor to row #1 column #2. */
    cursor_address("cup", "cm"),
    /** Move cursor down one line. */
    cursor_down("cud1", "do"),
    /** Move cursor to home position. */
    cursor_home("home", "ho"),
    /** Make cursor invisible. */
    cursor_invisible("civis", "vi"),
    /** Move cursor left one space. */
    cursor_left("cub1", "le"),
    /** Memory relative cursor addressing. */
    cursor_mem_address("mrcup", "CM"),
    /** Make cursor appear normal. */
    cursor_normal("cnorm", "ve"),
    /** Move cursor right one space. */
    cursor_right("cuf1", "nd"),
    /** Move cursor to last line, first column. */
    cursor_to_ll("ll", "ll"),
    /** Move cursor up one line. */
    cursor_up("cuu1", "up"),
    /** Make cursor very visible. */
    cursor_visible("cvvis", "vs"),
    /** Define a character. */
    define_char("defc", "ZE"),
    /** Delete character. */
    delete_character("dch1", "dc"),
    /** Delete line. */
    delete_line("dl1", "dl"),
    /** Dial phone number #1. */
    dial_phone("dial", "DI"),
    /** Disable status line. */
    dis_status_line("dsl", "ds"),
    /** Display clock at row #1, column #2. */
    display_clock("dclk", "DK"),
    /** Move cursor down half a line. */
    down_half_line("hd", "hd"),
    /** Enable alternate character set. */
    ena_acs("enacs", "eA"),
    /** Start alternate character set. */
    enter_alt_charset_mode("smacs", "as"),
    /** Turn on automatic margins. */
    enter_am_mode("smam", "SA"),
    /** Turn on blinking. */
    enter_blink_mode("blink", "mb"),
    /** Turn on bold (extra bright) mode. */
    enter_bold_mode("bold", "md"),
    /** Enter alternate screen mode. */
    enter_ca_mode("smcup", "ti"),
    /** Enter delete mode. */
    enter_delete_mode("smdc", "dm"),
    /** Turn on half-bright mode. */
    enter_dim_mode("dim", "mh"),
    /** Enter double-wide mode. */
    enter_doublewide_mode("swidm", "ZF"),
    /** Enter draft-quality mode. */
    enter_draft_quality("sdrfq", "ZG"),
    /** Enter insert mode. */
    enter_insert_mode("smir", "im"),
    /** Enter italic mode. */
    enter_italics_mode("sitm", "ZH"),
    /** Start leftward carriage motion. */
    enter_leftward_mode("slm", "ZI"),
    /** Start micro-motion mode. */
    enter_micro_mode("smicm", "ZJ"),
    /** Enter NLQ mode. */
    enter_near_letter_quality("snlq", "ZK"),
    /** Enter normal-quality mode. */
    enter_normal_quality("snrmq", "ZL"),
    /** Turn on protected mode. */
    enter_protected_mode("prot", "mp"),
    /** Turn on reverse video mode. */
    enter_reverse_mode("rev", "mr"),
    /** Turn on secure (invisible) mode. */
    enter_secure_mode("invis", "mk"),
    /** Enter shadow-print mode. */
    enter_shadow_mode("sshm", "ZM"),
    /** Begin standout mode. */
    enter_standout_mode("smso", "so"),
    /** Enter subscript mode. */
    enter_subscript_mode("ssubm", "ZN"),
    /** Enter superscript mode. */
    enter_superscript_mode("ssupm", "ZO"),
    /** Begin underline mode. */
    enter_underline_mode("smul", "us"),
    /** Start upward carriage motion. */
    enter_upward_mode("sum", "ZP"),
    /** Turn on xon/xoff handshaking. */
    enter_xon_mode("smxon", "SX"),
    /** Erase #1 characters. */
    erase_chars("ech", "ec"),
    /** End alternate character set. */
    exit_alt_charset_mode("rmacs", "ae"),
    /** Turn off automatic margins. */
    exit_am_mode("rmam", "RA"),
    /** Turn off all attributes. */
    exit_attribute_mode("sgr0", "me"),
    /** Exit alternate screen mode. */
    exit_ca_mode("rmcup", "te"),
    /** End delete mode. */
    exit_delete_mode("rmdc", "ed"),
    /** End double-wide mode. */
    exit_doublewide_mode("rwidm", "ZQ"),
    /** Exit insert mode. */
    exit_insert_mode("rmir", "ei"),
    /** End italic mode. */
    exit_italics_mode("ritm", "ZR"),
    /** End leftward carriage motion. */
    exit_leftward_mode("rlm", "ZS"),
    /** End micro-motion mode. */
    exit_micro_mode("rmicm", "ZT"),
    /** End shadow-print mode. */
    exit_shadow_mode("rshm", "ZU"),
    /** Exit standout mode. */
    exit_standout_mode("rmso", "se"),
    /** End subscript mode. */
    exit_subscript_mode("rsubm", "ZV"),
    /** End superscript mode. */
    exit_superscript_mode("rsupm", "ZW"),
    /** Exit underline mode. */
    exit_underline_mode("rmul", "ue"),
    /** End upward carriage motion. */
    exit_upward_mode("rum", "ZX"),
    /** Turn off xon/xoff handshaking. */
    exit_xon_mode("rmxon", "RX"),
    /** Pause for 2-3 seconds. */
    fixed_pause("pause", "PA"),
    /** Flash switch hook. */
    flash_hook("hook", "fh"),
    /** Visible bell (may move cursor). */
    flash_screen("flash", "vb"),
    /** Hardcopy terminal page eject. */
    form_feed("ff", "ff"),
    /** Return from status line. */
    from_status_line("fsl", "fs"),
    /** Go to window #1. */
    goto_window("wingo", "WG"),
    /** Hang-up phone. */
    hangup("hup", "HU"),
    /** Initialization string 1. */
    init_1string("is1", "i1"),
    /** Initialization string 2. */
    init_2string("is2", "is"),
    /** Initialization string 3. */
    init_3string("is3", "i3"),
    /** Name of initialization file. */
    init_file("if", "if"),
    /** Path name of program for initialization. */
    init_prog("iprog", "iP"),
    /** Initialize color #1 to RGB values #2 #3 #4. */
    initialize_color("initc", "Ic"),
    /** Initialize color pair #1 to fg=#2 bg=#3. */
    initialize_pair("initp", "Ip"),
    /** Insert character. */
    insert_character("ich1", "ic"),
    /** Insert line. */
    insert_line("il1", "al"),
    /** Insert padding after inserted character. */
    insert_padding("ip", "ip"),
    /** Upper left of keypad key. */
    key_a1("ka1", "K1"),
    /** Upper right of keypad key. */
    key_a3("ka3", "K3"),
    /** Center of keypad key. */
    key_b2("kb2", "K2"),
    /** Backspace key. */
    key_backspace("kbs", "kb"),
    /** Begin key. */
    key_beg("kbeg", "@1"),
    /** Back-tab key. */
    key_btab("kcbt", "kB"),
    /** Lower left of keypad key. */
    key_c1("kc1", "K4"),
    /** Lower right of keypad key. */
    key_c3("kc3", "K5"),
    /** Cancel key. */
    key_cancel("kcan", "@2"),
    /** Clear-all-tabs key. */
    key_catab("ktbc", "ka"),
    /** Clear-screen or erase key. */
    key_clear("kclr", "kC"),
    /** Close key. */
    key_close("kclo", "@3"),
    /** Command key. */
    key_command("kcmd", "@4"),
    /** Copy key. */
    key_copy("kcpy", "@5"),
    /** Create key. */
    key_create("kcrt", "@6"),
    /** Clear-tab key. */
    key_ctab("kctab", "kt"),
    /** Delete-character key. */
    key_dc("kdch1", "kD"),
    /** Delete-line key. */
    key_dl("kdl1", "kL"),
    /** Down-arrow key. */
    key_down("kcud1", "kd"),
    /** Exit insert-mode key. */
    key_eic("krmir", "kM"),
    /** End key. */
    key_end("kend", "@7"),
    /** Enter/send key. */
    key_enter("kent", "@8"),
    /** Clear-to-end-of-line key. */
    key_eol("kel", "kE"),
    /** Clear-to-end-of-screen key. */
    key_eos("ked", "kS"),
    /** Exit key. */
    key_exit("kext", "@9"),
    /** Function key F0. */
    key_f0("kf0", "k0"),
    /** Function key F1. */
    key_f1("kf1", "k1"),
    /** Function key F10. */
    key_f10("kf10", "k;"),
    /** Function key F11. */
    key_f11("kf11", "F1"),
    /** Function key F12. */
    key_f12("kf12", "F2"),
    /** Function key F13. */
    key_f13("kf13", "F3"),
    /** Function key F14. */
    key_f14("kf14", "F4"),
    /** Function key F15. */
    key_f15("kf15", "F5"),
    /** Function key F16. */
    key_f16("kf16", "F6"),
    /** Function key F17. */
    key_f17("kf17", "F7"),
    /** Function key F18. */
    key_f18("kf18", "F8"),
    /** Function key F19. */
    key_f19("kf19", "F9"),
    /** Function key F2. */
    key_f2("kf2", "k2"),
    /** Function key F20. */
    key_f20("kf20", "FA"),
    /** Function key F21. */
    key_f21("kf21", "FB"),
    /** Function key F22. */
    key_f22("kf22", "FC"),
    /** Function key F23. */
    key_f23("kf23", "FD"),
    /** Function key F24. */
    key_f24("kf24", "FE"),
    /** Function key F25. */
    key_f25("kf25", "FF"),
    /** Function key F26. */
    key_f26("kf26", "FG"),
    /** Function key F27. */
    key_f27("kf27", "FH"),
    /** Function key F28. */
    key_f28("kf28", "FI"),
    /** Function key F29. */
    key_f29("kf29", "FJ"),
    /** Function key F3. */
    key_f3("kf3", "k3"),
    /** Function key F30. */
    key_f30("kf30", "FK"),
    /** Function key F31. */
    key_f31("kf31", "FL"),
    /** Function key F32. */
    key_f32("kf32", "FM"),
    /** Function key F33. */
    key_f33("kf33", "FN"),
    /** Function key F34. */
    key_f34("kf34", "FO"),
    /** Function key F35. */
    key_f35("kf35", "FP"),
    /** Function key F36. */
    key_f36("kf36", "FQ"),
    /** Function key F37. */
    key_f37("kf37", "FR"),
    /** Function key F38. */
    key_f38("kf38", "FS"),
    /** Function key F39. */
    key_f39("kf39", "FT"),
    /** Function key F4. */
    key_f4("kf4", "k4"),
    /** Function key F40. */
    key_f40("kf40", "FU"),
    /** Function key F41. */
    key_f41("kf41", "FV"),
    /** Function key F42. */
    key_f42("kf42", "FW"),
    /** Function key F43. */
    key_f43("kf43", "FX"),
    /** Function key F44. */
    key_f44("kf44", "FY"),
    /** Function key F45. */
    key_f45("kf45", "FZ"),
    /** Function key F46. */
    key_f46("kf46", "Fa"),
    /** Function key F47. */
    key_f47("kf47", "Fb"),
    /** Function key F48. */
    key_f48("kf48", "Fc"),
    /** Function key F49. */
    key_f49("kf49", "Fd"),
    /** Function key F5. */
    key_f5("kf5", "k5"),
    /** Function key F50. */
    key_f50("kf50", "Fe"),
    /** Function key F51. */
    key_f51("kf51", "Ff"),
    /** Function key F52. */
    key_f52("kf52", "Fg"),
    /** Function key F53. */
    key_f53("kf53", "Fh"),
    /** Function key F54. */
    key_f54("kf54", "Fi"),
    /** Function key F55. */
    key_f55("kf55", "Fj"),
    /** Function key F56. */
    key_f56("kf56", "Fk"),
    /** Function key F57. */
    key_f57("kf57", "Fl"),
    /** Function key F58. */
    key_f58("kf58", "Fm"),
    /** Function key F59. */
    key_f59("kf59", "Fn"),
    /** Function key F6. */
    key_f6("kf6", "k6"),
    /** Function key F60. */
    key_f60("kf60", "Fo"),
    /** Function key F61. */
    key_f61("kf61", "Fp"),
    /** Function key F62. */
    key_f62("kf62", "Fq"),
    /** Function key F63. */
    key_f63("kf63", "Fr"),
    /** Function key F7. */
    key_f7("kf7", "k7"),
    /** Function key F8. */
    key_f8("kf8", "k8"),
    /** Function key F9. */
    key_f9("kf9", "k9"),
    /** Find key. */
    key_find("kfnd", "@0"),
    /** Help key. */
    key_help("khlp", "%1"),
    /** Home key. */
    key_home("khome", "kh"),
    /** Insert-character key. */
    key_ic("kich1", "kI"),
    /** Insert-line key. */
    key_il("kil1", "kA"),
    /** Left-arrow key. */
    key_left("kcub1", "kl"),
    /** Lower-left key (home down). */
    key_ll("kll", "kH"),
    /** Mark key. */
    key_mark("kmrk", "%2"),
    /** Message key. */
    key_message("kmsg", "%3"),
    /** Move key. */
    key_move("kmov", "%4"),
    /** Next key. */
    key_next("knxt", "%5"),
    /** Next-page key. */
    key_npage("knp", "kN"),
    /** Open key. */
    key_open("kopn", "%6"),
    /** Options key. */
    key_options("kopt", "%7"),
    /** Previous-page key. */
    key_ppage("kpp", "kP"),
    /** Previous key. */
    key_previous("kprv", "%8"),
    /** Print key. */
    key_print("kprt", "%9"),
    /** Redo key. */
    key_redo("krdo", "%0"),
    /** Reference key. */
    key_reference("kref", "&1"),
    /** Refresh key. */
    key_refresh("krfr", "&2"),
    /** Replace key. */
    key_replace("krpl", "&3"),
    /** Restart key. */
    key_restart("krst", "&4"),
    /** Resume key. */
    key_resume("kres", "&5"),
    /** Right-arrow key. */
    key_right("kcuf1", "kr"),
    /** Save key. */
    key_save("ksav", "&6"),
    /** Shifted begin key. */
    key_sbeg("kBEG", "&9"),
    /** Shifted cancel key. */
    key_scancel("kCAN", "&0"),
    /** Shifted command key. */
    key_scommand("kCMD", "*1"),
    /** Shifted copy key. */
    key_scopy("kCPY", "*2"),
    /** Shifted create key. */
    key_screate("kCRT", "*3"),
    /** Shifted delete-character key. */
    key_sdc("kDC", "*4"),
    /** Shifted delete-line key. */
    key_sdl("kDL", "*5"),
    /** Select key. */
    key_select("kslt", "*6"),
    /** Shifted end key. */
    key_send("kEND", "*7"),
    /** Shifted clear-to-end-of-line key. */
    key_seol("kEOL", "*8"),
    /** Shifted exit key. */
    key_sexit("kEXT", "*9"),
    /** Scroll-forward key. */
    key_sf("kind", "kF"),
    /** Shifted find key. */
    key_sfind("kFND", "*0"),
    /** Shifted help key. */
    key_shelp("kHLP", "#1"),
    /** Shifted home key. */
    key_shome("kHOM", "#2"),
    /** Shifted insert-character key. */
    key_sic("kIC", "#3"),
    /** Shifted left-arrow key. */
    key_sleft("kLFT", "#4"),
    /** Shifted message key. */
    key_smessage("kMSG", "%a"),
    /** Shifted move key. */
    key_smove("kMOV", "%b"),
    /** Shifted next key. */
    key_snext("kNXT", "%c"),
    /** Shifted options key. */
    key_soptions("kOPT", "%d"),
    /** Shifted previous key. */
    key_sprevious("kPRV", "%e"),
    /** Shifted print key. */
    key_sprint("kPRT", "%f"),
    /** Scroll-backward key. */
    key_sr("kri", "kR"),
    /** Shifted redo key. */
    key_sredo("kRDO", "%g"),
    /** Shifted replace key. */
    key_sreplace("kRPL", "%h"),
    /** Shifted right-arrow key. */
    key_sright("kRIT", "%i"),
    /** Shifted resume key. */
    key_srsume("kRES", "%j"),
    /** Shifted save key. */
    key_ssave("kSAV", "!1"),
    /** Shifted suspend key. */
    key_ssuspend("kSPD", "!2"),
    /** Set-tab key. */
    key_stab("khts", "kT"),
    /** Shifted undo key. */
    key_sundo("kUND", "!3"),
    /** Suspend key. */
    key_suspend("kspd", "&7"),
    /** Undo key. */
    key_undo("kund", "&8"),
    /** Up-arrow key. */
    key_up("kcuu1", "ku"),
    /** Leave keyboard transmit mode. */
    keypad_local("rmkx", "ke"),
    /** Enter keyboard transmit mode. */
    keypad_xmit("smkx", "ks"),
    /** Label on function key F0. */
    lab_f0("lf0", "l0"),
    /** Label on function key F1. */
    lab_f1("lf1", "l1"),
    /** Label on function key F10. */
    lab_f10("lf10", "la"),
    /** Label on function key F2. */
    lab_f2("lf2", "l2"),
    /** Label on function key F3. */
    lab_f3("lf3", "l3"),
    /** Label on function key F4. */
    lab_f4("lf4", "l4"),
    /** Label on function key F5. */
    lab_f5("lf5", "l5"),
    /** Label on function key F6. */
    lab_f6("lf6", "l6"),
    /** Label on function key F7. */
    lab_f7("lf7", "l7"),
    /** Label on function key F8. */
    lab_f8("lf8", "l8"),
    /** Label on function key F9. */
    lab_f9("lf9", "l9"),
    /** Label format. */
    label_format("fln", "Lf"),
    /** Turn off soft labels. */
    label_off("rmln", "LF"),
    /** Turn on soft labels. */
    label_on("smln", "LO"),
    /** Turn off meta mode. */
    meta_off("rmm", "mo"),
    /** Turn on meta mode (8th bit on). */
    meta_on("smm", "mm"),
    /** Move cursor to column #1 in micro mode. */
    micro_column_address("mhpa", "ZY"),
    /** Move cursor down in micro mode. */
    micro_down("mcud1", "ZZ"),
    /** Move cursor left in micro mode. */
    micro_left("mcub1", "Za"),
    /** Move cursor right in micro mode. */
    micro_right("mcuf1", "Zb"),
    /** Move cursor to row #1 in micro mode. */
    micro_row_address("mvpa", "Zc"),
    /** Move cursor up in micro mode. */
    micro_up("mcuu1", "Zd"),
    /** Newline (behave like carriage return followed by line feed). */
    newline("nel", "nw"),
    /** Match software bits to print-head pins. */
    order_of_pins("porder", "Ze"),
    /** Reset all color pairs to the default. */
    orig_colors("oc", "oc"),
    /** Set default pair to its original value. */
    orig_pair("op", "op"),
    /** Padding character. */
    pad_char("pad", "pc"),
    /** Delete #1 characters. */
    parm_dch("dch", "DC"),
    /** Delete #1 lines. */
    parm_delete_line("dl", "DL"),
    /** Move cursor down #1 lines. */
    parm_down_cursor("cud", "DO"),
    /** Move cursor down #1 lines in micro mode. */
    parm_down_micro("mcud", "Zf"),
    /** Insert #1 characters. */
    parm_ich("ich", "IC"),
    /** Scroll forward #1 lines. */
    parm_index("indn", "SF"),
    /** Insert #1 lines. */
    parm_insert_line("il", "AL"),
    /** Move cursor left #1 spaces. */
    parm_left_cursor("cub", "LE"),
    /** Move cursor left #1 spaces in micro mode. */
    parm_left_micro("mcub", "Zg"),
    /** Move cursor right #1 spaces. */
    parm_right_cursor("cuf", "RI"),
    /** Move cursor right #1 spaces in micro mode. */
    parm_right_micro("mcuf", "Zh"),
    /** Scroll backward #1 lines. */
    parm_rindex("rin", "SR"),
    /** Move cursor up #1 lines. */
    parm_up_cursor("cuu", "UP"),
    /** Move cursor up #1 lines in micro mode. */
    parm_up_micro("mcuu", "Zi"),
    /** Program function key #1 to type string #2. */
    pkey_key("pfkey", "pk"),
    /** Program function key #1 to execute string #2. */
    pkey_local("pfloc", "pl"),
    /** Program function key #1 to transmit string #2. */
    pkey_xmit("pfx", "px"),
    /** Program label #1 to show string #2. */
    plab_norm("pln", "pn"),
    /** Print contents of screen. */
    print_screen("mc0", "ps"),
    /** Turn on printer for #1 bytes. */
    prtr_non("mc5p", "pO"),
    /** Turn off printer. */
    prtr_off("mc4", "pf"),
    /** Turn on printer. */
    prtr_on("mc5", "po"),
    /** Select pulse dialing. */
    pulse("pulse", "PU"),
    /** Dial number #1 without detection. */
    quick_dial("qdial", "QD"),
    /** Remove clock. */
    remove_clock("rmclk", "RC"),
    /** Repeat character #1 #2 times. */
    repeat_char("rep", "rp"),
    /** Send next input character. */
    req_for_input("rfi", "RF"),
    /** Reset string 1. */
    reset_1string("rs1", "r1"),
    /** Reset string 2. */
    reset_2string("rs2", "r2"),
    /** Reset string 3. */
    reset_3string("rs3", "r3"),
    /** Name of reset file. */
    reset_file("rf", "rf"),
    /** Restore cursor to position of last save. */
    restore_cursor("rc", "rc"),
    /** Move cursor to row #1. */
    row_address("vpa", "cv"),
    /** Save current cursor position. */
    save_cursor("sc", "sc"),
    /** Scroll text up. */
    scroll_forward("ind", "sf"),
    /** Scroll text down. */
    scroll_reverse("ri", "sr"),
    /** Select character set. */
    select_char_set("scs", "Zj"),
    /** Define video attributes. */
    set_attributes("sgr", "sa"),
    /** Set background color. */
    set_background("setb", "Sb"),
    /** Set bottom margin at current line. */
    set_bottom_margin("smgb", "Zk"),
    /** Set bottom margin at line #1 or #2 lines from bottom. */
    set_bottom_margin_parm("smgbp", "Zl"),
    /** Set clock at row #1, column #2. */
    set_clock("sclk", "SC"),
    /** Set current color pair to #1. */
    set_color_pair("scp", "sp"),
    /** Set foreground color. */
    set_foreground("setf", "Sf"),
    /** Set left margin at current column. */
    set_left_margin("smgl", "ML"),
    /** Set left (right) margin at column #1. */
    set_left_margin_parm("smglp", "Zm"),
    /** Set right margin at current column. */
    set_right_margin("smgr", "MR"),
    /** Set right margin at column #1. */
    set_right_margin_parm("smgrp", "Zn"),
    /** Set a tab in every row, current columns. */
    set_tab("hts", "st"),
    /** Set top margin at current line. */
    set_top_margin("smgt", "Zo"),
    /** Set top (bottom) margin at row #1. */
    set_top_margin_parm("smgtp", "Zp"),
    /** Current window is lines #1-#2 cols #3-#4. */
    set_window("wind", "wi"),
    /** Start printing bit image graphics. */
    start_bit_image("sbim", "Zq"),
    /** Start character set definition. */
    start_char_set_def("scsd", "Zr"),
    /** Stop printing bit image graphics. */
    stop_bit_image("rbim", "Zs"),
    /** End definition of character set. */
    stop_char_set_def("rcsd", "Zt"),
    /** List of subscriptable characters. */
    subscript_characters("subcs", "Zu"),
    /** List of superscriptable characters. */
    superscript_characters("supcs", "Zv"),
    /** Tab to next 8-space hardware tab stop. */
    tab("ht", "ta"),
    /** Printing any of these characters causes carriage return. */
    these_cause_cr("docr", "Zw"),
    /** Move to status line, column #1. */
    to_status_line("tsl", "ts"),
    /** Select touch tone dialing. */
    tone("tone", "TO"),
    /** Underline character and move past it. */
    underline_char("uc", "uc"),
    /** Move cursor up half a line. */
    up_half_line("hu", "hu"),
    /** User string 0. */
    user0("u0", "u0"),
    /** User string 1. */
    user1("u1", "u1"),
    /** User string 2. */
    user2("u2", "u2"),
    /** User string 3. */
    user3("u3", "u3"),
    /** User string 4. */
    user4("u4", "u4"),
    /** User string 5. */
    user5("u5", "u5"),
    /** User string 6. */
    user6("u6", "u6"),
    /** User string 7. */
    user7("u7", "u7"),
    /** User string 8. */
    user8("u8", "u8"),
    /** User string 9. */
    user9("u9", "u9"),
    /** Wait for dial-tone. */
    wait_tone("wait", "WA"),
    /** XOFF character. */
    xoff_character("xoffc", "XF"),
    /** XON character. */
    xon_character("xonc", "XN"),
    /** No motion for subsequent characters. */
    zero_motion("zerom", "Zx"),
    /** Alternate escape for scancode emulation. */
    alt_scancode_esc("scesa", "S8"),
    /** Move cursor to beginning of same row in bit image. */
    bit_image_carriage_return("bicr", "Yv"),
    /** Move to next row of the bit image. */
    bit_image_newline("binel", "Zz"),
    /** Repeat bit image cell #1 #2 times. */
    bit_image_repeat("birep", "Xy"),
    /** Produce #1'th item from list of character set names. */
    char_set_names("csnm", "Zy"),
    /** Initialize sequence for multiple codesets. */
    code_set_init("csin", "ci"),
    /** Give name for color #1. */
    color_names("colornm", "Yw"),
    /** Define rectangular bit image region. */
    define_bit_image_region("defbi", "Yx"),
    /** Indicate language/codeset support. */
    device_type("devt", "dv"),
    /** Display PC character #1. */
    display_pc_char("dispc", "S1"),
    /** End a bit image region. */
    end_bit_image_region("endbi", "Yy"),
    /** Enter PC charset mode. */
    enter_pc_charset_mode("smpch", "S2"),
    /** Enter scancode mode. */
    enter_scancode_mode("smsc", "S4"),
    /** Exit PC charset mode. */
    exit_pc_charset_mode("rmpch", "S3"),
    /** Exit scancode mode. */
    exit_scancode_mode("rmsc", "S5"),
    /** Curses should get button events. */
    get_mouse("getm", "Gm"),
    /** Mouse event has occurred. */
    key_mouse("kmous", "Km"),
    /** Mouse status information. */
    mouse_info("minfo", "Mi"),
    /** PC terminal options. */
    pc_term_options("pctrm", "S6"),
    /** Program function key #1 to transmit #2 and show #3. */
    pkey_plab("pfxl", "xl"),
    /** Request mouse position. */
    req_mouse_pos("reqmp", "RQ"),
    /** Escape for scancode emulation. */
    scancode_escape("scesc", "S7"),
    /** Shift to code set 0 (EUC set 0, ASCII). */
    set0_des_seq("s0ds", "s0"),
    /** Shift to code set 1. */
    set1_des_seq("s1ds", "s1"),
    /** Shift to code set 2. */
    set2_des_seq("s2ds", "s2"),
    /** Shift to code set 3. */
    set3_des_seq("s3ds", "s3"),
    /** Set ANSI background color to #1. */
    set_a_background("setab", "AB"),
    /** Set ANSI foreground color to #1. */
    set_a_foreground("setaf", "AF"),
    /** Change to ribbon color #1. */
    set_color_band("setcolor", "Yz"),
    /** Set both left and right margins to #1, #2. */
    set_lr_margin("smglr", "ML"),
    /** Set page length to #1 lines. */
    set_page_length("slines", "YZ"),
    /** Set both top and bottom margins to #1, #2. */
    set_tb_margin("smgtb", "MT"),
    /** Enter horizontal highlight mode. */
    enter_horizontal_hl_mode("ehhlm", "Xh"),
    /** Enter left highlight mode. */
    enter_left_hl_mode("elhlm", "Xl"),
    /** Enter low highlight mode. */
    enter_low_hl_mode("elohlm", "Xo"),
    /** Enter right highlight mode. */
    enter_right_hl_mode("erhlm", "Xr"),
    /** Enter top highlight mode. */
    enter_top_hl_mode("ethlm", "Xt"),
    /** Enter vertical highlight mode. */
    enter_vertical_hl_mode("evhlm", "Xv"),
    /** Set ANSI attributes. */
    set_a_attributes("sgr1", "sA"),
    /** Set page length to #1 inches. */
    set_pglen_inch("slength", "sL");

    /** The terminfo capability name. */
    private final String name;
    /** The short termcap code for this capability. */
    private final String cap;

    /**
     * Creates a new capability with the specified terminfo name and termcap code.
     *
     * @param name the terminfo capability name
     * @param cap the short termcap code
     */
    Capability(String name, String cap) {
        this.name = name;
        this.cap = cap;
    }

    /**
     * Returns the terminfo capability name.
     *
     * @return the terminfo name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the short termcap code for this capability.
     *
     * @return the termcap code
     */
    public String getCap() {
        return cap;
    }

    /**
     * Finds a capability by its name, terminfo name, or termcap code.
     *
     * @param name the name to search for (can be enum name, terminfo name, or termcap code)
     * @return the matching capability, or null if not found
     */
    public static Capability byName(String name) {
        for (Capability c : values()) {
            if (c.name().equals(name) || c.getName().equals(name) || c.getCap().equals(name))
                return c;
        }
        return null;
    }
}
