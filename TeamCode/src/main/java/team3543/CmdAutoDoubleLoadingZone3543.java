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

package team3543;

import common.CmdSkystoneVision;
import common.CommonAuto;
import common.RobotInfo;
import common.SimplePidDrive;
import trclib.TrcEvent;
import trclib.TrcPidController;
import trclib.TrcPose2D;
import trclib.TrcRobot;
import trclib.TrcStateMachine;
import trclib.TrcTimer;

class CmdAutoDoubleLoadingZone3543 implements TrcRobot.RobotCommand
{
    private static final boolean debugXPid = true;
    private static final boolean debugYPid = true;
    private static final boolean debugTurnPid = true;

    private enum State
    {
        BEGIN,
        START_DELAY,
        MOVE_TO_SKYSTONE_LINE,
        MOVE_TO_FIRST_STONE,
        DO_VISION,
        GO_DOWN_ON_SKYSTONE,
        GRAB_SKYSTONE,
        PULL_SKYSTONE,
        START_EXTENDER_ARM_RETRACTION,
        TURN_TOWARDS_FOUNDATION,
        GOTO_FOUNDATION,
        TURN_BACK_TO_FOUNDATION,
        APPROACH_FOUNDATION,
        DROP_SKYSTONE,
        BACK_OFF_FOUNDATION,
        TURN_TOWARDS_SKYSTONES,
        MOVE_BACK_TO_SKYSTONES,
        TURN_BACK_TO_SKYSTONES,
        FINISH_DELAY,
        PULL_FOUNDATION_TO_WALL,
        UNHOOK_FOUNDATION,
        CLEAR_OF_FOUNDATION,
        MOVE_TO_FOUNDATION_SIDE,
        PUSH_FOUNDATION_TO_WALL,
        MOVE_BACK_TO_WALL,
        MOVE_UNDER_BRIDGE,
        SKIP_MOVE_FOUNDATION_PARK_WALL,
        MOVE_TOWARDS_CENTER,
        START_RETRACTIONS,
        DONE
    }   //enum State

    private static final String moduleName = "CmdAutoDoubleLoadingZone3543";

    private final Robot3543 robot;
    private final CommonAuto.AutoChoices autoChoices;
    private final CmdSkystoneVision.Parameters visionParams;
    private final TrcTimer timer;
    private final TrcEvent event;
    private final TrcStateMachine<State> sm;
    private final double allianceDirection;
    private final SimplePidDrive<State> simplePidDrive;
    private final TrcPidController xPidCtrl;
    private final TrcPidController yPidCtrl;
    private final TrcPidController turnPidCtrl;
    private CmdSkystoneVision skystoneVisionCommand = null;
    private TrcPidController.PidCoefficients savedYPidCoeff = null;
    private int skystonesDropped = 0;

    /**
     * Constructor: Create an instance of the object.
     *
     * @param robot specifies the robot object for providing access to various global objects.
     */
    CmdAutoDoubleLoadingZone3543(Robot3543 robot, CommonAuto.AutoChoices autoChoices)
    {
        robot.globalTracer.traceInfo(moduleName, "robot=%s", robot);

        this.robot = robot;
        this.autoChoices = autoChoices;
        visionParams = new CmdSkystoneVision.Parameters()
                .setUseVisionTrigger(robot.preferences.useVisionTrigger)
                .setScanTowardsWall(false)
                .setScootCount(2)
                .setGrabberOffset(RobotInfo3543.GRABBER_OFFSET_X, RobotInfo3543.GRABBER_OFFSET_Y);
        timer = new TrcTimer(moduleName);
        event = new TrcEvent(moduleName);
        sm = new TrcStateMachine<>(moduleName);
        skystoneVisionCommand = new CmdSkystoneVision(robot, autoChoices, visionParams);
        allianceDirection = autoChoices.alliance == CommonAuto.Alliance.RED_ALLIANCE ? 1.0 : -1.0;
        simplePidDrive = new SimplePidDrive<>(robot.pidDrive, event, sm);
        xPidCtrl = robot.pidDrive.getXPidCtrl();
        yPidCtrl = robot.pidDrive.getYPidCtrl();
        turnPidCtrl = robot.pidDrive.getTurnPidCtrl();
        sm.start(State.BEGIN);
    }   //CmdAutoLoadingZone3543

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

        xPidCtrl.setOutputLimit(1.0);
        yPidCtrl.setOutputLimit(1.0);
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
            boolean traceState = true;
            double xTarget = 0.0;
            double yTarget = 0.0;
            double turnTarget = 0.0;
            State nextState;

            robot.dashboard.displayPrintf(1, "State: %s", state);

            switch (state)
            {
                case BEGIN:
                    //
                    // Set the robot's absolute field starting position.
                    //
                    double startX = RobotInfo.ABS_LOADING_ZONE_ROBOT_START_X_WALL * allianceDirection;
                    robot.pidDrive.setAbsolutePose(new TrcPose2D(startX, RobotInfo.ABS_ROBOT_START_Y));

                    xPidCtrl.setNoOscillation(true);
                    yPidCtrl.setNoOscillation(true);
                    turnPidCtrl.setNoOscillation(true);
                    sm.setState(State.START_DELAY);
                    //
                    // Intentionally falling through to the next state.
                    //
                case START_DELAY:
                    //
                    // Do start delay if any.
                    //
                    if (autoChoices.startDelay == 0.0)
                    {
                        sm.setState(State.MOVE_TO_SKYSTONE_LINE);
                        //
                        // Intentionally falling through to the next state.
                        //
                    }
                    else
                    {
                        timer.set(autoChoices.startDelay, event);
                        sm.waitForSingleEvent(event, State.MOVE_TO_SKYSTONE_LINE);
                        break;
                    }

                case MOVE_TO_SKYSTONE_LINE:
                    //
                    // Move closer slowly to a distance so Vuforia can detect the target.
                    //
                    robot.setFlashLightOn(true, true);
                    robot.extenderArm.setPosition(4.5);
                    robot.grabber.release();
                    robot.wrist.extend();

                    xPidCtrl.setOutputLimit(0.5);
                    yPidCtrl.setOutputLimit(0.5);
                    yTarget = 20.0;
                    simplePidDrive.setRelativeYTarget(yTarget, State.MOVE_TO_FIRST_STONE);
                    break;

                case MOVE_TO_FIRST_STONE:
                    xTarget = RobotInfo.ABS_FAR_STONE1_X * allianceDirection;
                    simplePidDrive.setAbsoluteXTarget(xTarget, State.DO_VISION);
                    break;

                case DO_VISION:
                    //
                    // Do vision to detect and go to the skystone. If vision did not detect skystone, it will stop
                    // at the last stone.
                    //

                    if (skystonesDropped != 0) {
                        visionParams.setScootCount(0);
                    }
                    skystoneVisionCommand.start();

                    if (skystoneVisionCommand.cmdPeriodic(elapsedTime))
                    {
                        //
                        // Skystone vision is done, continue on.
                        //
                        sm.setState(State.GO_DOWN_ON_SKYSTONE);
                        //
                        // Intentionally falling through to the next state.
                        //
                    }
                    else
                    {
                        traceState = false;
                        break;
                    }

                    sm.setState(State.GO_DOWN_ON_SKYSTONE);

                case GO_DOWN_ON_SKYSTONE:
                    robot.extenderArm.setPosition(RobotInfo3543.EXTENDER_ARM_PICKUP_POS, event);
                    sm.waitForSingleEvent(event, State.GRAB_SKYSTONE);
                    break;

                case GRAB_SKYSTONE:
                    //
                    // Don't need to wait for the grab to finish before moving. Can tune this down to minimum for
                    // saving time.
                    //
                    robot.grabber.grab(3.0, event);
                    sm.waitForSingleEvent(event, State.PULL_SKYSTONE);
                    break;

                case PULL_SKYSTONE:
                    yTarget = -8.0;
                    simplePidDrive.setRelativeYTarget(yTarget, State.START_EXTENDER_ARM_RETRACTION);
                    break;

                case START_EXTENDER_ARM_RETRACTION:
                    //
                    // Bring the extender arm back so we don't run into the bridge during travel.
                    //
                    nextState = autoChoices.strafeToFoundation?
                            State.GOTO_FOUNDATION: State.TURN_TOWARDS_FOUNDATION;
                    robot.extenderArm.setPosition(RobotInfo3543.EXTENDER_ARM_CARRY_POS, event);
                    sm.waitForSingleEvent(event, nextState);
                    break;

                case TURN_TOWARDS_FOUNDATION:
                    turnTarget = 90.0 * allianceDirection;
                    simplePidDrive.setAbsoluteHeadingTarget(turnTarget, State.GOTO_FOUNDATION);
                    break;

                case GOTO_FOUNDATION:
                    nextState = autoChoices.strafeToFoundation?
                            State.APPROACH_FOUNDATION: State.TURN_BACK_TO_FOUNDATION;
                    // Need to go full speed to save time.
                    xPidCtrl.setOutputLimit(1.0);
                    yPidCtrl.setOutputLimit(1.0);
                    //
                    // We might need to add something after this that readjusts the robot's position
                    // to center after it drops off the second SkyStone. Not sure yet.
                    //
                    xTarget = (skystonesDropped == 0? RobotInfo.ABS_FOUNDATION_DROP_FAR_X:
                            RobotInfo.ABS_FOUNDATION_DROP_NEAR_X) * allianceDirection;
                    simplePidDrive.setAbsoluteXTarget(xTarget, nextState);
                    break;

                case TURN_BACK_TO_FOUNDATION:
                    turnTarget = 0.0;
                    simplePidDrive.setAbsoluteHeadingTarget(turnTarget, State.APPROACH_FOUNDATION);
                    break;

                case APPROACH_FOUNDATION:
                    //
                    // Raise the elevator and extender arm to drop position while approaching the foundation.
                    //
                    robot.elevator.setPosition(RobotInfo3543.ELEVATOR_DROP_HEIGHT);
                    robot.extenderArm.setPosition(RobotInfo3543.EXTENDER_ARM_DROP_POS);
                    yTarget =  11.0;
                    simplePidDrive.setRelativeYTarget(yTarget, State.DROP_SKYSTONE);
                    break;

                case DROP_SKYSTONE:
                    robot.grabber.release();
                    skystonesDropped++;
                    if (skystonesDropped == 1) {
                        timer.set(2.0, event);
                        sm.waitForSingleEvent(event, State.BACK_OFF_FOUNDATION);
                    } else {
                        if (autoChoices.moveFoundation) {
                            //
                            // Start dropping the skystone but we don't have to wait for it. Just wait for the foundation
                            // to be latched and the robot can move immediately. The skystone can continue to drop enroute.
                            //
                            nextState = State.FINISH_DELAY;
                            robot.frontFoundationLatch.grab(event);
                        } else {
                            //
                            // We are not moving the foundation so we need to wait until the skystone is dropped.
                            //
                            nextState = autoChoices.parkUnderBridge == CommonAuto.ParkPosition.PARK_CLOSE_TO_CENTER ?
                                    State.MOVE_TOWARDS_CENTER : State.SKIP_MOVE_FOUNDATION_PARK_WALL;
                            timer.set(1.5, event);
                        }
                        sm.waitForSingleEvent(event, nextState);
                    }
                    break;

                case BACK_OFF_FOUNDATION:
                    robot.extenderArm.zeroCalibrate();
                    robot.elevator.zeroCalibrate();
                    yTarget = -11.0;
                    nextState = autoChoices.strafeToFoundation?
                            State.MOVE_BACK_TO_SKYSTONES: State.TURN_TOWARDS_SKYSTONES;
                    simplePidDrive.setRelativeYTarget(yTarget, nextState);
                    break;

                case TURN_TOWARDS_SKYSTONES:
                    turnTarget = -90.0 * allianceDirection;
                    simplePidDrive.setAbsoluteHeadingTarget(turnTarget, State.MOVE_BACK_TO_SKYSTONES);
                    break;

                case MOVE_BACK_TO_SKYSTONES:
                    double nextSkystoneXPos = skystoneVisionCommand.getSkystoneXPos() - 24.0*allianceDirection;
                    //
                    // If the SkyStone we first picked up was the third one, we will not be able to
                    // get the corresponding stone on the wall side, so just get the furthest left.
                    //
//                    if (skystonePlacement < 2) {
//                        xTarget = RobotInfo.ABS_WALL_FIRST_STONE_X +
//                                skystoneVisionCommand.getSkystonePlacement() * 6.0;
//                    } else {
//                        xTarget = RobotInfo.ABS_FAR_FIRST_STONE_X;
//                    }

                    // Quick fix to prevent robot from running into wall if SkyStone 1
                    if (Math.abs(nextSkystoneXPos) <= 9.0) {
                        xTarget = RobotInfo.ABS_FAR_STONE3_X*allianceDirection;
                    } else {
                        xTarget = nextSkystoneXPos;
                    }

                    nextState = autoChoices.strafeToFoundation?
                            State.DO_VISION: State.TURN_BACK_TO_SKYSTONES;
                    simplePidDrive.setAbsoluteXTarget(xTarget, nextState);
                    break;

                case TURN_BACK_TO_SKYSTONES:
                    turnTarget = 0.0;
                    simplePidDrive.setAbsoluteHeadingTarget(turnTarget, State.DO_VISION);
                    break;

                case FINISH_DELAY:
                    //
                    // Do finish delay if any.
                    // This is for performing a double-skystone autonomous with our alliance partner.
                    // In this strategy, we will go first and drop the first skystone. Our partner should follow
                    // closely. In case our partner is slow, we will wait for them to finish dropping the second
                    // skystone and clear of the foundation before we pull the foundation to the wall.
                    // Our full autonomous takes about 20 seconds, so we can afford to wait up to approx. 10 seconds.
                    // But for safety margin, we should not wait more than 5-6 seconds.
                    //
                    if (autoChoices.finishDelay == 0.0)
                    {
                        sm.setState(State.PULL_FOUNDATION_TO_WALL);
                        //
                        // Intentionally falling through to the next state.
                        //
                    }
                    else
                    {
                        timer.set(autoChoices.finishDelay, event);
                        sm.waitForSingleEvent(event, State.PULL_FOUNDATION_TO_WALL);
                        break;
                    }

                case PULL_FOUNDATION_TO_WALL:
                    //
                    // Pulling the foundation added a lot of friction that causes the wheels to slip. As a result,
                    // the drivebase odometry will no longer be accurate. Let's drive the robot with extra distance
                    // to make sure it hits the wall and we will correct the odometry and Absolute Target Pose in the
                    // Y direction.
                    //
                    savedYPidCoeff = yPidCtrl.getPidCoefficients();
                    TrcPidController.PidCoefficients loadedYPidCoeff = savedYPidCoeff.clone();
                    loadedYPidCoeff.kP = RobotInfo3543.ENCODER_Y_LOADED_KP;
                    yPidCtrl.setPidCoefficients(loadedYPidCoeff);
                    yTarget = -43.0;
                    simplePidDrive.setRelativeYTarget(yTarget, State.UNHOOK_FOUNDATION);
                    break;

                case UNHOOK_FOUNDATION:
                    if (savedYPidCoeff != null)
                    {
                        yPidCtrl.setPidCoefficients(savedYPidCoeff);
                        savedYPidCoeff = null;
                    }
                    // Correct odometry and absTargetPose after wheel slippage.
                    TrcPose2D pose = robot.driveBase.getAbsolutePose();
                    pose.y = RobotInfo.ABS_ROBOT_START_Y;
                    robot.driveBase.setAbsolutePose(pose);
                    pose = robot.pidDrive.getAbsoluteTargetPose();
                    pose.y = RobotInfo.ABS_ROBOT_START_Y;
                    robot.pidDrive.setAbsoluteTargetPose(pose);
                    //
                    // Release the foundation and continue.
                    //
                    robot.frontFoundationLatch.release(event);
                    sm.waitForSingleEvent(event, State.CLEAR_OF_FOUNDATION);
                    break;

                case CLEAR_OF_FOUNDATION:
                    //
                    // Move the robot clear of the foundation otherwise the foundation move won't score if the robot
                    // is still in contact with the foundation. Besides, we need to bump the foundations closer to
                    // the wall to make sure it's in the building site.
                    //
                    xTarget = RobotInfo.ABS_NEXT_TO_PARTNER_PARK_X * allianceDirection;
                    simplePidDrive.setAbsoluteXTarget(xTarget, State.MOVE_TO_FOUNDATION_SIDE);
                    break;

                case MOVE_TO_FOUNDATION_SIDE:
                    //
                    // We can safely retract everything here because the skystone should be dropped by now.
                    //
                    robot.extenderArm.setPosition(RobotInfo3543.EXTENDER_ARM_RETRACT_POS);
                    robot.wrist.retract();
                    robot.elevator.zeroCalibrate();
                    //
                    // Move to the side of the foundation so we can bump it to the wall.
                    //
                    yTarget = RobotInfo.ABS_CENTER_BRIDGE_PARK_Y;
                    simplePidDrive.setAbsoluteYTarget(yTarget, State.PUSH_FOUNDATION_TO_WALL);
                    break;

                case PUSH_FOUNDATION_TO_WALL:
                    //
                    // Bump the foundation toward the wall to make sure it lands inside the building site.
                    //
                    xTarget = 16.0 * allianceDirection;
                    nextState = autoChoices.parkUnderBridge == CommonAuto.ParkPosition.PARK_CLOSE_TO_CENTER?
                            State.MOVE_UNDER_BRIDGE: State.MOVE_BACK_TO_WALL;
                    simplePidDrive.setRelativeXTarget(xTarget, nextState);
                    break;

                case MOVE_BACK_TO_WALL:
                    nextState = autoChoices.parkUnderBridge == CommonAuto.ParkPosition.NO_PARK?
                            State.DONE: State.MOVE_UNDER_BRIDGE;
                    yTarget = RobotInfo.ABS_ROBOT_START_Y;
                    simplePidDrive.setAbsoluteYTarget(yTarget, nextState);
                    break;

                case MOVE_UNDER_BRIDGE:
                    xTarget = RobotInfo.ABS_UNDER_BRIDGE_PARK_X * allianceDirection;
                    simplePidDrive.setAbsoluteXTarget(xTarget, State.DONE);
                    break;

                case SKIP_MOVE_FOUNDATION_PARK_WALL:
                    yTarget = RobotInfo.ABS_ROBOT_START_Y;
                    simplePidDrive.setAbsoluteYTarget(yTarget, State.START_RETRACTIONS);
                    break;

                case MOVE_TOWARDS_CENTER:
                    yTarget = RobotInfo.ABS_CENTER_BRIDGE_PARK_Y;
                    simplePidDrive.setAbsoluteYTarget(yTarget, State.START_RETRACTIONS);
                    break;

                case START_RETRACTIONS:
                    robot.elevator.zeroCalibrate();
                    robot.extenderArm.zeroCalibrate();
                    robot.wrist.retract(event);

                    nextState = autoChoices.parkUnderBridge == CommonAuto.ParkPosition.NO_PARK?
                            State.DONE: State.MOVE_UNDER_BRIDGE;
                    sm.waitForSingleEvent(event, nextState);
                    break;

                case DONE:
                default:
                    //
                    // We are done.
                    //
                    cancel();
                    break;
            }

            if (traceState)
            {
                robot.traceStateInfo(elapsedTime, state.toString(), xTarget, yTarget, turnTarget);
            }
        }

        if (robot.pidDrive.isActive() && (debugXPid || debugYPid || debugTurnPid))
        {
            if (robot.battery != null)
            {
                robot.globalTracer.traceInfo("Battery", "Voltage=%5.2fV (%5.2fV)",
                        robot.battery.getVoltage(), robot.battery.getLowestVoltage());
            }

            robot.globalTracer.traceInfo(moduleName, "RobotPose: %s", robot.driveBase.getAbsolutePose());

            if (debugXPid && xPidCtrl != null)
            {
                xPidCtrl.printPidInfo(robot.globalTracer, elapsedTime);
            }

            if (debugYPid)
            {
                yPidCtrl.printPidInfo(robot.globalTracer, elapsedTime);
            }

            if (debugTurnPid)
            {
                turnPidCtrl.printPidInfo(robot.globalTracer, elapsedTime);
            }
        }

        return !sm.isEnabled();
    }   //cmdPeriodic

}   //class CmdAutoLoadingZone3543