/*
 * Copyright (c) 2018 Titan Robotics Club (http://www.titanrobotics.com)
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
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package common;

import ftclib.FtcDcMotor;
import ftclib.FtcDigitalInput;
import trclib.TrcEvent;
import trclib.TrcPidActuator;
import trclib.TrcPidController;

public class Elevator
{
    private FtcDigitalInput upperLimitSwitch;
    private FtcDigitalInput lowerLimitSwitch;
    private FtcDcMotor elevatorMotor;
    private TrcPidController pidController;
    private TrcPidActuator pidElevator;

    public Elevator(
            TrcPidController.PidCoefficients pidCoeff, double calPower, double minHeight, double maxHeight,
            double scale, double offset, double tolerance)
    {
        upperLimitSwitch = new FtcDigitalInput("elevatorUpperLimit");
        lowerLimitSwitch = new FtcDigitalInput("elevatorLowerLimit");
        upperLimitSwitch.setInverted(true);
        lowerLimitSwitch.setInverted(true);

        elevatorMotor = new FtcDcMotor("elevatorMotor", lowerLimitSwitch, upperLimitSwitch);
        elevatorMotor.setBrakeModeEnabled(true);
        elevatorMotor.setOdometryEnabled(true);

        pidController = new TrcPidController(
                "elevatorPidController", pidCoeff, tolerance, this::getPosition);
        pidElevator = new TrcPidActuator(
                "pidElevator", elevatorMotor, lowerLimitSwitch, pidController, calPower,
                minHeight, maxHeight);
        pidElevator.setPositionScale(scale, offset);
    }   //Elevator

    public void zeroCalibrate()
    {
        pidElevator.zeroCalibrate();
    }   //zeroCalibrate

    public void setManualOverride(boolean enabled)
    {
        pidElevator.setManualOverride(enabled);
    }   //setManualOverride

    public void setPower(double power)
    {
        pidElevator.setPower(power);
    }   //setPower

    public void setPosition(double target, TrcEvent event, double timeout)
    {
        pidElevator.setTarget(target, event, timeout);
    }   //setPosition

    public void setPosition(double target)
    {
        pidElevator.setTarget(target, null, 0.0);
    }   //setPosition

    public double getPosition()
    {
        return pidElevator.getPosition();
    }   //getPosition

    public boolean isUpperLimitSwitchActive()
    {
        return upperLimitSwitch.isActive();
    }   //isUpperLimitSwitchActive

    public boolean isLowerLimitSwitchActive()
    {
        return lowerLimitSwitch.isActive();
    }   //isLowerLimitSwitchActive

}   //class Elevator
