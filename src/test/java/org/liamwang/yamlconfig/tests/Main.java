package org.liamwang.yamlconfig.tests;

import org.junit.jupiter.api.Test;
import org.liamwang.yamlconfig.YamlConfigPrefix;

public class Main {

    public static FakeMotor motorA = new FakeMotor();

    @Test
    public void testYamlConfig() {
        System.out.println("----------");
        System.out.println("Program started...");

        YamlConfigPrefix vPidConfig = new YamlConfigPrefix("DriveTrain/VelocityPID");

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
