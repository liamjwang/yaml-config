package org.liamwang.yamlconfig.tests;

public class FakeMotor {

    private double kP;
    private double kI;
    private double kD;

    public void config_kP(double kP) {
        this.kP = kP;
    }

    public void config_kI(double kI) {
        this.kI = kI;
    }

    public void config_kD(double kD) {
        this.kD = kD;
    }

    public void printPIDConfig() {
        System.out.println("kP: " + kP + " kI: " + kI + " kD: " + kD);
    }
}
