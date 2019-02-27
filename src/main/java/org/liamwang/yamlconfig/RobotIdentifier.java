package org.liamwang.yamlconfig;

public class RobotIdentifier {

    private static final String NAME_TXT_PATH = "name.txt";
    public static final String PRACTICE_ROBOT_NAME = "ferb";

    private static String robotName = "";

    static {
//        try {
//            Files.readString(Paths.get(NAME_TXT_PATH));
//        } catch (IOException ignored) {
//        }
    }

    public static String getRobotName() {
        return "ferb";
//        return robotName;
    }

    public static boolean isPractice() {
        return getRobotName().startsWith(PRACTICE_ROBOT_NAME);
    }
}
