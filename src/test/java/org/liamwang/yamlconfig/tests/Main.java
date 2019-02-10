package org.liamwang.yamlconfig.tests;

import org.liamwang.yamlconfig.YConfig;

public class Main {

    public static FakeMotor motorA = new FakeMotor();

    public static void main(String[] args) {
        System.out.println("----------");
        System.out.println("Program Started...");

        YConfig vPidConfig = new YConfig("DriveTrain/VelocityPID");

        vPidConfig.registerPrefixListener(() -> {
            motorA.config_kP(vPidConfig.getDouble("P", 3));
            motorA.config_kI(vPidConfig.getDouble("I", 3));
            motorA.config_kD(vPidConfig.getDouble("D", 3));
            System.out.print("Motor values updated: ");
            motorA.printPIDConfig();
        });

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(System.currentTimeMillis() + " Running..");
        }
    }
}
