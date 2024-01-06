package dev.joago;

public class Main {
    public static void main(String[] args) {
        System.out.println(toSnakeCase("HelloWorld"));
    }
    private static String toSnakeCase(String str) {
        String regex = "([a-z])([A-Z]+)";
        String replacement = "$1_$2";
        str = str
                .replaceAll(
                        regex, replacement)
                .toLowerCase();
        return str;
    }
}