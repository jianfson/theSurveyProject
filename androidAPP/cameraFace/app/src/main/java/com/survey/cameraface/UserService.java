package com.survey.cameraface;

public class UserService {
    public static boolean signIn(String name, String password) {
        HttpThread httpThread = new HttpThread("http://223.128.83.45:8081/BOOKCITY/loginServlet?action=login", name, password);
        try {
            httpThread.start();
            httpThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return httpThread.getResult();
    }

    public static boolean signUp(String name, String password) {
        HttpThread httpThread = new HttpThread("http://223.128.83.45:8081/BOOKCITY/registServlet?action=regist", name, password);
        try {
            httpThread.start();
            httpThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return httpThread.getResult();
    }
}