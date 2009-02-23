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

import drbd.data.Host;
import drbd.data.Cluster;
import drbd.utilities.Tools;
import drbd.utilities.DRBD;
import drbd.utilities.Heartbeat;
import drbd.utilities.SSH.ExecCommandThread;
import drbd.utilities.MyButton;
import drbd.gui.SpringUtilities;
import drbd.utilities.ExecCallback;
import drbd.gui.ProgressBar;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.ArrayList;
import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Color;
import javax.swing.border.TitledBorder;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;

/**
 * An implementation of a dialog where heartbeat is initialized on all hosts.
 *
 * @author Rasto Levrinc
 * @version $Id$
 */
public class ClusterInit extends DialogCluster {
    /** Serial Version UID. */
    private static final long serialVersionUID = 1L;
    /** List with texts if drbd is loaded per host. */
    private List<JLabel> drbdLoadedInfos;
    /** List of load drbd buttons. */
    private List<MyButton> drbdLoadButtons;

    /** List with texts whether hb is started per host. */
    private List<JLabel> hbStartedInfos;
    /** List of start hb buttons. */
    private List<MyButton> hbStartButtons;

    /** List with texts whether hb status is working per host. */
    private List<JLabel> hbStatusInfos;
    /** List of hb status buttons. */
    private List<MyButton> enterPasswdButton;

    /** Main panel, so that it can be revalidated, if something have changed.
     */
    private JPanel mainPanel;
    /** Whether the checking of the cluster should be stopped. */
    private volatile boolean checkClusterStopped;
    /** Last drbd-loaded check. */
    private Boolean[] lastDrbdLoaded;
    /** Last hb-started check. */
    private Boolean[] lastHbStarted;
    /** Last hb-status check. */
    private Boolean[] lastHbStatus;
    /** Last hb-status exit code */
    private Integer[] lastHbExitCode;

    /** Cluster check thread. */
    private Thread checkClusterThread = null;
    /** Button that acts as a finish button. This is used by methods that
     * override this one and use different finish/next button.
     */
    private String button = null;
    /** Interval between checks. */
    private static final int CHECK_INTERVAL = 1000;
    /** On which host the hb password should be changed. */
    private volatile Host changeHbPasswordOnHost = null;

    /**
     * Prepares a new <code>ClusterInit</code> object.
     */
    public ClusterInit(final WizardDialog previousDialog,
                       final Cluster cluster) {
        super(previousDialog, cluster);
        //setButton(nextButton()); // TODO:

        setButton(finishButton());
    }

    /**
     * Sets button. Which acts as a finish button.
     */
    private void setButton(final String button) {
        this.button = button;
    }

    /**
     * Stops the checks and waits for them to stop.
     */
    private void stopCheckCluster() {
        checkClusterStopped = true;
    }

    /**
     * After the dialog is finished.
     */
    protected final void finishDialog() {
        stopCheckCluster();
    }

    /**
     * Is called before the dialog is canceled. It stops all the checks.
     */
    public final void cancelDialog() {
        stopCheckCluster();
    }

    /**
     * Returns the next dialog.
     */
    public final WizardDialog nextDialog() {
        stopCheckCluster();
        if (changeHbPasswordOnHost != null) {
            changeHbPasswordOnHost = null;
            //return new ClusterHbChangePasswd(this,
            //                                 getCluster(),
            //                                 changeHbPasswordOnHost);
            return new ClusterFinish(this, getCluster());
        } else {
            return new ClusterFinish(this, getCluster());
        }
    }

    /**
     * Returns the title of the dialog.
     */
    protected final String getClusterDialogTitle() {
        return Tools.getString("Dialog.ClusterInit.Title");
    }

    /**
     * Returns the description of the dialog.
     */
    protected final String getDescription() {
        return Tools.getString("Dialog.ClusterInit.Description");
    }

    /**
     * Inits the dialog.
     */
    protected final void initDialog() {
        super.initDialog();

        enableComponentsLater(new JComponent[]{buttonClass(nextButton())});
        lastDrbdLoaded = null;
        lastHbStarted  = null;
        lastHbStatus   = null;
        lastHbExitCode = null;
        checkClusterThread = new Thread(
            new Runnable() {
                public void run() {
                    while (!checkClusterStopped) {
                        checkCluster();
                        if (!checkClusterStopped) {
                            try {
                                Thread.sleep(CHECK_INTERVAL);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                    checkClusterThread = null;
                }
            });
        checkClusterThread.start();
        //buttonClass(nextButton()).setEnabled(true); // TODO: remove this
    }

    /**
     * Checks drbds and heartbeats on all nodes of the cluster.
     */
    private void checkCluster() {
        /* check if modules are loaded. */
        final Host[] hosts = getCluster().getHostsArray();
        final Boolean[] drbdLoaded = new Boolean[hosts.length];
        final Boolean[] hbStarted  = new Boolean[hosts.length];
        final Boolean[] hbStatus   = new Boolean[hosts.length];
        final Integer[] hbExitCode = new Integer[hosts.length];
        ExecCommandThread[] tsDrbd     = new ExecCommandThread[hosts.length];
        ExecCommandThread[] tsHb       = new ExecCommandThread[hosts.length];
        ExecCommandThread[] tsHbStatus = new ExecCommandThread[hosts.length];
        int i = 0;

        for (final Host h : hosts) {
            final int index = i;
            tsDrbd[i] = h.execCommand("DRBD.isModuleLoaded",
                             (ProgressBar) null,
                             new ExecCallback() {
                                 public void done(final String ans) {
                                     drbdLoaded[index] = true;
                                 }
                                 public void doneError(final String ans,
                                                       final int exitCode) {
                                     drbdLoaded[index] = false;
                                 }
                             }, false);
            tsHb[i] = h.execCommand("Heartbeat.isStarted",
                             (ProgressBar) null,
                             new ExecCallback() {
                                 public void done(final String ans) {
                                     hbStarted[index] = true;
                                 }
                                 public void doneError(final String ans,
                                                       final int exitCode) {
                                     hbStarted[index] = false;
                                 }
                             }, false);
            tsHbStatus[i] = h.execCommandRaw(
                     Heartbeat.getMgmtCommand("all_nodes",
                                              getCluster().getHbPasswd(),
                                              ""),
                     (ProgressBar) null,
                     new ExecCallback() {
                         public void done(final String ans) {
                             final String[] s = ans.split("\\r?\\n");
                             if (s[0].equals("ok")) {
                                 hbStatus[index] = true;
                             } else {
                                 hbStatus[index] = false;
                             }
                             hbExitCode[index] = 0;
                         }

                         public void doneError(final String ans,
                                               final int exitCode) {
                             hbStatus[index] = false;
                             hbExitCode[index] = exitCode;
                         }

                     }, false, false);
            i++;
        }
        for (ExecCommandThread t : tsDrbd) {
            // wait for all of them
            try {
                t.join();
            } catch (java.lang.InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        i = 0;
        boolean oneFailed = false;
        boolean oneChanged = false;
        final boolean lastDrbdLoadedExists = (lastDrbdLoaded != null);
        if (!lastDrbdLoadedExists) {
            lastDrbdLoaded = new Boolean[hosts.length];
        }
        for (Boolean l : drbdLoaded) {
            boolean changed = false;

            if (lastDrbdLoadedExists) {
                if (lastDrbdLoaded[i].booleanValue() != l.booleanValue()) {
                    oneChanged = true;
                    changed = true;
                    lastDrbdLoaded[i] = Boolean.valueOf(l);
                }
            } else {
                oneChanged = true;
                changed = true;
                lastDrbdLoaded[i] = Boolean.valueOf(l);
            }
            if (l.booleanValue()) {
                if (changed) {
                    drbdLoadedInfos.get(i).setText(
                            Tools.getString("Dialog.ClusterInit.DrbdIsLoaded"));
                    drbdLoadedInfos.get(i).setForeground(Color.BLACK);
                }
            } else {
                oneFailed = true;
                if (changed) {
                    drbdLoadedInfos.get(i).setText(
                        Tools.getString("Dialog.ClusterInit.DrbdIsNotLoaded"));
                    drbdLoadedInfos.get(i).setForeground(Color.RED);
                    drbdLoadButtons.get(i).setVisible(true);
                }
            }
            i++;
        }

        for (ExecCommandThread t : tsHb) {
            // wait for all of them
            try {
                t.join();
            } catch (java.lang.InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        i = 0;
        final boolean lastHbStartedExists = (lastHbStarted != null);
        if (!lastHbStartedExists) {
            lastHbStarted = new Boolean[hosts.length];
        }
        for (Boolean l : hbStarted) {
            boolean changed = false;
            if (lastHbStartedExists) {
                if (lastHbStarted[i].booleanValue() != l.booleanValue()) {
                    oneChanged = true;
                    changed = true;
                    lastHbStarted[i] = Boolean.valueOf(l);
                }
            } else {
                oneChanged = true;
                changed = true;
                lastHbStarted[i] = Boolean.valueOf(l);
            }
            if (l.booleanValue()) {
                if (changed) {
                    hbStartedInfos.get(i).setText(
                            Tools.getString("Dialog.ClusterInit.HbIsRunning"));
                    hbStartedInfos.get(i).setForeground(Color.BLACK);
                    hbStartButtons.get(i).setVisible(false);
                }
            } else {
                oneFailed = true;
                if (changed) {
                    hbStartedInfos.get(i).setText(
                            Tools.getString("Dialog.ClusterInit.HbIsStopped"));
                    hbStartButtons.get(i).setVisible(true);
                    hbStartedInfos.get(i).setForeground(Color.RED);
                }
            }
            i++;
        }

        for (ExecCommandThread t : tsHbStatus) {
            // wait for all of them
            try {
                t.join();
            } catch (java.lang.InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        i = 0;

        final boolean lastHbStatusExists = (lastHbStatus != null);
        if (!lastHbStatusExists) {
            lastHbStatus = new Boolean[hosts.length];
            lastHbExitCode = new Integer[hosts.length];
        }
        for (Boolean l : hbStatus) {
            if (!lastHbStarted[i]) {
                hbStatusInfos.get(i).setVisible(false);
                enterPasswdButton.get(i).setVisible(false);
                continue;
            } else {
                hbStatusInfos.get(i).setVisible(true);
            }
            boolean changed = false;
            if (lastHbStatusExists && lastHbStatus[i] != null) {
                if (lastHbStatus[i].booleanValue() != l.booleanValue()
                    || lastHbExitCode[i].intValue() != hbExitCode[i].intValue()) {
                    oneChanged = true;
                    changed = true;
                    lastHbStatus[i] = Boolean.valueOf(l);
                    lastHbExitCode[i] = hbExitCode[i];
                }
            } else {
                oneChanged = true;
                changed = true;
                lastHbStatus[i] = Boolean.valueOf(l);
                lastHbExitCode[i] = hbExitCode[i];
            }
            if (l.booleanValue()) {
                if (changed) {
                    hbStatusInfos.get(i).setText(
                        Tools.getString("Dialog.ClusterInit.HbStatusWorks"));
                    enterPasswdButton.get(i).setVisible(false);
                    hbStatusInfos.get(i).setForeground(Color.BLACK);
                }
            } else {
                oneFailed = true;
                if (changed) {
                    if (253 == hbExitCode[i]) {
                        /* wrong hb password */
                        hbStatusInfos.get(i).setText(
                          Tools.getString("Dialog.ClusterInit.HbStatusWrongPassword"));
                        enterPasswdButton.get(i).setVisible(true);
                    } else {
                        hbStatusInfos.get(i).setText(
                          Tools.getString("Dialog.ClusterInit.HbStatusDoesntWork"));
                        enterPasswdButton.get(i).setVisible(false);
                    }
                    hbStatusInfos.get(i).setForeground(Color.RED);
                }
            }
            i++;
        }

        if (oneChanged) {
            mainPanel.invalidate();
            mainPanel.validate();
            mainPanel.repaint();

            if (oneFailed) {
                buttonClass(button).setEnabled(false);
            } else {
                buttonClass(button).setEnabled(true);
            }

            enableComponents();
        }
    }

    /**
     * Returns the input pane with status information about drbd and heartbeat
     * and some buttons.
     */
    protected final JComponent getInputPane() {
        /* Waiting for check cluster thread to finish. To avoid all races. This
         * can happen after clicking the back button from the next dialog. */
        final Thread t = checkClusterThread;
        if (t != null) {
            try {
                t.join();
            } catch (java.lang.InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        checkClusterStopped = false;
        drbdLoadedInfos = new ArrayList<JLabel>();
        drbdLoadButtons = new ArrayList<MyButton>();

        hbStartedInfos = new ArrayList<JLabel>();
        hbStartButtons = new ArrayList<MyButton>();

        hbStatusInfos = new ArrayList<JLabel>();
        enterPasswdButton = new ArrayList<MyButton>();

        mainPanel = new JPanel(new GridLayout(1, 0));
        //mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));

        final Host[] hosts = getCluster().getHostsArray();
        int i = 0;
        for (final Host host : hosts) {
            final int index = i;

            final SpringLayout layout = new SpringLayout();
            final JPanel pane = new JPanel(layout);
            pane.setAlignmentX(Component.LEFT_ALIGNMENT);

            final TitledBorder titledBorder =
                            BorderFactory.createTitledBorder(host.getName());
            titledBorder.setTitleJustification(TitledBorder.LEFT);
            pane.setBorder(titledBorder);

            drbdLoadedInfos.add(new JLabel(
                        Tools.getString("Dialog.ClusterInit.CheckingDrbd")));
            drbdLoadButtons.add(new MyButton(
                        Tools.getString("Dialog.ClusterInit.LoadDrbdButton")));
            drbdLoadButtons.get(i).setVisible(false);

            drbdLoadButtons.get(i).addActionListener(
                new ActionListener() {
                    public void actionPerformed(final ActionEvent e) {
                        final Thread thread = new Thread(
                            new Runnable() {
                                public void run() {
                                    drbdLoadButtons.get(index).setVisible(false);
                                    DRBD.load(host);
                                    if (host.isDrbdUpgraded()) {
                                        DRBD.adjust(host, "all");
                                    }
                                    checkCluster();
                                }
                            }
                        );
                        thread.start();
                    }
                });

            pane.add(drbdLoadedInfos.get(i));
            pane.add(drbdLoadButtons.get(i));

            hbStartedInfos.add(new JLabel(
                        Tools.getString("Dialog.ClusterInit.CheckingHb")));
            hbStartButtons.add(new MyButton(
                        Tools.getString("Dialog.ClusterInit.StartHbButton")));
            hbStartButtons.get(i).setVisible(false);

            hbStartButtons.get(i).addActionListener(
                new ActionListener() {
                    public void actionPerformed(final ActionEvent e) {
                        final Thread thread = new Thread(
                            new Runnable() {
                                public void run() {
                                    hbStartButtons.get(index).setVisible(false);
                                    Heartbeat.start(host);
                                    checkCluster();
                                }
                            }
                        );
                        thread.start();
                    }
                });

            pane.add(hbStartedInfos.get(i));
            pane.add(hbStartButtons.get(i));

            hbStatusInfos.add(new JLabel(
                    Tools.getString("Dialog.ClusterInit.CheckingHbStatus")));
            enterPasswdButton.add(new MyButton(
                    Tools.getString("Dialog.ClusterInit.EnterPasswordButton")));
            enterPasswdButton.get(i).setVisible(false);

            enterPasswdButton.get(i).addActionListener(
                new ActionListener() {
                    public void actionPerformed(final ActionEvent e) {
                        final Thread thread = new Thread(
                            new Runnable() {
                                public void run() {
                                    enterPasswdButton.get(index).setVisible(false);
                                    changeHbPasswordOnHost = host;
                                    ((MyButton) buttonClass(
                                                    nextButton())).pressButton();
                                }
                            }
                        );
                        thread.start();
                    }
                });

            pane.add(hbStatusInfos.get(i));
            pane.add(enterPasswdButton.get(i));

            i++;
            SpringUtilities.makeCompactGrid(pane, 3, 2,  //rows, cols
                                                  1, 1,  //initX, initY
                                                  1, 0); //xPad, yPad
            mainPanel.add(pane);
        }
        return new JScrollPane(mainPanel);
    }
}
