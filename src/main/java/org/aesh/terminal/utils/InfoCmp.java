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
package org.aesh.terminal.utils;

import org.aesh.tty.Capability;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infocmp helper methods.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 */
public final class InfoCmp {

    private static final Map<String, String> CAPS = new HashMap<>();

    private InfoCmp() {
    }

    public static void setDefaultInfoCmp(String terminal, String caps) {
        CAPS.putIfAbsent(terminal, caps);
    }

    public static String getInfoCmp(
            String terminal
    ) throws IOException, InterruptedException {
        String caps = CAPS.get(terminal);
        if (caps == null) {
            Process p = new ProcessBuilder(OSUtils.INFOCMP_COMMAND, terminal).start();
            caps = ExecHelper.waitAndCapture(p);
            CAPS.put(terminal, caps);
        }
        return caps;
    }

    public static void parseInfoCmp(
            String capabilities,
            Set<Capability> bools,
            Map<Capability, Integer> ints,
            Map<Capability, String> strings
    ) {
        String[] lines = capabilities.split("\n");
        for (int i = 1; i < lines.length; i++) {
            Matcher m = Pattern.compile("\\s*(([^,]|\\\\,)+)\\s*[,$]").matcher(lines[i]);
            while (m.find()) {
                String cap = m.group(1);
                if (cap.contains("#")) {
                    int index = cap.indexOf('#');
                    String key = cap.substring(0, index);
                    String val = cap.substring(index + 1);
                    int iVal = Integer.valueOf(val);
                    Capability c = Capability.byName(key);
                    if (c != null) {
                        ints.put(c, iVal);
                    }
                } else if (cap.contains("=")) {
                    int index = cap.indexOf('=');
                    String key = cap.substring(0, index);
                    String val = cap.substring(index + 1);
                    Capability c = Capability.byName(key);
                    if (c != null) {
                        strings.put(c, val);
                    }
                } else {
                    Capability c = Capability.byName(cap);
                    if (c != null) {
                        bools.add(c);
                    }
                }
            }
        }
    }

    public static final String WINDOWS_CAPS =
            "windows|windows console compatibility,\n" +
                    "\tam, mc5i, mir, msgr,\n" +
                    "\tcolors#8, cols#80, it#8, lines#24, ncv#3, pairs#64,\n" +
                    "\tbel=^G, blink=\\E[5m, bold=\\E[1m, cbt=\\E[Z, clear=\\E[H\\E[J,\n" +
                    "\tcr=^M, cub=\\E[%p1%dD, cub1=\\E[D, cud=\\E[%p1%dB, cud1=\\E[B,\n" +
                    "\tcuf=\\E[%p1%dC, cuf1=\\E[C, cup=\\E[%i%p1%d;%p2%dH,\n" +
                    "\tcuu=\\E[%p1%dA, cuu1=\\E[A,\n" +
                    "\tdl=\\E[%p1%dM, dl1=\\E[M, ech=\\E[%p1%dX,\n" +
                    "\tel1=\\E[1K, home=\\E[H, hpa=\\E[%i%p1%dG,\n" +
                    "\tind=^J,\n" +
                    "\tinvis=\\E[8m, kbs=^H, kcbt=\\E[Z,\n" +
                    "\tkcub1=\\EOD, kcud1=\\EOB, kcuf1=\\EOC, kcuu1=\\EOA,\n" +
                    "\tkhome=\\E[H, \n" +
                    "\top=\\E[39;49m,\n" +
                    "\trev=\\E[7m,\n" +
                    "\trmacs=\\E[10m, rmpch=\\E[10m, rmso=\\E[m, rmul=\\E[m,\n" +
                    "\tsetab=\\E[4%p1%dm, setaf=\\E[3%p1%dm,\n" +
                    "\tsgr=\\E[0;10%?%p1%t;7%;%?%p2%t;4%;%?%p3%t;7%;%?%p4%t;5%;%?%p6%t;1%;%?%p7%t;8%;%?%p9%t;11%;m,\n" +
                    "\tsgr0=\\E[0;10m,\n" +
                    "\tsmso=\\E[7m,\n" +
                    "\tsmul=\\E[4m,\n" +
                    "\tkdch1=\\E[3~, kich1=\\E[2~, kend=\\E[4~, knp=\\E[6~, kpp=\\E[5~,\n" +
                    "\tkf1=\\EOP, kf2=\\EOQ, kf3=\\EOR, kf4=\\EOS, kf5=\\E[15~, kf6=\\E[17~,\n" +
                    "\tkf7=\\E[18~, kf8=\\E[19~, kf9=\\E[20~, kf10=\\E[21~, kf11=\\E[23~, kf12=\\E[24~,\n";

    public static final String ANSI_CAPS =
            "ansi|ansi/pc-term compatible with color,\n" +
                    "\tam, mc5i, mir, msgr,\n" +
                    "\tcolors#8, cols#80, it#8, lines#24, ncv#3, pairs#64,\n" +
                    "\tacsc=+\\020\\,\\021-\\030.^Y0\\333`\\004a\\261f\\370g\\361h\\260j\\331k\\277l\\332m\\300n\\305o~p\\304q\\304r\\304s_t\\303u\\264v\\301w\\302x\\263y\\363z\\362{\\343|\\330}\\234~\\376,\n" +
                    "\tbel=^G, blink=\\E[5m, bold=\\E[1m, cbt=\\E[Z, clear=\\E[H\\E[J,\n" +
                    "\tcr=^M, cub=\\E[%p1%dD, cub1=\\E[D, cud=\\E[%p1%dB, cud1=\\E[B,\n" +
                    "\tcuf=\\E[%p1%dC, cuf1=\\E[C, cup=\\E[%i%p1%d;%p2%dH,\n" +
                    "\tcuu=\\E[%p1%dA, cuu1=\\E[A, dch=\\E[%p1%dP, dch1=\\E[P,\n" +
                    "\tdl=\\E[%p1%dM, dl1=\\E[M, ech=\\E[%p1%dX, ed=\\E[J, el=\\E[K,\n" +
                    "\tel1=\\E[1K, home=\\E[H, hpa=\\E[%i%p1%dG, ht=\\E[I, hts=\\EH,\n" +
                    "\tich=\\E[%p1%d@, il=\\E[%p1%dL, il1=\\E[L, ind=^J,\n" +
                    "\tindn=\\E[%p1%dS, invis=\\E[8m, kbs=^H, kcbt=\\E[Z, kcub1=\\E[D,\n" +
                    "\tkcud1=\\E[B, kcuf1=\\E[C, kcuu1=\\E[A, khome=\\E[H, kich1=\\E[L,\n" +
                    "\tmc4=\\E[4i, mc5=\\E[5i, nel=\\r\\E[S, op=\\E[39;49m,\n" +
                    "\trep=%p1%c\\E[%p2%{1}%-%db, rev=\\E[7m, rin=\\E[%p1%dT,\n" +
                    "\trmacs=\\E[10m, rmpch=\\E[10m, rmso=\\E[m, rmul=\\E[m,\n" +
                    "\ts0ds=\\E(B, s1ds=\\E)B, s2ds=\\E*B, s3ds=\\E+B,\n" +
                    "\tsetab=\\E[4%p1%dm, setaf=\\E[3%p1%dm,\n" +
                    "\tsgr=\\E[0;10%?%p1%t;7%;%?%p2%t;4%;%?%p3%t;7%;%?%p4%t;5%;%?%p6%t;1%;%?%p7%t;8%;%?%p9%t;11%;m,\n" +
                    "\tsgr0=\\E[0;10m, smacs=\\E[11m, smpch=\\E[11m, smso=\\E[7m,\n" +
                    "\tsmul=\\E[4m, tbc=\\E[2g, u6=\\E[%i%d;%dR, u7=\\E[6n,\n" +
                    "\tu8=\\E[?%[;0123456789]c, u9=\\E[c, vpa=\\E[%i%p1%dd,";

    public static final String XTERM_CAPS =
            "#\tReconstructed via infocmp from file: /usr/share/terminfo/78/xterm\n" +
                    "xterm|xterm terminal emulator (X Window System),\n" +
                    "\tam, bce, km, mc5i, mir, msgr, npc, xenl,\n" +
                    "\tcolors#8, cols#80, it#8, lines#24, pairs#64,\n" +
                    "\tacsc=``aaffggiijjkkllmmnnooppqqrrssttuuvvwwxxyyzz{{||}}~~,\n" +
                    "\tbel=^G, blink=\\E[5m, bold=\\E[1m, cbt=\\E[Z, civis=\\E[?25l,\n" +
                    "\tclear=\\E[H\\E[2J, cnorm=\\E[?12l\\E[?25h, cr=^M,\n" +
                    "\tcsr=\\E[%i%p1%d;%p2%dr, cub=\\E[%p1%dD, cub1=^H,\n" +
                    "\tcud=\\E[%p1%dB, cud1=^J, cuf=\\E[%p1%dC, cuf1=\\E[C,\n" +
                    "\tcup=\\E[%i%p1%d;%p2%dH, cuu=\\E[%p1%dA, cuu1=\\E[A,\n" +
                    "\tcvvis=\\E[?12;25h, dch=\\E[%p1%dP, dch1=\\E[P, dl=\\E[%p1%dM,\n" +
                    "\tdl1=\\E[M, ech=\\E[%p1%dX, ed=\\E[J, el=\\E[K, el1=\\E[1K,\n" +
                    "\tflash=\\E[?5h$<100/>\\E[?5l, home=\\E[H, hpa=\\E[%i%p1%dG,\n" +
                    "\tht=^I, hts=\\EH, ich=\\E[%p1%d@, il=\\E[%p1%dL, il1=\\E[L,\n" +
                    "\tind=^J, indn=\\E[%p1%dS, invis=\\E[8m,\n" +
                    "\tis2=\\E[!p\\E[?3;4l\\E[4l\\E>, kDC=\\E[3;2~, kEND=\\E[1;2F,\n" +
                    "\tkHOM=\\E[1;2H, kIC=\\E[2;2~, kLFT=\\E[1;2D, kNXT=\\E[6;2~,\n" +
                    "\tkPRV=\\E[5;2~, kRIT=\\E[1;2C, kb2=\\EOE, kbs=^H, kcbt=\\E[Z,\n" +
                    "\tkcub1=\\EOD, kcud1=\\EOB, kcuf1=\\EOC, kcuu1=\\EOA,\n" +
                    "\tkdch1=\\E[3~, kend=\\EOF, kent=\\EOM, kf1=\\EOP, kf10=\\E[21~,\n" +
                    "\tkf11=\\E[23~, kf12=\\E[24~, kf13=\\E[1;2P, kf14=\\E[1;2Q,\n" +
                    "\tkf15=\\E[1;2R, kf16=\\E[1;2S, kf17=\\E[15;2~, kf18=\\E[17;2~,\n" +
                    "\tkf19=\\E[18;2~, kf2=\\EOQ, kf20=\\E[19;2~, kf21=\\E[20;2~,\n" +
                    "\tkf22=\\E[21;2~, kf23=\\E[23;2~, kf24=\\E[24;2~,\n" +
                    "\tkf25=\\E[1;5P, kf26=\\E[1;5Q, kf27=\\E[1;5R, kf28=\\E[1;5S,\n" +
                    "\tkf29=\\E[15;5~, kf3=\\EOR, kf30=\\E[17;5~, kf31=\\E[18;5~,\n" +
                    "\tkf32=\\E[19;5~, kf33=\\E[20;5~, kf34=\\E[21;5~,\n" +
                    "\tkf35=\\E[23;5~, kf36=\\E[24;5~, kf37=\\E[1;6P, kf38=\\E[1;6Q,\n" +
                    "\tkf39=\\E[1;6R, kf4=\\EOS, kf40=\\E[1;6S, kf41=\\E[15;6~,\n" +
                    "\tkf42=\\E[17;6~, kf43=\\E[18;6~, kf44=\\E[19;6~,\n" +
                    "\tkf45=\\E[20;6~, kf46=\\E[21;6~, kf47=\\E[23;6~,\n" +
                    "\tkf48=\\E[24;6~, kf49=\\E[1;3P, kf5=\\E[15~, kf50=\\E[1;3Q,\n" +
                    "\tkf51=\\E[1;3R, kf52=\\E[1;3S, kf53=\\E[15;3~, kf54=\\E[17;3~,\n" +
                    "\tkf55=\\E[18;3~, kf56=\\E[19;3~, kf57=\\E[20;3~,\n" +
                    "\tkf58=\\E[21;3~, kf59=\\E[23;3~, kf6=\\E[17~, kf60=\\E[24;3~,\n" +
                    "\tkf61=\\E[1;4P, kf62=\\E[1;4Q, kf63=\\E[1;4R, kf7=\\E[18~,\n" +
                    "\tkf8=\\E[19~, kf9=\\E[20~, khome=\\EOH, kich1=\\E[2~,\n" +
                    "\tkind=\\E[1;2B, kmous=\\E[M, knp=\\E[6~, kpp=\\E[5~,\n" +
                    "\tkri=\\E[1;2A, mc0=\\E[i, mc4=\\E[4i, mc5=\\E[5i, meml=\\El,\n" +
                    "\tmemu=\\Em, op=\\E[39;49m, rc=\\E8, rev=\\E[7m, ri=\\EM,\n" +
                    "\trin=\\E[%p1%dT, rmacs=\\E(B, rmam=\\E[?7l, rmcup=\\E[?1049l,\n" +
                    "\trmir=\\E[4l, rmkx=\\E[?1l\\E>, rmm=\\E[?1034l, rmso=\\E[27m,\n" +
                    "\trmul=\\E[24m, rs1=\\Ec, rs2=\\E[!p\\E[?3;4l\\E[4l\\E>, sc=\\E7,\n" +
                    "\tsetab=\\E[4%p1%dm, setaf=\\E[3%p1%dm,\n" +
                    "\tsetb=\\E[4%?%p1%{1}%=%t4%e%p1%{3}%=%t6%e%p1%{4}%=%t1%e%p1%{6}%=%t3%e%p1%d%;m,\n" +
                    "\tsetf=\\E[3%?%p1%{1}%=%t4%e%p1%{3}%=%t6%e%p1%{4}%=%t1%e%p1%{6}%=%t3%e%p1%d%;m,\n" +
                    "\tsgr=%?%p9%t\\E(0%e\\E(B%;\\E[0%?%p6%t;1%;%?%p2%t;4%;%?%p1%p3%|%t;7%;%?%p4%t;5%;%?%p7%t;8%;m,\n" +
                    "\tsgr0=\\E(B\\E[m, smacs=\\E(0, smam=\\E[?7h, smcup=\\E[?1049h,\n" +
                    "\tsmir=\\E[4h, smkx=\\E[?1h\\E=, smm=\\E[?1034h, smso=\\E[7m,\n" +
                    "\tsmul=\\E[4m, tbc=\\E[3g, u6=\\E[%i%d;%dR, u7=\\E[6n,\n" +
                    "\tu8=\\E[?1;2c, u9=\\E[c, vpa=\\E[%i%p1%dd,\n";

    public static final String XTERM_256COLOR_CAPS =
            "xterm-256color|xterm with 256 colors,\n" +
                    "\tam, bce, ccc, km, mc5i, mir, msgr, npc, xenl,\n" +
                    "\tcolors#256, cols#80, it#8, lines#24, pairs#32767,\n" +
                    "\tacsc=``aaffggiijjkkllmmnnooppqqrrssttuuvvwwxxyyzz{{||}}~~,\n" +
                    "\tbel=^G, blink=\\E[5m, bold=\\E[1m, cbt=\\E[Z, civis=\\E[?25l,\n" +
                    "\tclear=\\E[H\\E[2J, cnorm=\\E[?12l\\E[?25h, cr=^M,\n" +
                    "\tcsr=\\E[%i%p1%d;%p2%dr, cub=\\E[%p1%dD, cub1=^H,\n" +
                    "\tcud=\\E[%p1%dB, cud1=^J, cuf=\\E[%p1%dC, cuf1=\\E[C,\n" +
                    "\tcup=\\E[%i%p1%d;%p2%dH, cuu=\\E[%p1%dA, cuu1=\\E[A,\n" +
                    "\tcvvis=\\E[?12;25h, dch=\\E[%p1%dP, dch1=\\E[P, dl=\\E[%p1%dM,\n" +
                    "\tdl1=\\E[M, ech=\\E[%p1%dX, ed=\\E[J, el=\\E[K, el1=\\E[1K,\n" +
                    "\tflash=\\E[?5h$<100/>\\E[?5l, home=\\E[H, hpa=\\E[%i%p1%dG,\n" +
                    "\tht=^I, hts=\\EH, ich=\\E[%p1%d@, il=\\E[%p1%dL, il1=\\E[L,\n" +
                    "\tind=^J, indn=\\E[%p1%dS,\n" +
                    "\tinitc=\\E]4;%p1%d;rgb\\:%p2%{255}%*%{1000}%/%2.2X/%p3%{255}%*%{1000}%/%2.2X/%p4%{255}%*%{1000}%/%2.2X\\E\\\\,\n" +
                    "\tinvis=\\E[8m, is2=\\E[!p\\E[?3;4l\\E[4l\\E>, kDC=\\E[3;2~,\n" +
                    "\tkEND=\\E[1;2F, kHOM=\\E[1;2H, kIC=\\E[2;2~, kLFT=\\E[1;2D,\n" +
                    "\tkNXT=\\E[6;2~, kPRV=\\E[5;2~, kRIT=\\E[1;2C, kb2=\\EOE, kbs=^H,\n" +
                    "\tkcbt=\\E[Z, kcub1=\\EOD, kcud1=\\EOB, kcuf1=\\EOC, kcuu1=\\EOA,\n" +
                    "\tkdch1=\\E[3~, kend=\\EOF, kent=\\EOM, kf1=\\EOP, kf10=\\E[21~,\n" +
                    "\tkf11=\\E[23~, kf12=\\E[24~, kf13=\\E[1;2P, kf14=\\E[1;2Q,\n" +
                    "\tkf15=\\E[1;2R, kf16=\\E[1;2S, kf17=\\E[15;2~, kf18=\\E[17;2~,\n" +
                    "\tkf19=\\E[18;2~, kf2=\\EOQ, kf20=\\E[19;2~, kf21=\\E[20;2~,\n" +
                    "\tkf22=\\E[21;2~, kf23=\\E[23;2~, kf24=\\E[24;2~,\n" +
                    "\tkf25=\\E[1;5P, kf26=\\E[1;5Q, kf27=\\E[1;5R, kf28=\\E[1;5S,\n" +
                    "\tkf29=\\E[15;5~, kf3=\\EOR, kf30=\\E[17;5~, kf31=\\E[18;5~,\n" +
                    "\tkf32=\\E[19;5~, kf33=\\E[20;5~, kf34=\\E[21;5~,\n" +
                    "\tkf35=\\E[23;5~, kf36=\\E[24;5~, kf37=\\E[1;6P, kf38=\\E[1;6Q,\n" +
                    "\tkf39=\\E[1;6R, kf4=\\EOS, kf40=\\E[1;6S, kf41=\\E[15;6~,\n" +
                    "\tkf42=\\E[17;6~, kf43=\\E[18;6~, kf44=\\E[19;6~,\n" +
                    "\tkf45=\\E[20;6~, kf46=\\E[21;6~, kf47=\\E[23;6~,\n" +
                    "\tkf48=\\E[24;6~, kf49=\\E[1;3P, kf5=\\E[15~, kf50=\\E[1;3Q,\n" +
                    "\tkf51=\\E[1;3R, kf52=\\E[1;3S, kf53=\\E[15;3~, kf54=\\E[17;3~,\n" +
                    "\tkf55=\\E[18;3~, kf56=\\E[19;3~, kf57=\\E[20;3~,\n" +
                    "\tkf58=\\E[21;3~, kf59=\\E[23;3~, kf6=\\E[17~, kf60=\\E[24;3~,\n" +
                    "\tkf61=\\E[1;4P, kf62=\\E[1;4Q, kf63=\\E[1;4R, kf7=\\E[18~,\n" +
                    "\tkf8=\\E[19~, kf9=\\E[20~, khome=\\EOH, kich1=\\E[2~,\n" +
                    "\tkind=\\E[1;2B, kmous=\\E[M, knp=\\E[6~, kpp=\\E[5~,\n" +
                    "\tkri=\\E[1;2A, mc0=\\E[i, mc4=\\E[4i, mc5=\\E[5i, meml=\\El,\n" +
                    "\tmemu=\\Em, op=\\E[39;49m, rc=\\E8, rev=\\E[7m, ri=\\EM,\n" +
                    "\trin=\\E[%p1%dT, rmacs=\\E(B, rmam=\\E[?7l, rmcup=\\E[?1049l,\n" +
                    "\trmir=\\E[4l, rmkx=\\E[?1l\\E>, rmm=\\E[?1034l, rmso=\\E[27m,\n" +
                    "\trmul=\\E[24m, rs1=\\Ec, rs2=\\E[!p\\E[?3;4l\\E[4l\\E>, sc=\\E7,\n" +
                    "\tsetab=\\E[%?%p1%{8}%<%t4%p1%d%e%p1%{16}%<%t10%p1%{8}%-%d%e48;5;%p1%d%;m,\n" +
                    "\tsetaf=\\E[%?%p1%{8}%<%t3%p1%d%e%p1%{16}%<%t9%p1%{8}%-%d%e38;5;%p1%d%;m,\n" +
                    "\tsgr=%?%p9%t\\E(0%e\\E(B%;\\E[0%?%p6%t;1%;%?%p2%t;4%;%?%p1%p3%|%t;7%;%?%p4%t;5%;%?%p7%t;8%;m,\n" +
                    "\tsgr0=\\E(B\\E[m, smacs=\\E(0, smam=\\E[?7h, smcup=\\E[?1049h,\n" +
                    "\tsmir=\\E[4h, smkx=\\E[?1h\\E=, smm=\\E[?1034h, smso=\\E[7m,\n" +
                    "\tsmul=\\E[4m, tbc=\\E[3g, u6=\\E[%i%d;%dR, u7=\\E[6n,\n" +
                    "\tu8=\\E[?1;2c, u9=\\E[c, vpa=\\E[%i%p1%dd,";

    static {
        setDefaultInfoCmp("ansi", ANSI_CAPS);
        setDefaultInfoCmp("xterm", XTERM_CAPS);
        setDefaultInfoCmp("xterm-256color", XTERM_256COLOR_CAPS);
        setDefaultInfoCmp("windows", WINDOWS_CAPS);
    }

}
