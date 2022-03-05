package com.example.rpctest;

public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String string) {
        return "hello " + string + " ！";
    }
}
