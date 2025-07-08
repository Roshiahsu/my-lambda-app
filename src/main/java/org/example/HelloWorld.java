package org.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class HelloWorld implements RequestHandler<Object, String> {


    @Override
    public String handleRequest(Object o, Context context) {
        return "Hello Java ,I'm Roshia~";
    }
}
