/*
 * Copyright (c) 2019 Titan Robotics Club (http://www.titanrobotics.com)
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

package common;

import java.util.Locale;

import trclib.TrcEvent;
import trclib.TrcPose2D;
import trclib.TrcRobot;
import trclib.TrcStateMachine;
import trclib.TrcTrigger;
import trclib.TrcUtil;

/**
 * This class implements the state machine that performs skystone detection using Vuforia. It assumes the robot
 * starts at the position right in front of the left-most or right-most of the 3-stone set and at a distance that
 * Vuforia is able to detect the skystone. This is refactored to a standalone RobotCommand so that the autonomous may
 * call it again to detect the second skystone and also both teams can share the same skystone detection code. At the
 * end of the execution, the robot should be right in front of the detected skystone at grabbing distance so that the
 * caller can just grab the stone right there. If for some reason Vuforia failed to detect the skystone, it will stop
 * in front of the last stone of the 3-stone set hoping it is the skystone. Even if it is not the skystone, we will
 * still score some points transporting it to the building zone.
 */
public class CmdSkystoneVision implements TrcRobot.RobotCommand
{
    private static final double VISION_TIMEOUT = 1.0;

    public static class Parameters
    {
        boolean useVisionTrigger = false;
        boolean assumeLeftIfNotFound = false;
        boolean scanTowardsWall = true;
        int scootCount = 0;
        double grabberOffsetX = 0.0;
        double grabberOffsetY = 0.0;
        double scanDirection = -1.0;

        public Parameters setUseVisionTrigger(boolean useVisionTrigger)
        {
            this.useVisionTrigger = useVisionTrigger;
            return this;
        }

        public Parameters setAssumeLeftIfNotFound(boolean assumeLeftIfNotFound)
        {
            this.assumeLeftIfNotFound = assumeLeftIfNotFound;
            return this;
        }

        public Parameters setScanTowardsWall(boolean scanTowardsWall)
        {
            this.scanTowardsWall = scanTowardsWall;
            this.scanDirection = scanTowardsWall? 1.0: -1.0;
            return this;
        }

        public Parameters setScootCount(int scootCount)
        {
            this.scootCount = scootCount;
            return this;
        }

        public Parameters setGrabberOffset(double grabberOffsetX, double grabberOffsetY)
        {
            this.grabberOffsetX = grabberOffsetX;
            this.grabberOffsetY = grabberOffsetY;
            return this;
        }

    }   //class Parameters

    private enum State
    {
        SCAN_FOR_SKYSTONE,
        SETUP_VISION,
        GET_TARGET_POSE,
        ALIGN_SKYSTONE,
        GOTO_SKYSTONE,
        ALIGN_SECOND_SKYSTONE,
        DONE
    }   //enum State

    private static final String moduleName = "CmdSkystoneVision";

    private final Robot robot;
    private final Parameters visionParams;
    private final TrcEvent event;
    private final TrcStateMachine<State> sm;
    private final TrcTrigger visionTrigger;
    private final double allianceDirection;
    private TrcPose2D skystonePose = null;
    private double visionTimeout = 0.0;
    private double skystoneXPos = 0.0;

    /**
     * Constructor: Create an instance of the object.
     *
     * @param robot specifies the robot object for providing access to various global objects.
     * @param autoChoices specifies the object containing all autonomous choices.
     * @param visionParams specifies the parameters for the SkystoneVision command.
     */
    public CmdSkystoneVision(Robot robot, CommonAuto.AutoChoices autoChoices, Parameters visionParams)
    {
        this.robot = robot;
        this.visionParams = visionParams;
        event = new TrcEvent(moduleName);
        sm = new TrcStateMachine<>(moduleName);
        visionTrigger = visionParams.useVisionTrigger?
                new TrcTrigger("VisionTrigger", this::isTriggered, this::targetDetected): null;
        allianceDirection = autoChoices.alliance == CommonAuto.Alliance.RED_ALLIANCE? 1.0: -1.0;
    }   //CmdSkystoneVision

    public void start()
    {
        if (!sm.isEnabled())
        {
            sm.start(visionParams.useVisionTrigger ? State.SCAN_FOR_SKYSTONE : State.SETUP_VISION);
        }
    }   //start

    public double getSkystoneXPos()
    {
        return skystoneXPos;
    }   //getSkystoneXPos

    private boolean isTriggered()
    {
        TrcPose2D pose = robot.getSkyStonePose();

        if (pose != null)
        {
            skystonePose = pose;
            robot.globalTracer.traceInfo(
                    "visionTrigger", ">>> Skystone found at x=%.1f, y=%.1f.",
                    skystonePose.x, skystonePose.y);
            robot.speak(String.format(Locale.US, "Sky stone found at %.1f inches.", skystonePose.x));
        }

        return pose != null;
    }   //isTriggered

    private void targetDetected()
    {
        if (robot.pidDrive.isActive())
        {
            robot.pidDrive.cancel();
        }
    }   //targetDetected

    //
    // Implements the TrcRobot.RobotCommand interface.
    //

    /**
     * This method checks if the current RobotCommand  is running.
     *
     * @return true if the command is running, false otherwise.
     */
    @Override
    public boolean isActive()
    {
        return sm.isEnabled();
    }   //isActive

    /**
     * This method cancels the command if it is active.
     */
    @Override
    public void cancel()
    {
        if (robot.pidDrive.isActive())
        {
            robot.pidDrive.cancel();
        }

        if (visionTrigger != null)
        {
            visionTrigger.setEnabled(false);
        }

        sm.stop();
    }   //cancel

    /**
     * This method must be called periodically by the caller to drive the command sequence forward.
     *
     * @param elapsedTime specifies the elapsed time in seconds since the start of the robot mode.
     * @return true if the command sequence is completed, false otherwise.
     */
    @Override
    public boolean cmdPeriodic(double elapsedTime)
    {
        State state = sm.checkReadyAndGetState();

        if (state == null)
        {
            robot.dashboard.displayPrintf(1, "State: disabled or waiting...");
        }
        else
        {
            double xTarget = 0.0;
            double yTarget = 0.0;
            double turnTarget = 0.0;
            State nextState;

            robot.dashboard.displayPrintf(1, "State: %s", state);

            switch (state)
            {
                case SCAN_FOR_SKYSTONE:
                    visionTrigger.setEnabled(true);
                    //
                    // Strafe across all three stone to find the skystone. When a skystone is spotted, the strafe
                    // will be interrupted by visionTrigger.
                    // Note: If we are scanning the stone set closer to the wall and towards the wall, the scan
                    // distance is shorter or else we will run into the wall.
                    //
                    xTarget = visionParams.scanTowardsWall &&
                              Math.abs(robot.driveBase.getXPosition()) < RobotInfo.ABS_LOADING_ZONE_ROBOT_START_X_MID?
                                RobotInfo.SKYSTONE_SCAN_DISTANCE_WALL: RobotInfo.SKYSTONE_SCAN_DISTANCE_FAR;
                    xTarget *= allianceDirection * visionParams.scanDirection;
                    robot.pidDrive.setRelativeXTarget(xTarget, event);
                    sm.waitForSingleEvent(event, State.ALIGN_SKYSTONE);
                    break;

                case SETUP_VISION:
                    //
                    // Vuforia may take time to detect target, set a timeout for retrying.
                    //
                    visionTimeout = TrcUtil.getCurrentTime() + VISION_TIMEOUT;
                    sm.setState(State.GET_TARGET_POSE);
                    //
                    // Intentionally falling through to the next state.
                    //
                case GET_TARGET_POSE:
                    //
                    // Get the detected skystone pose. If pose is null, it could be because Vuforia is still
                    // processing the image. So keep repeating this state until it finds the target or vision
                    // timeout has expired.
                    //
                    skystonePose = robot.getSkyStonePose();
                    if (skystonePose == null)
                    {
                        if (TrcUtil.getCurrentTime() > visionTimeout)
                        {
                            if (visionParams.scootCount > 0)
                            {
                                StringBuilder sentence = new StringBuilder("Not found, ");

                                robot.globalTracer.traceInfo(
                                        "GetTargetPose", ">>> Skystone not found, try next stone.");
                                visionParams.scootCount--;
                                xTarget = -9.0*allianceDirection*visionParams.scanDirection;
                                //
                                // If this is the last stone, don't need to check if it's a skystone, just grab and go.
                                // Note that our stone grabber design cannot grab the last stone touching the perimeter
                                // wall so if we are dealing with the 3-stone set closer to the wall, we will only
                                // scoot once to get to the middle stone and not attempting the last stone since we
                                // can't get to it anyway.
                                //
                                if (visionParams.scootCount == 0)
                                {
//                                    xTarget = -visionParams.giveUpDistance * allianceDirection * visionParams.scanDirection;
                                    nextState = State.ALIGN_SKYSTONE;
                                    sentence.append("give up.");
                                }
                                else
                                {
                                    nextState = State.SETUP_VISION;
                                    sentence.append("try next.");
                                }
                                robot.speak(sentence.toString());
                                robot.pidDrive.setRelativeXTarget(xTarget, event);
                                sm.waitForSingleEvent(event, nextState);
                            }
                            else if (visionParams.assumeLeftIfNotFound)
                            {
                                //
                                // We came here because we are either doing SINGLE_SKYSTONE or DOUBLE_SKYSTONE for
                                // the first skystone and we did not find it. It's probably because our grabber
                                // is blocking the camera's left corner. So we will assume the left stone is the
                                // skystone.
                                //
                                robot.globalTracer.traceInfo(
                                        "GetTargetPose", ">>> Skystone not found, assume left.");
                                robot.speak("Not found, assume left.");
                                sm.setState(State.ALIGN_SKYSTONE);
                            }
                            else
                            {
                                //
                                // Should never come here because the code above should have handled it when
                                // scootCount was 1 and decremented to 0 but will handle it just in case.
                                //
                                robot.globalTracer.traceInfo(
                                        "GetTargetPose", ">>> Skystone not found, giving up.");
                                robot.speak("Not found, give up.");
                                sm.setState(State.ALIGN_SKYSTONE);
                            }
                        }
                    }
                    else
                    {
                        final String msgTag = "getTargetPose";
                        // Vuforia found the skystone.
                        robot.globalTracer.traceInfo(
                                msgTag, ">>> Skystone found at x=%.1f, y=%.1f.", skystonePose.x, skystonePose.y);
                        if (Math.abs(skystonePose.x) > 8.0)
                        {
                            robot.globalTracer.traceInfo(msgTag, ">>> Vuforia is way off, ignore it!");
                            robot.speak("Vuforia is way off.");
                            skystonePose = null;
                        }
                        else
                        {
                            robot.speak(String.format(
                                    Locale.US, "Sky stone found at %.1f inches.", skystonePose.x));
                        }
                        sm.setState(State.ALIGN_SKYSTONE);
                    }
                    break;

                case ALIGN_SKYSTONE:
                    //
                    // Either we found the skystone or we pretend the one in front is the skystone. In the case
                    // assumeLeftIfNotFound is true, we scoot a little to the stone on the left.
                    // If we did find the skystone, it may be mis-aligned, so let's align to it before we grab it.
                    //
                    if (visionTrigger != null)
                    {
                        visionTrigger.setEnabled(false);
                    }

                    xTarget = 0.0;
                    if (skystonePose != null)
                    {
                        xTarget += skystonePose.x + visionParams.grabberOffsetX;
                    }
                    else if (visionParams.assumeLeftIfNotFound)
                    {
                        xTarget -= 9.0;
                    }

                    if (xTarget == 0.0)
                    {
                        if (skystoneXPos != 0.0)
                        {
                            //
                            // We are going for the second skystone but Vuforia can't detect it or it's giving us
                            // bogus distance. Since we already know where the second skystone is, let's bump the
                            // wall to realign the robot and just go there.
                            //
                            robot.globalTracer.traceInfo("AlignSkystone",
                                    ">>> Vuforia can't detect 2nd skystone, hit the wall to realign!");
                            xTarget = RobotInfo.ABS_ROBOT_X_ORIGIN;
                            xTarget *= allianceDirection;
                            robot.pidDrive.setAbsoluteXTarget(xTarget, event);
                            sm.waitForSingleEvent(event, State.ALIGN_SECOND_SKYSTONE);
                            break;
                        }
                        else
                        {
                            //
                            // Vuforia did not detect the first skystone, let's assume it's right in front.
                            //
                            sm.setState(State.GOTO_SKYSTONE);
                            //
                            // Intentionally falling through to the next state.
                            //
                        }
                    }
                    else
                    {
                        robot.pidDrive.setRelativeXTarget(xTarget, event);
                        sm.waitForSingleEvent(event, State.GOTO_SKYSTONE);
                        break;
                    }

                case GOTO_SKYSTONE:
                    skystoneXPos = robot.driveBase.getXPosition();
                    yTarget = RobotInfo.ABS_GRAB_SKYSTONE_POS_Y - visionParams.grabberOffsetY;
                    robot.pidDrive.setAbsoluteYTarget(yTarget, event);
                    sm.waitForSingleEvent(event, State.DONE);
                    break;

                case ALIGN_SECOND_SKYSTONE:
                    //
                    // We just hit the wall to realign the robot. Let's reset the robot's X odometry.
                    //
                    TrcPose2D pose = robot.driveBase.getFieldPosition();
                    pose.x = RobotInfo.ABS_ROBOT_X_ORIGIN * allianceDirection;
                    robot.driveBase.setFieldPosition(pose);

                    pose = robot.pidDrive.getAbsoluteTargetPose();
                    pose.x = RobotInfo.ABS_ROBOT_X_ORIGIN * allianceDirection;
                    robot.pidDrive.setAbsoluteTargetPose(pose);

                    xTarget = skystoneXPos - 24.0*allianceDirection;
                    robot.pidDrive.setAbsoluteXTarget(xTarget, event);
                    sm.waitForSingleEvent(event, State.GOTO_SKYSTONE);
                    break;

                case DONE:
                default:
                    //
                    // We are done.
                    //
                    cancel();
                    break;
            }

            robot.traceStateInfo(sm.getState());
        }

        return !sm.isEnabled();
    }   //cmdPeriodic

}   //class CmdSkystoneVision
