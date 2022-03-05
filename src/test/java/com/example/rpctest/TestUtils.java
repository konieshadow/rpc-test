package com.example.rpctest;

import java.util.concurrent.Callable;

public class TestUtils {

    public static <T> T delayGet(Callable<T> callable, T expect, int period, int times) {
        T result = null;
        int i = 0;
        while (i++ < times) {
            try {
                Thread.sleep(period);
                result = callable.call();
                if (result != null && result.equals(expect)) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}