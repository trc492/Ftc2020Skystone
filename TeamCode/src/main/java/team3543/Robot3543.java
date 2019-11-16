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

import common.Elevator;
import common.Grabber;
import common.Robot;
import common.ServoEndEffector;
import ftclib.FtcDcMotor;
import trclib.TrcHashMap;
import trclib.TrcHomographyMapper;
import trclib.TrcMecanumDriveBase;
import trclib.TrcPidController;
import trclib.TrcPidDrive;
import trclib.TrcRobot;

class Robot3543 extends Robot
{
    private static final TrcHashMap<String, Boolean> preferences3543 = new TrcHashMap<String, Boolean>()
            .add("hasRobot", true)
            .add("hasElevator", true)
            .add("useTraceLog", true)
            .add("useSpeech", true)
            .add("useBatteryMonitor", false)
            .add("useLoopPerformanceMonitor", true)
            .add("useBlinkin", true)
            .add("useVelocityControl", false)
            .add("useVuforia", true)
            .add("useTensorFlow", false)
            .add("useFlashLight", true)
            .add("showVuforiaView", false)
            .add("showTensorFlowView", false)
            .add("initSubsystems", true)
            .add("useVisionTrigger", false)
            .add("team3543", true);
    private static final PhoneParameters phoneParams3543 = new PhoneParameters()
            .setCameraDir(RobotInfo3543.CAMERA_DIR)
            .setCameraMonitorFeedback(RobotInfo3543.CAMERA_MONITOR_FEEDBACK)
            .setPhoneIsPortrait(RobotInfo3543.PHONE_IS_PORTRAIT)
            .setPhoneFrontOffset(RobotInfo3543.PHONE_FRONT_OFFSET)
            .setPhoneLeftOffset(RobotInfo3543.PHONE_LEFT_OFFSET)
            .setPhoneHeightOffset(RobotInfo3543.PHONE_HEIGHT_OFFSET);
    private static final Elevator.Parameters elevatorParams3543 = new Elevator.Parameters()
            .setMinHeight(RobotInfo3543.ELEVATOR_MIN_HEIGHT)
            .setMaxHeight(RobotInfo3543.ELEVATOR_MAX_HEIGHT)
            .setScale(RobotInfo3543.ELEVATOR_SCALE)
            .setOffset(RobotInfo3543.ELEVATOR_OFFSET)
            .setKp(RobotInfo3543.ELEVATOR_KP)
            .setKi(RobotInfo3543.ELEVATOR_KI)
            .setKd(RobotInfo3543.ELEVATOR_KD)
            .setTolerance(RobotInfo3543.ELEVATOR_TOLERANCE)
            .setCalPower(RobotInfo3543.ELEVATOR_CAL_POWER);
    private static final Wrist3543.Parameters wristParams3543 = new Wrist3543.Parameters()
            .setMaxStepRate(RobotInfo3543.WRIST_MAX_STEPRATE)
            .setMinPos(RobotInfo3543.WRIST_MIN_POS)
            .setMaxPos(RobotInfo3543.WRIST_MAX_POS)
            .setRetractPos(RobotInfo3543.WRIST_RETRACT_POS)
            .setExtendPos(RobotInfo3543.WRIST_EXTEND_POS)
            .setInverted(RobotInfo3543.WRIST_INVERTED);
    private static final ServoEndEffector.Parameters grabberParams3543 = new ServoEndEffector.Parameters()
            .setRetractPos(RobotInfo3543.GRABBER_CLOSE_POS)
            .setRetractTime(RobotInfo3543.GRABBER_GRAB_TIME)
            .setExtendPos(RobotInfo3543.GRABBER_OPEN_POS)
            .setExtendTime(RobotInfo3543.GRABBER_RELEASE_TIME);
    private static final ServoEndEffector.Parameters foundationLatchParams3543 = new ServoEndEffector.Parameters()
            .setRetractPos(RobotInfo3543.FOUNDATION_LATCH_RELEASE_POS)
            .setRetractTime(RobotInfo3543.FOUNDATION_LATCH_RELEASE_TIME)
            .setExtendPos(RobotInfo3543.FOUNDATION_LATCH_GRAB_POS)
            .setExtendTime(RobotInfo3543.FOUNDATION_LATCH_GRAB_TIME);

    ExtenderArm3543 extenderArm = null;
    Wrist3543 wrist = null;

    Robot3543(TrcRobot.RunMode runMode)
    {
        super(runMode, RobotInfo3543.ROBOT_NAME, preferences3543, phoneParams3543);
        //
        // Initialize vision subsystems.
        //
        if (preferences.getBoolean("useVuforia") && vuforia != null &&
            (runMode == TrcRobot.RunMode.AUTO_MODE || runMode == TrcRobot.RunMode.TEST_MODE))
        {
            initVuforia(phoneParams3543, RobotInfo3543.ROBOT_LENGTH, RobotInfo3543.ROBOT_WIDTH);
        }

        if (preferences.getBoolean("useTensorFlow") && vuforia != null &&
            (runMode == TrcRobot.RunMode.AUTO_MODE || runMode == TrcRobot.RunMode.TEST_MODE))
        {
            TrcHomographyMapper.Rectangle cameraRect = new TrcHomographyMapper.Rectangle(
                    RobotInfo3543.HOMOGRAPHY_CAMERA_TOPLEFT_X, RobotInfo3543.HOMOGRAPHY_CAMERA_TOPLEFT_Y,
                    RobotInfo3543.HOMOGRAPHY_CAMERA_TOPRIGHT_X, RobotInfo3543.HOMOGRAPHY_CAMERA_TOPRIGHT_Y,
                    RobotInfo3543.HOMOGRAPHY_CAMERA_BOTTOMLEFT_X, RobotInfo3543.HOMOGRAPHY_CAMERA_BOTTOMLEFT_Y,
                    RobotInfo3543.HOMOGRAPHY_CAMERA_BOTTOMRIGHT_X, RobotInfo3543.HOMOGRAPHY_CAMERA_BOTTOMRIGHT_Y);

            TrcHomographyMapper.Rectangle worldRect = new TrcHomographyMapper.Rectangle(
                    RobotInfo3543.HOMOGRAPHY_WORLD_TOPLEFT_X, RobotInfo3543.HOMOGRAPHY_WORLD_TOPLEFT_Y,
                    RobotInfo3543.HOMOGRAPHY_WORLD_TOPRIGHT_X, RobotInfo3543.HOMOGRAPHY_WORLD_TOPRIGHT_Y,
                    RobotInfo3543.HOMOGRAPHY_WORLD_BOTTOMLEFT_X, RobotInfo3543.HOMOGRAPHY_WORLD_BOTTOMLEFT_Y,
                    RobotInfo3543.HOMOGRAPHY_WORLD_BOTTOMRIGHT_X, RobotInfo3543.HOMOGRAPHY_WORLD_BOTTOMRIGHT_Y);

            initTensorFlow(preferences.getBoolean("showTensorFlowView"), cameraRect, worldRect);
        }

        if (preferences.getBoolean("hasRobot"))
        {
            //
            // Initialize DriveBase.
            //
            initDriveBase();
            //
            // Initialize other subsystems.
            //
            if (preferences.getBoolean("initSubsystems"))
            {
                if (preferences.getBoolean("hasElevator"))
                {
                    elevator = new Elevator(preferences3543, elevatorParams3543, null);
                    elevator.zeroCalibrate();
                }

                extenderArm = new ExtenderArm3543();
                extenderArm.retract();

                wrist = new Wrist3543(wristParams3543);
                wrist.retract();

                grabber = new Grabber("grabberServo", grabberParams3543);
                grabber.release();

                foundationLatch = new Grabber("foundationLatchServo", foundationLatchParams3543);
                foundationLatch.release();
            }
        }
        //
        // Tell the driver initialization is complete.
        //
        speak("Init complete!");
    }   //Robot3543

    private void initDriveBase()
    {
        leftFrontWheel = new FtcDcMotor("lfWheel");
        rightFrontWheel = new FtcDcMotor("rfWheel");
        leftRearWheel = new FtcDcMotor("lrWheel");
        rightRearWheel = new FtcDcMotor("rrWheel");

        leftFrontWheel.motor.setMode(RobotInfo3543.DRIVE_MOTOR_MODE);
        rightFrontWheel.motor.setMode(RobotInfo3543.DRIVE_MOTOR_MODE);
        leftRearWheel.motor.setMode(RobotInfo3543.DRIVE_MOTOR_MODE);
        rightRearWheel.motor.setMode(RobotInfo3543.DRIVE_MOTOR_MODE);

        leftFrontWheel.setOdometryEnabled(true);
        rightFrontWheel.setOdometryEnabled(true);
        leftRearWheel.setOdometryEnabled(true);
        rightRearWheel.setOdometryEnabled(true);

        if (preferences.getBoolean("useVelocityControl"))
        {
            TrcPidController.PidCoefficients motorPidCoef = new TrcPidController.PidCoefficients(
                    RobotInfo3543.MOTOR_KP, RobotInfo3543.MOTOR_KI, RobotInfo3543.MOTOR_KD);
            leftFrontWheel.enableVelocityMode(RobotInfo3543.MOTOR_MAX_VELOCITY, motorPidCoef);
            rightFrontWheel.enableVelocityMode(RobotInfo3543.MOTOR_MAX_VELOCITY, motorPidCoef);
            leftRearWheel.enableVelocityMode(RobotInfo3543.MOTOR_MAX_VELOCITY, motorPidCoef);
            rightRearWheel.enableVelocityMode(RobotInfo3543.MOTOR_MAX_VELOCITY, motorPidCoef);
        }

        leftFrontWheel.setInverted(false);
        leftRearWheel.setInverted(false);
        rightFrontWheel.setInverted(true);
        rightRearWheel.setInverted(true);

        leftFrontWheel.setBrakeModeEnabled(true);
        leftRearWheel.setBrakeModeEnabled(true);
        rightFrontWheel.setBrakeModeEnabled(true);
        rightRearWheel.setBrakeModeEnabled(true);

        driveBase = new TrcMecanumDriveBase(leftFrontWheel, leftRearWheel, rightFrontWheel, rightRearWheel, gyro);
        driveBase.setPositionScales(RobotInfo3543.ENCODER_X_INCHES_PER_COUNT, RobotInfo3543.ENCODER_Y_INCHES_PER_COUNT);
        driveMode = DriveMode.HOLONOMIC_MODE;
        //
        // Initialize PID drive.
        //
        encoderXPidCtrl = new TrcPidController(
                "encoderXPidCtrl",
                new TrcPidController.PidCoefficients(
                        RobotInfo3543.ENCODER_X_KP, RobotInfo3543.ENCODER_X_KI, RobotInfo3543.ENCODER_X_KD),
                RobotInfo3543.ENCODER_X_TOLERANCE, driveBase::getXPosition);
        encoderYPidCtrl = new TrcPidController(
                "encoderYPidCtrl",
                new TrcPidController.PidCoefficients(
                        RobotInfo3543.ENCODER_Y_KP, RobotInfo3543.ENCODER_Y_KI, RobotInfo3543.ENCODER_Y_KD),
                RobotInfo3543.ENCODER_Y_TOLERANCE, driveBase::getYPosition);
        gyroPidCtrl = new TrcPidController(
                "gyroPidCtrl",
                new TrcPidController.PidCoefficients(
                        RobotInfo3543.GYRO_KP, RobotInfo3543.GYRO_KI, RobotInfo3543.GYRO_KD),
                RobotInfo3543.GYRO_TOLERANCE, driveBase::getHeading);
        gyroPidCtrl.setAbsoluteSetPoint(true);
        gyroPidCtrl.setOutputRange(-RobotInfo3543.TURN_POWER_LIMIT, RobotInfo3543.TURN_POWER_LIMIT);

        pidDrive = new TrcPidDrive(
                "pidDrive", driveBase, encoderXPidCtrl, encoderYPidCtrl, gyroPidCtrl);
        pidDrive.setAbsTargetModeEnabled(true);
        pidDrive.setStallTimeout(RobotInfo3543.PIDDRIVE_STALL_TIMEOUT);
        pidDrive.setBeep(androidTone);
    }   //initDriveBase

}   //class Robot3543
