/*
 * This file is part of DRBD Management Console by LINBIT HA-Solutions GmbH
 * written by Rasto Levrinc.
 *
 * Copyright (C) 2009, LINBIT HA-Solutions GmbH.
 *
 * DRBD Management Console is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * DRBD Management Console is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with drbd; see the file COPYING.  If not, write to
 * the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package drbd.configs;

import java.util.Arrays;

/**
 * Here are commands for rhel version 6.
 */
public class DistResource_redhatenterpriseserver_6 extends
            java.util.ListResourceBundle {

    /** Get contents. */
    protected final Object[][] getContents() {
        return Arrays.copyOf(contents, contents.length);
    }

    /** Contents. */
    private static Object[][] contents = {
        /* distribution name that is used in the download url */
        {"distributiondir", "rhel6"},

        /* support */
        {"Support", "redhatenterpriseserver-6"},

        /* Heartbeat/Pacemaker clusterlabs */
        {"HbPmInst.install.text.1",
         "clusterlabs repo: 1.0.x/3.0.x" },

        {"HbPmInst.install.1",
         "wget -N -nd -P /etc/yum.repos.d/"
         + " http://www.clusterlabs.org/rpm/epel-6/clusterlabs.repo && "
         + " rpm -Uvh http://download.fedora.redhat.com/pub/epel/6/i386"
         + "/epel-release-6-4.noarch.rpm ; "
         + "yum -y -x resource-agents-3.* -x openais-1* -x openais-0.9*"
         + " -x heartbeat-2.1* install pacemaker.@ARCH@ heartbeat.@ARCH@"
         + "&& /sbin/chkconfig --add heartbeat"},

        /* Corosync/Openais/Pacemaker clusterlabs */
        {"PmInst.install.text.1",
         "clusterlabs repo: 1.0.x/1.2.x" },

        {"PmInst.install.1",
         "wget -N -nd -P /etc/yum.repos.d/"
         + " http://www.clusterlabs.org/rpm/epel-6/clusterlabs.repo && "
         + " rpm -Uvh http://download.fedora.redhat.com/pub/epel/6/i386"
         + "/epel-release-6-4.noarch.rpm ; "
         + "(yum -y -x resource-agents-3.* -x openais-1* -x openais-0.9*"
         + " -x heartbeat-2.1* install pacemaker.@ARCH@ corosync.@ARCH@"
         + " && if [ -e /etc/corosync/corosync.conf ]; then"
         + " mv /etc/corosync/corosync.conf /etc/corosync/corosync.conf.orig;"
         + " fi)"
         + " && (/sbin/chkconfig --del heartbeat;"
         + " /sbin/chkconfig --level 2345 corosync on"
         + " && /sbin/chkconfig --level 016 corosync off)"},

        /* Workaround, where aisexec hangs the gui if called directly. */
        {"Openais.startOpenais.i686",
         "echo '/etc/init.d/openais start'|at now"},
        {"Openais.reloadOpenais.i686",
         "echo '/etc/init.d/openais reload'|at now"},
    };
}