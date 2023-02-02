package org.firstinspires.ftc.teamcode.subsystems;

import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.auto.AutoConstants;

import java.util.Timer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

@Config
public class TapePositionSensor {
    public static double red_kp = 0.3;
    public static double red_ki = 0.175;
    public static double red_kd = 0.04;

    public static double blue_kp = 0.1;
    public static double blue_ki = 0.2;
    public static double blue_kd = 0.007;

    private static double kp;
    private static double ki;
    private static double kd;
    public static double BLUE_HUE = 210.0;
    public static double BLUE_GAIN = 50;
    public static double RED_GREY_THRESH = 50;
    public static double BLUE_GREY_THRESH = 200;
    public enum TapeColour {
        RED,
        BLUE
    }

    private ColourSensor leftSensor;
    private ColourSensor rightSensor;
    private TapeColour colour;
    private PIDController pidController;

    public TapePositionSensor(HardwareMap map, TapeColour colour) {
        leftSensor = new ColourSensor(map, "cs0", colour == TapeColour.RED ? 20 : 40);
        rightSensor = new ColourSensor(map, "cs1", colour == TapeColour.RED ? 20 : 40);
        if(colour == TapeColour.RED) {
            kp = red_kp;
            ki = red_ki;
            kd = red_kd;
        } else {
            kp = blue_kp;
            ki = blue_ki;
            kd = blue_kd;
        }
        this.colour = colour;
        pidController = new PIDController(this::getPosition);
    }

    public double getPosition() {

        if(colour == TapeColour.RED) {
            return (rightSensor.getHsvValues()[0] - leftSensor.getHsvValues()[0])/ 255.0f;
        } else {
            return (((rightSensor.getHsvValues()[0] + 360 - BLUE_HUE) % 360) - ((leftSensor.getHsvValues()[0] + 360 - BLUE_HUE) % 360))/ 255.0f;
        }
    }

    public void setColour(TapeColour colour) {
        this.colour = colour;
        if(colour == TapeColour.BLUE) {
            leftSensor.setGain((float) BLUE_GAIN);
            rightSensor.setGain((float) BLUE_GAIN);
            // set pid
            kp = blue_kp;
            ki = blue_ki;
            kd = blue_kd;
        } else if(colour == TapeColour.RED) {
            leftSensor.setGain(20);
            rightSensor.setGain(20);
            // set pid
            kp = red_kp;
            ki = red_ki;
            kd = red_kd;
        } else {
            throw new IllegalArgumentException("Invalid colour");
        }
    }

    public PIDController getPidController() {
        return pidController;
    }

    public double getSuggestedPower() {
        return pidController.getOutput();
    }

    public boolean align(DoubleConsumer strafer, Telemetry telemetry) {
        double estimate;
        ElapsedTime timer = new ElapsedTime();
        timer.reset();

        telemetry.addData("Left Hue", leftSensor.getHsvValues()[0]);
        telemetry.addData("Right Hue", rightSensor.getHsvValues()[0]);
        telemetry.addData("Left Saturation", leftSensor.getHsvValues()[1]);
        telemetry.addData("Right Saturation", rightSensor.getHsvValues()[1]);
        telemetry.addData("Left Value", leftSensor.getHsvValues()[2]);
        telemetry.addData("Right Value", rightSensor.getHsvValues()[2]);

        do {
            estimate = getPosition();
            strafer.accept(getSuggestedPower());
            telemetry.addData("Alignment Estimate", estimate);
            telemetry.addData("Tolerance", AutoConstants.TAPE_TOLERANCE);
            if(timer.milliseconds() >= 2000) return false;
            //telemetry.update();
        } while (Math.abs(estimate) >= AutoConstants.TAPE_TOLERANCE);
        strafer.accept(0);
        telemetry.update();
        return true;
    }

    public boolean isInRange() {
        if(this.colour == TapeColour.RED) {
            return !(leftSensor.getHsvValues()[0] > RED_GREY_THRESH) || !(rightSensor.getHsvValues()[0] > RED_GREY_THRESH); // return true if either value works
        } else {
            return !(leftSensor.getHsvValues()[0] < BLUE_GREY_THRESH) || !(rightSensor.getHsvValues()[0] < BLUE_GREY_THRESH);
        }
    }

    public static class PIDController {
        //private double kp;
        //private double ki;
        //private double kd;
        private double lastError;
        private double integral;
        private double derivative;
        private static final double SETPOINT = 0;

        DoubleSupplier input;

        /*public PIDController(double kp, double ki, double kd) {
            this.kp = kp;
            this.ki = ki;
            this.kd = kd;
        }*/

        private ElapsedTime timer = new ElapsedTime();

        public PIDController(DoubleSupplier input) {
            timer.reset();
            lastError = SETPOINT - input.getAsDouble();
            this.input = input;
            Timer timer = new Timer();
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    updateValues();
                }
            }, 0, 10);
        }


        public void updateValues() {
            // Calculate error
            double error = SETPOINT - input.getAsDouble();
            // Calculate integral
            integral = this.integral + (timer.nanoseconds() * error)/Math.pow(10, 9);
            // Calculate derivative
            derivative = ((error - lastError) / timer.nanoseconds()) * Math.pow(10, 9);
            // Set last error
            lastError = error;
            timer.reset();
        }

        public void resetPIDState() {
            derivative = 0;
            integral = 0;
            timer.reset();
        }


        public double getOutput() {
            return kp * lastError + ki * integral + kd * derivative;
        }

    }
}
