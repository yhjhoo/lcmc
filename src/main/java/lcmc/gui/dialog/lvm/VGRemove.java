/*
 * This file is part of DRBD Management Console by LINBIT HA-Solutions GmbH
 * written by Rasto Levrinc.
 *
 * Copyright (C) 2009, LINBIT HA-Solutions GmbH.
 * Copyright (C) 2011-2012, Rastislav Levrinc.
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

package lcmc.gui.dialog.lvm;

import lcmc.gui.SpringUtilities;
import lcmc.gui.resources.BlockDevInfo;

import lcmc.utilities.Tools;
import lcmc.utilities.MyButton;
import lcmc.utilities.LVM;
import lcmc.data.Host;
import lcmc.data.Cluster;
import lcmc.data.resources.BlockDevice;
import lcmc.gui.Browser;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.JLabel;
import javax.swing.JCheckBox;
import java.awt.FlowLayout;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * This class implements VG Remove dialog.
 *
 * @author Rasto Levrinc
 * @version $Id$
 */
public final class VGRemove extends LV {
    /** Serial version UID. */
    private static final long serialVersionUID = 1L;
    /** Remove VG timeout. */
    private static final int REMOVE_TIMEOUT = 5000;
    /** Block device info object. */
    private final MyButton removeButton = new MyButton("Remove VG");
    private final List<BlockDevInfo> blockDevInfos =
                                                new ArrayList<BlockDevInfo>();
    private Map<Host, JCheckBox> hostCheckBoxes = null;
    private final boolean multiSelection;
    /** Description. */
    private static final String VG_REMOVE_DESCRIPTION =
                                                     "Remove a volume group.";
    /** Remove new VGRemove object. */
    public VGRemove(final BlockDevInfo bdi) {
        super(null);
        blockDevInfos.add(bdi);
        multiSelection = false;
    }

    /** Remove new VGRemove object. */
    public VGRemove(final List<BlockDevInfo> bdis) {
        super(null);
        blockDevInfos.addAll(bdis);
        multiSelection = true;
    }

    /** Finishes the dialog and sets the information. */
    @Override
    protected void finishDialog() {
        /* disable finish dialog button. */
    }

    /** Returns the title of the dialog. */
    @Override
    protected String getDialogTitle() {
        return "Remove VG";
    }

    /** Returns the description of the dialog. */
    @Override
    protected String getDescription() {
        return VG_REMOVE_DESCRIPTION;
    }

    /** Inits the dialog. */
    @Override
    protected void initDialogBeforeVisible() {
        super.initDialogBeforeVisible();
        enableComponentsLater(new JComponent[]{});
    }

    /** Inits the dialog after it becomes visible. */
    @Override
    protected void initDialogAfterVisible() {
        enableComponents();
        makeDefaultAndRequestFocus(removeButton);
    }

    private String getVGName(final BlockDevInfo bdi) {
        if (bdi.getBlockDevice().isDrbd()) {
            return bdi.getBlockDevice().getDrbdBlockDevice()
                                        .getVolumeGroupOnPhysicalVolume();
        } else {
            return bdi.getBlockDevice().getVolumeGroupOnPhysicalVolume();
        }
    }

    private Map<Host, Set<String>> getVGNames() {
        final Map<Host, Set<String>> vgNames =
                                        new LinkedHashMap<Host, Set<String>>();
        for (final BlockDevInfo bdi : blockDevInfos) {
            final Host h = bdi.getHost();
            Set<String> vgs = vgNames.get(h);
            if (vgs == null) {
                vgs = new LinkedHashSet<String>();
                vgNames.put(h, vgs);
            }
            vgs.add(getVGName(bdi));
        }
        return vgNames;
    }

    /** Returns the input pane. */
    @Override
    protected JComponent getInputPane() {
        removeButton.setEnabled(false);
        final JPanel pane = new JPanel(new SpringLayout());
        final JPanel inputPane = new JPanel(new SpringLayout());
        inputPane.setBackground(Browser.BUTTON_PANEL_BACKGROUND);

        inputPane.add(new JLabel("Volume Groups: "));
        final StringBuilder vgNamesString = new StringBuilder();

        final Map<Host, Set<String>> vgNames = getVGNames();

        for (final Map.Entry<Host, Set<String>> entry : vgNames.entrySet()) {
            final Host h = entry.getKey();
            vgNamesString.append(h.getName()).append(": ");
            final Set<String> vgs = entry.getValue();
            vgNamesString.append(Tools.join(", ", vgs)).append(' ');
        }

        inputPane.add(new JLabel(vgNamesString.toString()));
        removeButton.addActionListener(new RemoveActionListener());
        inputPane.add(removeButton);
        SpringUtilities.makeCompactGrid(inputPane, 1, 3,  /* rows, cols */
                                                   1, 1,  /* initX, initY */
                                                   1, 1); /* xPad, yPad */

        pane.add(inputPane);
        final JPanel bdPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bdPane.add(new JLabel("Block Devices: "));
        final Set<String> bds = new HashSet<String>();
        final Set<Host> selectedHosts = new HashSet<Host>();
        for (final BlockDevInfo bdi : blockDevInfos) {
            for (final BlockDevice bd : bdi.getHost().getBlockDevices()) {
                final String thisVG = bd.getVolumeGroupOnPhysicalVolume();
                if (vgNames.get(bdi.getHost()).contains(thisVG)) {
                    bds.add(bd.getName());
                }

            }
            if (bdi.getBlockDevice().isDrbd()) {
                for (final BlockDevice bd
                                  : bdi.getHost().getDrbdBlockDevices()) {
                    final String thisVG = bd.getVolumeGroupOnPhysicalVolume();
                    if (vgNames.get(bdi.getHost()).contains(thisVG)) {
                        bds.add(bd.getName());
                    }

                }
            }
            selectedHosts.add(bdi.getHost());
        }
        bdPane.add(new JLabel(Tools.join(", ", bds)));
        pane.add(bdPane);
        final JPanel hostsPane = new JPanel(
                        new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        final Host host = blockDevInfos.get(0).getHost();
        final Cluster cluster = host.getCluster();
        hostCheckBoxes = Tools.getHostCheckBoxes(cluster);
        hostsPane.add(new JLabel("Select Hosts: "));
        for (final Host h : hostCheckBoxes.keySet()) {
            hostCheckBoxes.get(h).addItemListener(
                                            new ItemChangeListener(true));
            if (host == h) {
                hostCheckBoxes.get(h).setEnabled(false);
                hostCheckBoxes.get(h).setSelected(true);
            } else if (isOneDrbd(blockDevInfos)) {
                hostCheckBoxes.get(h).setEnabled(false);
                hostCheckBoxes.get(h).setSelected(false);
            } else {
                hostCheckBoxes.get(h).setEnabled(true);
                hostCheckBoxes.get(h).setSelected(selectedHosts.contains(h));
            }
            hostsPane.add(hostCheckBoxes.get(h));
        }
        final javax.swing.JScrollPane sp = new javax.swing.JScrollPane(
                                                               hostsPane);
        sp.setPreferredSize(new java.awt.Dimension(0, 45));
        pane.add(sp);
        pane.add(getProgressBarPane(null));
        pane.add(getAnswerPane(""));
        SpringUtilities.makeCompactGrid(pane, 5, 1,  /* rows, cols */
                                              0, 0,  /* initX, initY */
                                              0, 0); /* xPad, yPad */
        removeButton.setEnabled(true);
        return pane;
    }

    /** Remove action listener. */
    private class RemoveActionListener implements ActionListener {
        @Override
        public void actionPerformed(final ActionEvent e) {
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Tools.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            removeButton.setEnabled(false);
                        }
                    });
                    disableComponents();
                    getProgressBar().start(REMOVE_TIMEOUT
                                           * hostCheckBoxes.size());
                    boolean oneFailed = false;

                    if (multiSelection) {
                        final Map<Host, Set<String>> vgNames = getVGNames();
                        for (final Map.Entry<Host, Set<String>> entry
                                                        : vgNames.entrySet()) {
                            final Host h = entry.getKey();
                            for (final String vgName : entry.getValue()) {
                                if (hostCheckBoxes.get(h).isSelected()) {
                                    final boolean ret = vgRemove(h, vgName);
                                    if (!ret) {
                                        oneFailed = true;
                                    }
                                }
                            }
                        }
                    } else {
                        for (final Host h : hostCheckBoxes.keySet()) {
                            if (hostCheckBoxes.get(h).isSelected()) {
                                final boolean ret =
                                   vgRemove(h, getVGName(blockDevInfos.get(0)));
                                if (!ret) {
                                    oneFailed = true;
                                }
                            }
                        }
                    }
                    for (final Host h : hostCheckBoxes.keySet()) {
                        if (hostCheckBoxes.get(h).isSelected()) {
                            h.getBrowser().getClusterBrowser().updateHWInfo(
                                                           h,
                                                           Host.UPDATE_LVM);
                        }
                    }
                    enableComponents();
                    if (oneFailed) {
                        Tools.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                removeButton.setEnabled(true);
                            }
                        });
                        progressBarDoneError();
                    } else {
                        progressBarDone();
                        disposeDialog();
                    }
                }
            });
            thread.start();
        }
    }

    /** Remove VG. */
    private boolean vgRemove(final Host host,
                             final String vgName) {
        final boolean ret = LVM.vgRemove(host, vgName, false);
        if (ret) {
            answerPaneAddText("Volume group "
                              + vgName
                              + " was successfully removed "
                              + " on " + host.getName() + ".");
        } else {
            answerPaneAddTextError("Removing volume group "
                                    + vgName
                                    + " on " + host.getName()
                                    + " failed.");
        }
        return ret;
    }

    /** Size combo box item listener. */
    private class ItemChangeListener implements ItemListener {
        /** Whether to check buttons on both select and deselect. */
        private final boolean onDeselect;
        /** Create ItemChangeListener object. */
        public ItemChangeListener(final boolean onDeselect) {
            super();
            this.onDeselect = onDeselect;
        }
        @Override
        public void itemStateChanged(final ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED || onDeselect) {
                removeButton.setEnabled(true);
            }
        }
    }
}
