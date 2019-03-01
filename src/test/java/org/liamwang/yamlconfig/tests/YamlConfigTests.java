package org.liamwang.yamlconfig.tests;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.liamwang.yamlconfig.YamlConfigPrefix;

public class YamlConfigTests {

    private static final Logger logger = Logger.getLogger(YamlConfigTests.class);

    public static FakeMotor motorA = new FakeMotor();

    @Test
    public void testYamlConfig() {
        System.out.println("----------");
        System.out.println("Program started...");

        Logger.getRootLogger().setLevel(Level.DEBUG);
        BasicConfigurator.configure();

        YamlConfigPrefix vPidConfig = new YamlConfigPrefix("DriveTrain/VelocityPID");
//
//        vPidConfig.registerPrefixListener(() -> {
//            motorA.config_kP(vPidConfig.getDouble("P", 3));
//            motorA.config_kI(vPidConfig.getDouble("I", 3));
//            motorA.config_kD(vPidConfig.getDouble("D", 3));
//            System.out.print("Motor values updated: ");
//            motorA.printPIDConfig();
//        });
//
//        while (true) {
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            System.out.println(System.currentTimeMillis() + " Running..");
//        }
    }
}
