/*
 * This file is part of LCMC written by Rasto Levrinc.
 *
 * Copyright (C) 2013, Rastislav Levrinc.
 *
 * The LCMC is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * The LCMC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LCMC; see the file COPYING.  If not, write to
 * the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package lcmc.robotest;

import static lcmc.robotest.RoboTest.*;
import java.awt.event.KeyEvent;
import lcmc.utilities.Tools;
import lcmc.utilities.Logger;
import lcmc.utilities.LoggerFactory;

/**
 * This class is used to test the GUI.
 *
 * @author Rasto Levrinc
 */
final class PcmkTest7 {
    /** Logger. */
    private static final Logger LOG = LoggerFactory.getLogger(PcmkTest7.class);

    /** Private constructor, cannot be instantiated. */
    private PcmkTest7() {
        /* Cannot be instantiated. */
    }

    static void start(final int count) {
        slowFactor = 0.5f;
        aborted = false;
        final int dummy1X = 235;
        final int dummy1Y = 207;
        disableStonith();
        for (int i = count; i > 0; i--) {
            if (i % 5 == 0) {
                info("test7 I: " + i);
            }
            checkTest("test7", 1);
            /* create dummy */
            sleep(5000);
            chooseDummy(dummy1X, dummy1Y, false, true);
            checkTest("test7", 2);
            sleep(5000);
            stopResource(dummy1X, dummy1Y);
            checkTest("test7", 3);
            sleep(5000);
            /* copy/paste */
            moveTo(dummy1X + 10 , dummy1Y + 10);
            leftClick();
            robot.keyPress(KeyEvent.VK_CONTROL);
            press(KeyEvent.VK_C);
            press(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            moveTo(dummy1X + 10 , dummy1Y + 90);
            leftClick();
            moveTo(Tools.getString("Browser.ApplyResource"));
            sleep(4000);
            leftClick();
            checkTest("test7", 4);

            removeResource(dummy1X, dummy1Y, CONFIRM_REMOVE);
            removeResource(dummy1X, dummy1Y + 90, CONFIRM_REMOVE);
        }
        System.gc();
    }
}
