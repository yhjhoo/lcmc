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


package lcmc.gui.dialog.drbdConfig;

import lcmc.utilities.Tools;
import lcmc.utilities.DRBD;
import lcmc.gui.resources.DrbdVolumeInfo;
import lcmc.gui.resources.BlockDevInfo;
import lcmc.gui.dialog.WizardDialog;
import lcmc.gui.SpringUtilities;
import lcmc.gui.ClusterBrowser;
import lcmc.Exceptions;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.SpringLayout;
import javax.swing.BoxLayout;

import java.awt.Component;
import java.net.UnknownHostException;

import lcmc.utilities.Logger;
import lcmc.utilities.LoggerFactory;

/**
 * An implementation of a dialog where user can enter drbd block device
 * information.
 *
 * @author Rasto Levrinc
 * @version $Id$
 *
 */
final class BlockDev extends DrbdConfig {
    /** Logger. */
    private static final Logger LOG = LoggerFactory.getLogger(BlockDev.class);
    /** Serial version UID. */
    private static final long serialVersionUID = 1L;
    /** This block device. */
    private final BlockDevInfo blockDevInfo;

    /** Prepares a new <code>BlockDev</code> object. */
    BlockDev(final WizardDialog previousDialog,
             final DrbdVolumeInfo dli,
             final BlockDevInfo blockDevInfo) {
        super(previousDialog, dli);
        this.blockDevInfo = blockDevInfo;
        dli.getDrbdResourceInfo().getDrbdInfo().setSelectedNode(blockDevInfo);
        dli.getDrbdResourceInfo().getDrbdInfo().selectMyself();
    }

    /** Applies the changes to the blockDevInfo object. */
    @Override
    protected void finishDialog() {
        Tools.waitForSwing();
        blockDevInfo.apply(false);
    }

    /** Calls drbdadm get-gi, to find out if there is meta-data area. */
    private String getGI(final BlockDevInfo bdi) {
        final boolean testOnly = false;
        final String gi = DRBD.getGI(
                       bdi.getHost(),
                       bdi.getDrbdVolumeInfo().getDrbdResourceInfo().getName(),
                       bdi.getDrbdVolumeInfo().getName(),
                       testOnly);
        return gi;
    }

    /**
     * Sets next dialog and returns it. It is either second block device, or
     * drbd config create md dialog. In the second case the drbd admin adjust
     * is called.
     */
    @Override
    public WizardDialog nextDialog() {
        if (getDrbdVolumeInfo().isFirstBlockDevInfo(blockDevInfo)) {
            final BlockDevInfo oBdi =
                    getDrbdVolumeInfo().getOtherBlockDevInfo(blockDevInfo);
            return new BlockDev(this, getDrbdVolumeInfo(), oBdi);
        } else {
            final BlockDevInfo oBdi =
                    getDrbdVolumeInfo().getOtherBlockDevInfo(blockDevInfo);
            try {
                // TODO: check this
                final boolean testOnly = false;
                getDrbdVolumeInfo().getDrbdResourceInfo().getDrbdInfo()
                                                    .createDrbdConfig(false);
                final String gi1 = getGI(blockDevInfo);
                final String gi2 = getGI(oBdi);
                if (gi1 == null || gi2 == null) {
                    getDrbdVolumeInfo().getDrbdResourceInfo().setHaveToCreateMD(
                                                                          true);
                }
                final ClusterBrowser browser =
                        getDrbdVolumeInfo().getDrbdResourceInfo().getBrowser();
                browser.reloadAllComboBoxes(null);
                Tools.getGUIData().expandTerminalSplitPane(1);
                Tools.getGUIData().getMainFrame().requestFocus();
            } catch (Exceptions.DrbdConfigException dce) {
                LOG.appError("nextDialog: config failed", dce);
            } catch (UnknownHostException e) {
                LOG.appError("nextDialog: config failed", e);
            }
            return new CreateMD(this, getDrbdVolumeInfo());
        }
    }

    /** Returns title of the dialog. */
    @Override
    protected String getDialogTitle() {
        return Tools.getString("Dialog.DrbdConfig.BlockDev.Title");
    }

    /**
     * Returns description of the dialog.
     */
    @Override
    protected String getDescription() {
        return Tools.getString("Dialog.DrbdConfig.BlockDev.Description");
    }

    /** Inits the dialog. */
    @Override
    protected void initDialogBeforeVisible() {
        super.initDialogBeforeVisible();
        enableComponentsLater(new JComponent[]{buttonClass(nextButton())});
    }

    /** Inits the dialog after it becomes visible. */
    @Override
    protected void initDialogAfterVisible() {
        enableComponents();

        final String[] params = blockDevInfo.getParametersFromXML();
        Tools.invokeLater(new Runnable() {
            @Override
            public void run() {
                buttonClass(nextButton()).setEnabled(
                    blockDevInfo.checkResourceFieldsCorrect(null, params));
            }
        });
        if (Tools.getConfigData().getAutoOptionGlobal("autodrbd") != null) {
            pressNextButton();
        }
    }

    /** Returns the input pane with block device parameters. */
    @Override
    protected JComponent getInputPane() {
        final JPanel inputPane = new JPanel(new SpringLayout());
        final JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        final String[] params = blockDevInfo.getParametersFromXML();
        blockDevInfo.selectMyself();
        blockDevInfo.waitForInfoPanel();
        blockDevInfo.addWizardParams(
                 optionsPanel,
                 params,
                 buttonClass(nextButton()),
                 Tools.getDefaultSize("Dialog.DrbdConfig.BlockDev.LabelWidth"),
                 Tools.getDefaultSize("Dialog.DrbdConfig.BlockDev.FieldWidth"),
                 null);

        final JPanel p = new JPanel();
        p.setBackground(Tools.getDefaultColor("ConfigDialog.Background.Dark"));
        p.add(new JLabel(blockDevInfo.getHost().getName()));
        inputPane.add(p);
        inputPane.add(optionsPanel);
        SpringUtilities.makeCompactGrid(inputPane, 2, 1,  //rows, cols
                                                   0, 0,  //initX, initY
                                                   0, 0); //xPad, yPad
        return inputPane;
    }
}
