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

package drbd.gui.dialog;

import drbd.utilities.Tools;
import drbd.data.Host;
import drbd.gui.SpringUtilities;
import drbd.utilities.ExecCallback;
import drbd.gui.ProgressBar;

import javax.swing.SpringLayout;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import java.util.Map;
import java.util.HashMap;


/**
 * An implementation of an dialog, with log files.
 *
 * @author Rasto Levrinc
 * @version $Id$
 */
public class HostLogs extends ConfigDialog {
    /** Serial version UID. */
    private static final long serialVersionUID = 1L;
    /** Text area where the log is written to. */
    private final JTextArea logTextArea = new JTextArea();
    /** Host from which we get the log/logs. */
    private final Host host;

    /**
     * Prepares a new <code>HostLogs</code> object.
     */
    public HostLogs(final Host host) {
        super();
        this.host = host;
    }

    /**
     * Returns the host.
     */
    protected Host getHost() {
        return host;
    }

    /**
     * Returns a command name from the DistResource that gets the drbd log file.
     * "HostLogs.hbLog"
     */
    protected String logFileCommand() {
        return "Logs.hbLog";
    }

    /**
     * Returns a pattern that should be searched in the config file.
     * ("heartbeat:").
     */
    protected String grepPattern() {
        return "heartbeat:";
    }

    /**
     * Inits the dialog and starts the log command.
     */
    protected void initDialog() {
        super.initDialog();
        final Thread thread = new Thread(
            new Runnable() {
                public void run() {
                    getLog();
                }
            });
        thread.start();
    }

    /**
     * Exectures the log command greps the pattern and sets the log area with
     * the result.
     */
    protected void getLog() {
        final Map<String, String> replaceHash = new HashMap<String, String>();
        replaceHash.put("@GREPPATTERN@", grepPattern());
        final String command = host.getDistCommand(logFileCommand(),
                                                   replaceHash);
        host.execCommandRaw(command,
                             (ProgressBar) null,
                             new ExecCallback() {
                                 public void done(final String ans) {
                                     SwingUtilities.invokeLater(
                                                        new Runnable() {
                                         public void run() {
                                            logTextArea.setText(ans);
                                            logTextArea.setCaretPosition(
                                                logTextArea.getDocument().
                                                                 getLength());
                                         }
                                     });
                                     enableComponents();
                                 }
                                 public void doneError(final String ans,
                                                       final int exitCode) {
                                     Tools.sshError(host,
                                                    command,
                                                    ans,
                                                    exitCode);
                                     SwingUtilities.invokeLater(
                                                        new Runnable() {
                                         public void run() {
                                            logTextArea.setText("error...");

                                            enableComponents();
                                         }
                                     });
                                 }
                             }, false, false);
    }

    /**
     * Gets the title of the dialog, defined as Dialog.Logs.Title in
     * TextResources.
     */
    protected String getDialogTitle() {
        return Tools.getString("Dialog.HostLogs.Title");
    }

    /**
     * Returns description for dialog. Nothing
     */
    protected String getDescription() {
        return "";
    }

    /**
     * Returns the pane where the log file will be shown.
     */
    protected JComponent getInputPane() {
        final JPanel pane = new JPanel(new SpringLayout());
        logTextArea.setEditable(false);
        logTextArea.setText("loading...");

        pane.add(new JScrollPane(logTextArea));
        SpringUtilities.makeCompactGrid(pane, 1, 1,  // rows, cols
                                              1, 1,  // initX, initY
                                              1, 1); // xPad, yPad
        return pane;
    }
}