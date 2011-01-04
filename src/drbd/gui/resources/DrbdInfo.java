/*
 * This file is part of DRBD Management Console by LINBIT HA-Solutions GmbH
 * written by Rasto Levrinc.
 *
 * Copyright (C) 2009-2010, LINBIT HA-Solutions GmbH.
 * Copyright (C) 2009-2010, Rasto Levrinc
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
package drbd.gui.resources;

import drbd.Exceptions;
import drbd.AddDrbdConfigDialog;
import drbd.gui.Browser;
import drbd.gui.ClusterBrowser;
import drbd.gui.GuiComboBox;
import drbd.data.Host;
import drbd.data.DrbdXML;
import drbd.data.resources.Resource;
import drbd.data.DRBDtestData;
import drbd.data.ConfigData;
import drbd.utilities.Tools;
import drbd.utilities.ButtonCallback;
import drbd.utilities.DRBD;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Enumeration;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Iterator;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import drbd.utilities.MyButton;

/**
 * This class provides drbd info. For one it shows the editable global
 * drbd config, but if a drbd block device is selected it forwards to the
 * block device info, which is defined in HostBrowser.java.
 */
public class DrbdInfo extends EditableInfo {
    /** Selected block device. */
    private BlockDevInfo selectedBD = null;
    /** Cache for the info panel. */
    private JComponent infoPanel = null;

    /**
     * Prepares a new <code>DrbdInfo</code> object.
     */
    public DrbdInfo(final String name, final Browser browser) {
        super(name, browser);
        setResource(new Resource(name));
        ((ClusterBrowser) browser).getDrbdGraph().setDrbdInfo(this);
    }

    /** Sets stored parameters. */
    public final void setParameters() {
        final DrbdXML dxml = getBrowser().getDrbdXML();
        for (final String param : getParametersFromXML()) {
            String value;
            value = dxml.getGlobalConfigValue(param);
            final String defaultValue = getParamDefault(param);
            if (value == null) {
                value = defaultValue;
            }
            if (value == null) {
                value = "";
            }
            if ("".equals(value) && "usage-count".equals(param)) {
                value = "yes"; /* we don't get this parameter from
                                  the dump. */
            }
            final String oldValue = getParamSaved(param);
            final GuiComboBox cb = paramComboBoxGet(param, null);
            if (!Tools.areEqual(value, oldValue)) {
                getResource().setValue(param, value);
                if (cb != null) {
                    cb.setValue(value);
                }
            }
        }
    }

    /**
     * Returns browser object of this info.
     */
    protected final ClusterBrowser getBrowser() {
        return (ClusterBrowser) super.getBrowser();
    }

    /**
     * Returns menu drbd icon.
     */
    public final ImageIcon getMenuIcon(final boolean testOnly) {
        return null;
    }

    /**
     * Sets selected block device.
     */
    public final void setSelectedNode(final BlockDevInfo bdi) {
        this.selectedBD = bdi;
    }

    /**
     * Gets combo box for paremeter in te global config. usage-count is
     * disabled.
     */
    protected final GuiComboBox getParamComboBox(final String param,
                                                 final String prefix,
                                                 final int width) {
        final GuiComboBox cb = super.getParamComboBox(param, prefix, width);
        if ("usage-count".equals(param)) {
            cb.setEnabled(false);
        }
        return cb;
    }

    /**
     * Creates drbd config.
     */
    public final void createDrbdConfig(final boolean testOnly)
               throws Exceptions.DrbdConfigException {
        final StringBuffer config = new StringBuffer(160);
        config.append("## generated by drbd-gui ");
        config.append(Tools.getRelease());
        config.append("\n\n");

        final StringBuffer global = new StringBuffer(80);
        final DrbdXML dxml = getBrowser().getDrbdXML();
        final String[] params = dxml.getGlobalParams();
        global.append("global {\n");
        for (String param : params) {
            String value = getComboBoxValue(param);
            if ("usage-count".equals(param)
                && (value == null || "".equals(value))) {
                value = "yes";
            }
            if (value == null || "".equals(value)) {
                continue;
            }
            if (!value.equals(dxml.getParamDefault(param))) {
                if (isCheckBox(param)
                    || "booleanhandler".equals(getParamType(param))) {
                    if (value.equals(Tools.getString("Boolean.True"))) {
                        /* boolean parameter */
                        global.append("\t\t" + param + ";\n");
                    }
                } else {
                    global.append("\t\t");
                    global.append(param);
                    global.append('\t');
                    global.append(Tools.escapeConfig(value));
                    global.append(";\n");
                }
            }
        }
        global.append("}\n");
        if (global.length() > 0) {
            config.append(global);
        }
        final Host[] hosts = getBrowser().getCluster().getHostsArray();
        for (Host host : hosts) {
            final StringBuffer resConfig = new StringBuffer("");
            for (final DrbdResourceInfo dri
                                       : getBrowser().getDrbdResHashValues()) {
                if (dri.resourceInHost(host)) {
                    resConfig.append('\n');
                    try {
                        resConfig.append(dri.drbdResourceConfig());
                    } catch (Exceptions.DrbdConfigException dce) {
                        throw dce;
                    }
                }
            }
            String dir;
            String configName;
            boolean makeBackup;
            String preCommand;
            if (testOnly) {
                dir = "/var/lib/drbd/";
                configName = "drbd.conf-drbd-mc-test";
                makeBackup = false;
                preCommand = null;
            } else {
                dir = "/etc/";
                configName = "drbd.conf";
                makeBackup = true;
                preCommand = "mv /etc/drbd.d{,.bak.`date +'%s'`}";
            }
            host.getSSH().createConfig(config.toString()
                                       + resConfig.toString(),
                                       configName,
                                       dir,
                                       "0600",
                                       makeBackup,
                                       preCommand);
        }
    }

    /**
     * Returns lsit of all parameters as an array.
     */
    public final String[] getParametersFromXML() {
        final DrbdXML drbdXML = getBrowser().getDrbdXML();
        if (drbdXML == null) {
            return null;
        }
        return drbdXML.getGlobalParams();
    }

    /**
     * Checks parameter's new value if it is correct.
     */
    protected final boolean checkParam(final String param,
                                       final String newValue) {
        return getBrowser().getDrbdXML().checkParam(param, newValue);
    }

    /**
     * Returns default value of the parameter.
     */
    protected final String getParamDefault(final String param) {
        return getBrowser().getDrbdXML().getParamDefault(param);
    }

    /** Returns the regexp of the parameter. */
    protected String getParamRegexp(final String param) {
        return null;
    }

    /**
     * Returns the preferred value of the parameter.
     */
    protected final String getParamPreferred(final String param) {
        return getBrowser().getDrbdXML().getParamPreferred(param);
    }

    /**
     * Possible choices for pulldown menus, or null if it is not a pull
     * down menu.
     */
    protected final Object[] getParamPossibleChoices(final String param) {
        return getBrowser().getDrbdXML().getPossibleChoices(param);
    }

    /**
     * Returns paramter short description, for user visible text.
     */
    protected final String getParamShortDesc(final String param) {
        return getBrowser().getDrbdXML().getParamShortDesc(param);
    }

    /**
     * Returns parameter long description, for tool tips.
     */
    protected final String getParamLongDesc(final String param) {
        return getBrowser().getDrbdXML().getParamLongDesc(param);
    }

    /**
     * Returns section to which this parameter belongs.
     * This is used for grouping in the info panel.
     */
    protected final String getSection(final String param) {
        return getBrowser().getDrbdXML().getSection(param);
    }

    /**
     * Returns whether the parameter is required.
     */
    protected final boolean isRequired(final String param) {
        return getBrowser().getDrbdXML().isRequired(param);
    }

    /** Returns whether this parameter is advanced. */
    protected final boolean isAdvanced(final String param) {
        if (!Tools.areEqual(getParamDefault(param),
                            getParamSaved(param))) {
            /* it changed, show it */
            return false;
        }
        return getBrowser().getDrbdXML().isAdvanced(param);
    }

    /** Returns access type of this parameter. */
    protected final ConfigData.AccessType getAccessType(final String param) {
        return getBrowser().getDrbdXML().getAccessType(param);
    }

    /** Whether the parameter should be enabled. */
    protected final boolean isEnabled(final String param) {
        return true;
    }

    /** Returns whether the parameter is of the integer type. */
    protected final boolean isInteger(final String param) {
        return getBrowser().getDrbdXML().isInteger(param);
    }

    /** Returns whether the parameter is of the label type. */
    protected final boolean isLabel(final String param) {
        return getBrowser().getDrbdXML().isLabel(param);
    }

    /**
     * Returns whether the parameter is of the time type.
     */
    protected final boolean isTimeType(final String param) {
        /* not required */
        return false;
    }

    /**
     * Returns true if unit has prefix.
     */
    protected final boolean hasUnitPrefix(final String param) {
        return getBrowser().getDrbdXML().hasUnitPrefix(param);
    }

    /**
     * Returns long name of the unit, for user visible uses.
     */
    protected final String getUnitLong(final String param) {
        return getBrowser().getDrbdXML().getUnitLong(param);
    }

    /**
     * Returns the default unit of the parameter.
     */
    protected final String getDefaultUnit(final String param) {
        return getBrowser().getDrbdXML().getDefaultUnit(param);
    }

    /**
     * Returns whether the parameter is check box.
     */
    protected final boolean isCheckBox(final String param) {
        final String type = getBrowser().getDrbdXML().getParamType(param);
        if (type == null) {
            return false;
        }
        if (ClusterBrowser.DRBD_RES_BOOL_TYPE_NAME.equals(type)) {
            return true;
        }
        return false;
    }

    /**
     * Returns parameter type, boolean etc.
     */
    protected final String getParamType(final String param) {
        return getBrowser().getDrbdXML().getParamType(param);
    }

    /**
     * Applies changes made in the info panel by user.
     */
    public final void apply(final boolean testOnly) {
        if (!testOnly) {
            final String[] params = getParametersFromXML();
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    getApplyButton().setEnabled(false);
                    getRevertButton().setEnabled(false);
                    getApplyButton().setToolTipText(null);
                }
            });
            storeComboBoxValues(params);
            //checkResourceFieldsChanged(null, params);
            setAllApplyButtons();
        }
    }

    /**
     * Returns info panel for drbd. If a block device was selected, its
     * info panel is shown.
     */
    public final JComponent getInfoPanel() {
        if (selectedBD != null) { /* block device is not in drbd */
            return selectedBD.getInfoPanel();
        }
        if (infoPanel != null) {
            return infoPanel;
        }
        final JPanel mainPanel = new JPanel();
        if (getBrowser().getDrbdXML() == null) {
            mainPanel.add(new JLabel("drbd info not available"));
            return mainPanel;
        }
        final ButtonCallback buttonCallback = new ButtonCallback() {
            private volatile boolean mouseStillOver = false;

            /**
             * Whether the whole thing should be enabled.
             */
            public final boolean isEnabled() {
                final Host dcHost = getBrowser().getDCHost();
                if (dcHost == null) {
                    return false;
                }
                final String pmV = dcHost.getPacemakerVersion();
                final String hbV = dcHost.getHeartbeatVersion();
                if (pmV == null
                    && hbV != null
                    && Tools.compareVersions(hbV, "2.1.4") <= 0) {
                    return false;
                }
                return true;
            }

            public final void mouseOut() {
                if (!isEnabled()) {
                    return;
                }
                mouseStillOver = false;
                getBrowser().getDrbdGraph().stopTestAnimation(getApplyButton());
                getApplyButton().setToolTipText(null);
            }

            public final void mouseOver() {
                if (!isEnabled()) {
                    return;
                }
                mouseStillOver = true;
                getApplyButton().setToolTipText(
                       Tools.getString("ClusterBrowser.StartingDRBDtest"));
                getApplyButton().setToolTipBackground(Tools.getDefaultColor(
                                "ClusterBrowser.Test.Tooltip.Background"));
                Tools.sleep(250);
                if (!mouseStillOver) {
                    return;
                }
                mouseStillOver = false;
                final CountDownLatch startTestLatch = new CountDownLatch(1);
                getBrowser().getDrbdGraph().startTestAnimation(getApplyButton(),
                                                               startTestLatch);
                getBrowser().drbdtestLockAcquire();
                getBrowser().setDRBDtestData(null);
                apply(true);
                final Map<Host,String> testOutput =
                                         new LinkedHashMap<Host, String>();
                try {
                    createDrbdConfig(true);
                    for (final Host h
                                : getBrowser().getCluster().getHostsArray()) {
                        DRBD.adjust(h, "all", true);
                        testOutput.put(h, DRBD.getDRBDtest());
                    }
                } catch (Exceptions.DrbdConfigException dce) {
                    getBrowser().clStatusUnlock();
                    Tools.appError("config failed");
                }
                final DRBDtestData dtd = new DRBDtestData(testOutput);
                getApplyButton().setToolTipText(dtd.getToolTip());
                getBrowser().setDRBDtestData(dtd);
                getBrowser().drbdtestLockRelease();
                startTestLatch.countDown();
            }
        };
        initApplyButton(buttonCallback);
        mainPanel.setBackground(ClusterBrowser.PANEL_BACKGROUND);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        final JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBackground(ClusterBrowser.STATUS_BACKGROUND);
        buttonPanel.setMinimumSize(new Dimension(0, 50));
        buttonPanel.setPreferredSize(new Dimension(0, 50));
        buttonPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 50));

        final JPanel optionsPanel = new JPanel();
        optionsPanel.setBackground(ClusterBrowser.PANEL_BACKGROUND);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        mainPanel.add(buttonPanel);

        /* Actions */
        final JMenuBar mb = new JMenuBar();
        mb.setBackground(ClusterBrowser.PANEL_BACKGROUND);
        final JMenu serviceCombo = getActionsMenu();
        mb.add(serviceCombo);
        buttonPanel.add(mb, BorderLayout.EAST);

        final String[] params = getParametersFromXML();
        addParams(optionsPanel,
                  params,
                  Tools.getDefaultInt("ClusterBrowser.DrbdResLabelWidth"),
                  Tools.getDefaultInt("ClusterBrowser.DrbdResFieldWidth"),
                  null);

        getApplyButton().addActionListener(
            new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    final Thread thread = new Thread(new Runnable() {
                        public void run() {
                            getBrowser().clStatusLock();
                            apply(false);
                            try {
                                createDrbdConfig(false);
                                for (final Host h
                                       : getBrowser().getCluster().getHosts()) {
                                    DRBD.adjust(h, "all", false);
                                }
                            } catch (
                                final Exceptions.DrbdConfigException dce) {
                                getBrowser().clStatusUnlock();
                                Tools.appError("config failed");
                            }
                            getBrowser().clStatusUnlock();
                        }
                    });
                    thread.start();
                }
            }
        );
        getRevertButton().addActionListener(
            new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    final Thread thread = new Thread(new Runnable() {
                        public void run() {
                            getBrowser().clStatusLock();
                            revert();
                            getBrowser().clStatusUnlock();
                        }
                    });
                    thread.start();
                }
            }
        );

        /* apply button */
        addApplyButton(buttonPanel);
        addRevertButton(buttonPanel);
        //SwingUtilities.invokeLater(new Runnable() {
        //    public void run() {
        //        setApplyButtons(null, params);
        //    }
        //});

        mainPanel.add(optionsPanel);

        final JPanel newPanel = new JPanel();
        newPanel.setBackground(ClusterBrowser.PANEL_BACKGROUND);
        newPanel.setLayout(new BoxLayout(newPanel, BoxLayout.Y_AXIS));
        newPanel.add(buttonPanel);
        newPanel.add(getMoreOptionsPanel(
               Tools.getDefaultInt("ClusterBrowser.DrbdResLabelWidth")
               + Tools.getDefaultInt("ClusterBrowser.DrbdResFieldWidth") + 4));
        newPanel.add(new JScrollPane(mainPanel));
        infoPanel = newPanel;
        return infoPanel;
    }

    /**
     * Clears info panel cache.
     * TODO: should select something.
     */
    public final boolean selectAutomaticallyInTreeMenu() {
        return infoPanel == null;
    }

    /**
     * Returns drbd graph in a panel.
     */
    public final JPanel getGraphicalView() {
        if (selectedBD != null) {
            getBrowser().getDrbdGraph().pickBlockDevice(selectedBD);
        }
        return getBrowser().getDrbdGraph().getGraphPanel();
    }

    /**
     * Selects and highlights this node. This function is overwritten
     * because block devices don't have here their own node, but
     * views change depending on selectedNode variable.
     */
    public final void selectMyself() {
        if (selectedBD == null || !selectedBD.getBlockDevice().isDrbd()) {
            getBrowser().reload(getNode(), true);
            getBrowser().nodeChanged(getNode());
        } else {
            getBrowser().reload(selectedBD.getNode(), true);
            getBrowser().nodeChanged(selectedBD.getNode());
        }
    }

    /**
     * Returns new drbd resource index, the one that is not used .
     */
    private int getNewDrbdResourceIndex() {
        final Iterator<String> it =
                         getBrowser().getDrbdResHash().keySet().iterator();
        int index = -1;

        while (it.hasNext()) {
            final String name = it.next();
            // TODO: should not assume r0
            final Pattern p = Pattern.compile("^" + "r" + "(\\d+)$");
            final Matcher m = p.matcher(name);

            if (m.matches()) {
                final int i = Integer.parseInt(m.group(1));
                if (i > index) {
                    index = i;
                }
            }
        }
        getBrowser().putDrbdResHash();
        return index + 1;
    }

    /**
     * Adds drbd resource. If resource name and drbd device are null.
     * They will be created with first unused index. E.g. r0, r1 ...
     * /dev/drbd0, /dev/drbd1 ... etc.
     *
     * @param name
     *              resource name.
     * @param drbdDevStr
     *              drbd device like /dev/drbd0
     * @param bd1
     *              block device
     * @param bd2
     *              block device
     * @param interactive
     *              whether dialog box will be displayed
     */
    public final boolean addDrbdResource(String name,
                                         String drbdDevStr,
                                         final BlockDevInfo bd1,
                                         final BlockDevInfo bd2,
                                         final boolean interactive,
                                         final boolean testOnly) {
        if (getBrowser().getDrbdResHash().containsKey(name)) {
            getBrowser().putDrbdResHash();
            return false;
        }
        getBrowser().putDrbdResHash();
        DrbdResourceInfo dri;
        if (bd1 == null || bd2 == null) {
            return false;
        }
        final DrbdXML dxml = getBrowser().getDrbdXML();
        if (name == null && drbdDevStr == null) {
            int index = getNewDrbdResourceIndex();
            name = "r" + Integer.toString(index);
            drbdDevStr = "/dev/drbd" + Integer.toString(index);

            /* search for next available drbd device */
            final Map<String, DrbdResourceInfo> drbdDevHash =
                                                 getBrowser().getDrbdDevHash();
            while (drbdDevHash.containsKey(drbdDevStr)) {
                index++;
                drbdDevStr = "/dev/drbd" + Integer.toString(index);
            }
            getBrowser().putDrbdDevHash();
            dri = new DrbdResourceInfo(name,
                                       drbdDevStr,
                                       bd1,
                                       bd2,
                                       getBrowser());
        } else {
            dri = new DrbdResourceInfo(name,
                                       drbdDevStr,
                                       bd1,
                                       bd2,
                                       getBrowser());
            final String[] sections = dxml.getSections();
            for (String section : sections) {
                final String[] params = dxml.getSectionParams(section);
                for (String param : params) {
                    String value = dxml.getConfigValue(name, section, param);
                    if ("".equals(value)) {
                        value = dxml.getParamDefault(param);
                    }
                    dri.getDrbdResource().setValue(param, value);
                }
            }
            dri.getDrbdResource().setCommited(true);
        }
        /* We want next port number on both devices to be the same,
         * although other port numbers may not be the same on both. */
        final int viPort1 = bd1.getNextVIPort();
        final int viPort2 = bd2.getNextVIPort();
        final int viPort;
        if (viPort1 > viPort2) {
            viPort = viPort1;
        } else {
            viPort = viPort2;
        }
        bd1.setDefaultVIPort(viPort + 1);
        bd2.setDefaultVIPort(viPort + 1);

        dri.getDrbdResource().setDefaultValue(
                                        DrbdResourceInfo.DRBD_RES_PARAM_NAME,
                                        name);
        dri.getDrbdResource().setDefaultValue(
                                        DrbdResourceInfo.DRBD_RES_PARAM_DEV,
                                        drbdDevStr);
        getBrowser().getDrbdResHash().put(name, dri);
        getBrowser().putDrbdResHash();
        getBrowser().getDrbdDevHash().put(drbdDevStr, dri);
        getBrowser().putDrbdDevHash();

        if (bd1 != null) {
            bd1.setDrbd(true);
            bd1.setDrbdResourceInfo(dri);
            bd1.cleanup();
            bd1.setInfoPanel(null); /* reload panel */
            bd1.getInfoPanel();
            //bd1.selectMyself();
        }
        if (bd2 != null) {
            bd2.setDrbd(true);
            bd2.setDrbdResourceInfo(dri);
            bd2.cleanup();
            bd2.setInfoPanel(null); /* reload panel */
            bd2.getInfoPanel();
            //bd2.selectMyself();
        }

        final DefaultMutableTreeNode drbdResourceNode =
                                           new DefaultMutableTreeNode(dri);
        dri.setNode(drbdResourceNode);

        getBrowser().getDrbdNode().add(drbdResourceNode);

        final DefaultMutableTreeNode drbdBDNode1 =
                                           new DefaultMutableTreeNode(bd1);
        bd1.setNode(drbdBDNode1);
        final DefaultMutableTreeNode drbdBDNode2 =
                                           new DefaultMutableTreeNode(bd2);
        bd2.setNode(drbdBDNode2);
        drbdResourceNode.add(drbdBDNode1);
        drbdResourceNode.add(drbdBDNode2);

        getBrowser().getDrbdGraph().addDrbdResource(dri, bd1, bd2);
        dri.getInfoPanel();
        final DrbdResourceInfo driF = dri;
        if (interactive) {
            if (bd1 != null) {
                bd1.getBlockDevice().setNew(true);
            }
            if (bd2 != null) {
                bd2.getBlockDevice().setNew(true);
            }
            final Thread thread = new Thread(new Runnable() {
                public void run() {
                    //reload(getNode());
                    getBrowser().reload(drbdResourceNode, true);
                    AddDrbdConfigDialog adrd = new AddDrbdConfigDialog(driF);
                    adrd.showDialogs();
                    /* remove wizard parameters from hashes. */
                    for (final String p : bd1.getParametersFromXML()) {
                        bd1.paramComboBoxRemove(p, "wizard");
                        bd2.paramComboBoxRemove(p, "wizard");
                    }
                    for (final String p : driF.getParametersFromXML()) {
                        driF.paramComboBoxRemove(p, "wizard");
                    }
                    if (adrd.isCanceled()) {
                        driF.removeMyselfNoConfirm(testOnly);
                        getBrowser().getDrbdGraph().stopAnimation(bd1);
                        getBrowser().getDrbdGraph().stopAnimation(bd2);
                        return;
                    }

                    getBrowser().updateCommonBlockDevices();
                    final DrbdXML newDrbdXML =
                        new DrbdXML(getBrowser().getCluster().getHostsArray());
                    final String configString1 =
                                    newDrbdXML.getConfig(bd1.getHost());
                    newDrbdXML.update(configString1);
                    final String configString2 =
                                    newDrbdXML.getConfig(bd2.getHost());
                    newDrbdXML.update(configString2);
                    getBrowser().setDrbdXML(newDrbdXML);
                    getBrowser().resetFilesystems();
                    driF.selectMyself();
                }
            });
            thread.start();
        } else {
            getBrowser().resetFilesystems();
        }
        return true;
    }

    /** Whether the parameter should be enabled only in advanced mode. */
    protected final boolean isEnabledOnlyInAdvancedMode(final String param) {
         return false;
    }

    /**
     * Returns whether the specified parameter or any of the parameters
     * have changed. If param is null, only param will be checked,
     * otherwise all parameters will be checked.
     */
    public boolean checkResourceFieldsChanged(final String param,
                                              final String[] params) {
        boolean changed = false;
        for (final DrbdResourceInfo dri : getBrowser().getDrbdResHashValues()) {
            if (dri.checkResourceFieldsChanged(param,
                                               dri.getParametersFromXML(),
                                               true)) {
                changed = true;
            }
        }
        return super.checkResourceFieldsChanged(param, params) || changed;
    }

    /**
     * Returns whether all the parameters are correct. If param is null,
     * all paremeters will be checked, otherwise only the param, but other
     * parameters will be checked only in the cache. This is good if only
     * one value is changed and we don't want to check everything.
     */
    public boolean checkResourceFieldsCorrect(final String param,
                                              final String[] params,
                                              final boolean fromDrbdInfo) {
        boolean correct = true;
        for (final DrbdResourceInfo dri : getBrowser().getDrbdResHashValues()) {
            if (!dri.checkResourceFieldsCorrect(param,
                                                dri.getParametersFromXML(),
                                                true)) {
                correct = false;
            }
        }
        return super.checkResourceFieldsCorrect(param, params) && correct;
    }

    /** Revert all values. */
    public final void revert() {
        super.revert();
        for (final DrbdResourceInfo dri : getBrowser().getDrbdResHashValues()) {
            dri.revert();
        }
    }

    /** Set all apply buttons. */
    public final void setAllApplyButtons() {
        for (final DrbdResourceInfo dri : getBrowser().getDrbdResHashValues()) {
            dri.storeComboBoxValues(dri.getParametersFromXML());
            dri.setAllApplyButtons();
        }
    }
}
