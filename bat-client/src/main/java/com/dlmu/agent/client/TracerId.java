/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dlmu.agent.client;

import com.dlmu.agent.common.conf.DTraceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TreeSet;

/**
 * <p>The HTrace dTraceClient ID.</p>
 * <p>
 * <p>HTrace dTraceClient IDs are created from format strings.
 * Format strings contain variables which the TracerId class will
 * replace with the correct values at runtime.</p>
 * <p>
 * <ul>
 * <li>%{tname}: the dTraceClient name supplied when creating the Tracer.</li>
 * <li>%{pname}: the process name obtained from the JVM.</li>
 * <li>%{ip}: will be replaced with an ip address.</li>
 * <li>%{pid}: the numerical process ID from the operating system.</li>
 * </ul>
 * <p>
 * <p>For example, the string "%{pname}/%{ip}" will be replaced with something
 * like: DataNode/192.168.0.1, assuming that the process' name is DataNode
 * and its IP address is 192.168.0.1.</p>
 * <p>
 * ID strings can contain backslashes as escapes.
 * For example, "\a" will map to "a".  "\%{ip}" will map to the literal
 * string "%{ip}", not the IP address.  A backslash itself can be escaped by a
 * preceding backslash.
 */
public final class TracerId {
    private static final Logger logger = LoggerFactory.getLogger(TracerId.class);

    /**
     * The configuration key to use for process id
     */
    public static final String TRACER_ID_KEY = "dTraceClient.id";

    /**
     * The default dTraceClient ID to use if no other ID is configured.
     */
    private static final String DEFAULT_TRACER_ID = "%{tname}/%{ip}";

    private final String tracerName;

    private final String tracerId;

    public TracerId(DTraceConfiguration conf, String tracerName) {
        this.tracerName = tracerName;
        String fmt = conf.get(TRACER_ID_KEY, DEFAULT_TRACER_ID);
        StringBuilder bld = new StringBuilder();
        StringBuilder varBld = null;
        boolean escaping = false;
        int varSeen = 0;
        for (int i = 0, len = fmt.length(); i < len; i++) {
            char c = fmt.charAt(i);
            if (c == '\\') {
                if (!escaping) {
                    escaping = true;
                    continue;
                }
            }
            switch (varSeen) {
                case 0:
                    if (c == '%') {
                        if (!escaping) {
                            varSeen = 1;
                            continue;
                        }
                    }
                    escaping = false;
                    varSeen = 0;
                    bld.append(c);
                    break;
                case 1:
                    if (c == '{') {
                        if (!escaping) {
                            varSeen = 2;
                            varBld = new StringBuilder();
                            continue;
                        }
                    }
                    escaping = false;
                    varSeen = 0;
                    bld.append("%").append(c);
                    break;
                default:
                    if (c == '}') {
                        if (!escaping) {
                            String var = varBld.toString();
                            bld.append(processShellVar(var));
                            varBld = null;
                            varSeen = 0;
                            continue;
                        }
                    }
                    escaping = false;
                    varBld.append(c);
                    varSeen++;
                    break;
            }
        }
        if (varSeen > 0) {
            logger.warn("Unterminated process ID substitution variable at the end " +
                    "of format string " + fmt);
        }
        this.tracerId = bld.toString();
        if (logger.isTraceEnabled()) {
            logger.trace("ProcessID(fmt=" + fmt + "): computed process ID of \"" +
                    this.tracerId + "\"");
        }
    }

    private String processShellVar(String var) {
        if (var.equals("tname")) {
            return tracerName;
        } else if (var.equals("pname")) {
            return getProcessName();
        } else if (var.equals("ip")) {
            return getBestIpString();
        } else if (var.equals("pid")) {
            return Long.valueOf(getOsPid()).toString();
        } else {
            logger.warn("unknown ProcessID variable " + var);
            return "";
        }
    }

    static String getProcessName() {
        String cmdLine = System.getProperty("sun.java.command");
        if (cmdLine != null && !cmdLine.isEmpty()) {
            String fullClassName = cmdLine.split("\\s+")[0];
            String[] classParts = fullClassName.split("\\.");
            cmdLine = classParts[classParts.length - 1];
        }
        return (cmdLine == null || cmdLine.isEmpty()) ? "Unknown" : cmdLine;
    }

    /**
     * <p>Get the best IP address that represents this node.</p>
     * <p>
     * This is complicated since nodes can have multiple network interfaces,
     * and each network interface can have multiple IP addresses.  What we're
     * looking for here is an IP address that will serve to identify this node
     * to HTrace.  So we prefer site-local addresess (i.e. private ones on the
     * LAN) to publicly routable interfaces.  If there are multiple addresses
     * to choose from, we select the one which comes first in textual sort
     * order.  This should ensure that we at least consistently call each node
     * by a single name.
     */
    static String getBestIpString() {
        Enumeration<NetworkInterface> ifaces;
        try {
            ifaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            logger.error("Error getting network interfaces", e);
            return "127.0.0.1";
        }
        TreeSet<String> siteLocalCandidates = new TreeSet<String>();
        TreeSet<String> candidates = new TreeSet<String>();
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            for (Enumeration<InetAddress> addrs =
                 iface.getInetAddresses(); addrs.hasMoreElements(); ) {
                InetAddress addr = addrs.nextElement();
                if (!addr.isLoopbackAddress()) {
                    if (addr.isSiteLocalAddress()) {
                        siteLocalCandidates.add(addr.getHostAddress());
                    } else {
                        candidates.add(addr.getHostAddress());
                    }
                }
            }
        }
        if (!siteLocalCandidates.isEmpty()) {
            return siteLocalCandidates.first();
        }
        if (!candidates.isEmpty()) {
            return candidates.first();
        }
        return "127.0.0.1";
    }

    /**
     * <p>Get the process id from the operating system.</p>
     * <p>
     * Unfortunately, there is no simple method to get the process id in Java.
     * The approach we take here is to use the shell method (see
     * {TracerId#getOsPidFromShellPpid}) unless we are on Windows, where the
     * shell is not available.  On Windows, we use
     * {TracerId#getOsPidFromManagementFactory}, which depends on some
     * undocumented features of the JVM, but which doesn't require a shell.
     */
    static long getOsPid() {
        if ((System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)).
                contains("windows")) {
            return getOsPidFromManagementFactory();
        } else {
            return getOsPidFromShellPpid();
        }
    }

    /**
     * <p>Get the process ID by executing a shell and printing the PPID (parent
     * process ID).</p>
     * <p>
     * This method of getting the process ID doesn't depend on any undocumented
     * features of the virtual machine, and should work on almost any UNIX
     * operating system.
     */
    private static long getOsPidFromShellPpid() {
        Process p = null;
        StringBuilder sb = new StringBuilder();
        try {
            p = new ProcessBuilder("/usr/bin/env", "sh", "-c", "echo $PPID").
                    redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
            int exitVal = p.waitFor();
            if (exitVal != 0) {
                throw new IOException("Process exited with error code " +
                        Integer.valueOf(exitVal).toString());
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while getting operating system pid from " +
                    "the shell.", e);
            return 0L;
        } catch (IOException e) {
            logger.error("Error getting operating system pid from the shell.", e);
            return 0L;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
        try {
            return Long.parseLong(sb.toString());
        } catch (NumberFormatException e) {
            logger.error("Error parsing operating system pid from the shell.", e);
            return 0L;
        }
    }

    /**
     * <p>Get the process ID by looking at the name of the managed bean for the
     * runtime system of the Java virtual machine.</p>
     * <p>
     * Although this is undocumented, in the Oracle JVM this name is of the form
     * [OS_PROCESS_ID]@[HOSTNAME].
     */
    private static long getOsPidFromManagementFactory() {
        try {
            return Long.parseLong(ManagementFactory.getRuntimeMXBean().
                    getName().split("@")[0]);
        } catch (NumberFormatException e) {
            logger.error("Failed to get the operating system process ID from the name " +
                    "of the managed bean for the JVM.", e);
            return 0L;
        }
    }

    public String get() {
        return tracerId;
    }
}
