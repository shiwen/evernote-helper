package me.shiwen.evernote;

/**
 * Created by Shiwen on 2016/7/16.
 */
public class Test {
    public static void main(String... args) {
        Inner i = new Inner("a");
        change(i);
        System.out.println(i.s);
    }

    private static void change(Inner i) {
        i = new Inner("b");
    }

    public static class Inner {
        public String s;
        public Inner(String s) {
            this.s = s;
        }
    }
}
