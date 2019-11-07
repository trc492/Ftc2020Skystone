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

package team6541;

import common.CommonAuto;
import common.Robot;
import trclib.TrcAbsTargetDrive;
import trclib.TrcEvent;
import trclib.TrcRobot;
import trclib.TrcStateMachine;
import trclib.TrcTimer;

public class CmdAutoBuildingZone6541 implements TrcRobot.RobotCommand
{
    private static final boolean useSimpleRoute = true;

    private enum State
    {
        DO_DELAY,
        DECIDE_INITIAL_ROUTE,
        DRIVE_DIRECTLY_UNDER_BRIDGE_IF_NOT_MOVING_FOUNDATION,
        RAISE_ELEVATOR,
        DRIVE_UNTIL_GRABBER_ALIGNED_WITH_FOUNDATION,
        CRAB_TO_ALIGN_WITH_FOUNDATION,
        GO_FORWARD_A_BIT_MORE,
        HOOK_FOUNDATION,
        ROTATE_FOUNDATION_TO_CORNER,
        PUSH_FOUNDATION_TO_WALL,
        UNHOOK_FOUNDATION,
        BACK_OFF_FROM_FOUNDATION,
        LOWER_ELEVATOR_AFTER_BACKING_OFF,
        CRAB_TOWARD_WALL,
        ALIGN_WITH_BRIDGE,
        PARK_UNDER_BRIDGE,
        DONE
    }   //enum State

    private static final String moduleName = "CmdAutoBuildingZone3543";

    private final Robot robot;
    private final CommonAuto.AutoChoices autoChoices;
    private final TrcEvent event;
    private final TrcTimer timer;
    private final TrcStateMachine<State> sm;
    private final TrcAbsTargetDrive<State> absTargetDrive;

    public CmdAutoBuildingZone6541(Robot robot, CommonAuto.AutoChoices autoChoices)
    {
        this.robot = robot;
        this.autoChoices = autoChoices;
        event = new TrcEvent(moduleName);
        timer = new TrcTimer(moduleName);
        sm = new TrcStateMachine<>(moduleName);
        sm.start(State.DO_DELAY);
        absTargetDrive = new TrcAbsTargetDrive<>(
                "CmdAutoBuildingZone3543", robot.driveBase, robot.pidDrive, event, sm);
    }   //CmdAutoBuildingZone3543

    @Override
    public boolean isActive()
    {
        return sm.isEnabled();
    }   //isActive

    @Override
    public void cancel()
    {
        robot.pidDrive.getXPidCtrl().restoreOutputLimit();
        robot.pidDrive.getYPidCtrl().restoreOutputLimit();
        sm.stop();
    }   //cancel

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
            double xTarget = 0.0, yTarget = 0.0, turnTarget = 0.0;
            State nextState = null;

            robot.dashboard.displayPrintf(1, "State: %s", state);
            switch (state)
            {
                case DO_DELAY:
                    //
                    // Do delay if any.
                    //
                    robot.pidDrive.getXPidCtrl().saveAndSetOutputLimit(0.5);
                    robot.pidDrive.getYPidCtrl().saveAndSetOutputLimit(0.5);

                    if (autoChoices.delay == 0.0)
                    {
                        sm.setState(State.DECIDE_INITIAL_ROUTE);
                        //
                        // Intentionally falling through to the next state.
                        //
                    }
                    else
                    {
                        timer.set(autoChoices.delay, event);
                        sm.waitForSingleEvent(event, State.DECIDE_INITIAL_ROUTE);
                        break;
                    }

                case DECIDE_INITIAL_ROUTE:
                    // are we going to move the foundation?
                    // if so, we will set the next state to raising the elevator and moving the foundation.
                    // if not so, we will check if we will park under the bridge.
                    // if we will park under the bridge, then we will drive forwards directly under the bridge.
                    // if we will not park under the bridge, then we will do nothing, and go to done.
                    nextState = autoChoices.moveFoundation ?
                            State.RAISE_ELEVATOR :
                            (autoChoices.parkUnderBridge ?
                                    State.DRIVE_DIRECTLY_UNDER_BRIDGE_IF_NOT_MOVING_FOUNDATION :
                                    State.DONE);
                    sm.setState(nextState);
                    break;

                case DRIVE_DIRECTLY_UNDER_BRIDGE_IF_NOT_MOVING_FOUNDATION:
                    // if not moving the foundation and parking under bridge, drive directly forwards to bridge.
                    yTarget = 24.0;
                    absTargetDrive.setYTarget(yTarget, State.DONE);
                    break;

                case RAISE_ELEVATOR:
                    // raise the elevator to prevent end effector from colliding with bridge.
                    robot.elevator.setPosition(15.0, event, 0.0);
                    sm.waitForSingleEvent(event, State.DRIVE_UNTIL_GRABBER_ALIGNED_WITH_FOUNDATION);
                    break;

                case DRIVE_UNTIL_GRABBER_ALIGNED_WITH_FOUNDATION:
                    // drive forward 3 inches to prevent robot base from colliding orthogonally to foundation.
                    yTarget = 3.0;
                    absTargetDrive.setYTarget(yTarget, State.CRAB_TO_ALIGN_WITH_FOUNDATION);
                    break;

                case CRAB_TO_ALIGN_WITH_FOUNDATION:
                    // crab over to the foundation, and target the center of the foundation with the grabber.
                    xTarget = autoChoices.alliance == CommonAuto.Alliance.RED_ALLIANCE ? 50.0 : -50.0;
                    absTargetDrive.setXTarget(xTarget, State.GO_FORWARD_A_BIT_MORE);
                    break;

                case GO_FORWARD_A_BIT_MORE:
                    // drive backward 5 inches to align grabber vertically with foundation.
                    yTarget = -5.0;
                    absTargetDrive.setYTarget(yTarget, State.HOOK_FOUNDATION);
                    break;

                case HOOK_FOUNDATION:
                    // hook the foundation.
                    // after that, rotate a magnitude of 60 degrees to face the corner.
                    nextState = State.ROTATE_FOUNDATION_TO_CORNER;
                    if (robot.foundationLatch != null)
                    {
                        robot.foundationLatch.grab(event);
                        sm.waitForSingleEvent(event, nextState);
                    }
                    else
                    {
                        sm.setState(nextState);
                    }
                    break;

                case ROTATE_FOUNDATION_TO_CORNER:
                    // rotate with an angular displacement magnitude of 60 degrees to face the building zone corner.
                    // direction of rotation will vary based on red alliance or blue alliance.
                    // if red alliance, rotate 60 degrees clockwise.
                    // if blue alliance, rotate 60 degrees anti-clockwise.
                    turnTarget = autoChoices.alliance == CommonAuto.Alliance.RED_ALLIANCE ? 60.0 : -60.0;
                    absTargetDrive.setTurnTarget(turnTarget, State.PUSH_FOUNDATION_TO_WALL);
                    break;

                case PUSH_FOUNDATION_TO_WALL:
                    // pid drive is unreliable due to wheel slip when robot is overcoming significant kinetic friction,
                    // which is present when the robot is pushing heavy loads such as foundation.
                    // as a result, blindly drive the board into the corner for 2 seconds, applying moderate torque.
                    // afterwards, unhook the foundation and decide whether to park under the bridge.
                    robot.driveBase.holonomicDrive(0.0, -0.6, 0.0);
                    timer.set(1.0, event);
                    sm.waitForSingleEvent(event, State.UNHOOK_FOUNDATION);
                    break;

                case UNHOOK_FOUNDATION:
                    // will we park under the bridge?
                    // for both cases, we will first unhook the foundation.
                    // if we are parking under bridge, we will drive to the bridge.
                    // otherwise, we will remain at the site of the foundation until match end. (set state to done)
                    nextState = autoChoices.parkUnderBridge ?
                            State.BACK_OFF_FROM_FOUNDATION :
                            State.DONE;
                    if(robot.foundationLatch != null)
                    {
                        robot.foundationLatch.release(event);
                        sm.waitForSingleEvent(event, nextState);
                    }
                    else
                    {
                        sm.setState(nextState);
                    }
                    break;

                case BACK_OFF_FROM_FOUNDATION:
                    yTarget = 4.0;
                    absTargetDrive.setYTarget(yTarget, State.LOWER_ELEVATOR_AFTER_BACKING_OFF);
                    break;

                case LOWER_ELEVATOR_AFTER_BACKING_OFF:
                    robot.elevator.zeroCalibrate();
                    timer.set(2.0, event);
                    sm.waitForSingleEvent(event, State.CRAB_TOWARD_WALL);
                    break;

                case CRAB_TOWARD_WALL:
                    xTarget = autoChoices.alliance == CommonAuto.Alliance.RED_ALLIANCE ? -48.0 : 48.0;
                    absTargetDrive.setXTarget(xTarget, State.ALIGN_WITH_BRIDGE);
                    break;

                case ALIGN_WITH_BRIDGE:
                    turnTarget = autoChoices.alliance == CommonAuto.Alliance.RED_ALLIANCE ? 30.0 : -30.0;
                    absTargetDrive.setTurnTarget(turnTarget, State.PARK_UNDER_BRIDGE);
                    break;

                case PARK_UNDER_BRIDGE:
                    yTarget = -12.0;
                    absTargetDrive.setYTarget(yTarget, State.DONE);
                    break;

                case DONE:
                default:
                    //
                    // We are done.
                    //
                    robot.pidDrive.getXPidCtrl().restoreOutputLimit();
                    robot.pidDrive.getYPidCtrl().restoreOutputLimit();
                    sm.stop();
                    break;
            }

            robot.traceStateInfo(elapsedTime, state.toString(), xTarget, yTarget, turnTarget);
        }

        return !sm.isEnabled();
    }   //cmdPeriodic

}   //CmdAutoBuildingZone3543