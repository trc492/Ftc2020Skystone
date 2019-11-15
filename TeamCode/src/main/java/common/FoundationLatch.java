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

import ftclib.FtcServo;
import trclib.TrcEvent;
import trclib.TrcHashMap;

public class FoundationLatch implements GenericGrabber
{
    private final FtcServo servo = new FtcServo("foundationLatchServo");
    private final double closePos, closeTime;
    private final double openPos, openTime;

    public FoundationLatch(TrcHashMap<String, Double> params)
    {
        this.closePos = params.getDouble("closePos");
        this.closeTime = params.getDouble("closeTime");
        this.openPos = params.getDouble("openPos");
        this.openTime = params.getDouble("openTime");
    }   //FoundationLatch

    @Override
    public double getPosition()
    {
        return servo.getPosition();
    }   //getPosition

    @Override
    public void grab()
    {
        servo.setPosition(closePos);
    }   //grab

    @Override
    public void grab(TrcEvent event)
    {
        servo.setPosition(closePos, closeTime, event);
    }   //grab

    @Override
    public void grab(double time, TrcEvent event)
    {
        servo.setPosition(closePos, time, event);
    }   //grab

    @Override
    public void release()
    {
        servo.setPosition(openPos);
    }   //release

    @Override
    public void release(TrcEvent event)
    {
        servo.setPosition(openPos, openTime, event);
    }   //release

    @Override
    public void release(double time, TrcEvent event)
    {
        servo.setPosition(openPos, time, event);
    }   //release

}   //class FoundationLatch
