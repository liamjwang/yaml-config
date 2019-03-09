package org.liamwang.yamlconfig.tests;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.liamwang.yamlconfig.YamlConfig;
import org.liamwang.yamlconfig.YamlConfigEntry;
import org.liamwang.yamlconfig.YamlConfigPrefix;

public class YamlConfigTests {

    private static final Logger logger = Logger.getLogger(YamlConfigTests.class);

    public static FakeMotor motorA = new FakeMotor();

    @Test
    public void testYamlConfig() {
        System.out.println("----------");
        System.out.println("Program started...");

        Logger.getRootLogger().setLevel(Level.ERROR);
        BasicConfigurator.configure();

        YamlConfigPrefix vPidConfig = YamlConfig.getPrefix("DriveTrain/VelocityPID");

        vPidConfig.registerPrefixListener(prefix -> {
            motorA.config_kP(prefix.getEntry("P/////").getDouble(3));
            motorA.config_kI(prefix.getEntry("I").getDouble(3));
            motorA.config_kD(prefix.getEntry("D").getDouble(3));
            System.out.print("Motor values updated: ");
            motorA.printPIDConfig();
        });

        YamlConfigEntry tpmConfig = YamlConfig.getEntry("DriveTrain/UnitConversions/TicksPerMeter");

        tpmConfig.registerListener(entry -> {
            System.out.println("TicksPerMeter updated: " + entry.getDouble(0));
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
