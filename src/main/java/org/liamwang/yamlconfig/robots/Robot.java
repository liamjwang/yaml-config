package org.liamwang.yamlconfig.robots;

import edu.wpi.first.wpilibj.TimedRobot;
import org.liamwang.yamlconfig.RobotIdentifier;

public class Robot extends TimedRobot {

    private String name = "";

    @Override
    public void robotInit() {
        if (RobotIdentifier.isPractice()) {
            System.out.println("I am a practice robot!");
        } else {
            System.out.println("I am the competition robot!");
        }
    }

    @Override
    public void robotPeriodic() {
    }

    @Override
    public void disabledInit() {
    }

    @Override
    public void autonomousInit() {
    }

    @Override
    public void teleopInit() {
    }

    @Override
    public void disabledPeriodic() {
    }

    @Override
    public void autonomousPeriodic() {
    }

    @Override
    public void teleopPeriodic() {
    }
}
