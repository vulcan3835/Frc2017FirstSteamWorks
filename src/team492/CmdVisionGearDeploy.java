/*
 * Copyright (c) 2017 Titan Robotics Club (http://www.titanrobotics.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package team492;

import org.opencv.core.Rect;

import frclib.FrcValueMenu;
import trclib.TrcEvent;
import trclib.TrcRobot;
import trclib.TrcStateMachine;
// import trclib.TrcTimer;

class CmdVisionGearDeploy implements TrcRobot.RobotCommand
{
    private static enum State
    {
        ALIGN_HORIZONTALLY, ALIGN_DISTANCE, DEPLOY_GEAR, BACKUP, DONE
    }   //enum State

    private static final String moduleName = "CmdVisionGearDeploy";

    private Robot robot;
    private double extendTime;
    private double backupDistance;
    private double horizontalAlignmentThreshold;
    private double distanceAlignmentThreshold;
    private TrcEvent event;
    private TrcStateMachine<State> sm;
    private double horizontalAlignmentMultiplier;
    private double distanceAlignmentMultiplier;
    private double idealTargetSize;

    CmdVisionGearDeploy(Robot robot)
    {
        this.robot = robot;
        
        FrcValueMenu extendTimeMenu = new FrcValueMenu("PneumaticExtendTime", 0.3);
        FrcValueMenu horizontalAlignmentMultiplierMenu = new FrcValueMenu("HorizontalAlignmentMultiplier", 1.0);
        FrcValueMenu backupDistanceMenu = new FrcValueMenu("BackupDistance", 12.0);
        FrcValueMenu horizontalAlignmentThresholdMenu = new FrcValueMenu("HorizontalAlignmentThreshold", 10.0);
        FrcValueMenu distanceAlignmentThresholdMenu = new FrcValueMenu("DistanceALignmentThreshold", 10.0);
        FrcValueMenu distanceAlignmentMultiplierMenu = new FrcValueMenu("DistanceALignmentMultiplier", 1.0);
        FrcValueMenu idealTargetSizeMenu = new FrcValueMenu("IdealTargetSize", 150);

        extendTime = Math.abs(extendTimeMenu.getCurrentValue());
        horizontalAlignmentMultiplier = Math.abs(horizontalAlignmentMultiplierMenu.getCurrentValue());
        backupDistance =  Math.abs(backupDistanceMenu.getCurrentValue());
        horizontalAlignmentThreshold = Math.abs(horizontalAlignmentThresholdMenu.getCurrentValue());
        distanceAlignmentThreshold  = Math.abs(distanceAlignmentThresholdMenu.getCurrentValue());
        distanceAlignmentMultiplier  = Math.abs(distanceAlignmentMultiplierMenu.getCurrentValue());
        idealTargetSize  = Math.abs(idealTargetSizeMenu.getCurrentValue());

        event = new TrcEvent(moduleName);
        sm = new TrcStateMachine<>(moduleName);
        sm.start(State.ALIGN_HORIZONTALLY);
    }   //CmdVisionGearDeploy

    //
    // Implements the TrcRobot.RobotCommand interface.
    //

    @Override
    public boolean cmdPeriodic(double elapsedTime)
    {
        boolean done = !sm.isEnabled();
        //
        // Print debug info.
        //
        State state = sm.getState();
        robot.dashboard.displayPrintf(1, "State: %s", state != null ? state.toString() : "Disabled");

        if (sm.isReady())
        {
            state = sm.getState();

            switch (state)
            {

                case ALIGN_HORIZONTALLY:
                    //
                    // Position robot at target.
                    // get info from vision (m: alignment, n:distance)
                    double midpoint = getHorizontalPosition();
                    double screenMidpoint = RobotInfo.CAM_WIDTH/2;
                    double horizontalErrorMargin = midpoint - screenMidpoint;
                    if (Math.abs(horizontalErrorMargin) > horizontalAlignmentThreshold)
                    {
                        double strafeTarget = horizontalErrorMargin*horizontalAlignmentMultiplier;
                        robot.pidDrive.setTarget(strafeTarget, 0.0, robot.targetHeading, false, event);
                        sm.waitForSingleEvent(event, State.ALIGN_HORIZONTALLY);
                    }
                    else
                    {
                    	sm.setState(State.ALIGN_DISTANCE);
                    }
                    break;
                    
                case ALIGN_DISTANCE:
                	double actualTargetSize = getTargetSize();
                	double distanceErrorMargin = idealTargetSize - actualTargetSize;
                	if ( distanceErrorMargin > distanceAlignmentThreshold)
                	{
                		double distanceTarget = distanceErrorMargin*distanceAlignmentMultiplier;
                	    robot.pidDrive.setTarget(0.0, distanceTarget, robot.targetHeading, false, event);
                        sm.waitForSingleEvent(event, State.ALIGN_DISTANCE);
                	}
                	else
                	{
                		sm.setState(State.DEPLOY_GEAR);
                	}
                    break;
                    
                case DEPLOY_GEAR:
                    //
                    // Place gear on axle.
                    //
                    robot.mailbox.extend(extendTime, event);
                    sm.waitForSingleEvent(event, State.BACKUP);
                    break;

                case BACKUP:
                    //
                    // Backup from axle.
                    //
                    robot.pidDrive.setTarget(0.0, -backupDistance, robot.targetHeading, false, event);
                    sm.waitForSingleEvent(event, State.DONE);
                    break;

                case DONE:
                default:
                    //
                    // We are done.
                    //
                    done = true;
                    sm.stop();
                    break;
            }
            robot.traceStateInfo(elapsedTime, state.toString());
        }

        return done;
    }   //cmdPeriodic

    private double getHorizontalPosition()
    {
        Rect memeTangle = robot.visionTarget.getTargetRect();
        double midpoint = memeTangle.x + memeTangle.width / 2;
        return midpoint;
    }
    private double getTargetSize()
    {
    	Rect dankTangle = robot.visionTarget.getTargetRect();
    	double targetSize = dankTangle.height;
    	return targetSize;
    }

}   //class CmdVisionGearDeploy
